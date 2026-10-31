import type {
  AssistantReply,
  BannerDraft,
  CampaignListItem,
  DraftCampaign,
  DraftChoices,
  EntityProposal,
  IncomingBlock,
} from "../api/types";

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
  if (text.includes("age_46_60") || text.includes("46–60") || text.includes("46-60"))
    return "AGE_46_60";
  if (text.includes("age_26_45") || text.includes("26–45") || text.includes("26-45"))
    return "AGE_26_45";
  return null;
}

export function topicCodeFromText(text: string): "LIFESTYLE" | "EVENT" | null {
  if (text.includes("lifestyle") || text.includes("образ жизни")) return "LIFESTYLE";
  if (text.includes("event") || text.includes("событ")) return "EVENT";
  return null;
}

export const WEIGHT_OFFER = "вес 10";

export function utcToday(now = new Date()): string {
  return now.toISOString().slice(0, 10);
}

export function weightFromText(text: string): number | null {
  const match = text.match(/вес\s*(\d+)/);
  return match ? Number(match[1]) : null;
}

export function ctrFromText(text: string): number | null {
  const match = text.match(/ctr\s*(\d+)/);
  return match ? Number(match[1]) : null;
}

export function isWideReach(text: string): boolean {
  return text.includes("широкий охват");
}

function addUtcDays(iso: string, days: number): string {
  const [year, month, day] = iso.split("-").map(Number);
  return new Date(Date.UTC(year, month - 1, day + days)).toISOString().slice(0, 10);
}

export function resolveSchedule(
  text: string,
  today: string,
  current: { startDate?: string | null; endDate?: string | null },
): { startDate: string | null; endDate: string | null; notes: string[]; touched: boolean } {
  const lower = text.toLowerCase();
  const iso = text.match(/\d{4}-\d{2}-\d{2}/);
  const tomorrow = lower.includes("завтра");
  const twoWeeks = /на\s*2\s*недел/.test(lower) || lower.includes("на две недели");
  const yearEnd = lower.includes("до конца года");
  const notes: string[] = [];
  let start = current.startDate ?? null;
  let end = current.endDate ?? null;
  let touched = false;
  if (!iso && !tomorrow && !twoWeeks && !yearEnd)
    return { startDate: start, endDate: end, notes, touched };

  if (iso) {
    if (iso[0] < today) notes.push("Дата начала не раньше сегодня. Её не записал.");
    else {
      start = iso[0];
      touched = true;
    }
  } else if (tomorrow) {
    start = addUtcDays(today, 1);
    touched = true;
  }

  if (twoWeeks) {
    if (!start) start = today;
    end = addUtcDays(start, 14);
    touched = true;
  }

  if (yearEnd) {
    const nextEnd = `${today.slice(0, 4)}-12-31`;
    if (start && nextEnd < start) notes.push("Конец раньше начала. Его не записал.");
    else {
      end = nextEnd;
      touched = true;
    }
  }

  if (start && end && end < start) {
    notes.push("Конец раньше начала. Его не записал.");
    end = current.endDate ?? null;
  }

  return { startDate: start, endDate: end, notes, touched };
}

export function applyServerMetrics(draft: DraftCampaign): DraftCampaign {
  return {
    ...draft,
    weight: draft.weight ?? 0,
    initialCtr: draft.initialCtr ?? 15,
  };
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

export function premiumCardDraft(): DraftCampaign {
  return {
    name: "Премиальная карта",
    marketingName: "Премиальная карта",
    startDate: "2026-10-01",
    startTime: null,
    endDate: null,
    endTime: null,
    topic: "SALES",
    tagIds: [],
    keywords: null,
    audienceType: null,
    includedSegments: [],
    excludedSegments: [],
    placeIds: [],
    weight: null,
    initialCtr: null,
    creativeBrief: null,
    banner: null,
    pendingProposal: null,
    savedCampaignId: 318,
  };
}

export const CAMPAIGN_ROWS: CampaignListItem[] = [
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
];

const SEGMENT_FACTS: Record<string, { type: string; count: number; state: string }> = {
  FEMALE: { type: "GENDER", count: 42_000_000, state: "ACTIVE" },
  CITY_MSK: { type: "GEO_CITY", count: 13_000_000, state: "ACTIVE" },
  AGE_26_45: { type: "AGE", count: 24_000_000, state: "ACTIVE" },
  AGE_46_60: { type: "AGE", count: 18_000_000, state: "ACTIVE" },
  EXC_UNSUBSCRIBED: { type: "BASIC", count: 15_000_000, state: "ACTIVE" },
};

const PLACE_FACTS: Record<number, { state: string; defaultBanner: string | null }> = {
  1: { state: "ACTIVE", defaultBanner: "STORY" },
  4: { state: "INACTIVE", defaultBanner: "STORY" },
  8: { state: "INACTIVE", defaultBanner: "SMART_BANNER" },
};

export const CITY_MSK_REASON = "Москва, GEO_CITY, 13 000 000, ACTIVE";
export const FEMALE_REASON = "Женский пол, GENDER, 42 000 000, ACTIVE";
export const PLACE_MAIN_REASON = "Главный экран, место 1, баннер по умолчанию STORY";
export const INACTIVE_PLACE_WARNING = "Место показа выключено";

export function choicesFor(draft: DraftCampaign): DraftChoices | undefined {
  const segments = [
    ...(draft.includedSegments ?? []).flatMap((code) => {
      const fact = SEGMENT_FACTS[code];
      return fact ? [{ code, list: "included" as const, ...fact }] : [];
    }),
    ...(draft.excludedSegments ?? []).flatMap((code) => {
      const fact = SEGMENT_FACTS[code];
      return fact ? [{ code, list: "excluded" as const, ...fact }] : [];
    }),
  ];
  const places = (draft.placeIds ?? []).flatMap((id) => {
    const fact = PLACE_FACTS[id];
    return fact ? [{ id, ...fact }] : [];
  });
  if (segments.length === 0 && places.length === 0) return undefined;
  return { segments, places };
}

export function draftBlock(
  draft: DraftCampaign,
  missing: string[],
  readyToSave: boolean,
): IncomingBlock {
  const choices = choicesFor(draft);
  return { type: "campaign_draft", draft, missing, readyToSave, ...(choices ? { choices } : {}) };
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
  return {
    kind: "TAG",
    name: "Вклад",
    code: null,
    description: "Продуктовый тег для кампаний по вкладам",
  };
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
  return { text: "Кампании открываются слева.", blocks: [] };
}

export function warningsReply(): AssistantReply {
  return {
    text: "Есть предупреждения. Они не мешают продолжать диалог.",
    blocks: [{ type: "warnings", items: ["У кампании нет тегов", "Место показа выключено"] }],
  };
}
