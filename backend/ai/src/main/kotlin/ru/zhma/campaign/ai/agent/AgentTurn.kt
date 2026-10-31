package ru.zhma.campaign.ai.agent

import ru.zhma.campaign.ai.llm.LlmClient
import ru.zhma.campaign.ai.llm.StructuredLlm
import ru.zhma.campaign.ai.model.AssistantReply
import ru.zhma.campaign.ai.model.CatalogSnapshot
import ru.zhma.campaign.ai.model.CatalogText
import ru.zhma.campaign.ai.model.CommitAction
import ru.zhma.campaign.ai.model.CommitRequest
import ru.zhma.campaign.ai.model.DraftCampaign
import ru.zhma.campaign.ai.model.ParsedIntent
import ru.zhma.campaign.ai.model.TurnInput
import ru.zhma.campaign.ai.model.TurnResult
import ru.zhma.campaign.ai.model.UiBlock
import ru.zhma.campaign.ai.prompt.Prompts

class AgentTurn(
    private val llm: LlmClient,
) {
    private val structured = StructuredLlm(llm)
    private val intents = IntentParser(structured)

    suspend fun handle(input: TurnInput): TurnResult {
        if (isBareConfirm(input.userText)) return confirm(input)
        val parsed =
            intents.parse(
                text = input.userText,
                draft = input.draft,
                catalogText = CatalogText.render(input.catalog),
                today = input.today,
            )
        if (parsed.intent == "CANCEL") return cancel(input)
        if (parsed.intent == "CONFIRM" || parsed.confirm == true) return confirm(input)
        return when (parsed.intent) {
            "GENERATE_CREATIVE" -> creative(input, parsed)
            "LIST_CAMPAIGNS" -> list(input)
            "OTHER" -> other(input)
            else -> edit(input, parsed)
        }
    }

    private suspend fun edit(
        input: TurnInput,
        parsed: ParsedIntent,
    ): TurnResult {
        val merged = DraftMerger.apply(input.draft, parsed.patch)
        val dated =
            RelativeDates.ground(
                RelativeDates.keepSpokenDates(input.draft, merged),
                input.userText,
                input.today,
            )
        val measured = groundMetrics(input.draft, dated, input.userText)
        val resolved = EntityResolver.resolve(measured.draft, parsed.patch, input.catalog)
        val validation = CampaignValidator.check(resolved.draft, input.catalog, input.today)
        val blocks = mutableListOf<UiBlock>()
        if (validation.warnings.isNotEmpty()) blocks += UiBlock.Warnings(validation.warnings)
        val proposal = resolved.draft.pendingProposal
        if (proposal != null) blocks += UiBlock.EntityProposalBlock(proposal)

        val options =
            when {
                proposal != null -> listOf("подтверждаю", "отмена")
                validation.readyToSave -> listOf("Сохранить")
                else -> optionsFor(validation.missing.firstOrNull(), input.catalog, resolved.suggestions)
            }
        if (validation.readyToSave || validation.problems.isNotEmpty()) {
            blocks += UiBlock.CampaignDraft(resolved.draft, validation.missing, validation.readyToSave)
        }
        if (options.isNotEmpty()) blocks += UiBlock.QuickReplies(options)

        val question =
            when {
                proposal != null -> "Могу создать ${proposalLabel(proposal.kind)} «${proposal.name}». Подтвердите."
                validation.missing.isNotEmpty() -> polish(input, validation.missing, options, fallbackQuestion(validation.missing))
                validation.problems.isNotEmpty() -> validation.problems.joinToString(" ")
                else -> "Черновик собран. Нажмите «Сохранить», чтобы записать кампанию."
            }
        val text = listOfNotNull(resolved.note, measured.hint, question).joinToString(" ")
        return TurnResult(AssistantReply(text, blocks), resolved.draft, commit = null)
    }

    private fun confirm(input: TurnInput): TurnResult {
        val draft = input.draft
        val proposal = draft.pendingProposal
        if (proposal != null) {
            val action = if (proposal.kind == "TAG") CommitAction.CREATE_TAG else CommitAction.CREATE_PLACE
            return TurnResult(
                reply =
                    AssistantReply(
                        text = "Создаю «${proposal.name}» и привязываю к черновику.",
                        blocks = listOf(UiBlock.EntityProposalBlock(proposal)),
                    ),
                draft = draft,
                commit = CommitRequest(action),
            )
        }
        val validation = CampaignValidator.check(draft, input.catalog, input.today)
        if (validation.readyToSave && draft.savedCampaignId == null) {
            return TurnResult(
                reply =
                    AssistantReply(
                        text = "Сохраняю кампанию. После записи её можно отправить на модерацию.",
                        blocks = listOf(UiBlock.CampaignDraft(draft, validation.missing, true)),
                    ),
                draft = draft,
                commit = CommitRequest(CommitAction.SAVE_CAMPAIGN),
            )
        }
        if (draft.savedCampaignId != null && Regex("""модерац""", RegexOption.IGNORE_CASE).containsMatchIn(input.userText)) {
            return TurnResult(
                reply = AssistantReply("Отправляю кампанию на модерацию.", emptyList()),
                draft = draft,
                commit = CommitRequest(CommitAction.TO_MODERATION),
            )
        }
        val text =
            if (draft.savedCampaignId != null) {
                "Кампания уже сохранена. Напишите, если её нужно отправить на модерацию."
            } else {
                "Пока нечего сохранять. " + fallbackQuestion(validation.missing)
            }
        return TurnResult(AssistantReply(text, emptyList()), draft, commit = null)
    }

    private fun cancel(input: TurnInput): TurnResult =
        TurnResult(
            reply = AssistantReply("Предложение снято.", emptyList()),
            draft = input.draft.copy(pendingProposal = null),
            commit = null,
        )

    private fun list(input: TurnInput): TurnResult {
        val text = if (input.campaigns.isEmpty()) "Сохранённых кампаний пока нет." else "Список кампаний."
        return TurnResult(
            reply = AssistantReply(text, listOf(UiBlock.CampaignList(input.campaigns))),
            draft = input.draft,
            commit = null,
        )
    }

    private fun other(input: TurnInput): TurnResult =
        TurnResult(
            reply =
                AssistantReply(
                    "Я собираю рекламные кампании: параметры, аудиторию, места показа и баннер. Опишите, что нужно завести.",
                    emptyList(),
                ),
            draft = input.draft,
            commit = null,
        )

    private suspend fun creative(
        input: TurnInput,
        parsed: ParsedIntent,
    ): TurnResult {
        val brief = parsed.patch.creativeBrief ?: input.userText
        val draft = input.draft.copy(creativeBrief = brief)
        val bannerType =
            if (Regex("""сторис|story""", RegexOption.IGNORE_CASE).containsMatchIn(input.userText)) {
                "STORY"
            } else {
                "SMART_BANNER"
            }
        val channel = chooseChannel(input.userText)
        val link = Regex("""https?://\S+""").find(input.userText)?.value
        val actionType = if (link != null) "externalLink" else "default"
        val copies =
            structured.ask(
                system =
                    Prompts.bannerCopy(
                        product = draft.name ?: brief,
                        audience = draft.includedSegments.joinToString().ifBlank { "не выбрана" },
                        placement =
                            draft.placeIds
                                .mapNotNull { id ->
                                    input.catalog.places
                                        .find { it.id == id }
                                        ?.description
                                }.joinToString()
                                .ifBlank { "не выбрано" },
                        n = 1,
                    ),
                user = brief,
                serializer = BannerCopyCodec.listSerializer,
                validate = BannerCopyCodec::validate,
            )
        val banner = BannerCopyCodec.toDraft(copies.first(), bannerType, channel, actionType, link)
        val withBanner = draft.copy(banner = banner)
        val problems =
            CampaignValidator
                .check(withBanner, input.catalog, input.today)
                .problems
                .filter { problem ->
                    problem.contains("Заголовок") ||
                        problem.contains("Текст") ||
                        problem.contains("ссылк") ||
                        problem.contains("Сторис") ||
                        problem.contains("Канал")
                }
        if (problems.isNotEmpty()) {
            return TurnResult(AssistantReply(problems.joinToString(" "), emptyList()), draft, commit = null)
        }
        return TurnResult(
            reply =
                AssistantReply(
                    "Вот вариант баннера. В кампанию он попадёт после сохранения.",
                    listOf(UiBlock.CreativePreview(banner)),
                ),
            draft = withBanner,
            commit = null,
        )
    }

    private suspend fun polish(
        input: TurnInput,
        missing: List<String>,
        options: List<String>,
        fallback: String,
    ): String {
        val missingText = missing.joinToString(", ") { FIELD_LABELS[it] ?: it }
        val optionsText = options.joinToString(", ")
        return runCatching {
            llm.chat(
                system = Prompts.askMissing(missingText, optionsText),
                user = input.userText,
                temperature = 0.5,
                json = false,
            )
        }.getOrElse { fallback }.ifBlank { fallback }
    }

    private fun groundMetrics(
        before: DraftCampaign,
        merged: DraftCampaign,
        userText: String,
    ): MeasuredDraft {
        val weightNumber = Regex("""(?i)(?:вес|weight|охват)\D{0,15}(\d+(?:[.,]\d+)?)""").find(userText)
        val ctrNumber = Regex("""(?i)ctr\D{0,10}(\d+(?:[.,]\d+)?)""").find(userText)
        val hint =
            if (
                weightNumber == null &&
                Regex("""(?i)широк\p{L}*\s+охват|максимальн\p{L}*\s+охват""").containsMatchIn(userText)
            ) {
                "Для широкого охвата могу поставить вес 10. Назовите число, если согласны."
            } else {
                null
            }
        val weight =
            weightNumber
                ?.groupValues
                ?.get(1)
                ?.replace(',', '.')
                ?.toDouble() ?: before.weight
        val ctr =
            ctrNumber
                ?.groupValues
                ?.get(1)
                ?.replace(',', '.')
                ?.toDouble() ?: before.initialCtr
        return MeasuredDraft(merged.copy(weight = weight, initialCtr = ctr), hint)
    }

    private data class MeasuredDraft(
        val draft: DraftCampaign,
        val hint: String?,
    )

    private companion object {
        val FIELD_LABELS =
            mapOf(
                "name" to "название",
                "startDate" to "дату начала",
                "startTime" to "время начала",
                "topic" to "тему",
                "audienceType" to "тип аудитории",
                "places" to "места показа",
                "segments" to "сегменты",
            )

        fun isBareConfirm(text: String): Boolean = text.trim().lowercase() in setOf("подтверждаю", "сохранить", "сохрани", "да")

        fun proposalLabel(kind: String): String = if (kind == "TAG") "тег" else "место показа"

        fun fallbackQuestion(missing: List<String>): String {
            if (missing.isEmpty()) return "Уточните, что подтвердить."
            val labels = missing.joinToString(", ") { FIELD_LABELS[it] ?: it }
            return "Нужно уточнить: $labels."
        }

        fun optionsFor(
            code: String?,
            catalog: CatalogSnapshot,
            suggestions: List<String>,
        ): List<String> {
            val raw =
                when (code) {
                    "topic" -> catalog.topics.map { it.label }
                    "audienceType" -> listOf("Все пользователи", "Сегмент")
                    "places" -> catalog.places.filter { it.state == "ACTIVE" }.map { it.description }
                    "segments" -> suggestions.ifEmpty { catalog.segments.filter { it.state == "ACTIVE" }.map { it.name } }
                    else -> emptyList()
                }
            return raw.distinct().take(5).let { if (it.size >= 2) it else emptyList() }
        }

        fun chooseChannel(text: String): String {
            val web = Regex("""\bweb\b""", RegexOption.IGNORE_CASE).containsMatchIn(text)
            val ios = Regex("""\bios\b""", RegexOption.IGNORE_CASE).containsMatchIn(text)
            return when {
                ios -> "iOS"
                web -> "WEB"
                else -> "ANDROID"
            }
        }
    }
}
