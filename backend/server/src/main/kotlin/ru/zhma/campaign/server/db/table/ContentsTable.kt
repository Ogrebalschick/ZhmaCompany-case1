package ru.zhma.campaign.server.db.table

import org.jetbrains.exposed.sql.Table

object ContentsTable : Table("contents") {
    val id = long("id").autoIncrement()
    val campaignId = long("campaign_id")
    val name = varchar("name", 200)
    val contentType = varchar("content_type", 32)
    val contentJson = text("content_json")
    override val primaryKey = PrimaryKey(id)
}
