import { QuickReplies } from "../blocks/QuickReplies";
import styles from "./QuickReplyBar.module.css";

export function QuickReplyBar({
  options,
  disabled,
  onSend,
}: {
  options: string[];
  disabled: boolean;
  onSend: (text: string) => void;
}) {
  if (options.length === 0) return null;
  return (
    <div className={styles.bar}>
      <p>Можно ответить</p>
      <QuickReplies options={options} disabled={disabled} onSend={onSend} />
    </div>
  );
}
