package ru.zhma.campaign.ai.agent

import ru.zhma.campaign.ai.model.CatalogSnapshot
import ru.zhma.campaign.ai.model.SegmentRef

/** Что код сам понял из реплики, без модели. Всё сверено со справочником. */
data class Extracted(
    val name: String? = null,
    val topic: String? = null,
    val audienceType: String? = null,
    val segments: List<String> = emptyList(),
    val placeIds: List<Long> = emptyList(),
    val placeSuggestions: List<String> = emptyList(),
    val notes: List<String> = emptyList(),
    val wantsCreative: Boolean = false,
    val wantsImage: Boolean = false,
    val wantsList: Boolean = false,
    val bannerType: String? = null,
    /** Своего сегмента под названный возраст нет — предложение его создать. */
    val segmentProposal: ru.zhma.campaign.ai.model.EntityProposal? = null,
)

/**
 * Детерминированный разбор брифа: города и регионы, пол, возраст, места показа, «всем пользователям»,
 * название в кавычках, явная тема. Модель дополняет этот разбор, но не может его затереть.
 */
object TextExtractor {
    private val segmentAliases: Map<String, List<Regex>> =
        mapOf(
            "CITY_MSK" to listOf(Regex("""(?<!\p{L})москв\p{L}*""")),
            "CITY_SPB" to listOf(Regex("""петербург\p{L}*"""), Regex("""(?<!\p{L})(спб|питер\p{L}*)(?!\p{L})""")),
            "CITY_NVS" to listOf(Regex("""новосибирск\p{L}*""")),
            "CITY_KZN" to listOf(Regex("""(?<!\p{L})казан[ьиюе](?!\p{L})""")),
            "CITY_EKB" to listOf(Regex("""екатеринбург\p{L}*"""), Regex("""(?<!\p{L})екб(?!\p{L})""")),
            "CITY_SLD" to listOf(Regex("""(?<!\p{L})сочи(?!\p{L})""")),
            "REGION_MOS_OBL" to listOf(Regex("""московск\p{L}*\s+област\p{L}*"""), Regex("""подмосков\p{L}*""")),
            "REGION_LEN_OBL" to listOf(Regex("""ленинградск\p{L}*\s+област\p{L}*"""), Regex("""ленобласт\p{L}*""")),
            "REGION_KRD_KRAI" to listOf(Regex("""краснодарск\p{L}*\s+кра\p{L}*"""), Regex("""кубан\p{L}*""")),
            "REGION_SVD_OBL" to listOf(Regex("""свердловск\p{L}*\s+област\p{L}*""")),
            "FEMALE" to listOf(Regex("""(?<!\p{L})(женщин\p{L}*|девуш\p{L}*|женск\p{L}*|дам(?:ам|ы)?)(?!\p{L})""")),
            "MALE" to listOf(Regex("""(?<!\p{L})(мужчин\p{L}*|мужск\p{L}*|парн(?:ей|ям|и))(?!\p{L})""")),
            "CHANNEL_NEW_USER" to listOf(Regex("""нов\p{L}*\s+(пользовател|клиент)\p{L}*""")),
            "CHANNEL_VIP" to listOf(Regex("""(?<!\p{L})vip(?!\p{L})"""), Regex("""(?<!\p{L})вип(?!\p{L})""")),
        )

    private val allUsers =
        Regex(
            """(?<!\p{L})(всем\s+(пользовател|клиент|юзер)\p{L}*|все[мх]?\s+пользовател\p{L}*|для\s+всех|на\s+всех(?!\s+мест)|без\s+сегмент\p{L}*|вся\s+база|всей\s+базе|всю\s+базу|массов\p{L}*\s+(показ|рассылк|аудитор)\p{L}*)""",
        )
    private val segmentedWord = Regex("""(?<!\p{L}|без\s)(сегмент\p{L}*|сегментн\p{L}*)""")

    private val quoted = Regex("""[«"“„']([^«»"“”„']{2,100})[»"”“']""")
    private val namedAs =
        Regex(
            """(?<!\p{L})(?:назов\p{L}*|назвать|назван\p{L}*|имя\s+кампании|название\s+кампании)(?:\s+(?:её|ее|его|кампанию|как|будет|пусть))*\s*[:—-]?\s+([^,.;!?\n]{2,80})""",
        )
    private val topicNamed = Regex("""(?<!\p{L})тем[аыуе](?:\s+кампании)?\s*[:—-]?\s*([\p{L}_]{3,30})""")

    private const val VERB =
        "(?:сделай|сделать|сделаем|создай|создать|сгенерируй|сгенерировать|сгенерируем|генерируй|нарисуй|нарисовать|" +
            "подготовь|подготовить|придумай|придумать|напиши|написать|собери|переделай|перепиши|перегенерируй|" +
            "покажи|давай|нужен|нужна|нужно|хочу|добавь|обнови|поменяй|замени|ещё|еще|другой|другую|новый|новую)"
    private const val CREATIVE_OBJECT =
        "(?:баннер\\p{L}*|креатив\\p{L}*|макет\\p{L}*|превью|сторис(?!\\s+на\\s+(?:главн|экран))|stories|story|" +
            "текст\\p{L}*|заголов\\p{L}*|вариант\\p{L}*)"
    private const val IMAGE_OBJECT =
        "(?:картинк\\p{L}*|изображени\\p{L}*|иллюстрац\\p{L}*|фото\\p{L}*|фон(?!\\p{L})|обложк\\p{L}*|визуал\\p{L}*|рисун\\p{L}*)"
    private val creativeRequest = Regex("""(?<!\p{L})$VERB(?:\s+[\p{L}-]+){0,3}?\s+$CREATIVE_OBJECT""")
    private val imageRequest = Regex("""(?<!\p{L})$VERB(?:\s+[\p{L}-]+){0,3}?\s+$IMAGE_OBJECT|^\s*$IMAGE_OBJECT""")
    private val listRequest = Regex("""(список|покажи|мои|все)\s+(\p{L}+\s+)?кампани\p{L}*|какие\s+(есть\s+)?кампании""")

    private data class AgeRange(
        val from: Int,
        val to: Int,
        val label: String,
    )

    fun extract(
        rawText: String,
        catalog: CatalogSnapshot,
    ): Extracted {
        val text = rawText.lowercase().replace('ё', 'е')
        val notes = mutableListOf<String>()
        val active = catalog.segments.filter { it.state == "ACTIVE" }

        val segments = mutableListOf<String>()
        segmentAliases.forEach { (code, patterns) ->
            if (active.any { it.code == code } && patterns.any { it.containsMatchIn(text) }) segments += code
        }
        active.forEach { seg ->
            val name = seg.name.lowercase().replace('ё', 'е')
            if (name.length >= 5 && text.contains(name) && seg.code !in segments) segments += seg.code
        }
        // «Московская область» не должна давать ещё и Москву.
        if ("REGION_MOS_OBL" in segments &&
            !Regex("""(?<!\p{L})москв\p{L}*""").containsMatchIn(text.replace(Regex("""московск\p{L}*"""), ""))
        ) {
            segments.remove("CITY_MSK")
        }

        var ageProposal: ru.zhma.campaign.ai.model.EntityProposal? = null
        val age = ageRange(text)
        if (age != null) {
            val picked = ageSegments(age, active)
            if (picked.isNotEmpty()) {
                segments += picked.map { it.code }
                val exact = picked.size == 1 && ageBounds(picked[0])?.let { it.first == age.from && it.second == age.to } == true
                if (!exact) {
                    notes +=
                        "Сегмента «${age.label}» в справочнике нет, взял ближайшие по возрасту: ${picked.joinToString(", ") { it.name }}."
                    ageProposal =
                        ru.zhma.campaign.ai.model.EntityProposal(
                            kind = "SEGMENT",
                            name = "Возраст ${age.label}",
                            code = "AGE_${age.from}_" + if (age.to >= 200) "PLUS" else age.to.toString(),
                            description = "Клиенты в возрасте ${age.label}",
                            segmentType = "AGE",
                            replaces = picked.map { it.code },
                        )
                }
            }
        }

        val placeResult = places(text, catalog)
        val audience =
            when {
                allUsers.containsMatchIn(text) -> "ALL_USERS"
                segments.isNotEmpty() || segmentedWord.containsMatchIn(text) -> "SEGMENTED"
                else -> null
            }

        val name =
            quoted
                .find(rawText)
                ?.takeIf { !BannerEdit.mentions(rawText) }
                ?.groupValues
                ?.get(1)
                ?.trim()
                ?: namedAs.find(text)?.let { m -> rawText.substring(m.groups[1]!!.range).trim().trim('«', '»', '"') }

        val topic =
            topicNamed.find(text)?.groupValues?.get(1)?.let { word ->
                catalog.topics
                    .firstOrNull { t ->
                        t.value.equals(word, true) || t.label.lowercase().startsWith(word.take(5)) && word.length >= 5
                    }?.value
            }

        val wantsImage = imageRequest.containsMatchIn(text)
        val wantsCreative = !wantsImage && creativeRequest.containsMatchIn(text)
        val bannerType =
            when {
                Regex("""сторис|stories|story""").containsMatchIn(text) -> "STORY"
                Regex("""смарт|smart""").containsMatchIn(text) -> "SMART_BANNER"
                else -> null
            }

        return Extracted(
            name = name?.takeIf { it.isNotBlank() && it.length <= 100 },
            topic = topic,
            audienceType = audience,
            segments = segments.distinct(),
            placeIds = placeResult.ids,
            placeSuggestions = placeResult.suggestions,
            notes = notes,
            wantsCreative = wantsCreative,
            wantsImage = wantsImage,
            wantsList = listRequest.containsMatchIn(text),
            bannerType = bannerType,
            segmentProposal = ageProposal,
        )
    }

    private data class Places(
        val ids: List<Long>,
        val suggestions: List<String>,
    )

    private fun places(
        text: String,
        catalog: CatalogSnapshot,
    ): Places {
        val byCode = catalog.places.associateBy { it.code }
        val ids = mutableListOf<Long>()
        val suggestions = mutableListOf<String>()
        val main = Regex("""главн\p{L}*""").containsMatchIn(text)
        val story = Regex("""сторис|stories|истори[ия](?!\s+операц)|промо-?блок""").containsMatchIn(text)
        val smart = Regex("""смарт|smart|баннер""").containsMatchIn(text)
        if (main) {
            when {
                story -> byCode["mainScreenStories"]?.let { ids += it.id }
                smart -> byCode["mainScreenSmartBanner"]?.let { ids += it.id }
                else ->
                    listOfNotNull(
                        byCode["mainScreenStories"],
                        byCode["mainScreenSmartBanner"],
                    ).forEach { suggestions += it.description }
            }
        }
        if (Regex("""каталог""").containsMatchIn(text) &&
            !Regex("""до\s+редизайн""").containsMatchIn(text)
        ) {
            byCode["catalogPromo"]?.let { ids += it.id }
        }
        if (Regex("""платеж""").containsMatchIn(text)) byCode["paymentsScreenBanner"]?.let { ids += it.id }
        if (Regex("""истори\p{L}*\s+операц""").containsMatchIn(text)) byCode["historyScreenBanner"]?.let { ids += it.id }
        if (Regex("""экран\p{L}*\s+вклад|вклад\p{L}*\s+экран|раздел\p{L}*\s+вклад""").containsMatchIn(text)) {
            byCode["depositScreenPromo"]?.let {
                ids +=
                    it.id
            }
        }
        catalog.places.forEach { place ->
            val description = place.description.lowercase()
            if (text.contains(description) || text.contains(place.code.lowercase())) ids += place.id
        }
        return Places(ids.distinct(), suggestions.distinct())
    }

    private fun ageRange(text: String): AgeRange? {
        Regex("""(?<!\d)(\d{2})\s*\+""").find(text)?.let { m ->
            val n = m.groupValues[1].toInt()
            if (n in 14..90) return AgeRange(n, 200, "$n+")
        }
        Regex("""(?:старше|от)\s+(\d{2})(?:\s*(?:до|-|–|—)\s*(\d{2}))?\s*(лет|год\p{L}*)?""").find(text)?.let { m ->
            val from = m.groupValues[1].toInt()
            val to = m.groupValues[2].toIntOrNull()
            val isAge = m.value.startsWith("старше") || m.groupValues[3].isNotEmpty()
            if (isAge && from in 14..90) {
                return if (to != null) AgeRange(from, to, "$from–$to") else AgeRange(from, 200, "$from+")
            }
        }
        Regex("""(?<!\d)(\d{2})\s*[-–—]\s*(\d{2})\s*(?:лет|год)""").find(text)?.let { m ->
            return AgeRange(m.groupValues[1].toInt(), m.groupValues[2].toInt(), "${m.groupValues[1]}–${m.groupValues[2]}")
        }
        Regex("""(?:младше|моложе|до)\s+(\d{2})\s*(?:лет|года)""").find(text)?.let { m ->
            return AgeRange(0, m.groupValues[1].toInt(), "до ${m.groupValues[1]}")
        }
        if (Regex("""пенсионер\p{L}*""").containsMatchIn(text)) return AgeRange(60, 200, "60+")
        if (Regex("""студент\p{L}*|молодеж\p{L}*""").containsMatchIn(text)) return AgeRange(18, 25, "18–25")
        return null
    }

    private fun ageBounds(seg: SegmentRef): Pair<Int, Int>? {
        val m = Regex("""AGE_(\d+)_(\d+|PLUS)""").find(seg.code) ?: return null
        val from = m.groupValues[1].toInt()
        val to = m.groupValues[2].toIntOrNull() ?: 200
        return from to to
    }

    private fun ageSegments(
        range: AgeRange,
        active: List<SegmentRef>,
    ): List<SegmentRef> {
        val overlapping =
            active.mapNotNull { seg ->
                val bounds = ageBounds(seg) ?: return@mapNotNull null
                val overlap = minOf(bounds.second, range.to) - maxOf(bounds.first, range.from)
                if (overlap >= 0) seg to overlap else null
            }
        // Пересечение в один-два года («от 25» и «18–25») не считаем: берём сегменты, где возраст реально совпадает.
        val solid = overlapping.filter { it.second >= 2 }.map { it.first }
        return solid.ifEmpty { overlapping.map { it.first } }
    }
}
