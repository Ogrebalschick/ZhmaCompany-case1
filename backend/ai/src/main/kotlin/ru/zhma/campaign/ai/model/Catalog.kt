package ru.zhma.campaign.ai.model

import kotlinx.serialization.Serializable

@Serializable
data class TopicRef(
    val value: String,
    val label: String,
)

@Serializable
data class TagRef(
    val id: Long,
    val name: String,
)

@Serializable
data class SegmentRef(
    val code: String,
    val name: String,
    val type: String,
    val state: String,
    val count: Long,
)

@Serializable
data class PlaceRef(
    val id: Long,
    val code: String,
    val description: String,
    val defaultBanner: String?,
    val state: String,
)

@Serializable
data class BannerTypeRef(
    val code: String,
    val titleMax: Int,
    val textMax: Int,
    val channels: List<String>,
)

@Serializable
data class ChannelRef(
    val code: String,
    val description: String,
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

object CatalogText {
    fun render(snapshot: CatalogSnapshot): String =
        buildString {
            appendLine("topics:")
            snapshot.topics.forEach { appendLine("${it.value} | ${it.label}") }
            appendLine("tags:")
            snapshot.tags.forEach { appendLine("${it.id} | ${it.name}") }
            appendLine("segments:")
            snapshot.segments.forEach {
                appendLine("${it.code} | ${it.name} | ${it.type} | ${it.state} | ${it.count}")
            }
            appendLine("places:")
            snapshot.places.forEach {
                appendLine("${it.id} | ${it.code} | ${it.description} | ${it.defaultBanner ?: ""} | ${it.state}")
            }
            appendLine("bannerTypes:")
            snapshot.bannerTypes.forEach {
                appendLine("${it.code} | titleMax=${it.titleMax} | textMax=${it.textMax} | ${it.channels.joinToString(",")}")
            }
            appendLine("channels:")
            snapshot.channels.forEach { appendLine("${it.code} | ${it.description}") }
        }.trimEnd()
}
