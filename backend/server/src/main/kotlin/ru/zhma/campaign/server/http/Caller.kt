package ru.zhma.campaign.server.http

import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.ApplicationCallPipeline
import io.ktor.server.request.httpMethod
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.intercept
import io.ktor.util.AttributeKey

data class Caller(val user: String, val role: String)

val CallerKey = AttributeKey<Caller>("Caller")

private val knownRoles = setOf("campaignManager", "campaignModerator")

fun Route.installCaller() {
    intercept(ApplicationCallPipeline.Call) {
        val current = context
        if (current.request.httpMethod == HttpMethod.Options) return@intercept
        val user = current.request.headers["X-User"]
        val role = current.request.headers["X-Role"]
        val errors = mutableListOf<ApiError>()
        if (user.isNullOrBlank()) {
            errors += ApiError("X-User", "Заголовок X-User обязателен", "X-User")
        }
        if (role.isNullOrBlank()) {
            errors += ApiError("X-Role", "Заголовок X-Role обязателен", "X-Role")
        } else if (role !in knownRoles) {
            errors += ApiError("X-Role", "Роль должна быть campaignManager или campaignModerator", "X-Role")
        }
        if (errors.isNotEmpty()) {
            current.respond(HttpStatusCode.BadRequest, ErrorResponse(errors))
            finish()
            return@intercept
        }
        current.attributes.put(CallerKey, Caller(user!!.trim(), role!!.trim()))
        current.response.header("X-User", user.trim())
        current.response.header("X-Role", role.trim())
    }
}

fun ApplicationCall.caller(): Caller = attributes[CallerKey]

fun ApplicationCall.requireManager() {
    if (caller().role != "campaignManager") throw roleForbidden()
}

fun ApplicationCall.requireModerator() {
    if (caller().role != "campaignModerator") throw roleForbidden()
}
