package ru.zhma.campaign.ai.llm

import kotlinx.serialization.json.JsonPrimitive

/**
 * Модель-заглушка без сети. Разбирает реплику простыми правилами, чтобы демо работало офлайн.
 * Основной разбор дат, справочника и кнопок всё равно делает код агента, а не эта заглушка.
 */
class MockLlmClient : LlmClient {
    override suspend fun chat(
        system: String,
        user: String,
        temperature: Double,
        json: Boolean,
    ): String {
        if (json && system.contains("CAMPAIGN_EDIT")) return mockIntent(user, system)
        if (json && system.contains("headline")) return mockBannerCopy(user)
        return mockPhrase(user)
    }

    private fun mockIntent(
        userRaw: String,
        system: String,
    ): String {
        val user = userRaw.substringBefore("\n\n(Предыдущий")
        val lower = user.lowercase()
        val trimmed = lower.trim().trimEnd('.', '!')

        if (Regex("""список|покажи кампании|мои кампании""").containsMatchIn(lower)) return intent("LIST_CAMPAIGNS")
        if (trimmed in setOf("подтверждаю", "сохрани", "да", "сохранить")) return """{"intent":"CONFIRM","patch":{},"confirm":true}"""
        if (trimmed in setOf("отмена", "нет", "не надо")) return intent("CANCEL")
        if (lower.contains("модерац")) return intent("TO_MODERATION")
        if (Regex("""картинк|изображен|иллюстрац""").containsMatchIn(lower)) return intent("GENERATE_IMAGE", "\"creativeBrief\":${q(user)}")
        if (Regex("""(сделай|создай|придумай|другой|переделай)\s.*(баннер|креатив|текст|вариант|сторис)""").containsMatchIn(lower)) {
            return intent("GENERATE_CREATIVE", "\"creativeBrief\":${q(user)}")
        }
        if (user.trim().endsWith("?")) return intent("QUESTION")

        val fields = mutableListOf<String>()
        val awaitingName = system.contains("спросил про поле: name")
        val hasName = Regex(""""name"\s*:""").containsMatchIn(system.substringAfter("Текущий черновик:").substringBefore("Каталог"))
        Regex("""«([^»]+)»""").find(user)?.let { fields += "\"name\":${q(it.groupValues[1].trim())}" }
        if (fields.isEmpty() && !hasName && !awaitingName) {
            Regex("""(?iu)кампани\p{L}*\s+(?:на|для|про|по)\s+(.+?)(?:\s+для\s+|,|$)""").find(user)?.let { m ->
                val name =
                    m.groupValues[1]
                        .trim()
                        .replaceFirstChar { it.uppercase() }
                        .take(60)
                if (name.length >= 3) fields += "\"name\":${q(name)}"
            }
        }
        topicGuess(lower)?.let { fields += "\"topicGuess\":\"$it\"" }
        if (lower.contains("вклад") && Regex("""акци|продаж|ставк""").containsMatchIn(lower)) fields += "\"topic\":\"SALES\""
        return intent("CAMPAIGN_EDIT", fields.joinToString(","))
    }

    private fun topicGuess(lower: String): String? =
        when {
            Regex("""бан[яи]|спа|отдых|путешеств|аквапарк|фитнес|ресторан""").containsMatchIn(lower) -> "LIFESTYLE"
            Regex("""вклад|кредит|карт[аыу]|скидк|кэшбэк|акци|ипотек""").containsMatchIn(lower) -> "SALES"
            Regex("""концерт|мероприят|событи|фестивал""").containsMatchIn(lower) -> "EVENT"
            Regex("""курс|обучен|вебинар""").containsMatchIn(lower) -> "COURSES"
            Regex("""совет""").containsMatchIn(lower) -> "ADVICES"
            else -> null
        }

    private fun intent(
        name: String,
        patch: String = "",
    ): String = """{"intent":"$name","patch":{$patch}}"""

    private fun q(value: String): String = JsonPrimitive(value).toString()

    private fun mockBannerCopy(user: String): String {
        val lower = user.lowercase()
        val headline =
            when {
                Regex("""бан[яи]|спа""").containsMatchIn(lower) -> "Тепло бани и спа в Сочи"
                lower.contains("вклад") -> "Вклад для ваших целей"
                else -> "Ваше новое предложение"
            }
        val body = "Выберите время и забронируйте отдых в приложении банка."
        val image = "cozy spa interior with warm light, wooden sauna, calm atmosphere"
        return """[{"headline":"$headline","body":"$body","cta":"Подробнее","image_prompt_en":"$image"}]"""
    }

    private fun mockPhrase(user: String): String =
        if (user.contains("?")) {
            "Работаю без подключённой модели, поэтому на вопросы отвечаю кратко. Продолжим заполнять кампанию."
        } else {
            "Принято. Продолжим работу над кампанией."
        }
}
