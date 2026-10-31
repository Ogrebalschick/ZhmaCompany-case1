package ru.zhma.campaign.ai.prompt

object Prompts {
    fun intent(
        today: String,
        catalog: String,
        draftJson: String,
    ): String = "Сегодня $today.\nКаталог:\n$catalog\nЧерновик:\n$draftJson"

    fun askMissing(
        missing: String,
        options: String,
    ): String = "Спроси только про: $missing. Варианты: $options."

    fun bannerCopy(
        product: String,
        audience: String,
        placement: String,
        n: Int,
    ): String = "Баннер x$n. Продукт: $product. Аудитория: $audience. Место: $placement."
}
