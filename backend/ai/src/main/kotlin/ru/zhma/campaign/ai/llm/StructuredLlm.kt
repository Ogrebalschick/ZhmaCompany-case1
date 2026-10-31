package ru.zhma.campaign.ai.llm

import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json

/** Просит у модели JSON по схеме, вытаскивает его из ответа и повторяет запрос, если JSON кривой. */
class StructuredLlm(
    private val llm: LlmClient,
) {
    private val json =
        Json {
            ignoreUnknownKeys = true
            isLenient = true
            coerceInputValues = true
        }

    suspend fun <T> ask(
        system: String,
        user: String,
        serializer: KSerializer<T>,
        validate: (T) -> String? = { null },
        retries: Int = 2,
    ): T {
        var lastError: String? = null

        for (attempt in 1..retries) {
            val temperature = if (attempt == 1) 0.1 else 0.0
            val currentUser =
                if (lastError == null) {
                    user
                } else {
                    "$user\n\n(Предыдущий ответ невалиден: $lastError. Верни только валидный JSON по схеме.)"
                }

            try {
                val raw = llm.chat(system, currentUser, temperature, json = true)
                val cleaned = extractJson(raw)
                val parsed: T = json.decodeFromString(serializer, cleaned)
                val error = validate(parsed)
                if (error == null) return parsed
                lastError = error
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                lastError = (e.message ?: "parse error").take(200)
            }
        }
        throw IllegalStateException("StructuredLlm failed after $retries attempts. Last error: $lastError")
    }

    companion object {
        /** Достаёт первый целый JSON-объект или массив из ответа: убирает рассуждения, ```-ограды и текст вокруг. */
        fun extractJson(raw: String): String {
            val s = stripThinking(raw).replace("```json", "```").replace("```", " ").trim()
            val start = s.indexOfFirst { it == '{' || it == '[' }
            if (start < 0) return s
            val open = s[start]
            val close = if (open == '{') '}' else ']'
            var depth = 0
            var inString = false
            var escape = false
            for (i in start until s.length) {
                val c = s[i]
                if (inString) {
                    when {
                        escape -> escape = false
                        c == '\\' -> escape = true
                        c == '"' -> inString = false
                    }
                    continue
                }
                when (c) {
                    '"' -> inString = true
                    open -> depth++
                    close -> {
                        depth--
                        if (depth == 0) return s.substring(start, i + 1)
                    }
                }
            }
            return s.substring(start)
        }
    }
}
