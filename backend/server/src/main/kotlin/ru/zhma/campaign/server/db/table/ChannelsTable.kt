package ru.zhma.campaign.server.db.table

import org.jetbrains.exposed.sql.Table

object ChannelsTable : Table("channels") {
    val id = long("id")
    val code = varchar("code", 32).uniqueIndex()
    val description = varchar("description", 128)
    override val primaryKey = PrimaryKey(id)
}
