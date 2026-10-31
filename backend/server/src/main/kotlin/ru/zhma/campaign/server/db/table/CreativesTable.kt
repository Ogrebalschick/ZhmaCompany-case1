package ru.zhma.campaign.server.db.table

import org.jetbrains.exposed.sql.Table

object CreativesTable : Table("creatives") {
    val id = long("id").autoIncrement()
    val campaignId = long("campaign_id")
    val name = varchar("name", 200)
    val bannerId = long("banner_id")
    val contentId = long("content_id").nullable()
    val channelCode = varchar("channel_code", 32)
    override val primaryKey = PrimaryKey(id)
}
