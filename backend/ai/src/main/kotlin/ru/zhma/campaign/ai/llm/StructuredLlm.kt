package ru.zhma.campaign.ai.llm

import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json

class StructuredLlm(
    private val llm: LlmClient,
) {
    private val json =
        Json {
            ignoreUnknownKeys = true
        }

    suspend fun <T> ask(
        system: String,
        user: String,
        serializer: KSerializer<T>,
        validate: (T) -> String? = { null },
        retries: Int = 3,
    ): T {
        var lastError: String? = null

        for (attempt in 1..retries) {
            val temperature = if (attempt == 1) 0.2 else 0.0
            val currentUser =
                if (lastError == null) {
                    user
                } else {
                    "$user\nПредыдущий ответ невалиден: $lastError Верни только валидный JSON по схеме."
                }

            try {
                val raw = llm.chat(system, currentUser, temperature, json = true)
                val cleaned = stripFence(raw)
                val parsed: T = json.decodeFromString(serializer, cleaned)
                val error = validate(parsed)
                if (error == null) {
                    return parsed
                }
                lastError = error
            } catch (e: Exception) {
                lastError = e.message ?: "parse error"
            }
        }
        throw IllegalStateException("StructuredLlm failed after $retries attempts. Last error: $lastError")
    }

    private fun stripFence(raw: String): String {
        var s = raw.trim()
        if (s.startsWith("```json")) {
            s = s.substringAfter("```json").trim()
        } else if (s.startsWith("```")) {
            s = s.substringAfter("```").trim()
        }
        if (s.endsWith("```")) {
            s = s.substringBeforeLast("```").trim()
        }
        return s
    }
}
