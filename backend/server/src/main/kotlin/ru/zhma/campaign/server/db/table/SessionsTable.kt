package ru.zhma.campaign.server.db.table

import org.jetbrains.exposed.sql.Table

object SessionsTable : Table("sessions") {
    val id = varchar("id", 64)
    val draftJson = text("draft_json")
    val createdAt = varchar("created_at", 40)
    override val primaryKey = PrimaryKey(id)
}
