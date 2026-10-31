import type { CampaignListItem } from "../../api/types";
import { publishedLabel, stateHint } from "../../labels/states";
import styles from "./CampaignListRow.module.css";

export function CampaignListRow({ item }: { item: CampaignListItem }) {
  const hint = stateHint(item.state);
  const dates = item.endDate ? `${item.startDate} — ${item.endDate}` : item.startDate;
  return (
    <li className={styles.row}>
      <div>
        <strong>{item.name}</strong>
        <span className={styles.dates}>{dates}</span>
      </div>
      <div className={styles.badges}>
        <span className={styles.state} data-state={item.state}>
          {item.state}
        </span>
        {hint ? <span className={styles.hint}>{hint}</span> : null}
        <span className={styles.published} data-published={item.published ? "yes" : "no"}>
          {publishedLabel(item.published)}
        </span>
      </div>
    </li>
  );
}
