package ru.zhma.campaign.server.db.table

import org.jetbrains.exposed.sql.Table

object CampaignPlacesTable : Table("campaign_places") {
    val campaignId = long("campaign_id")
    val placeId = long("place_id")
    override val primaryKey = PrimaryKey(campaignId, placeId)
}
