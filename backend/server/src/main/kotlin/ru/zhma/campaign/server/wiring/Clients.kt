package ru.zhma.campaign.server.wiring

import ru.zhma.campaign.ai.image.ImageGenClient
import ru.zhma.campaign.ai.image.OpenAiImageClient
import ru.zhma.campaign.ai.llm.LlmClient
import ru.zhma.campaign.ai.llm.MockLlmClient
import ru.zhma.campaign.ai.llm.OpenAiCompatibleClient

/** Живая модель, если заданы адрес и модель (см. `.env`). Иначе — заглушка без сети. */
fun llmClient(): LlmClient {
    val baseUrl = AppConfig.llmBaseUrl
    val model = AppConfig.llmModel
    if (baseUrl == null || model == null || AppConfig.bool("LLM_MOCK") == true) return MockLlmClient()
    val client =
        OpenAiCompatibleClient(
            baseUrl = baseUrl,
            model = model,
            token = AppConfig.llmApiKey,
            disableThinking = AppConfig.bool("LLM_DISABLE_THINKING") ?: true,
            maxTokens = AppConfig.int("LLM_MAX_TOKENS", 1200),
            timeoutMs = AppConfig.int("LLM_TIMEOUT_SEC", 120) * 1000,
            tools = AppConfig.bool("LLM_TOOLS") ?: true,
        )
    if (AppConfig.bool("LLM_LOG") == false) return client
    return LoggingLlmClient(client, llmLogFile)
}

/** Журнал запросов к модели — рядом с базой. */
var llmLogFile: java.io.File = java.io.File("data/llm.log")

/** Генерация картинок: адрес `IMAGE_*` или тот же host:port, что у LLM. `IMAGE_ENABLED=false` выключает. */
fun imageClient(): ImageGenClient? {
    if (AppConfig.bool("IMAGE_ENABLED") == false) return null
    val baseUrl = AppConfig.imageBaseUrl ?: return null
    return OpenAiImageClient(
        baseUrl = baseUrl,
        model = AppConfig.get("IMAGE_MODEL") ?: "qwen-image-2.1",
        token = AppConfig.get("IMAGE_API_KEY") ?: AppConfig.llmApiKey,
        api = AppConfig.get("IMAGE_API") ?: "openai",
        steps = AppConfig.get("IMAGE_STEPS")?.toIntOrNull(),
        timeoutMs = imageTimeoutSec().toInt() * 1000,
    )
}

fun imageTimeoutSec(): Long = AppConfig.get("IMAGE_TIMEOUT_SEC")?.toLongOrNull() ?: 1200
