import { useLayoutEffect, useRef, useState } from "react";
import { PendingStatus } from "./PendingStatus";
import styles from "./Composer.module.css";

const MAX_HEIGHT = 168;

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
  const [thumb, setThumb] = useState<{ top: number; height: number } | null>(null);
  const field = useRef<HTMLTextAreaElement>(null);

  function placeThumb(node: HTMLTextAreaElement) {
    const overflow = node.scrollHeight > node.clientHeight + 1;
    if (!overflow) {
      setThumb(null);
      return;
    }
    const track = Math.max(24, node.clientHeight - 52);
    const height = Math.max(20, Math.round(track * (node.clientHeight / node.scrollHeight)));
    const maxTop = Math.max(0, track - height);
    const range = node.scrollHeight - node.clientHeight;
    const top = range <= 0 ? 8 : 8 + (node.scrollTop / range) * maxTop;
    setThumb({ top, height });
  }

  useLayoutEffect(() => {
    const node = field.current;
    if (!node) return;
    node.style.height = "0px";
    const next = Math.min(node.scrollHeight, MAX_HEIGHT);
    node.style.height = `${next}px`;
    node.style.overflowY = node.scrollHeight > MAX_HEIGHT ? "auto" : "hidden";
    placeThumb(node);
  }, [value]);

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
        <div className={styles.field}>
          <textarea
            ref={field}
            id="composer"
            className={styles.input}
            data-testid="composer"
            rows={1}
            placeholder="Опишите кампанию"
            title="Enter отправляет, Shift+Enter переносит строку"
            value={value}
            disabled={disabled}
            onScroll={(event) => placeThumb(event.currentTarget)}
            onChange={(event) => setValue(event.target.value)}
            onKeyDown={(event) => {
              if (event.key === "Enter" && !event.shiftKey) {
                event.preventDefault();
                submit();
              }
            }}
          />
          {thumb ? (
            <span
              className={styles.thumb}
              style={{ top: thumb.top, height: thumb.height }}
              aria-hidden="true"
            />
          ) : null}
          <button
            type="submit"
            className={styles.send}
            data-testid="send"
            aria-label="Отправить"
            disabled={disabled || !value.trim()}
          >
            <SendIcon />
          </button>
        </div>
      </div>
    </form>
  );
}

function SendIcon() {
  return (
    <svg viewBox="0 0 24 24" width="18" height="18" aria-hidden="true">
      <path
        fill="currentColor"
        d="M3.2 11.1 20.2 3.4c.7-.3 1.4.4 1.1 1.1l-7.7 16.9c-.3.7-1.3.6-1.5-.1l-2.1-6.4-6.4-2.1c-.7-.2-.8-1.2-.2-1.5z"
      />
    </svg>
  );
}
