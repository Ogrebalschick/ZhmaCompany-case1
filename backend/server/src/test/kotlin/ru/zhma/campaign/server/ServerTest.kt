package ru.zhma.campaign.server

import io.ktor.client.HttpClient
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.testing.testApplication
import kotlinx.coroutines.delay
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.double
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.update
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import ru.zhma.campaign.server.db.DatabaseFactory
import ru.zhma.campaign.server.db.SessionsTable
import ru.zhma.campaign.server.json.AppJson
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ServerTest {
    @TempDir
    lateinit var temp: Path

    @AfterEach
    fun tearDown() {
        DatabaseFactory.close()
    }

    @Test
    fun seedContainsSegmentAndPlace() = testApplication {
        application { module(db()) }
        val client = jsonClient()
        val segments = client.get("/api/segments") { manager() }
        assertEquals(HttpStatusCode.OK, segments.status)
        assertEquals("ivanov.p", segments.headers["X-User"])
        val age = segments.jsonArray().first { it.jsonObject["code"]!!.jsonPrimitive.content == "AGE_26_45" }.jsonObject
        assertEquals("ACTIVE", age["state"]!!.jsonPrimitive.content)
        val winter = segments.jsonArray().first { it.jsonObject["code"]!!.jsonPrimitive.content == "SEG_DRAFT_WINTER" }.jsonObject
        assertEquals("NEW", winter["state"]!!.jsonPrimitive.content)
        val places = client.get("/api/places") { manager() }.jsonArray()
        val main = places.first { it.jsonObject["id"]!!.jsonPrimitive.content == "1" }.jsonObject
        assertEquals("mainScreenStories", main["code"]!!.jsonPrimitive.content)
        assertEquals(11, client.get("/api/topics") { manager() }.jsonArray().size)
        assertEquals(6, client.get("/api/tags") { manager() }.jsonArray().size)
        assertEquals(40, segments.jsonArray().size)
        assertEquals(8, places.size)
    }

    @Test
    fun missingHeaderIs400() = testApplication {
        application { module(db()) }
        val response = jsonClient().get("/api/topics")
        assertEquals(HttpStatusCode.BadRequest, response.status)
        assertEquals("X-User", response.errorCode())
    }

    @Test
    fun unknownCampaignIs404() = testApplication {
        application { module(db()) }
        val response = jsonClient().get("/api/campaigns/999") { manager() }
        assertEquals(HttpStatusCode.NotFound, response.status)
        assertEquals("ENTITY_NOT_FOUND", response.errorCode())
    }

    @Test
    fun approveByManagerIsForbidden() = testApplication {
        application { module(db()) }
        val response = jsonClient().post("/api/campaigns/1/approve") { manager() }
        assertEquals(HttpStatusCode.BadRequest, response.status)
        assertEquals("ROLE_FORBIDDEN", response.errorCode())
    }

    @Test
    fun emptyMessageDoesNotCrash() = testApplication {
        application { module(db()) }
        val client = jsonClient()
        val created = client.post("/api/sessions") { manager() }
        assertEquals(HttpStatusCode.OK, created.status)
        val sessionId = created.json()["sessionId"]!!.jsonPrimitive.content
        val posted = client.post("/api/sessions/$sessionId/messages") {
            manager()
            contentType(ContentType.Application.Json)
            setBody("""{"text":""}""")
        }
        assertEquals(HttpStatusCode.Accepted, posted.status)
        val jobId = posted.json()["jobId"]!!.jsonPrimitive.content
        var status = "PENDING"
        repeat(50) {
            status = client.get("/api/jobs/$jobId") { manager() }.json()["status"]!!.jsonPrimitive.content
            if (status != "PENDING") return@repeat
            delay(100)
        }
        assertTrue(status == "DONE" || status == "FAILED")
        assertEquals(HttpStatusCode.OK, client.get("/api/topics") { manager() }.status)
    }

    @Test
    fun confirmModerationAndActivate() = testApplication {
        application { module(db()) }
        val client = jsonClient()
        val sessionId = client.post("/api/sessions") { manager() }.json()["sessionId"]!!.jsonPrimitive.content
        DatabaseFactory.db {
            SessionsTable.update({ SessionsTable.id eq sessionId }) {
                it[draftJson] = draftJson()
            }
        }
        val created = client.post("/api/campaigns/0/confirm?sessionId=$sessionId") { manager() }
        assertEquals(HttpStatusCode.OK, created.status)
        val campaign = created.json()
        val id = campaign["id"]!!.jsonPrimitive.content
        assertEquals("DRAFT", campaign["state"]!!.jsonPrimitive.content)
        assertEquals(false, campaign["published"]!!.jsonPrimitive.content.toBoolean())
        assertEquals(0.0, campaign["weight"]!!.jsonPrimitive.double)
        assertEquals(15.0, campaign["initialCtr"]!!.jsonPrimitive.double)
        assertEquals("ivanov.p", campaign["author"]!!.jsonPrimitive.content)
        val warnings = client.get("/api/campaigns/$id/warnings") { manager() }.json()
        assertTrue((warnings["errors"] as JsonArray).isEmpty())
        assertTrue(warnings["warnings"]!!.jsonArray.any { it.jsonPrimitive.content.contains("креатив") })

        val moderated = client.post("/api/campaigns/$id/to-moderation") { manager() }
        assertEquals("ON_MODERATION", moderated.json()["state"]!!.jsonPrimitive.content)

        val noComment = client.post("/api/campaigns/$id/decline") {
            moderator()
            contentType(ContentType.Application.Json)
            setBody("""{"comment":""}""")
        }
        assertEquals(HttpStatusCode.BadRequest, noComment.status)

        val declined = client.post("/api/campaigns/$id/decline") {
            moderator()
            contentType(ContentType.Application.Json)
            setBody("""{"comment":"поправьте ставку"}""")
        }
        assertEquals("NEEDS_WORK", declined.json()["state"]!!.jsonPrimitive.content)

        client.post("/api/campaigns/$id/to-moderation") { manager() }
        val forbidden = client.post("/api/campaigns/$id/approve") { manager() }
        assertEquals("ROLE_FORBIDDEN", forbidden.errorCode())
        val approved = client.post("/api/campaigns/$id/approve") { moderator() }
        assertEquals("APPROVED", approved.json()["state"]!!.jsonPrimitive.content)
        val activateForbidden = client.post("/api/campaigns/$id/activate") { moderator() }
        assertEquals("ROLE_FORBIDDEN", activateForbidden.errorCode())
        val active = client.post("/api/campaigns/$id/activate") { manager() }
        assertEquals("ACTIVE", active.json()["state"]!!.jsonPrimitive.content)
        assertEquals(true, active.json()["published"]!!.jsonPrimitive.content.toBoolean())

        val audit = client.get("/api/campaigns/$id/audit") { manager() }.jsonArray()
        val actions = audit.map { it.jsonObject["action"]!!.jsonPrimitive.content }
        assertTrue("CREATE" in actions)
        assertTrue("TO_MODERATION" in actions)
        assertTrue("DECLINE" in actions)
        assertTrue("APPROVE" in actions)
        assertTrue("ACTIVATE" in actions)
        val decline = audit.first { it.jsonObject["action"]!!.jsonPrimitive.content == "DECLINE" }.jsonObject
        assertEquals("поправьте ставку", decline["comment"]!!.jsonPrimitive.content)
    }

    @Test
    fun duplicateNameAndSegmentRules() = testApplication {
        application { module(db()) }
        val client = jsonClient()
        val first = session(client)
        putDraft(first, draftJson(name = "Одинаковая"))
        client.post("/api/campaigns/0/confirm?sessionId=$first") { manager() }
        val second = session(client)
        putDraft(second, draftJson(name = "Одинаковая"))
        val duplicate = client.post("/api/campaigns/0/confirm?sessionId=$second") { manager() }
        assertEquals(HttpStatusCode.BadRequest, duplicate.status)
        assertEquals("NAME_DUPLICATE", duplicate.errorCode())

        val third = session(client)
        putDraft(third, draftJson(name = "Все пользователи", audience = "ALL_USERS", included = """["AGE_26_45"]"""))
        val forbidden = client.post("/api/campaigns/0/confirm?sessionId=$third") { manager() }
        assertEquals("SEGMENTS_FORBIDDEN_FOR_ALL_USERS", forbidden.errorCode())
    }

    private fun db(): String = temp.resolve("test.db").toString()

    private fun io.ktor.server.testing.ApplicationTestBuilder.jsonClient(): HttpClient = createClient {
        install(ContentNegotiation) { json(AppJson) }
    }

    private fun io.ktor.client.request.HttpRequestBuilder.manager() {
        header("X-User", "ivanov.p")
        header("X-Role", "campaignManager")
    }

    private fun io.ktor.client.request.HttpRequestBuilder.moderator() {
        header("X-User", "petrova.m")
        header("X-Role", "campaignModerator")
    }

    private suspend fun HttpResponse.json(): JsonObject = AppJson.parseToJsonElement(bodyAsText()).jsonObject

    private suspend fun HttpResponse.jsonArray() = AppJson.parseToJsonElement(bodyAsText()).jsonArray

    private suspend fun HttpResponse.errorCode(): String =
        json()["errors"]!!.jsonArray.first().jsonObject["code"]!!.jsonPrimitive.content

    private suspend fun session(client: HttpClient): String =
        client.post("/api/sessions") { manager() }.json()["sessionId"]!!.jsonPrimitive.content

    private suspend fun putDraft(sessionId: String, json: String) {
        DatabaseFactory.db {
            SessionsTable.update({ SessionsTable.id eq sessionId }) {
                it[draftJson] = json
            }
        }
    }

    private fun draftJson(
        name: String = "Вклад весна",
        audience: String = "SEGMENTED",
        included: String = """["AGE_26_45"]""",
    ) = """
        {
          "name": "$name",
          "startDate": "2026-11-05",
          "startTime": "10:00",
          "topic": "SALES",
          "audienceType": "$audience",
          "includedSegments": $included,
          "placeIds": [1],
          "tagIds": [5]
        }
    """.trimIndent()
}
