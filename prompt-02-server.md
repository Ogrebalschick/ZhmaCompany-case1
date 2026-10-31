# Агент 2. Сервер (`:server` + сборка)

Ты поднимаешь HTTP, SQLite и исполнение уже принятых решений автомата. Разбор реплики и тексты промптов не пиши.

Работай параллельно с тремя другими агентами. `case-readonly/` не меняй.

## Твои пути

- `backend/settings.gradle.kts`
- `backend/build.gradle.kts`
- `backend/gradlew`, wrapper
- `backend/ai/build.gradle.kts` — только зависимости модуля, без исходников чужих пакетов
- `backend/server/**`
- `README.md` в корне — как запустить сервер и фронт, заголовки, список ручек

## Чужие пути

- `backend/ai/src/**` (его пишут агенты 1 и 3)
- `frontend/**`
- корневые `prompt-*.md`

Исходники `:ai` появятся в пакетах `ru.zhma.campaign.ai.model`, `.agent`, `.llm`, `.prompt`, `.image`. Сервер вызывает их, не дублирует модели.

Ожидай класс:

```kotlin
package ru.zhma.campaign.ai.agent

class AgentTurn(private val llm: LlmClient) {
    suspend fun handle(input: TurnInput): TurnResult
}
```

`TurnResult`: `reply: AssistantReply`, `draft: DraftCampaign`, `commit: CommitRequest?`.
`CommitRequest.action`: `SAVE_CAMPAIGN`, `CREATE_TAG`, `CREATE_PLACE`, `TO_MODERATION`.
`LlmClient` бери реализацией `ru.zhma.campaign.ai.llm.MockLlmClient` по умолчанию. Если класс ещё не влит, оставь точку сборки `fun llmClient(): LlmClient` в одном файле сервера, чтобы подменить её на GigaChat позже. Свой парсер намерений не пиши.

## Сборка

Kotlin, Ktor 3, kotlinx.serialization, Exposed, sqlite-jdbc, корутины. JVM target 17 (на машине может стоять более новый JDK). Модули: `:ai`, `:server`. `:server` зависит от `:ai`. Плагин serialization на оба модуля. Точка входа `:server`, `./gradlew :server:run`, порт 8080. Без Docker.

`:ai` — библиотека: serialization, coroutines, junit. Ktor, Exposed и sqlite в `:ai` не подключай.

## Хранение

Файл SQLite `backend/data/campaigns.db` (каталог в `.gitignore` сервера).

Таблицы под домен брифа, не под бюджет и регионы:

- справочники: topics, tags, segments, places, banner_types, channels
- campaigns: поля шапки §2.2 `task.md` (`name`, `marketingName`, даты и время, `topic`, `keywords`, `audienceType`, `weight`, `initialCtr`, `state`, `published`, `author`, `lastEditAuthor`)
- campaign_tags, campaign_places, campaign_segments (`included` boolean)
- banners, contents, creatives — JSON текст и связи; для первого среза достаточно баннера из `BannerDraft`
- audit
- sessions (`id`, `draft_json`, `created_at`)
- messages (`session_id`, `role`, `text`, `blocks_json`, `created_at`)
- jobs (`id`, `session_id`, `status`, `result_json`, `error`, `created_at`)

Сид справочников скопируй из `case-readonly/mock-data.md` §1: 11 тем, 6 тегов, 40 сегментов, 8 мест, 2 типа баннера, 3 канала. Новых кодов не придумывай. Кампании 101–115 в этот срез не загружай. `SEG_DRAFT_WINTER` засей со `state=NEW`.

`weight` по умолчанию 0, `initialCtr` по умолчанию 15, если в черновике null. Новая кампания: `state=DRAFT`, `published=false`, автор из `X-User`.

## Чат и jobs

- `POST /api/sessions` → `{ "sessionId": "..." }`. Пустой черновик.
- `GET /api/sessions/{id}/messages` → история, у сообщений ассистента разобранные `blocks`.
- `POST /api/sessions/{id}/messages` тело `{ "text": "..." }` → `202 { "jobId": "..." }`.
- `GET /api/jobs/{jobId}` → `{ "status": "PENDING|DONE|FAILED", "result": AssistantReply | null, "error": string | null }`.

Обработчик job:

1. Дописать user-сообщение.
2. Собрать `CatalogSnapshot` из БД и лёгкий список кампаний (`CampaignListItem`).
3. `today` — текущая дата UTC `YYYY-MM-DD`.
4. `AgentTurn.handle`.
5. Если `commit` пустой — сохранить новый `draft_json` как есть.
6. Если `commit` есть — выполнить запись ниже, затем сохранить черновик уже с id из БД.
7. Сохранить ответ ассистента. Статус job `DONE`. Любое исключение: `FAILED` и короткий `error`, процесс не ронять.

Таймаут job 30 секунд.

## Что делает commit

Сервер — единственное место записи кампании.

- `CREATE_TAG`: `POST`-логика тега (`name` из proposal), id в `draft.tagIds`, `pendingProposal = null`.
- `CREATE_PLACE`: обязательны `code` и `description`. `state=ACTIVE`. `defaultBanner` не задавай. id в `draft.placeIds`, proposal снять.
- `SAVE_CAMPAIGN`: вставить кампанию из черновика. Места и сегменты записать вместе с шапкой. Если `draft.banner != null`, вставить баннер и один креатив. В черновик записать `savedCampaignId`. Повторный SAVE при уже заполненном id обновляет шапку полной заменой.
- `TO_MODERATION`: из `DRAFT` или `NEEDS_WORK` в `ON_MODERATION`. Дальше по ЖЦ этот срез может отвечать 400 с кодом правила, если переход запрещён.

Каждая запись и переход — строка audit: `entity`, `entityId`, `action`, `changes`, `at`, `actor`, `comment`.

Маппинг баннера в JSON, который кладётся в таблицу banners:

```json
{
  "actionType": "default",
  "title": "...",
  "text": "...",
  "link": null,
  "button": { "name": "..." }
}
```

`link` клади только для `externalLink` и `openSmartLink`. Канал креатива — `BannerDraft.channel`. Тип баннера — код `STORY` или `SMART_BANNER`.

`activate` в этом срезе: заглушка CBRO всегда успешна, ставит `published=true` и `state=ACTIVE`. Вызов только отдельной ручкой, не из чата.

## Остальные ручки

Префикс `/api`. Заголовки на каждом запросе: `X-User`, `X-Role` (`campaignManager` или `campaignModerator`). Нет заголовка → 400. Роль проверяй раньше состояния. `to-moderation` доступен менеджеру. `approve` и `decline` — модератору, иначе `ROLE_FORBIDDEN`.

Отдай в объёме, нужном чату и проверке:

- `GET /api/topics|tags|segments|places|banner-types|channels`
- `GET /api/campaigns`, `GET /api/campaigns/{id}`
- `POST /api/campaigns/{id}/confirm` — тот же код, что `SAVE` черновика, у которого `savedCampaignId` уже равен этому id или черновик ещё не записан и привязан к сессии через query `sessionId`
- `POST /api/campaigns/{id}/to-moderation`
- `GET /api/campaigns/{id}/warnings` — те же человеческие предупреждения, что валидатор (нет тегов, выключенное место, нет креатива на место). Ошибки готовности верни в `errors`, предупреждения в `warnings`
- `GET /api/campaigns/{id}/audit`

Списки справочников целиком, без пагинации. Неизвестный id → 404 `ENTITY_NOT_FOUND`.

CORS для `http://localhost:5173`.

## Критерий готовности

`./gradlew :server:run` поднимает порт 8080 на пустой базе и засеянном справочнике. `POST /api/sessions` и пустой job с текстом не валят процесс даже если класс `AgentTurn` на момент первой компиляции нужно подключить сразу после появления исходников агента 1. Сид содержит сегмент `AGE_26_45` и место `mainScreenStories` (id 1).
