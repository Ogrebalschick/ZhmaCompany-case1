package ru.zhma.campaign.ai.agent

import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import ru.zhma.campaign.ai.model.BannerDraft

@Serializable
data class BannerCopy(
    val headline: String = "",
    val body: String = "",
    val cta: String = "",
)

object BannerCopyCodec {
    val listSerializer = ListSerializer(BannerCopy.serializer())

    fun validate(copies: List<BannerCopy>): String? {
        val copy = copies.firstOrNull() ?: return "Пустой список вариантов"
        return when {
            copy.headline.isBlank() || copy.body.isBlank() || copy.cta.isBlank() -> "Пустые поля баннера"
            copy.headline.length > 40 -> "Заголовок длиннее 40 символов"
            copy.body.length > 90 -> "Текст длиннее 90 символов"
            copy.cta.length > 20 -> "Текст кнопки длиннее 20 символов"
            else -> null
        }
    }

    fun toDraft(
        copy: BannerCopy,
        bannerType: String,
        channel: String,
        actionType: String,
        link: String?,
    ): BannerDraft =
        BannerDraft(
            bannerType = bannerType,
            channel = channel,
            title = copy.headline,
            text = copy.body,
            actionType = actionType,
            link = link,
            cta = copy.cta,
        )
}
