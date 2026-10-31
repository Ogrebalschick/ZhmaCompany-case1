package ru.zhma.campaign.ai.agent

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import ru.zhma.campaign.ai.model.BannerDraft

@Serializable
data class BannerCopy(
    val headline: String = "",
    val body: String = "",
    val cta: String = "",
    @SerialName("image_prompt_en")
    val imagePrompt: String = "",
)

object BannerCopyCodec {
    val listSerializer = ListSerializer(BannerCopy.serializer())

    /**
     * Принимает любой разумный ответ модели: объект с полями, массив объектов или обёртку `{"items":[...]}`.
     * llama.cpp с `response_format: json_object` возвращает только объект — поэтому массив не обязателен.
     */
    object Lenient : kotlinx.serialization.KSerializer<BannerCopy> {
        override val descriptor =
            kotlinx.serialization.json.JsonElement
                .serializer()
                .descriptor

        override fun serialize(
            encoder: kotlinx.serialization.encoding.Encoder,
            value: BannerCopy,
        ) = BannerCopy.serializer().serialize(encoder, value)

        override fun deserialize(decoder: kotlinx.serialization.encoding.Decoder): BannerCopy {
            val json = decoder as kotlinx.serialization.json.JsonDecoder
            val element = json.decodeJsonElement()
            val obj = find(element) ?: throw kotlinx.serialization.SerializationException("Нет объекта с headline")
            return json.json.decodeFromJsonElement(BannerCopy.serializer(), obj)
        }

        private fun find(element: kotlinx.serialization.json.JsonElement): kotlinx.serialization.json.JsonObject? =
            when (element) {
                is kotlinx.serialization.json.JsonObject ->
                    if ("headline" in element || "title" in element) {
                        element
                    } else {
                        element.values.firstNotNullOfOrNull { find(it) }
                    }
                is kotlinx.serialization.json.JsonArray -> element.firstNotNullOfOrNull { find(it) }
                else -> null
            }
    }

    /** Чистит текст: без восклицательных знаков, в пределах лимитов, по границе слова. */
    fun tidy(copy: BannerCopy): BannerCopy =
        copy.copy(
            headline = cut(copy.headline, 60),
            body = cut(copy.body, 160),
            cta = cut(copy.cta, 25),
        )

    private fun cut(
        value: String,
        max: Int,
    ): String {
        val clean = value.replace("!", "").trim()
        if (clean.length <= max) return clean
        return clean
            .take(max)
            .substringBeforeLast(' ')
            .trimEnd(',', ' ', '—', '-')
            .ifBlank { clean.take(max) }
    }

    fun validateOne(copy: BannerCopy): String? =
        if (copy.headline.isBlank() || copy.body.isBlank() || copy.cta.isBlank()) "Пустые поля баннера: нужны headline, body, cta" else null

    fun validate(copies: List<BannerCopy>): String? {
        val copy = copies.firstOrNull() ?: return "Пустой список вариантов"
        return when {
            copy.headline.isBlank() || copy.body.isBlank() || copy.cta.isBlank() -> "Пустые поля баннера"
            copy.headline.length > 60 -> "Заголовок длиннее 60 символов"
            copy.body.length > 140 -> "Текст длиннее 140 символов"
            copy.cta.length > 25 -> "Текст кнопки длиннее 25 символов"
            copy.headline.contains('!') || copy.body.contains('!') -> "Восклицательные знаки запрещены"
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
            title = copy.headline.trim(),
            text = copy.body.trim(),
            actionType = actionType,
            link = link,
            cta = copy.cta.trim(),
            imagePrompt = copy.imagePrompt.trim().ifBlank { null },
        )
}
