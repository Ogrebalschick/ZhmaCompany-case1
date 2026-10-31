package ru.zhma.campaign.server.service

import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.update
import ru.zhma.campaign.ai.model.AssistantReply
import ru.zhma.campaign.ai.model.BannerDraft
import ru.zhma.campaign.ai.model.BannerTypeRef
import ru.zhma.campaign.ai.model.CampaignListItem
import ru.zhma.campaign.ai.model.CatalogSnapshot
import ru.zhma.campaign.ai.model.ChannelRef
import ru.zhma.campaign.ai.model.DraftCampaign
import ru.zhma.campaign.ai.model.PlaceRef
import ru.zhma.campaign.ai.model.SegmentRef
import ru.zhma.campaign.ai.model.TagRef
import ru.zhma.campaign.ai.model.TopicRef
import ru.zhma.campaign.ai.model.UiBlock
import ru.zhma.campaign.server.db.AuditTable
import ru.zhma.campaign.server.db.BannerTypesTable
import ru.zhma.campaign.server.db.BannersTable
import ru.zhma.campaign.server.db.CampaignPlacesTable
import ru.zhma.campaign.server.db.CampaignSegmentsTable
import ru.zhma.campaign.server.db.CampaignTagsTable
import ru.zhma.campaign.server.db.CampaignsTable
import ru.zhma.campaign.server.db.ChannelsTable
import ru.zhma.campaign.server.db.ContentsTable
import ru.zhma.campaign.server.db.CreativesTable
import ru.zhma.campaign.server.db.JobsTable
import ru.zhma.campaign.server.db.MessagesTable
import ru.zhma.campaign.server.db.PlacesTable
import ru.zhma.campaign.server.db.SegmentsTable
import ru.zhma.campaign.server.db.SessionsTable
import ru.zhma.campaign.server.db.TagsTable
import ru.zhma.campaign.server.db.TopicsTable
import ru.zhma.campaign.server.http.ApiError
import ru.zhma.campaign.server.http.AuditEntryDto
import ru.zhma.campaign.server.http.BannerButtonJson
import ru.zhma.campaign.server.http.BannerContentJson
import ru.zhma.campaign.server.http.BannerDto
import ru.zhma.campaign.server.http.BannerTypeDto
import ru.zhma.campaign.server.http.Caller
import ru.zhma.campaign.server.http.CampaignDetailsDto
import ru.zhma.campaign.server.http.CampaignStateDto
import ru.zhma.campaign.server.http.ChannelDto
import ru.zhma.campaign.server.http.ContentDto
import ru.zhma.campaign.server.http.CreativeDto
import ru.zhma.campaign.server.http.MessageDto
import ru.zhma.campaign.server.http.SegmentsDto
import ru.zhma.campaign.server.http.WarningsResponse
import ru.zhma.campaign.server.http.bad
import ru.zhma.campaign.server.http.notFound
import ru.zhma.campaign.server.http.roleForbidden
import ru.zhma.campaign.server.json.AppJson
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

private val auditTime = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss")
private val linkActions = setOf("externalLink", "openSmartLink")

fun auditNow(): String = LocalDateTime.now(ZoneOffset.UTC).format(auditTime)

fun loadSnapshot(): CatalogSnapshot = CatalogSnapshot(
    topics = TopicsTable.selectAll().orderBy(TopicsTable.value).map {
        TopicRef(it[TopicsTable.value], it[TopicsTable.label])
    },
    tags = TagsTable.selectAll().orderBy(TagsTable.id).map { TagRef(it[TagsTable.id], it[TagsTable.name]) },
    segments = SegmentsTable.selectAll().orderBy(SegmentsTable.code).map {
        SegmentRef(it[SegmentsTable.code], it[SegmentsTable.name], it[SegmentsTable.type], it[SegmentsTable.state], it[SegmentsTable.count])
    },
    places = PlacesTable.selectAll().orderBy(PlacesTable.id).map {
        PlaceRef(it[PlacesTable.id], it[PlacesTable.code], it[PlacesTable.description], it[PlacesTable.defaultBanner], it[PlacesTable.state])
    },
    bannerTypes = BannerTypesTable.selectAll().orderBy(BannerTypesTable.id).map {
        BannerTypeRef(it[BannerTypesTable.code], it[BannerTypesTable.titleMax], it[BannerTypesTable.textMax], splitChannels(it[BannerTypesTable.channels]))
    },
    channels = ChannelsTable.selectAll().orderBy(ChannelsTable.id).map {
        ChannelRef(it[ChannelsTable.code], it[ChannelsTable.description])
    },
)

fun loadLiveCatalog(): LiveCatalog {
    val names = CampaignsTable.selectAll().associate { it[CampaignsTable.name] to it[CampaignsTable.id] }
    return LiveCatalog(
        topics = TopicsTable.selectAll().map { it[TopicsTable.value] }.toSet(),
        tagIds = TagsTable.selectAll().map { it[TagsTable.id] }.toSet(),
        segmentStates = SegmentsTable.selectAll().associate { it[SegmentsTable.code] to it[SegmentsTable.state] },
        places = PlacesTable.selectAll().associate {
            it[PlacesTable.id] to PlaceInfo(it[PlacesTable.id], it[PlacesTable.code], it[PlacesTable.defaultBanner], it[PlacesTable.state])
        },
        bannerTypes = BannerTypesTable.selectAll().associate {
            it[BannerTypesTable.code] to BannerTypeInfo(
                it[BannerTypesTable.id],
                it[BannerTypesTable.titleMax],
                it[BannerTypesTable.textMax],
                splitChannels(it[BannerTypesTable.channels]).toSet(),
            )
        },
        channels = ChannelsTable.selectAll().map { it[ChannelsTable.code] }.toSet(),
    ).also { cachedNames = names }
}

private var cachedNames: Map<String, Long> = emptyMap()

fun listTopics() = TopicsTable.selectAll().orderBy(TopicsTable.value).map { TopicRef(it[TopicsTable.value], it[TopicsTable.label]) }

fun listTags() = TagsTable.selectAll().orderBy(TagsTable.id).map { TagRef(it[TagsTable.id], it[TagsTable.name]) }

fun listSegments() = SegmentsTable.selectAll().orderBy(SegmentsTable.code).map {
    SegmentRef(it[SegmentsTable.code], it[SegmentsTable.name], it[SegmentsTable.type], it[SegmentsTable.state], it[SegmentsTable.count])
}

fun listPlaces() = PlacesTable.selectAll().orderBy(PlacesTable.id).map {
    PlaceRef(it[PlacesTable.id], it[PlacesTable.code], it[PlacesTable.description], it[PlacesTable.defaultBanner], it[PlacesTable.state])
}

fun listBannerTypes() = BannerTypesTable.selectAll().orderBy(BannerTypesTable.id).map {
    BannerTypeDto(
        it[BannerTypesTable.id],
        it[BannerTypesTable.code],
        it[BannerTypesTable.description],
        it[BannerTypesTable.previewForm],
        it[BannerTypesTable.titleMax],
        it[BannerTypesTable.textMax],
        splitChannels(it[BannerTypesTable.channels]),
    )
}

fun listChannels() = ChannelsTable.selectAll().orderBy(ChannelsTable.id).map {
    ChannelDto(it[ChannelsTable.id], it[ChannelsTable.code], it[ChannelsTable.description])
}

fun listCampaignItems(): List<CampaignListItem> = CampaignsTable.selectAll().orderBy(CampaignsTable.id).map { it.toListItem() }

fun campaignDetails(id: Long): CampaignDetailsDto {
    val row = campaignRow(id)
    val tags = CampaignTagsTable.selectAll().where { CampaignTagsTable.campaignId eq id }.map { it[CampaignTagsTable.tagId] }
    val places = CampaignPlacesTable.selectAll().where { CampaignPlacesTable.campaignId eq id }.map { it[CampaignPlacesTable.placeId] }
    val included = mutableListOf<String>()
    val excluded = mutableListOf<String>()
    CampaignSegmentsTable.selectAll().where { CampaignSegmentsTable.campaignId eq id }.forEach {
        if (it[CampaignSegmentsTable.included]) included += it[CampaignSegmentsTable.code] else excluded += it[CampaignSegmentsTable.code]
    }
    val banners = BannersTable.selectAll().where { BannersTable.campaignId eq id }.map {
        BannerDto(
            it[BannersTable.id],
            it[BannersTable.campaignId],
            it[BannersTable.name],
            it[BannersTable.bannerTypeId],
            AppJson.parseToJsonElement(it[BannersTable.contentJson]),
        )
    }
    val contents = ContentsTable.selectAll().where { ContentsTable.campaignId eq id }.map {
        ContentDto(
            it[ContentsTable.id],
            it[ContentsTable.campaignId],
            it[ContentsTable.name],
            it[ContentsTable.contentType],
            AppJson.parseToJsonElement(it[ContentsTable.contentJson]),
        )
    }
    val creatives = CreativesTable.selectAll().where { CreativesTable.campaignId eq id }.map {
        CreativeDto(
            it[CreativesTable.id],
            it[CreativesTable.campaignId],
            it[CreativesTable.name],
            it[CreativesTable.bannerId],
            it[CreativesTable.contentId],
            it[CreativesTable.channelCode],
        )
    }
    return CampaignDetailsDto(
        id = row[CampaignsTable.id],
        name = row[CampaignsTable.name],
        marketingName = row[CampaignsTable.marketingName],
        startDate = row[CampaignsTable.startDate],
        startTime = row[CampaignsTable.startTime],
        endDate = row[CampaignsTable.endDate],
        endTime = row[CampaignsTable.endTime],
        topic = row[CampaignsTable.topic],
        tags = tags,
        keywords = row[CampaignsTable.keywords],
        audienceType = row[CampaignsTable.audienceType],
        weight = row[CampaignsTable.weight],
        initialCtr = row[CampaignsTable.initialCtr],
        state = row[CampaignsTable.state],
        published = row[CampaignsTable.published],
        author = row[CampaignsTable.author],
        lastEditAuthor = row[CampaignsTable.lastEditAuthor],
        places = places,
        segments = SegmentsDto(included, excluded),
        banners = banners,
        contents = contents,
        creatives = creatives,
    )
}

fun warningsOf(id: Long): WarningsResponse {
    campaignRow(id)
    val input = storedInput(id)
    val catalog = loadLiveCatalog()
    val checked = checkCampaign(input, catalog)
    val errors = checked.missing + checked.errors
    val placeInfos = input.placeIds.mapNotNull { catalog.places[it] }
    val typeCodes = creativeTypeCodes(id)
    return WarningsResponse(errors, warningLines(input.tagIds, placeInfos, typeCodes))
}

fun auditOf(id: Long): List<AuditEntryDto> {
    campaignRow(id)
    return AuditTable.selectAll().where { AuditTable.campaignId eq id }.orderBy(AuditTable.id).map {
        AuditEntryDto(
            entity = it[AuditTable.entity],
            entityId = it[AuditTable.entityId],
            action = it[AuditTable.action],
            changes = it[AuditTable.changes]?.let { json -> AppJson.parseToJsonElement(json) },
            at = it[AuditTable.at],
            actor = it[AuditTable.actor],
            comment = it[AuditTable.comment],
        )
    }
}

fun stateOf(id: Long): CampaignStateDto {
    val row = campaignRow(id)
    return CampaignStateDto(row[CampaignsTable.id], row[CampaignsTable.state], row[CampaignsTable.published])
}

fun applyCommit(draft: DraftCampaign, action: String, caller: Caller): DraftCampaign {
    if (caller.role != "campaignManager") throw roleForbidden()
    return when (action) {
        "CREATE_TAG" -> createTag(draft, caller.user)
        "CREATE_PLACE" -> createPlace(draft, caller.user)
        "SAVE_CAMPAIGN" -> saveCampaign(draft, caller.user)
        "TO_MODERATION" -> {
            val id = draft.savedCampaignId ?: throw bad(ApiError("savedCampaignId", "Сначала сохраните кампанию", "savedCampaignId"))
            moderate(id, caller.user)
            draft
        }
        else -> throw bad(ApiError("action", "Неизвестное действие", "action"))
    }
}

fun confirmDraft(sessionId: String, pathId: Long, caller: Caller): CampaignDetailsDto {
    if (caller.role != "campaignManager") throw roleForbidden()
    val row = SessionsTable.selectAll().where { SessionsTable.id eq sessionId }.singleOrNull() ?: throw notFound("Сессия не найдена")
    val draft = AppJson.decodeFromString(DraftCampaign.serializer(), row[SessionsTable.draftJson])
    if (draft.savedCampaignId != null && draft.savedCampaignId != pathId) throw notFound("Кампания не найдена")
    val saved = saveCampaign(draft, caller.user)
    SessionsTable.update({ SessionsTable.id eq sessionId }) {
        it[draftJson] = AppJson.encodeToString(DraftCampaign.serializer(), saved)
    }
    return campaignDetails(saved.savedCampaignId!!)
}

fun moderate(id: Long, actor: String) {
    val row = campaignRow(id)
    val state = row[CampaignsTable.state]
    if (state != "DRAFT" && state != "NEEDS_WORK") {
        throw bad(ApiError("TO_MODERATION_NOT_ALLOWED", "Отправка на модерацию из состояния $state недоступна", "state"))
    }
    val checked = checkCampaign(storedInput(id), loadLiveCatalog())
    if (checked.missing.isNotEmpty()) throw ru.zhma.campaign.server.http.ApiException(io.ktor.http.HttpStatusCode.NotFound, checked.missing)
    if (checked.errors.isNotEmpty()) throw bad(checked.errors)
    CampaignsTable.update({ CampaignsTable.id eq id }) {
        it[CampaignsTable.state] = "ON_MODERATION"
        it[lastEditAuthor] = actor
    }
    writeAudit(id, "CAMPAIGN", id, "TO_MODERATION", null, actor, null)
}

fun approve(id: Long, actor: String) {
    val row = campaignRow(id)
    if (row[CampaignsTable.state] != "ON_MODERATION") {
        throw bad(ApiError("APPROVE_NOT_ALLOWED", "Одобрение доступно только из модерации", "state"))
    }
    CampaignsTable.update({ CampaignsTable.id eq id }) {
        it[state] = "APPROVED"
        it[lastEditAuthor] = actor
    }
    writeAudit(id, "CAMPAIGN", id, "APPROVE", null, actor, null)
}

fun decline(id: Long, actor: String, comment: String?) {
    if (comment.isNullOrBlank()) throw bad(ApiError("comment", "Комментарий обязателен", "comment"))
    val row = campaignRow(id)
    if (row[CampaignsTable.state] != "ON_MODERATION") {
        throw bad(ApiError("DECLINE_NOT_ALLOWED", "Отклонение доступно только из модерации", "state"))
    }
    CampaignsTable.update({ CampaignsTable.id eq id }) {
        it[state] = "NEEDS_WORK"
        it[lastEditAuthor] = actor
    }
    writeAudit(id, "CAMPAIGN", id, "DECLINE", null, actor, comment.trim())
}

fun activate(id: Long, actor: String) {
    val row = campaignRow(id)
    if (row[CampaignsTable.state] != "APPROVED") {
        throw bad(ApiError("ACTIVATE_NOT_ALLOWED", "Активация доступна только из состояния APPROVED", "state"))
    }
    CampaignsTable.update({ CampaignsTable.id eq id }) {
        it[state] = "ACTIVE"
        it[published] = true
        it[lastEditAuthor] = actor
    }
    writeAudit(id, "CAMPAIGN", id, "ACTIVATE", null, actor, null)
}

fun createSession(id: String) {
    SessionsTable.insert {
        it[SessionsTable.id] = id
        it[draftJson] = AppJson.encodeToString(DraftCampaign.serializer(), DraftCampaign())
        it[createdAt] = Instant.now().toString()
    }
}

fun sessionExists(id: String): Boolean = SessionsTable.selectAll().where { SessionsTable.id eq id }.empty().not()

fun loadDraft(sessionId: String): DraftCampaign {
    val row = SessionsTable.selectAll().where { SessionsTable.id eq sessionId }.singleOrNull() ?: throw notFound("Сессия не найдена")
    return AppJson.decodeFromString(DraftCampaign.serializer(), row[SessionsTable.draftJson])
}

fun saveDraft(sessionId: String, draft: DraftCampaign) {
    SessionsTable.update({ SessionsTable.id eq sessionId }) {
        it[draftJson] = AppJson.encodeToString(DraftCampaign.serializer(), draft)
    }
}

fun appendMessage(sessionId: String, role: String, text: String, blocks: List<UiBlock>?) {
    MessagesTable.insert {
        it[MessagesTable.sessionId] = sessionId
        it[MessagesTable.role] = role
        it[MessagesTable.text] = text
        it[blocksJson] = blocks?.let { value -> AppJson.encodeToString(ListSerializer(UiBlock.serializer()), value) }
        it[createdAt] = Instant.now().toString()
    }
}

fun listMessages(sessionId: String): List<MessageDto> {
    if (!sessionExists(sessionId)) throw notFound("Сессия не найдена")
    return MessagesTable.selectAll().where { MessagesTable.sessionId eq sessionId }.orderBy(MessagesTable.id).map {
        MessageDto(
            role = it[MessagesTable.role],
            text = it[MessagesTable.text],
            blocks = it[MessagesTable.blocksJson]?.let { json -> AppJson.parseToJsonElement(json) } ?: JsonArray(emptyList()),
            createdAt = it[MessagesTable.createdAt],
        )
    }
}

fun insertJob(id: String, sessionId: String) {
    JobsTable.insert {
        it[JobsTable.id] = id
        it[JobsTable.sessionId] = sessionId
        it[status] = "PENDING"
        it[createdAt] = Instant.now().toString()
    }
}

fun jobRow(id: String): ResultRow = JobsTable.selectAll().where { JobsTable.id eq id }.singleOrNull() ?: throw notFound("Задача не найдена")

fun finishJob(id: String, reply: AssistantReply) {
    JobsTable.update({ JobsTable.id eq id }) {
        it[status] = "DONE"
        it[resultJson] = AppJson.encodeToString(AssistantReply.serializer(), reply)
        it[error] = null
    }
}

fun failJob(id: String, message: String) {
    if (JobsTable.selectAll().where { JobsTable.id eq id }.empty()) return
    JobsTable.update({ JobsTable.id eq id }) {
        it[status] = "FAILED"
        it[error] = message
    }
}

private fun createTag(draft: DraftCampaign, actor: String): DraftCampaign {
    val proposal = draft.pendingProposal ?: throw bad(ApiError("pendingProposal", "Нет предложения тега", "pendingProposal"))
    if (proposal.kind != "TAG") throw bad(ApiError("kind", "Ожидалось предложение тега", "kind"))
    val name = proposal.name.trim()
    if (name.isEmpty()) throw bad(ApiError("name", "Укажите имя тега", "name"))
    val id = (TagsTable.selectAll().map { it[TagsTable.id] }.maxOrNull() ?: 0L) + 1L
    TagsTable.insert {
        it[TagsTable.id] = id
        it[TagsTable.name] = name
    }
    writeAudit(draft.savedCampaignId, "TAG", id, "CREATE", null, actor, null)
    return draft.copy(tagIds = (draft.tagIds + id).distinct(), pendingProposal = null)
}

private fun createPlace(draft: DraftCampaign, actor: String): DraftCampaign {
    val proposal = draft.pendingProposal ?: throw bad(ApiError("pendingProposal", "Нет предложения места", "pendingProposal"))
    if (proposal.kind != "PLACE") throw bad(ApiError("kind", "Ожидалось предложение места", "kind"))
    val code = proposal.code?.trim().orEmpty()
    val description = proposal.description?.trim().orEmpty()
    if (code.isEmpty()) throw bad(ApiError("code", "Укажите код места", "code"))
    if (description.isEmpty()) throw bad(ApiError("description", "Укажите описание места", "description"))
    val taken = PlacesTable.selectAll().where { PlacesTable.code eq code }.any()
    if (taken) throw bad(ApiError("code", "Место с таким кодом уже есть", "code"))
    val id = (PlacesTable.selectAll().map { it[PlacesTable.id] }.maxOrNull() ?: 0L) + 1L
    PlacesTable.insert {
        it[PlacesTable.id] = id
        it[PlacesTable.code] = code
        it[PlacesTable.description] = description
        it[defaultBanner] = null
        it[state] = "ACTIVE"
    }
    writeAudit(draft.savedCampaignId, "PLACE", id, "CREATE", null, actor, null)
    return draft.copy(placeIds = (draft.placeIds + id).distinct(), pendingProposal = null)
}

private fun saveCampaign(draft: DraftCampaign, actor: String): DraftCampaign {
    val catalog = loadLiveCatalog()
    val input = CheckInput(
        id = draft.savedCampaignId,
        name = draft.name,
        marketingName = draft.marketingName,
        startDate = draft.startDate,
        startTime = draft.startTime,
        endDate = draft.endDate,
        endTime = draft.endTime,
        topic = draft.topic,
        tagIds = draft.tagIds,
        keywords = draft.keywords,
        audienceType = draft.audienceType,
        included = draft.includedSegments,
        excluded = draft.excludedSegments,
        placeIds = draft.placeIds,
        banners = listOfNotNull(draft.banner),
        existingNames = cachedNames,
    )
    val checked = checkCampaign(input, catalog)
    if (checked.missing.isNotEmpty()) throw ru.zhma.campaign.server.http.ApiException(io.ktor.http.HttpStatusCode.NotFound, checked.missing)
    if (checked.errors.isNotEmpty()) throw bad(checked.errors)
    val weight = draft.weight ?: 0.0
    val ctr = draft.initialCtr ?: 15.0
    val id = if (draft.savedCampaignId == null) {
        insertCampaign(draft, actor, weight, ctr)
    } else {
        updateCampaign(draft, actor, weight, ctr)
    }
    replaceLinks(id, draft)
    val banner = draft.banner
    if (banner != null) replaceBanner(id, banner, catalog, actor, draft.savedCampaignId != null)
    return draft.copy(savedCampaignId = id, weight = weight, initialCtr = ctr)
}

private fun insertCampaign(draft: DraftCampaign, actor: String, weight: Double, ctr: Double): Long {
    val id = CampaignsTable.insert {
        it[name] = draft.name!!.trim()
        it[marketingName] = draft.marketingName.blankToNull()
        it[startDate] = draft.startDate!!.trim()
        it[startTime] = draft.startTime!!.trim()
        it[endDate] = draft.endDate.blankToNull()
        it[endTime] = draft.endTime.blankToNull()
        it[topic] = draft.topic!!.trim()
        it[keywords] = draft.keywords.blankToNull()
        it[audienceType] = draft.audienceType!!.trim()
        it[CampaignsTable.weight] = weight
        it[initialCtr] = ctr
        it[state] = "DRAFT"
        it[published] = false
        it[author] = actor
        it[lastEditAuthor] = actor
    } get CampaignsTable.id
    writeAudit(id, "CAMPAIGN", id, "CREATE", null, actor, null)
    writeAudit(id, "SEGMENTS", id, "CREATE", null, actor, null)
    return id
}

private fun updateCampaign(draft: DraftCampaign, actor: String, weight: Double, ctr: Double): Long {
    val id = draft.savedCampaignId!!
    val row = campaignRow(id)
    val next = when (val state = row[CampaignsTable.state]) {
        "DRAFT", "NEEDS_WORK" -> state
        "APPROVED" -> "DRAFT"
        "ACTIVE", "ACTIVE_EDITING" -> "ACTIVE_EDITING"
        else -> throw bad(ApiError("UPDATE_NOT_ALLOWED", "Кампанию в состоянии $state нельзя изменить", "state"))
    }
    val name = draft.name!!.trim()
    val marketing = draft.marketingName.blankToNull()
    val startDate = draft.startDate!!.trim()
    val startTime = draft.startTime!!.trim()
    val endDate = draft.endDate.blankToNull()
    val endTime = draft.endTime.blankToNull()
    val topic = draft.topic!!.trim()
    val keywords = draft.keywords.blankToNull()
    val audience = draft.audienceType!!.trim()
    val changes = headerDiff(
        mapOf(
            "name" to (row[CampaignsTable.name] to name),
            "marketingName" to (row[CampaignsTable.marketingName] to marketing),
            "startDate" to (row[CampaignsTable.startDate] to startDate),
            "startTime" to (row[CampaignsTable.startTime] to startTime),
            "endDate" to (row[CampaignsTable.endDate] to endDate),
            "endTime" to (row[CampaignsTable.endTime] to endTime),
            "topic" to (row[CampaignsTable.topic] to topic),
            "keywords" to (row[CampaignsTable.keywords] to keywords),
            "audienceType" to (row[CampaignsTable.audienceType] to audience),
            "weight" to (row[CampaignsTable.weight].toString() to weight.toString()),
            "initialCtr" to (row[CampaignsTable.initialCtr].toString() to ctr.toString()),
            "state" to (row[CampaignsTable.state] to next),
        ),
    )
    CampaignsTable.update({ CampaignsTable.id eq id }) {
        it[CampaignsTable.name] = name
        it[marketingName] = marketing
        it[CampaignsTable.startDate] = startDate
        it[CampaignsTable.startTime] = startTime
        it[CampaignsTable.endDate] = endDate
        it[CampaignsTable.endTime] = endTime
        it[CampaignsTable.topic] = topic
        it[CampaignsTable.keywords] = keywords
        it[audienceType] = audience
        it[CampaignsTable.weight] = weight
        it[initialCtr] = ctr
        it[state] = next
        it[lastEditAuthor] = actor
    }
    writeAudit(id, "CAMPAIGN", id, "UPDATE", changes, actor, null)
    writeAudit(id, "SEGMENTS", id, "UPDATE", null, actor, null)
    return id
}

private fun replaceLinks(id: Long, draft: DraftCampaign) {
    CampaignTagsTable.deleteWhere { campaignId eq id }
    draft.tagIds.distinct().forEach { tagId ->
        CampaignTagsTable.insert {
            it[campaignId] = id
            it[CampaignTagsTable.tagId] = tagId
        }
    }
    CampaignPlacesTable.deleteWhere { campaignId eq id }
    draft.placeIds.distinct().forEach { placeId ->
        CampaignPlacesTable.insert {
            it[campaignId] = id
            it[CampaignPlacesTable.placeId] = placeId
        }
    }
    CampaignSegmentsTable.deleteWhere { campaignId eq id }
    draft.includedSegments.map { it.trim() }.filter { it.isNotEmpty() }.distinct().forEach { code ->
        CampaignSegmentsTable.insert {
            it[campaignId] = id
            it[CampaignSegmentsTable.code] = code
            it[included] = true
        }
    }
    draft.excludedSegments.map { it.trim() }.filter { it.isNotEmpty() }.distinct().forEach { code ->
        CampaignSegmentsTable.insert {
            it[campaignId] = id
            it[CampaignSegmentsTable.code] = code
            it[included] = false
        }
    }
}

private fun replaceBanner(campaignId: Long, banner: BannerDraft, catalog: LiveCatalog, actor: String, replacing: Boolean) {
    val had = BannersTable.selectAll().where { BannersTable.campaignId eq campaignId }.any()
    CreativesTable.deleteWhere { CreativesTable.campaignId eq campaignId }
    BannersTable.deleteWhere { BannersTable.campaignId eq campaignId }
    val typeId = catalog.bannerTypes.getValue(banner.bannerType).id
    val content = BannerContentJson(
        actionType = banner.actionType,
        title = banner.title,
        text = banner.text,
        link = if (banner.actionType in linkActions) banner.link else null,
        button = banner.cta?.trim()?.takeIf { it.isNotEmpty() }?.let { BannerButtonJson(it) },
    )
    val bannerId = BannersTable.insert {
        it[BannersTable.campaignId] = campaignId
        it[name] = banner.title?.trim()?.takeIf { it.isNotEmpty() } ?: "Баннер"
        it[bannerTypeId] = typeId
        it[contentJson] = AppJson.encodeToString(BannerContentJson.serializer(), content)
    } get BannersTable.id
    CreativesTable.insert {
        it[CreativesTable.campaignId] = campaignId
        it[name] = "Креатив ${banner.channel}"
        it[CreativesTable.bannerId] = bannerId
        it[contentId] = null
        it[channelCode] = banner.channel
    }
    val action = if (replacing && had) "UPDATE" else "CREATE"
    writeAudit(campaignId, "BANNER", bannerId, action, null, actor, null)
    writeAudit(campaignId, "CREATIVE", bannerId, action, null, actor, null)
}

private fun storedInput(id: Long): CheckInput {
    val row = campaignRow(id)
    val tags = CampaignTagsTable.selectAll().where { CampaignTagsTable.campaignId eq id }.map { it[CampaignTagsTable.tagId] }
    val places = CampaignPlacesTable.selectAll().where { CampaignPlacesTable.campaignId eq id }.map { it[CampaignPlacesTable.placeId] }
    val included = mutableListOf<String>()
    val excluded = mutableListOf<String>()
    CampaignSegmentsTable.selectAll().where { CampaignSegmentsTable.campaignId eq id }.forEach {
        if (it[CampaignSegmentsTable.included]) included += it[CampaignSegmentsTable.code] else excluded += it[CampaignSegmentsTable.code]
    }
    return CheckInput(
        id = id,
        name = row[CampaignsTable.name],
        marketingName = row[CampaignsTable.marketingName],
        startDate = row[CampaignsTable.startDate],
        startTime = row[CampaignsTable.startTime],
        endDate = row[CampaignsTable.endDate],
        endTime = row[CampaignsTable.endTime],
        topic = row[CampaignsTable.topic],
        tagIds = tags,
        keywords = row[CampaignsTable.keywords],
        audienceType = row[CampaignsTable.audienceType],
        included = included,
        excluded = excluded,
        placeIds = places,
        banners = storedBanners(id),
        existingNames = CampaignsTable.selectAll().associate { it[CampaignsTable.name] to it[CampaignsTable.id] },
    )
}

private fun storedBanners(campaignId: Long): List<BannerDraft> {
    val typeById = BannerTypesTable.selectAll().associate { it[BannerTypesTable.id] to it[BannerTypesTable.code] }
    val channelByBanner = CreativesTable.selectAll().where { CreativesTable.campaignId eq campaignId }
        .associate { it[CreativesTable.bannerId] to it[CreativesTable.channelCode] }
    return BannersTable.selectAll().where { BannersTable.campaignId eq campaignId }.map { row ->
        val content = AppJson.decodeFromString(BannerContentJson.serializer(), row[BannersTable.contentJson])
        BannerDraft(
            bannerType = typeById[row[BannersTable.bannerTypeId]] ?: "",
            channel = channelByBanner[row[BannersTable.id]] ?: "",
            title = content.title,
            text = content.text,
            actionType = content.actionType,
            link = content.link,
            cta = content.button?.name,
        )
    }
}

private fun creativeTypeCodes(campaignId: Long): List<String> {
    val typeById = BannerTypesTable.selectAll().associate { it[BannerTypesTable.id] to it[BannerTypesTable.code] }
    val bannerType = BannersTable.selectAll().where { BannersTable.campaignId eq campaignId }
        .associate { it[BannersTable.id] to typeById[it[BannersTable.bannerTypeId]] }
    return CreativesTable.selectAll().where { CreativesTable.campaignId eq campaignId }.mapNotNull { bannerType[it[CreativesTable.bannerId]] }
}

private fun campaignRow(id: Long): ResultRow =
    CampaignsTable.selectAll().where { CampaignsTable.id eq id }.singleOrNull() ?: throw notFound("Кампания не найдена")

private fun ResultRow.toListItem() = CampaignListItem(
    id = this[CampaignsTable.id],
    name = this[CampaignsTable.name],
    state = this[CampaignsTable.state],
    published = this[CampaignsTable.published],
    topic = this[CampaignsTable.topic],
    startDate = this[CampaignsTable.startDate],
    endDate = this[CampaignsTable.endDate],
)

private fun writeAudit(campaignId: Long?, entity: String, entityId: Long, action: String, changes: String?, actor: String, comment: String?) {
    AuditTable.insert {
        it[AuditTable.campaignId] = campaignId
        it[AuditTable.entity] = entity
        it[AuditTable.entityId] = entityId
        it[AuditTable.action] = action
        it[AuditTable.changes] = changes
        it[at] = auditNow()
        it[AuditTable.actor] = actor
        it[AuditTable.comment] = comment
    }
}

private fun headerDiff(fields: Map<String, Pair<String?, String?>>): String? {
    val obj = buildJsonObject {
        fields.forEach { (key, pair) ->
            if (pair.first != pair.second) {
                put(key, buildJsonObject {
                    put("from", pair.first?.let { JsonPrimitive(it) } ?: JsonNull)
                    put("to", pair.second?.let { JsonPrimitive(it) } ?: JsonNull)
                })
            }
        }
    }
    return obj.takeIf { it.isNotEmpty() }?.toString()
}

private fun splitChannels(value: String): List<String> = value.split(',').map { it.trim() }.filter { it.isNotEmpty() }

private fun String?.blankToNull(): String? = this?.trim()?.takeIf { it.isNotEmpty() }
