import type { AssistantReply, BannerDraft, DraftCampaign, EntityProposal, IncomingBlock } from "../api/types";

export const MOCK_SESSION_ID = "mock-session";

export function isDepositMoscowBrief(text: string): boolean {
  return text.includes("вклад") && text.includes("москв");
}

export function incompleteDraft(): DraftCampaign {
  return {
    name: "Вклад для Москвы",
    marketingName: "Вклад",
    startDate: "2026-11-05",
    startTime: null,
    endDate: "2026-12-31",
    endTime: "23:59",
    topic: "SALES",
    tagIds: [],
    keywords: null,
    audienceType: "SEGMENTED",
    includedSegments: ["CITY_MSK", "AGE_26_45"],
    excludedSegments: ["EXC_UNSUBSCRIBED"],
    placeIds: [1],
    weight: null,
    initialCtr: null,
    creativeBrief: null,
    banner: null,
    pendingProposal: null,
    savedCampaignId: null,
  };
}

export function readyDraft(): DraftCampaign {
  return { ...incompleteDraft(), startTime: "10:00" };
}

export function savedDraft(): DraftCampaign {
  return { ...readyDraft(), savedCampaignId: 201 };
}

export function draftBlock(draft: DraftCampaign, missing: string[], readyToSave: boolean): IncomingBlock {
  return { type: "campaign_draft", draft, missing, readyToSave };
}

export function storyBanner(): BannerDraft {
  return {
    bannerType: "STORY",
    channel: "ANDROID",
    title: "Вклад для Москвы",
    text: "Откройте вклад в приложении и смотрите условия в карточке продукта.",
    actionType: "default",
    link: null,
    cta: "Подробнее",
  };
}

export function smartBanner(): BannerDraft {
  return {
    bannerType: "SMART_BANNER",
    channel: "WEB",
    title: "Вклад без визита в офис",
    text: "Условия и срок — в карточке продукта. Оформите, когда будете готовы.",
    actionType: "default",
    link: null,
    cta: "Смотреть",
  };
}

export function tagProposal(): EntityProposal {
  return { kind: "TAG", name: "Вклад", code: null, description: "Продуктовый тег для кампаний по вкладам" };
}

export function placeProposal(): EntityProposal {
  return {
    kind: "PLACE",
    name: "Экран накоплений",
    code: "savingsScreen",
    description: "Промо на экране накоплений",
  };
}

export function campaignListReply(): AssistantReply {
  return {
    text: "Вот кампании, которые уже есть.",
    blocks: [
      {
        type: "campaign_list",
        items: [
          {
            id: 201,
            name: "Вклад для Москвы",
            state: "DRAFT",
            published: false,
            topic: "SALES",
            startDate: "2026-11-05",
            endDate: "2026-12-31",
          },
          {
            id: 318,
            name: "Премиальная карта",
            state: "ACTIVE",
            published: true,
            topic: "SALES",
            startDate: "2026-10-01",
            endDate: null,
          },
        ],
      },
    ],
  };
}

export function warningsReply(): AssistantReply {
  return {
    text: "Есть предупреждения. Они не мешают продолжать диалог.",
    blocks: [{ type: "warnings", items: ["У кампании нет тегов", "Место показа выключено"] }],
  };
}
