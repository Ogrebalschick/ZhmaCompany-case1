import type { PlaceChoice, SegmentChoice } from "../api/types";

export const ALL_USERS_HINT = "Списки включённых и исключённых должны быть пустыми.";

export function formatCount(count: number): string {
  return String(Math.trunc(count)).replace(/\B(?=(\d{3})+(?!\d))/g, " ");
}

export function segmentReason(choice: SegmentChoice): string {
  return `${choice.type}, ${formatCount(choice.count)}, ${choice.state}`;
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
  const banner = choice.defaultBanner
    ? `баннер по умолчанию ${choice.defaultBanner}`
    : "баннера по умолчанию нет";
  return `${choice.state}, ${banner}`;
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
  return [date, time].filter(Boolean).join(" ");
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
  if (value === "ALL_USERS") return "Все пользователи (ALL_USERS)";
  if (value === "SEGMENTED") return "По сегментам (SEGMENTED)";
  return value;
}

export function bannerTypeLabel(code: string): string {
  if (code === "STORY") return "История (STORY)";
  if (code === "SMART_BANNER") return "Смарт баннер (SMART_BANNER)";
  return code;
}
