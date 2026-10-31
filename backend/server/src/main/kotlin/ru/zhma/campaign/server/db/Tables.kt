package ru.zhma.campaign.server.db

import org.jetbrains.exposed.sql.Table

object TopicsTable : Table("topics") {
    val value = varchar("value", 64)
    val label = varchar("label", 128)
    override val primaryKey = PrimaryKey(value)
}

object TagsTable : Table("tags") {
    val id = long("id")
    val name = varchar("name", 200)
    override val primaryKey = PrimaryKey(id)
}

object SegmentsTable : Table("segments") {
    val code = varchar("code", 64)
    val name = varchar("name", 200)
    val type = varchar("type", 64)
    val state = varchar("state", 16)
    val count = long("count")
    override val primaryKey = PrimaryKey(code)
}

object PlacesTable : Table("places") {
    val id = long("id")
    val code = varchar("code", 64).uniqueIndex()
    val description = varchar("description", 256)
    val defaultBanner = varchar("default_banner", 64).nullable()
    val state = varchar("state", 16)
    override val primaryKey = PrimaryKey(id)
}

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

object ChannelsTable : Table("channels") {
    val id = long("id")
    val code = varchar("code", 32).uniqueIndex()
    val description = varchar("description", 128)
    override val primaryKey = PrimaryKey(id)
}

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

object CampaignTagsTable : Table("campaign_tags") {
    val campaignId = long("campaign_id")
    val tagId = long("tag_id")
    override val primaryKey = PrimaryKey(campaignId, tagId)
}

object CampaignPlacesTable : Table("campaign_places") {
    val campaignId = long("campaign_id")
    val placeId = long("place_id")
    override val primaryKey = PrimaryKey(campaignId, placeId)
}

object CampaignSegmentsTable : Table("campaign_segments") {
    val campaignId = long("campaign_id")
    val code = varchar("code", 64)
    val included = bool("included")
    override val primaryKey = PrimaryKey(campaignId, code)
}

object BannersTable : Table("banners") {
    val id = long("id").autoIncrement()
    val campaignId = long("campaign_id")
    val name = varchar("name", 200)
    val bannerTypeId = long("banner_type_id")
    val contentJson = text("content_json")
    override val primaryKey = PrimaryKey(id)
}

object ContentsTable : Table("contents") {
    val id = long("id").autoIncrement()
    val campaignId = long("campaign_id")
    val name = varchar("name", 200)
    val contentType = varchar("content_type", 32)
    val contentJson = text("content_json")
    override val primaryKey = PrimaryKey(id)
}

object CreativesTable : Table("creatives") {
    val id = long("id").autoIncrement()
    val campaignId = long("campaign_id")
    val name = varchar("name", 200)
    val bannerId = long("banner_id")
    val contentId = long("content_id").nullable()
    val channelCode = varchar("channel_code", 32)
    override val primaryKey = PrimaryKey(id)
}

object AuditTable : Table("audit") {
    val id = long("id").autoIncrement()
    val campaignId = long("campaign_id").nullable()
    val entity = varchar("entity", 32)
    val entityId = long("entity_id")
    val action = varchar("action", 32)
    val changes = text("changes").nullable()
    val at = varchar("at", 32)
    val actor = varchar("actor", 128)
    val comment = text("comment").nullable()
    override val primaryKey = PrimaryKey(id)
}

object SessionsTable : Table("sessions") {
    val id = varchar("id", 64)
    val draftJson = text("draft_json")
    val createdAt = varchar("created_at", 40)
    override val primaryKey = PrimaryKey(id)
}

object MessagesTable : Table("messages") {
    val id = long("id").autoIncrement()
    val sessionId = varchar("session_id", 64)
    val role = varchar("role", 16)
    val text = text("text")
    val blocksJson = text("blocks_json").nullable()
    val createdAt = varchar("created_at", 40)
    override val primaryKey = PrimaryKey(id)
}

object JobsTable : Table("jobs") {
    val id = varchar("id", 64)
    val sessionId = varchar("session_id", 64)
    val status = varchar("status", 16)
    val resultJson = text("result_json").nullable()
    val error = text("error").nullable()
    val createdAt = varchar("created_at", 40)
    override val primaryKey = PrimaryKey(id)
}
