package ru.zhma.campaign.ai.model

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
