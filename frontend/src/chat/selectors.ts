import { asCampaignDraft, asQuickReplies } from "../api/guards";
import type { CampaignDraftBlock, ChatMessage } from "../api/types";

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

export function latestQuickReplies(messages: ChatMessage[]): string[] {
  for (let index = messages.length - 1; index >= 0; index -= 1) {
    const message = messages[index];
    if (message.role !== "assistant") continue;
    const block = (message.blocks ?? []).find((item) => item.type === "quick_replies");
    if (!block) return [];
    return asQuickReplies(block)?.options ?? [];
  }
  return [];
}
