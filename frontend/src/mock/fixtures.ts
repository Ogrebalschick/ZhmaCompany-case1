import type { AssistantReply, BannerDraft, DraftCampaign, EntityProposal, IncomingBlock } from "../api/types";

export const MOCK_SESSION_ID = "mock-session";

export const AGE_46_REPLY = "AGE_46_60";
export const AGE_26_REPLY = "AGE_26_45";
export const TOPIC_LIFESTYLE_REPLY = "LIFESTYLE";
export const TOPIC_EVENT_REPLY = "EVENT";

export function isDepositMoscowBrief(text: string): boolean {
  return text.includes("вклад") && text.includes("москв");
}

export function isOwnBrief(text: string): boolean {
  const leisure = text.includes("аквапарк") || text.includes("баня");
  const women = text.includes("женщин") || text.includes("старше 35");
  return leisure && women;
}

export function ownBriefDraft(): DraftCampaign {
  return {
    name: "Баня и аквапарк",
    marketingName: "Баня и аквапарк",
    startDate: null,
    startTime: null,
    endDate: null,
    endTime: null,
    topic: null,
    tagIds: [],
    keywords: null,
    audienceType: "SEGMENTED",
    includedSegments: ["FEMALE", "CITY_MSK"],
    excludedSegments: [],
    placeIds: [1],
    weight: null,
    initialCtr: null,
    creativeBrief: null,
    banner: null,
    pendingProposal: null,
    savedCampaignId: null,
  };
}

export function ownMissing(draft: DraftCampaign): string[] {
  const segments = draft.includedSegments ?? [];
  const missing: string[] = [];
  if (!draft.startDate) missing.push("startDate");
  if (!draft.startTime) missing.push("startTime");
  if (!draft.topic) missing.push("topic");
  if (!segments.includes("AGE_26_45") && !segments.includes("AGE_46_60")) missing.push("segments");
  return missing;
}

export function ageCodeFromText(text: string): "AGE_46_60" | "AGE_26_45" | null {
  if (text.includes("age_46_60") || text.includes("46–60") || text.includes("46-60")) return "AGE_46_60";
  if (text.includes("age_26_45") || text.includes("26–45") || text.includes("26-45")) return "AGE_26_45";
  return null;
}

export function topicCodeFromText(text: string): "LIFESTYLE" | "EVENT" | null {
  if (text.includes("lifestyle") || text.includes("образ жизни")) return "LIFESTYLE";
  if (text.includes("event") || text.includes("событ")) return "EVENT";
  return null;
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
