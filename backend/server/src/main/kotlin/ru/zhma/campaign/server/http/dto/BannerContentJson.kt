package ru.zhma.campaign.server.http.dto

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable

@Serializable
data class BannerButtonJson(
    val name: String,
)

@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class BannerContentJson(
    val actionType: String,
    val title: String? = null,
    val text: String? = null,
    val link: String? = null,
    val button: BannerButtonJson? = null,
    /** Адрес сгенерированной картинки. В JSON попадает, только если картинка есть. */
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val image: String? = null,
)
