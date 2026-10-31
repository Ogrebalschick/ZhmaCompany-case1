import { missingLabel } from "../../labels/fields";
import styles from "./MissingList.module.css";

export function MissingList({ codes }: { codes: string[] }) {
  if (codes.length === 0) return null;
  return (
    <div className={styles.wrap} data-testid="missing-fields">
      <p>Осталось уточнить</p>
      <ul>
        {codes.map((code) => (
          <li key={code} data-code={code}>
            {missingLabel(code)}
          </li>
        ))}
      </ul>
    </div>
  );
}
