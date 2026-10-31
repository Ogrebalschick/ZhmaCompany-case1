package ru.zhma.campaign.server.images

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withTimeout
import org.slf4j.LoggerFactory
import ru.zhma.campaign.ai.image.ImageGenClient
import ru.zhma.campaign.ai.image.ImageRequests
import ru.zhma.campaign.ai.image.ImageTicket
import java.io.File
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Duration.Companion.seconds

/**
 * Очередь генерации картинок. Картинки рисуются по одной (одна видеокарта), лежат файлами в `dir`.
 * Статус живёт в памяти; после перезапуска готовая картинка находится по файлу.
 */
class ImageJobs(
    private val client: ImageGenClient,
    private val dir: File,
    private val scope: CoroutineScope,
    private val timeoutSec: Long = 300,
) : ImageRequests {
    data class Status(
        val status: String,
        val error: String? = null,
        val prompt: String? = null,
    )

    private val statuses = ConcurrentHashMap<String, Status>()
    private val gpu = Semaphore(1)
    private val log = LoggerFactory.getLogger(ImageJobs::class.java)

    init {
        dir.mkdirs()
    }

    override fun enqueue(
        prompt: String,
        width: Int,
        height: Int,
    ): ImageTicket {
        val id = UUID.randomUUID().toString()
        statuses[id] = Status(PENDING, prompt = prompt)
        scope.launch {
            try {
                val bytes =
                    gpu.withPermit {
                        withTimeout(timeoutSec.seconds) { client.generate(prompt, width, height) }
                    }
                file(id).writeBytes(bytes)
                statuses[id] = Status(DONE, prompt = prompt)
            } catch (e: Throwable) {
                if (e is kotlinx.coroutines.CancellationException && e !is kotlinx.coroutines.TimeoutCancellationException) throw e
                log.warn("image {} failed: {}", id, e.message)
                statuses[id] = Status(FAILED, error = (e.message ?: e::class.simpleName ?: "ошибка").take(300), prompt = prompt)
            }
        }
        return ImageTicket(id, url(id), PENDING)
    }

    fun status(id: String): Status? {
        if (!SAFE_ID.matches(id)) return null
        return statuses[id] ?: if (file(id).isFile) Status(DONE) else null
    }

    fun bytes(id: String): ByteArray? = if (SAFE_ID.matches(id) && file(id).isFile) file(id).readBytes() else null

    private fun file(id: String) = File(dir, "$id.png")

    companion object {
        const val PENDING = "PENDING"
        const val DONE = "DONE"
        const val FAILED = "FAILED"
        private val SAFE_ID = Regex("""^[0-9a-f-]{36}$""")

        fun url(id: String) = "/api/images/$id"
    }
}
