import styles from "./DraftField.module.css";

export function DraftField({
  fieldKey,
  label,
  value,
  hint,
  flash,
  flashToken = 0,
}: {
  fieldKey: string;
  label: string;
  value: string;
  hint?: string;
  flash?: boolean;
  flashToken?: number;
}) {
  return (
    <div
      key={flash ? `${fieldKey}-${flashToken}` : fieldKey}
      className={flash ? `${styles.field} ${styles.flash}` : styles.field}
      data-field={fieldKey}
      data-flash={flash ? "yes" : "no"}
    >
      <dt>{label}</dt>
      <dd>
        {value}
        {hint ? <span className={styles.hint}>{hint}</span> : null}
      </dd>
    </div>
  );
}
