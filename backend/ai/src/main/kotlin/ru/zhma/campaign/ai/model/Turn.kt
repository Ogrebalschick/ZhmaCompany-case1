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
)

@Serializable
data class TurnInput(
    val draft: DraftCampaign,
    val userText: String,
    val catalog: CatalogSnapshot,
    val today: String,
    val campaigns: List<CampaignListItem> = emptyList(),
)

@Serializable
data class AssistantReply(
    val text: String,
    val blocks: List<UiBlock>,
)

@Serializable
data class CommitRequest(
    val action: String,
)

object CommitAction {
    const val SAVE_CAMPAIGN = "SAVE_CAMPAIGN"
    const val CREATE_TAG = "CREATE_TAG"
    const val CREATE_PLACE = "CREATE_PLACE"
    const val TO_MODERATION = "TO_MODERATION"
}

@Serializable
data class TurnResult(
    val reply: AssistantReply,
    val draft: DraftCampaign,
    val commit: CommitRequest? = null,
)
