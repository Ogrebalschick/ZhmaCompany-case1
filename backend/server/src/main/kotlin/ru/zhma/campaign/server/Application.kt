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
import ru.zhma.campaign.server.http.ApiError
import ru.zhma.campaign.server.http.ApiException
import ru.zhma.campaign.server.http.ErrorResponse
import ru.zhma.campaign.server.http.route.apiRoutes
import ru.zhma.campaign.server.images.ImageJobs
import ru.zhma.campaign.server.jobs.JobProcessor
import ru.zhma.campaign.server.json.AppJson
import ru.zhma.campaign.server.wiring.AppConfig
import ru.zhma.campaign.server.wiring.imageClient
import ru.zhma.campaign.server.wiring.imageTimeoutSec
import java.io.File

fun main() {
    val port = AppConfig.backendPort
    val log = org.slf4j.LoggerFactory.getLogger("campaign")
    log.info(
        "Сервер: http://localhost:{} | LLM: {} ({}) | картинки: {} ({})",
        port,
        if (AppConfig.bool("LLM_MOCK") == true) "заглушка (LLM_MOCK=true)" else AppConfig.llmBaseUrl ?: "заглушка",
        AppConfig.llmModel ?: "-",
        AppConfig.imageBaseUrl ?: "выкл",
        AppConfig.get("IMAGE_MODEL") ?: "qwen-image-2.1",
    )
    embeddedServer(Netty, port = port, host = AppConfig.backendHost, module = Application::module).start(wait = true)
}

fun Application.module(dbPath: String = System.getProperty("campaign.db") ?: AppConfig.get("DB_PATH") ?: "data/campaigns.db") {
    DatabaseFactory.init(dbPath)
    ru.zhma.campaign.server.wiring.llmLogFile =
        File(dbPath).absoluteFile.parentFile.resolve("llm.log")
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    val images =
        imageClient()?.let { client ->
            val dir = File(dbPath).absoluteFile.parentFile.resolve("images")
            ImageJobs(client, dir, scope, imageTimeoutSec())
        }
    val jobs = JobProcessor(scope, images)
    monitor.subscribe(ApplicationStopped) { jobs.cancel() }

    install(ContentNegotiation) { json(AppJson) }
    install(CORS) {
        allowHost("localhost:${AppConfig.frontendPort}", schemes = listOf("http"))
        allowHost("127.0.0.1:${AppConfig.frontendPort}", schemes = listOf("http"))
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
            call.application.environment.log
                .error("request failed", cause)
            if (!call.response.isSent) {
                call.respond(
                    HttpStatusCode.InternalServerError,
                    ErrorResponse(listOf(ApiError("INTERNAL", "Внутренняя ошибка", null))),
                )
            }
        }
    }
    routing { apiRoutes(jobs, images) }
}
