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
