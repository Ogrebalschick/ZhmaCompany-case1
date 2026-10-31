package ru.zhma.campaign.server.db.table

import org.jetbrains.exposed.sql.Table

object BannerTypesTable : Table("banner_types") {
    val id = long("id")
    val code = varchar("code", 64).uniqueIndex()
    val description = varchar("description", 128)
    val previewForm = varchar("preview_form", 64)
    val titleMax = integer("title_max")
    val textMax = integer("text_max")
    val channels = varchar("channels", 128)
    override val primaryKey = PrimaryKey(id)
}
