package ru.zhma.campaign.ai.llm

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.serializer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class StructuredLlmTest {

    private class ScriptedLlm(private val script: List<String>) : LlmClient {
        var calls = 0
        override suspend fun chat(system: String, user: String, temperature: Double, json: Boolean): String {
            val idx = calls.coerceAtMost(script.size - 1)
            calls++
            return script[idx]
        }
    }

    private val intSerializer = Int.serializer()

    @Test
    fun `accepts fenced json`() = runBlocking {
        val llm = ScriptedLlm(listOf("```json\n42\n```"))
        val sl = StructuredLlm(llm)
        val result = sl.ask("sys", "user", intSerializer)
        assertEquals(42, result)
        assertEquals(1, llm.calls)
    }

    @Test
    fun `accepts bare json number`() = runBlocking {
        val llm = ScriptedLlm(listOf("17"))
        val sl = StructuredLlm(llm)
        val result = sl.ask("sys", "user", intSerializer)
        assertEquals(17, result)
        assertEquals(1, llm.calls)
    }

    @Test
    fun `rejects junk until third valid attempt`() = runBlocking {
        val llm = ScriptedLlm(listOf("not json", "still bad", "7"))
        val sl = StructuredLlm(llm)
        val result = sl.ask("sys", "user", intSerializer)
        assertEquals(7, result)
        assertEquals(3, llm.calls)
    }

    @Test
    fun `throws after retries when never valid`() = runBlocking {
        val llm = ScriptedLlm(listOf("junk", "more junk", "nope"))
        val sl = StructuredLlm(llm)
        val ex = try {
            sl.ask("sys", "user", intSerializer)
            null
        } catch (e: IllegalStateException) {
            e
        }
        assertNotNull(ex)
        assertTrue(ex!!.message!!.contains("3"))
        assertEquals(3, llm.calls)
    }

    @Test
    fun `validate failure triggers retry then succeeds`() = runBlocking {
        val llm = ScriptedLlm(listOf("1", "5"))
        val sl = StructuredLlm(llm)
        val result = sl.ask(
            system = "sys",
            user = "user",
            serializer = intSerializer,
            validate = { if (it != 5) "value must be 5" else null }
        )
        assertEquals(5, result)
        assertEquals(2, llm.calls)
    }
}