package ru.zhma.campaign.ai.llm

class MockLlmClient : LlmClient {
    override suspend fun chat(
        system: String,
        user: String,
        temperature: Double,
        json: Boolean,
    ): String = "Принято."
}
