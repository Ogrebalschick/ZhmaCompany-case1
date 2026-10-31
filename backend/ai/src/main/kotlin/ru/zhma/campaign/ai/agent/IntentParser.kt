package ru.zhma.campaign.ai.agent

import ru.zhma.campaign.ai.llm.StructuredLlm
import ru.zhma.campaign.ai.model.AppJson
import ru.zhma.campaign.ai.model.DraftCampaign
import ru.zhma.campaign.ai.model.ParsedIntent
import ru.zhma.campaign.ai.prompt.Prompts

class IntentParser(
    private val llm: StructuredLlm,
) {
    suspend fun parse(
        text: String,
        draft: DraftCampaign,
        catalogText: String,
        today: String,
    ): ParsedIntent =
        llm.ask(
            system = Prompts.intent(today, catalogText, AppJson.encodeToString(DraftCampaign.serializer(), draft)),
            user = text,
            serializer = ParsedIntent.serializer(),
            validate = { parsed ->
                if (parsed.intent in KNOWN_INTENTS) null else "Неизвестный intent ${parsed.intent}"
            },
        )

    private companion object {
        val KNOWN_INTENTS =
            setOf(
                "CAMPAIGN_EDIT",
                "CONFIRM",
                "CANCEL",
                "GENERATE_CREATIVE",
                "LIST_CAMPAIGNS",
                "OTHER",
            )
    }
}
