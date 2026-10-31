package ru.zhma.campaign.ai.agent

import ru.zhma.campaign.ai.llm.StructuredLlm
import ru.zhma.campaign.ai.model.DraftCampaign
import ru.zhma.campaign.ai.model.Intents
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
    ): ParsedIntent {
        val parsed =
            llm.ask(
                system = Prompts.intent(today, catalogText, DraftView.compactJson(draft), draft.awaiting),
                user = text,
                serializer = ParsedIntent.serializer(),
                validate = { parsed ->
                    if (parsed.intent.uppercase() in Intents.ALL) null else "Неизвестный intent ${parsed.intent}"
                },
            )
        return parsed.copy(intent = parsed.intent.uppercase())
    }
}
