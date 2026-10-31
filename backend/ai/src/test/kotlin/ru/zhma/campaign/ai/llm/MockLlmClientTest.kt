package ru.zhma.campaign.ai.llm

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class MockLlmClientTest {

    private val llm = MockLlmClient()
    private val json = Json { ignoreUnknownKeys = true }

    // System-строка, по которой мок понимает «разбор намерения».
    private val intentSystem =
        "Разбери намерение. Возможные intent: CAMPAIGN_EDIT, CONFIRM, CANCEL, GENERATE_CREATIVE, LIST_CAMPAIGNS, OTHER. Верни JSON."

    // Набор фраз для ревью — видно, что именно проверяется.
    private val PHRASES = listOf(
        "Настрой кампанию по вкладу в Москве для возраста 26-45 лет", // полный бриф: Москва + 26-45
        "покажи кампании",
        "подтверждаю",
        "отмена",
        "сделай сторис",
        "Назови кампанию «Тестовая»", // без дат
    )

    private fun patch(text: String): JsonObject =
        json.parseToJsonElement(text).jsonObject["patch"]?.jsonObject ?: JsonObject(emptyMap())

    private fun intent(text: String): String =
        json.parseToJsonElement(text).jsonObject["intent"]!!.jsonPrimitive.content

    @Test
    fun `full brief extracts Moscow and age segment`() = runBlocking {
        val out = llm.chat(intentSystem, PHRASES[0], 0.2, json = true)
        assertEquals("CAMPAIGN_EDIT", intent(out))
        val segs = patch(out)["includedSegments"]!!.jsonArray.map { it.jsonPrimitive.content }
        assertTrue(segs.contains("CITY_MSK"))
        assertTrue(segs.contains("AGE_26_45"))
        // Даты не выдуманы.
        assertFalse(patch(out).containsKey("startDate"))
        assertFalse(patch(out).containsKey("startTime"))
    }

    @Test
    fun `shows list campaigns`() = runBlocking {
        val out = llm.chat(intentSystem, PHRASES[1], 0.2, json = true)
        assertEquals("LIST_CAMPAIGNS", intent(out))
    }

    @Test
    fun `confirm`() = runBlocking {
        val out = llm.chat(intentSystem, PHRASES[2], 0.2, json = true)
        assertEquals("CONFIRM", intent(out))
        assertEquals(
            true,
            json.parseToJsonElement(out).jsonObject["confirm"]!!.jsonPrimitive.content == "true"
        )
    }

    @Test
    fun `cancel`() = runBlocking {
        val out = llm.chat(intentSystem, PHRASES[3], 0.2, json = true)
        assertEquals("CANCEL", intent(out))
        assertEquals(
            false,
            json.parseToJsonElement(out).jsonObject["confirm"]!!.jsonPrimitive.content == "true"
        )
    }

    @Test
    fun `generate creative keeps brief`() = runBlocking {
        val out = llm.chat(intentSystem, PHRASES[4], 0.2, json = true)
        assertEquals("GENERATE_CREATIVE", intent(out))
        val brief = patch(out)["creativeBrief"]!!.jsonPrimitive.content
        assertTrue(brief.contains("сторис"))
    }

    @Test
    fun `phrase without dates has no startDate and keeps name`() = runBlocking {
        val out = llm.chat(intentSystem, PHRASES[5], 0.2, json = true)
        assertEquals("CAMPAIGN_EDIT", intent(out))
        assertFalse(patch(out).containsKey("startDate"))
        assertFalse(patch(out).containsKey("startTime"))
        assertEquals("Тестовая", patch(out)["name"]!!.jsonPrimitive.content)
    }

    @Test
    fun `banner copy returns array within limits`() = runBlocking {
        val system = "Сгенерируй копирайт: headline, body, cta. Верни JSON-массив."
        val out = llm.chat(system, "вклад для физлиц", 0.2, json = true)
        val arr = json.parseToJsonElement(out).jsonArray
        assertTrue(arr is JsonArray)
        assertTrue(arr.isNotEmpty())
        for (item in arr) {
            val h = item.jsonObject["headline"]!!.jsonPrimitive.content
            val b = item.jsonObject["body"]!!.jsonPrimitive.content
            val c = item.jsonObject["cta"]!!.jsonPrimitive.content
            assertTrue(h.length <= 40)
            assertTrue(b.length <= 90)
            assertTrue(c.length <= 20)
        }
    }
}
