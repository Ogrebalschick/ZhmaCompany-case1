package ru.zhma.campaign.server.http.route

import io.ktor.server.routing.Route
import io.ktor.server.routing.route
import ru.zhma.campaign.server.http.installCaller
import ru.zhma.campaign.server.jobs.JobProcessor

fun Route.apiRoutes(jobs: JobProcessor) {
    route("/api") {
        installCaller()
        catalogRoutes()
        campaignRoutes()
        chatRoutes(jobs)
    }
}
