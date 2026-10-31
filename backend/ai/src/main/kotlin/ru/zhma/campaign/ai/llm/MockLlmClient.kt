package ru.zhma.campaign.ai.llm

class MockLlmClient : LlmClient {
    override suspend fun chat(
        system: String,
        user: String,
        temperature: Double,
        json: Boolean,
    ): String {
        if (json && looksLikeIntent(system)) {
            return mockIntent(user)
        }
        if (json && looksLikeBannerCopy(system)) {
            return mockBannerCopy(user)
        }
        return mockPhrase(user)
    }

    private fun looksLikeIntent(system: String): Boolean = system.contains("intent") && system.contains("CAMPAIGN_EDIT")

    private fun looksLikeBannerCopy(system: String): Boolean =
        system.contains("headline") && system.contains("body") && system.contains("cta")

    private fun mockIntent(user: String): String {
        val lower = user.lowercase()

        if (lower.contains("список") || lower.contains("покажи кампании")) {
            return """{"intent":"LIST_CAMPAIGNS","patch":{}}"""
        }

        val trimmed = user.trim().lowercase()
        if (trimmed == "подтверждаю" || trimmed == "сохрани" || trimmed == "да") {
            return """{"intent":"CONFIRM","patch":{},"confirm":true}"""
        }

        if (lower.contains("отмена") || lower.contains("не надо")) {
            return """{"intent":"CANCEL","patch":{},"confirm":false}"""
        }

        if (lower.contains("баннер") || lower.contains("креатив") || lower.contains("сторис")) {
            val brief = user.replace("\"", "\\\"")
            return """{"intent":"GENERATE_CREATIVE","patch":{"creativeBrief":"$brief"}}"""
        }

        // CAMPAIGN_EDIT
        val patchFields = mutableListOf<String>()
        Regex("«([^»]+)»").find(user)?.let { patchFields.add("\"name\":\"${it.groupValues[1].trim()}\"") }

        val segments = mutableListOf<String>()
        if (lower.contains("москв")) segments.add("CITY_MSK")
        if (lower.contains("санкт-петербург") || lower.contains("спб")) segments.add("CITY_SPB")
        if ((lower.contains("26") && lower.contains("45"))) segments.add("AGE_26_45")
        if (segments.isNotEmpty()) {
            patchFields.add("\"includedSegments\":[${segments.joinToString(",") { "\"$it\"" }}]")
        }

        if (lower.contains("все пользователи")) patchFields.add("\"audienceType\":\"ALL_USERS\"")
        if (lower.contains("вклад") && (lower.contains("акция") || lower.contains("продаж"))) {
            patchFields.add("\"topic\":\"SALES\"")
        }
        if (lower.contains("главн")) patchFields.add("\"placeIds\":[1]")
        Regex("\\d{4}-\\d{2}-\\d{2}").find(user)?.let { patchFields.add("\"startDate\":\"${it.value}\"") }
        Regex("\\b(\\d{2}:\\d{2})\\b").find(user)?.let { patchFields.add("\"startTime\":\"${it.groupValues[1]}\"") }
        Regex("(?:вес|weight)\\s*[:=]?\\s*(\\d+\\.?\\d*)", RegexOption.IGNORE_CASE).find(user)?.let {
            patchFields.add("\"weight\":${it.groupValues[1]}")
        }

        val patchJson = if (patchFields.isEmpty()) "{}" else "{${patchFields.joinToString(",")}}"
        return """{"intent":"CAMPAIGN_EDIT","patch":$patchJson}"""
    }

    private fun mockBannerCopy(user: String): String {
        val headline = "Ваше предложение"
        val body = "Узнайте больше о продукте и узнайте условия"
        val cta = "Подробнее"
        val imagePrompt = "clean product illustration, soft colors, minimal style"
        return """[{"headline":"$headline","body":"$body","cta":"$cta","image_prompt_en":"$imagePrompt"}]"""
    }

    private fun mockPhrase(user: String): String = "Принято. Готов продолжить работу над кампанией."
}
