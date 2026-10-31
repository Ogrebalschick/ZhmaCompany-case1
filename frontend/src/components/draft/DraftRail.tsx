import type { CampaignDraftBlock } from "../../api/types";
import { CampaignDraftCard } from "../blocks/CampaignDraftCard";
import styles from "./DraftRail.module.css";

export function DraftRail({
  block,
  disabled,
  onSave,
}: {
  block: CampaignDraftBlock | null;
  disabled: boolean;
  onSave: () => void;
}) {
  return (
    <aside className={styles.rail} data-testid="draft-rail">
      <div className={styles.inner}>
        <h2>Сейчас в черновике</h2>
        {block ? (
          <>
            <p className={styles.status}>{block.readyToSave ? "Можно сохранить" : "Есть незаполненные поля"}</p>
            <CampaignDraftCard block={block} disabled={disabled} onSave={onSave} variant="rail" />
          </>
        ) : (
          <p className={styles.empty}>Черновик появится здесь, когда ассистент соберёт поля.</p>
        )}
      </div>
    </aside>
  );
}
