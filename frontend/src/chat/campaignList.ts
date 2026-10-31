import { asCampaignDraft } from "../api/guards";
import type { CampaignListItem, DraftCampaign, IncomingBlock } from "../api/types";

export const SCREEN_TITLE = "Сборка кампании";
export const CURRENT_DRAFT_ID = 0;

export function draftFromBlocks(blocks: IncomingBlock[] | undefined): DraftCampaign | null {
  if (!blocks) return null;
  for (let index = blocks.length - 1; index >= 0; index -= 1) {
    const block = asCampaignDraft(blocks[index]);
    if (block) return block.draft;
  }
  return null;
}

export function withCurrentDraft(
  list: CampaignListItem[],
  draft: DraftCampaign | null | undefined,
): CampaignListItem[] {
  const saved = list.filter((item) => item.id !== CURRENT_DRAFT_ID);
  const name = draft?.name?.trim();
  if (!name || !draft) return saved;
  const savedId = draft.savedCampaignId;
  if (typeof savedId === "number" && saved.some((item) => item.id === savedId)) return saved;
  if (saved.some((item) => item.name === name)) return saved;
  return [
    {
      id: typeof savedId === "number" ? savedId : CURRENT_DRAFT_ID,
      name,
      state: "DRAFT",
      published: false,
      topic: draft.topic ?? null,
      startDate: draft.startDate?.trim() ? draft.startDate : "",
      endDate: draft.endDate ?? null,
    },
    ...saved,
  ];
}
