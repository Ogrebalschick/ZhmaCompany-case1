import styles from "./UserMessage.module.css";

export function UserMessage({ text }: { text: string }) {
  return (
    <article className={styles.row} data-testid="user-message">
      <p className={styles.bubble}>{text}</p>
    </article>
  );
}
