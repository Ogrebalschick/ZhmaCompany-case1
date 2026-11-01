package ru.zhma.campaign.ai.agent

import ru.zhma.campaign.ai.model.AssistantReply
import ru.zhma.campaign.ai.model.CampaignListItem
import ru.zhma.campaign.ai.model.CommitAction
import ru.zhma.campaign.ai.model.CommitRequest
import ru.zhma.campaign.ai.model.ReplyOption
import ru.zhma.campaign.ai.model.TurnInput
import ru.zhma.campaign.ai.model.TurnResult
import ru.zhma.campaign.ai.model.UiBlock

/**
 * Чат модератора: показать кампании на модерации, одобрить, отклонить с комментарием.
 * Команда модератора сама по себе и есть явное подтверждение. Без комментария отклонить нельзя.
 */
object ModeratorTurn {
    private val approve = Regex("""(?<!\p{L})(одобр\p{L}*|утверд\p{L}*|approve)(?:\s+кампани\p{L}*)?\s*(?:№|#|n)?\s*(\d+)?""")
    private val decline =
        Regex(
            """(?<!\p{L})(отклон\p{L}*|верн\p{L}*(?:\s+\p{L}+)?\s+на\s+доработку|на\s+доработку|decline)(?:\s+кампани\p{L}*)?\s*(?:№|#|n)?\s*(\d+)?\s*[:,.—–-]?\s*(.*)""",
            RegexOption.DOT_MATCHES_ALL,
        )

    fun handle(input: TurnInput): TurnResult {
        val raw = input.userText.trim()
        val lower = raw.lowercase().replace('ё', 'е')
        val waiting = input.campaigns.filter { it.state == "ON_MODERATION" }

        decline.find(lower)?.let { m ->
            val id = m.groupValues[2].toLongOrNull() ?: waiting.singleOrNull()?.id
            val comment = raw.substring(m.groups[3]!!.range).trim()
            val campaign = id?.let { i -> input.campaigns.find { it.id == i } }
            return when {
                campaign == null -> listReply(input, waiting, "Уточните номер кампании, например: «отклони 5: нет даты окончания».")
                campaign.state != "ON_MODERATION" ->
                    listReply(
                        input,
                        waiting,
                        "Кампания № ${campaign.id} сейчас не на модерации (${stateLabel(campaign.state)}).",
                    )
                comment.length < 3 ->
                    reply(
                        input,
                        "Напишите причину отклонения, она уйдёт менеджеру. Например: «отклони ${campaign.id}: добавьте картинку к баннеру».",
                    )
                else ->
                    TurnResult(
                        AssistantReply("Возвращаю кампанию «${campaign.name}» на доработку с комментарием: «$comment».", emptyList()),
                        input.draft,
                        CommitRequest(CommitAction.DECLINE, campaign.id, comment),
                    )
            }
        }
        approve.find(lower)?.let { m ->
            val id = m.groupValues[2].toLongOrNull() ?: waiting.singleOrNull()?.id
            val campaign = id?.let { i -> input.campaigns.find { it.id == i } }
            return when {
                campaign == null -> listReply(input, waiting, "Уточните номер кампании, например: «одобри 5».")
                campaign.state != "ON_MODERATION" ->
                    listReply(
                        input,
                        waiting,
                        "Кампания № ${campaign.id} сейчас не на модерации (${stateLabel(campaign.state)}).",
                    )
                else ->
                    TurnResult(
                        AssistantReply("Одобряю кампанию «${campaign.name}».", emptyList()),
                        input.draft,
                        CommitRequest(CommitAction.APPROVE, campaign.id),
                    )
            }
        }
        val head =
            if (waiting.isEmpty()) {
                "Вы в роли модератора. Сейчас на модерации ничего нет."
            } else {
                "Вы в роли модератора. На модерации: ${waiting.size}. Одобрить — «одобри N», вернуть — «отклони N: причина»."
            }
        return listReply(input, waiting, head)
    }

    private fun listReply(
        input: TurnInput,
        waiting: List<CampaignListItem>,
        text: String,
    ): TurnResult {
        val blocks = mutableListOf<UiBlock>()
        if (waiting.isNotEmpty()) blocks += UiBlock.CampaignList(waiting)
        val items = waiting.take(6).map { ReplyOption("Одобрить № ${it.id}", "одобри ${it.id}", it.name) }
        if (items.isNotEmpty()) blocks += UiBlock.QuickReplies(items.map { it.value }, field = "moderation", items = items)
        return TurnResult(AssistantReply(text, blocks), input.draft)
    }

    private fun reply(
        input: TurnInput,
        text: String,
    ) = TurnResult(AssistantReply(text, emptyList()), input.draft)

    fun stateLabel(state: String): String =
        when (state) {
            "DRAFT" -> "черновик"
            "ON_MODERATION" -> "на модерации"
            "NEEDS_WORK" -> "на доработке"
            "APPROVED" -> "одобрена"
            "ACTIVE" -> "активна"
            "ACTIVE_EDITING" -> "активна, правится"
            "STOPPED" -> "остановлена"
            else -> state
        }
}
