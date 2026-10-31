package ru.zhma.campaign.server.db.table

import org.jetbrains.exposed.sql.Table

object TopicsTable : Table("topics") {
    val value = varchar("value", 64)
    val label = varchar("label", 128)
    override val primaryKey = PrimaryKey(value)
}
