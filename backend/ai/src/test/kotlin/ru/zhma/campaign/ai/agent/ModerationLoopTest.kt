package ru.zhma.campaign.ai.agent

import kotlinx.coroutines.runBlocking
import ru.zhma.campaign.ai.llm.MockLlmClient
import ru.zhma.campaign.ai.model.CampaignListItem
import ru.zhma.campaign.ai.model.CommitAction
import ru.zhma.campaign.ai.model.DraftCampaign
import ru.zhma.campaign.ai.model.TurnInput
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ModerationLoopTest {
    private val onModeration = CampaignListItem(5, "Спа в Сочи", "ON_MODERATION", false, "LIFESTYLE", "2026-11-10")

    private fun input(
        text: String,
        role: String,
        draft: DraftCampaign = DraftCampaign(),
        campaigns: List<CampaignListItem> = listOf(onModeration),
    ) = TurnInput(draft, text, TestCatalog.full(), "2026-11-01", campaigns, role = role)

    @Test
    fun moderatorDeclineNeedsComment() =
        runBlocking {
            val agent = AgentTurn(MockLlmClient())
            val noComment = agent.handle(input("отклони 5", "campaignModerator"))
            assertNull(noComment.commit)
            val ok = agent.handle(input("Отклони 5: добавьте картинку", "campaignModerator"))
            assertEquals(CommitAction.DECLINE, ok.commit?.action)
            assertEquals(5L, ok.commit?.campaignId)
            assertEquals("добавьте картинку", ok.commit?.comment)
            val approve = agent.handle(input("одобрить", "campaignModerator"))
            assertEquals(CommitAction.APPROVE, approve.commit?.action)
        }

    @Test
    fun managerSeesDeclineOnceAndCanResubmit() =
        runBlocking {
            val agent = AgentTurn(MockLlmClient())
            val declined = onModeration.copy(state = "NEEDS_WORK", lastComment = "добавьте картинку")
            val draft =
                DraftCampaign(
                    name = "Спа в Сочи",
                    startDate = "2026-11-10",
                    startTime = "10:00",
                    topic = "LIFESTYLE",
                    audienceType = "ALL_USERS",
                    placeIds = listOf(1),
                    savedCampaignId = 5,
                    knownState = "ON_MODERATION",
                )
            val first = agent.handle(input("привет", "campaignManager", draft, listOf(declined)))
            assertTrue(first.reply.text.contains("добавьте картинку"))
            assertEquals("NEEDS_WORK", first.draft.knownState)
            val again = agent.handle(input("отправь на модерацию", "campaignManager", first.draft, listOf(declined)))
            assertEquals(CommitAction.TO_MODERATION, again.commit?.action)
            assertTrue(!again.reply.text.contains("вернул"))
        }

    @Test
    fun openCampaignAsksServerToLoad() =
        runBlocking {
            val r = AgentTurn(MockLlmClient()).handle(input("открой кампанию 5", "campaignManager"))
            assertEquals(CommitAction.LOAD_CAMPAIGN, r.commit?.action)
            assertEquals(5L, r.commit?.campaignId)
        }
}
