package ru.zhma.campaign.ai.agent

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import ru.zhma.campaign.ai.model.CatalogSnapshot
import ru.zhma.campaign.ai.model.DraftCampaign
import ru.zhma.campaign.ai.model.DraftChoices
import ru.zhma.campaign.ai.model.PlaceChoice
import ru.zhma.campaign.ai.model.SegmentChoice

/** Представления черновика: компактный JSON для модели, человеческая сводка, список изменений. */
object DraftView {
    fun compactJson(draft: DraftCampaign): String =
        buildJsonObject {
            draft.name?.let { put("name", it) }
            draft.topic?.let { put("topic", it) }
            draft.audienceType?.let { put("audienceType", it) }
            if (draft.includedSegments.isNotEmpty()) put("includedSegments", JsonArray(draft.includedSegments.map { JsonPrimitive(it) }))
            if (draft.excludedSegments.isNotEmpty()) put("excludedSegments", JsonArray(draft.excludedSegments.map { JsonPrimitive(it) }))
            if (draft.placeIds.isNotEmpty()) put("placeIds", JsonArray(draft.placeIds.map { JsonPrimitive(it) }))
            if (draft.tagIds.isNotEmpty()) put("tagIds", JsonArray(draft.tagIds.map { JsonPrimitive(it) }))
            draft.startDate?.let { put("startDate", it) }
            draft.startTime?.let { put("startTime", it) }
            draft.endDate?.let { put("endDate", it) }
            draft.endTime?.let { put("endTime", it) }
            draft.weight?.let { put("weight", it) }
            draft.initialCtr?.let { put("initialCtr", it) }
            draft.banner?.let { b ->
                put("banner", "${b.bannerType}/${b.channel}: ${b.title.orEmpty()} | ${b.text.orEmpty()} | ${b.cta.orEmpty()}")
            }
        }.toString()

    fun summary(
        draft: DraftCampaign,
        catalog: CatalogSnapshot,
    ): String =
        buildString {
            appendLine("Название: ${draft.name ?: "—"}")
            appendLine("Тема: ${draft.topic?.let { topicLabel(it, catalog) } ?: "—"}")
            appendLine("Аудитория: ${audienceLabel(draft.audienceType)}")
            if (draft.includedSegments.isNotEmpty()) appendLine("Сегменты: ${segmentNames(draft.includedSegments, catalog)}")
            if (draft.excludedSegments.isNotEmpty()) appendLine("Исключить: ${segmentNames(draft.excludedSegments, catalog)}")
            appendLine("Места: ${placeNames(draft.placeIds, catalog).ifBlank { "—" }}")
            appendLine("Старт: ${listOfNotNull(DateParser.human(draft.startDate), draft.startTime).joinToString(" ").ifBlank { "—" }}")
            appendLine("Окончание: ${listOfNotNull(DateParser.human(draft.endDate), draft.endTime).joinToString(" ").ifBlank { "—" }}")
            if (draft.tagIds.isNotEmpty()) {
                appendLine(
                    "Теги: ${draft.tagIds.mapNotNull { id ->
                        catalog.tags.find { it.id == id }?.name
                    }.joinToString()}",
                )
            }
            draft.weight?.let { appendLine("Вес: $it") }
            draft.banner?.let { b ->
                appendLine("Баннер: ${b.bannerType}, ${b.channel}; «${b.title.orEmpty()}» — ${b.text.orEmpty()} [${b.cta.orEmpty()}]")
                if (b.imageUrl != null) appendLine("Картинка: ${b.imageStatus ?: "есть"}")
            }
            append(
                "Сохранена: ${draft.savedCampaignId?.let {
                    "да, #$it" + if (draft.dirty) " (есть несохранённые правки)" else ""
                } ?: "нет"}",
            )
        }

    fun changes(
        before: DraftCampaign,
        after: DraftCampaign,
        catalog: CatalogSnapshot,
    ): List<String> {
        val out = mutableListOf<String>()
        if (after.name != before.name && after.name != null) out += "название «${after.name}»"
        if (after.topic != before.topic && after.topic != null) out += "тема «${topicLabel(after.topic, catalog)}»"
        if (after.audienceType != before.audienceType &&
            after.audienceType != null
        ) {
            out += "аудитория — ${audienceLabel(after.audienceType)}"
        }
        val addedSegments = after.includedSegments - before.includedSegments.toSet()
        if (addedSegments.isNotEmpty()) out += "сегменты: ${segmentNames(addedSegments, catalog)}"
        val removedSegments = before.includedSegments - after.includedSegments.toSet()
        if (removedSegments.isNotEmpty() &&
            after.audienceType != "ALL_USERS"
        ) {
            out += "убрал сегменты: ${segmentNames(removedSegments, catalog)}"
        }
        val addedExcluded = after.excludedSegments - before.excludedSegments.toSet()
        if (addedExcluded.isNotEmpty()) out += "исключения: ${segmentNames(addedExcluded, catalog)}"
        val addedPlaces = after.placeIds - before.placeIds.toSet()
        if (addedPlaces.isNotEmpty()) out += "место: ${placeNames(addedPlaces, catalog)}"
        val removedPlaces = before.placeIds - after.placeIds.toSet()
        if (removedPlaces.isNotEmpty()) out += "убрал место: ${placeNames(removedPlaces, catalog)}"
        if (after.startDate != before.startDate || after.startTime != before.startTime) {
            val value = listOfNotNull(DateParser.human(after.startDate), after.startTime).joinToString(" ")
            if (value.isNotBlank()) out += "старт $value"
        }
        if (after.endDate != before.endDate || after.endTime != before.endTime) {
            val value = listOfNotNull(DateParser.human(after.endDate), after.endTime).joinToString(" ")
            if (value.isNotBlank()) out += "окончание $value"
        }
        val addedTags = after.tagIds - before.tagIds.toSet()
        if (addedTags.isNotEmpty()) out += "теги: ${addedTags.mapNotNull { id -> catalog.tags.find { it.id == id }?.name }.joinToString()}"
        if (after.weight != before.weight && after.weight != null) out += "вес ${after.weight}"
        if (after.initialCtr != before.initialCtr && after.initialCtr != null) out += "CTR ${after.initialCtr}"
        return out
    }

    fun choices(
        draft: DraftCampaign,
        catalog: CatalogSnapshot,
    ): DraftChoices {
        fun seg(
            code: String,
            list: String,
        ): SegmentChoice? =
            catalog.segments.find { it.code == code }?.let { SegmentChoice(it.code, list, it.name, it.type, it.count, it.state) }
        val counts = draft.includedSegments.mapNotNull { code -> catalog.segments.find { it.code == code }?.count }
        val reachMin = counts.maxOrNull()
        val reachMax = counts.sum().takeIf { counts.isNotEmpty() }
        val reachText =
            when {
                draft.audienceType == "ALL_USERS" -> "Все пользователи приложения"
                reachMin == null -> null
                counts.size == 1 -> "Охват — до ${Questions.humanCount(reachMin)}"
                else -> "Охват — не меньше ${Questions.humanCount(reachMin)}"
            }
        return DraftChoices(
            reachMin = reachMin,
            reachMax = reachMax,
            reachText = reachText,
            segments =
                draft.includedSegments.mapNotNull {
                    seg(
                        it,
                        "included",
                    )
                } + draft.excludedSegments.mapNotNull { seg(it, "excluded") },
            places =
                draft.placeIds.mapNotNull { id ->
                    catalog.places.find { it.id == id }?.let { PlaceChoice(it.id, it.code, it.description, it.state, it.defaultBanner) }
                },
        )
    }

    fun topicLabel(
        code: String,
        catalog: CatalogSnapshot,
    ): String = catalog.topics.find { it.value == code }?.label ?: code

    fun audienceLabel(type: String?): String =
        when (type) {
            "ALL_USERS" -> "все пользователи"
            "SEGMENTED" -> "сегменты"
            else -> "—"
        }

    fun segmentNames(
        codes: List<String>,
        catalog: CatalogSnapshot,
    ): String = codes.joinToString(", ") { code -> catalog.segments.find { it.code == code }?.name ?: code }

    fun placeNames(
        ids: List<Long>,
        catalog: CatalogSnapshot,
    ): String = ids.joinToString(", ") { id -> catalog.places.find { it.id == id }?.description ?: "#$id" }
}
