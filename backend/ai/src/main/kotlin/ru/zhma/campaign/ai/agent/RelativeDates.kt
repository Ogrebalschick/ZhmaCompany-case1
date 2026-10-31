package ru.zhma.campaign.ai.agent

import ru.zhma.campaign.ai.model.DraftCampaign
import java.time.LocalDate

object RelativeDates {
    internal fun keepSpokenDates(
        before: DraftCampaign,
        merged: DraftCampaign,
    ): DraftCampaign =
        merged.copy(
            startDate = before.startDate,
            startTime = before.startTime,
            endDate = before.endDate,
            endTime = before.endTime,
        )

    private val isoDate = Regex("""\d{4}-\d{2}-\d{2}""")
    private val clock = Regex("""\b(?:[01]\d|2[0-3]):[0-5]\d\b""")
    private val weeks = Regex("""на\s+(\d+)\s+недел""", RegexOption.IGNORE_CASE)
    private val daySpan = Regex("""на\s+(\d+)\s+(?:день|дня|дней)""", RegexOption.IGNORE_CASE)

    fun ground(
        before: DraftCampaign,
        userText: String,
        todayIso: String,
    ): DraftCampaign {
        val today = LocalDate.parse(todayIso)
        var start = before.startDate
        var end = before.endDate
        var startTime = before.startTime
        var endTime = before.endTime

        val explicit = isoDate.findAll(userText).map { it.value }.toList()
        val wantsTomorrow = Regex("""завтра""", RegexOption.IGNORE_CASE).containsMatchIn(userText)
        val weekMatch = weeks.find(userText)
        val dayMatch = daySpan.find(userText)
        val untilYearEnd = Regex("""до конца года""", RegexOption.IGNORE_CASE).containsMatchIn(userText)

        if (explicit.isNotEmpty()) {
            start = explicit[0]
            if (explicit.size >= 2) end = explicit[1]
        } else if (wantsTomorrow || weekMatch != null || dayMatch != null) {
            val startDate = if (wantsTomorrow) today.plusDays(1) else today
            start = startDate.toString()
            val shiftDays =
                when {
                    weekMatch != null -> weekMatch.groupValues[1].toLong() * 7
                    dayMatch != null -> dayMatch.groupValues[1].toLong()
                    else -> null
                }
            if (shiftDays != null) end = startDate.plusDays(shiftDays).toString()
        }

        if (untilYearEnd) {
            end = LocalDate.of(today.year, 12, 31).toString()
        }

        val times = clock.findAll(userText).map { it.value }.toList()
        if (times.isNotEmpty()) {
            startTime = times[0]
            if (times.size >= 2) endTime = times[1]
        }

        return before.copy(
            startDate = start,
            startTime = startTime,
            endDate = end,
            endTime = endTime,
        )
    }
}
