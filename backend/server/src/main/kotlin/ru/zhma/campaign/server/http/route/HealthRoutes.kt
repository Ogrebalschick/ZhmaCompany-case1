package ru.zhma.campaign.server.http.route

import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import ru.zhma.campaign.server.wiring.AppConfig
import java.net.HttpURLConnection
import java.net.URI

@Serializable
data class ServiceHealth(
    val url: String?,
    val model: String?,
    val ok: Boolean,
    val latencyMs: Long? = null,
    val note: String? = null,
)

@Serializable
data class HealthResponse(
    val server: Boolean,
    val llm: ServiceHealth,
    val images: ServiceHealth,
    val tools: Boolean,
    val watermark: String?,
)

private var cached: Pair<Long, HealthResponse>? = null

/** `GET /api/health` — живы ли модель и генерация картинок. Без заголовков ролей, кэш 20 секунд. */
fun Route.healthRoutes() {
    get("/health") {
        val now = System.currentTimeMillis()
        val hit = cached?.takeIf { now - it.first < 20_000 }?.second
        call.respond(hit ?: probe().also { cached = now to it })
    }
}

private suspend fun probe(): HealthResponse {
    val mock = AppConfig.bool("LLM_MOCK") == true || AppConfig.llmBaseUrl == null || AppConfig.llmModel == null
    val llm =
        if (mock) {
            ServiceHealth(null, null, true, note = "модель-заглушка, сеть не нужна")
        } else {
            ping(AppConfig.llmBaseUrl!!).let { (ok, ms, note) -> ServiceHealth(AppConfig.llmBaseUrl, AppConfig.llmModel, ok, ms, note) }
        }
    val imageUrl = AppConfig.imageBaseUrl.takeIf { AppConfig.bool("IMAGE_ENABLED") != false }
    val images =
        when {
            imageUrl == null -> ServiceHealth(null, null, false, note = "генерация картинок выключена")
            imageUrl == AppConfig.llmBaseUrl && !mock -> llm.copy(model = AppConfig.get("IMAGE_MODEL") ?: "qwen-image-2.1")
            else ->
                ping(imageUrl).let { (ok, ms, note) ->
                    ServiceHealth(imageUrl, AppConfig.get("IMAGE_MODEL") ?: "qwen-image-2.1", ok, ms, note)
                }
        }
    val watermark =
        if (AppConfig.bool("WATERMARK_ENABLED") ==
            false
        ) {
            null
        } else {
            AppConfig.get("WATERMARK_TEXT") ?: "ЧЕРНОВИК · НЕ ПРОШЛО МОДЕРАЦИЮ"
        }
    return HealthResponse(true, llm, images, !mock && (AppConfig.bool("LLM_TOOLS") ?: true), watermark)
}

private suspend fun ping(baseUrl: String): Triple<Boolean, Long?, String?> =
    withContext(Dispatchers.IO) {
        val started = System.currentTimeMillis()
        try {
            val conn = URI.create(baseUrl.trimEnd('/') + "/models").toURL().openConnection() as HttpURLConnection
            conn.connectTimeout = 3000
            conn.readTimeout = 3000
            val code = conn.responseCode
            Triple(code in 200..499, System.currentTimeMillis() - started, if (code in 200..299) null else "HTTP $code")
        } catch (e: Exception) {
            Triple(false, null, "нет связи: ${e.message?.take(80)}")
        }
    }
