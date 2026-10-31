package ru.zhma.campaign.ai.agent

import kotlinx.coroutines.CancellationException
import kotlinx.serialization.Serializable
import ru.zhma.campaign.ai.llm.StructuredLlm
import ru.zhma.campaign.ai.model.BannerDraft
import ru.zhma.campaign.ai.model.ComplianceItem
import ru.zhma.campaign.ai.model.ComplianceReport
import ru.zhma.campaign.ai.prompt.Prompts

/**
 * Проверка рекламного текста на соответствие Федеральному закону № 38-ФЗ «О рекламе».
 *
 * Два слоя:
 * 1. Правила в коде — быстрые и объяснимые: превосходные степени, гарантии, ставки без условий,
 *    «бесплатно», медицинские услуги, запрещённые категории, маркировка интернет-рекламы.
 * 2. Модель-юрист — дочитывает смысл: недостоверность, вводящие в заблуждение формулировки, этика.
 *
 * Итог — форма с пунктами «статья · статус · что исправить». Сохранение и модерацию она не блокирует:
 * решение остаётся за человеком, но модератор видит отчёт.
 */
class Compliance(
    private val structured: StructuredLlm?,
) {
    @Serializable
    private data class AiItem(
        val article: String = "",
        val status: String = "WARN",
        val message: String = "",
        val fix: String? = null,
        val quote: String? = null,
    )

    @Serializable
    private data class AiAnswer(
        val items: List<AiItem> = emptyList(),
    )

    suspend fun check(
        banner: BannerDraft,
        campaignName: String?,
        withAi: Boolean = true,
    ): ComplianceReport {
        val text = listOfNotNull(banner.title, banner.text, banner.cta).joinToString(" · ")
        val items = rules(text, campaignName).toMutableList()
        var aiChecked = false
        if (withAi && structured != null) {
            try {
                val answer =
                    structured.ask(
                        system = Prompts.compliance(),
                        user =
                            "Название кампании: ${campaignName ?: "—"}\nЗаголовок: ${banner.title.orEmpty()}\n" +
                                "Текст: ${banner.text.orEmpty()}\nКнопка: ${banner.cta.orEmpty()}",
                        serializer = AiAnswer.serializer(),
                        retries = 1,
                    )
                aiChecked = true
                answer.items
                    .filter { it.message.isNotBlank() && it.status.uppercase() in setOf("WARN", "FAIL") }
                    .take(5)
                    .forEach { ai ->
                        val article = ai.article.ifBlank { "38-ФЗ" }
                        val duplicate =
                            items.any {
                                it.status != "OK" &&
                                    it.article.substringBefore(
                                        " ч.",
                                    ) == article.substringBefore(" ч.")
                            }
                        if (!duplicate) {
                            items += ComplianceItem("ai", article, ai.status.uppercase(), ai.message, ai.fix, ai.quote, source = "ai")
                        }
                    }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                aiChecked = false
            }
        }
        val verdict =
            when {
                items.any { it.status == "FAIL" } -> "FAIL"
                items.any { it.status == "WARN" } -> "WARN"
                else -> "OK"
            }
        return ComplianceReport(verdict, items.sortedBy { ORDER.indexOf(it.status) }, aiChecked, text)
    }

    companion object {
        private val ORDER = listOf("FAIL", "WARN", "INFO", "OK")

        private fun rx(pattern: String) = Regex(pattern, setOf(RegexOption.IGNORE_CASE))

        private val superlative =
            rx(
                """(?<!\p{L})(лучш\p{L}*|самы[йеяхм]\p{L}*\s+(выгодн|низк|высок|надёжн|надежн|дешёв|дешев)\p{L}*|№\s*1|номер\s+один|единственн\p{L}*|лидер\p{L}*|непревзойд\p{L}*|первы[йе]\s+в\s+росси)""",
            )
        private val guarantee = rx("""(?<!\p{L})(гарант\p{L}*|100\s*%|без\s+риска|безрисков\p{L}*|точно\s+одобр\p{L}*|одобрим\s+всем)""")
        private val financial =
            rx(
                """(?<!\p{L})(вклад\p{L}*|кредит\p{L}*|ипотек\p{L}*|ставк\p{L}*|процент\p{L}*|доход\p{L}*|кэшб[эе]к\p{L}*|рассрочк\p{L}*|займ\p{L}*|карт[аыуе]\p{L}*|инвест\p{L}*)""",
            )
        private val rate = rx("""\d+[.,]?\d*\s*%|(?<!\p{L})ставк\p{L}*\s+(от|до)?\s*\d""")
        private val credit = rx("""(?<!\p{L})(кредит\p{L}*|ипотек\p{L}*|рассрочк\p{L}*|займ\p{L}*)""")
        private val free = rx("""(?<!\p{L})(бесплатн\p{L}*|даром|за\s+0\s*₽|0\s*руб\p{L}*)""")
        private val medical =
            rx(
                """(?<!\p{L})(лечен\p{L}*|лечебн\p{L}*|медицин\p{L}*|оздоров\p{L}*|массаж\p{L}*|терапи\p{L}*|процедур\p{L}*|клиник\p{L}*|врач\p{L}*|диагностик\p{L}*)""",
            )
        private val alcohol = rx("""(?<!\p{L})(алкогол\p{L}*|вин[оау](?!\p{L})|пив[оау](?!\p{L})|коньяк\p{L}*|шампанск\p{L}*|водк\p{L}*)""")
        private val tobacco = rx("""(?<!\p{L})(табак\p{L}*|сигарет\p{L}*|вейп\p{L}*|кальян\p{L}*|никотин\p{L}*)""")
        private val gambling = rx("""(?<!\p{L})(казино|букмекер\p{L}*|ставки\s+на\s+спорт|азартн\p{L}*|лотере\p{L}*)""")
        private val latinWord = Regex("""(?<![\p{L}@./])[A-Za-z]{3,}(?![\p{L}.@/])""")
        private val allowedLatin = setOf("ios", "android", "web", "sms", "pin", "qr", "nfc", "online", "vip")

        fun rules(
            text: String,
            campaignName: String?,
        ): List<ComplianceItem> {
            val out = mutableListOf<ComplianceItem>()
            val isFinancial = financial.containsMatchIn(text) || financial.containsMatchIn(campaignName.orEmpty())

            superlative.find(text)?.let {
                out +=
                    ComplianceItem(
                        "superlative",
                        "ст. 5 ч. 3 п. 1",
                        "WARN",
                        "Превосходная степень «${it.value}» без подтверждения считается некорректным сравнением с конкурентами.",
                        "Уберите сравнение или укажите, по какому критерию и по чьим данным это так.",
                        it.value,
                    )
            }
            guarantee.find(text)?.let {
                out +=
                    ComplianceItem(
                        "guarantee",
                        if (isFinancial) "ст. 28 ч. 2 п. 1" else "ст. 5 ч. 3 п. 2",
                        if (isFinancial) "FAIL" else "WARN",
                        if (isFinancial) {
                            "Реклама финансовых услуг не должна гарантировать результат или одобрение («${it.value}»)."
                        } else {
                            "Обещание «${it.value}» может быть признано недостоверным."
                        },
                        "Замените гарантию на нейтральную формулировку: «узнайте условия», «можно оформить онлайн».",
                        it.value,
                    )
            }
            if (rate.containsMatchIn(text)) {
                val isCredit = credit.containsMatchIn(text)
                out +=
                    ComplianceItem(
                        "rate",
                        if (isCredit) "ст. 28 ч. 3" else "ст. 28 ч. 2 п. 2",
                        "WARN",
                        if (isCredit) {
                            "Если в рекламе кредита есть ставка, нужно указать и остальные условия: полную стоимость кредита (ПСК), срок, сумму."
                        } else {
                            "Ставку нельзя показывать без условий, которые на неё влияют (срок, сумма, тип вклада)."
                        },
                        "Добавьте условия мелким текстом или уберите цифру из заголовка.",
                        rate.find(text)?.value,
                    )
            }
            if (isFinancial && !Regex("""(?i)банк""").containsMatchIn(text)) {
                out +=
                    ComplianceItem(
                        "advertiser",
                        "ст. 28 ч. 1",
                        "WARN",
                        "В рекламе банковских услуг должно быть наименование банка.",
                        "Добавьте наименование банка в текст или подпись баннера.",
                    )
            }
            free.find(text)?.let {
                out +=
                    ComplianceItem(
                        "free",
                        "ст. 5 ч. 3 п. 4",
                        "WARN",
                        "«${it.value}» — если есть условия (подписка, минимальная сумма), без них это недостоверная информация о цене.",
                        "Добавьте условие: «бесплатно при …».",
                        it.value,
                    )
            }
            medical.find(text)?.let {
                out +=
                    ComplianceItem(
                        "medical",
                        "ст. 24 ч. 7",
                        "WARN",
                        "Упоминание медицинских услуг («${it.value}») требует предупреждения о противопоказаниях.",
                        "Добавьте: «Имеются противопоказания. Необходима консультация специалиста» — не менее 5% площади баннера.",
                        it.value,
                    )
            }
            alcohol.find(text)?.let {
                out +=
                    ComplianceItem(
                        "alcohol",
                        "ст. 21",
                        "FAIL",
                        "Реклама алкоголя в интернете и приложениях запрещена («${it.value}»).",
                        "Уберите упоминание алкоголя.",
                        it.value,
                    )
            }
            tobacco.find(text)?.let {
                out +=
                    ComplianceItem(
                        "tobacco",
                        "ст. 7 п. 8",
                        "FAIL",
                        "Реклама табака и никотинсодержащей продукции запрещена («${it.value}»).",
                        "Уберите упоминание.",
                        it.value,
                    )
            }
            gambling.find(text)?.let {
                out +=
                    ComplianceItem(
                        "gambling",
                        "ст. 27",
                        "FAIL",
                        "Реклама азартных игр в приложении банка не допускается («${it.value}»).",
                        "Уберите упоминание.",
                        it.value,
                    )
            }
            val foreign =
                latinWord
                    .findAll(text)
                    .map { it.value }
                    .filter { it.lowercase() !in allowedLatin }
                    .distinct()
                    .toList()
            if (foreign.isNotEmpty()) {
                out +=
                    ComplianceItem(
                        "language",
                        "53-ФЗ «О государственном языке», ст. 5 ч. 11 38-ФЗ",
                        "WARN",
                        "Иностранные слова без перевода: ${foreign.joinToString(", ")}.",
                        "Замените на русские слова или добавьте перевод.",
                        foreign.first(),
                    )
            }
            out +=
                ComplianceItem(
                    "marking",
                    "ст. 18.1",
                    "INFO",
                    "Интернет-реклама маркируется: пометка «Реклама», рекламодатель и токен erid.",
                    "Пометка и erid добавляются при выгрузке в ОРД после модерации.",
                )
            if (out.none { it.status != "INFO" }) {
                out += ComplianceItem("clean", "38-ФЗ", "OK", "Явных нарушений в тексте не найдено.")
            }
            return out
        }
    }
}
