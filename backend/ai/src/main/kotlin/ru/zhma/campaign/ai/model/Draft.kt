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
    /** Поле, про которое ассистент спросил последним. Ответ кнопкой разбирается кодом без модели. */
    val awaiting: String? = null,
    /** Сколько раз подряд спрашивали каждое поле: защита от зацикливания. */
    val askCounts: Map<String, Int> = emptyMap(),
    /** Черновик менялся после последнего сохранения. */
    val dirty: Boolean = false,
    /** «На 2 недели», сказанное до даты старта: конец посчитается, когда появится старт. */
    val pendingDurationDays: Long? = null,
    /** Догадки модели, которые показываются кнопками, но не попадают в поля без согласия. */
    val nameGuess: String? = null,
    val topicGuess: String? = null,
    /** Состояние сохранённой кампании, которое ассистент уже видел: чтобы сообщить об отклонении один раз. */
    val knownState: String? = null,
    /** Предупреждения, которые уже показали: повторно в каждом сообщении не выводим. */
    val shownWarnings: List<String> = emptyList(),
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
    val imageId: String? = null,
    val imageUrl: String? = null,
    val imagePrompt: String? = null,
    val imageStatus: String? = null,
    /** Проверка текста на соответствие закону «О рекламе». Пересчитывается при каждой смене текста. */
    val compliance: ComplianceReport? = null,
)

/** Одна строка проверки: правило, статья закона, статус и как исправить. */
@Serializable
data class ComplianceItem(
    val rule: String,
    val article: String,
    /** OK, INFO, WARN или FAIL. */
    val status: String,
    val message: String,
    val fix: String? = null,
    val quote: String? = null,
    /** Кто нашёл: `code` (правило в коде) или `ai` (модель-юрист). */
    val source: String = "code",
)

@Serializable
data class ComplianceReport(
    /** Итог: OK — замечаний нет, WARN — есть что поправить, FAIL — нарушение. */
    val verdict: String,
    val items: List<ComplianceItem>,
    val aiChecked: Boolean = false,
    val checkedText: String = "",
)

@Serializable
data class EntityProposal(
    /** TAG, PLACE или SEGMENT. */
    val kind: String,
    val name: String,
    val code: String? = null,
    val description: String? = null,
    /** Для SEGMENT: тип сегмента (AGE, INTEREST…). */
    val segmentType: String? = null,
    /** Для SEGMENT: какие временные коды заменить новым сегментом после создания. */
    val replaces: List<String> = emptyList(),
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
    val removeSegments: List<String>? = null,
    val placeIds: List<Long>? = null,
    val removePlaceIds: List<Long>? = null,
    val placeQuery: String? = null,
    val tagQuery: String? = null,
    val startDate: String? = null,
    val startTime: String? = null,
    val endDate: String? = null,
    val endTime: String? = null,
    val weight: Double? = null,
    val initialCtr: Double? = null,
    val creativeBrief: String? = null,
    @Serializable(with = LenientStringSerializer::class)
    val nameGuess: String? = null,
    @Serializable(with = LenientStringSerializer::class)
    val topicGuess: String? = null,
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

object Intents {
    const val CAMPAIGN_EDIT = "CAMPAIGN_EDIT"
    const val CONFIRM = "CONFIRM"
    const val CANCEL = "CANCEL"
    const val GENERATE_CREATIVE = "GENERATE_CREATIVE"
    const val GENERATE_IMAGE = "GENERATE_IMAGE"
    const val LIST_CAMPAIGNS = "LIST_CAMPAIGNS"
    const val TO_MODERATION = "TO_MODERATION"
    const val QUESTION = "QUESTION"
    const val OTHER = "OTHER"

    val ALL = setOf(CAMPAIGN_EDIT, CONFIRM, CANCEL, GENERATE_CREATIVE, GENERATE_IMAGE, LIST_CAMPAIGNS, TO_MODERATION, QUESTION, OTHER)
}
