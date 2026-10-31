import { useState } from "react";
import controls from "../../styles/controls.module.css";
import { PendingStatus } from "./PendingStatus";
import styles from "./Composer.module.css";

export function Composer({
  pending,
  disabled,
  onSend,
}: {
  pending: boolean;
  disabled: boolean;
  onSend: (text: string) => boolean;
}) {
  const [value, setValue] = useState("");

  function submit() {
    if (onSend(value)) setValue("");
  }

  return (
    <form
      className={styles.dock}
      onSubmit={(event) => {
        event.preventDefault();
        submit();
      }}
    >
      <div className={styles.inner}>
        {pending ? <PendingStatus /> : null}
        <label className={styles.label} htmlFor="composer">
          Сообщение
        </label>
        <div className={styles.row}>
          <textarea
            id="composer"
            className={styles.input}
            data-testid="composer"
            rows={3}
            placeholder="Опишите кампанию. Enter отправляет, Shift+Enter переносит строку."
            value={value}
            disabled={disabled}
            onChange={(event) => setValue(event.target.value)}
            onKeyDown={(event) => {
              if (event.key === "Enter" && !event.shiftKey) {
                event.preventDefault();
                submit();
              }
            }}
          />
          <button type="submit" className={controls.primary} data-testid="send" disabled={disabled || !value.trim()}>
            Отправить
          </button>
        </div>
      </div>
    </form>
  );
}
