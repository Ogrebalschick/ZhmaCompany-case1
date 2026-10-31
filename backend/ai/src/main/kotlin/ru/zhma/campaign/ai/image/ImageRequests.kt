package ru.zhma.campaign.ai.image

/**
 * Асинхронная генерация картинок. Реализует сервер: ставит задачу в очередь и сразу отдаёт id и адрес.
 * Картинка появится по адресу, когда модель её дорисует; статус — `PENDING`, `DONE` или `FAILED`.
 */
interface ImageRequests {
    fun enqueue(
        prompt: String,
        width: Int,
        height: Int,
    ): ImageTicket
}

data class ImageTicket(
    val id: String,
    val url: String,
    val status: String,
)
