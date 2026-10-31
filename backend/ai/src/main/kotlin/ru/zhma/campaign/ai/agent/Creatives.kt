package ru.zhma.campaign.ai.agent

import kotlinx.coroutines.CancellationException
import ru.zhma.campaign.ai.image.ImageRequests
import ru.zhma.campaign.ai.llm.StructuredLlm
import ru.zhma.campaign.ai.model.BannerDraft
import ru.zhma.campaign.ai.model.CatalogSnapshot
import ru.zhma.campaign.ai.model.DraftCampaign
import ru.zhma.campaign.ai.model.TurnInput
import ru.zhma.campaign.ai.prompt.Prompts

data class CreativeResult(
    val draft: DraftCampaign,
    val problems: List<String> = emptyList(),
    val message: String? = null,
)

/** Баннер (текст от модели, проверка лимитов кодом) и картинка к нему (асинхронно через [ImageRequests]). */
class Creatives(
    private val structured: StructuredLlm,
    private val images: ImageRequests?,
) {
    val imagesEnabled: Boolean
        get() = images != null

    val compliance = Compliance(structured)

    suspend fun banner(
        input: TurnInput,
        draft: DraftCampaign,
        brief: String,
        bannerTypeHint: String? = null,
        channelHint: String? = null,
        linkHint: String? = null,
    ): CreativeResult {
        val catalog = input.catalog
        val bannerType = normalizeType(bannerTypeHint) ?: draft.banner?.bannerType ?: defaultType(draft, catalog)
        val channel = chooseChannel(channelHint ?: input.userText, bannerType, draft.banner?.channel)
        val link = linkHint ?: Regex("""https?://\S+""").find(input.userText)?.value ?: draft.banner?.link
        val actionType = if (link != null) "externalLink" else "default"
        var fallbackUsed = false
        val copy =
            try {
                structured
                    .ask(
                        system =
                            Prompts.bannerCopy(
                                product = draft.name ?: brief,
                                audience = DraftView.segmentNames(draft.includedSegments, catalog).ifBlank { "все пользователи" },
                                placement = DraftView.placeNames(draft.placeIds, catalog).ifBlank { "не выбрано" },
                                bannerType = bannerType,
                                brief = brief,
                                current = draft.banner?.let { "«${it.title.orEmpty()}» — ${it.text.orEmpty()} [${it.cta.orEmpty()}]" },
                            ),
                        user = brief,
                        serializer = BannerCopyCodec.Lenient,
                        validate = BannerCopyCodec::validateOne,
                        retries = 2,
                    ).let(BannerCopyCodec::tidy)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                fallbackUsed = true
                fallbackCopy(draft, brief)
            }
        val fresh = BannerCopyCodec.toDraft(copy, bannerType, channel, actionType, link)
        val old = draft.banner
        val banner =
            fresh.copy(
                imageId = old?.imageId,
                imageUrl = old?.imageUrl,
                imageStatus = old?.imageStatus,
                imagePrompt = fresh.imagePrompt ?: old?.imagePrompt,
            )
        val problems = bannerProblems(banner, catalog)
        if (problems.isNotEmpty()) return CreativeResult(draft, problems)
        val report = compliance.check(banner, draft.name)
        return CreativeResult(
            draft.copy(banner = banner.copy(compliance = report), creativeBrief = brief),
            message = if (fallbackUsed) FALLBACK_NOTE else null,
        )
    }

    suspend fun image(
        input: TurnInput,
        draft: DraftCampaign,
        brief: String,
        style: String? = null,
        withText: Boolean? = null,
    ): CreativeResult {
        val requests =
            images ?: return CreativeResult(draft, listOf("Генерация картинок не подключена: на сервере не задан IMAGE_BASE_URL."))
        var current = draft
        if (current.banner == null) {
            val made = banner(input, current, brief)
            if (made.problems.isNotEmpty()) return made
            current = made.draft
        }
        val banner = current.banner!!
        val wantsText = withText ?: Regex("""(?iu)надпис|с\s+текстом|с\s+заголовк""").containsMatchIn(input.userText)
        val details = listOfNotNull(banner.imagePrompt, style, brief.takeIf { it != input.userText || it.length < 200 }).joinToString(". ")
        val prompt = Prompts.imagePrompt(current.name ?: banner.title.orEmpty(), banner.title, details, banner.bannerType, wantsText)
        val (w, h) = if (banner.bannerType == "STORY") 720 to 1280 else 1280 to 720
        val ticket =
            try {
                requests.enqueue(prompt, w, h)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                return CreativeResult(current, listOf("Не удалось поставить картинку в очередь: ${e.message?.take(120)}"))
            }
        val next = banner.copy(imageId = ticket.id, imageUrl = ticket.url, imagePrompt = prompt, imageStatus = ticket.status)
        return CreativeResult(
            current.copy(banner = next),
            message = "Рисую картинку для баннера, через минуту-две она появится в превью. Пока можно продолжать.",
        )
    }

    /** Если модель не ответила: заголовок — название, текст — из пожелания менеджера, без выдумок. */
    private fun fallbackCopy(
        draft: DraftCampaign,
        brief: String,
    ): BannerCopy {
        val wish =
            brief
                .replace(
                    Regex(
                        """(?iu)^(сделай|создай|добавь|напиши|поменяй|перепиши)\s+(баннер|сторис|текст)?\s*(на\s+баннер)?\s*,?\s*(что)?\s*""",
                    ),
                    "",
                ).trim()
                .replaceFirstChar { it.uppercase() }
        return BannerCopyCodec.tidy(
            BannerCopy(
                headline = draft.name ?: "Новое предложение",
                body = wish.takeIf { it.length in 8..160 } ?: "Подробности и условия — в приложении.",
                cta = "Подробнее",
            ),
        )
    }

    private fun normalizeType(raw: String?): String? =
        when {
            raw == null -> null
            raw.contains("STORY", true) || raw.contains("сторис", true) -> "STORY"
            raw.contains("SMART", true) || raw.contains("баннер", true) -> "SMART_BANNER"
            else -> null
        }

    private fun defaultType(
        draft: DraftCampaign,
        catalog: CatalogSnapshot,
    ): String =
        draft.placeIds.firstNotNullOfOrNull { id -> catalog.places.find { it.id == id }?.defaultBanner }
            ?: "SMART_BANNER"

    private fun chooseChannel(
        text: String,
        bannerType: String,
        previous: String?,
    ): String {
        val lower = text.lowercase()
        val asked =
            when {
                Regex("""(?<!\p{L})(ios|айос|iphone|айфон)(?!\p{L})""").containsMatchIn(lower) -> "iOS"
                Regex("""(?<!\p{L})(android|андроид)(?!\p{L})""").containsMatchIn(lower) -> "ANDROID"
                Regex("""(?<!\p{L})(web|веб|сайт)(?!\p{L})""").containsMatchIn(lower) -> "WEB"
                else -> null
            }
        val channel = asked ?: previous ?: "ANDROID"
        return if (bannerType == "STORY" && channel == "WEB") "ANDROID" else channel
    }

    companion object {
        const val FALLBACK_NOTE = "Модель не ответила вовремя — подставил черновой текст из ваших слов, его можно поправить."

        fun bannerProblems(
            banner: BannerDraft,
            catalog: CatalogSnapshot,
        ): List<String> {
            val problems = mutableListOf<String>()
            val limits = catalog.bannerTypes.find { it.code == banner.bannerType }
            if (banner.bannerType == "STORY" && banner.channel == "WEB") problems += "Сторис доступна только в iOS и ANDROID."
            if (limits != null &&
                banner.channel !in limits.channels
            ) {
                problems += "Канал ${banner.channel} не подходит типу ${banner.bannerType}."
            }
            if (limits != null &&
                (banner.title?.length ?: 0) > limits.titleMax
            ) {
                problems += "Заголовок длиннее ${limits.titleMax} символов."
            }
            if (limits != null && (banner.text?.length ?: 0) > limits.textMax) problems += "Текст длиннее ${limits.textMax} символов."
            val needsLink = banner.actionType == "externalLink" || banner.actionType == "openSmartLink"
            if (needsLink && banner.link.isNullOrBlank()) problems += "Для этого действия нужна ссылка."
            return problems
        }
    }
}
