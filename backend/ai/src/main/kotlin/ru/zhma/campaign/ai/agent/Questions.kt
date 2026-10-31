package ru.zhma.campaign.ai.agent

import ru.zhma.campaign.ai.model.CatalogSnapshot
import ru.zhma.campaign.ai.model.DraftCampaign
import ru.zhma.campaign.ai.model.ReplyOption
import ru.zhma.campaign.ai.model.UiBlock
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.TemporalAdjusters
import java.util.Locale

data class Question(
    val field: String,
    val text: String,
    val options: List<ReplyOption>,
    val multi: Boolean = false,
    val allowCustom: Boolean = true,
) {
    fun block(): UiBlock.QuickReplies =
        UiBlock.QuickReplies(
            options = options.map { it.value },
            field = field,
            question = text,
            multi = multi,
            allowCustom = allowCustom,
            items = options,
        )
}

/** Один вопрос за раз и варианты ответа из справочника. Тексты вопросов — шаблоны, без модели. */
object Questions {
    /** Порядок, в котором спрашиваем недостающие поля. */
    val ORDER = listOf("name", "topic", "audienceType", "segments", "places", "startDate", "startTime")

    val FIELD_LABELS =
        mapOf(
            "name" to "название",
            "startDate" to "дата старта",
            "startTime" to "время старта",
            "topic" to "тема",
            "audienceType" to "тип аудитории",
            "places" to "места показа",
            "segments" to "сегменты",
        )

    private val ru = Locale.forLanguageTag("ru")
    private val dayMonth = DateTimeFormatter.ofPattern("d MMMM", ru)
    private val numeric = DateTimeFormatter.ofPattern("dd.MM.yyyy")

    fun pick(missing: List<String>): String? = ORDER.firstOrNull { it in missing } ?: missing.firstOrNull()

    fun build(
        field: String,
        draft: DraftCampaign,
        catalog: CatalogSnapshot,
        today: String,
        suggestions: List<String>,
        repeat: Int,
    ): Question {
        val base =
            when (field) {
                "name" -> name(draft)
                "topic" -> topic(draft, catalog)
                "audienceType" ->
                    Question(
                        field,
                        "Кому показываем: всем пользователям или выбранным сегментам?",
                        listOf(
                            ReplyOption("Выбранным сегментам", "Сегмент пользователей", "город, пол, возраст, интересы"),
                            ReplyOption("Всем пользователям", "Все пользователи", "без сегментации"),
                        ),
                        allowCustom = false,
                    )
                "segments" -> segments(catalog, suggestions)
                "places" -> places(catalog, suggestions)
                "startDate" -> startDate(today)
                "startTime" ->
                    Question(
                        field,
                        "Во сколько запускаем?",
                        listOf("00:00", "09:00", "10:00", "12:00", "18:00").map { ReplyOption(it, "в $it") },
                    )
                else -> Question(field, "Уточните: ${FIELD_LABELS[field] ?: field}.", emptyList())
            }
        if (repeat < 2) return base
        val hint =
            when (field) {
                "name" -> "Напишите название одной фразой, например «Баня и спа в Сочи»."
                "startDate" -> "Напишите дату, например «10 ноября» или «10.11»."
                "startTime" -> "Напишите время, например «10:00»."
                "topic" -> "Выберите тему кнопкой."
                "segments" -> "Выберите сегменты кнопками или назовите город, пол, возраст."
                "places" -> "Выберите место кнопкой."
                else -> "Выберите вариант кнопкой."
            }
        return base.copy(text = "Не получилось разобрать ответ. ${base.text} $hint")
    }

    private fun name(draft: DraftCampaign): Question {
        val guess = draft.nameGuess?.takeIf { it.isNotBlank() }
        val text = if (guess != null) "Как назовём кампанию? Могу предложить «$guess»." else "Как назовём кампанию?"
        val options = listOfNotNull(guess?.let { ReplyOption(it, it, "предложение", recommended = true) })
        return Question("name", text, options)
    }

    private fun topic(
        draft: DraftCampaign,
        catalog: CatalogSnapshot,
    ): Question {
        val guess = catalog.topics.find { it.value == draft.topicGuess }
        val text =
            if (guess != null) {
                "Какая тема у кампании? По описанию похоже на «${guess.label}»."
            } else {
                "Какая тема у кампании?"
            }
        val ordered = listOfNotNull(guess) + catalog.topics.filter { it != guess }
        val options =
            ordered.map { t ->
                ReplyOption(
                    label = t.label,
                    value = t.label,
                    hint = TOPIC_HINTS[t.value],
                    recommended = t == guess,
                )
            }
        return Question("topic", text, options, allowCustom = false)
    }

    private fun segments(
        catalog: CatalogSnapshot,
        suggestions: List<String>,
    ): Question {
        val active = catalog.segments.filter { it.state == "ACTIVE" && !it.code.startsWith("EXC_") }
        val suggested = suggestions.mapNotNull { s -> active.find { it.name == s || it.code == s } }
        val defaults = active.filter { it.type in setOf("GEO_CITY", "GENDER", "AGE") }
        val list = (suggested + defaults).distinct().take(14)
        val options =
            list.map { seg ->
                ReplyOption(seg.name, seg.name, "${typeLabel(seg.type)}, ${humanCount(seg.count)}", recommended = seg in suggested)
            }
        return Question("segments", "Каким сегментам показываем? Можно выбрать несколько.", options, multi = true)
    }

    private fun places(
        catalog: CatalogSnapshot,
        suggestions: List<String>,
    ): Question {
        val active = catalog.places.filter { it.state == "ACTIVE" }
        val options =
            active.map { p ->
                ReplyOption(
                    p.description,
                    p.description,
                    p.defaultBanner?.let { if (it == "STORY") "сторис" else "смарт-баннер" },
                    recommended = p.description in suggestions,
                )
            }
        return Question("places", "Где показываем кампанию? Можно выбрать несколько мест.", options, multi = true)
    }

    private fun startDate(todayIso: String): Question {
        val today = LocalDate.parse(todayIso)
        val monday = today.with(TemporalAdjusters.next(DayOfWeek.MONDAY))
        val firstOfMonth = today.with(TemporalAdjusters.firstDayOfNextMonth())
        val options =
            listOf(
                ReplyOption("Сегодня, ${today.format(dayMonth)}", "с ${today.format(numeric)}"),
                ReplyOption("Завтра, ${today.plusDays(1).format(dayMonth)}", "с ${today.plusDays(1).format(numeric)}"),
                ReplyOption(
                    "${monday.dayOfWeek.getDisplayName(
                        TextStyle.SHORT,
                        ru,
                    ).replaceFirstChar { it.uppercase() }}, ${monday.format(dayMonth)}",
                    "с ${monday.format(numeric)}",
                ),
                ReplyOption(firstOfMonth.format(dayMonth), "с ${firstOfMonth.format(numeric)}"),
            ).distinctBy { it.value }
        return Question("startDate", "С какой даты запускаем?", options)
    }

    /** Тип сегмента по-человечески — коды справочника на экран не выводим. */
    fun typeLabel(type: String): String =
        when (type) {
            "GEO_CITY" -> "город"
            "GEO_REGION" -> "регион"
            "AGE" -> "возраст"
            "GENDER" -> "пол"
            "INTEREST" -> "интересы"
            "PRODUCT" -> "клиенты продукта"
            "CHANNEL" -> "канал"
            else -> "сегмент"
        }

    fun humanCount(count: Long): String =
        when {
            count >= 1_000_000 -> "%.1f млн".format(Locale.US, count / 1_000_000.0).replace(".0 ", " ")
            count >= 1_000 -> "${count / 1_000} тыс."
            else -> count.toString()
        }

    val TOPIC_HINTS =
        mapOf(
            "SALES" to "продажи, акции продуктов",
            "EVENT" to "событие, мероприятие",
            "LIFESTYLE" to "отдых, досуг, образ жизни",
            "RECOMMENDATION" to "рекомендации",
            "ADVICES" to "советы",
            "COURSES" to "обучение",
            "FINANCIAL_TIPS" to "финансовые советы",
            "RETARGET" to "повторный контакт",
            "HINTS" to "подсказки по приложению",
            "EXTRA" to "дополнительно",
            "OTHER" to "другое",
        )
}
