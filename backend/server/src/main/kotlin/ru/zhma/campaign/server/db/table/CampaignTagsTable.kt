package ru.zhma.campaign.server.db.table

import org.jetbrains.exposed.sql.Table

object CampaignTagsTable : Table("campaign_tags") {
    val campaignId = long("campaign_id")
    val tagId = long("tag_id")
    override val primaryKey = PrimaryKey(campaignId, tagId)
}
