package ru.zhma.campaign.ai.agent

import ru.zhma.campaign.ai.model.AssistantReply
import ru.zhma.campaign.ai.model.CampaignListItem
import ru.zhma.campaign.ai.model.CatalogSnapshot
import ru.zhma.campaign.ai.model.CommitAction
import ru.zhma.campaign.ai.model.DraftCampaign
import ru.zhma.campaign.ai.model.ReplyOption
import ru.zhma.campaign.ai.model.Stage
import ru.zhma.campaign.ai.model.UiBlock

/** Ответ после того, как сервер выполнил запись (или не смог). Чистые функции, без базы. */
object CommitReplies {
    fun done(
        action: String,
        draft: DraftCampaign,
        catalog: CatalogSnapshot,
        today: String,
        campaign: CampaignListItem? = null,
    ): AssistantReply {
        val v = CampaignValidator.check(draft, catalog, today)
        val card =
            UiBlock.CampaignDraft(
                draft,
                v.missing,
                v.readyToSave,
                DraftView.choices(draft, catalog),
                if (v.missing.isEmpty()) Stage.DESIGN else Stage.BRIEF,
            )
        return when (action) {
            CommitAction.SAVE_CAMPAIGN -> {
                val savedState = campaign?.state?.let { ModeratorTurn.stateLabel(it) } ?: "черновик"
                val items = mutableListOf(ReplyOption("На модерацию", "Отправь на модерацию", recommended = true))
                if (draft.banner == null) items += ReplyOption("Сделать баннер", "Сделай баннер")
                AssistantReply(
                    "Кампания «${draft.name}» сохранена (№ ${draft.savedCampaignId}), статус — ${campaign?.state?.let {
                        ModeratorTurn
                            .stateLabel(
                                it,
                            )
                    } ?: "черновик"}. " +
                        "Чтобы отправить её на модерацию, напишите «отправь на модерацию».",
                    listOf(card, buttons(items)),
                )
            }
            CommitAction.APPROVE ->
                AssistantReply("Кампания «${campaign?.name ?: ""}» одобрена. Менеджер может её активировать.", emptyList())
            CommitAction.ACTIVATE ->
                AssistantReply(
                    "Кампания «${draft.name}» запущена: статус — «активна», опубликована.",
                    listOf(card),
                )
            CommitAction.STOP ->
                AssistantReply("Показ кампании «${draft.name}» остановлен: статус — «остановлена», не опубликована.", listOf(card))
            CommitAction.DECLINE ->
                AssistantReply(
                    "Кампания «${campaign?.name ?: ""}» возвращена на доработку. Менеджер увидит комментарий в чате.",
                    emptyList(),
                )
            CommitAction.LOAD_CAMPAIGN -> {
                val state = campaign?.state?.let { ModeratorTurn.stateLabel(it) } ?: "сохранена"
                val comment = campaign?.lastComment?.takeIf { campaign.state == "NEEDS_WORK" }
                val items = mutableListOf<ReplyOption>()
                if (draft.banner == null) items += ReplyOption("Сделать баннер", "Сделай баннер")
                if (campaign?.state in setOf("DRAFT", "NEEDS_WORK")) items += ReplyOption("На модерацию", "Отправь на модерацию")
                val text =
                    buildString {
                        append("Открыл кампанию «${draft.name}» (№ ${draft.savedCampaignId}), статус — $state.")
                        if (comment != null) append(" Комментарий модератора: «$comment».")
                        append(" Напишите, что поправить: правки попадут в базу после «Сохранить».")
                    }
                AssistantReply(text, listOfNotNull(card, items.takeIf { it.isNotEmpty() }?.let(::buttons)))
            }
            CommitAction.TO_MODERATION ->
                AssistantReply(
                    "Кампания «${draft.name}» отправлена на модерацию. Статус — «на модерации». " +
                        "Если модератор вернёт её на доработку, поправим здесь же и отправим снова.",
                    listOf(card),
                )
            else -> {
                val name =
                    when (action) {
                        CommitAction.CREATE_TAG -> draft.tagIds.lastOrNull()?.let { id -> catalog.tags.find { it.id == id }?.name }
                        CommitAction.CREATE_SEGMENT ->
                            draft.includedSegments.lastOrNull()?.let { c ->
                                catalog.segments.find { it.code == c }?.name
                            }
                        else -> draft.placeIds.lastOrNull()?.let { id -> catalog.places.find { it.id == id }?.description }
                    }
                val what =
                    when (action) {
                        CommitAction.CREATE_TAG -> "тег"
                        CommitAction.CREATE_SEGMENT -> "сегмент"
                        else -> "место показа"
                    }
                val next =
                    if (v.readyToSave) {
                        buttons(listOf(ReplyOption("Сохранить", "Сохранить", recommended = true)))
                    } else {
                        null
                    }
                val tail =
                    if (v.missing.isNotEmpty()) {
                        " Для сохранения ещё нужно: ${v.missing.joinToString(", ") { Questions.FIELD_LABELS[it] ?: it }}."
                    } else {
                        ""
                    }
                AssistantReply("Создал $what «${name ?: ""}» и привязал к черновику.$tail", listOfNotNull(card, next))
            }
        }
    }

    fun failed(
        action: String,
        draft: DraftCampaign,
        errors: List<String>,
        catalog: CatalogSnapshot,
        today: String,
    ): AssistantReply {
        val v = CampaignValidator.check(draft, catalog, today)
        val what =
            when (action) {
                CommitAction.SAVE_CAMPAIGN -> "сохранить кампанию"
                CommitAction.TO_MODERATION -> "отправить на модерацию"
                CommitAction.CREATE_TAG -> "создать тег"
                CommitAction.APPROVE -> "одобрить кампанию"
                CommitAction.ACTIVATE -> "запустить кампанию"
                CommitAction.DECLINE -> "вернуть кампанию на доработку"
                CommitAction.LOAD_CAMPAIGN -> "открыть кампанию"
                CommitAction.CREATE_SEGMENT -> "создать сегмент"
                CommitAction.STOP -> "остановить показ"
                CommitAction.SHOW_AUDIT -> "показать журнал"
                else -> "создать место"
            }
        return AssistantReply(
            "Не получилось $what: ${errors.joinToString(
                "; ",
            ).ifBlank { "ошибка сервера" }}. Черновик не потерян, поправим и попробуем ещё раз.",
            listOf(UiBlock.CampaignDraft(draft, v.missing, v.readyToSave, DraftView.choices(draft, catalog))),
        )
    }

    private fun buttons(items: List<ReplyOption>) = UiBlock.QuickReplies(items.map { it.value }, field = "next", items = items)
}
