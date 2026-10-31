package ru.zhma.campaign.ai.llm

import kotlinx.serialization.KSerializer
import ru.zhma.campaign.ai.model.AppJson

class StructuredLlm(
    private val llm: LlmClient,
) {
    suspend fun <T> ask(
        system: String,
        user: String,
        serializer: KSerializer<T>,
        validate: (T) -> String? = { null },
        retries: Int = 3,
    ): T {
        var feedback = ""
        repeat(retries) { attempt ->
            val raw =
                llm.chat(
                    system,
                    user + feedback,
                    temperature = if (attempt == 0) 0.2 else 0.0,
                    json = true,
                )
            val clean = raw.removePrefix("```json").removeSuffix("```").trim()
            val decoded = runCatching { AppJson.decodeFromString(serializer, clean) }
            val value = decoded.getOrNull()
            if (value != null) {
                val problem = validate(value)
                if (problem == null) return value
                feedback = invalid(problem)
            } else {
                feedback = invalid(decoded.exceptionOrNull()?.message ?: "невалидный JSON")
            }
        }
        error("LLM не вернула валидный JSON за $retries попыток")
    }

    private fun invalid(reason: String): String = "\n\nПредыдущий ответ невалиден: $reason. Верни только валидный JSON по схеме."
}
