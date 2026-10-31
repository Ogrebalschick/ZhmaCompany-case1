package ru.zhma.campaign.ai.model

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

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
    @Serializable(with = LenientStringSerializer::class)
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

@OptIn(ExperimentalSerializationApi::class)
internal object LenientStringSerializer : KSerializer<String?> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("LenientString", PrimitiveKind.STRING)

    override fun serialize(
        encoder: Encoder,
        value: String?,
    ) {
        if (value == null) encoder.encodeNull() else encoder.encodeString(value)
    }

    override fun deserialize(decoder: Decoder): String? {
        val element = (decoder as JsonDecoder).decodeJsonElement()
        return when (element) {
            is JsonNull -> null
            is JsonPrimitive -> element.contentOrNull?.takeIf { it.isNotBlank() }
            is JsonArray ->
                element
                    .mapNotNull { item -> (item as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() } }
                    .joinToString(", ")
                    .ifBlank { null }
            else -> null
        }
    }
}

@Serializable
data class ParsedIntent(
    val intent: String,
    val patch: DraftPatch = DraftPatch(),
    val confirm: Boolean? = null,
)
