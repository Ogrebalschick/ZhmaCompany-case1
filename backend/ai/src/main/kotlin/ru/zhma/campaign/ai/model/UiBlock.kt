package ru.zhma.campaign.ai.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

val AppJson: Json =
    Json {
        classDiscriminator = "type"
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

@Serializable
sealed class UiBlock {
    @Serializable
    @SerialName("campaign_draft")
    data class CampaignDraft(
        val draft: DraftCampaign,
        val missing: List<String>,
        val readyToSave: Boolean,
    ) : UiBlock()

    @Serializable
    @SerialName("quick_replies")
    data class QuickReplies(
        val options: List<String>,
    ) : UiBlock()

    @Serializable
    @SerialName("entity_proposal")
    data class EntityProposalBlock(
        val proposal: EntityProposal,
    ) : UiBlock()

    @Serializable
    @SerialName("creative_preview")
    data class CreativePreview(
        val banner: BannerDraft,
    ) : UiBlock()

    @Serializable
    @SerialName("campaign_list")
    data class CampaignList(
        val items: List<CampaignListItem>,
    ) : UiBlock()

    @Serializable
    @SerialName("warnings")
    data class Warnings(
        val items: List<String>,
    ) : UiBlock()
}
