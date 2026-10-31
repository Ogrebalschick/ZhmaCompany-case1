package ru.zhma.campaign.server.http.route

import io.ktor.http.ContentDisposition
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.response.respondBytes
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import kotlinx.serialization.Serializable
import org.slf4j.LoggerFactory
import ru.zhma.campaign.server.db.DatabaseFactory.db
import ru.zhma.campaign.server.http.notFound
import ru.zhma.campaign.server.images.ImageJobs
import ru.zhma.campaign.server.images.Watermark
import ru.zhma.campaign.server.service.imageApproved
import ru.zhma.campaign.server.wiring.AppConfig

@Serializable
data class ImageStatusResponse(
    val id: String,
    val status: String,
    val url: String,
    val downloadUrl: String,
    /** true — картинка отдаётся с водяным знаком: кампания ещё не прошла модерацию. */
    val watermarked: Boolean,
    val error: String? = null,
)

private val log = LoggerFactory.getLogger("images")

/**
 * Картинки баннеров. Без заголовков X-User/X-Role: их открывает тег `<img>` и ссылка «Скачать».
 * - `GET /api/images/{id}/status` — `PENDING | DONE | FAILED`, есть ли водяной знак.
 * - `GET /api/images/{id}` — PNG. Пока кампания не прошла модерацию — с водяным знаком (`WATERMARK_TEXT`).
 * - `GET /api/images/{id}?download=1` — то же, но файлом.
 */
fun Route.imageRoutes(images: ImageJobs?) {
    get("/images/{id}/status") {
        val id = call.parameters["id"].orEmpty()
        val status = images?.status(id) ?: throw notFound("Картинка не найдена")
        val watermarked = watermarkEnabled() && !db { imageApproved(id) }
        call.respond(
            ImageStatusResponse(id, status.status, ImageJobs.url(id), ImageJobs.url(id) + "?download=1", watermarked, status.error),
        )
    }
    get("/images/{id}") {
        val id = call.parameters["id"].orEmpty()
        val bytes = images?.bytes(id)
        if (bytes == null) {
            val status = images?.status(id) ?: throw notFound("Картинка не найдена")
            call.respond(
                HttpStatusCode.NotFound,
                ImageStatusResponse(id, status.status, ImageJobs.url(id), ImageJobs.url(id) + "?download=1", true, status.error),
            )
            return@get
        }
        val watermarked = watermarkEnabled() && !db { imageApproved(id) }
        val body =
            if (watermarked) {
                runCatching { Watermark.apply(bytes, watermarkText()) }
                    .onFailure { log.warn("watermark failed: {}", it.message) }
                    .getOrDefault(bytes)
            } else {
                bytes
            }
        if (call.request.queryParameters["download"] != null) {
            val name = "banner-${id.take(8)}${if (watermarked) "-draft" else ""}.png"
            call.response.header(
                HttpHeaders.ContentDisposition,
                ContentDisposition.Attachment.withParameter(ContentDisposition.Parameters.FileName, name).toString(),
            )
        }
        call.response.header(HttpHeaders.CacheControl, "no-cache")
        call.respondBytes(body, ContentType.Image.PNG)
    }
}

private fun watermarkEnabled(): Boolean = AppConfig.bool("WATERMARK_ENABLED") ?: true

private fun watermarkText(): String = AppConfig.get("WATERMARK_TEXT") ?: "ЧЕРНОВИК · НЕ ПРОШЛО МОДЕРАЦИЮ"
