package ru.zhma.campaign.ai.agent

/**
 * Точная правка текста баннера: «заголовок «Тёплая зима»», «текст: …», «кнопка — Записаться».
 * Применяется кодом слово в слово, без модели — иначе модель переписывает текст по-своему.
 */
object BannerEdit {
    data class Fields(
        val title: String? = null,
        val text: String? = null,
        val cta: String? = null,
    ) {
        val isEmpty: Boolean
            get() = title == null && text == null && cta == null
    }

    private const val FIELD =
        "(заголов\\p{L}*|подзаголов\\p{L}*|основн\\p{L}*\\s+текст\\p{L}*|текст\\p{L}*|описани\\p{L}*|надпис\\p{L}*\\s+на\\s+кнопке|кнопк\\p{L}*|cta)"
    private const val TAIL = "(?:\\s+(?:баннера|сторис|креатива|на\\s+кнопке))?"
    private val quoted = Regex("""(?<!\p{L})$FIELD$TAIL\s*(?:на|в|:|—|–|-|=)?\s*[«"“„]([^«»"“”„]{1,500})[»"”“]""", RegexOption.IGNORE_CASE)
    private const val COLON_FIELD = "(заголов\\p{L}*|подзаголов\\p{L}*|текст\\p{L}*\\s+баннера|кнопк\\p{L}*)"
    private val colon = Regex("""(?<!\p{L})$COLON_FIELD$TAIL\s*[:=—–]\s*([^\n;]{1,500})""", RegexOption.IGNORE_CASE)

    fun parse(raw: String): Fields {
        var fields = Fields()
        val matches = quoted.findAll(raw).toList().ifEmpty { colon.findAll(raw).toList() }
        for (m in matches) {
            val value = m.groupValues[2].trim().trimEnd('.')
            if (value.isEmpty()) continue
            val key = m.groupValues[1].lowercase()
            fields =
                when {
                    key.startsWith("кноп") || key.startsWith("надпис") || key == "cta" -> fields.copy(cta = value)
                    key.startsWith("заголов") -> fields.copy(title = value)
                    else -> fields.copy(text = value)
                }
        }
        return fields
    }

    /** Есть ли в реплике правка баннера — чтобы текст в кавычках не принять за название кампании. */
    fun mentions(raw: String): Boolean = !parse(raw).isEmpty
}
