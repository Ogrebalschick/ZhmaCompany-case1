package ru.zhma.campaign.server.service

import ru.zhma.campaign.ai.model.BannerDraft
import ru.zhma.campaign.server.http.ApiError

data class PlaceInfo(
    val id: Long,
    val code: String,
    val defaultBanner: String?,
    val state: String,
)

data class BannerTypeInfo(
    val id: Long,
    val titleMax: Int,
    val textMax: Int,
    val channels: Set<String>,
)

data class LiveCatalog(
    val topics: Set<String>,
    val tagIds: Set<Long>,
    val segmentStates: Map<String, String>,
    val places: Map<Long, PlaceInfo>,
    val bannerTypes: Map<String, BannerTypeInfo>,
    val channels: Set<String>,
)

data class CheckInput(
    val id: Long?,
    val name: String?,
    val marketingName: String?,
    val startDate: String?,
    val startTime: String?,
    val endDate: String?,
    val endTime: String?,
    val topic: String?,
    val tagIds: List<Long>,
    val keywords: String?,
    val audienceType: String?,
    val included: List<String>,
    val excluded: List<String>,
    val placeIds: List<Long>,
    val banners: List<BannerDraft>,
    val existingNames: Map<String, Long>,
)

data class CheckResult(
    val errors: List<ApiError>,
    val missing: List<ApiError>,
)
