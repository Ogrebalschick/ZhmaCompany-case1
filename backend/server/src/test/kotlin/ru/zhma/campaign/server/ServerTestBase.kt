package ru.zhma.campaign.server

import io.ktor.client.HttpClient
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.testing.ApplicationTestBuilder
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.update
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.io.TempDir
import ru.zhma.campaign.server.db.DatabaseFactory
import ru.zhma.campaign.server.db.table.SessionsTable
import ru.zhma.campaign.server.json.AppJson
import java.nio.file.Path

abstract class ServerTestBase {
    @TempDir
    lateinit var temp: Path

    @AfterEach
    fun tearDown() {
        DatabaseFactory.close()
    }

    protected fun db(): String = temp.resolve("test.db").toString()

    protected fun ApplicationTestBuilder.jsonClient(): HttpClient = createClient {
        install(ContentNegotiation) { json(AppJson) }
    }

    protected fun HttpRequestBuilder.manager() {
        header("X-User", "ivanov.p")
        header("X-Role", "campaignManager")
    }

    protected fun HttpRequestBuilder.moderator() {
        header("X-User", "petrova.m")
        header("X-Role", "campaignModerator")
    }

    protected suspend fun HttpResponse.json(): JsonObject = AppJson.parseToJsonElement(bodyAsText()).jsonObject

    protected suspend fun HttpResponse.jsonArray() = AppJson.parseToJsonElement(bodyAsText()).jsonArray

    protected suspend fun HttpResponse.errorCode(): String =
        json()["errors"]!!.jsonArray.first().jsonObject["code"]!!.jsonPrimitive.content

    protected suspend fun session(client: HttpClient): String =
        client.post("/api/sessions") { manager() }.json()["sessionId"]!!.jsonPrimitive.content

    protected suspend fun putDraft(sessionId: String, json: String) {
        DatabaseFactory.db {
            SessionsTable.update({ SessionsTable.id eq sessionId }) {
                it[draftJson] = json
            }
        }
    }

    protected fun draftJson(
        name: String = "Вклад весна",
        audience: String = "SEGMENTED",
        included: String = """["AGE_26_45"]""",
        places: String = "[1]",
        tags: String = "[5]",
        endDate: String? = null,
        endTime: String? = null,
        banner: String? = null,
        savedCampaignId: String? = null,
    ): String {
        val extra = buildString {
            if (endDate != null) append(""","endDate":"$endDate"""")
            if (endTime != null) append(""","endTime":"$endTime"""")
            if (banner != null) append(""","banner":$banner""")
            if (savedCampaignId != null) append(""","savedCampaignId":$savedCampaignId""")
        }
        return """
            {
              "name": "$name",
              "startDate": "2026-11-05",
              "startTime": "10:00",
              "topic": "SALES",
              "audienceType": "$audience",
              "includedSegments": $included,
              "placeIds": $places,
              "tagIds": $tags
              $extra
            }
        """.trimIndent()
    }

    protected fun storyBanner(channel: String = "iOS", title: String = "Вклад") =
        """{"bannerType":"STORY","channel":"$channel","title":"$title","text":"Текст","actionType":"default"}"""
}
