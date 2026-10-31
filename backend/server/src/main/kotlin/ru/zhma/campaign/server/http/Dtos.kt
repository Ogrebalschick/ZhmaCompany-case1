package ru.zhma.campaign.server.http

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import ru.zhma.campaign.ai.model.AssistantReply

@Serializable
data class SessionResponse(val sessionId: String)

@Serializable
data class PostMessageBody(val text: String = "")

@Serializable
data class JobAccepted(val jobId: String)

@Serializable
data class JobStatusResponse(
    val status: String,
    val result: AssistantReply? = null,
    val error: String? = null,
)

@Serializable
data class MessageDto(
    val role: String,
    val text: String,
    val blocks: JsonElement,
    val createdAt: String,
)

@Serializable
data class MessagesResponse(val messages: List<MessageDto>)

@Serializable
data class BannerTypeDto(
    val id: Long,
    val code: String,
    val description: String,
    val previewForm: String,
    val titleMaxLength: Int,
    val textMaxLength: Int,
    val channels: List<String>,
)

@Serializable
data class ChannelDto(val id: Long, val code: String, val description: String)

@Serializable
data class SegmentsDto(val included: List<String>, val excluded: List<String>)

@Serializable
data class BannerDto(
    val id: Long,
    val campaignId: Long,
    val name: String,
    val bannerTypeId: Long,
    val content: JsonElement,
)

@Serializable
data class ContentDto(
    val id: Long,
    val campaignId: Long,
    val name: String,
    val contentType: String,
    val content: JsonElement,
)

@Serializable
data class CreativeDto(
    val id: Long,
    val campaignId: Long,
    val name: String,
    val bannerId: Long,
    val contentId: Long? = null,
    val channelCode: String,
)

@Serializable
data class CampaignDetailsDto(
    val id: Long,
    val name: String,
    val marketingName: String? = null,
    val startDate: String,
    val startTime: String,
    val endDate: String? = null,
    val endTime: String? = null,
    val topic: String,
    val tags: List<Long>,
    val keywords: String? = null,
    val audienceType: String,
    val weight: Double,
    val initialCtr: Double,
    val state: String,
    val published: Boolean,
    val author: String,
    val lastEditAuthor: String,
    val places: List<Long>,
    val segments: SegmentsDto,
    val banners: List<BannerDto>,
    val contents: List<ContentDto>,
    val creatives: List<CreativeDto>,
)

@Serializable
data class CampaignStateDto(val id: Long, val state: String, val published: Boolean)

@Serializable
data class WarningsResponse(val errors: List<ApiError>, val warnings: List<String>)

@Serializable
data class AuditEntryDto(
    val entity: String,
    val entityId: Long,
    val action: String,
    val changes: JsonElement? = null,
    val at: String,
    val actor: String,
    val comment: String? = null,
)

@Serializable
data class CommentBody(val comment: String? = null)

@Serializable
data class BannerButtonJson(val name: String)

@Serializable
data class BannerContentJson(
    val actionType: String,
    val title: String? = null,
    val text: String? = null,
    val link: String? = null,
    val button: BannerButtonJson? = null,
)
