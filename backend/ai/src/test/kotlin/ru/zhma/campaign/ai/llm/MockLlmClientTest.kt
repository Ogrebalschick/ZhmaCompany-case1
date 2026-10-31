package ru.zhma.campaign.ai.llm

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
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
    private val intentSystem = "Разбор. intent — CAMPAIGN_EDIT | CONFIRM | ... Текущий черновик:\n{}\nКаталог:\n"

    private fun parse(text: String): JsonObject = json.parseToJsonElement(text).jsonObject

    private fun intent(text: String): String = parse(text)["intent"]!!.jsonPrimitive.content

    private fun patch(text: String): JsonObject = parse(text)["patch"]?.jsonObject ?: JsonObject(emptyMap())

    @Test
    fun `brief gives name and topic guess but no dates`() =
        runBlocking {
            val out = llm.chat(intentSystem, "Кампания на баню и спа в Сочи для женщин, сторис на главном", 0.1, json = true)
            assertEquals("CAMPAIGN_EDIT", intent(out))
            assertEquals("Баню и спа в Сочи", patch(out)["name"]!!.jsonPrimitive.content)
            assertEquals("LIFESTYLE", patch(out)["topicGuess"]!!.jsonPrimitive.content)
            assertFalse(patch(out).containsKey("startDate"))
        }

    @Test
    fun `commands`() =
        runBlocking {
            assertEquals("LIST_CAMPAIGNS", intent(llm.chat(intentSystem, "покажи кампании", 0.1, true)))
            assertEquals("CONFIRM", intent(llm.chat(intentSystem, "подтверждаю", 0.1, true)))
            assertEquals("CANCEL", intent(llm.chat(intentSystem, "отмена", 0.1, true)))
            assertEquals("GENERATE_CREATIVE", intent(llm.chat(intentSystem, "сделай текст баннера", 0.1, true)))
            assertEquals("GENERATE_IMAGE", intent(llm.chat(intentSystem, "нарисуй картинку", 0.1, true)))
            assertEquals("QUESTION", intent(llm.chat(intentSystem, "какие есть места?", 0.1, true)))
        }

    @Test
    fun `quoted name is taken as is`() =
        runBlocking {
            val out = llm.chat(intentSystem, "Назови кампанию «Тестовая»", 0.1, json = true)
            assertEquals("Тестовая", patch(out)["name"]!!.jsonPrimitive.content)
        }

    @Test
    fun `banner copy returns array within limits`() =
        runBlocking {
            val out = llm.chat("Сгенерируй: headline, body, cta.", "баня", 0.2, json = true)
            val arr = json.parseToJsonElement(out).jsonArray
            assertTrue(arr.isNotEmpty())
            val item = arr[0].jsonObject
            assertTrue(item["headline"]!!.jsonPrimitive.content.length <= 40)
            assertTrue(item["body"]!!.jsonPrimitive.content.length <= 90)
            assertTrue(item["cta"]!!.jsonPrimitive.content.length <= 20)
        }
}
