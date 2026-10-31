import {
  asCampaignDraft,
  asCampaignList,
  asCreativePreview,
  asEntityProposal,
  asQuickReplies,
  asWarnings,
} from "../../api/guards";
import type { IncomingBlock } from "../../api/types";
import { CampaignDraftCard } from "../blocks/CampaignDraftCard";
import { CampaignList } from "../blocks/CampaignList";
import { CreativePreview } from "../blocks/CreativePreview";
import { EntityProposalCard } from "../blocks/EntityProposalCard";
import { QuickReplies } from "../blocks/QuickReplies";
import { UnknownBlock } from "../blocks/UnknownBlock";
import { WarningsBanner } from "../blocks/WarningsBanner";
import styles from "./BlockList.module.css";

export function BlockList({
  blocks,
  disabled,
  onSend,
  onSave,
}: {
  blocks: IncomingBlock[];
  disabled: boolean;
  onSend: (text: string) => void;
  onSave: () => void;
}) {
  if (blocks.length === 0) return null;
  return (
    <div className={styles.list}>
      {blocks.map((block, index) => {
        const key = `${block.type}-${index}`;
        const draft = asCampaignDraft(block);
        if (draft) return <CampaignDraftCard key={key} block={draft} disabled={disabled} onSave={onSave} />;
        const replies = asQuickReplies(block);
        if (replies) {
          return <QuickReplies key={key} options={replies.options} disabled={disabled} onSend={onSend} />;
        }
        const proposal = asEntityProposal(block);
        if (proposal) {
          return <EntityProposalCard key={key} proposal={proposal.proposal} disabled={disabled} onSend={onSend} />;
        }
        const creative = asCreativePreview(block);
        if (creative) return <CreativePreview key={key} banner={creative.banner} />;
        const list = asCampaignList(block);
        if (list) return <CampaignList key={key} items={list.items} />;
        const warnings = asWarnings(block);
        if (warnings) return <WarningsBanner key={key} items={warnings.items} />;
        return <UnknownBlock key={key} />;
      })}
    </div>
  );
}
