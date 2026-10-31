package ru.zhma.campaign.server

import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class CatalogServerTest : ServerTestBase() {
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
    fun channelsBannerTypesAndMissingRole() = testApplication {
        application { module(db()) }
        val client = jsonClient()
        val channels = client.get("/api/channels") { manager() }.jsonArray()
        assertEquals(listOf("iOS", "ANDROID", "WEB"), channels.map { it.jsonObject["code"]!!.jsonPrimitive.content })
        val types = client.get("/api/banner-types") { manager() }.jsonArray()
        assertEquals(setOf("STORY", "SMART_BANNER"), types.map { it.jsonObject["code"]!!.jsonPrimitive.content }.toSet())
        val story = types.first { it.jsonObject["code"]!!.jsonPrimitive.content == "STORY" }.jsonObject
        assertEquals(100, story["titleMaxLength"]!!.jsonPrimitive.content.toInt())
        val noRole = client.get("/api/channels") { header("X-User", "ivanov.p") }
        assertEquals(HttpStatusCode.BadRequest, noRole.status)
        assertEquals("X-Role", noRole.errorCode())
    }

    @Test
    fun missingUserIs400() = testApplication {
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
}
