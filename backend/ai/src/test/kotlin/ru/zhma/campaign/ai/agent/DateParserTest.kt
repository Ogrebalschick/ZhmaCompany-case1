package ru.zhma.campaign.ai.agent

import ru.zhma.campaign.ai.model.DraftCampaign
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DateParserTest {
    private val today = "2026-11-01"

    private fun p(
        text: String,
        awaiting: String? = null,
    ) = DateParser.parse(text, today, awaiting)

    @Test
    fun textMonthWithTime() {
        val f = p("Старт 10 ноября в 10:00")
        assertEquals("2026-11-10", f.startDate)
        assertEquals("10:00", f.startTime)
        assertNull(f.endDate)
    }

    @Test
    fun untilYearEndIsEndDate() {
        val f = p("Кампания на баню и спа в Сочи для женщин, сторис на главном, до конца года")
        assertEquals("2026-12-31", f.endDate)
        assertNull(f.startDate)
        assertNull(f.startTime)
    }

    @Test
    fun rangeWithPrepositions() {
        val f = p("с 15.11 по 20.12.2026, с 9:30 до 21:00")
        assertEquals("2026-11-15", f.startDate)
        assertEquals("2026-12-20", f.endDate)
        assertEquals("09:30", f.startTime)
        assertEquals("21:00", f.endTime)
    }

    @Test
    fun pastDayMonthRollsToNextYear() {
        assertEquals("2027-01-15", p("старт 15 января").startDate)
        assertEquals("2027-10-05", p("5 октября").startDate)
    }

    @Test
    fun relativeWords() {
        assertEquals("2026-11-02", p("завтра в 9 утра").startDate)
        assertEquals("09:00", p("завтра в 9 утра").startTime)
        assertEquals("19:00", p("в 7 вечера").startTime)
        assertEquals("2026-11-01", p("с сегодняшнего дня").startDate)
        assertEquals("2026-11-02", p("в понедельник").startDate)
    }

    @Test
    fun bareHourAndDotTime() {
        assertEquals("10:00", p("в 10").startTime)
        assertEquals("10:30", p("в 10.30").startTime)
        assertNull(p("в 3 местах").startTime)
    }

    @Test
    fun endOfMonthAndTimeAfterEndDate() {
        val f = p("до конца ноября 23:59")
        assertEquals("2026-11-30", f.endDate)
        assertEquals("23:59", f.endTime)
    }

    @Test
    fun durationWaitsForStart() {
        val noStart = DateParser.apply(DraftCampaign(), p("на 2 недели"))
        assertNull(noStart.startDate)
        assertEquals(14L, noStart.pendingDurationDays)
        val withStart = DateParser.apply(noStart, p("старт 10 ноября"))
        assertEquals("2026-11-10", withStart.startDate)
        assertEquals("2026-11-24", withStart.endDate)
    }

    @Test
    fun awaitingEndDateMakesBareDateEnd() {
        val f = p("20 декабря", awaiting = "endDate")
        assertEquals("2026-12-20", f.endDate)
        assertNull(f.startDate)
    }

    @Test
    fun noDatesNoFacts() {
        val f = p("кампания без даты для женщин 35+")
        assertTrue(f.isEmpty)
    }

    @Test
    fun isoDateStillWorks() {
        assertEquals("2026-11-05", p("2026-11-05 10:00").startDate)
    }
}
