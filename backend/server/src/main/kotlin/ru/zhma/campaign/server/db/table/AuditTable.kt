package ru.zhma.campaign.server.db.table

import org.jetbrains.exposed.sql.Table

object AuditTable : Table("audit") {
    val id = long("id").autoIncrement()
    val campaignId = long("campaign_id").nullable()
    val entity = varchar("entity", 32)
    val entityId = long("entity_id")
    val action = varchar("action", 32)
    val changes = text("changes").nullable()
    val at = varchar("at", 32)
    val actor = varchar("actor", 128)
    val comment = text("comment").nullable()
    override val primaryKey = PrimaryKey(id)
}
