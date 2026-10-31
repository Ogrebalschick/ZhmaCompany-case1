package ru.zhma.campaign.server.http.dto

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class AuditEntryDto(
    val entity: String,
    val entityId: Long,
    val action: String,
    val changes: JsonElement? = null,
    val at: String,
    val actor: String,
    val comment: String? = null,
)
