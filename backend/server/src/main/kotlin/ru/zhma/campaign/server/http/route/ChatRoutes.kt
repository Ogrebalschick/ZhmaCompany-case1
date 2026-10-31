package ru.zhma.campaign.server.http.route

import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import ru.zhma.campaign.ai.model.AssistantReply
import ru.zhma.campaign.server.db.DatabaseFactory.db
import ru.zhma.campaign.server.db.table.JobsTable
import ru.zhma.campaign.server.http.caller
import ru.zhma.campaign.server.http.dto.JobAccepted
import ru.zhma.campaign.server.http.dto.JobStatusResponse
import ru.zhma.campaign.server.http.dto.MessagesResponse
import ru.zhma.campaign.server.http.dto.PostMessageBody
import ru.zhma.campaign.server.http.dto.SessionResponse
import ru.zhma.campaign.server.http.notFound
import ru.zhma.campaign.server.jobs.JobProcessor
import ru.zhma.campaign.server.json.AppJson
import ru.zhma.campaign.server.service.createSession
import ru.zhma.campaign.server.service.insertJob
import ru.zhma.campaign.server.service.jobRow
import ru.zhma.campaign.server.service.listMessages
import ru.zhma.campaign.server.service.sessionExists
import java.util.UUID

fun Route.chatRoutes(jobs: JobProcessor) {
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
