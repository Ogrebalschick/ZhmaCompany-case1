package ru.zhma.campaign.ai.llm

interface LlmClient {
    suspend fun chat(system: String, user: String, temperature: Double, json: Boolean = false): String
}

class MockLlmClient : LlmClient {
    override suspend fun chat(system: String, user: String, temperature: Double, json: Boolean): String = "Принято."
}
