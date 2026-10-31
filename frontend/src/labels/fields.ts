import type { PlaceChoice, SegmentChoice } from "../api/types";

export const ALL_USERS_HINT = "Списки включённых и исключённых должны быть пустыми.";

export function formatCount(count: number): string {
  return String(Math.trunc(count)).replace(/\B(?=(\d{3})+(?!\d))/g, " ");
}

const SEGMENT_TYPES: Record<string, string> = {
  GEO_CITY: "город",
  GEO_REGION: "регион",
  AGE: "возраст",
  GENDER: "пол",
  INTEREST: "интересы",
  PRODUCT: "клиенты продукта",
  CHANNEL: "канал",
  BASIC: "сегмент",
};

export function segmentReason(choice: SegmentChoice): string {
  const type = SEGMENT_TYPES[choice.type] ?? "сегмент";
  const count = choice.count > 0 ? `${formatCount(choice.count)} клиентов` : "новый сегмент";
  const off = choice.state === "ACTIVE" ? "" : ", выключен";
  return `${choice.name ?? ""}${choice.name ? ": " : ""}${type}, ${count}${off}`;
}

export function segmentReasons(
  choices: SegmentChoice[] | undefined,
  list: "included" | "excluded",
  codes: string[] | undefined,
): string | undefined {
  if (!choices || !codes || codes.length === 0) return undefined;
  const lines = codes.flatMap((code) => {
    const row = choices.find((item) => item.code === code && item.list === list);
    return row ? [segmentReason(row)] : [];
  });
  return lines.length > 0 ? lines.join("\n") : undefined;
}

export function placeReason(choice: PlaceChoice): string {
  const banner =
    choice.defaultBanner === "STORY"
      ? "формат — сторис"
      : choice.defaultBanner
        ? "формат — смарт-баннер"
        : "формат любой";
  return choice.state === "ACTIVE" ? banner : `${banner}, место выключено`;
}

export function placeReasons(
  choices: PlaceChoice[] | undefined,
  ids: number[] | undefined,
): string | undefined {
  if (!choices || !ids || ids.length === 0) return undefined;
  const lines = ids.flatMap((id) => {
    const row = choices.find((item) => item.id === id);
    return row ? [placeReason(row)] : [];
  });
  return lines.length > 0 ? lines.join("\n") : undefined;
}

export function orDash(value: string | number | null | undefined): string {
  if (value === null || value === undefined || value === "") return "—";
  return String(value);
}

export function formatSchedule(date?: string | null, time?: string | null): string {
  if (!date && !time) return "—";
  const human = date?.match(/^(\d{4})-(\d{2})-(\d{2})$/);
  const d = human ? `${human[3]}.${human[2]}.${human[1]}` : date;
  return [d, time].filter(Boolean).join(" в ");
}

export function formatMetric(value: number | null | undefined): string {
  if (value === null || value === undefined) return "—";
  return String(value);
}

export function weightHint(
  weight: number | null | undefined,
  ctr: number | null | undefined,
): string | undefined {
  if (weight != null) return undefined;
  if (ctr == null) return "Если не задать, при сохранении сервер поставит вес 0 и CTR 15.";
  return "Если не задать, при сохранении сервер поставит 0.";
}

export function ctrHint(
  weight: number | null | undefined,
  ctr: number | null | undefined,
): string | undefined {
  if (ctr != null || weight == null) return undefined;
  return "Если не задать, при сохранении сервер поставит 15.";
}

export function formatCampaignId(id: number | null | undefined): string {
  if (id === null || id === undefined) return "ещё не сохранена";
  return String(id);
}

const MISSING_LABELS: Record<string, string> = {
  name: "название",
  startDate: "дата начала",
  startTime: "время начала",
  topic: "тема",
  audienceType: "тип аудитории",
  places: "места показа",
  segments: "сегменты",
};

export function missingLabel(code: string): string {
  return MISSING_LABELS[code] ?? code;
}

export function audienceLabel(value: string | null | undefined): string {
  if (!value) return "—";
  if (value === "ALL_USERS") return "Все пользователи";
  if (value === "SEGMENTED") return "Выбранные сегменты";
  return value;
}

export function bannerTypeLabel(code: string): string {
  if (code === "STORY") return "Сторис";
  if (code === "SMART_BANNER") return "Смарт-баннер";
  return code;
}
