package ru.zhma.campaign.ai.agent

import ru.zhma.campaign.ai.model.CatalogSnapshot
import ru.zhma.campaign.ai.model.DraftCampaign
import ru.zhma.campaign.ai.model.DraftPatch

/**
 * Ответ кнопкой (или тем же текстом руками) на вопрос про конкретное поле.
 * Разбирается кодом по справочнику: модель не нужна, ошибиться негде.
 */
object QuickAnswer {
    fun match(
        rawText: String,
        draft: DraftCampaign,
        catalog: CatalogSnapshot,
    ): DraftPatch? {
        val text = norm(rawText)
        if (text.isEmpty()) return null
        return when (draft.awaiting) {
            "name" -> draft.nameGuess?.takeIf { norm(it) == text }?.let { DraftPatch(name = it) }
            "topic" ->
                catalog.topics
                    .find { norm(it.label) == text || norm(it.value) == text }
                    ?.let { DraftPatch(topic = it.value) }
            "audienceType" -> audience(text)
            "segments" -> segments(text, catalog)
            "places" -> places(text, catalog)
            "tags" -> tags(text, catalog)
            else -> null
        } ?: globalMatch(text, catalog)
    }

    /** Точное совпадение с подписью из справочника, даже если вопрос был про другое поле. */
    private fun globalMatch(
        text: String,
        catalog: CatalogSnapshot,
    ): DraftPatch? {
        audience(text)?.let { return it }
        catalog.topics.find { norm(it.label) == text && it.label.length > 4 }?.let { return DraftPatch(topic = it.value) }
        return null
    }

    private fun audience(text: String): DraftPatch? =
        when (text) {
            "все пользователи", "всем пользователям", "всем", "все" -> DraftPatch(audienceType = "ALL_USERS")
            "сегмент пользователей", "сегмент", "сегменты", "выбранным сегментам", "по сегментам", "сегментная" ->
                DraftPatch(audienceType = "SEGMENTED")
            else -> null
        }

    private fun segments(
        text: String,
        catalog: CatalogSnapshot,
    ): DraftPatch? {
        val parts = split(text)
        val codes =
            parts.map { part ->
                catalog.segments.find { it.state == "ACTIVE" && (norm(it.name) == part || norm(it.code) == part) }?.code
                    ?: return null
            }
        return DraftPatch(includedSegments = codes, audienceType = "SEGMENTED")
    }

    private fun places(
        text: String,
        catalog: CatalogSnapshot,
    ): DraftPatch? {
        val parts = split(text)
        val ids =
            parts.map { part ->
                catalog.places.find { norm(it.description) == part || norm(it.code) == part }?.id ?: return null
            }
        return DraftPatch(placeIds = ids)
    }

    /** Ответ на меню тегов: известные — по id, одно неизвестное имя — в tagQuery (станет предложением создать). */
    private fun tags(
        text: String,
        catalog: CatalogSnapshot,
    ): DraftPatch {
        val parts = split(text)
        val ids = parts.mapNotNull { part -> catalog.tags.find { norm(it.name) == part }?.id }
        val unknown = parts.filter { part -> catalog.tags.none { norm(it.name) == part } }
        return DraftPatch(tagIds = ids, tagQuery = unknown.firstOrNull()?.replaceFirstChar { it.uppercase() })
    }

    private fun split(text: String): List<String> = text.split(Regex("""\s*[,;\n]\s*""")).map { it.trim() }.filter { it.isNotEmpty() }

    fun norm(value: String): String =
        value
            .lowercase()
            .replace('ё', 'е')
            .replace(Regex("""[–—]"""), "-")
            .trim()
            .trimEnd('.', '!', '?')
            .replace(Regex("""\s+"""), " ")
}
