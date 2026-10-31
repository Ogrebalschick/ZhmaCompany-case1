package ru.zhma.campaign.server

import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationStopped
import io.ktor.server.application.install
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.cors.routing.CORS
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.response.respond
import io.ktor.server.routing.routing
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import ru.zhma.campaign.server.db.DatabaseFactory
import ru.zhma.campaign.server.http.ApiException
import ru.zhma.campaign.server.http.ErrorResponse
import ru.zhma.campaign.server.http.apiRoutes
import ru.zhma.campaign.server.jobs.JobProcessor
import ru.zhma.campaign.server.json.AppJson

fun main() {
    embeddedServer(Netty, port = 8080, host = "0.0.0.0", module = Application::module).start(wait = true)
}

fun Application.module(dbPath: String = System.getProperty("campaign.db") ?: "data/campaigns.db") {
    DatabaseFactory.init(dbPath)
    val jobs = JobProcessor(CoroutineScope(SupervisorJob() + Dispatchers.IO))
    monitor.subscribe(ApplicationStopped) { jobs.cancel() }

    install(ContentNegotiation) { json(AppJson) }
    install(CORS) {
        allowHost("localhost:5173", schemes = listOf("http"))
        allowHeader(HttpHeaders.ContentType)
        allowHeader("X-User")
        allowHeader("X-Role")
        exposeHeader("X-User")
        exposeHeader("X-Role")
        allowMethod(HttpMethod.Get)
        allowMethod(HttpMethod.Post)
        allowMethod(HttpMethod.Put)
        allowMethod(HttpMethod.Delete)
        allowMethod(HttpMethod.Options)
        allowNonSimpleContentTypes = true
    }
    install(StatusPages) {
        exception<ApiException> { call, cause ->
            call.respond(cause.status, ErrorResponse(cause.errors))
        }
        exception<Throwable> { call, cause ->
            call.application.environment.log.error("request failed", cause)
            if (!call.response.isSent) {
                call.respond(
                    HttpStatusCode.InternalServerError,
                    ErrorResponse(listOf(ru.zhma.campaign.server.http.ApiError("INTERNAL", "Внутренняя ошибка", null))),
                )
            }
        }
    }
    routing { apiRoutes(jobs) }
}
