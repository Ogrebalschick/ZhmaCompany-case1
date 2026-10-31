import styles from "./PendingStatus.module.css";

export function PendingStatus() {
  return (
    <p className={styles.status} role="status" aria-live="polite" data-testid="pending-status">
      Собираю ответ
    </p>
  );
}
