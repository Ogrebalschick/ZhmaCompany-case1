package ru.zhma.campaign.server.http.dto

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import ru.zhma.campaign.ai.model.AssistantReply

@Serializable
data class SessionResponse(val sessionId: String)

@Serializable
data class PostMessageBody(val text: String = "")

@Serializable
data class JobAccepted(val jobId: String)

@Serializable
data class JobStatusResponse(
    val status: String,
    val result: AssistantReply? = null,
    val error: String? = null,
)

@Serializable
data class MessageDto(
    val role: String,
    val text: String,
    val blocks: JsonElement,
    val createdAt: String,
)

@Serializable
data class MessagesResponse(val messages: List<MessageDto>)
