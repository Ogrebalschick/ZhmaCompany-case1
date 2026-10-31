package ru.zhma.campaign.ai.agent

import ru.zhma.campaign.ai.model.DraftCampaign
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class RelativeDatesTest {
    @Test
    fun datesComeFromSpeechNotFromPatch() {
        val today = "2026-11-01"
        val twoWeeks =
            RelativeDates.ground(
                RelativeDates.keepSpokenDates(DraftCampaign(), DraftCampaign(startDate = "2030-01-01")),
                "на 2 недели",
                today,
            )
        assertEquals(today, twoWeeks.startDate)
        assertEquals("2026-11-15", twoWeeks.endDate)

        val unspoken =
            RelativeDates.ground(
                RelativeDates.keepSpokenDates(DraftCampaign(), DraftCampaign(startDate = "2030-01-01", name = "Вклад")),
                "кампания без даты",
                today,
            )
        assertNull(unspoken.startDate)
        assertEquals("Вклад", unspoken.name)
    }
}
