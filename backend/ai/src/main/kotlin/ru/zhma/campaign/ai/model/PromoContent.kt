package ru.zhma.campaign.ai.model

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** Промо-экран, который собирается из того же баннера и кладётся в content креатива. */
object PromoContent {
    const val TYPE = "PROMO"

    fun tree(banner: BannerDraft): JsonObject {
        val parts = mutableListOf<JsonObject>()
        banner.title.filled()?.let { parts += text(it) }
        banner.text.filled()?.let { parts += text(it) }
        banner.imageUrl.filled()?.let { url ->
            parts +=
                buildJsonObject {
                    put("role", "image")
                    put("url", url)
                }
        }
        banner.cta.filled()?.let { name ->
            parts +=
                buildJsonObject {
                    put("role", "button")
                    put("name", name)
                    put("action", action(banner))
                }
        }
        return buildJsonObject {
            put("role", "promo")
            put(
                "component",
                buildJsonObject {
                    put("role", "linearContainer")
                    put("axis", "v")
                    put("components", JsonArray(parts))
                },
            )
        }
    }

    private fun text(value: String): JsonObject =
        buildJsonObject {
            put("role", "text")
            put("value", value)
        }

    private fun action(banner: BannerDraft): JsonObject =
        buildJsonObject {
            put("actionType", banner.actionType)
            val link = banner.link.filled()
            if (link != null && banner.actionType in LINK_ACTIONS) put("link", link)
        }

    private fun String?.filled(): String? = this?.trim()?.takeIf { it.isNotEmpty() }

    private val LINK_ACTIONS = setOf("externalLink", "openSmartLink")
}
