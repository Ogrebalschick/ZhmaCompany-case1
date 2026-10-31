package ru.zhma.campaign.ai.agent

import ru.zhma.campaign.ai.model.DraftCampaign
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters

/** Что о датах и времени сказано в одной реплике. Пустые поля — не сказано. */
data class DateFacts(
    val startDate: String? = null,
    val endDate: String? = null,
    val startTime: String? = null,
    val endTime: String? = null,
    val durationDays: Long? = null,
    val mentioned: Boolean = false,
) {
    val isEmpty: Boolean
        get() = startDate == null && endDate == null && startTime == null && endTime == null && durationDays == null
}

/**
 * Разбор дат и времени из русской речи кодом, без модели.
 *
 * Понимает: `2026-11-10`, `10.11`, `10.11.2026`, «10 ноября», «сегодня», «завтра», «послезавтра»,
 * «в понедельник», «через 3 дня», «до конца года / месяца / недели», «до конца ноября»,
 * «на 2 недели», «10:00», «в 10.30», «в 10», «в 7 вечера», «полдень».
 * Роль даты (старт или конец) берётся из соседних слов: «с», «старт», «начало» — старт;
 * «до», «по», «окончание» — конец. Без маркеров первая дата — старт, вторая — конец.
 */
object DateParser {
    private enum class Role { START, END, NONE }

    private data class Hit(
        val from: Int,
        val to: Int,
        val date: LocalDate,
        val role: Role,
    )

    private data class TimeHit(
        val from: Int,
        val to: Int,
        val time: String,
        val role: Role,
    )

    private const val MONTH =
        "(январ\\p{L}*|янв|феврал\\p{L}*|фев|март\\p{L}*|мар|апрел\\p{L}*|апр|ма[йя]|июн\\p{L}*|июл\\p{L}*|" +
            "август\\p{L}*|авг|сентябр\\p{L}*|сент|сен|октябр\\p{L}*|окт|ноябр\\p{L}*|нояб|ноя|декабр\\p{L}*|дек)(?!\\p{L})\\.?"

    private val isoDate = Regex("""(?<!\d)(\d{4})-(\d{1,2})-(\d{1,2})(?!\d)""")
    private val numericDate = Regex("""(?<![\d.:])(\d{1,2})[./](\d{2})(?:[./](\d{4}|\d{2}))?(?![\d:])""")
    private val textDate = Regex("""(?<!\d)(\d{1,2})(?:-?(?:го|е|ое))?\s+$MONTH(?:\s+(\d{4}))?(?:\s*(?:года|г\.?)(?!\p{L}))?""")
    private val wordDate = Regex("""(?<!\p{L})(сегодня|сегодняшн\p{L}*\s+дня|завтра|послезавтра)(?!\p{L})""")
    private val inDays =
        Regex("""через\s+(\d+|один|одну|два|две|три|четыре|пять|шесть|семь|десять)\s+(дн\p{L}*|день|недел\p{L}*|месяц\p{L}*)""")
    private val weekday =
        Regex(
            """(?<!\p{L})(?:в|во|с|со|до|по)\s+(следующ\p{L}*\s+)?(понедельник\p{L}*|вторник\p{L}*|сред[ауы]|четверг\p{L}*|пятниц[ауы]|суббот[ауы]|воскресень[еяю])""",
        )
    private val periodEnd = Regex("""(?<!\p{L})(?:до\s+)?кон[е]?ц\p{L}*\s+(года|месяца|недели|квартала)""")
    private val monthEnd = Regex("""(?<!\p{L})(?:до\s+)?кон[е]?ц\p{L}*\s+$MONTH""")
    private val monthStart = Regex("""(?<!\p{L})(?:с\s+)?начал\p{L}*\s+$MONTH""")
    private val newYear = Regex("""до\s+нового\s+года""")
    private val duration =
        Regex(
            """(?<!\p{L})на\s+(\d+|одн[уо]|один|два|две|три|четыре|пять|шесть|семь|восемь|девять|десять)?\s*(дн\p{L}*|день|недел\p{L}*|месяц\p{L}*)(?!\p{L})""",
        )

    private val colonTime = Regex("""(?<![\d.:])([01]?\d|2[0-3]):([0-5]\d)(?!\d)""")
    private val dotTime = Regex("""(?<![\d.:])([01]?\d|2[0-3])\.([0-5]\d)(?![\d.])""")
    private val hourTime =
        Regex(
            """(?<!\p{L})(в|к|с|до|по)\s+(\d{1,2})(?:\s*(час\p{L}*|ч)(?!\p{L}))?(?:\s*(утра|дня|вечера|ночи))?(?=\s*(?:$|[,.;!?)\n]|и\s|по\s|мск|до\s))""",
        )
    private val noonWords = Regex("""(?<!\p{L})(полдень|полночь|полуночи)(?!\p{L})""")

    private val endMarker =
        Regex(
            """(?<!\p{L})(до|по|конец|конца|окончани\p{L}*|окончить|заканчива\p{L}*|закончи\p{L}*|завершени\p{L}*|завершить|финиш\p{L}*|дедлайн|стоп)(?!\p{L})""",
        )
    private val startMarker =
        Regex(
            """(?<!\p{L})(с|со|от|старт\p{L}*|начал\p{L}*|начина\p{L}*|начать|запуск\p{L}*|запусти\p{L}*|стартуем|стартует)(?!\p{L})""",
        )

    private val numbers =
        mapOf(
            "один" to 1L,
            "одну" to 1L,
            "одно" to 1L,
            "два" to 2L,
            "две" to 2L,
            "три" to 3L,
            "четыре" to 4L,
            "пять" to 5L,
            "шесть" to 6L,
            "семь" to 7L,
            "восемь" to 8L,
            "девять" to 9L,
            "десять" to 10L,
        )

    fun parse(
        rawText: String,
        todayIso: String,
        awaiting: String? = null,
    ): DateFacts {
        val today = LocalDate.parse(todayIso)
        val text = rawText.lowercase().replace('ё', 'е')
        val hits = mutableListOf<Hit>()
        val taken = BooleanArray(text.length)

        fun free(range: IntRange): Boolean = range.none { taken[it] }

        fun add(
            range: IntRange,
            date: LocalDate?,
            forced: Role? = null,
        ) {
            if (date == null || !free(range)) return
            range.forEach { taken[it] = true }
            hits += Hit(range.first, range.last + 1, date, forced ?: Role.NONE)
        }

        periodEnd.findAll(text).forEach { m ->
            val date =
                when (m.groupValues[1]) {
                    "года" -> LocalDate.of(today.year, 12, 31)
                    "месяца" -> today.with(TemporalAdjusters.lastDayOfMonth())
                    "недели" -> today.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY))
                    else -> quarterEnd(today)
                }
            add(m.range, date, Role.END)
        }
        newYear.findAll(text).forEach { m -> add(m.range, LocalDate.of(today.year, 12, 31), Role.END) }
        monthEnd.findAll(text).forEach { m ->
            val month = monthIndex(m.groupValues[1]) ?: return@forEach
            val first = rollYear(LocalDate.of(today.year, month, 1).with(TemporalAdjusters.lastDayOfMonth()), today, false)
            add(m.range, first.with(TemporalAdjusters.lastDayOfMonth()), Role.END)
        }
        monthStart.findAll(text).forEach { m ->
            val month = monthIndex(m.groupValues[1]) ?: return@forEach
            add(m.range, rollYear(LocalDate.of(today.year, month, 1), today, false))
        }
        isoDate.findAll(text).forEach { m ->
            val (y, mo, d) = m.destructured
            add(m.range, safeDate(y.toInt(), mo.toInt(), d.toInt()))
        }
        textDate.findAll(text).forEach { m ->
            val day = m.groupValues[1].toInt()
            val month = monthIndex(m.groupValues[2]) ?: return@forEach
            val year = m.groupValues[3].toIntOrNull()
            val date = safeDate(year ?: today.year, month, day)
            add(m.range, if (year == null && date != null) rollYear(date, today, true) else date)
        }
        numericDate.findAll(text).forEach { m ->
            val day = m.groupValues[1].toInt()
            val month = m.groupValues[2].toInt()
            val yearRaw = m.groupValues[3]
            val before = text.substring(maxOf(0, m.range.first - 3), m.range.first)
            val looksLikeTime = yearRaw.isEmpty() && Regex("""(?:^|\s)(?:в|к)\s*$""").containsMatchIn(before)
            if (looksLikeTime) return@forEach
            val year =
                when (yearRaw.length) {
                    0 -> null
                    2 -> 2000 + yearRaw.toInt()
                    else -> yearRaw.toInt()
                }
            val date = safeDate(year ?: today.year, month, day)
            add(m.range, if (year == null && date != null) rollYear(date, today, true) else date)
        }
        wordDate.findAll(text).forEach { m ->
            val word = m.groupValues[1]
            val date =
                when {
                    word.startsWith("сегодня") -> today
                    word == "завтра" -> today.plusDays(1)
                    else -> today.plusDays(2)
                }
            add(m.range, date)
        }
        inDays.findAll(text).forEach { m ->
            val n = count(m.groupValues[1]) ?: return@forEach
            add(m.range, today.plusDays(n * unitDays(m.groupValues[2])))
        }
        weekday.findAll(text).forEach { m ->
            val dow = dayOfWeek(m.groupValues[2]) ?: return@forEach
            var date = today.with(TemporalAdjusters.next(dow))
            if (m.groupValues[1].isNotBlank() && date.isBefore(today.plusDays(7))) date = date.plusWeeks(1)
            add(m.range, date)
        }

        val durationDays =
            duration.findAll(text).firstNotNullOfOrNull { m ->
                if (!free(m.range)) return@firstNotNullOfOrNull null
                val n = if (m.groupValues[1].isBlank()) 1L else count(m.groupValues[1])
                n?.let { it * unitDays(m.groupValues[2]) }
            }

        hits.sortBy { it.from }
        val roled =
            hits.mapIndexed { index, hit ->
                if (hit.role != Role.NONE) {
                    hit
                } else {
                    val windowStart = maxOf(if (index > 0) hits[index - 1].to else 0, hit.from - 30)
                    hit.copy(role = roleBefore(text.substring(windowStart, hit.from)))
                }
            }
        val dates = resolveRoles(roled.map { it.role }, awaiting == "endDate")
        var startDate: LocalDate? = null
        var endDate: LocalDate? = null
        roled.forEachIndexed { index, hit ->
            when (dates[index]) {
                Role.START -> if (startDate == null) startDate = hit.date
                Role.END -> endDate = hit.date
                Role.NONE -> Unit
            }
        }

        val masked = buildString { text.forEachIndexed { i, ch -> append(if (taken[i]) ' ' else ch) } }
        val times = findTimes(masked)
        val timeRoles =
            times.mapIndexed { index, t ->
                if (t.role != Role.NONE) return@mapIndexed t.role
                val windowStart = maxOf(if (index > 0) times[index - 1].to else 0, t.from - 25)
                val marked = roleBefore(masked.substring(windowStart, t.from))
                if (marked != Role.NONE) return@mapIndexed marked
                val nearDate = roled.lastOrNull { it.to <= t.from && t.from - it.to <= 12 }
                if (nearDate != null) dates[roled.indexOf(nearDate)] else Role.NONE
            }
        val resolvedTimes = resolveRoles(timeRoles, awaiting == "endTime")
        var startTime: String? = null
        var endTime: String? = null
        times.forEachIndexed { index, t ->
            when (resolvedTimes[index]) {
                Role.START -> if (startTime == null) startTime = t.time
                Role.END -> endTime = t.time
                Role.NONE -> Unit
            }
        }

        val mentioned =
            hits.isNotEmpty() ||
                times.isNotEmpty() ||
                durationDays != null ||
                Regex("""\d""").containsMatchIn(text) ||
                Regex(MONTH).containsMatchIn(text)
        return DateFacts(
            startDate = startDate?.toString(),
            endDate = endDate?.toString(),
            startTime = startTime,
            endTime = endTime,
            durationDays = durationDays,
            mentioned = mentioned,
        )
    }

    /** Накладывает сказанное на черновик. Несказанное не трогает. */
    fun apply(
        draft: DraftCampaign,
        facts: DateFacts,
    ): DraftCampaign {
        val start = facts.startDate ?: draft.startDate
        var end = facts.endDate ?: draft.endDate
        var pending = draft.pendingDurationDays
        val days = facts.durationDays
        if (days != null && facts.endDate == null) {
            if (start != null) {
                end = LocalDate.parse(start).plusDays(days).toString()
                pending = null
            } else {
                pending = days
            }
        } else if (facts.startDate != null && pending != null && facts.endDate == null && draft.endDate == null) {
            end = LocalDate.parse(facts.startDate).plusDays(pending).toString()
            pending = null
        }
        return draft.copy(
            startDate = start,
            endDate = end,
            startTime = facts.startTime ?: draft.startTime,
            endTime = facts.endTime ?: draft.endTime,
            pendingDurationDays = pending,
        )
    }

    private val human = DateTimeFormatter.ofPattern("dd.MM.yyyy")

    fun human(iso: String?): String? = iso?.let { runCatching { LocalDate.parse(it).format(human) }.getOrDefault(it) }

    private fun findTimes(text: String): List<TimeHit> {
        val out = mutableListOf<TimeHit>()
        val used = BooleanArray(text.length)

        fun push(
            range: IntRange,
            hour: Int,
            minute: Int,
            role: Role = Role.NONE,
        ) {
            if (hour !in 0..23 || minute !in 0..59 || range.any { used[it] }) return
            range.forEach { used[it] = true }
            out += TimeHit(range.first, range.last + 1, "%02d:%02d".format(hour, minute), role)
        }
        colonTime.findAll(text).forEach { m -> push(m.range, m.groupValues[1].toInt(), m.groupValues[2].toInt()) }
        dotTime.findAll(text).forEach { m -> push(m.range, m.groupValues[1].toInt(), m.groupValues[2].toInt()) }
        hourTime.findAll(text).forEach { m ->
            var hour = m.groupValues[2].toInt()
            when (m.groupValues[4]) {
                "вечера" -> if (hour in 1..11) hour += 12
                "дня" -> if (hour in 1..6) hour += 12
                "ночи" -> if (hour == 12) hour = 0
            }
            val preposition = m.groupValues[1]
            val role =
                when (preposition) {
                    "до", "по" -> Role.END
                    "с" -> Role.START
                    else -> Role.NONE
                }
            val numberStart = m.range.first + m.value.indexOf(m.groupValues[2])
            push(numberStart..m.range.last, hour, 0, role)
        }
        noonWords.findAll(text).forEach { m -> push(m.range, if (m.groupValues[1] == "полдень") 12 else 0, 0) }
        return out.sortedBy { it.from }
    }

    private fun roleBefore(window: String): Role {
        val end = endMarker.findAll(window).lastOrNull()
        val start = startMarker.findAll(window).lastOrNull()
        return when {
            end == null && start == null -> Role.NONE
            start == null -> Role.END
            end == null -> Role.START
            end.range.first > start.range.first -> Role.END
            else -> Role.START
        }
    }

    private fun resolveRoles(
        roles: List<Role>,
        preferEnd: Boolean,
    ): List<Role> {
        val out = roles.toMutableList()
        var hasStart = out.contains(Role.START)
        var hasEnd = out.contains(Role.END)
        out.indices.forEach { i ->
            if (out[i] != Role.NONE) return@forEach
            out[i] =
                when {
                    preferEnd && !hasEnd -> Role.END.also { hasEnd = true }
                    !hasStart -> Role.START.also { hasStart = true }
                    !hasEnd -> Role.END.also { hasEnd = true }
                    else -> Role.NONE
                }
        }
        return out
    }

    private fun safeDate(
        year: Int,
        month: Int,
        day: Int,
    ): LocalDate? = runCatching { LocalDate.of(year, month, day) }.getOrNull()

    private fun rollYear(
        date: LocalDate,
        today: LocalDate,
        exactDay: Boolean,
    ): LocalDate {
        val past = if (exactDay) date.isBefore(today) else date.withDayOfMonth(date.lengthOfMonth()).isBefore(today)
        return if (past) date.plusYears(1) else date
    }

    private fun quarterEnd(today: LocalDate): LocalDate {
        val lastMonth = ((today.monthValue - 1) / 3 + 1) * 3
        return LocalDate.of(today.year, lastMonth, 1).with(TemporalAdjusters.lastDayOfMonth())
    }

    private fun count(raw: String): Long? = raw.toLongOrNull() ?: numbers[raw]

    private fun unitDays(unit: String): Long =
        when {
            unit.startsWith("недел") -> 7L
            unit.startsWith("месяц") -> 30L
            else -> 1L
        }

    private fun monthIndex(word: String): Int? {
        val w = word.trimEnd('.')
        return when {
            w.startsWith("янв") -> 1
            w.startsWith("фев") -> 2
            w.startsWith("мар") -> 3
            w.startsWith("апр") -> 4
            w.startsWith("ма") -> 5
            w.startsWith("июн") -> 6
            w.startsWith("июл") -> 7
            w.startsWith("авг") -> 8
            w.startsWith("сен") -> 9
            w.startsWith("окт") -> 10
            w.startsWith("ноя") -> 11
            w.startsWith("дек") -> 12
            else -> null
        }
    }

    private fun dayOfWeek(word: String): DayOfWeek? =
        when {
            word.startsWith("понедельник") -> DayOfWeek.MONDAY
            word.startsWith("вторник") -> DayOfWeek.TUESDAY
            word.startsWith("сред") -> DayOfWeek.WEDNESDAY
            word.startsWith("четверг") -> DayOfWeek.THURSDAY
            word.startsWith("пятниц") -> DayOfWeek.FRIDAY
            word.startsWith("суббот") -> DayOfWeek.SATURDAY
            word.startsWith("воскресен") -> DayOfWeek.SUNDAY
            else -> null
        }
}
