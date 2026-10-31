package ru.zhma.campaign.server.http.dto

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import ru.zhma.campaign.server.http.ApiError

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
data class CommentBody(val comment: String? = null)
