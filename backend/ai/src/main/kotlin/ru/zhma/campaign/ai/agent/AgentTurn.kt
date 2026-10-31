package ru.zhma.campaign.ai.agent

import kotlinx.coroutines.CancellationException
import ru.zhma.campaign.ai.image.ImageRequests
import ru.zhma.campaign.ai.llm.LlmClient
import ru.zhma.campaign.ai.llm.StructuredLlm
import ru.zhma.campaign.ai.model.AssistantReply
import ru.zhma.campaign.ai.model.CatalogSnapshot
import ru.zhma.campaign.ai.model.CatalogText
import ru.zhma.campaign.ai.model.CommitAction
import ru.zhma.campaign.ai.model.CommitRequest
import ru.zhma.campaign.ai.model.DraftCampaign
import ru.zhma.campaign.ai.model.DraftPatch
import ru.zhma.campaign.ai.model.EntityProposal
import ru.zhma.campaign.ai.model.Intents
import ru.zhma.campaign.ai.model.ParsedIntent
import ru.zhma.campaign.ai.model.ReplyOption
import ru.zhma.campaign.ai.model.Stage
import ru.zhma.campaign.ai.model.TurnInput
import ru.zhma.campaign.ai.model.TurnResult
import ru.zhma.campaign.ai.model.UiBlock
import ru.zhma.campaign.ai.prompt.Prompts

/** Итог применения реплики к черновику. */
data class EditOutcome(
    val draft: DraftCampaign,
    val changes: List<String>,
    val notes: List<String>,
    val suggestions: List<String> = emptyList(),
)

/**
 * Один ход диалога. Чистая функция: черновик + реплика + справочник → ответ, новый черновик и, может быть, запрос на запись.
 *
 * Этап 1 (BRIEF, пока не заполнены обязательные поля) ведёт автомат: код разбирает даты, справочник и ответы кнопками,
 * модель достаёт остальное в JSON. Вопрос — один за раз, с меню вариантов.
 * Этап 2 (DESIGN, всё обязательное есть) ведёт [DesignAgent]: модель свободно общается и вызывает инструменты.
 * Разбор полей при этом продолжает работать на каждой реплике. Запись в базу — только через [CommitRequest] после явного «да».
 */
class AgentTurn(
    private val llm: LlmClient,
    private val images: ImageRequests? = null,
) {
    private val structured = StructuredLlm(llm)
    private val intents = IntentParser(structured)
    private val creatives = Creatives(structured, images)
    private val design = DesignAgent(llm, creatives)

    suspend fun handle(input: TurnInput): TurnResult {
        if (input.role == "campaignModerator") return ModeratorTurn.handle(input)
        val state = input.draft.savedCampaignId?.let { id -> input.campaigns.find { it.id == id } }
        val result = managerTurn(input)
        if (state == null) return result
        val draft = result.draft.copy(knownState = state.state)
        val changed = input.draft.knownState != state.state
        val notice =
            when {
                !changed -> null
                state.state == "NEEDS_WORK" ->
                    "Модератор вернул кампанию на доработку" + (state.lastComment?.let { ": «$it»" } ?: "") +
                        ". Поправим здесь же, сохраним и снова отправим на модерацию."
                state.state == "APPROVED" ->
                    "Модератор одобрил кампанию — водяной знак с картинки снят, её можно скачать. " +
                        "Чтобы запустить показ, напишите «запусти кампанию»."
                else -> null
            } ?: return result.copy(draft = draft)
        var blocks = result.reply.blocks
        if (state.state == "APPROVED" && result.commit == null) {
            val items = listOf(ReplyOption("Запустить кампанию", "Запусти кампанию", recommended = true))
            blocks =
                blocks.filterNot { it is UiBlock.QuickReplies } +
                UiBlock.QuickReplies(items.map { it.value }, field = "next", items = items)
        }
        return result.copy(draft = draft, reply = result.reply.copy(text = notice + "\n\n" + result.reply.text, blocks = blocks))
    }

    private suspend fun managerTurn(input: TurnInput): TurnResult {
        val text = input.userText.trim()
        val lower = QuickAnswer.norm(text)
        if (text.isEmpty()) return greet(input)

        if (lower in CANCEL_WORDS) return cancel(input)
        if (CONFIRM.matches(lower)) return confirm(input)
        if (MODERATION.containsMatchIn(lower)) return moderation(input)
        OPEN.find(lower)?.let { m -> return open(input, m.groupValues[1].toLong()) }
        val command = lower.length <= COMMAND_MAX
        if (command && ACTIVATE.containsMatchIn(lower)) activate(input)?.let { return it }
        if (command && STOP.containsMatchIn(lower)) stop(input)?.let { return it }
        if (command && AUDIT.containsMatchIn(lower)) audit(input)?.let { return it }
        if (input.draft.banner != null && FIX_COMPLIANCE.containsMatchIn(lower)) return fixCompliance(input)
        BannerEdit.parse(text).takeIf { !it.isEmpty }?.let { return editBanner(input, it) }
        if (TAGS.containsMatchIn(lower) && input.draft.awaiting != "tags") return tagsReply(input, text)
        if (COMPLIANCE.containsMatchIn(lower)) return complianceReply(input)

        val draft = input.draft
        val quick = QuickAnswer.match(text, draft, input.catalog)
        val facts = DateParser.parse(text, input.today, draft.awaiting)
        val extracted = TextExtractor.extract(text, input.catalog)
        val pureAnswer = quick != null || isPureDateAnswer(draft.awaiting, facts, lower)

        val parsed: ParsedIntent? =
            if (pureAnswer) {
                ParsedIntent(Intents.CAMPAIGN_EDIT, quick ?: DraftPatch())
            } else {
                safeParse(input, text)
            }
        val llmPatch = parsed?.patch ?: DraftPatch()
        val intent =
            when {
                extracted.wantsList || parsed?.intent == Intents.LIST_CAMPAIGNS -> Intents.LIST_CAMPAIGNS
                extracted.wantsImage || parsed?.intent == Intents.GENERATE_IMAGE -> Intents.GENERATE_IMAGE
                extracted.wantsCreative || parsed?.intent == Intents.GENERATE_CREATIVE -> Intents.GENERATE_CREATIVE
                parsed?.intent == Intents.TO_MODERATION -> return moderation(input)
                parsed?.intent == Intents.CONFIRM && lower.split(' ').size <= 4 -> return confirm(input)
                parsed?.intent == Intents.CANCEL && (draft.pendingProposal != null || lower.split(' ').size <= 3) -> return cancel(input)
                parsed?.intent == Intents.QUESTION || parsed == null && text.endsWith("?") -> Intents.QUESTION
                parsed?.intent == Intents.OTHER -> Intents.OTHER
                else -> Intents.CAMPAIGN_EDIT
            }

        val edit = applyEdits(input, llmPatch, extracted, facts, text)
        val validation = CampaignValidator.check(edit.draft, input.catalog, input.today)
        val ready = validation.missing.isEmpty() && edit.draft.pendingProposal == null

        if (ready && llm.supportsTools && intent != Intents.LIST_CAMPAIGNS) {
            design.run(input, edit, intent)?.let { return it }
        }

        return when (intent) {
            Intents.GENERATE_CREATIVE -> creativeReply(input, edit, llmPatch.creativeBrief, extracted.bannerType)
            Intents.GENERATE_IMAGE -> imageReply(input, edit, llmPatch.creativeBrief)
            Intents.LIST_CAMPAIGNS -> list(input, edit.draft)
            Intents.QUESTION -> if (edit.changes.isNotEmpty()) editReply(input, edit) else questionReply(input, edit)
            Intents.OTHER -> if (edit.changes.isNotEmpty()) editReply(input, edit) else otherReply(input, edit)
            else -> editReply(input, edit, unparsed = parsed == null && edit.changes.isEmpty())
        }
    }

    private suspend fun safeParse(
        input: TurnInput,
        text: String,
    ): ParsedIntent? =
        try {
            intents.parse(text, input.draft, CatalogText.render(input.catalog), input.today)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        }

    // ---------- Применение правок ----------

    internal fun applyEdits(
        input: TurnInput,
        llmPatch: DraftPatch,
        extracted: Extracted,
        facts: DateFacts,
        text: String,
    ): EditOutcome {
        val before = input.draft
        val catalog = input.catalog
        val lower = text.lowercase().replace('ё', 'е')
        val notes = mutableListOf<String>()

        val sane = sanitize(llmPatch, extracted, before, catalog, lower)
        var draft = DraftMerger.apply(before, sane)

        extracted.name?.let { draft = draft.copy(name = it) }
        if (before.awaiting == "name" && extracted.name == null && sane.name == null) {
            plainName(text, facts, extracted)?.let { draft = draft.copy(name = it) }
        }
        extracted.topic?.let { draft = draft.copy(topic = it) }
        if (extracted.segments.isNotEmpty()) {
            draft = draft.copy(includedSegments = (draft.includedSegments + extracted.segments).distinct())
        }
        if (extracted.placeIds.isNotEmpty()) draft = draft.copy(placeIds = (draft.placeIds + extracted.placeIds).distinct())
        extracted.audienceType?.let { type ->
            draft =
                if (type == "ALL_USERS") {
                    draft.copy(audienceType = type, includedSegments = emptyList(), excludedSegments = emptyList())
                } else {
                    draft.copy(audienceType = type)
                }
        }
        if (draft.audienceType == null && draft.includedSegments.isNotEmpty()) draft = draft.copy(audienceType = "SEGMENTED")
        if (draft.audienceType == "ALL_USERS" && extracted.segments.isNotEmpty() && extracted.audienceType != "ALL_USERS") {
            draft = draft.copy(audienceType = "SEGMENTED")
        }

        val dateFacts = if (facts.isEmpty && facts.mentioned) llmDates(llmPatch, input.today) else facts
        draft = DateParser.apply(draft, dateFacts)

        val measured = Metrics.ground(before, draft, text)
        draft = measured.draft
        measured.hint?.let { notes += it }

        val resolved = EntityResolver.resolve(draft, sane, catalog)
        draft = resolved.draft
        if (draft.pendingProposal == null && extracted.segmentProposal != null) {
            draft = draft.copy(pendingProposal = extracted.segmentProposal)
        }
        notes += extracted.notes
        resolved.note?.let { notes += it }
        if (extracted.placeSuggestions.isNotEmpty() && extracted.placeIds.isEmpty() && draft.placeIds.isEmpty()) {
            notes += "На главном экране два места: ${extracted.placeSuggestions.joinToString(" и ")}. Выберите нужное."
        }

        val changed = DraftMerger.essence(draft) != DraftMerger.essence(before)
        draft = draft.copy(dirty = before.dirty || (changed && before.savedCampaignId != null))
        val suggestions = (resolved.suggestions + extracted.placeSuggestions).distinct()
        return EditOutcome(
            draft = draft.copy(nameGuess = if (draft.name != null) null else draft.nameGuess),
            changes = DraftView.changes(before, draft, catalog),
            notes = notes.distinct(),
            suggestions = suggestions,
        )
    }

    /**
     * Фильтр догадок модели. Гео, пол и возраст надёжнее понимает код: такие коды от модели принимаются,
     * только если слово есть в реплике. Места — только если в реплике есть слово из описания места.
     */
    private fun sanitize(
        patch: DraftPatch,
        extracted: Extracted,
        before: DraftCampaign,
        catalog: CatalogSnapshot,
        lower: String,
    ): DraftPatch {
        val strictTypes = setOf("GEO_CITY", "GEO_REGION", "GENDER", "AGE")

        fun keepSegment(code: String): Boolean {
            val seg = catalog.segments.find { it.code.equals(code, true) } ?: return true // неизвестный — резолвер скажет «нет такого»
            if (seg.type !in strictTypes) return true
            if (seg.code in extracted.segments) return true
            if (seg.type == "AGE") return false
            val stem =
                seg.name
                    .lowercase()
                    .replace('ё', 'е')
                    .take(4)
            return stem.length >= 4 && lower.contains(stem)
        }

        fun keepPlace(id: Long): Boolean {
            if (id in extracted.placeIds) return true
            val place = catalog.places.find { it.id == id } ?: return false
            val words =
                place.description
                    .lowercase()
                    .split(Regex("""[^\p{L}]+"""))
                    .filter { it.length >= 5 }
            return words.any { lower.contains(it.take(5)) }
        }
        val renaming = Regex("""назов|назван|переимен|имя\s+кампан""").containsMatchIn(lower)
        val name = patch.name?.takeIf { before.name == null || renaming || before.awaiting == "name" }
        return patch.copy(
            name = name,
            includedSegments = patch.includedSegments?.filter(::keepSegment),
            excludedSegments = patch.excludedSegments?.filter(::keepSegment),
            placeIds = patch.placeIds?.filter(::keepPlace),
            topic = patch.topic?.takeIf { code -> catalog.topics.any { it.value == code } },
            topicGuess = patch.topicGuess?.takeIf { code -> catalog.topics.any { it.value == code } },
            audienceType = patch.audienceType?.takeIf { it == "ALL_USERS" || it == "SEGMENTED" },
            startDate = null,
            startTime = null,
            endDate = null,
            endTime = null,
        )
    }

    private fun llmDates(
        patch: DraftPatch,
        today: String,
    ): DateFacts {
        fun date(value: String?): String? =
            value?.takeIf { v ->
                runCatching { java.time.LocalDate.parse(v) }.getOrNull()?.let { !it.isBefore(java.time.LocalDate.parse(today)) } ==
                    true
            }

        fun time(value: String?): String? = value?.takeIf { Regex("""^(?:[01]\d|2[0-3]):[0-5]\d$""").matches(it) }
        return DateFacts(date(patch.startDate), date(patch.endDate), time(patch.startTime), time(patch.endTime))
    }

    /** Ответ на вопрос «как назовём?» — сам текст и есть название, если в нём нет других фактов. */
    private fun plainName(
        text: String,
        facts: DateFacts,
        extracted: Extracted,
    ): String? {
        if (!facts.isEmpty || extracted.segments.isNotEmpty() || extracted.placeIds.isNotEmpty()) return null
        if (text.endsWith("?") || text.length > 80) return null
        val cleaned =
            text
                .replace(Regex("""^(?iu)(пусть\s+будет|давай|назови(?:те)?|название|назовём|назовем)\s*[:—-]?\s*"""), "")
                .trim()
                .trim('«', '»', '"', '\'', '.')
        return cleaned.takeIf { it.length >= 2 }?.replaceFirstChar { it.uppercase() }
    }

    private fun isPureDateAnswer(
        awaiting: String?,
        facts: DateFacts,
        lower: String,
    ): Boolean {
        if (awaiting != "startDate" && awaiting != "startTime") return false
        if (facts.isEmpty) return false
        val rest =
            lower
                .replace(Regex("""\d{1,4}[.:/-]?\d{0,2}[.:/-]?\d{0,4}"""), " ")
                .replace(
                    Regex("""(?<!\p{L})(с|со|в|во|до|по|на|и|старт\p{L}*|начал\p{L}*|запуск\p{L}*|утра|вечера|дня|года?|г)(?!\p{L})"""),
                    " ",
                ).replace(
                    Regex(
                        """январ\p{L}*|феврал\p{L}*|март\p{L}*|апрел\p{L}*|ма[яй]|июн\p{L}*|июл\p{L}*|август\p{L}*|сентябр\p{L}*|октябр\p{L}*|ноябр\p{L}*|декабр\p{L}*|сегодня|завтра|послезавтра""",
                    ),
                    " ",
                ).replace(Regex("""[^\p{L}]"""), "")
        return rest.length <= 3
    }

    // ---------- Ответы ----------

    private fun editReply(
        input: TurnInput,
        edit: EditOutcome,
        unparsed: Boolean = false,
    ): TurnResult {
        val draft = edit.draft
        val catalog = input.catalog
        val validation = CampaignValidator.check(draft, catalog, input.today)
        val blocks = mutableListOf<UiBlock>()
        val freshWarnings = validation.warnings - draft.shownWarnings.toSet()
        if (freshWarnings.isNotEmpty()) blocks += UiBlock.Warnings(freshWarnings)
        val proposal = draft.pendingProposal
        if (proposal != null) blocks += UiBlock.EntityProposalBlock(proposal)

        val head = mutableListOf<String>()
        if (edit.changes.isNotEmpty()) head += "Записал: ${edit.changes.joinToString(", ")}."
        head += edit.notes
        if (unparsed) head += "Не понял, что поменять в кампании."

        val stage = if (validation.missing.isEmpty()) Stage.DESIGN else Stage.BRIEF
        var next = draft
        val tail: String
        when {
            proposal != null -> {
                tail =
                    "Создать ${if (proposal.kind == "TAG") "тег" else "место показа"} «${proposal.name}»? В базу он попадёт только после подтверждения."
                blocks += yesNo()
                next = next.copy(awaiting = "proposal")
            }
            validation.missing.isNotEmpty() -> {
                val field = Questions.pick(validation.missing)!!
                val repeat = if (draft.awaiting == field && edit.changes.isEmpty()) (draft.askCounts[field] ?: 1) + 1 else 1
                val q = askFor(field, validation.missing, draft, input, repeat, edit.suggestions)
                tail = q.text
                blocks += UiBlock.CampaignDraft(draft, validation.missing, false, DraftView.choices(draft, catalog), stage)
                blocks += q.block()
                next = next.copy(awaiting = field, askCounts = mapOf(field to repeat))
            }
            validation.problems.isNotEmpty() -> {
                tail = validation.problems.joinToString(" ")
                next = next.copy(awaiting = null, askCounts = emptyMap())
            }
            else -> {
                tail = readyText(draft)
                blocks += readyButtons(draft)
                next = next.copy(awaiting = null, askCounts = emptyMap())
            }
        }
        if (blocks.none { it is UiBlock.CampaignDraft }) {
            blocks.add(blocks.indexOfFirst { it is UiBlock.QuickReplies }.takeIf { it >= 0 } ?: blocks.size, draftBlock(next, input))
        }
        return TurnResult(AssistantReply((head + tail).joinToString(" "), blocks), next.copy(shownWarnings = validation.warnings))
    }

    private fun askFor(
        field: String,
        missing: List<String>,
        draft: DraftCampaign,
        input: TurnInput,
        repeat: Int,
        suggestions: List<String> = emptyList(),
    ): Question {
        val q = Questions.build(field, draft, input.catalog, input.today, suggestions, repeat)
        if (field == "startDate" && "startTime" in missing && repeat < 2) {
            return q.copy(text = "Когда запускаем? Нужны дата и время старта, например «10 ноября в 10:00».")
        }
        return q
    }

    private fun readyText(draft: DraftCampaign): String =
        when {
            draft.savedCampaignId != null && !draft.dirty ->
                "Кампания сохранена. Можно доработать баннер или написать «отправь на модерацию»."
            draft.savedCampaignId != null -> "Есть несохранённые правки. Нажмите «Сохранить», чтобы записать их."
            draft.banner == null -> "Все обязательные поля заполнены. Можно сохранить кампанию или сначала сделать баннер."
            else -> "Черновик готов. Нажмите «Сохранить», чтобы записать кампанию."
        }

    private fun readyButtons(draft: DraftCampaign): UiBlock.QuickReplies {
        val items = mutableListOf<ReplyOption>()
        if (draft.savedCampaignId == null || draft.dirty) items += ReplyOption("Сохранить", "Сохранить", recommended = true)
        if (draft.banner == null) items += ReplyOption("Сделать баннер", "Сделай баннер")
        if (draft.banner != null &&
            draft.banner.imageUrl == null
        ) {
            items += ReplyOption("Нарисовать картинку", "Сделай картинку для баннера")
        }
        if (draft.savedCampaignId != null && !draft.dirty) items += ReplyOption("На модерацию", "Отправь на модерацию", recommended = true)
        return UiBlock.QuickReplies(items.map { it.value }, field = "next", items = items)
    }

    private fun draftBlock(
        draft: DraftCampaign,
        input: TurnInput,
    ): UiBlock.CampaignDraft {
        val v = CampaignValidator.check(draft, input.catalog, input.today)
        return UiBlock.CampaignDraft(
            draft,
            v.missing,
            v.readyToSave,
            DraftView.choices(draft, input.catalog),
            if (v.missing.isEmpty()) Stage.DESIGN else Stage.BRIEF,
        )
    }

    private fun confirm(input: TurnInput): TurnResult {
        val draft = input.draft
        val proposal = draft.pendingProposal
        if (proposal != null) {
            val action =
                when (proposal.kind) {
                    "TAG" -> CommitAction.CREATE_TAG
                    "SEGMENT" -> CommitAction.CREATE_SEGMENT
                    else -> CommitAction.CREATE_PLACE
                }
            return TurnResult(
                AssistantReply("Создаю «${proposal.name}» и привязываю к черновику.", listOf(UiBlock.EntityProposalBlock(proposal))),
                draft.copy(awaiting = null),
                CommitRequest(action),
            )
        }
        if (draft.awaiting == "stop" && draft.savedCampaignId != null) {
            return TurnResult(
                AssistantReply("Останавливаю показ.", emptyList()),
                draft.copy(awaiting = null),
                CommitRequest(CommitAction.STOP, draft.savedCampaignId),
            )
        }
        if (draft.awaiting == "name" && draft.nameGuess != null) {
            return editReply(input, outcome(input, draft.copy(name = draft.nameGuess, nameGuess = null)))
        }
        if (draft.awaiting == "topic" && draft.topicGuess != null) {
            return editReply(input, outcome(input, draft.copy(topic = draft.topicGuess)))
        }
        val validation = CampaignValidator.check(draft, input.catalog, input.today)
        if (validation.readyToSave && (draft.savedCampaignId == null || draft.dirty)) {
            val text = if (draft.savedCampaignId == null) "Сохраняю кампанию." else "Сохраняю изменения кампании."
            return TurnResult(
                AssistantReply(text, listOf(draftBlock(draft, input))),
                draft.copy(awaiting = null, askCounts = emptyMap()),
                CommitRequest(CommitAction.SAVE_CAMPAIGN),
            )
        }
        if (draft.savedCampaignId != null) {
            val items = listOf(ReplyOption("На модерацию", "Отправь на модерацию", recommended = true))
            return TurnResult(
                AssistantReply(
                    "Кампания уже сохранена, новых правок нет. Чтобы отправить её на модерацию, напишите «отправь на модерацию».",
                    listOf(UiBlock.QuickReplies(items.map { it.value }, field = "next", items = items)),
                ),
                draft,
            )
        }
        val edit = EditOutcome(draft, emptyList(), listOf(cannotSave(validation)))
        return editReply(input, edit)
    }

    private fun outcome(
        input: TurnInput,
        after: DraftCampaign,
    ): EditOutcome {
        val dirty =
            input.draft.dirty || (input.draft.savedCampaignId != null && DraftMerger.essence(after) != DraftMerger.essence(input.draft))
        return EditOutcome(after.copy(dirty = dirty), DraftView.changes(input.draft, after, input.catalog), emptyList())
    }

    private fun cannotSave(validation: ValidationResult): String =
        when {
            validation.missing.isNotEmpty() ->
                "Сохранить пока нельзя: не хватает — ${validation.missing.joinToString(", ") { Questions.FIELD_LABELS[it] ?: it }}."
            validation.problems.isNotEmpty() -> "Сохранить пока нельзя: ${validation.problems.joinToString(" ")}"
            else -> "Сохранить пока нельзя."
        }

    private fun moderation(input: TurnInput): TurnResult {
        val draft = input.draft
        val id = draft.savedCampaignId
        val save = listOf(ReplyOption("Сохранить", "Сохранить", recommended = true))
        val saveBlock = UiBlock.QuickReplies(save.map { it.value }, field = "next", items = save)
        if (id == null) {
            val validation = CampaignValidator.check(draft, input.catalog, input.today)
            if (!validation.readyToSave) {
                return editReply(
                    input,
                    EditOutcome(draft, emptyList(), listOf("На модерацию отправляется сохранённая кампания. ${cannotSave(validation)}")),
                )
            }
            return TurnResult(
                AssistantReply("Сначала сохраните кампанию, потом отправлю её на модерацию.", listOf(draftBlock(draft, input), saveBlock)),
                draft,
            )
        }
        if (draft.dirty) {
            return TurnResult(
                AssistantReply(
                    "В черновике есть несохранённые правки. Сохраните их, потом отправлю на модерацию.",
                    listOf(draftBlock(draft, input), saveBlock),
                ),
                draft,
            )
        }
        val state = input.campaigns.find { it.id == id }?.state
        return when (state) {
            "ON_MODERATION" -> TurnResult(AssistantReply("Кампания уже на модерации.", emptyList()), draft)
            null, "DRAFT", "NEEDS_WORK" ->
                TurnResult(
                    AssistantReply("Отправляю кампанию «${draft.name}» на модерацию.", emptyList()),
                    draft.copy(awaiting = null),
                    CommitRequest(CommitAction.TO_MODERATION),
                )
            else -> TurnResult(AssistantReply("Из состояния $state на модерацию отправить нельзя.", emptyList()), draft)
        }
    }

    /** Точная правка текста баннера. Если баннера ещё нет — сначала делаем его, потом подставляем текст. */
    private suspend fun editBanner(
        input: TurnInput,
        fields: BannerEdit.Fields,
    ): TurnResult {
        var draft = input.draft
        if (draft.banner == null) {
            val made = creatives.banner(input, draft, input.userText)
            if (made.problems.isNotEmpty()) return TurnResult(AssistantReply(made.problems.joinToString(" "), emptyList()), draft)
            draft = made.draft
        }
        val old = draft.banner!!
        val edited = old.copy(title = fields.title ?: old.title, text = fields.text ?: old.text, cta = fields.cta ?: old.cta)
        val problems = Creatives.bannerProblems(edited, input.catalog)
        if (problems.isNotEmpty()) return TurnResult(AssistantReply(problems.joinToString(" "), emptyList()), input.draft)
        val report = creatives.compliance.check(edited, draft.name)
        val next = draft.copy(banner = edited.copy(compliance = report), dirty = input.draft.dirty || input.draft.savedCampaignId != null)
        val what =
            listOfNotNull(
                fields.title?.let { "заголовок «$it»" },
                fields.text?.let { "текст «$it»" },
                fields.cta?.let { "кнопку «$it»" },
            ).joinToString(", ")
        return withBanner(input, EditOutcome(next, emptyList(), emptyList()), next, "Поменял $what.")
    }

    /** «Укажи тег»: известные теги ставит код; новый в кавычках — предложение создать; иначе — меню тегов. */
    private fun tagsReply(
        input: TurnInput,
        text: String,
    ): TurnResult {
        val draft = input.draft
        val lower = QuickAnswer.norm(text)
        val known =
            input.catalog.tags
                .filter { lower.contains(QuickAnswer.norm(it.name)) }
                .map { it.id }
        val quotedNew =
            Regex("""[«"“]([^«»"“”]{2,40})[»"”]""")
                .find(text)
                ?.groupValues
                ?.get(1)
                ?.trim()
                ?.takeIf { name -> input.catalog.tags.none { QuickAnswer.norm(it.name) == QuickAnswer.norm(name) } }
                ?: TAG_NAME.find(text)?.groupValues?.get(1)?.trim()?.takeIf { name ->
                    QuickAnswer.norm(name).split(' ').none { it in TAG_STOP } &&
                        input.catalog.tags.none { QuickAnswer.norm(it.name) == QuickAnswer.norm(name) }
                }
        if (known.isNotEmpty() || quotedNew != null) {
            var next = draft.copy(tagIds = (draft.tagIds + known).distinct())
            if (quotedNew !=
                null
            ) {
                next = next.copy(pendingProposal = EntityProposal(kind = "TAG", name = quotedNew, description = quotedNew))
            }
            return editReply(input, outcome(input, next))
        }
        val items = input.catalog.tags.map { ReplyOption(it.name, it.name, recommended = it.id in draft.tagIds) }
        val q =
            UiBlock.QuickReplies(
                items.map { it.value },
                field = "tags",
                question = "Какие теги поставить?",
                multi = true,
                allowCustom = true,
                items = items,
            )
        return TurnResult(
            AssistantReply("Какие теги поставить? Можно выбрать несколько или написать новый — создам после «Да».", listOf(q)),
            draft.copy(awaiting = "tags"),
        )
    }

    private suspend fun complianceReply(input: TurnInput): TurnResult {
        val draft = input.draft
        val banner =
            draft.banner
                ?: return TurnResult(
                    AssistantReply(
                        "Проверять пока нечего: сначала сделаем баннер.",
                        listOf(
                            UiBlock.QuickReplies(
                                listOf("Сделай баннер"),
                                field = "next",
                                items = listOf(ReplyOption("Сделать баннер", "Сделай баннер")),
                            ),
                        ),
                    ),
                    draft,
                )
        val report = creatives.compliance.check(banner, draft.name)
        val next = draft.copy(banner = banner.copy(compliance = report))
        return TurnResult(AssistantReply(complianceText(report), complianceBlocks(next, report)), next)
    }

    private suspend fun fixCompliance(input: TurnInput): TurnResult {
        val draft = input.draft
        val report = draft.banner?.compliance ?: creatives.compliance.check(draft.banner!!, draft.name, withAi = false)
        val fixes = report.items.filter { it.status == "WARN" || it.status == "FAIL" }.mapNotNull { it.fix ?: it.message }
        if (fixes.isEmpty()) return TurnResult(AssistantReply("Замечаний к тексту нет, исправлять нечего.", emptyList()), draft)
        val brief = (draft.creativeBrief?.let { "$it. " } ?: "") + "Исправь замечания юриста: " + fixes.joinToString("; ")
        val result = creatives.banner(input, draft, brief)
        if (result.problems.isNotEmpty()) return TurnResult(AssistantReply(result.problems.joinToString(" "), emptyList()), draft)
        val next = result.draft.copy(dirty = draft.dirty || draft.savedCampaignId != null)
        val newReport = next.banner!!.compliance!!
        val text = "Переписал текст с учётом замечаний. " + complianceText(newReport)
        return TurnResult(
            AssistantReply(text, listOf<UiBlock>(UiBlock.CreativePreview(next.banner)) + complianceBlocks(next, newReport)),
            next,
        )
    }

    private fun complianceText(report: ru.zhma.campaign.ai.model.ComplianceReport): String {
        val problems = report.items.count { it.status == "WARN" || it.status == "FAIL" }
        val who = if (report.aiChecked) "Правила 38-ФЗ и ИИ-юрист" else "Правила 38-ФЗ (ИИ-юрист недоступен)"
        return when (report.verdict) {
            "OK" -> "$who: явных нарушений закона «О рекламе» нет."
            "FAIL" -> "$who: есть нарушения — с таким текстом модерацию лучше не проходить. Замечаний: $problems."
            else -> "$who: есть что поправить, замечаний: $problems. Сохранение это не блокирует."
        }
    }

    private fun complianceBlocks(
        draft: DraftCampaign,
        report: ru.zhma.campaign.ai.model.ComplianceReport,
    ): List<UiBlock> {
        val items = mutableListOf<ReplyOption>()
        if (report.verdict != "OK") items += ReplyOption("Исправить замечания", "Исправь замечания юриста", recommended = true)
        if (draft.savedCampaignId == null || draft.dirty) items += ReplyOption("Сохранить", "Сохранить")
        return listOf(UiBlock.Compliance(report), UiBlock.QuickReplies(items.map { it.value }, field = "next", items = items))
    }

    /** «Останови показ» — для активной кампании, только после «Да». */
    private fun stop(input: TurnInput): TurnResult? {
        val draft = input.draft
        val id = draft.savedCampaignId ?: return null
        val state = input.campaigns.find { it.id == id }?.state ?: return null
        if (state != "ACTIVE" && state != "ACTIVE_EDITING") {
            return TurnResult(
                AssistantReply("Останавливать нечего: кампания не в показе (${ModeratorTurn.stateLabel(state)}).", emptyList()),
                draft,
            )
        }
        return TurnResult(
            AssistantReply(
                "Остановить показ кампании «${draft.name}»? Она перестанет показываться клиентам, данные не удалятся.",
                listOf(yesNo()),
            ),
            draft.copy(awaiting = "stop"),
        )
    }

    /** «Покажи журнал» — чтение, выполняется сразу: сервер подставит записи аудита. */
    private fun audit(input: TurnInput): TurnResult? {
        val id =
            input.draft.savedCampaignId
                ?: return TurnResult(AssistantReply("Журнал появится после сохранения кампании.", emptyList()), input.draft)
        return TurnResult(AssistantReply("Журнал кампании.", emptyList()), input.draft, CommitRequest(CommitAction.SHOW_AUDIT, id))
    }

    /** «Запусти кампанию» — только для сохранённой и одобренной модератором. Иначе фраза идёт обычным разбором. */
    private fun activate(input: TurnInput): TurnResult? {
        val draft = input.draft
        val id = draft.savedCampaignId ?: return null
        val state = input.campaigns.find { it.id == id }?.state ?: return null
        return when (state) {
            "APPROVED" ->
                TurnResult(
                    AssistantReply("Запускаю кампанию «${draft.name}».", emptyList()),
                    draft,
                    CommitRequest(CommitAction.ACTIVATE, id),
                )
            "ACTIVE" -> TurnResult(AssistantReply("Кампания уже запущена.", emptyList()), draft)
            "ON_MODERATION" -> TurnResult(AssistantReply("Кампания ещё на модерации: запустить можно после одобрения.", emptyList()), draft)
            else -> null
        }
    }

    private fun open(
        input: TurnInput,
        id: Long,
    ): TurnResult {
        val campaign =
            input.campaigns.find { it.id == id }
                ?: return TurnResult(
                    AssistantReply("Кампании №$id нет. Напишите «покажи кампании», чтобы увидеть список.", emptyList()),
                    input.draft,
                )
        return TurnResult(
            AssistantReply("Открываю кампанию «${campaign.name}».", emptyList()),
            input.draft,
            CommitRequest(CommitAction.LOAD_CAMPAIGN, id),
        )
    }

    private fun cancel(input: TurnInput): TurnResult {
        val draft = input.draft
        val text = if (draft.pendingProposal != null) "Хорошо, не создаю «${draft.pendingProposal.name}»." else "Хорошо, ничего не меняю."
        val next =
            draft.copy(
                pendingProposal = null,
                awaiting =
                    if (draft.awaiting == "proposal" ||
                        draft.awaiting == "stop"
                    ) {
                        null
                    } else {
                        draft.awaiting
                    },
            )
        return TurnResult(AssistantReply(text, listOf(draftBlock(next, input))), next)
    }

    private fun list(
        input: TurnInput,
        draft: DraftCampaign,
    ): TurnResult {
        val text = if (input.campaigns.isEmpty()) "Сохранённых кампаний пока нет." else "Список кампаний."
        return TurnResult(AssistantReply(text, listOf(UiBlock.CampaignList(input.campaigns))), draft)
    }

    private fun greet(input: TurnInput): TurnResult {
        val example = "Кампания на баню и спа в Сочи для женщин, сторис на главном, до конца года"
        val items = listOf(ReplyOption("Пример брифа", example))
        return TurnResult(
            AssistantReply(
                "Опишите кампанию одной фразой: что рекламируем, для кого, где показать и когда. Например: «$example».",
                listOf(UiBlock.QuickReplies(items.map { it.value }, items = items)),
            ),
            input.draft,
        )
    }

    private suspend fun questionReply(
        input: TurnInput,
        edit: EditOutcome,
    ): TurnResult {
        val draft = edit.draft
        val validation = CampaignValidator.check(draft, input.catalog, input.today)
        val answer = answerFreely(input, draft, validation.missing)
        val blocks = mutableListOf<UiBlock>()
        var next = draft
        var tail = ""
        val field = Questions.pick(validation.missing)
        if (field != null && draft.pendingProposal == null) {
            val q = askFor(field, validation.missing, draft, input, 1, edit.suggestions)
            blocks += draftBlock(draft, input)
            blocks += q.block()
            tail = " " + q.text
            next = draft.copy(awaiting = field)
        } else if (validation.missing.isEmpty()) {
            blocks += readyButtons(draft)
        }
        return TurnResult(AssistantReply(answer + tail, blocks), next)
    }

    internal suspend fun answerFreely(
        input: TurnInput,
        draft: DraftCampaign,
        missing: List<String>,
    ): String {
        val history =
            input.history.takeLast(6).joinToString("\n") { (if (it.role == "user") "Менеджер: " else "Ассистент: ") + it.text.take(400) }
        val user = if (history.isBlank()) input.userText else "Недавний диалог:\n$history\n\nВопрос менеджера: ${input.userText}"
        return try {
            llm
                .chat(
                    system =
                        Prompts.answer(
                            input.today,
                            CatalogText.render(input.catalog),
                            DraftView.summary(draft, input.catalog),
                            missing.joinToString(", ") { Questions.FIELD_LABELS[it] ?: it },
                        ),
                    user = user,
                    temperature = 0.3,
                    json = false,
                ).trim()
                .ifBlank { FALLBACK_ANSWER }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            FALLBACK_ANSWER
        }
    }

    private suspend fun otherReply(
        input: TurnInput,
        edit: EditOutcome,
    ): TurnResult {
        val draft = edit.draft
        if (DraftMerger.essence(draft).all { it == null || it == emptyList<Any>() }) return greet(input)
        return questionReply(input, edit)
    }

    private suspend fun creativeReply(
        input: TurnInput,
        edit: EditOutcome,
        brief: String?,
        bannerType: String?,
    ): TurnResult {
        val result = creatives.banner(input, edit.draft, brief ?: input.userText, bannerType)
        if (result.problems.isNotEmpty()) {
            return TurnResult(AssistantReply(result.problems.joinToString(" "), listOf(draftBlock(edit.draft, input))), edit.draft)
        }
        val note = listOfNotNull("Вот вариант баннера. В базу он попадёт после сохранения.", result.message).joinToString(" ")
        return withBanner(input, edit, result.draft, note)
    }

    private suspend fun imageReply(
        input: TurnInput,
        edit: EditOutcome,
        brief: String?,
    ): TurnResult {
        val result = creatives.image(input, edit.draft, brief ?: input.userText)
        if (result.problems.isNotEmpty()) {
            return TurnResult(AssistantReply(result.problems.joinToString(" "), listOf(draftBlock(edit.draft, input))), edit.draft)
        }
        return withBanner(input, edit, result.draft, result.message ?: "Рисую картинку для баннера, она появится в превью.")
    }

    private fun withBanner(
        input: TurnInput,
        edit: EditOutcome,
        draft: DraftCampaign,
        message: String,
    ): TurnResult {
        val saved = input.draft.savedCampaignId != null
        val next = draft.copy(dirty = input.draft.dirty || saved)
        val banner = next.banner!!
        val blocks = mutableListOf<UiBlock>(UiBlock.CreativePreview(banner))
        banner.compliance?.let { blocks += UiBlock.Compliance(it) }
        val validation = CampaignValidator.check(next, input.catalog, input.today)
        val head = mutableListOf<String>()
        if (edit.changes.isNotEmpty()) head += "Записал: ${edit.changes.joinToString(", ")}."
        head += edit.notes
        head += message
        banner.compliance?.takeIf { message.startsWith("Вот вариант") || message.startsWith("Поменял") }?.let { head += complianceText(it) }
        var result = next
        if (validation.missing.isNotEmpty()) {
            val field = Questions.pick(validation.missing)!!
            val q = askFor(field, validation.missing, next, input, 1)
            head += "Для сохранения ещё нужно: ${validation.missing.joinToString(", ") { Questions.FIELD_LABELS[it] ?: it }}. ${q.text}"
            blocks += draftBlock(next, input)
            blocks += q.block()
            result = next.copy(awaiting = field, askCounts = mapOf(field to 1))
        } else {
            val items = mutableListOf(ReplyOption("Другой вариант", "Сделай другой вариант баннера"))
            if (banner.compliance?.verdict in setOf("WARN", "FAIL")) {
                items.add(0, ReplyOption("Исправить замечания", "Исправь замечания юриста", recommended = true))
            }
            if (images != null && banner.imageUrl == null) items += ReplyOption("Нарисовать картинку", "Сделай картинку для баннера")
            if (images != null && banner.imageUrl != null) items += ReplyOption("Другая картинка", "Сделай другую картинку")
            items += ReplyOption("Сохранить", "Сохранить", recommended = true)
            blocks += draftBlock(next, input)
            blocks += UiBlock.QuickReplies(items.map { it.value }, field = "next", items = items)
            result = next.copy(awaiting = null, askCounts = emptyMap())
        }
        return TurnResult(AssistantReply(head.joinToString(" "), blocks), result)
    }

    private companion object {
        val CANCEL_WORDS = setOf("отмена", "отменить", "отмени", "не надо", "нет", "не нужно", "не создавай", "отказано", "отказ")

        fun yesNo(): UiBlock.QuickReplies {
            val items = listOf(ReplyOption("Да", "Да", recommended = true), ReplyOption("Отказано", "Отказано"))
            return UiBlock.QuickReplies(items.map { it.value }, field = "proposal", items = items, allowCustom = false)
        }

        val CONFIRM =
            Regex(
                """^(да|ага|ок|окей|давай|подтверждаю|подтвердить|подтверди|сохранить|сохрани|сохраняй|сохраняем|согласен|согласна|верно|все верно|да, сохрани|да сохрани|да, сохранить|записывай|создавай)$""",
            )
        val MODERATION = Regex("""модерац""")
        val TAG_NAME = Regex("""(?iu)(?<!\p{L})(?:тег|тэг)\p{L}*\s+([\p{L}\d][\p{L}\d\- ]{1,30}?)\s*[.!]?$""")
        val TAG_STOP =
            setOf(
                "кампании",
                "кампанию",
                "кампания",
                "для",
                "к",
                "на",
                "какой",
                "какие",
                "нужен",
                "нужно",
                "тоже",
                "ещё",
                "еще",
                "бег",
                "без",
            )
        val TAGS = Regex("""(?<!\p{L})(тег|тега|теги|тегов|тегом|тэг\p{L}*|метк\p{L}*)(?!\p{L})""")

        /** Команды («покажи журнал», «запусти кампанию») короткие; длинная реплика — это бриф, а не команда. */
        const val COMMAND_MAX = 60
        val COMPLIANCE =
            Regex(
                """(провер\p{L}*.{0,40}(закон|юрист|фас|38-фз|соответств|законност)|соответству\p{L}*\s+(ли\s+)?закону|юридическ\p{L}*\s+проверк|законн\p{L}*\s+ли)""",
            )
        val FIX_COMPLIANCE = Regex("""(исправ\p{L}*|учти|учесть|перепиш\p{L}*|поправ\p{L}*).{0,30}(замечан|закон|юрист)""")
        val STOP =
            Regex(
                """(?<!\p{L})(останови\p{L}*|остановить|выключи|сними\s+с\s+показа|прекрати\p{L}*)\s*(показ\p{L}*|кампани\p{L}*|рекламу)""",
            )
        val AUDIT = Regex("""(?<!\p{L})(журнал\p{L}*|аудит(?!ори)\p{L}*|истори\p{L}*\s+(изменений|кампании)|кто\s+(менял|правил))""")
        val ACTIVATE =
            Regex("""(?<!\p{L})(запусти\p{L}*|активируй|активировать|опубликуй|включи)\s+(кампани\p{L}*|показ\p{L}*|е[её]|рекламу)""")
        val OPEN =
            Regex(
                """(?:открой|открыть|загрузи|редактиру\p{L}*|поправ\p{L}*|исправ\p{L}*|измени\p{L}*|продолж\p{L}*)\s+кампани\p{L}*\s*(?:№|#)?\s*(\d+)""",
            )
        const val FALLBACK_ANSWER = "Не получилось ответить на вопрос: модель сейчас недоступна. Продолжим заполнять кампанию."
    }
}
