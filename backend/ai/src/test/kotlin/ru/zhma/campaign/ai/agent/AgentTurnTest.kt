package ru.zhma.campaign.ai.agent

import kotlinx.coroutines.runBlocking
import ru.zhma.campaign.ai.image.ImageRequests
import ru.zhma.campaign.ai.image.ImageTicket
import ru.zhma.campaign.ai.llm.LlmClient
import ru.zhma.campaign.ai.llm.LlmMessage
import ru.zhma.campaign.ai.llm.LlmToolReply
import ru.zhma.campaign.ai.llm.MockLlmClient
import ru.zhma.campaign.ai.llm.ToolCall
import ru.zhma.campaign.ai.llm.ToolSpec
import ru.zhma.campaign.ai.model.CampaignListItem
import ru.zhma.campaign.ai.model.CommitAction
import ru.zhma.campaign.ai.model.DraftCampaign
import ru.zhma.campaign.ai.model.DraftPatch
import ru.zhma.campaign.ai.model.TurnInput
import ru.zhma.campaign.ai.model.TurnResult
import ru.zhma.campaign.ai.model.UiBlock
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AgentTurnTest {
    @Test
    fun patchDoesNotEraseFilledFields() {
        val merged =
            DraftMerger.apply(
                DraftCampaign(name = "Старое имя", topic = "SALES", placeIds = listOf(1)),
                DraftPatch(marketingName = "Весенний вклад", placeIds = listOf(3)),
            )
        assertEquals("Старое имя", merged.name)
        assertEquals("SALES", merged.topic)
        assertEquals(listOf(1L, 3L), merged.placeIds)
        assertEquals("Весенний вклад", merged.marketingName)
    }

    @Test
    fun allUsersWithSegmentsIsNotReady() {
        val draft = readyDraft().copy(audienceType = "ALL_USERS", includedSegments = listOf("AGE_26_45"))
        assertEquals(false, CampaignValidator.check(draft, catalog, TODAY).readyToSave)
    }

    /** Сценарий из презентации целиком, на модели-заглушке. */
    @Test
    fun demoScenario() =
        runBlocking {
            val agent = AgentTurn(MockLlmClient())
            var draft = DraftCampaign()

            // 1. Бриф: в черновик попадает только сказанное.
            var r = agent.handle(input(draft, "Кампания на баню и спа в Сочи для женщин, сторис на главном, до конца года"))
            draft = r.draft
            assertEquals("Баню и спа в Сочи", draft.name)
            assertEquals(listOf("CITY_SLD", "FEMALE"), draft.includedSegments)
            assertEquals("SEGMENTED", draft.audienceType)
            assertEquals(listOf(1L), draft.placeIds)
            assertEquals("2026-12-31", draft.endDate)
            assertNull(draft.startDate)
            assertNull(draft.startTime)
            assertNull(r.commit)
            assertEquals("LIFESTYLE", draft.topicGuess)
            assertEquals("topic", draft.awaiting)
            val topicMenu =
                r.reply.blocks
                    .filterIsInstance<UiBlock.QuickReplies>()
                    .single()
            assertEquals("Lifestyle", topicMenu.items.first().value)
            assertTrue(topicMenu.items.first().recommended)

            // 2. Ответ кнопкой: модель не нужна.
            r = AgentTurn(ThrowingLlm()).handle(input(draft, "Lifestyle"))
            draft = r.draft
            assertEquals("LIFESTYLE", draft.topic)
            assertEquals("startDate", draft.awaiting)

            // 3. Дата и время старта. Остальное не затирается.
            r = agent.handle(input(draft, "Старт 10 ноября в 10:00"))
            draft = r.draft
            assertEquals("2026-11-10", draft.startDate)
            assertEquals("10:00", draft.startTime)
            assertEquals("2026-12-31", draft.endDate)
            assertEquals(listOf("CITY_SLD", "FEMALE"), draft.includedSegments)
            assertTrue(CampaignValidator.check(draft, catalog, TODAY).readyToSave)
            assertTrue(r.reply.text.contains("старт 10.11.2026 10:00"))

            // 4. Баннер: превью, в базу ничего.
            r = agent.handle(input(draft, "сделай сторис"))
            draft = r.draft
            assertNull(r.commit)
            val banner =
                r.reply.blocks
                    .filterIsInstance<UiBlock.CreativePreview>()
                    .single()
                    .banner
            assertEquals("STORY", banner.bannerType)
            assertTrue(banner.title!!.isNotBlank())

            // 5. Сохранить.
            r = agent.handle(input(draft, "Сохранить"))
            assertEquals(CommitAction.SAVE_CAMPAIGN, r.commit?.action)
            draft = r.draft.copy(savedCampaignId = 42, dirty = false)

            // 6. Второе «да» на модерацию само не отправляет.
            r = agent.handle(input(draft, "да"))
            assertNull(r.commit)

            // 7. Фраза со словом «модерация».
            r = agent.handle(input(draft, "отправь на модерацию"))
            assertEquals(CommitAction.TO_MODERATION, r.commit?.action)
        }

    @Test
    fun repeatedUnclearAnswerGetsHintNotSameLoop() =
        runBlocking {
            val agent = AgentTurn(ThrowingLlm())
            var draft = readyDraft().copy(startDate = null, startTime = null, awaiting = "startDate", askCounts = mapOf("startDate" to 1))
            var r = agent.handle(input(draft, "ну когда-нибудь"))
            draft = r.draft
            assertEquals(2, draft.askCounts["startDate"])
            assertTrue(r.reply.text.contains("например"))
            r = agent.handle(input(draft, "послезавтра в 12"))
            assertEquals("2026-11-03", r.draft.startDate)
            assertEquals("12:00", r.draft.startTime)
        }

    @Test
    fun llmFailureDoesNotBreakTurn() =
        runBlocking {
            val r =
                AgentTurn(
                    ThrowingLlm(),
                ).handle(input(DraftCampaign(), "Кампания для мужчин из Москвы на смарт-баннер на главном с 15 ноября"))
            assertEquals(listOf("CITY_MSK", "MALE"), r.draft.includedSegments)
            assertEquals(listOf(2L), r.draft.placeIds)
            assertEquals("2026-11-15", r.draft.startDate)
            assertEquals("name", r.draft.awaiting)
        }

    @Test
    fun nameAnswerIsTakenAsIs() =
        runBlocking {
            val draft = DraftCampaign(awaiting = "name")
            val r = AgentTurn(ThrowingLlm()).handle(input(draft, "Тёплая осень"))
            assertEquals("Тёплая осень", r.draft.name)
        }

    @Test
    fun hallucinatedAgeFromModelIsDropped() =
        runBlocking {
            val llm = FakeLlm("""{"intent":"CAMPAIGN_EDIT","patch":{"includedSegments":["FEMALE","AGE_26_45"],"name":"Спа"}}""")
            val r = AgentTurn(llm).handle(input(DraftCampaign(), "кампания для женщин"))
            assertEquals(listOf("FEMALE"), r.draft.includedSegments)
        }

    @Test
    fun confirmReadyDraftAsksToSaveAndDoesNotTouchDisk() =
        runBlocking {
            val result = AgentTurn(FakeLlm("""{"intent":"CONFIRM"}""")).handle(input(readyDraft(), "подтверждаю"))
            assertEquals(CommitAction.SAVE_CAMPAIGN, result.commit?.action)
            assertNull(result.draft.savedCampaignId)
        }

    @Test
    fun editAfterSaveMakesDraftDirtyAndSaveUpdates() =
        runBlocking {
            val saved = readyDraft().copy(savedCampaignId = 7)
            val edited = AgentTurn(ThrowingLlm()).handle(input(saved, "старт 20 ноября"))
            assertTrue(edited.draft.dirty)
            val save = AgentTurn(ThrowingLlm()).handle(input(edited.draft, "сохранить"))
            assertEquals(CommitAction.SAVE_CAMPAIGN, save.commit?.action)
            val moderation = AgentTurn(ThrowingLlm()).handle(input(edited.draft, "на модерацию"))
            assertNull(moderation.commit)
        }

    @Test
    fun listCampaignsUsesInput() =
        runBlocking {
            val item = CampaignListItem(7, "Вклад", "DRAFT", false, "SALES", "2026-11-05", null)
            val result =
                AgentTurn(FakeLlm("""{"intent":"LIST_CAMPAIGNS","patch":{}}"""))
                    .handle(input(DraftCampaign(), "покажи кампании", campaigns = listOf(item)))
            assertEquals(
                listOf(item),
                result.reply.blocks
                    .filterIsInstance<UiBlock.CampaignList>()
                    .single()
                    .items,
            )
            assertNull(result.commit)
        }

    @Test
    fun unknownTagBecomesProposal() =
        runBlocking {
            val llm = FakeLlm("""{"intent":"CAMPAIGN_EDIT","patch":{"tagQuery":"Баня"}}""")
            val r = AgentTurn(llm).handle(input(readyDraft(), "повесь тег Баня"))
            assertEquals("TAG", r.draft.pendingProposal?.kind)
            val confirm = AgentTurn(llm).handle(input(r.draft, "да"))
            assertEquals(CommitAction.CREATE_TAG, confirm.commit?.action)
        }

    @Test
    fun designStageUsesToolsAndNeverCommits() =
        runBlocking {
            val llm =
                ToolLlm(
                    listOf(
                        LlmToolReply(
                            null,
                            listOf(ToolCall("1", "generate_banner", """{"brief":"акцент на тишину","bannerType":"STORY"}""")),
                        ),
                        LlmToolReply(null, listOf(ToolCall("2", "generate_image", """{"description":"тихий спа","style":"фото"}"""))),
                        LlmToolReply("Сделал сторис и поставил картинку в очередь. Сохраняем?", emptyList()),
                    ),
                )
            val images = FakeImages()
            val r = AgentTurn(llm, images).handle(input(readyDraft(), "сделай сторис потише и картинку"))
            assertNull(r.commit)
            val banner = r.draft.banner
            assertNotNull(banner)
            assertEquals("STORY", banner.bannerType)
            assertEquals("img-1", banner.imageId)
            assertEquals(1, images.calls)
            assertTrue(r.reply.text.contains("Сохраняем"))
            assertTrue(r.reply.blocks.any { it is UiBlock.CreativePreview })
        }

    @Test
    fun designStageAskUserShowsMenu() =
        runBlocking {
            val llm =
                ToolLlm(
                    listOf(
                        LlmToolReply(
                            null,
                            listOf(
                                ToolCall("1", "ask_user", """{"question":"Какой тон текста?","options":["Спокойный","Яркий","Деловой"]}"""),
                            ),
                        ),
                    ),
                )
            val r: TurnResult = AgentTurn(llm).handle(input(readyDraft(), "помоги с текстом"))
            val menu =
                r.reply.blocks
                    .filterIsInstance<UiBlock.QuickReplies>()
                    .single()
            assertEquals("ask", menu.field)
            assertEquals(listOf("Спокойный", "Яркий", "Деловой"), menu.options)
            assertTrue(r.reply.text.contains("Какой тон"))
        }

    @Test
    fun designStageFallsBackWhenToolsFail() =
        runBlocking {
            val r = AgentTurn(BrokenToolsLlm()).handle(input(readyDraft(), "сделай баннер"))
            assertTrue(r.reply.blocks.any { it is UiBlock.CreativePreview })
        }

    // ---------- Помощники ----------

    private val catalog = TestCatalog.full()

    private class FakeLlm(
        private val reply: String,
    ) : LlmClient {
        override suspend fun chat(
            system: String,
            user: String,
            temperature: Double,
            json: Boolean,
        ): String = if (system.contains("CAMPAIGN_EDIT")) reply else """[{"headline":"Тишина","body":"Спа рядом","cta":"Записаться"}]"""
    }

    private class ThrowingLlm : LlmClient {
        override suspend fun chat(
            system: String,
            user: String,
            temperature: Double,
            json: Boolean,
        ): String = error("модель недоступна")
    }

    private class ToolLlm(
        private val script: List<LlmToolReply>,
    ) : LlmClient {
        private var step = 0
        override val supportsTools = true

        override suspend fun chat(
            system: String,
            user: String,
            temperature: Double,
            json: Boolean,
        ): String =
            if (system.contains("CAMPAIGN_EDIT")) {
                """{"intent":"GENERATE_CREATIVE","patch":{}}"""
            } else {
                """[{"headline":"Тишина и пар","body":"Спа в Сочи ждёт вас","cta":"Записаться","image_prompt_en":"quiet spa"}]"""
            }

        override suspend fun chatWithTools(
            messages: List<LlmMessage>,
            tools: List<ToolSpec>,
            temperature: Double,
        ): LlmToolReply = script[minOf(step++, script.size - 1)]
    }

    private class BrokenToolsLlm : LlmClient {
        override val supportsTools = true

        override suspend fun chat(
            system: String,
            user: String,
            temperature: Double,
            json: Boolean,
        ): String =
            if (system.contains("CAMPAIGN_EDIT")) {
                """{"intent":"GENERATE_CREATIVE","patch":{}}"""
            } else {
                """[{"headline":"Тишина","body":"Спа рядом","cta":"Записаться"}]"""
            }

        override suspend fun chatWithTools(
            messages: List<LlmMessage>,
            tools: List<ToolSpec>,
            temperature: Double,
        ): LlmToolReply = error("tools require --jinja")
    }

    private class FakeImages : ImageRequests {
        var calls = 0

        override fun enqueue(
            prompt: String,
            width: Int,
            height: Int,
        ): ImageTicket {
            calls++
            return ImageTicket("img-$calls", "/api/images/img-$calls", "PENDING")
        }
    }

    private fun input(
        draft: DraftCampaign,
        text: String,
        campaigns: List<CampaignListItem> = emptyList(),
    ) = TurnInput(draft = draft, userText = text, catalog = catalog, today = TODAY, campaigns = campaigns)

    private fun readyDraft() =
        DraftCampaign(
            name = "Спа в Сочи",
            startDate = "2026-11-05",
            startTime = "10:00",
            topic = "LIFESTYLE",
            audienceType = "SEGMENTED",
            includedSegments = listOf("CITY_SLD"),
            placeIds = listOf(1),
            tagIds = listOf(2),
        )

    private companion object {
        const val TODAY = "2026-11-01"
    }
}
