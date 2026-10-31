package ru.zhma.campaign.server.http

import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.createRouteScopedPlugin
import io.ktor.server.application.install
import io.ktor.server.request.httpMethod
import io.ktor.server.request.path
import io.ktor.server.response.header
import io.ktor.server.routing.Route
import io.ktor.util.AttributeKey

data class Caller(
    val user: String,
    val role: String,
)

val CallerKey = AttributeKey<Caller>("Caller")

private val knownRoles = setOf("campaignManager", "campaignModerator")

private val CallerPlugin =
    createRouteScopedPlugin(name = "CallerHeaders") {
        onCall { call ->
            if (call.request.httpMethod == HttpMethod.Options) return@onCall
            // Картинки открывает тег <img>, он не умеет слать заголовки.
            if (call.request.path().startsWith("/api/images/") || call.request.path() == "/api/health") return@onCall
            val user = call.request.headers["X-User"]
            val role = call.request.headers["X-Role"]
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
                throw ApiException(HttpStatusCode.BadRequest, errors)
            }
            call.attributes.put(CallerKey, Caller(user!!.trim(), role!!.trim()))
            call.response.header("X-User", user.trim())
            call.response.header("X-Role", role.trim())
        }
    }

fun Route.installCaller() {
    install(CallerPlugin)
}

fun ApplicationCall.caller(): Caller = attributes[CallerKey]

fun ApplicationCall.requireManager() {
    if (caller().role != "campaignManager") throw roleForbidden()
}

fun ApplicationCall.requireModerator() {
    if (caller().role != "campaignModerator") throw roleForbidden()
}
