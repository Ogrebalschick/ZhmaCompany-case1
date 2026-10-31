package ru.zhma.campaign.ai.agent

import ru.zhma.campaign.ai.model.DraftCampaign
import ru.zhma.campaign.ai.model.DraftPatch

object DraftMerger {
    fun apply(
        draft: DraftCampaign,
        patch: DraftPatch,
    ): DraftCampaign {
        val merged =
            draft.copy(
                name = patch.name ?: draft.name,
                marketingName = patch.marketingName ?: draft.marketingName,
                startDate = patch.startDate ?: draft.startDate,
                startTime = patch.startTime ?: draft.startTime,
                endDate = patch.endDate ?: draft.endDate,
                endTime = patch.endTime ?: draft.endTime,
                topic = patch.topic ?: draft.topic,
                tagIds = patch.tagIds ?: draft.tagIds,
                keywords = patch.keywords ?: draft.keywords,
                audienceType = patch.audienceType ?: draft.audienceType,
                includedSegments = patch.includedSegments ?: draft.includedSegments,
                excludedSegments = patch.excludedSegments ?: draft.excludedSegments,
                placeIds = patch.placeIds ?: draft.placeIds,
                weight = patch.weight ?: draft.weight,
                initialCtr = patch.initialCtr ?: draft.initialCtr,
                creativeBrief = patch.creativeBrief ?: draft.creativeBrief,
            )
        if (patch.audienceType == "ALL_USERS") {
            return merged.copy(includedSegments = emptyList(), excludedSegments = emptyList())
        }
        return merged
    }
}
