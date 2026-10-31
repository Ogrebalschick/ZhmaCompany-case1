package ru.zhma.campaign.server.db.table

import org.jetbrains.exposed.sql.Table

object SegmentsTable : Table("segments") {
    val code = varchar("code", 64)
    val name = varchar("name", 200)
    val type = varchar("type", 64)
    val state = varchar("state", 16)
    val count = long("count")
    override val primaryKey = PrimaryKey(code)
}
