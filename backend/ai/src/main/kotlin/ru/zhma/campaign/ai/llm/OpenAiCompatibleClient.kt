package ru.zhma.campaign.ai.llm

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
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import kotlinx.serialization.json.addJsonObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Клиент для любого OpenAI-совместимого API (GigaChat, Ollama, LM Studio, llama.cpp, vLLM и т.д.).
 *
 * [baseUrl] — корень API с префиксом `/v1`, например `http://localhost:11434/v1/` или
 * `https://gigachatai.ru/v1`. Хвостовый `/` допустим.
 * [token] — Bearer-токен; для локальных серверов без авторизации может быть null — тогда
 * заголовок Authorization не отправляется.
 */
class OpenAiCompatibleClient(
    val baseUrl: String = env("OPENAI_BASE_URL") ?: env("GIGACHAT_BASE_URL") ?: "https://gigachatai.ru/v1",
    val model: String = env("OPENAI_MODEL") ?: env("GIGACHAT_MODEL") ?: "gigachat-pro",
    val token: String? = env("OPENAI_API_KEY") ?: env("GIGACHAT_TOKEN"),
) : LlmClient {

    override suspend fun chat(system: String, user: String, temperature: Double, json: Boolean): String =
        withContext(Dispatchers.IO) {
            val body = buildJsonObject {
                put("model", model)
                putJsonArray("messages") {
                    addJsonObject { put("role", "system"); put("content", system) }
                    addJsonObject { put("role", "user"); put("content", user) }
                }
                put("temperature", temperature)
                if (json) {
                    putJsonObject("response_format") { put("type", "json_object") }
                }
            }.toString()

            val endpoint = baseUrl.trimEnd('/') + "/chat/completions"
            val conn = URL(endpoint).openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.connectTimeout = TIMEOUT_MS
            conn.readTimeout = TIMEOUT_MS
            conn.setRequestProperty("Content-Type", "application/json")
            if (!token.isNullOrBlank()) {
                conn.setRequestProperty("Authorization", "Bearer $token")
            }
            conn.doOutput = true
            conn.outputStream.use { it.write(body.toByteArray()) }

            val code = conn.responseCode
            if (code != HttpURLConnection.HTTP_OK) {
                val errBody = conn.errorStream?.bufferedReader()?.readText() ?: "HTTP $code"
                throw IllegalStateException("OpenAI-compatible API $code: $errBody")
            }
            val respText = conn.inputStream.bufferedReader().readText()
            extractContent(respText)
        }

    /** Достаёт `choices[0].message.content` из ответа; если ответа не-OpenAI — возвращает как есть. */
    private fun extractContent(respText: String): String {
        val parsed = runCatching { Json.parseToJsonElement(respText).jsonObject }.getOrNull() ?: return respText
        val choices = parsed["choices"] as? JsonArray ?: return respText
        val message = (choices.firstOrNull() as? kotlinx.serialization.json.JsonObject)?.get("message") as? JsonObject
            ?: return respText
        return (message["content"] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() } ?: respText
    }

    companion object {
        private const val TIMEOUT_MS = 20_000

        private fun env(name: String): String? = System.getenv(name)?.takeIf { it.isNotBlank() }
    }
}