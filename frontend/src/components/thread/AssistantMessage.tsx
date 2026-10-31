import type { CampaignDraftBlock, IncomingBlock } from "../../api/types";
import { BlockList } from "./BlockList";
import styles from "./AssistantMessage.module.css";

export function AssistantMessage({
  text,
  blocks,
  previousDraft,
  disabled,
  onSend,
  onSave,
}: {
  text: string;
  blocks?: IncomingBlock[];
  previousDraft: CampaignDraftBlock | null;
  disabled: boolean;
  onSend: (text: string) => void;
  onSave: () => void;
}) {
  return (
    <article className={styles.card} data-testid="assistant-message">
      {text ? <p className={styles.text}>{text}</p> : null}
      <BlockList
        blocks={blocks ?? []}
        previousDraft={previousDraft}
        disabled={disabled}
        onSend={onSend}
        onSave={onSave}
      />
    </article>
  );
}
