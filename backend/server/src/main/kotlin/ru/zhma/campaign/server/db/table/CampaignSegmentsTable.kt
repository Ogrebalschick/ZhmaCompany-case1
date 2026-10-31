package ru.zhma.campaign.server.db.table

import org.jetbrains.exposed.sql.Table

object CampaignSegmentsTable : Table("campaign_segments") {
    val campaignId = long("campaign_id")
    val code = varchar("code", 64)
    val included = bool("included")
    override val primaryKey = PrimaryKey(campaignId, code)
}
