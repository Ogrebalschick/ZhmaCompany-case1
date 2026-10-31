package ru.zhma.campaign.ai.agent

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TextExtractorTest {
    private val catalog = TestCatalog.full()

    @Test
    fun demoBrief() {
        val e = TextExtractor.extract("Кампания на баню и спа в Сочи для женщин, сторис на главном, до конца года", catalog)
        assertEquals(listOf("CITY_SLD", "FEMALE"), e.segments)
        assertEquals(listOf(1L), e.placeIds)
        assertEquals("SEGMENTED", e.audienceType)
        assertFalse(e.wantsCreative)
    }

    @Test
    fun ageWithoutExactSegmentIsExplained() {
        val e = TextExtractor.extract("для женщин 35+ из Москвы", catalog)
        assertTrue("FEMALE" in e.segments)
        assertTrue("CITY_MSK" in e.segments)
        assertTrue("AGE_26_45" in e.segments && "AGE_46_60" in e.segments)
        assertTrue(e.notes.single().contains("35+"))
    }

    @Test
    fun regionDoesNotAddCity() {
        val e = TextExtractor.extract("для Московской области", catalog)
        assertEquals(listOf("REGION_MOS_OBL"), e.segments)
    }

    @Test
    fun allUsers() {
        assertEquals("ALL_USERS", TextExtractor.extract("показать всем пользователям в каталоге", catalog).audienceType)
        assertEquals(listOf(3L), TextExtractor.extract("показать всем пользователям в каталоге", catalog).placeIds)
    }

    @Test
    fun creativeAndImageRequests() {
        assertTrue(TextExtractor.extract("сделай сторис с акцентом на тишину", catalog).wantsCreative)
        assertTrue(TextExtractor.extract("нарисуй картинку с бассейном", catalog).wantsImage)
        assertFalse(TextExtractor.extract("хочу кампанию, сторис на главном", catalog).wantsCreative)
    }

    @Test
    fun quotedName() {
        assertEquals("Тёплый ноябрь", TextExtractor.extract("назови «Тёплый ноябрь»", catalog).name)
    }
}
