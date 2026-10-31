package ru.zhma.campaign.ai.agent

import kotlinx.coroutines.runBlocking
import ru.zhma.campaign.ai.llm.LlmClient
import ru.zhma.campaign.ai.llm.MockLlmClient
import ru.zhma.campaign.ai.model.BannerDraft
import ru.zhma.campaign.ai.model.CampaignListItem
import ru.zhma.campaign.ai.model.CommitAction
import ru.zhma.campaign.ai.model.DraftCampaign
import ru.zhma.campaign.ai.model.TurnInput
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FinalPathsTest {
    private val ready =
        DraftCampaign(
            name = "Квартиры в Самаре",
            startDate = "2026-11-05",
            startTime = "10:00",
            topic = "SALES",
            audienceType = "ALL_USERS",
            placeIds = listOf(1),
        )

    private fun input(
        draft: DraftCampaign,
        text: String,
        campaigns: List<CampaignListItem> = emptyList(),
    ) = TurnInput(draft, text, TestCatalog.full(), "2026-11-01", campaigns)

    /** llama.cpp с json_object отдаёт объект, а не массив — текст баннера должен приниматься. */
    @Test
    fun bannerCopyAcceptsObject() =
        runBlocking {
            val llm =
                object : LlmClient {
                    override suspend fun chat(
                        system: String,
                        user: String,
                        temperature: Double,
                        json: Boolean,
                    ): String =
                        if (system.contains("CAMPAIGN_EDIT")) {
                            """{"intent":"GENERATE_CREATIVE","patch":{}}"""
                        } else {
                            """{"headline":"Студии в Самаре!","body":"Квартиры-студии 10–15 м² рядом с центром","cta":"Смотреть"}"""
                        }
                }
            val r = AgentTurn(llm).handle(input(ready, "добавь на баннер текст, что квартиры студии 10-15 метров"))
            assertEquals("Квартиры-студии 10–15 м² рядом с центром", r.draft.banner?.text)
            assertEquals("Студии в Самаре", r.draft.banner?.title)
        }

    @Test
    fun unknownAgeOffersNewSegment() =
        runBlocking {
            val r = AgentTurn(MockLlmClient()).handle(input(DraftCampaign(), "кампания для клиентов 65+"))
            val proposal = r.draft.pendingProposal
            assertEquals("SEGMENT", proposal?.kind)
            assertEquals("AGE_65_PLUS", proposal?.code)
            assertEquals(listOf("AGE_60_PLUS"), proposal?.replaces)
            val yes = AgentTurn(MockLlmClient()).handle(input(r.draft, "Да"))
            assertEquals(CommitAction.CREATE_SEGMENT, yes.commit?.action)
        }

    @Test
    fun stopNeedsYesAndAuditIsRead() =
        runBlocking {
            val active = listOf(CampaignListItem(3, "Квартиры", "ACTIVE", true, "SALES", "2026-11-05"))
            val saved = ready.copy(savedCampaignId = 3, knownState = "ACTIVE", banner = BannerDraft("STORY", "ANDROID", "a", "b"))
            val ask = AgentTurn(MockLlmClient()).handle(input(saved, "останови показ", active))
            assertEquals(null, ask.commit)
            val yes = AgentTurn(MockLlmClient()).handle(input(ask.draft, "Да", active))
            assertEquals(CommitAction.STOP, yes.commit?.action)
            val audit = AgentTurn(MockLlmClient()).handle(input(saved, "покажи журнал", active))
            assertEquals(CommitAction.SHOW_AUDIT, audit.commit?.action)
            assertTrue(audit.commit?.campaignId == 3L)
        }
}

class AudienceWordIsNotAuditTest {
    @Test
    fun briefWithAudienceWordIsBrief() =
        runBlocking {
            val text =
                "Делаем рекламную кампанию туров в Новосибирск \"Зима в Сибири\" с кэшбеком по карте, " +
                    "для любителей путешествовать! Аудитория от 25 до 45 лет"
            val r = AgentTurn(MockLlmClient()).handle(TurnInput(DraftCampaign(), text, TestCatalog.full(), "2026-11-01"))
            assertEquals(null, r.commit)
            assertEquals("Зима в Сибири", r.draft.name)
            assertTrue("AGE_26_45" in r.draft.includedSegments)
            assertTrue(!r.reply.text.contains("Журнал"))
        }
}

class TagsAndWarningsTest {
    private val ready =
        DraftCampaign(
            name = "Зима в Сибири",
            startDate = "2026-12-01",
            startTime = "10:00",
            topic = "LIFESTYLE",
            audienceType = "SEGMENTED",
            includedSegments = listOf("CITY_SLD", "FEMALE"),
            placeIds = listOf(1),
        )

    private fun input(
        draft: DraftCampaign,
        text: String,
    ) = TurnInput(draft, text, TestCatalog.full(), "2026-11-01")

    @Test
    fun tagRequestShowsMenuThenSetsTag() =
        runBlocking {
            val ask = AgentTurn(MockLlmClient()).handle(input(ready, "давай укажем тег кампании"))
            assertEquals("tags", ask.draft.awaiting)
            val menu =
                ask.reply.blocks
                    .filterIsInstance<ru.zhma.campaign.ai.model.UiBlock.QuickReplies>()
                    .single()
            assertTrue(menu.multi)
            val picked = AgentTurn(MockLlmClient()).handle(input(ask.draft, "Промо"))
            assertEquals(listOf(2L), picked.draft.tagIds)
        }

    @Test
    fun warningsAreShownOnce() =
        runBlocking {
            val first = AgentTurn(MockLlmClient()).handle(input(ready.copy(startTime = null), "в 10:00"))
            assertTrue(first.reply.blocks.any { it is ru.zhma.campaign.ai.model.UiBlock.Warnings })
            val second = AgentTurn(MockLlmClient()).handle(input(first.draft, "в 11:00"))
            assertTrue(second.reply.blocks.none { it is ru.zhma.campaign.ai.model.UiBlock.Warnings })
        }
}
