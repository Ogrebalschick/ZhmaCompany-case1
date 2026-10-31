# Модуль `:ai`

## Что это

Библиотека конечного автомата диалога. Точки входа в процесс нет: модуль не поднимает HTTP и не открывает базу. Модель только достаёт JSON из реплики и коротко формулирует вопрос. Что спросить, какой черновик валиден и какое действие записать, решает код.

Пакеты: `model`, `agent`, `llm`, `prompt`, `image`.

Клиенты в `llm` и `image`:

- `LlmClient`, `StructuredLlm` — повтор JSON до трёх раз
- `MockLlmClient` — эвристики без сети, ими пользуются тесты и сервер без переменных модели
- `OpenAiCompatibleClient` — любой OpenAI-совместимый `/v1/chat/completions`. Один вызов ждёт до 45 секунд
- `GigaChatClient` — обёртка над `OpenAiCompatibleClient`. Пустой токен даёт ошибку, моком себя не подменяет
- `ImageGenClient`, `PlaceholderImageGen` — одноцветный PNG. В автомат не подключён, сеть не использует

Тексты `Prompts` лежат в `prompt` и в клиенты не вшиты.

## Кто вызывает

Модуль подключает `:server`. Снаружи нужны пакет `ru.zhma.campaign.ai.model` и класс `ru.zhma.campaign.ai.agent.AgentTurn`. Остальные типы в `agent` — шаги этого хода, сервер их не вызывает.

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

## Как запустить

Запускать нечего: это библиотека. Процесс поднимает `:server`, см. `backend/server/README.md` и корневой `README.md`.

## Как проверить

Из каталога `backend`. На Windows `gradlew.bat`, на Unix `./gradlew`.

```bat
gradlew.bat :ai:check
gradlew.bat :ai:ktlintCheck
gradlew.bat :ai:ktlintFormat
```

`:ai:check` компилирует и гоняет тесты. `ktlintCheck` только стиль. `ktlintFormat` переписывает исходники под этот стиль.

## Структура

Корень исходников: `src/main/kotlin/ru/zhma/campaign/ai/`.

- `model` — данные, которые сервер передаёт и получает.
- `agent` — шаги автомата. Снаружи нужен только `AgentTurn`.
- `llm` — клиенты и разбор JSON.
- `prompt` — три текста `Prompts`: намерение, недостающие поля, текст баннера.
- `image` — интерфейс генерации и PNG-заглушка.
- `src/test/kotlin` — тесты мерджера, валидатора, списка кампаний, относительных дат, мока, `StructuredLlm` и PNG.

Классы `agent`:

- `AgentTurn` — один ход диалога: `handle`.
- `IntentParser` — один вызов `StructuredLlm` за намерением.
- `DraftMerger` — накладывает на черновик только непустые поля патча. Переход на `ALL_USERS` очищает сегменты.
- `RelativeDates` — даты и время берутся из реплики и из уже сохранённого черновика. `keepSpokenDates` отбрасывает даты, которые модель подставила сама.
- `EntityResolver` — коды сегментов и id мест или тегов только из каталога. Неизвестный сегмент не создаётся. Неизвестный тег или место становится `pendingProposal`.
- `CampaignValidator` — список `missing` и флаг `readyToSave`.
- `BannerCopy` — разбор JSON одного баннера для шага `GENERATE_CREATIVE`. Дерево PROMO-контента не собирается.
