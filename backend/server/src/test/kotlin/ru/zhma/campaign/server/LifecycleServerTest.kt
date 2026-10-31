package ru.zhma.campaign.server

import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.double
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LifecycleServerTest : ServerTestBase() {
    @Test
    fun approveByManagerIsForbidden() = testApplication {
        application { module(db()) }
        val response = jsonClient().post("/api/campaigns/1/approve") { manager() }
        assertEquals(HttpStatusCode.BadRequest, response.status)
        assertEquals("ROLE_FORBIDDEN", response.errorCode())
    }

    @Test
    fun confirmModerationAndActivate() = testApplication {
        application { module(db()) }
        val client = jsonClient()
        val sessionId = session(client)
        putDraft(sessionId, draftJson())
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
    fun forbiddenTransitionsWinAfterRoleCheck() = testApplication {
        application { module(db()) }
        val client = jsonClient()
        val sessionId = session(client)
        putDraft(sessionId, draftJson())
        val id = client.post("/api/campaigns/0/confirm?sessionId=$sessionId") { manager() }.json()["id"]!!.jsonPrimitive.content

        val declineEarly = client.post("/api/campaigns/$id/decline") {
            moderator()
            contentType(ContentType.Application.Json)
            setBody("""{"comment":"рано"}""")
        }
        assertEquals("DECLINE_NOT_ALLOWED", declineEarly.errorCode())
        val approveEarly = client.post("/api/campaigns/$id/approve") { moderator() }
        assertEquals("APPROVE_NOT_ALLOWED", approveEarly.errorCode())
        val activateEarly = client.post("/api/campaigns/$id/activate") { manager() }
        assertEquals("ACTIVATE_NOT_ALLOWED", activateEarly.errorCode())

        client.post("/api/campaigns/$id/to-moderation") { manager() }
        val again = client.post("/api/campaigns/$id/to-moderation") { manager() }
        assertEquals("TO_MODERATION_NOT_ALLOWED", again.errorCode())
        val managerDecline = client.post("/api/campaigns/$id/decline") {
            manager()
            contentType(ContentType.Application.Json)
            setBody("""{"comment":"не та роль"}""")
        }
        assertEquals("ROLE_FORBIDDEN", managerDecline.errorCode())
        assertEquals("ON_MODERATION", client.get("/api/campaigns/$id") { manager() }.json()["state"]!!.jsonPrimitive.content)
    }
}
