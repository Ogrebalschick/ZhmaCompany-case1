package ru.zhma.campaign.server.http.route

import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import ru.zhma.campaign.server.db.DatabaseFactory.db
import ru.zhma.campaign.server.http.ApiError
import ru.zhma.campaign.server.http.bad
import ru.zhma.campaign.server.http.caller
import ru.zhma.campaign.server.http.dto.CommentBody
import ru.zhma.campaign.server.http.requireManager
import ru.zhma.campaign.server.http.requireModerator
import ru.zhma.campaign.server.service.activate
import ru.zhma.campaign.server.service.approve
import ru.zhma.campaign.server.service.auditOf
import ru.zhma.campaign.server.service.campaignDetails
import ru.zhma.campaign.server.service.confirmDraft
import ru.zhma.campaign.server.service.decline
import ru.zhma.campaign.server.service.listCampaignItems
import ru.zhma.campaign.server.service.moderate
import ru.zhma.campaign.server.service.stateOf
import ru.zhma.campaign.server.service.warningsOf

fun Route.campaignRoutes() {
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
}

internal fun ApplicationCall.longId(): Long {
    val raw = parameters["id"]
    return raw?.toLongOrNull() ?: throw bad(ApiError("id", "Некорректный идентификатор", "id"))
}
