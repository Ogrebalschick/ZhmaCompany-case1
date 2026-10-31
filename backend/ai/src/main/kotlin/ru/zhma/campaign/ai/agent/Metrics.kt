package ru.zhma.campaign.ai.agent

import ru.zhma.campaign.ai.model.DraftCampaign

/** Вес и начальный CTR берутся только из чисел в реплике. Модель их не выдумывает. */
object Metrics {
    data class Measured(
        val draft: DraftCampaign,
        val hint: String?,
    )

    private val weightNumber = Regex("""(?iu)(?:вес|weight)\D{0,15}?(\d+(?:[.,]\d+)?)""")
    private val ctrNumber = Regex("""(?iu)ctr\D{0,10}?(\d+(?:[.,]\d+)?)""")
    private val wideReach = Regex("""(?iu)широк\p{L}*\s+охват|максимальн\p{L}*\s+охват""")

    fun ground(
        before: DraftCampaign,
        merged: DraftCampaign,
        userText: String,
    ): Measured {
        val weight =
            weightNumber
                .find(userText)
                ?.groupValues
                ?.get(1)
                ?.replace(',', '.')
                ?.toDoubleOrNull()
        val ctr =
            ctrNumber
                .find(userText)
                ?.groupValues
                ?.get(1)
                ?.replace(',', '.')
                ?.toDoubleOrNull()
        val hint =
            if (weight == null && wideReach.containsMatchIn(userText)) {
                "Для широкого охвата могу поставить вес 10. Назовите число, если согласны."
            } else {
                null
            }
        return Measured(
            merged.copy(weight = weight ?: before.weight, initialCtr = ctr ?: before.initialCtr),
            hint,
        )
    }
}
