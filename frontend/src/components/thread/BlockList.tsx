import {
  asCampaignDraft,
  asCampaignList,
  asCompliance,
  asCreativePreview,
  asEntityProposal,
  asQuickReplies,
  asWarnings,
} from "../../api/guards";
import type { CampaignDraftBlock, IncomingBlock } from "../../api/types";
import { DraftChangeNote } from "../blocks/DraftChangeNote";
import { CampaignList } from "../blocks/CampaignList";
import { ComplianceCard } from "../blocks/ComplianceCard";
import { CreativePreview } from "../blocks/CreativePreview";
import { EntityProposalCard } from "../blocks/EntityProposalCard";
import { UnknownBlock } from "../blocks/UnknownBlock";
import { WarningsBanner } from "../blocks/WarningsBanner";
import styles from "./BlockList.module.css";

export function BlockList({
  blocks,
  previousDraft,
  disabled,
  onSend,
  onSave,
}: {
  blocks: IncomingBlock[];
  previousDraft: CampaignDraftBlock | null;
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
        if (draft) {
          return (
            <DraftChangeNote
              key={key}
              block={draft}
              previous={previousDraft}
              disabled={disabled}
              onSave={onSave}
            />
          );
        }
        // Кнопки ответа рисует панель над полем ввода, в ленте они не дублируются.
        if (asQuickReplies(block)) return null;
        const proposal = asEntityProposal(block);
        if (proposal) {
          return (
            <EntityProposalCard
              key={key}
              proposal={proposal.proposal}
              disabled={disabled}
              onSend={onSend}
            />
          );
        }
        const creative = asCreativePreview(block);
        if (creative) return <CreativePreview key={key} banner={creative.banner} />;
        const compliance = asCompliance(block);
        if (compliance) return <ComplianceCard key={key} report={compliance.report} />;
        const list = asCampaignList(block);
        if (list) return <CampaignList key={key} items={list.items} />;
        const warnings = asWarnings(block);
        if (warnings) return <WarningsBanner key={key} items={warnings.items} />;
        return <UnknownBlock key={key} />;
      })}
    </div>
  );
}
