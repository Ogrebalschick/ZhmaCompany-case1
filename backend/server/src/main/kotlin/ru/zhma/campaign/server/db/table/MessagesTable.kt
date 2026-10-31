package ru.zhma.campaign.server.db.table

import org.jetbrains.exposed.sql.Table

object MessagesTable : Table("messages") {
    val id = long("id").autoIncrement()
    val sessionId = varchar("session_id", 64)
    val role = varchar("role", 16)
    val text = text("text")
    val blocksJson = text("blocks_json").nullable()
    val createdAt = varchar("created_at", 40)
    override val primaryKey = PrimaryKey(id)
}
