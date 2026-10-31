import type { CampaignListItem } from "../../api/types";
import { publishedLabel, stateHint } from "../../labels/states";
import styles from "./CampaignListRow.module.css";

export function CampaignListRow({
  item,
  onSelect,
}: {
  item: CampaignListItem;
  onSelect?: () => void;
}) {
  const hint = stateHint(item.state);
  const dates = item.endDate ? `${item.startDate} — ${item.endDate}` : item.startDate;
  const body = (
    <>
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
    </>
  );
  if (!onSelect) return <li className={styles.row}>{body}</li>;
  return (
    <li>
      <button
        type="button"
        className={`${styles.row} ${styles.pick}`}
        data-testid={`campaign-${item.id}`}
        onClick={onSelect}
      >
        {body}
      </button>
    </li>
  );
}
