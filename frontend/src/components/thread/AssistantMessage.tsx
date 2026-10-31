import type { IncomingBlock } from "../../api/types";
import { BlockList } from "./BlockList";
import styles from "./AssistantMessage.module.css";

export function AssistantMessage({
  text,
  blocks,
  disabled,
  onSend,
  onSave,
}: {
  text: string;
  blocks?: IncomingBlock[];
  disabled: boolean;
  onSend: (text: string) => void;
  onSave: () => void;
}) {
  return (
    <article className={styles.card} data-testid="assistant-message">
      {text ? <p className={styles.text}>{text}</p> : null}
      <BlockList blocks={blocks ?? []} disabled={disabled} onSend={onSend} onSave={onSave} />
    </article>
  );
}
