package ru.zhma.campaign.ai.model

import kotlinx.serialization.Serializable

@Serializable
data class CampaignListItem(
    val id: Long,
    val name: String,
    val state: String,
    val published: Boolean,
    val topic: String,
    val startDate: String,
    val endDate: String? = null,
    /** Комментарий модератора при последнем отклонении. */
    val lastComment: String? = null,
    val author: String? = null,
)

@Serializable
data class TurnInput(
    val draft: DraftCampaign,
    val userText: String,
    val catalog: CatalogSnapshot,
    val today: String,
    val campaigns: List<CampaignListItem> = emptyList(),
    val history: List<HistoryItem> = emptyList(),
    /** `campaignManager` или `campaignModerator`. */
    val role: String = "campaignManager",
)

@Serializable
data class HistoryItem(
    val role: String,
    val text: String,
)

@Serializable
data class AssistantReply(
    val text: String,
    val blocks: List<UiBlock>,
)

@Serializable
data class CommitRequest(
    val action: String,
    /** Для действий модератора и загрузки — над какой кампанией. */
    val campaignId: Long? = null,
    val comment: String? = null,
)

object CommitAction {
    const val SAVE_CAMPAIGN = "SAVE_CAMPAIGN"
    const val CREATE_TAG = "CREATE_TAG"
    const val CREATE_PLACE = "CREATE_PLACE"
    const val CREATE_SEGMENT = "CREATE_SEGMENT"
    const val TO_MODERATION = "TO_MODERATION"
    const val APPROVE = "APPROVE"
    const val DECLINE = "DECLINE"
    const val ACTIVATE = "ACTIVATE"
    const val STOP = "STOP"

    /** Не запись: сервер кладёт в ответ журнал (аудит) кампании. */
    const val SHOW_AUDIT = "SHOW_AUDIT"

    /** Не запись: сервер подставляет в черновик сохранённую кампанию, чтобы её поправить. */
    const val LOAD_CAMPAIGN = "LOAD_CAMPAIGN"
}

object Stage {
    /** Идёт сбор обязательных полей: ведёт автомат. */
    const val BRIEF = "BRIEF"

    /** Обязательные поля есть: свободный диалог с инструментами (баннер, картинка, правки). */
    const val DESIGN = "DESIGN"
}

@Serializable
data class TurnResult(
    val reply: AssistantReply,
    val draft: DraftCampaign,
    val commit: CommitRequest? = null,
)
