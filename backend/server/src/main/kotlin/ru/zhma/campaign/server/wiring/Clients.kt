package ru.zhma.campaign.server.wiring

import ru.zhma.campaign.ai.llm.LlmClient
import ru.zhma.campaign.ai.llm.MockLlmClient

fun llmClient(): LlmClient = MockLlmClient()
