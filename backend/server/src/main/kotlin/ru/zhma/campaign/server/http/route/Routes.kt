package ru.zhma.campaign.server.http.route

import io.ktor.server.routing.Route
import io.ktor.server.routing.route
import ru.zhma.campaign.server.http.installCaller
import ru.zhma.campaign.server.images.ImageJobs
import ru.zhma.campaign.server.jobs.JobProcessor

fun Route.apiRoutes(
    jobs: JobProcessor,
    images: ImageJobs? = null,
) {
    route("/api") {
        installCaller()
        catalogRoutes()
        campaignRoutes()
        chatRoutes(jobs)
        imageRoutes(images)
        healthRoutes()
    }
}
