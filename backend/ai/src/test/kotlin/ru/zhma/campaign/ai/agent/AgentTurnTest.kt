package ru.zhma.campaign.ai.agent

import kotlinx.coroutines.runBlocking
import ru.zhma.campaign.ai.llm.LlmClient
import ru.zhma.campaign.ai.model.CampaignListItem
import ru.zhma.campaign.ai.model.CatalogSnapshot
import ru.zhma.campaign.ai.model.ChannelRef
import ru.zhma.campaign.ai.model.CommitAction
import ru.zhma.campaign.ai.model.DraftCampaign
import ru.zhma.campaign.ai.model.DraftPatch
import ru.zhma.campaign.ai.model.PlaceRef
import ru.zhma.campaign.ai.model.SegmentRef
import ru.zhma.campaign.ai.model.TagRef
import ru.zhma.campaign.ai.model.TopicRef
import ru.zhma.campaign.ai.model.TurnInput
import ru.zhma.campaign.ai.model.UiBlock
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AgentTurnTest {
    @Test
    fun patchDoesNotEraseFilledFields() {
        val merged =
            DraftMerger.apply(
                DraftCampaign(name = "Старое имя", topic = "SALES", placeIds = listOf(1)),
                DraftPatch(marketingName = "Весенний вклад"),
            )
        assertEquals("Старое имя", merged.name)
        assertEquals("SALES", merged.topic)
        assertEquals(listOf(1L), merged.placeIds)
        assertEquals("Весенний вклад", merged.marketingName)
    }

    @Test
    fun allUsersWithSegmentsIsNotReady() {
        val draft =
            readyDraft().copy(
                audienceType = "ALL_USERS",
                includedSegments = listOf("AGE_26_45"),
            )
        val result = CampaignValidator.check(draft, sampleCatalog(), TODAY)
        assertEquals(false, result.readyToSave)
    }

    @Test
    fun confirmReadyDraftAsksToSaveAndDoesNotTouchDisk() =
        runBlocking {
            val result =
                AgentTurn(FakeLlm("""{"intent":"CONFIRM","confirm":true}"""))
                    .handle(input(readyDraft(), "подтверждаю"))
            assertEquals(CommitAction.SAVE_CAMPAIGN, result.commit?.action)
            assertNull(result.draft.savedCampaignId)
        }

    @Test
    fun listCampaignsUsesInput() =
        runBlocking {
            val item =
                CampaignListItem(
                    id = 7,
                    name = "Вклад",
                    state = "DRAFT",
                    published = false,
                    topic = "SALES",
                    startDate = "2026-11-05",
                    endDate = null,
                )
            val result =
                AgentTurn(FakeLlm("""{"intent":"LIST_CAMPAIGNS","patch":{},"confirm":false}"""))
                    .handle(input(DraftCampaign(), "покажи кампании", campaigns = listOf(item)))
            val block =
                result.reply.blocks
                    .filterIsInstance<UiBlock.CampaignList>()
                    .single()
            assertEquals(listOf(item), block.items)
            assertNull(result.commit)
            assertTrue(result.draft.name == null)
        }

    private class FakeLlm(
        private val reply: String,
    ) : LlmClient {
        override suspend fun chat(
            system: String,
            user: String,
            temperature: Double,
            json: Boolean,
        ): String = reply
    }

    private fun input(
        draft: DraftCampaign,
        text: String,
        campaigns: List<CampaignListItem> = emptyList(),
    ) = TurnInput(
        draft = draft,
        userText = text,
        catalog = sampleCatalog(),
        today = TODAY,
        campaigns = campaigns,
    )

    private fun readyDraft() =
        DraftCampaign(
            name = "Вклад весна",
            startDate = "2026-11-05",
            startTime = "10:00",
            topic = "SALES",
            audienceType = "SEGMENTED",
            includedSegments = listOf("AGE_26_45"),
            placeIds = listOf(1),
        )

    private fun sampleCatalog() =
        CatalogSnapshot(
            topics = listOf(TopicRef("SALES", "Продажи"), TopicRef("EXTRA", "Дополнительно")),
            tags = listOf(TagRef(5, "Вклад")),
            segments =
                listOf(
                    SegmentRef("AGE_26_45", "Возраст 26–45", "AGE", "ACTIVE", 24_000_000),
                    SegmentRef("SEG_DRAFT_WINTER", "Черновик. Зимняя кампания", "BASIC", "NEW", 0),
                ),
            places =
                listOf(
                    PlaceRef(1, "mainScreenStories", "Промо-блок на главном экране", "STORY", "ACTIVE"),
                    PlaceRef(4, "oldStoriesSlot", "Архивный сторис-слот", "STORY", "INACTIVE"),
                ),
            bannerTypes = emptyList(),
            channels = listOf(ChannelRef("ANDROID", "Android")),
        )

    private companion object {
        const val TODAY = "2026-11-01"
    }
}
