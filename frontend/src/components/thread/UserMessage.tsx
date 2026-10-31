import styles from "./UserMessage.module.css";

export function UserMessage({ text, entering }: { text: string; entering?: boolean }) {
  return (
    <article className={styles.row} data-testid="user-message">
      <p className={styles.bubble} data-entering={entering ? "" : undefined}>
        {text}
      </p>
    </article>
  );
}
