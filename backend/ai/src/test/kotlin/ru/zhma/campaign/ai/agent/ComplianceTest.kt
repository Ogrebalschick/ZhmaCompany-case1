package ru.zhma.campaign.ai.agent

import kotlinx.coroutines.runBlocking
import ru.zhma.campaign.ai.model.BannerDraft
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ComplianceTest {
    private fun check(
        title: String,
        text: String,
        name: String = "Кампания",
    ) = runBlocking {
        Compliance(null).check(BannerDraft("STORY", "ANDROID", title, text, cta = "Подробнее"), name, withAi = false)
    }

    @Test
    fun guaranteedCreditIsViolation() {
        val r = check("Лучший кредит", "Гарантированно одобрим под 9%")
        assertEquals("FAIL", r.verdict)
        val rules = r.items.map { it.rule }
        assertTrue("guarantee" in rules && "superlative" in rules && "rate" in rules && "advertiser" in rules)
        assertTrue(r.items.first { it.rule == "rate" }.article == "ст. 28 ч. 3")
    }

    @Test
    fun spaNeedsContraindicationWarning() {
        val r = check("Тепло бани и спа", "Массаж и оздоровительные процедуры в Сочи")
        assertEquals("WARN", r.verdict)
        assertTrue(r.items.any { it.rule == "medical" && it.article == "ст. 24 ч. 7" })
    }

    @Test
    fun cleanTextIsOk() {
        val r = check("Тёплый вечер в Сочи", "Выберите время и отдохните после рабочей недели")
        assertEquals("OK", r.verdict)
        assertTrue(r.items.any { it.rule == "marking" && it.status == "INFO" })
    }
}

class BannerEditTest {
    @Test
    fun exactTitleEditKeepsCampaignName() =
        runBlocking {
            val draft =
                ru.zhma.campaign.ai.model.DraftCampaign(
                    name = "Спа в Сочи",
                    startDate = "2026-11-05",
                    startTime = "10:00",
                    topic = "LIFESTYLE",
                    audienceType = "ALL_USERS",
                    placeIds = listOf(1),
                    banner = BannerDraft("STORY", "ANDROID", "Старый", "Старый текст", cta = "Подробнее"),
                )
            val input =
                ru.zhma.campaign.ai.model.TurnInput(
                    draft,
                    "поменяй заголовок на «Тёплая зима» и кнопку «Записаться»",
                    TestCatalog.full(),
                    "2026-11-01",
                )
            val r =
                AgentTurn(
                    ru.zhma.campaign.ai.llm
                        .MockLlmClient(),
                ).handle(input)
            assertEquals("Спа в Сочи", r.draft.name)
            assertEquals("Тёплая зима", r.draft.banner?.title)
            assertEquals("Записаться", r.draft.banner?.cta)
            assertEquals("Старый текст", r.draft.banner?.text)
        }

    @Test
    fun parseColonForm() {
        val f = BannerEdit.parse("заголовок: Тепло рядом")
        assertEquals("Тепло рядом", f.title)
    }
}
