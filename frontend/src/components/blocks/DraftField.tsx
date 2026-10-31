import styles from "./DraftField.module.css";

export function DraftField({ label, value, hint }: { label: string; value: string; hint?: string }) {
  return (
    <div className={styles.field}>
      <dt>{label}</dt>
      <dd>
        {value}
        {hint ? <span className={styles.hint}>{hint}</span> : null}
      </dd>
    </div>
  );
}
