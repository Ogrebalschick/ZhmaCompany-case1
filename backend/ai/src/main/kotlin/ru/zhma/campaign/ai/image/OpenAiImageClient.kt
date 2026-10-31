package ru.zhma.campaign.ai.image

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import java.net.HttpURLConnection
import java.net.URI
import java.util.Base64

/**
 * Генерация картинок через HTTP.
 *
 * `api = "openai"` (по умолчанию) — `POST {baseUrl}/images/generations`, как у OpenAI, llama-swap и stable-diffusion.cpp:
 * `{model, prompt, n, size, response_format: "b64_json"}` → `data[0].b64_json` или `data[0].url`.
 * `api = "sdapi"` — `POST {host}/sdapi/v1/txt2img` в стиле Automatic1111 / sd.cpp → `images[0]` в base64.
 */
class OpenAiImageClient(
    private val baseUrl: String,
    private val model: String?,
    private val token: String? = null,
    private val api: String = "openai",
    private val steps: Int? = null,
    private val timeoutMs: Int = 300_000,
) : ImageGenClient {
    override suspend fun generate(
        prompt: String,
        w: Int,
        h: Int,
    ): ByteArray =
        withContext(Dispatchers.IO) {
            if (api.equals("sdapi", ignoreCase = true)) sdapi(prompt, w, h) else openai(prompt, w, h)
        }

    private fun openai(
        prompt: String,
        w: Int,
        h: Int,
    ): ByteArray {
        val body =
            buildJsonObject {
                if (!model.isNullOrBlank()) put("model", model)
                put("prompt", prompt)
                put("n", 1)
                put("size", "${w}x$h")
                put("response_format", "b64_json")
                steps?.let { put("steps", it) }
            }
        val resp = post(baseUrl.trimEnd('/') + "/images/generations", body)
        val first =
            (resp["data"] as? JsonArray)?.firstOrNull() as? JsonObject ?: error("В ответе нет data[0]: ${resp.toString().take(200)}")
        (first["b64_json"] as? JsonPrimitive)?.contentOrNull?.let { return decode(it) }
        (first["url"] as? JsonPrimitive)?.contentOrNull?.let { url ->
            if (url.startsWith("data:")) return decode(url.substringAfter(","))
            return download(url)
        }
        error("В ответе нет b64_json и url")
    }

    private fun sdapi(
        prompt: String,
        w: Int,
        h: Int,
    ): ByteArray {
        val host = baseUrl.trimEnd('/').removeSuffix("/v1")
        val body =
            buildJsonObject {
                put("prompt", prompt)
                put("width", w)
                put("height", h)
                steps?.let { put("steps", it) }
            }
        val resp = post("$host/sdapi/v1/txt2img", body)
        val image = ((resp["images"] as? JsonArray)?.firstOrNull() as? JsonPrimitive)?.contentOrNull ?: error("В ответе нет images[0]")
        return decode(image)
    }

    private fun post(
        url: String,
        body: JsonObject,
    ): JsonObject {
        val conn = URI.create(url).toURL().openConnection() as HttpURLConnection
        conn.requestMethod = "POST"
        conn.connectTimeout = 15_000
        conn.readTimeout = timeoutMs
        conn.setRequestProperty("Content-Type", "application/json")
        if (!token.isNullOrBlank()) conn.setRequestProperty("Authorization", "Bearer $token")
        conn.doOutput = true
        conn.outputStream.use { it.write(body.toString().toByteArray()) }
        val code = conn.responseCode
        if (code !in 200..299) {
            val err = conn.errorStream?.bufferedReader()?.readText() ?: ""
            error("Image API $code: ${err.take(300)}")
        }
        return Json.parseToJsonElement(conn.inputStream.bufferedReader().readText()).jsonObject
    }

    private fun download(url: String): ByteArray {
        val absolute = if (url.startsWith("http")) url else URI.create(baseUrl).resolve(url).toString()
        val conn = URI.create(absolute).toURL().openConnection() as HttpURLConnection
        conn.connectTimeout = 15_000
        conn.readTimeout = 60_000
        return conn.inputStream.use { it.readBytes() }
    }

    private fun decode(b64: String): ByteArray = Base64.getMimeDecoder().decode(b64.substringAfter("base64,"))
}
