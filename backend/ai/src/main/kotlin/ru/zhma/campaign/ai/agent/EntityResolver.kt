package ru.zhma.campaign.ai.agent

import ru.zhma.campaign.ai.model.CatalogSnapshot
import ru.zhma.campaign.ai.model.DraftCampaign
import ru.zhma.campaign.ai.model.DraftPatch
import ru.zhma.campaign.ai.model.EntityProposal

data class ResolveResult(
    val draft: DraftCampaign,
    val note: String? = null,
    val suggestions: List<String> = emptyList(),
)

object EntityResolver {
    fun resolve(
        draft: DraftCampaign,
        patch: DraftPatch,
        catalog: CatalogSnapshot,
    ): ResolveResult {
        val notes = mutableListOf<String>()
        val suggestions = mutableListOf<String>()

        val unknown = mutableListOf<String>()
        val included = resolveSegments(draft.includedSegments, patch.segmentQuery, catalog, notes, suggestions, unknown)
        val excluded = draft.excludedSegments.filter { code -> catalog.segments.any { it.code == code } }

        val places = resolvePlaces(draft, patch, catalog, notes, suggestions)
        val tags = resolveTags(draft, patch, catalog, notes)
        val segmentProposal =
            unknown.lastOrNull()?.let { q ->
                EntityProposal(
                    kind = "SEGMENT",
                    name = q.replaceFirstChar { it.uppercase() },
                    code = "CUSTOM_" + slug(q).uppercase().take(30),
                    description = q,
                    segmentType = "INTEREST",
                )
            }
        val proposal =
            when {
                places.proposal != null -> places.proposal
                tags.proposal != null -> tags.proposal
                segmentProposal != null -> segmentProposal
                patch.placeQuery != null || patch.tagQuery != null -> null
                else -> draft.pendingProposal
            }

        return ResolveResult(
            draft =
                draft.copy(
                    includedSegments = included,
                    excludedSegments = excluded,
                    placeIds = places.ids,
                    tagIds = tags.ids,
                    pendingProposal = proposal,
                ),
            note = notes.joinToString(" ").ifBlank { null },
            suggestions = suggestions.distinct().take(5),
        )
    }

    private fun resolveSegments(
        codes: List<String>,
        query: String?,
        catalog: CatalogSnapshot,
        notes: MutableList<String>,
        suggestions: MutableList<String>,
        unknown: MutableList<String>,
    ): List<String> {
        val known =
            codes
                .mapNotNull { raw ->
                    val segment = catalog.segments.find { it.code.equals(raw, ignoreCase = true) }
                    if (segment == null) {
                        notes += "Сегмента «$raw» нет в справочнике."
                        suggestions += suggestSegments(raw, catalog)
                        null
                    } else {
                        segment.code
                    }
                }.toMutableList()

        val q = query?.trim().orEmpty()
        if (q.isNotEmpty()) {
            val exact = catalog.segments.find { it.code.equals(q, ignoreCase = true) || it.name.equals(q, ignoreCase = true) }
            if (exact != null) {
                if (exact.code !in known) known += exact.code
            } else {
                notes += "Сегмента «$q» нет в справочнике."
                // Возраст разбирает код (TextExtractor) — отдельный «сегмент» под него от модели не создаём.
                val looksLikeAge = Regex("""\d|возраст|лет(?!\p{L})""", RegexOption.IGNORE_CASE).containsMatchIn(q)
                if (q.length in 3..40 && !looksLikeAge) unknown += q
                suggestions += suggestSegments(q, catalog)
            }
        }
        return known.distinct()
    }

    private data class IdsAndProposal(
        val ids: List<Long>,
        val proposal: EntityProposal?,
    )

    private fun resolvePlaces(
        draft: DraftCampaign,
        patch: DraftPatch,
        catalog: CatalogSnapshot,
        notes: MutableList<String>,
        suggestions: MutableList<String>,
    ): IdsAndProposal {
        val ids = draft.placeIds.filter { id -> catalog.places.any { it.id == id } }.toMutableList()
        val query = patch.placeQuery?.trim().orEmpty()
        if (query.isEmpty()) return IdsAndProposal(ids, null)

        val matches =
            catalog.places.filter { place ->
                place.code.equals(query, ignoreCase = true) ||
                    place.description.contains(query, ignoreCase = true) ||
                    place.code.contains(query, ignoreCase = true)
            }
        return when (matches.size) {
            1 -> {
                if (matches[0].id !in ids) ids += matches[0].id
                IdsAndProposal(ids, null)
            }
            0 ->
                IdsAndProposal(
                    ids,
                    EntityProposal(
                        kind = "PLACE",
                        name = query,
                        code = slug(query),
                        description = query,
                    ),
                )
            else -> {
                notes += "Нашлось несколько мест показа."
                suggestions += matches.take(5).map { it.description }
                IdsAndProposal(ids, null)
            }
        }
    }

    private fun resolveTags(
        draft: DraftCampaign,
        patch: DraftPatch,
        catalog: CatalogSnapshot,
        notes: MutableList<String>,
    ): IdsAndProposal {
        val ids = draft.tagIds.filter { id -> catalog.tags.any { it.id == id } }.toMutableList()
        val query = patch.tagQuery?.trim().orEmpty()
        if (query.isEmpty()) return IdsAndProposal(ids, null)

        val matches = catalog.tags.filter { it.name.equals(query, ignoreCase = true) || it.name.contains(query, ignoreCase = true) }
        return when {
            matches.size == 1 -> {
                if (matches[0].id !in ids) ids += matches[0].id
                IdsAndProposal(ids, null)
            }
            matches.isEmpty() ->
                IdsAndProposal(
                    ids,
                    EntityProposal(kind = "TAG", name = query, description = query),
                )
            else -> {
                notes += "Нашлось несколько тегов."
                IdsAndProposal(ids, null)
            }
        }
    }

    private fun suggestSegments(
        query: String,
        catalog: CatalogSnapshot,
    ): List<String> {
        val tokens = tokens(query)
        return catalog.segments
            .filter { it.state == "ACTIVE" }
            .map { it to score(tokens, it.name + " " + it.code) }
            .filter { it.second > 0 }
            .sortedByDescending { it.second }
            .take(5)
            .map { it.first.name }
    }

    private fun tokens(value: String): List<String> = value.lowercase().split(Regex("""[^\p{L}\p{N}]+""")).filter { it.length >= 3 }

    private fun score(
        queryTokens: List<String>,
        label: String,
    ): Int {
        val hay = tokens(label)
        return queryTokens.count { token -> hay.any { it.contains(token) || token.contains(it) } }
    }

    private fun slug(raw: String): String {
        val mapped =
            buildString {
                raw.lowercase().forEach { ch ->
                    val letter = TRANSLIT[ch]
                    if (letter != null) append(letter) else append(ch)
                }
            }
        val clean = mapped.replace(Regex("""[^a-z0-9]+"""), "")
        return clean.ifBlank { "customPlace" }.take(40)
    }

    private val TRANSLIT =
        mapOf(
            'а' to "a",
            'б' to "b",
            'в' to "v",
            'г' to "g",
            'д' to "d",
            'е' to "e",
            'ё' to "e",
            'ж' to "zh",
            'з' to "z",
            'и' to "i",
            'й' to "i",
            'к' to "k",
            'л' to "l",
            'м' to "m",
            'н' to "n",
            'о' to "o",
            'п' to "p",
            'р' to "r",
            'с' to "s",
            'т' to "t",
            'у' to "u",
            'ф' to "f",
            'х' to "h",
            'ц' to "c",
            'ч' to "ch",
            'ш' to "sh",
            'щ' to "sch",
            'ъ' to "",
            'ы' to "y",
            'ь' to "",
            'э' to "e",
            'ю' to "yu",
            'я' to "ya",
        )
}
