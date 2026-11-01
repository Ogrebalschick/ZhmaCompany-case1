package ru.zhma.campaign.ai.model

import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals

class PromoContentTest {
    @Test
    fun buildsVerticalPromoFromBanner() {
        val tree =
            PromoContent.tree(
                BannerDraft(
                    bannerType = "SMART_BANNER",
                    channel = "ANDROID",
                    title = "Вклад",
                    text = "Условия в приложении",
                    actionType = "externalLink",
                    link = "https://bank.example/deposit",
                    cta = "Подробнее",
                    imageUrl = "/api/images/abc",
                ),
            )
        assertEquals("promo", tree["role"]!!.jsonPrimitive.content)
        val column = tree["component"]!!.jsonObject
        assertEquals("linearContainer", column["role"]!!.jsonPrimitive.content)
        assertEquals("v", column["axis"]!!.jsonPrimitive.content)
        val parts = column["components"]!!.jsonArray.map { it.jsonObject }
        assertEquals(listOf("text", "text", "image", "button"), parts.map { it["role"]!!.jsonPrimitive.content })
        assertEquals("Вклад", parts[0]["value"]!!.jsonPrimitive.content)
        assertEquals("/api/images/abc", parts[2]["url"]!!.jsonPrimitive.content)
        val action = parts[3]["action"]!!.jsonObject
        assertEquals("externalLink", action["actionType"]!!.jsonPrimitive.content)
        assertEquals("https://bank.example/deposit", action["link"]!!.jsonPrimitive.content)
    }

    @Test
    fun skipsEmptyFieldsAndForbiddenLink() {
        val tree =
            PromoContent.tree(
                BannerDraft(
                    bannerType = "STORY",
                    channel = "iOS",
                    title = "  ",
                    text = "Только текст",
                    actionType = "closeCampaign",
                    link = "https://should-not-appear",
                    cta = "Закрыть",
                ),
            )
        val parts = tree["component"]!!.jsonObject["components"]!!.jsonArray.map { it.jsonObject }
        assertEquals(listOf("text", "button"), parts.map { it["role"]!!.jsonPrimitive.content })
        assertEquals(null, parts[1]["action"]!!.jsonObject["link"])
    }
}
