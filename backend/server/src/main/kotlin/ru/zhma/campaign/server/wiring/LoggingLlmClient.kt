package ru.zhma.campaign.server.wiring

import ru.zhma.campaign.ai.llm.LlmClient
import ru.zhma.campaign.ai.llm.LlmMessage
import ru.zhma.campaign.ai.llm.LlmToolReply
import ru.zhma.campaign.ai.llm.ToolSpec
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * Журнал запросов к модели: что спросили, что ответила, сколько думала. Пишет в `data/llm.log`.
 * Нужен, чтобы настраивать промпты на живой модели. Выключается `LLM_LOG=false`.
 */
class LoggingLlmClient(
    private val delegate: LlmClient,
    private val file: File,
) : LlmClient {
    override val supportsTools: Boolean
        get() = delegate.supportsTools

    override suspend fun chat(
        system: String,
        user: String,
        temperature: Double,
        json: Boolean,
    ): String {
        val started = System.currentTimeMillis()
        return try {
            delegate.chat(system, user, temperature, json).also { write("chat", started, system.lineSequence().first(), user, it) }
        } catch (e: Exception) {
            write("chat", started, system.lineSequence().first(), user, "ОШИБКА: ${e.message}")
            throw e
        }
    }

    override suspend fun chatWithTools(
        messages: List<LlmMessage>,
        tools: List<ToolSpec>,
        temperature: Double,
    ): LlmToolReply {
        val started = System.currentTimeMillis()
        val last = messages.lastOrNull()?.let { "${it.role}: ${it.content.orEmpty()}" }.orEmpty()
        return try {
            delegate.chatWithTools(messages, tools, temperature).also { reply ->
                val out = reply.toolCalls.joinToString("\n") { "→ ${it.name}(${it.arguments})" } + (reply.content?.let { "\n$it" } ?: "")
                write("tools", started, "шаг диалога, сообщений: ${messages.size}", last, out)
            }
        } catch (e: Exception) {
            write("tools", started, "шаг диалога", last, "ОШИБКА: ${e.message}")
            throw e
        }
    }

    @Synchronized
    private fun write(
        kind: String,
        started: Long,
        what: String,
        input: String,
        output: String,
    ) {
        runCatching {
            file.parentFile?.mkdirs()
            val ms = System.currentTimeMillis() - started
            file.appendText(
                "=== ${LocalDateTime.now().format(TIME)} | $kind | $ms мс | ${what.take(120)}\n" +
                    ">>> ${input.take(2000)}\n<<< ${output.take(4000)}\n\n",
            )
        }
    }

    private companion object {
        val TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
    }
}
