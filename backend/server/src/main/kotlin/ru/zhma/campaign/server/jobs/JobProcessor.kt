package ru.zhma.campaign.server.jobs

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import org.slf4j.LoggerFactory
import ru.zhma.campaign.ai.agent.AgentTurn
import ru.zhma.campaign.ai.agent.CommitReplies
import ru.zhma.campaign.ai.image.ImageRequests
import ru.zhma.campaign.ai.model.AssistantReply
import ru.zhma.campaign.ai.model.CampaignListItem
import ru.zhma.campaign.ai.model.CatalogSnapshot
import ru.zhma.campaign.ai.model.DraftCampaign
import ru.zhma.campaign.ai.model.HistoryItem
import ru.zhma.campaign.ai.model.TurnInput
import ru.zhma.campaign.server.db.DatabaseFactory
import ru.zhma.campaign.server.http.ApiException
import ru.zhma.campaign.server.http.Caller
import ru.zhma.campaign.server.service.appendMessage
import ru.zhma.campaign.server.service.applyCommit
import ru.zhma.campaign.server.service.auditText
import ru.zhma.campaign.server.service.failJob
import ru.zhma.campaign.server.service.finishJob
import ru.zhma.campaign.server.service.listCampaignItems
import ru.zhma.campaign.server.service.loadDraft
import ru.zhma.campaign.server.service.loadHistory
import ru.zhma.campaign.server.service.loadSnapshot
import ru.zhma.campaign.server.service.saveDraft
import ru.zhma.campaign.server.wiring.AppConfig
import ru.zhma.campaign.server.wiring.llmClient
import java.time.LocalDate
import java.time.ZoneId
import kotlin.time.Duration.Companion.seconds

class JobProcessor(
    private val scope: CoroutineScope,
    private val images: ImageRequests? = null,
) {
    private val log = LoggerFactory.getLogger(JobProcessor::class.java)

    fun start(
        jobId: String,
        sessionId: String,
        text: String,
        caller: Caller,
    ) {
        scope.launch {
            try {
                withTimeout(TURN_TIMEOUT) {
                    val loaded =
                        DatabaseFactory.db {
                            val history = loadHistory(sessionId, HISTORY)
                            appendMessage(sessionId, "user", text, null)
                            Loaded(loadDraft(sessionId), loadSnapshot(), listCampaignItems(), history)
                        }
                    val today = LocalDate.now(ZONE).toString()
                    val result =
                        AgentTurn(llmClient(), images).handle(
                            TurnInput(
                                draft = loaded.draft,
                                userText = text,
                                catalog = loaded.catalog,
                                today = today,
                                campaigns = loaded.campaigns,
                                history = loaded.history,
                                role = caller.role,
                            ),
                        )
                    val commit = result.commit
                    var draft = result.draft
                    var reply = result.reply
                    if (commit != null) {
                        try {
                            draft =
                                DatabaseFactory.db { applyCommit(result.draft, commit.action, caller, commit.campaignId, commit.comment) }
                            val (catalog, campaigns) = DatabaseFactory.db { loadSnapshot() to listCampaignItems() }
                            val campaign = (commit.campaignId ?: draft.savedCampaignId)?.let { id -> campaigns.find { it.id == id } }
                            if (campaign != null && draft.savedCampaignId == campaign.id) draft = draft.copy(knownState = campaign.state)
                            reply =
                                if (commit.action == "SHOW_AUDIT") {
                                    AssistantReply(DatabaseFactory.db { auditText(commit.campaignId!!) }, emptyList())
                                } else {
                                    CommitReplies.done(commit.action, draft, catalog, today, campaign)
                                }
                        } catch (e: ApiException) {
                            reply = CommitReplies.failed(commit.action, result.draft, e.errors.map { it.message }, loaded.catalog, today)
                        }
                    }
                    DatabaseFactory.db {
                        saveDraft(sessionId, draft)
                        appendMessage(sessionId, "assistant", reply.text, reply.blocks)
                        finishJob(jobId, reply)
                    }
                }
            } catch (timeout: TimeoutCancellationException) {
                markFailed(jobId, "Модель не ответила вовремя. Попробуйте ещё раз или переформулируйте.")
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                log.error("job {} failed", jobId, error)
                markFailed(jobId, shortMessage(error))
            }
        }
    }

    fun cancel() {
        scope.coroutineContext[Job]?.cancel()
    }

    private suspend fun markFailed(
        jobId: String,
        message: String,
    ) {
        try {
            DatabaseFactory.db { failJob(jobId, message) }
        } catch (error: Throwable) {
            log.error("job {} failed: {}", jobId, message, error)
        }
    }

    private companion object {
        val TURN_TIMEOUT = (AppConfig.get("TURN_TIMEOUT_SEC")?.toLongOrNull() ?: 300L).seconds
        val ZONE: ZoneId = runCatching { ZoneId.of(AppConfig.get("APP_TZ") ?: "Europe/Moscow") }.getOrDefault(ZoneId.of("UTC"))
        const val HISTORY = 12
    }
}

private data class Loaded(
    val draft: DraftCampaign,
    val catalog: CatalogSnapshot,
    val campaigns: List<CampaignListItem>,
    val history: List<HistoryItem>,
)

private fun shortMessage(error: Throwable): String {
    val text =
        when (error) {
            is ApiException -> error.errors.joinToString("; ") { it.message }
            else -> error.message ?: error::class.simpleName ?: "Ошибка"
        }
    return text.lineSequence().first().take(300)
}
