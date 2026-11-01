import styles from "./ProgressIndicator.module.css";

/** Неопределённый прогресс, пока список или диалог ещё не пришли. */
export function ProgressIndicator({ label = "Загрузка" }: { label?: string }) {
  return (
    <div className={styles.track} role="progressbar" aria-label={label} aria-valuetext={label}>
      <span className={styles.bar} />
    </div>
  );
}
