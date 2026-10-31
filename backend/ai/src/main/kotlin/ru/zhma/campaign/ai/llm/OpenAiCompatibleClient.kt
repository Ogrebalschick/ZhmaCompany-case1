package ru.zhma.campaign.ai.llm

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import java.net.HttpURLConnection
import java.net.URI

/**
 * Клиент для любого OpenAI-совместимого API (llama.cpp, vLLM, Ollama, LM Studio, GigaChat-прокси).
 *
 * [baseUrl] — корень API с `/v1`, например `http://100.104.60.3:9292/v1`.
 * [token] — Bearer-токен; пустой — заголовок Authorization не отправляется.
 *
 * Переменные окружения:
 * - `LLM_DISABLE_THINKING` (по умолчанию `true`) — просит Qwen3 не рассуждать (`chat_template_kwargs.enable_thinking=false`).
 * - `LLM_MAX_TOKENS` (по умолчанию 1200), `LLM_TIMEOUT_SEC` (по умолчанию 60).
 * - `LLM_TOOLS` (по умолчанию `true`) — разрешить вызов инструментов. Для llama.cpp сервер нужно запускать с `--jinja`.
 */
class OpenAiCompatibleClient(
    val baseUrl: String = env("OPENAI_BASE_URL") ?: env("GIGACHAT_BASE_URL") ?: "https://gigachatai.ru/v1",
    val model: String = env("OPENAI_MODEL") ?: env("GIGACHAT_MODEL") ?: "gigachat-pro",
    val token: String? = env("OPENAI_API_KEY") ?: env("GIGACHAT_TOKEN"),
    private val disableThinking: Boolean = env("LLM_DISABLE_THINKING")?.toBooleanStrictOrNull() ?: true,
    private val maxTokens: Int = env("LLM_MAX_TOKENS")?.toIntOrNull() ?: 1200,
    private val timeoutMs: Int = (env("LLM_TIMEOUT_SEC")?.toIntOrNull() ?: 60) * 1000,
    private val tools: Boolean = env("LLM_TOOLS")?.toBooleanStrictOrNull() ?: true,
) : LlmClient {
    override val supportsTools: Boolean
        get() = tools

    override suspend fun chat(
        system: String,
        user: String,
        temperature: Double,
        json: Boolean,
    ): String {
        val body =
            buildJsonObject {
                common(temperature)
                putJsonArray("messages") {
                    addJsonObject {
                        put("role", "system")
                        put("content", system)
                    }
                    addJsonObject {
                        put("role", "user")
                        put("content", user)
                    }
                }
                if (json) putJsonObject("response_format") { put("type", "json_object") }
            }
        val message = post(body) ?: return ""
        return stripThinking((message["content"] as? JsonPrimitive)?.contentOrNull.orEmpty())
    }

    override suspend fun chatWithTools(
        messages: List<LlmMessage>,
        tools: List<ToolSpec>,
        temperature: Double,
    ): LlmToolReply {
        val body =
            buildJsonObject {
                common(temperature)
                putJsonArray("messages") {
                    messages.forEach { m ->
                        addJsonObject {
                            put("role", m.role)
                            if (m.content != null) put("content", m.content) else put("content", "")
                            if (m.toolCallId != null) put("tool_call_id", m.toolCallId)
                            if (m.toolCalls.isNotEmpty()) {
                                putJsonArray("tool_calls") {
                                    m.toolCalls.forEach { call ->
                                        addJsonObject {
                                            put("id", call.id)
                                            put("type", "function")
                                            putJsonObject("function") {
                                                put("name", call.name)
                                                put("arguments", call.arguments)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                putJsonArray("tools") {
                    tools.forEach { tool ->
                        addJsonObject {
                            put("type", "function")
                            putJsonObject("function") {
                                put("name", tool.name)
                                put("description", tool.description)
                                put("parameters", tool.parameters)
                            }
                        }
                    }
                }
                put("tool_choice", "auto")
            }
        val message = post(body) ?: return LlmToolReply(null, emptyList())
        val content = (message["content"] as? JsonPrimitive)?.contentOrNull?.let { stripThinking(it) }
        val calls =
            (message["tool_calls"] as? JsonArray).orEmpty().mapIndexedNotNull { index, element ->
                val obj = element as? JsonObject ?: return@mapIndexedNotNull null
                val fn = obj["function"] as? JsonObject ?: return@mapIndexedNotNull null
                val name = (fn["name"] as? JsonPrimitive)?.contentOrNull ?: return@mapIndexedNotNull null
                val args =
                    when (val raw = fn["arguments"]) {
                        is JsonPrimitive -> raw.contentOrNull ?: "{}"
                        is JsonObject -> raw.toString()
                        else -> "{}"
                    }
                ToolCall((obj["id"] as? JsonPrimitive)?.contentOrNull ?: "call_$index", name, args)
            }
        return LlmToolReply(content?.takeIf { it.isNotBlank() }, calls)
    }

    private fun kotlinx.serialization.json.JsonObjectBuilder.common(temperature: Double) {
        put("model", model)
        put("temperature", temperature)
        put("max_tokens", maxTokens)
        if (disableThinking) {
            putJsonObject("chat_template_kwargs") { put("enable_thinking", false) }
        }
    }

    /** Отправляет запрос и возвращает `choices[0].message`. */
    private suspend fun post(body: JsonObject): JsonObject? =
        withContext(Dispatchers.IO) {
            val endpoint = baseUrl.trimEnd('/') + "/chat/completions"
            val conn = URI.create(endpoint).toURL().openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.connectTimeout = timeoutMs
            conn.readTimeout = timeoutMs
            conn.setRequestProperty("Content-Type", "application/json")
            if (!token.isNullOrBlank()) conn.setRequestProperty("Authorization", "Bearer $token")
            conn.doOutput = true
            conn.outputStream.use { it.write(body.toString().toByteArray()) }
            val code = conn.responseCode
            if (code != HttpURLConnection.HTTP_OK) {
                val errBody = conn.errorStream?.bufferedReader()?.readText() ?: "HTTP $code"
                throw IllegalStateException("LLM API $code: ${errBody.take(500)}")
            }
            val respText = conn.inputStream.bufferedReader().readText()
            val parsed = runCatching { Json.parseToJsonElement(respText).jsonObject }.getOrNull() ?: return@withContext null
            val choices = parsed["choices"] as? JsonArray ?: return@withContext null
            (choices.firstOrNull() as? JsonObject)?.get("message") as? JsonObject
        }

    companion object {
        private fun env(name: String): String? = System.getenv(name)?.takeIf { it.isNotBlank() }
    }
}
