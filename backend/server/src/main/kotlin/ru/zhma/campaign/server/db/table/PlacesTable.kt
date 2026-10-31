package ru.zhma.campaign.server.db.table

import org.jetbrains.exposed.sql.Table

object PlacesTable : Table("places") {
    val id = long("id")
    val code = varchar("code", 64).uniqueIndex()
    val description = varchar("description", 256)
    val defaultBanner = varchar("default_banner", 64).nullable()
    val state = varchar("state", 16)
    override val primaryKey = PrimaryKey(id)
}
