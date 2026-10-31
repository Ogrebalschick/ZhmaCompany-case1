package ru.zhma.campaign.server.service

import ru.zhma.campaign.ai.model.BannerDraft
import ru.zhma.campaign.server.http.ApiError
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeParseException

data class PlaceInfo(val id: Long, val code: String, val defaultBanner: String?, val state: String)

data class BannerTypeInfo(val id: Long, val titleMax: Int, val textMax: Int, val channels: Set<String>)

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

data class CheckResult(val errors: List<ApiError>, val missing: List<ApiError>)

private val timePattern = Regex("""^([01]\d|2[0-3]):[0-5]\d$""")
private val linkActions = setOf("externalLink", "openSmartLink")
private val noLinkActions = setOf("closeCampaign", "doneCampaign")
private val actionTypes = setOf("default", "externalLink", "openSmartLink", "closeCampaign", "doneCampaign")

fun checkCampaign(input: CheckInput, catalog: LiveCatalog): CheckResult {
    val errors = mutableListOf<ApiError>()
    val missing = mutableListOf<ApiError>()
    val name = input.name?.trim().orEmpty()
    if (name.isEmpty()) {
        errors += ApiError("name", "Укажите название кампании", "name")
    } else if (name.length > 100) {
        errors += ApiError("name", "Название длиннее 100 символов", "name")
    } else {
        val owner = input.existingNames[name]
        if (owner != null && owner != input.id) {
            errors += ApiError("NAME_DUPLICATE", "Кампания с таким названием уже есть", "name")
        }
    }
    val marketing = input.marketingName?.trim().orEmpty()
    if (marketing.length > 100) {
        errors += ApiError("marketingName", "Маркетинговое название длиннее 100 символов", "marketingName")
    }
    val keywords = input.keywords?.trim().orEmpty()
    if (keywords.length > 512) {
        errors += ApiError("keywords", "Ключевые слова длиннее 512 символов", "keywords")
    }
    val startDate = parseDate(input.startDate)
    if (input.startDate.isNullOrBlank()) {
        errors += ApiError("startDate", "Укажите дату начала", "startDate")
    } else if (startDate == null) {
        errors += ApiError("startDate", "Дата начала должна быть в формате YYYY-MM-DD", "startDate")
    }
    if (input.startTime.isNullOrBlank()) {
        errors += ApiError("startTime", "Укажите время начала в формате HH:mm", "startTime")
    } else if (!timePattern.matches(input.startTime)) {
        errors += ApiError("startTime", "Время начала должно быть в формате HH:mm", "startTime")
    }
    val endDate = parseDate(input.endDate)
    if (!input.endDate.isNullOrBlank() && endDate == null) {
        errors += ApiError("endDate", "Дата окончания должна быть в формате YYYY-MM-DD", "endDate")
    }
    if (!input.endTime.isNullOrBlank() && !timePattern.matches(input.endTime)) {
        errors += ApiError("endTime", "Время окончания должно быть в формате HH:mm", "endTime")
    }
    if (input.endTime.isNullOrBlank() && !input.endDate.isNullOrBlank() && endDate != null && startDate != null && endDate == startDate) {
        errors += ApiError("endTime", "Для того же дня укажите время окончания", "endTime")
    }
    if (!input.endTime.isNullOrBlank() && input.endDate.isNullOrBlank()) {
        errors += ApiError("endDate", "Укажите дату окончания", "endDate")
    }
    if (startDate != null && endDate != null && timePattern.matches(input.startTime ?: "") &&
        (input.endTime.isNullOrBlank() || timePattern.matches(input.endTime))
    ) {
        val start = startDate.atTime(LocalTime.parse(input.startTime))
        val end = if (input.endTime.isNullOrBlank()) endDate.atTime(LocalTime.MAX) else endDate.atTime(LocalTime.parse(input.endTime))
        if (!end.isAfter(start)) {
            errors += ApiError("DATE_RANGE_INVALID", "Конец показа должен быть позже начала", "endDate")
        }
    }
    val topic = input.topic?.trim().orEmpty()
    if (topic.isEmpty()) {
        errors += ApiError("topic", "Укажите тематику", "topic")
    } else if (topic !in catalog.topics) {
        errors += ApiError("topic", "Такой тематики нет в справочнике", "topic")
    }
    input.tagIds.distinct().forEach { tagId ->
        if (tagId !in catalog.tagIds) {
            missing += ApiError("ENTITY_NOT_FOUND", "Тег $tagId не найден", "tags")
        }
    }
    val audience = input.audienceType?.trim().orEmpty()
    if (audience.isEmpty()) {
        errors += ApiError("audienceType", "Укажите тип аудитории", "audienceType")
    } else if (audience != "ALL_USERS" && audience != "SEGMENTED") {
        errors += ApiError("audienceType", "Тип аудитории должен быть ALL_USERS или SEGMENTED", "audienceType")
    }
    val included = input.included.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
    val excluded = input.excluded.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
    if (audience == "ALL_USERS" && (included.isNotEmpty() || excluded.isNotEmpty())) {
        errors += ApiError(
            "SEGMENTS_FORBIDDEN_FOR_ALL_USERS",
            "Для аудитории «все пользователи» сегменты не задаются",
            "segments",
        )
    }
    (included + excluded).distinct().forEach { code ->
        val state = catalog.segmentStates[code]
        if (state == null) {
            errors += ApiError("segments", "Сегмент $code не найден", "segments")
        } else if (state != "ACTIVE") {
            errors += ApiError("segments", "Сегмент $code нельзя выбрать: состояние $state", "segments")
        }
    }
    if (included.intersect(excluded.toSet()).isNotEmpty()) {
        errors += ApiError("segments", "Один и тот же сегмент указан и во включении, и в исключении", "segments")
    }
    if (audience == "SEGMENTED" && included.none { catalog.segmentStates[it] == "ACTIVE" }) {
        errors += ApiError("segments", "Для сегментированной аудитории нужен хотя бы один активный сегмент включения", "segments")
    }
    val placeIds = input.placeIds.distinct()
    if (placeIds.isEmpty()) {
        errors += ApiError("places", "Укажите хотя бы одно место показа", "places")
    }
    placeIds.forEach { placeId ->
        if (catalog.places[placeId] == null) {
            missing += ApiError("ENTITY_NOT_FOUND", "Место показа $placeId не найдено", "places")
        }
    }
    input.banners.forEach { banner -> checkBanner(banner, catalog, errors, missing) }
    return CheckResult(errors, missing)
}

private fun checkBanner(
    banner: BannerDraft,
    catalog: LiveCatalog,
    errors: MutableList<ApiError>,
    missing: MutableList<ApiError>,
) {
    val type = catalog.bannerTypes[banner.bannerType]
    if (banner.bannerType.isBlank() || type == null) {
        missing += ApiError("ENTITY_NOT_FOUND", "Тип баннера ${banner.bannerType} не найден", "bannerType")
    }
    if (banner.channel.isBlank() || banner.channel !in catalog.channels) {
        missing += ApiError("ENTITY_NOT_FOUND", "Канал ${banner.channel} не найден", "channel")
    } else if (type != null && banner.channel !in type.channels) {
        errors += ApiError("channel", "Канал ${banner.channel} недоступен для типа ${banner.bannerType}", "channel")
    }
    if (banner.actionType !in actionTypes) {
        errors += ApiError("actionType", "Неизвестный actionType", "actionType")
    }
    val link = banner.link?.trim().orEmpty()
    if (banner.actionType in linkActions && link.isEmpty()) {
        errors += ApiError("link", "Для этого действия нужна ссылка", "link")
    }
    if (banner.actionType in noLinkActions && link.isNotEmpty()) {
        errors += ApiError("link", "Для этого действия ссылка запрещена", "link")
    }
    if (type != null) {
        val title = banner.title
        if (title != null && title.length > type.titleMax) {
            errors += ApiError("title", "Заголовок длиннее ${type.titleMax} символов", "title")
        }
        val text = banner.text
        if (text != null && text.length > type.textMax) {
            errors += ApiError("text", "Текст длиннее ${type.textMax} символов", "text")
        }
    }
}

private fun parseDate(value: String?): LocalDate? {
    if (value.isNullOrBlank()) return null
    return try {
        LocalDate.parse(value)
    } catch (_: DateTimeParseException) {
        null
    }
}

fun warningLines(
    tagIds: List<Long>,
    places: List<PlaceInfo>,
    creativeBannerTypes: List<String>,
): List<String> {
    val warnings = mutableListOf<String>()
    val uncovered = places.any { place ->
        val expected = place.defaultBanner ?: return@any false
        creativeBannerTypes.none { it == expected }
    }
    if (uncovered) warnings += "Не для всех мест показа настроены креативы"
    if (places.any { it.state != "ACTIVE" }) warnings += "Некоторые места показа деактивированы"
    if (tagIds.isEmpty()) warnings += "У кампании отсутствуют теги"
    return warnings
}
