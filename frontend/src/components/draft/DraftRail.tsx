import { useEffect, useRef, useState } from "react";
import type { CampaignDraftBlock } from "../../api/types";
import { changedFieldKeys } from "../../labels/draftRows";
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
  const previous = useRef<CampaignDraftBlock | null>(null);
  const [flashKeys, setFlashKeys] = useState<string[]>([]);
  const [flashToken, setFlashToken] = useState(0);

  useEffect(() => {
    if (!block) return;
    const keys = changedFieldKeys(block, previous.current);
    previous.current = block;
    if (keys.length === 0) return;
    setFlashKeys(keys);
    setFlashToken((value) => value + 1);
  }, [block]);

  return (
    <aside className={styles.rail} data-testid="draft-rail">
      <div className={styles.inner}>
        <h2>Сейчас в черновике</h2>
        {block ? (
          <>
            <p className={styles.status}>{block.readyToSave ? "Можно сохранить" : "Есть незаполненные поля"}</p>
            <CampaignDraftCard
              block={block}
              disabled={disabled}
              onSave={onSave}
              variant="rail"
              flashKeys={flashKeys}
              flashToken={flashToken}
            />
          </>
        ) : (
          <p className={styles.empty}>Черновик появится здесь, когда ассистент соберёт поля.</p>
        )}
      </div>
    </aside>
  );
}
