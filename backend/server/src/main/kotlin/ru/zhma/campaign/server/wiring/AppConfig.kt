package ru.zhma.campaign.server.wiring

import org.slf4j.LoggerFactory
import java.io.File

/**
 * Настройки приложения. Источники по приоритету:
 * 1. переменные окружения процесса;
 * 2. файл `.env` в корне проекта: ищется от текущей папки вверх (до 4 уровней) или по пути из `APP_ENV_FILE`.
 *
 * Все ключи описаны в `.env.example` в корне репозитория.
 */
object AppConfig {
    private val log = LoggerFactory.getLogger(AppConfig::class.java)
    private val file: File? = locate()
    private val values: Map<String, String> = file?.let(::parse).orEmpty()

    init {
        val where = file?.absolutePath ?: "не найден, беру только переменные окружения"
        log.info("Файл настроек .env: {}", where)
    }

    fun get(name: String): String? = System.getenv(name)?.takeIf { it.isNotBlank() } ?: values[name]?.takeIf { it.isNotBlank() }

    fun int(
        name: String,
        default: Int,
    ): Int = get(name)?.toIntOrNull() ?: default

    fun bool(name: String): Boolean? = get(name)?.lowercase()?.toBooleanStrictOrNull()

    /** Адрес LLM: `LLM_BASE_URL` целиком или `LLM_HOST` + `LLM_PORT` + `LLM_PATH` (по умолчанию `/v1`). */
    val llmBaseUrl: String?
        get() = get("LLM_BASE_URL") ?: get("OPENAI_BASE_URL") ?: url(get("LLM_HOST"), get("LLM_PORT"), get("LLM_PATH") ?: "/v1")

    val llmModel: String?
        get() = get("LLM_MODEL") ?: get("OPENAI_MODEL")

    val llmApiKey: String?
        get() = get("LLM_API_KEY") ?: get("OPENAI_API_KEY")

    /** Адрес генерации картинок; если не задан — тот же host:port, что у LLM. */
    val imageBaseUrl: String?
        get() {
            get("IMAGE_BASE_URL")?.let { return it }
            val host = get("IMAGE_HOST") ?: get("LLM_HOST")
            val port = get("IMAGE_PORT") ?: get("LLM_PORT")
            val path = get("IMAGE_PATH") ?: get("LLM_PATH") ?: "/v1"
            return url(host, port, path) ?: llmBaseUrl
        }

    val backendHost: String
        get() = get("BACKEND_HOST") ?: "0.0.0.0"

    val backendPort: Int
        get() = int("BACKEND_PORT", 8080)

    val frontendPort: Int
        get() = int("FRONTEND_PORT", 5173)

    private fun url(
        host: String?,
        port: String?,
        path: String,
    ): String? {
        if (host.isNullOrBlank()) return null
        val withScheme = if (host.startsWith("http://") || host.startsWith("https://")) host.trimEnd('/') else "http://$host"
        val withPort = if (port.isNullOrBlank()) withScheme else "$withScheme:$port"
        return withPort + "/" + path.trim('/').let { if (it.isEmpty()) "" else it }
    }

    private fun locate(): File? {
        val property = System.getProperty("app.env.file")
        if (property == "none") return null
        property?.let { path -> File(path).takeIf { it.isFile }?.let { return it } }
        System.getenv("APP_ENV_FILE")?.let { path -> File(path).takeIf { it.isFile }?.let { return it } }
        var dir: File? = File("").absoluteFile
        repeat(5) {
            val candidate = File(dir, ".env")
            if (candidate.isFile) return candidate
            dir = dir?.parentFile
        }
        return null
    }

    private fun parse(file: File): Map<String, String> =
        file
            .readLines(Charsets.UTF_8)
            .map { it.trim().removePrefix("") }
            .filter { it.isNotEmpty() && !it.startsWith("#") && it.contains('=') }
            .associate { line ->
                val key =
                    line
                        .substringBefore('=')
                        .trim()
                        .removePrefix("export ")
                        .trim()
                var value = line.substringAfter('=').trim()
                if (value.length >= 2 && (value.first() == '"' && value.last() == '"' || value.first() == '\'' && value.last() == '\'')) {
                    value = value.substring(1, value.length - 1)
                } else {
                    value = value.substringBefore(" #").trim()
                }
                key to value
            }
}
