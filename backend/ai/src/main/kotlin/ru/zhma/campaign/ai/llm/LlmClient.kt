package ru.zhma.campaign.ai.llm

import kotlinx.serialization.json.JsonObject

interface LlmClient {
    suspend fun chat(
        system: String,
        user: String,
        temperature: Double,
        json: Boolean = false,
    ): String

    /** Умеет ли клиент вызов инструментов (OpenAI `tools`). Если нет — второй этап ведёт автомат. */
    val supportsTools: Boolean
        get() = false

    /** Один шаг диалога с инструментами: модель отвечает текстом или просит вызвать инструменты. */
    suspend fun chatWithTools(
        messages: List<LlmMessage>,
        tools: List<ToolSpec>,
        temperature: Double,
    ): LlmToolReply = throw UnsupportedOperationException("Клиент не поддерживает инструменты")
}

data class LlmMessage(
    val role: String,
    val content: String?,
    val toolCalls: List<ToolCall> = emptyList(),
    val toolCallId: String? = null,
)

data class ToolCall(
    val id: String,
    val name: String,
    val arguments: String,
)

data class ToolSpec(
    val name: String,
    val description: String,
    val parameters: JsonObject,
)

data class LlmToolReply(
    val content: String?,
    val toolCalls: List<ToolCall>,
)

/** Убирает рассуждения `<think>…</think>`, которые иногда отдают Qwen-модели. */
fun stripThinking(raw: String): String {
    var s = raw.replace(Regex("""(?s)<think>.*?</think>"""), "")
    if (s.contains("</think>")) s = s.substringAfterLast("</think>")
    return s.trim()
}
