package ru.zhma.campaign.server.db.table

import org.jetbrains.exposed.sql.Table

object CampaignsTable : Table("campaigns") {
    val id = long("id").autoIncrement()
    val name = varchar("name", 100).uniqueIndex()
    val marketingName = varchar("marketing_name", 100).nullable()
    val startDate = varchar("start_date", 10)
    val startTime = varchar("start_time", 5)
    val endDate = varchar("end_date", 10).nullable()
    val endTime = varchar("end_time", 5).nullable()
    val topic = varchar("topic", 64)
    val keywords = varchar("keywords", 512).nullable()
    val audienceType = varchar("audience_type", 32)
    val weight = double("weight")
    val initialCtr = double("initial_ctr")
    val state = varchar("state", 32)
    val published = bool("published")
    val author = varchar("author", 128)
    val lastEditAuthor = varchar("last_edit_author", 128)
    override val primaryKey = PrimaryKey(id)
}
