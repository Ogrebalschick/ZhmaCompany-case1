package ru.zhma.campaign.server.db.table

import org.jetbrains.exposed.sql.Table

object JobsTable : Table("jobs") {
    val id = varchar("id", 64)
    val sessionId = varchar("session_id", 64)
    val status = varchar("status", 16)
    val resultJson = text("result_json").nullable()
    val error = text("error").nullable()
    val createdAt = varchar("created_at", 40)
    override val primaryKey = PrimaryKey(id)
}
