package ru.zhma.campaign.server.db.table

import org.jetbrains.exposed.sql.Table

object BannersTable : Table("banners") {
    val id = long("id").autoIncrement()
    val campaignId = long("campaign_id")
    val name = varchar("name", 200)
    val bannerTypeId = long("banner_type_id")
    val contentJson = text("content_json")
    override val primaryKey = PrimaryKey(id)
}
