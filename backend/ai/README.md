# Модуль `:ai`

Библиотека конечного автомата диалога. Точки входа в процесс нет: модуль не поднимает HTTP и не открывает базу. Его подключает `:server` и вызывает на каждое сообщение чата.

Сборка и тесты: из каталога `backend` команда `./gradlew :ai:check`. Стиль отдельно: `./gradlew :ai:ktlintCheck`, поправить формат: `./gradlew :ai:ktlintFormat`.

Пакет `llm` содержит `LlmClient` и `StructuredLlm`: повтор JSON до трёх раз. Пакет `prompt` содержит тексты `Prompts`, которые ход подставляет в вызов. Живой клиент GigaChat и мок с эвристиками сюда не входят.

## Зачем он нужен

Сервер не разбирает реплику сам. Он передаёт черновик, текст, снимок справочника и сегодняшнюю дату. Модуль решает, что спросить, какой черновик валиден и какое действие записать. Модель только достаёт JSON из реплики и коротко формулирует вопрос. В базу пишет сервер, и только если в ответе есть `commit`.

## Структура

Корень исходников: `src/main/kotlin/ru/zhma/campaign/ai/`.

- `model` — данные, которые сервер передаёт и получает.
- `agent` — шаги автомата. Снаружи нужен только `AgentTurn`.
- `llm` — `LlmClient` и разбор JSON в `StructuredLlm`.
- `prompt` — три текста `Prompts`: намерение, недостающие поля, текст баннера.
- `src/test/kotlin/.../agent` — тесты мерджера, валидатора, списка кампаний и относительных дат.

Классы `agent`:

- `AgentTurn` — один ход диалога: `handle`.
- `IntentParser` — один вызов `StructuredLlm` за намерением.
- `DraftMerger` — накладывает на черновик только непустые поля патча. Переход на `ALL_USERS` очищает сегменты.
- `RelativeDates` — даты и время берутся из реплики и из уже сохранённого черновика. `keepSpokenDates` отбрасывает даты, которые модель подставила сама.
- `EntityResolver` — коды сегментов и id мест или тегов только из каталога. Неизвестный сегмент не создаётся. Неизвестный тег или место становится `pendingProposal`.
- `CampaignValidator` — список `missing` и флаг `readyToSave`.
- `BannerCopy` — разбор JSON одного баннера для шага `GENERATE_CREATIVE`. Дерево PROMO-контента не собирается.

## API для других модулей

Пакет `ru.zhma.campaign.ai.model` и класс `ru.zhma.campaign.ai.agent.AgentTurn`. Остальные типы в `agent` — шаги этого хода, сервер их не вызывает.

Сервер создаёт ход так:

```kotlin
val turn = AgentTurn(llm)
val result = turn.handle(
    TurnInput(
        draft = draft,
        userText = text,
        catalog = snapshot,
        today = "2026-11-01",
        campaigns = campaigns,
    ),
)
```

`TurnInput` несёт текущий `DraftCampaign`, реплику, `CatalogSnapshot`, дату `YYYY-MM-DD` и лёгкий список кампаний для намерения `LIST_CAMPAIGNS`.

`TurnResult` возвращает три вещи:

- `reply` — текст и блоки `UiBlock` (`campaign_draft`, `quick_replies`, `entity_proposal`, `creative_preview`, `campaign_list`, `warnings`);
- `draft` — новый черновик, его надо сохранить в сессии;
- `commit` — `null` либо `CommitRequest` с действием `SAVE_CAMPAIGN`, `CREATE_TAG`, `CREATE_PLACE`, `TO_MODERATION`.

Пока `commit` пустой, сервер только обновляет сессию. Если действие есть, сервер выполняет запись и затем сохраняет черновик. Для `CREATE_TAG` и `CREATE_PLACE` предложение в черновике ещё лежит: сервер создаёт запись, подставляет id и сам снимает `pendingProposal`. `savedCampaignId` проставляет тоже сервер.

`CatalogText.render` собирает весь снимок справочника в текст для промпта. `AppJson` сериализует блоки с полем `type`.
