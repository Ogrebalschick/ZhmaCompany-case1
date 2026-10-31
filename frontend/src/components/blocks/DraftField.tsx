import styles from "./DraftField.module.css";

export function DraftField({ label, value }: { label: string; value: string }) {
  return (
    <div className={styles.field}>
      <dt>{label}</dt>
      <dd>{value}</dd>
    </div>
  );
}
