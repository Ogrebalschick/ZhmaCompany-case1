package ru.zhma.campaign.ai.llm

/**
 * Клиент GigaChat — обёртка над [OpenAiCompatibleClient].
 *
 * Токен передаётся в конструктор (обычно из `GIGACHAT_TOKEN`). Базовый URL и
 * модель читаются из переменных окружения:
 * - `GIGACHAT_BASE_URL` (по умолчанию `https://gigachatai.ru/v1`)
 * - `GIGACHAT_MODEL` (по умолчанию `gigachat-pro`)
 *
 * Если токен пустой — [chat] бросает понятное исключение, не подменяясь моком.
 * Промпты в клиент не вшиваются.
 */
class GigaChatClient(
    private val token: String,
) : LlmClient {
    private val delegate =
        OpenAiCompatibleClient(
            baseUrl = System.getenv("GIGACHAT_BASE_URL") ?: "https://gigachatai.ru/v1",
            model = System.getenv("GIGACHAT_MODEL") ?: "gigachat-pro",
            token = token,
            disableThinking = false,
            tools = false,
        )

    override suspend fun chat(
        system: String,
        user: String,
        temperature: Double,
        json: Boolean,
    ): String {
        if (token.isBlank()) {
            throw IllegalStateException("GigaChat не настроен: GIGACHAT_TOKEN не задан")
        }
        return delegate.chat(system, user, temperature, json)
    }
}
