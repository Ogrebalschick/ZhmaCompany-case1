import type { CSSProperties } from "react";

const LIMITS: Record<string, { title: number; text: number }> = {
  STORY: { title: 100, text: 500 },
  SMART_BANNER: { title: 250, text: 1000 },
};

export function bannerLimits(bannerType: string): { title: number; text: number } | null {
  return LIMITS[bannerType] ?? null;
}

export function lengthLine(
  label: string,
  value: string | null | undefined,
  limit: number,
): { text: string; over: boolean } {
  const left = limit - (value?.length ?? 0);
  if (left < 0) return { text: `${label} длиннее лимита на ${-left}`, over: true };
  return { text: `${label}: осталось ${left} из ${limit}`, over: false };
}

export function linkRule(
  actionType: string | null | undefined,
  link: string | null | undefined,
): { text: string; warn: boolean } | null {
  const hasLink = Boolean(link?.trim());
  if (actionType === "externalLink" || actionType === "openSmartLink") {
    return { text: "Ссылка нужна", warn: !hasLink };
  }
  if (actionType === "closeCampaign" || actionType === "doneCampaign") {
    return { text: "Ссылка запрещена", warn: hasLink };
  }
  if (actionType === "default") return { text: "Ссылка не нужна", warn: false };
  return null;
}

export function plateStyle(url: string | null | undefined): CSSProperties | undefined {
  const trimmed = url?.trim();
  if (!trimmed) return undefined;
  const safe = trimmed
    .replace(/\\/g, "\\\\")
    .replace(/"/g, '\\"')
    .replace(/[\n\r]/g, "");
  return {
    backgroundImage: `linear-gradient(180deg, rgba(11, 24, 32, 0.28), rgba(11, 24, 32, 0.5)), url("${safe}")`,
    backgroundSize: "cover",
    backgroundPosition: "center",
  };
}
