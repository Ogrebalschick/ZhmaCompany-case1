package ru.zhma.campaign.ai.agent

import ru.zhma.campaign.ai.model.BannerTypeRef
import ru.zhma.campaign.ai.model.CatalogSnapshot
import ru.zhma.campaign.ai.model.DraftCampaign
import java.time.LocalDate

data class ValidationResult(
    val missing: List<String>,
    val readyToSave: Boolean,
    val warnings: List<String>,
    val problems: List<String>,
)

object CampaignValidator {
    fun check(
        draft: DraftCampaign,
        catalog: CatalogSnapshot,
        today: String,
    ): ValidationResult {
        val missing = mutableListOf<String>()
        val problems = mutableListOf<String>()
        val warnings = mutableListOf<String>()

        val name = draft.name
        if (name == null || name.isBlank() || name.length > 100) missing += "name"
        if (!validDate(draft.startDate)) missing += "startDate"
        if (!validTime(draft.startTime)) missing += "startTime"
        if (draft.topic.isNullOrBlank() || catalog.topics.none { it.value == draft.topic }) missing += "topic"
        if (draft.audienceType != "ALL_USERS" && draft.audienceType != "SEGMENTED") missing += "audienceType"
        if (draft.placeIds.isEmpty() || draft.placeIds.any { id -> catalog.places.none { it.id == id } }) {
            missing += "places"
        }

        when (draft.audienceType) {
            "SEGMENTED" -> {
                val included = draft.includedSegments
                val active =
                    included.filter { code ->
                        catalog.segments.any { it.code == code && it.state == "ACTIVE" }
                    }
                if (active.isEmpty()) missing += "segments"
                if (included.any { code -> catalog.segments.none { it.code == code && it.state == "ACTIVE" } }) {
                    problems += "Включать можно только активные сегменты из справочника."
                }
            }
            "ALL_USERS" -> {
                if (draft.includedSegments.isNotEmpty() || draft.excludedSegments.isNotEmpty()) {
                    problems += "Для аудитории «все пользователи» списки сегментов должны быть пустыми."
                }
            }
        }

        val start = draft.startDate?.let { raw -> runCatching { LocalDate.parse(raw) }.getOrNull() }
        if (start != null && start.isBefore(LocalDate.parse(today))) {
            problems += "Дата начала уже прошла."
        }
        if (endNotAfterStart(draft)) {
            problems += "Окончание должно быть позже начала."
        }

        draft.placeIds
            .mapNotNull { id -> catalog.places.find { it.id == id } }
            .filter { it.state != "ACTIVE" }
            .forEach { _ -> warnings += "Место показа выключено" }

        if (draft.tagIds.isEmpty()) warnings += "У кампании нет тегов"

        draft.banner?.let { banner -> problems += bannerProblems(banner, catalog) }

        val ready = missing.isEmpty() && problems.isEmpty() && draft.pendingProposal == null
        return ValidationResult(
            missing = missing.distinct(),
            readyToSave = ready,
            warnings = warnings.distinct(),
            problems = problems.distinct(),
        )
    }

    private fun bannerProblems(
        banner: ru.zhma.campaign.ai.model.BannerDraft,
        catalog: CatalogSnapshot,
    ): List<String> {
        val problems = mutableListOf<String>()
        val limits: BannerTypeRef? = catalog.bannerTypes.find { it.code == banner.bannerType }
        if (banner.bannerType == "STORY" && banner.channel == "WEB") {
            problems += "Сторис доступна только в iOS и ANDROID."
        }
        if (limits != null && banner.channel !in limits.channels) {
            problems += "Канал ${banner.channel} не подходит типу ${banner.bannerType}."
        }
        val titleMax = limits?.titleMax
        val textMax = limits?.textMax
        if (titleMax != null && (banner.title?.length ?: 0) > titleMax) {
            problems += "Заголовок длиннее $titleMax символов."
        }
        if (textMax != null && (banner.text?.length ?: 0) > textMax) {
            problems += "Текст длиннее $textMax символов."
        }
        val needsLink = banner.actionType == "externalLink" || banner.actionType == "openSmartLink"
        val forbidsLink = banner.actionType == "closeCampaign" || banner.actionType == "doneCampaign"
        if (needsLink && banner.link.isNullOrBlank()) problems += "Для этого действия нужна ссылка."
        if (forbidsLink && !banner.link.isNullOrBlank()) problems += "Для этого действия ссылка запрещена."
        return problems
    }

    private fun validDate(value: String?): Boolean = value != null && runCatching { LocalDate.parse(value) }.isSuccess

    private fun validTime(value: String?): Boolean = value != null && Regex("""^(?:[01]\d|2[0-3]):[0-5]\d$""").matches(value)

    private fun endNotAfterStart(draft: DraftCampaign): Boolean {
        val start = draft.startDate?.let { raw -> runCatching { LocalDate.parse(raw) }.getOrNull() } ?: return false
        val end = draft.endDate?.let { raw -> runCatching { LocalDate.parse(raw) }.getOrNull() } ?: return false
        if (end.isAfter(start)) return false
        if (end.isBefore(start)) return true
        val startTime = draft.startTime
        val endTime = draft.endTime
        if (startTime == null || endTime == null) return true
        return endTime <= startTime
    }
}
