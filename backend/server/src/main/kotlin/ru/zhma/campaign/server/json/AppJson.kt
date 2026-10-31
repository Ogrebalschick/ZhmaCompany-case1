package ru.zhma.campaign.server.json

import kotlinx.serialization.json.Json

val AppJson =
    Json {
        classDiscriminator = "type"
        ignoreUnknownKeys = true
        encodeDefaults = true
        explicitNulls = true
    }
