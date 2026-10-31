package ru.zhma.campaign.ai.agent

import ru.zhma.campaign.ai.llm.LlmClient
import ru.zhma.campaign.ai.model.AssistantReply
import ru.zhma.campaign.ai.model.TurnInput
import ru.zhma.campaign.ai.model.TurnResult

class AgentTurn(private val llm: LlmClient) {
    suspend fun handle(input: TurnInput): TurnResult {
        val text = llm.chat(system = "", user = input.userText, temperature = 0.2, json = false)
        return TurnResult(reply = AssistantReply(text = text, blocks = emptyList()), draft = input.draft)
    }
}
