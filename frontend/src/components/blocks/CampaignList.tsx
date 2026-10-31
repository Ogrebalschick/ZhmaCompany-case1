import { asCampaignItem } from "../../api/guards";
import type { CampaignListItem } from "../../api/types";
import { CampaignListRow } from "./CampaignListRow";
import styles from "./CampaignList.module.css";

export function CampaignList({ items }: { items: CampaignListItem[] }) {
  const rows = items.flatMap((item) => {
    const row = asCampaignItem(item);
    return row ? [row] : [];
  });
  return (
    <section className={styles.list} data-testid="campaign-list">
      <h3>Кампании</h3>
      {rows.length === 0 ? <p className={styles.empty}>Список пуст</p> : null}
      <ul>
        {rows.map((item) => (
          <CampaignListRow key={item.id} item={item} />
        ))}
      </ul>
    </section>
  );
}
