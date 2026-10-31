package ru.zhma.campaign.server

import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.testApplication
import kotlinx.coroutines.delay
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ChatServerTest : ServerTestBase() {
    @Test
    fun emptyMessageDoesNotCrash() =
        testApplication {
            application { module(db()) }
            val client = jsonClient()
            val created = client.post("/api/sessions") { manager() }
            assertEquals(HttpStatusCode.OK, created.status)
            val sessionId = created.json()["sessionId"]!!.jsonPrimitive.content
            val posted =
                client.post("/api/sessions/$sessionId/messages") {
                    manager()
                    contentType(ContentType.Application.Json)
                    setBody("""{"text":""}""")
                }
            assertEquals(HttpStatusCode.Accepted, posted.status)
            val jobId = posted.json()["jobId"]!!.jsonPrimitive.content
            var status = "PENDING"
            repeat(50) {
                status =
                    client
                        .get("/api/jobs/$jobId") { manager() }
                        .json()["status"]!!
                        .jsonPrimitive.content
                if (status != "PENDING") return@repeat
                delay(100)
            }
            assertTrue(status == "DONE" || status == "FAILED")
            assertEquals(HttpStatusCode.OK, client.get("/api/topics") { manager() }.status)
        }

    @Test
    fun unknownSessionAndJob() =
        testApplication {
            application { module(db()) }
            val client = jsonClient()
            val missingSession =
                client.post("/api/sessions/no-such/messages") {
                    manager()
                    contentType(ContentType.Application.Json)
                    setBody("""{"text":"привет"}""")
                }
            assertEquals(HttpStatusCode.NotFound, missingSession.status)
            assertEquals("ENTITY_NOT_FOUND", missingSession.errorCode())
            val missingJob = client.get("/api/jobs/no-such") { manager() }
            assertEquals(HttpStatusCode.NotFound, missingJob.status)
            val messages = client.get("/api/sessions/no-such/messages") { manager() }
            assertEquals(HttpStatusCode.NotFound, messages.status)
        }
}
