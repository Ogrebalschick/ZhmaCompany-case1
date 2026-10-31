package ru.zhma.campaign.server.jobs

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import ru.zhma.campaign.ai.agent.AgentTurn
import ru.zhma.campaign.ai.model.TurnInput
import ru.zhma.campaign.server.db.DatabaseFactory
import ru.zhma.campaign.server.http.ApiException
import ru.zhma.campaign.server.http.Caller
import ru.zhma.campaign.server.service.applyCommit
import ru.zhma.campaign.server.service.appendMessage
import ru.zhma.campaign.server.service.failJob
import ru.zhma.campaign.server.service.finishJob
import ru.zhma.campaign.server.service.listCampaignItems
import ru.zhma.campaign.server.service.loadDraft
import ru.zhma.campaign.server.service.loadSnapshot
import ru.zhma.campaign.server.service.saveDraft
import ru.zhma.campaign.server.wiring.llmClient
import java.time.LocalDate
import java.time.ZoneOffset
import kotlin.time.Duration.Companion.seconds

class JobProcessor(private val scope: CoroutineScope) {
    fun start(jobId: String, sessionId: String, text: String, caller: Caller) {
        scope.launch {
            try {
                withTimeout(30.seconds) {
                    val loaded = DatabaseFactory.db {
                        appendMessage(sessionId, "user", text, null)
                        Loaded(loadDraft(sessionId), loadSnapshot(), listCampaignItems())
                    }
                    val today = LocalDate.now(ZoneOffset.UTC).toString()
                    val result = AgentTurn(llmClient()).handle(
                        TurnInput(
                            draft = loaded.draft,
                            userText = text,
                            catalog = loaded.catalog,
                            today = today,
                            campaigns = loaded.campaigns,
                        ),
                    )
                    DatabaseFactory.db {
                        val commit = result.commit
                        val draft = if (commit != null) {
                            applyCommit(result.draft, commit.action, caller)
                        } else {
                            result.draft
                        }
                        saveDraft(sessionId, draft)
                        appendMessage(sessionId, "assistant", result.reply.text, result.reply.blocks)
                        finishJob(jobId, result.reply)
                    }
                }
            } catch (timeout: TimeoutCancellationException) {
                markFailed(jobId, "Превышено время ожидания")
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                markFailed(jobId, shortMessage(error))
            }
        }
    }

    fun cancel() {
        scope.coroutineContext[kotlinx.coroutines.Job]?.cancel()
    }

    private suspend fun markFailed(jobId: String, message: String) {
        try {
            DatabaseFactory.db { failJob(jobId, message) }
        } catch (error: Throwable) {
            org.slf4j.LoggerFactory.getLogger(JobProcessor::class.java).error("job {} failed: {}", jobId, message, error)
        }
    }
}

private data class Loaded(
    val draft: ru.zhma.campaign.ai.model.DraftCampaign,
    val catalog: ru.zhma.campaign.ai.model.CatalogSnapshot,
    val campaigns: List<ru.zhma.campaign.ai.model.CampaignListItem>,
)

private fun shortMessage(error: Throwable): String {
    val text = when (error) {
        is ApiException -> error.errors.joinToString("; ") { it.message }
        else -> error.message ?: error::class.simpleName ?: "Ошибка"
    }
    return text.lineSequence().first().take(300)
}
