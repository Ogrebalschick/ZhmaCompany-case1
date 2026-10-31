# Агент 3. LLM, промпты, картинка-заглушка

Ты прячешь модель за интерфейсом и пишешь три короткие инструкции. Автомат, HTTP и таблицы не твои.

Работай параллельно с тремя другими агентами. `case-readonly/` не меняй.

## Твои пути

- `backend/ai/src/main/kotlin/ru/zhma/campaign/ai/llm/**`
- `backend/ai/src/main/kotlin/ru/zhma/campaign/ai/prompt/**`
- `backend/ai/src/main/kotlin/ru/zhma/campaign/ai/image/**`
- `backend/ai/src/test/kotlin/ru/zhma/campaign/ai/llm/**`
- `backend/ai/src/test/kotlin/ru/zhma/campaign/ai/prompt/**`

## Чужие пути

- `backend/ai/src/main/kotlin/ru/zhma/campaign/ai/model/**`
- `backend/ai/src/main/kotlin/ru/zhma/campaign/ai/agent/**`
- `backend/server/**`, Gradle-файлы, `frontend/**`, корневые `prompt-*.md`

Модели (`DraftPatch`, `ParsedIntent`, `BannerDraft`) пишет агент 1 в `ru.zhma.campaign.ai.model`. Сериализуй их оттуда. Если на старте файлов ещё нет, всё равно используй эти имена классов и пакет, свои DTO не заводи.

## Что написать

```kotlin
package ru.zhma.campaign.ai.llm

interface LlmClient {
    suspend fun chat(system: String, user: String, temperature: Double, json: Boolean = false): String
}

class StructuredLlm(private val llm: LlmClient) {
    suspend fun <T> ask(
        system: String,
        user: String,
        serializer: KSerializer<T>,
        validate: (T) -> String? = { null },
        retries: Int = 3,
    ): T
}

class MockLlmClient : LlmClient

class GigaChatClient(private val token: String) : LlmClient
```

```kotlin
package ru.zhma.campaign.ai.prompt

object Prompts {
    fun intent(today: String, catalog: String, draftJson: String): String
    fun askMissing(missing: String, options: String): String
    fun bannerCopy(product: String, audience: String, placement: String, n: Int): String
}
```

```kotlin
package ru.zhma.campaign.ai.image

interface ImageGenClient {
    suspend fun generate(prompt: String, w: Int, h: Int): ByteArray
}

class PlaceholderImageGen : ImageGenClient
```

`PlaceholderImageGen` возвращает маленький валидный PNG одного цвета. Сеть не использует. В автомат его не подключай.

## StructuredLlm

До `retries` попыток. Первая температура 0.2, следующие 0.0. С ответа снимай обёртку `` ```json `` и `` ``` ``. Успех: JSON парсится сериализатором и `validate` вернул null. Иначе допиши в user текст «Предыдущий ответ невалиден: … Верни только валидный JSON по схеме.» После последней неудачи брось исключение с числом попыток. Внутри `ask` не ходи в базу и не читай файлы проекта.

## MockLlmClient

Нужен, чтобы демо шло без ключа. Если `json == true` и system похож на разбор намерения — верни JSON `ParsedIntent`. Если system похож на копирайт баннера — верни JSON-массив из одного объекта `{headline, body, cta, image_prompt_en}` с короткими строками в лимитах (заголовок ≤ 40, текст ≤ 90, cta ≤ 20). Иначе верни одну деловую фразу без эмодзи.

Правила мока намерения, без выдуманных дат и бюджетов:

- «список», «покажи кампании» → `LIST_CAMPAIGNS`, пустой patch
- реплика ровно «подтверждаю» / «сохрани» / «да» → `CONFIRM`, `confirm: true`
- «отмена» / «не надо» → `CANCEL`, `confirm: false`
- «баннер», «креатив», «сторис» → `GENERATE_CREATIVE`; в `creativeBrief` положи исходный текст; если сказано «сторис» — это сигнал автомату, в патч отдельное поле типа не клади
- иначе `CAMPAIGN_EDIT` и заполни только явно найденное:
  - город: Москва → сегмент `CITY_MSK`, Санкт-Петербург / СПб → `CITY_SPB`
  - возраст 26–45 → `AGE_26_45`
  - «все пользователи» → `audienceType=ALL_USERS`
  - «вклад» как продукт → `topic=SALES` только если слово «акция» или «продаж» есть; иначе topic не ставь
  - «главн» → `placeIds: [1]` (`mainScreenStories`)
  - дата `YYYY-MM-DD` и время `HH:mm` копируй как есть
  - название в кавычках «...» → `name`
- поле, которого нет в реплике, оставь null. Число веса бери только из явной цифры.

Мок может ошибаться на свободном тексте. Это нормально: автомат всё равно валидирует.

## GigaChatClient

Каркас без обязательного живого вызова в тестах. Базовый URL и модель читай из переменных `GIGACHAT_TOKEN`, `GIGACHAT_MODEL`. Метод `chat` собирает system+user. Если токена нет — бросай понятное исключение «GigaChat не настроен», не подменяй себя моком молча. Таймаут HTTP 20 секунд. Промпты внутрь клиента не вшивай.

## Тексты Prompts

`intent` — system-инструкция. Вставь `today`, `catalog`, `draftJson`. Схема ответа ровно такая:

```json
{"intent":"CAMPAIGN_EDIT|CONFIRM|CANCEL|GENERATE_CREATIVE|LIST_CAMPAIGNS|OTHER","patch":{"name":null,"marketingName":null,"topic":null,"tagIds":null,"keywords":null,"audienceType":null,"includedSegments":null,"excludedSegments":null,"segmentQuery":null,"placeIds":null,"placeQuery":null,"tagQuery":null,"startDate":null,"startTime":null,"endDate":null,"endTime":null,"weight":null,"initialCtr":null,"creativeBrief":null},"confirm":null}
```

Правила внутри промпта:

- только JSON, без пояснений
- заполняй patch лишь тем, что явно сказано
- `topic`, коды сегментов и id мест бери только из каталога внизу промпта; если записи нет — id null, текст в `segmentQuery` / `placeQuery` / `tagQuery`
- даты вида «на 2 недели» не считай сам, оставь null (их считает автомат)
- не выдумывай вес, CTR, время и название

`askMissing`: 1–2 деловых предложения, спросить только про переданные поля, варианты перечислить если они переданы, без эмодзи и без новых вопросов.

`bannerCopy`: вернуть только JSON-массив из `n` объектов `headline` (≤ 40), `body` (≤ 90), `cta` (≤ 20), `image_prompt_en`. Запрет: гарантия одобрения, «лучший на рынке», проценты и суммы, которых нет во входе, капс и восклицательные знаки.

## Тесты

Без сети:

- `StructuredLlm` принимает JSON вFence и отвергает мусор до третьей попытки, на третьей валидной отдаёт объект
- `MockLlmClient` на фразах: полный бриф с Москвой и возрастом 26–45; «покажи кампании»; «подтверждаю»; «отмена»; «сделай сторис»; реплика без дат не содержит `startDate` в JSON
- `PlaceholderImageGen` отдаёт байты с сигнатурой PNG

Набор из этих фраз держи в тесте списком, чтобы его было видно на ревью.

## Критерий готовности

Пакеты `llm`, `prompt`, `image` компилируются против моделей агента 1. Тесты мока зелёные без ключа API. `AgentTurn`, роуты и таблицы этим промптом не создаются.
