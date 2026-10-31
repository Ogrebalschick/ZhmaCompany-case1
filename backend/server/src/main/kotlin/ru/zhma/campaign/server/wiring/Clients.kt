package ru.zhma.campaign.server.wiring

import ru.zhma.campaign.ai.llm.LlmClient
import ru.zhma.campaign.ai.llm.MockLlmClient
import ru.zhma.campaign.ai.llm.OpenAiCompatibleClient

fun llmClient(): LlmClient {
    val baseUrl = env("OPENAI_BASE_URL")
    val model = env("OPENAI_MODEL")
    if (baseUrl == null || model == null) return MockLlmClient()
    return OpenAiCompatibleClient(
        baseUrl = baseUrl,
        model = model,
        token = env("OPENAI_API_KEY"),
    )
}

private fun env(name: String): String? = System.getenv(name)?.takeIf { it.isNotBlank() }
