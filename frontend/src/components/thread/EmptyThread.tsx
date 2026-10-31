import { STARTER_BRIEF } from "../../chat/phrases";
import controls from "../../styles/controls.module.css";
import styles from "./EmptyThread.module.css";

export function EmptyThread({ disabled, onSend }: { disabled: boolean; onSend: (text: string) => void }) {
  return (
    <div className={styles.empty}>
      <h2>Соберите кампанию в диалоге</h2>
      <p>
        Опишите продукт, аудиторию и место показа. Ассистент спросит то, чего не хватает, и не сохранит
        кампанию без подтверждения.
      </p>
      <button
        type="button"
        className={controls.quiet}
        data-testid="starter-brief"
        disabled={disabled}
        onClick={() => onSend(STARTER_BRIEF)}
      >
        {STARTER_BRIEF}
      </button>
    </div>
  );
}
