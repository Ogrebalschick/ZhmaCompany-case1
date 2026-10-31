package ru.zhma.campaign.server.http.dto

import kotlinx.serialization.Serializable

@Serializable
data class BannerButtonJson(
    val name: String,
)

@Serializable
data class BannerContentJson(
    val actionType: String,
    val title: String? = null,
    val text: String? = null,
    val link: String? = null,
    val button: BannerButtonJson? = null,
)
