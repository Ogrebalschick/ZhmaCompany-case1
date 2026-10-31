package ru.zhma.campaign.server.db.table

import org.jetbrains.exposed.sql.Table

object TagsTable : Table("tags") {
    val id = long("id")
    val name = varchar("name", 200)
    override val primaryKey = PrimaryKey(id)
}
