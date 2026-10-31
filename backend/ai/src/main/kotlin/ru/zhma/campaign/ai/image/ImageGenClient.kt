package ru.zhma.campaign.ai.image

interface ImageGenClient {
    suspend fun generate(
        prompt: String,
        w: Int,
        h: Int,
    ): ByteArray
}
