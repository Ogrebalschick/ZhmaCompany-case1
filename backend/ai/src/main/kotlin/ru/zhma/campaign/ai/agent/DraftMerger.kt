package ru.zhma.campaign.ai.agent

import ru.zhma.campaign.ai.model.DraftCampaign
import ru.zhma.campaign.ai.model.DraftPatch

object DraftMerger {
    /**
     * Накладывает патч на черновик. Несказанное (null) не затирает заполненное.
     * Списки сегментов, мест и тегов дополняются; убрать можно через `remove*`.
     * Даты здесь не трогаются: их считает [DateParser].
     */
    fun apply(
        draft: DraftCampaign,
        patch: DraftPatch,
    ): DraftCampaign {
        val removeSegments = patch.removeSegments.orEmpty().toSet()
        val removePlaces = patch.removePlaceIds.orEmpty().toSet()
        val included = (draft.includedSegments + patch.includedSegments.orEmpty()).distinct().filter { it !in removeSegments }
        val excluded = (draft.excludedSegments + patch.excludedSegments.orEmpty()).distinct().filter { it !in removeSegments }
        val merged =
            draft.copy(
                name = patch.name?.trim()?.takeIf { it.isNotEmpty() } ?: draft.name,
                marketingName = patch.marketingName ?: draft.marketingName,
                topic = patch.topic ?: draft.topic,
                tagIds = (draft.tagIds + patch.tagIds.orEmpty()).distinct(),
                keywords = patch.keywords ?: draft.keywords,
                audienceType = patch.audienceType ?: draft.audienceType,
                includedSegments = included,
                excludedSegments = excluded,
                placeIds = (draft.placeIds + patch.placeIds.orEmpty()).distinct().filter { it !in removePlaces },
                weight = patch.weight ?: draft.weight,
                initialCtr = patch.initialCtr ?: draft.initialCtr,
                creativeBrief = patch.creativeBrief ?: draft.creativeBrief,
                nameGuess = patch.nameGuess ?: draft.nameGuess,
                topicGuess = patch.topicGuess ?: draft.topicGuess,
            )
        if (patch.audienceType == "ALL_USERS") {
            return merged.copy(includedSegments = emptyList(), excludedSegments = emptyList())
        }
        return merged
    }

    /** Поля кампании, по которым видно, изменилось ли что-то для сохранения. */
    fun essence(draft: DraftCampaign): List<Any?> =
        listOf(
            draft.name,
            draft.marketingName,
            draft.startDate,
            draft.startTime,
            draft.endDate,
            draft.endTime,
            draft.topic,
            draft.tagIds,
            draft.keywords,
            draft.audienceType,
            draft.includedSegments,
            draft.excludedSegments,
            draft.placeIds,
            draft.weight,
            draft.initialCtr,
            draft.banner?.copy(imageStatus = null),
        )
}
