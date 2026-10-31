package ru.zhma.campaign.server.http.dto

import kotlinx.serialization.Serializable

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
