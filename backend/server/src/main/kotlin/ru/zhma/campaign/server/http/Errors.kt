package ru.zhma.campaign.server.http

import io.ktor.http.HttpStatusCode
import kotlinx.serialization.Serializable

@Serializable
data class ApiError(val code: String, val message: String, val field: String? = null)

@Serializable
data class ErrorResponse(val errors: List<ApiError>)

class ApiException(
    val status: HttpStatusCode,
    val errors: List<ApiError>,
) : RuntimeException(errors.joinToString("; ") { it.message })

fun notFound(message: String = "Сущность не найдена"): ApiException =
    ApiException(HttpStatusCode.NotFound, listOf(ApiError("ENTITY_NOT_FOUND", message, null)))

fun roleForbidden(): ApiException = ApiException(
    HttpStatusCode.BadRequest,
    listOf(ApiError("ROLE_FORBIDDEN", "Действие недоступно для этой роли", null)),
)

fun bad(vararg errors: ApiError): ApiException = ApiException(HttpStatusCode.BadRequest, errors.toList())

fun bad(errors: List<ApiError>): ApiException = ApiException(HttpStatusCode.BadRequest, errors)
