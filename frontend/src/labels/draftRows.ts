import type { CampaignDraftBlock } from "../api/types";
import { formatPlaces, formatSegments, formatTags, topicLabel } from "./catalog";
import { audienceLabel, formatCampaignId, formatMetric, formatSchedule, orDash } from "./fields";

export interface DraftRow {
  key: string;
  label: string;
  value: string;
}

const QUIET = new Set(["—", "ещё не сохранена"]);

export function draftRows(block: CampaignDraftBlock): DraftRow[] {
  const draft = block.draft;
  return [
    { key: "name", label: "Название", value: orDash(draft.name) },
    { key: "marketingName", label: "Маркетинговое имя", value: orDash(draft.marketingName) },
    { key: "start", label: "Начало", value: formatSchedule(draft.startDate, draft.startTime) },
    { key: "end", label: "Окончание", value: formatSchedule(draft.endDate, draft.endTime) },
    { key: "topic", label: "Тема", value: draft.topic ? topicLabel(draft.topic) : "—" },
    { key: "audience", label: "Аудитория", value: audienceLabel(draft.audienceType) },
    { key: "included", label: "Сегменты включения", value: formatSegments(draft.includedSegments) },
    {
      key: "excluded",
      label: "Сегменты исключения",
      value: formatSegments(draft.excludedSegments),
    },
    { key: "places", label: "Места показа", value: formatPlaces(draft.placeIds) },
    { key: "tags", label: "Теги", value: formatTags(draft.tagIds) },
    { key: "weight", label: "Вес", value: formatMetric(draft.weight) },
    { key: "ctr", label: "Начальный CTR", value: formatMetric(draft.initialCtr) },
    { key: "id", label: "ID кампании", value: formatCampaignId(draft.savedCampaignId) },
  ];
}

export function changedFieldKeys(
  current: CampaignDraftBlock,
  previous: CampaignDraftBlock | null,
): string[] {
  const rows = draftRows(current);
  if (!previous) return rows.filter((row) => !QUIET.has(row.value)).map((row) => row.key);
  const before = new Map(draftRows(previous).map((row) => [row.key, row.value]));
  return rows.filter((row) => before.get(row.key) !== row.value).map((row) => row.key);
}

export function changedFieldLabels(
  current: CampaignDraftBlock,
  previous: CampaignDraftBlock | null,
): string[] {
  const keys = new Set(changedFieldKeys(current, previous));
  return draftRows(current)
    .filter((row) => keys.has(row.key))
    .map((row) => row.label);
}
