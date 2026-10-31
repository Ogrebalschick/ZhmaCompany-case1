package ru.zhma.campaign.ai.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

val AppJson: Json =
    Json {
        classDiscriminator = "type"
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

@Serializable
sealed class UiBlock {
    @Serializable
    @SerialName("campaign_draft")
    data class CampaignDraft(
        val draft: DraftCampaign,
        val missing: List<String>,
        val readyToSave: Boolean,
        val choices: DraftChoices? = null,
        val stage: String? = null,
    ) : UiBlock()

    /**
     * Кнопки ответа. `options` — тексты, которые уходят как сообщение (совместимость со старым экраном).
     * Остальные поля описывают меню: про какое поле вопрос, можно ли выбрать несколько, подписи и подсказки.
     */
    @Serializable
    @SerialName("quick_replies")
    data class QuickReplies(
        val options: List<String>,
        val field: String? = null,
        val question: String? = null,
        val multi: Boolean = false,
        val allowCustom: Boolean = true,
        val items: List<ReplyOption> = emptyList(),
    ) : UiBlock()

    @Serializable
    @SerialName("entity_proposal")
    data class EntityProposalBlock(
        val proposal: EntityProposal,
    ) : UiBlock()

    @Serializable
    @SerialName("creative_preview")
    data class CreativePreview(
        val banner: BannerDraft,
    ) : UiBlock()

    @Serializable
    @SerialName("campaign_list")
    data class CampaignList(
        val items: List<CampaignListItem>,
    ) : UiBlock()

    /** Отчёт о проверке баннера на соответствие закону «О рекламе». */
    @Serializable
    @SerialName("compliance")
    data class Compliance(
        val report: ComplianceReport,
    ) : UiBlock()

    @Serializable
    @SerialName("warnings")
    data class Warnings(
        val items: List<String>,
    ) : UiBlock()
}

@Serializable
data class ReplyOption(
    val label: String,
    val value: String,
    val hint: String? = null,
    val recommended: Boolean = false,
)

@Serializable
data class SegmentChoice(
    val code: String,
    val list: String,
    val name: String,
    val type: String,
    val count: Long,
    val state: String,
)

@Serializable
data class PlaceChoice(
    val id: Long,
    val code: String,
    val description: String,
    val state: String,
    val defaultBanner: String?,
)

@Serializable
data class DraftChoices(
    val segments: List<SegmentChoice> = emptyList(),
    val places: List<PlaceChoice> = emptyList(),
    /** Оценка охвата: сегменты включения работают по ИЛИ, поэтому охват — от самого большого сегмента до суммы. */
    val reachMin: Long? = null,
    val reachMax: Long? = null,
    /** Человеческое объяснение охвата. */
    val reachText: String? = null,
)
