package ru.zhma.campaign.server.http.route

import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import ru.zhma.campaign.server.db.DatabaseFactory.db
import ru.zhma.campaign.server.service.listBannerTypes
import ru.zhma.campaign.server.service.listChannels
import ru.zhma.campaign.server.service.listPlaces
import ru.zhma.campaign.server.service.listSegments
import ru.zhma.campaign.server.service.listTags
import ru.zhma.campaign.server.service.listTopics

fun Route.catalogRoutes() {
    get("/topics") { call.respond(db { listTopics() }) }
    get("/tags") { call.respond(db { listTags() }) }
    get("/segments") { call.respond(db { listSegments() }) }
    get("/places") { call.respond(db { listPlaces() }) }
    get("/banner-types") { call.respond(db { listBannerTypes() }) }
    get("/channels") { call.respond(db { listChannels() }) }
}
