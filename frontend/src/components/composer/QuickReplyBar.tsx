import { useState } from "react";
import type { QuickRepliesBlock, ReplyOption } from "../../api/types";
import { replyLabel } from "../../labels/catalog";
import controls from "../../styles/controls.module.css";
import styles from "./QuickReplyBar.module.css";

/**
 * Меню ответа на последний вопрос ассистента. Одиночный выбор — кнопка сразу отправляет текст.
 * Множественный (`multi`) — отмечаете варианты и жмёте «Готово»: уходит одна строка через запятую.
 */
export function QuickReplyBar({
  block,
  disabled,
  onSend,
}: {
  block: QuickRepliesBlock | null;
  disabled: boolean;
  onSend: (text: string) => void;
}) {
  const [picked, setPicked] = useState<string[]>([]);

  if (!block) return null;
  const items: ReplyOption[] =
    block.items && block.items.length > 0
      ? block.items
      : block.options.map((option) => ({ label: replyLabel(option), value: option }));
  if (items.length === 0) return null;

  const toggle = (value: string) =>
    setPicked((prev) =>
      prev.includes(value) ? prev.filter((v) => v !== value) : [...prev, value],
    );

  return (
    <div className={styles.bar} data-testid="quick-replies">
      <p>{block.multi ? "Выберите один или несколько вариантов" : "Можно ответить"}</p>
      <div className={styles.row}>
        {items.map((item, index) => {
          const active = picked.includes(item.value);
          const className = [
            styles.chip,
            item.recommended ? styles.recommended : "",
            active ? styles.active : "",
          ].join(" ");
          return (
            <button
              key={`${item.value}-${index}`}
              type="button"
              className={className}
              title={item.hint ?? undefined}
              disabled={disabled}
              onClick={() => (block.multi ? toggle(item.value) : onSend(item.value))}
            >
              {block.multi ? <span className={styles.box}>{active ? "✓" : ""}</span> : null}
              <span>{item.label}</span>
              {item.hint ? <small>{item.hint}</small> : null}
            </button>
          );
        })}
        {block.multi ? (
          <button
            type="button"
            className={controls.primary}
            disabled={disabled || picked.length === 0}
            onClick={() => onSend(picked.join(", "))}
          >
            Готово{picked.length > 0 ? ` (${picked.length})` : ""}
          </button>
        ) : null}
      </div>
    </div>
  );
}
