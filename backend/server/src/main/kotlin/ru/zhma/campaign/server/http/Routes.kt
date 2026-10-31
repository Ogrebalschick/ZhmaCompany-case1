package ru.zhma.campaign.server.http

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import ru.zhma.campaign.ai.model.AssistantReply
import ru.zhma.campaign.server.db.DatabaseFactory.db
import ru.zhma.campaign.server.jobs.JobProcessor
import ru.zhma.campaign.server.json.AppJson
import ru.zhma.campaign.server.service.activate
import ru.zhma.campaign.server.service.approve
import ru.zhma.campaign.server.service.auditOf
import ru.zhma.campaign.server.service.campaignDetails
import ru.zhma.campaign.server.service.confirmDraft
import ru.zhma.campaign.server.service.createSession
import ru.zhma.campaign.server.service.decline
import ru.zhma.campaign.server.service.insertJob
import ru.zhma.campaign.server.service.jobRow
import ru.zhma.campaign.server.service.listBannerTypes
import ru.zhma.campaign.server.service.listCampaignItems
import ru.zhma.campaign.server.service.listChannels
import ru.zhma.campaign.server.service.listMessages
import ru.zhma.campaign.server.service.listPlaces
import ru.zhma.campaign.server.service.listSegments
import ru.zhma.campaign.server.service.listTags
import ru.zhma.campaign.server.service.listTopics
import ru.zhma.campaign.server.service.moderate
import ru.zhma.campaign.server.service.sessionExists
import ru.zhma.campaign.server.service.stateOf
import ru.zhma.campaign.server.service.warningsOf
import ru.zhma.campaign.server.db.JobsTable
import java.util.UUID

fun Route.apiRoutes(jobs: JobProcessor) {
    route("/api") {
        installCaller()
        get("/topics") { call.respond(db { listTopics() }) }
        get("/tags") { call.respond(db { listTags() }) }
        get("/segments") { call.respond(db { listSegments() }) }
        get("/places") { call.respond(db { listPlaces() }) }
        get("/banner-types") { call.respond(db { listBannerTypes() }) }
        get("/channels") { call.respond(db { listChannels() }) }

        get("/campaigns") { call.respond(db { listCampaignItems() }) }
        get("/campaigns/{id}") { call.respond(db { campaignDetails(call.longId()) }) }
        get("/campaigns/{id}/warnings") { call.respond(db { warningsOf(call.longId()) }) }
        get("/campaigns/{id}/audit") { call.respond(db { auditOf(call.longId()) }) }

        post("/campaigns/{id}/confirm") {
            call.requireManager()
            val sessionId = call.request.queryParameters["sessionId"]
                ?: throw bad(ApiError("sessionId", "Укажите sessionId", "sessionId"))
            call.respond(db { confirmDraft(sessionId, call.longId(), call.caller()) })
        }
        post("/campaigns/{id}/to-moderation") {
            call.requireManager()
            val id = call.longId()
            call.respond(db {
                moderate(id, call.caller().user)
                stateOf(id)
            })
        }
        post("/campaigns/{id}/approve") {
            call.requireModerator()
            val id = call.longId()
            call.respond(db {
                approve(id, call.caller().user)
                stateOf(id)
            })
        }
        post("/campaigns/{id}/decline") {
            call.requireModerator()
            val body = call.receive<CommentBody>()
            val id = call.longId()
            call.respond(db {
                decline(id, call.caller().user, body.comment)
                stateOf(id)
            })
        }
        post("/campaigns/{id}/activate") {
            call.requireManager()
            val id = call.longId()
            call.respond(db {
                activate(id, call.caller().user)
                stateOf(id)
            })
        }

        post("/sessions") {
            val id = UUID.randomUUID().toString()
            db { createSession(id) }
            call.respond(SessionResponse(id))
        }
        get("/sessions/{id}/messages") {
            call.respond(MessagesResponse(db { listMessages(call.parameters["id"] ?: "") }))
        }
        post("/sessions/{id}/messages") {
            val sessionId = call.parameters["id"] ?: ""
            val body = call.receive<PostMessageBody>()
            if (!db { sessionExists(sessionId) }) throw notFound("Сессия не найдена")
            val jobId = UUID.randomUUID().toString()
            db { insertJob(jobId, sessionId) }
            jobs.start(jobId, sessionId, body.text, call.caller())
            call.respond(HttpStatusCode.Accepted, JobAccepted(jobId))
        }
        get("/jobs/{jobId}") {
            val row = db { jobRow(call.parameters["jobId"] ?: "") }
            val result = row[JobsTable.resultJson]?.let { AppJson.decodeFromString(AssistantReply.serializer(), it) }
            call.respond(JobStatusResponse(row[JobsTable.status], result, row[JobsTable.error]))
        }
    }
}

private fun ApplicationCall.longId(): Long {
    val raw = parameters["id"]
    return raw?.toLongOrNull() ?: throw bad(ApiError("id", "Некорректный идентификатор", "id"))
}
