package ru.zhma.campaign.ai.agent

import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import ru.zhma.campaign.ai.llm.LlmClient
import ru.zhma.campaign.ai.llm.LlmMessage
import ru.zhma.campaign.ai.llm.ToolCall
import ru.zhma.campaign.ai.llm.ToolSpec
import ru.zhma.campaign.ai.model.AssistantReply
import ru.zhma.campaign.ai.model.CatalogText
import ru.zhma.campaign.ai.model.DraftCampaign
import ru.zhma.campaign.ai.model.DraftPatch
import ru.zhma.campaign.ai.model.ReplyOption
import ru.zhma.campaign.ai.model.Stage
import ru.zhma.campaign.ai.model.TurnInput
import ru.zhma.campaign.ai.model.TurnResult
import ru.zhma.campaign.ai.model.UiBlock
import ru.zhma.campaign.ai.prompt.Prompts

/**
 * Второй этап: обязательные поля заполнены, модель ведёт свободный диалог и вызывает инструменты.
 *
 * Инструменты меняют только черновик и экран: `update_draft`, `generate_banner`, `generate_image`, `ask_user`, `show_draft`.
 * Записи в базу среди них нет — сохранение и модерация остаются за кодом и явным «да» человека.
 * Если модель не умеет инструменты или сервер вернул ошибку на первом шаге, [run] возвращает null и ход ведёт автомат.
 */
class DesignAgent(
    private val llm: LlmClient,
    private val creatives: Creatives,
) {
    private val json = Json { ignoreUnknownKeys = true }

    private class State(
        var draft: DraftCampaign,
        val blocks: MutableList<UiBlock> = mutableListOf(),
        val changes: MutableList<String> = mutableListOf(),
        var asked: UiBlock.QuickReplies? = null,
        var bannerShown: Boolean = false,
    )

    suspend fun run(
        input: TurnInput,
        edit: EditOutcome,
        intent: String,
    ): TurnResult? {
        val state = State(edit.draft, changes = edit.changes.toMutableList())
        val messages = mutableListOf<LlmMessage>()
        messages += LlmMessage("system", systemPrompt(input, state.draft))
        input.history.takeLast(HISTORY).forEach { h ->
            messages += LlmMessage(if (h.role == "user") "user" else "assistant", h.text.take(1200))
        }
        val codeNote =
            buildList {
                if (edit.changes.isNotEmpty()) {
                    add(
                        "код уже записал в черновик и сам сообщил менеджеру: ${edit.changes.joinToString(
                            ", ",
                        )}. Не перечисляй эти изменения ещё раз",
                    )
                }
                addAll(edit.notes)
                add("распознанное намерение: $intent")
            }.joinToString("; ")
        messages += LlmMessage("user", "${input.userText}\n\n[служебно: $codeNote]")

        var finalText: String? = null
        val tools = tools(input)
        for (step in 0 until MAX_STEPS) {
            val reply =
                try {
                    llm.chatWithTools(messages, tools, temperature = 0.4)
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    if (step == 0) return null
                    break
                }
            if (reply.toolCalls.isEmpty()) {
                finalText = reply.content
                break
            }
            messages += LlmMessage("assistant", reply.content, toolCalls = reply.toolCalls)
            for (call in reply.toolCalls) {
                val result =
                    try {
                        execute(call, state, input)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        """{"ok":false,"error":${JsonPrimitive(e.message ?: "ошибка")}}"""
                    }
                messages += LlmMessage("tool", result, toolCallId = call.id)
            }
            if (state.asked != null) {
                finalText = reply.content
                break
            }
        }
        return finish(input, edit, state, finalText)
    }

    private fun finish(
        input: TurnInput,
        edit: EditOutcome,
        state: State,
        finalText: String?,
    ): TurnResult {
        var draft = state.draft
        val before = input.draft
        if (draft.savedCampaignId != null && DraftMerger.essence(draft) != DraftMerger.essence(before)) draft = draft.copy(dirty = true)
        val validation = CampaignValidator.check(draft, input.catalog, input.today)
        val blocks = mutableListOf<UiBlock>()
        val freshWarnings = validation.warnings - draft.shownWarnings.toSet()
        if (freshWarnings.isNotEmpty()) blocks += UiBlock.Warnings(freshWarnings)
        draft = draft.copy(shownWarnings = validation.warnings)
        blocks += state.blocks
        val draftCard =
            UiBlock.CampaignDraft(
                draft,
                validation.missing,
                validation.readyToSave,
                DraftView.choices(draft, input.catalog),
                if (validation.missing.isEmpty()) Stage.DESIGN else Stage.BRIEF,
            )
        if (blocks.none { it is UiBlock.CampaignDraft } && (state.changes.isNotEmpty() || state.bannerShown)) blocks += draftCard

        val asked = state.asked
        val text =
            buildList {
                val body = finalText?.trim()?.takeIf { it.isNotEmpty() }
                if (edit.changes.isNotEmpty() && body?.contains("Записал") != true) add("Записал: ${edit.changes.joinToString(", ")}.")
                when {
                    body != null -> add(body)
                    asked?.question != null -> add(asked.question)
                    validation.problems.isNotEmpty() -> add(validation.problems.joinToString(" "))
                    else -> add("Готово. Что дальше: доработать баннер, нарисовать картинку или сохранить?")
                }
            }.joinToString(" ")

        if (asked != null) {
            blocks += asked
            draft = draft.copy(awaiting = "ask")
        } else {
            blocks += nextButtons(draft, validation.readyToSave)
            draft = draft.copy(awaiting = null, askCounts = emptyMap())
        }
        return TurnResult(AssistantReply(text, blocks), draft)
    }

    private fun nextButtons(
        draft: DraftCampaign,
        ready: Boolean,
    ): UiBlock.QuickReplies {
        val items = mutableListOf<ReplyOption>()
        if (ready && (draft.savedCampaignId == null || draft.dirty)) items += ReplyOption("Сохранить", "Сохранить", recommended = true)
        if (draft.banner == null) items += ReplyOption("Сделать баннер", "Сделай баннер")
        if (draft.banner != null && creatives.imagesEnabled) {
            items +=
                ReplyOption(if (draft.banner.imageUrl == null) "Нарисовать картинку" else "Другая картинка", "Сделай картинку для баннера")
        }
        if (draft.savedCampaignId != null && !draft.dirty) items += ReplyOption("На модерацию", "Отправь на модерацию", recommended = true)
        return UiBlock.QuickReplies(items.map { it.value }, field = "next", items = items)
    }

    private fun systemPrompt(
        input: TurnInput,
        draft: DraftCampaign,
    ): String {
        val status =
            when {
                draft.savedCampaignId == null -> "ещё не сохранена"
                draft.dirty -> "сохранена как #${draft.savedCampaignId}, есть несохранённые правки"
                else ->
                    "сохранена как #${draft.savedCampaignId}" +
                        (
                            input.campaigns
                                .find { it.id == draft.savedCampaignId }
                                ?.state
                                ?.let { ", состояние $it" } ?: ""
                        )
            }
        return Prompts.designAgent(input.today, CatalogText.render(input.catalog), DraftView.summary(draft, input.catalog), status)
    }

    // ---------- Инструменты ----------

    private suspend fun execute(
        call: ToolCall,
        state: State,
        input: TurnInput,
    ): String {
        val args =
            runCatching { json.parseToJsonElement(call.arguments.ifBlank { "{}" }) as? JsonObject }.getOrNull() ?: JsonObject(emptyMap())
        return when (call.name) {
            "update_draft" -> updateDraft(args, state, input)
            "generate_banner" -> {
                val result =
                    creatives.banner(
                        input,
                        state.draft,
                        brief = args.str("brief") ?: input.userText,
                        bannerTypeHint = args.str("bannerType"),
                        channelHint = args.str("channel"),
                        linkHint = args.str("link"),
                    )
                if (result.problems.isNotEmpty()) return err(result.problems.joinToString(" "))
                state.draft = result.draft
                showBanner(state)
                val b = result.draft.banner!!
                ok(
                    "баннер показан менеджеру: тип ${b.bannerType}, канал ${b.channel}, заголовок «${b.title}», текст «${b.text}», кнопка «${b.cta}». В базу не записан.",
                )
            }
            "generate_image" -> {
                val result =
                    creatives.image(
                        input,
                        state.draft,
                        brief = args.str("description") ?: input.userText,
                        style = args.str("style"),
                        withText = args.bool("withText"),
                    )
                if (result.problems.isNotEmpty()) return err(result.problems.joinToString(" "))
                state.draft = result.draft
                showBanner(state)
                ok("картинка поставлена в очередь и появится в превью через 1–2 минуты. Скажи об этом менеджеру.")
            }
            "ask_user" -> {
                val question = args.str("question") ?: return err("нет вопроса")
                val options =
                    (args["options"] as? JsonArray)
                        .orEmpty()
                        .mapNotNull {
                            (it as? JsonPrimitive)?.contentOrNull?.trim()
                        }.filter { it.isNotEmpty() }
                        .take(8)
                val multi = args.bool("multiple") ?: false
                val items = options.map { ReplyOption(it, it) }
                state.asked =
                    UiBlock.QuickReplies(options, field = "ask", question = question, multi = multi, allowCustom = true, items = items)
                ok("вопрос с вариантами показан, жди ответа менеджера и не задавай других вопросов.")
            }
            "edit_banner" -> {
                val banner = state.draft.banner ?: return err("баннера ещё нет — сначала generate_banner")
                val edited =
                    banner.copy(
                        title = args.str("title") ?: banner.title,
                        text = args.str("text") ?: banner.text,
                        cta = args.str("cta") ?: banner.cta,
                    )
                val problems = Creatives.bannerProblems(edited, input.catalog)
                if (problems.isNotEmpty()) return err(problems.joinToString(" "))
                state.draft = state.draft.copy(banner = edited.copy(compliance = creatives.compliance.check(edited, state.draft.name)))
                showBanner(state)
                ok("текст баннера заменён точно как просили")
            }
            "check_compliance" -> {
                val banner = state.draft.banner ?: return err("баннера ещё нет — сначала generate_banner")
                val report = creatives.compliance.check(banner, state.draft.name)
                state.draft = state.draft.copy(banner = banner.copy(compliance = report))
                showBanner(state)
                ok(
                    "отчёт показан. Итог: ${report.verdict}. " +
                        report.items.joinToString("; ") { "${it.article} ${it.status}: ${it.message}" },
                )
            }
            "show_draft" -> {
                val v = CampaignValidator.check(state.draft, input.catalog, input.today)
                state.blocks.removeAll { it is UiBlock.CampaignDraft }
                state.blocks +=
                    UiBlock.CampaignDraft(
                        state.draft,
                        v.missing,
                        v.readyToSave,
                        DraftView.choices(state.draft, input.catalog),
                        Stage.DESIGN,
                    )
                ok(DraftView.summary(state.draft, input.catalog))
            }
            else -> err("неизвестный инструмент ${call.name}")
        }
    }

    private fun showBanner(state: State) {
        state.blocks.removeAll { it is UiBlock.CreativePreview || it is UiBlock.Compliance }
        val banner = state.draft.banner!!
        state.blocks.add(0, UiBlock.CreativePreview(banner))
        banner.compliance?.let { state.blocks.add(1, UiBlock.Compliance(it)) }
        state.bannerShown = true
    }

    private fun updateDraft(
        args: JsonObject,
        state: State,
        input: TurnInput,
    ): String {
        val catalog = input.catalog
        val patch =
            DraftPatch(
                name = args.str("name"),
                topic = args.str("topic")?.takeIf { code -> catalog.topics.any { it.value == code } },
                audienceType = args.str("audienceType")?.takeIf { it == "ALL_USERS" || it == "SEGMENTED" },
                includedSegments = args.strList("addSegments"),
                removeSegments = args.strList("removeSegments"),
                excludedSegments = args.strList("excludeSegments"),
                placeIds = args.longList("addPlaceIds"),
                removePlaceIds = args.longList("removePlaceIds"),
                weight = (args["weight"] as? JsonPrimitive)?.doubleOrNull,
                tagIds = args.longList("addTagIds"),
                tagQuery = args.str("newTag"),
            )
        val before = state.draft
        var draft = DraftMerger.apply(before, patch)
        val facts =
            DateFacts(
                startDate = args.str("startDate")?.takeIf(::isoDate),
                endDate = args.str("endDate")?.takeIf(::isoDate),
                startTime = args.str("startTime")?.takeIf(::clock),
                endTime = args.str("endTime")?.takeIf(::clock),
            )
        draft = DateParser.apply(draft, facts)
        if (draft.audienceType == "SEGMENTED" && draft.includedSegments.isEmpty() && before.audienceType == "ALL_USERS") {
            return err("для сегментной аудитории нужен хотя бы один сегмент — передай addSegments")
        }
        val resolved = EntityResolver.resolve(draft, patch, catalog)
        draft = resolved.draft
        val v = CampaignValidator.check(draft, catalog, input.today)
        val changes = DraftView.changes(before, draft, catalog)
        state.draft = draft
        state.changes += changes
        return buildJsonObject {
            put("ok", true)
            putJsonArray("changes") { changes.forEach { add(JsonPrimitive(it)) } }
            resolved.note?.let { put("note", it) }
            putJsonArray("missing") { v.missing.forEach { add(JsonPrimitive(it)) } }
            putJsonArray("problems") { v.problems.forEach { add(JsonPrimitive(it)) } }
            putJsonArray("warnings") { v.warnings.forEach { add(JsonPrimitive(it)) } }
        }.toString()
    }

    private fun tools(input: TurnInput): List<ToolSpec> {
        val catalog = input.catalog
        val topicCodes = catalog.topics.map { it.value }
        val list =
            mutableListOf(
                ToolSpec(
                    "update_draft",
                    "Изменить поля черновика кампании. Передавай только то, что менеджер просит поменять. Коды и id — только из каталога.",
                    schema {
                        str("name", "Новое название")
                        enum("topic", topicCodes, "Код темы")
                        enum("audienceType", listOf("ALL_USERS", "SEGMENTED"), "Тип аудитории")
                        strArray("addSegments", "Коды сегментов, которые добавить")
                        strArray("removeSegments", "Коды сегментов, которые убрать")
                        strArray("excludeSegments", "Коды сегментов-исключений")
                        intArray("addPlaceIds", "id мест показа, которые добавить")
                        intArray("removePlaceIds", "id мест показа, которые убрать")
                        str("startDate", "Дата старта YYYY-MM-DD")
                        str("startTime", "Время старта HH:MM")
                        str("endDate", "Дата окончания YYYY-MM-DD")
                        str("endTime", "Время окончания HH:MM")
                        num("weight", "Вес кампании")
                        intArray("addTagIds", "id существующих тегов из каталога, которые повесить")
                        str("newTag", "Название нового тега, если подходящего в каталоге нет (создастся после подтверждения)")
                    },
                ),
                ToolSpec(
                    "generate_banner",
                    "Сделать или переделать текст баннера (заголовок, текст, кнопка) и показать превью. В базу не пишет.",
                    schema {
                        str("brief", "Пожелания к тексту: акцент, тон, что упомянуть")
                        enum("bannerType", listOf("STORY", "SMART_BANNER"), "Тип баннера")
                        enum("channel", listOf("iOS", "ANDROID", "WEB"), "Канал. Сторис — только iOS или ANDROID")
                        str("link", "Ссылка для кнопки, если менеджер её дал")
                    },
                ),
                ToolSpec(
                    "ask_user",
                    "Задать менеджеру один вопрос с вариантами ответа: на экране появится меню. После вызова жди ответа.",
                    schema(required = listOf("question", "options")) {
                        str("question", "Вопрос")
                        strArray("options", "2–6 коротких вариантов ответа")
                        bool("multiple", "Можно выбрать несколько вариантов")
                    },
                ),
                ToolSpec("show_draft", "Показать менеджеру карточку текущего черновика.", schema { }),
                ToolSpec(
                    "edit_banner",
                    "Заменить текст баннера ДОСЛОВНО, когда менеджер сам называет заголовок, текст или кнопку. Не переписывай за него.",
                    schema {
                        str("title", "Новый заголовок")
                        str("text", "Новый основной текст")
                        str("cta", "Новый текст кнопки")
                    },
                ),
                ToolSpec(
                    "check_compliance",
                    "Проверить текст баннера на соответствие закону «О рекламе» (38-ФЗ) и показать отчёт со статьями.",
                    schema { },
                ),
            )
        if (creatives.imagesEnabled) {
            list +=
                ToolSpec(
                    "generate_image",
                    "Нарисовать картинку для баннера. Генерация идёт 1–2 минуты в фоне, картинка появится в превью сама.",
                    schema {
                        str("description", "Что изобразить, по-русски или по-английски")
                        str("style", "Стиль: фото, иллюстрация, 3D, минимализм и т.п.")
                        bool("withText", "Нужна ли надпись на самой картинке")
                    },
                )
        }
        return list
    }

    private class SchemaBuilder {
        val props = mutableMapOf<String, JsonObject>()

        fun str(
            name: String,
            description: String,
        ) {
            props[name] =
                buildJsonObject {
                    put("type", "string")
                    put("description", description)
                }
        }

        fun num(
            name: String,
            description: String,
        ) {
            props[name] =
                buildJsonObject {
                    put("type", "number")
                    put("description", description)
                }
        }

        fun bool(
            name: String,
            description: String,
        ) {
            props[name] =
                buildJsonObject {
                    put("type", "boolean")
                    put("description", description)
                }
        }

        fun enum(
            name: String,
            values: List<String>,
            description: String,
        ) {
            props[name] =
                buildJsonObject {
                    put("type", "string")
                    put("description", description)
                    putJsonArray("enum") { values.forEach { add(JsonPrimitive(it)) } }
                }
        }

        fun strArray(
            name: String,
            description: String,
        ) {
            props[name] =
                buildJsonObject {
                    put("type", "array")
                    put("description", description)
                    putJsonObject("items") { put("type", "string") }
                }
        }

        fun intArray(
            name: String,
            description: String,
        ) {
            props[name] =
                buildJsonObject {
                    put("type", "array")
                    put("description", description)
                    putJsonObject("items") { put("type", "integer") }
                }
        }
    }

    private fun schema(
        required: List<String> = emptyList(),
        block: SchemaBuilder.() -> Unit,
    ): JsonObject {
        val b = SchemaBuilder().apply(block)
        return buildJsonObject {
            put("type", "object")
            put("properties", JsonObject(b.props))
            if (required.isNotEmpty()) putJsonArray("required") { required.forEach { add(JsonPrimitive(it)) } }
        }
    }

    private fun JsonObject.str(key: String): String? = (this[key] as? JsonPrimitive)?.contentOrNull?.trim()?.takeIf { it.isNotEmpty() }

    private fun JsonObject.bool(key: String): Boolean? = (this[key] as? JsonPrimitive)?.booleanOrNull

    private fun JsonObject.strList(key: String): List<String>? =
        (this[key] as? JsonArray)
            ?.mapNotNull {
                (it as? JsonPrimitive)?.contentOrNull?.trim()
            }?.filter { it.isNotEmpty() }
            ?.takeIf { it.isNotEmpty() }

    private fun JsonObject.longList(key: String): List<Long>? =
        (this[key] as? JsonArray)
            ?.mapNotNull { (it as? JsonPrimitive)?.let { p -> p.longOrNull ?: p.contentOrNull?.toLongOrNull() } }
            ?.takeIf { it.isNotEmpty() }

    private fun isoDate(value: String): Boolean = runCatching { java.time.LocalDate.parse(value) }.isSuccess

    private fun clock(value: String): Boolean = Regex("""^(?:[01]\d|2[0-3]):[0-5]\d$""").matches(value)

    private fun ok(message: String): String =
        buildJsonObject {
            put("ok", true)
            put("result", message)
        }.toString()

    private fun err(message: String): String =
        buildJsonObject {
            put("ok", false)
            put("error", message)
        }.toString()

    private companion object {
        const val MAX_STEPS = 5
        const val HISTORY = 8
    }
}
