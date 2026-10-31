# Агент 1. Конечный автомат (`:ai` model + agent)

Ты реализуешь только логику диалога. LLM извлекает JSON из реплики. Что спрашивать, что валидно и когда просить сервер сохранить — решает этот код.

Работай параллельно с тремя другими агентами. Пиши только в своих путях. `case-readonly/` не меняй.

## Твои пути

- `backend/ai/src/main/kotlin/ru/zhma/campaign/ai/model/**`
- `backend/ai/src/main/kotlin/ru/zhma/campaign/ai/agent/**`
- `backend/ai/src/test/kotlin/ru/zhma/campaign/ai/agent/**`

## Чужие пути

- `backend/ai/src/main/kotlin/ru/zhma/campaign/ai/llm/**`
- `backend/ai/src/main/kotlin/ru/zhma/campaign/ai/prompt/**`
- `backend/ai/src/main/kotlin/ru/zhma/campaign/ai/image/**`
- `backend/server/**`, Gradle-файлы, `frontend/**`, корневые `prompt-*.md`

Интерфейс `ru.zhma.campaign.ai.llm.LlmClient`, класс `StructuredLlm` и объект `ru.zhma.campaign.ai.prompt.Prompts` пишет агент 3. Вызывай их по сигнатурам ниже. Свои копии этих типов не создавай. Сборку Gradle пишет агент 2: модуль `:ai` без Ktor и без БД, kotlinx.serialization, корутины, тесты JUnit.

## Сигнатуры, которые уже заняты

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
```

```kotlin
package ru.zhma.campaign.ai.prompt

object Prompts {
    fun intent(today: String, catalog: String, draftJson: String): String
    fun askMissing(missing: String, options: String): String
    fun bannerCopy(product: String, audience: String, placement: String, n: Int): String
}
```

`StructuredLlm.ask` сам дописывает обратную связь при битом JSON. В `user` передавай реплику и контекст, в `system` — текст из `Prompts`.

## Что написать

Пакет `model`:

- `DraftCampaign`, `BannerDraft`, `EntityProposal`, `DraftPatch`, `ParsedIntent`
- `CatalogSnapshot` и строки справочника: `TopicRef`, `TagRef`, `SegmentRef`, `PlaceRef`, `BannerTypeRef`, `ChannelRef`
- `CampaignListItem`
- `UiBlock` (sealed, дискриминатор поля `type`)
- `AssistantReply`, `TurnInput`, `TurnResult`, `CommitRequest`
- `CatalogText.render(snapshot: CatalogSnapshot): String` — компактный текст каталога для промпта, без обрезки top-K

Пакет `agent`:

- `IntentParser` — один вызов `StructuredLlm` + `Prompts.intent`
- `DraftMerger` — накладывает только non-null поля патча
- `EntityResolver` — коды и id только из `CatalogSnapshot`; иначе предложение создать тег или место
- `CampaignValidator` — детерминированные проверки и список недостающих полей
- `AgentTurn(llm: LlmClient)` с методом `suspend fun handle(input: TurnInput): TurnResult`

Тесты мерджера, валидатора и `AgentTurn` на поддельном `LlmClient` в тестовом сорсе агента. `MockLlmClient` не пиши.

## Контракт моделей

Поля, которых нет в брифе (`budget`, `regions`, `product`), не добавляй.

```kotlin
@Serializable
data class DraftCampaign(
    val name: String? = null,
    val marketingName: String? = null,
    val startDate: String? = null,
    val startTime: String? = null,
    val endDate: String? = null,
    val endTime: String? = null,
    val topic: String? = null,
    val tagIds: List<Long> = emptyList(),
    val keywords: String? = null,
    val audienceType: String? = null,
    val includedSegments: List<String> = emptyList(),
    val excludedSegments: List<String> = emptyList(),
    val placeIds: List<Long> = emptyList(),
    val weight: Double? = null,
    val initialCtr: Double? = null,
    val creativeBrief: String? = null,
    val banner: BannerDraft? = null,
    val pendingProposal: EntityProposal? = null,
    val savedCampaignId: Long? = null,
)

@Serializable
data class BannerDraft(
    val bannerType: String,
    val channel: String,
    val title: String? = null,
    val text: String? = null,
    val actionType: String = "default",
    val link: String? = null,
    val cta: String? = null,
)

@Serializable
data class EntityProposal(
    val kind: String,
    val name: String,
    val code: String? = null,
    val description: String? = null,
)

@Serializable
data class DraftPatch(
    val name: String? = null,
    val marketingName: String? = null,
    val topic: String? = null,
    val tagIds: List<Long>? = null,
    val keywords: String? = null,
    val audienceType: String? = null,
    val includedSegments: List<String>? = null,
    val excludedSegments: List<String>? = null,
    val segmentQuery: String? = null,
    val placeIds: List<Long>? = null,
    val placeQuery: String? = null,
    val tagQuery: String? = null,
    val startDate: String? = null,
    val startTime: String? = null,
    val endDate: String? = null,
    val endTime: String? = null,
    val weight: Double? = null,
    val initialCtr: Double? = null,
    val creativeBrief: String? = null,
)

@Serializable
data class ParsedIntent(
    val intent: String,
    val patch: DraftPatch = DraftPatch(),
    val confirm: Boolean? = null,
)
```

`audienceType`: `ALL_USERS` или `SEGMENTED`. `BannerDraft.bannerType`: `STORY` или `SMART_BANNER`. `channel`: `iOS`, `ANDROID`, `WEB`. `EntityProposal.kind`: `TAG` или `PLACE`. `actionType`: `default`, `externalLink`, `openSmartLink`, `closeCampaign`, `doneCampaign`.

Справочник:

```kotlin
@Serializable data class TopicRef(val value: String, val label: String)
@Serializable data class TagRef(val id: Long, val name: String)
@Serializable data class SegmentRef(val code: String, val name: String, val type: String, val state: String, val count: Long)
@Serializable data class PlaceRef(val id: Long, val code: String, val description: String, val defaultBanner: String?, val state: String)
@Serializable data class BannerTypeRef(val code: String, val titleMax: Int, val textMax: Int, val channels: List<String>)
@Serializable data class ChannelRef(val code: String, val description: String)
@Serializable data class CampaignListItem(val id: Long, val name: String, val state: String, val published: Boolean, val topic: String, val startDate: String, val endDate: String?)
```

```kotlin
@Serializable
data class TurnInput(
    val draft: DraftCampaign,
    val userText: String,
    val catalog: CatalogSnapshot,
    val today: String,
    val campaigns: List<CampaignListItem> = emptyList(),
)

@Serializable
data class AssistantReply(val text: String, val blocks: List<UiBlock>)

@Serializable
data class TurnResult(
    val reply: AssistantReply,
    val draft: DraftCampaign,
    val commit: CommitRequest? = null,
)

@Serializable
data class CommitRequest(val action: String)
```

`CommitRequest.action`: `SAVE_CAMPAIGN`, `CREATE_TAG`, `CREATE_PLACE`, `TO_MODERATION`. Базу этот модуль не трогает: только возвращает `commit`.

`UiBlock` — sealed, kotlinx.serialization, поле `type`:

- `campaign_draft`: `draft`, `missing: List<String>`, `readyToSave: Boolean`
- `quick_replies`: `options: List<String>`
- `entity_proposal`: `proposal: EntityProposal`
- `creative_preview`: `banner: BannerDraft`
- `campaign_list`: `items: List<CampaignListItem>`
- `warnings`: `items: List<String>`

Json: `classDiscriminator = "type"`, `ignoreUnknownKeys = true`, `encodeDefaults = true`.

## Поведение `AgentTurn.handle`

1. Разобрать реплику через `IntentParser`. Допустимые intent: `CAMPAIGN_EDIT`, `CONFIRM`, `CANCEL`, `GENERATE_CREATIVE`, `LIST_CAMPAIGNS`, `OTHER`.
2. `CAMPAIGN_EDIT`: `DraftMerger`, затем `EntityResolver`, затем `CampaignValidator`.
   - Неизвестный сегмент: id не выдумывать, в тексте сказать, что такого кода нет, и предложить ближайшие `ACTIVE` из каталога. `pendingProposal` для сегмента не ставить.
   - Неизвестный тег или место: `pendingProposal` и блок `entity_proposal`. Пока предложение висит, кампанию не помечать `readyToSave`.
   - Есть недостающие поля: вопрос по шаблону (LLM через `Prompts.askMissing` можно вызвать, чтобы ужать фразу до 1–2 предложений). Блок `quick_replies`, если есть 2–5 готовых вариантов из каталога.
   - Черновик готов: блок `campaign_draft` и кнопка «Сохранить». `commit` пустой.
3. `CONFIRM` (или `confirm == true`):
   - есть `pendingProposal` → `commit` `CREATE_TAG` или `CREATE_PLACE`, предложение с черновика снять после того как сервер его исполнит; в ответе черновик всё ещё содержит proposal, сервер очистит его сам после вставки id;
   - proposal нет и `readyToSave` и `savedCampaignId == null` → `commit = SAVE_CAMPAIGN`;
   - `savedCampaignId != null` и реплика про модерацию → `commit = TO_MODERATION`;
   - иначе переспросить, что подтверждать.
4. `CANCEL`: `pendingProposal = null`, `commit = null`, коротко сказать, что предложение снято.
5. `GENERATE_CREATIVE`: собрать баннер через `Prompts.bannerCopy` и `StructuredLlm` в `BannerDraft`. Прогнать лимиты и `actionType`/`link`. Валидный результат записать в `draft.banner` и показать `creative_preview`. `commit` пустой. Дерево PROMO-контента не собирай.
6. `LIST_CAMPAIGNS`: блок `campaign_list` из `input.campaigns`. LLM не вызывать.
7. `OTHER`: одна вежливая фраза в рамках рекламных кампаний. Патч не применять.

Относительные сроки («на 2 недели», «до конца года», «с завтра») посчитай в коде от `input.today`. Если даты в реплике нет, `startDate` оставь пустым и спроси. Время без явной фразы не подставляй: спроси `startTime`.

`weight` и `initialCtr` заполняй только если пользователь сказал про охват или CTR. Широкий охват → предложи `weight` в тексте и положи в патч только после явного числа или согласия на предложенное число. Дефолты 0 и 15 ставит сервер.

## Валидатор

Готовность к сохранению (`readyToSave`), все пункты сразу:

- `name` непустое, до 100 символов
- `startDate` и `startTime` (`HH:mm`)
- `topic` из каталога
- `audienceType` задан
- `placeIds` не пуст, каждый id есть в каталоге
- `SEGMENTED` → хотя бы один included-код со `state=ACTIVE`
- `ALL_USERS` → оба списка сегментов пустые

Ошибки, которые снимают `readyToSave`:

- конец не позже начала, если конец задан
- дата начала раньше `today`
- код сегмента отсутствует или не `ACTIVE`
- `STORY` с каналом `WEB`
- `title`/`text` длиннее лимита типа (`STORY` 100/500, `SMART_BANNER` 250/1000 — читай из `BannerTypeRef`, не из константы, если строка типа есть в снимке)
- `link` обязателен для `externalLink` и `openSmartLink`
- `link` запрещён для `closeCampaign` и `doneCampaign`

Неактивное место и пустые теги: блок `warnings`, готовность не снимают. Тексты предупреждений человеческие: «У кампании нет тегов», «Место показа выключено».

Коды полей `missing` стабильные: `name`, `startDate`, `startTime`, `topic`, `audienceType`, `places`, `segments`.

## Критерий готовности

Юнит-тесты проходят без сети: наложение патча не затирает уже собранные поля; `ALL_USERS` с сегментами не готов; подтверждение готового черновика возвращает `SAVE_CAMPAIGN` и не пишет никуда на диск; список кампаний собирается из `TurnInput.campaigns`.
