package ru.zhma.campaign.ai.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

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
data class TopicRef(val value: String, val label: String)

@Serializable
data class TagRef(val id: Long, val name: String)

@Serializable
data class SegmentRef(val code: String, val name: String, val type: String, val state: String, val count: Long)

@Serializable
data class PlaceRef(val id: Long, val code: String, val description: String, val defaultBanner: String?, val state: String)

@Serializable
data class BannerTypeRef(val code: String, val titleMax: Int, val textMax: Int, val channels: List<String>)

@Serializable
data class ChannelRef(val code: String, val description: String)

@Serializable
data class CampaignListItem(
    val id: Long,
    val name: String,
    val state: String,
    val published: Boolean,
    val topic: String,
    val startDate: String,
    val endDate: String?,
)

@Serializable
data class CatalogSnapshot(
    val topics: List<TopicRef> = emptyList(),
    val tags: List<TagRef> = emptyList(),
    val segments: List<SegmentRef> = emptyList(),
    val places: List<PlaceRef> = emptyList(),
    val bannerTypes: List<BannerTypeRef> = emptyList(),
    val channels: List<ChannelRef> = emptyList(),
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
data class AssistantReply(val text: String, val blocks: List<UiBlock>)

@Serializable
data class TurnResult(
    val reply: AssistantReply,
    val draft: DraftCampaign,
    val commit: CommitRequest? = null,
)

@Serializable
data class CommitRequest(val action: String)

@Serializable
sealed interface UiBlock

@Serializable
@SerialName("campaign_draft")
data class CampaignDraftBlock(
    val draft: DraftCampaign,
    val missing: List<String> = emptyList(),
    val readyToSave: Boolean = false,
) : UiBlock

@Serializable
@SerialName("quick_replies")
data class QuickRepliesBlock(val options: List<String> = emptyList()) : UiBlock

@Serializable
@SerialName("entity_proposal")
data class EntityProposalBlock(val proposal: EntityProposal) : UiBlock

@Serializable
@SerialName("creative_preview")
data class CreativePreviewBlock(val banner: BannerDraft) : UiBlock

@Serializable
@SerialName("campaign_list")
data class CampaignListBlock(val items: List<CampaignListItem> = emptyList()) : UiBlock

@Serializable
@SerialName("warnings")
data class WarningsBlock(val items: List<String> = emptyList()) : UiBlock
