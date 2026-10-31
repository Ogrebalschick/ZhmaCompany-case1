import { asCampaignDraft, asQuickReplies } from "../api/guards";
import type { CampaignDraftBlock, ChatMessage, QuickRepliesBlock } from "../api/types";

export function latestDraftBlock(messages: ChatMessage[]): CampaignDraftBlock | null {
  for (let index = messages.length - 1; index >= 0; index -= 1) {
    const blocks = messages[index].blocks ?? [];
    for (let blockIndex = blocks.length - 1; blockIndex >= 0; blockIndex -= 1) {
      const draft = asCampaignDraft(blocks[blockIndex]);
      if (draft) return draft;
    }
  }
  return null;
}

/** Кнопки показываются только у последнего ответа ассистента и только в панели над полем ввода. */
export function latestQuickReplies(messages: ChatMessage[]): QuickRepliesBlock | null {
  const last = messages[messages.length - 1];
  if (!last || last.role !== "assistant") return null;
  const block = (last.blocks ?? []).find((item) => item.type === "quick_replies");
  return block ? asQuickReplies(block) : null;
}
