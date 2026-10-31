import { replyLabel } from "../../labels/catalog";
import controls from "../../styles/controls.module.css";
import styles from "./QuickReplies.module.css";

export function QuickReplies({
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
    <div className={styles.row} data-testid="quick-replies">
      {options.map((option, index) => (
        <button
          key={`${option}-${index}`}
          type="button"
          className={controls.quiet}
          disabled={disabled}
          onClick={() => onSend(option)}
        >
          {replyLabel(option)}
        </button>
      ))}
    </div>
  );
}
