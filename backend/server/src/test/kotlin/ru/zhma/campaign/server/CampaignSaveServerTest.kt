package ru.zhma.campaign.server

import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CampaignSaveServerTest : ServerTestBase() {
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

    @Test
    fun incompleteDraftAndBadBanner() = testApplication {
        application { module(db()) }
        val client = jsonClient()
        val empty = session(client)
        val rejected = client.post("/api/campaigns/0/confirm?sessionId=$empty") { manager() }
        assertEquals(HttpStatusCode.BadRequest, rejected.status)
        val codes = rejected.json()["errors"]!!.jsonArray.map { it.jsonObject["code"]!!.jsonPrimitive.content }
        assertTrue("name" in codes)
        assertTrue("places" in codes)

        val winter = session(client)
        putDraft(winter, draftJson(included = """["SEG_DRAFT_WINTER"]"""))
        val inactive = client.post("/api/campaigns/0/confirm?sessionId=$winter") { manager() }
        assertEquals(HttpStatusCode.BadRequest, inactive.status)
        assertTrue(inactive.bodyAsText().contains("SEG_DRAFT_WINTER"))

        val webStory = session(client)
        putDraft(webStory, draftJson(banner = storyBanner(channel = "WEB")))
        val channel = client.post("/api/campaigns/0/confirm?sessionId=$webStory") { manager() }
        assertEquals(HttpStatusCode.BadRequest, channel.status)
        assertEquals("channel", channel.errorCode())

        val range = session(client)
        putDraft(range, draftJson(endDate = "2026-11-01", endTime = "09:00"))
        val dates = client.post("/api/campaigns/0/confirm?sessionId=$range") { manager() }
        assertEquals("DATE_RANGE_INVALID", dates.errorCode())

        val byModerator = client.post("/api/campaigns/0/confirm?sessionId=$empty") { moderator() }
        assertEquals("ROLE_FORBIDDEN", byModerator.errorCode())
        val noSession = client.post("/api/campaigns/0/confirm") { manager() }
        assertEquals("sessionId", noSession.errorCode())
    }

    @Test
    fun saveBannerThenUpdateAndList() = testApplication {
        application { module(db()) }
        val client = jsonClient()
        val sessionId = session(client)
        putDraft(
            sessionId,
            draftJson(
                audience = "ALL_USERS",
                included = "[]",
                places = "[1,4]",
                tags = "[]",
                banner = storyBanner(),
            ),
        )
        val created = client.post("/api/campaigns/0/confirm?sessionId=$sessionId") { manager() }
        assertEquals(HttpStatusCode.OK, created.status)
        val body = created.json()
        val id = body["id"]!!.jsonPrimitive.content
        assertEquals("ALL_USERS", body["audienceType"]!!.jsonPrimitive.content)
        assertEquals(listOf("1", "4"), body["places"]!!.jsonArray.map { it.jsonPrimitive.content })
        val content = body["banners"]!!.jsonArray.first().jsonObject["content"]!!.jsonObject
        assertEquals("default", content["actionType"]!!.jsonPrimitive.content)
        assertEquals("iOS", body["creatives"]!!.jsonArray.first().jsonObject["channelCode"]!!.jsonPrimitive.content)
        val warnings = client.get("/api/campaigns/$id/warnings") { manager() }.json()
        val lines = warnings["warnings"]!!.jsonArray.map { it.jsonPrimitive.content }
        assertTrue(lines.none { it.contains("креатив") })
        assertTrue(lines.any { it.contains("деактивир") })
        assertTrue(lines.any { it.contains("тег") })

        putDraft(
            sessionId,
            draftJson(
                name = "Вклад весна 2",
                banner = storyBanner(title = "Новый заголовок"),
                savedCampaignId = id,
            ),
        )
        val updated = client.post("/api/campaigns/$id/confirm?sessionId=$sessionId") { manager() }
        assertEquals(HttpStatusCode.OK, updated.status)
        assertEquals(id, updated.json()["id"]!!.jsonPrimitive.content)
        assertEquals("Вклад весна 2", updated.json()["name"]!!.jsonPrimitive.content)
        assertEquals("Новый заголовок", updated.json()["banners"]!!.jsonArray.first().jsonObject["content"]!!.jsonObject["title"]!!.jsonPrimitive.content)
        val listed = client.get("/api/campaigns") { manager() }.jsonArray()
        assertEquals("Вклад весна 2", listed.single().jsonObject["name"]!!.jsonPrimitive.content)
        val audit = client.get("/api/campaigns/$id/audit") { manager() }.jsonArray()
        assertTrue(audit.any { it.jsonObject["action"]!!.jsonPrimitive.content == "UPDATE" })
    }
}
