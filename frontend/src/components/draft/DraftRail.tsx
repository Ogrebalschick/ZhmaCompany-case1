import { useEffect, useRef, useState } from "react";
import type { CampaignDraftBlock } from "../../api/types";
import { changedFieldKeys } from "../../labels/draftRows";
import { CampaignDraftCard } from "../blocks/CampaignDraftCard";
import styles from "./DraftRail.module.css";

const STATES: Record<string, string> = {
  DRAFT: "Черновик",
  ON_MODERATION: "На модерации",
  NEEDS_WORK: "На доработке",
  APPROVED: "Одобрена",
  ACTIVE: "Активна",
  ACTIVE_EDITING: "Активна, правится",
  STOPPED: "Остановлена",
};

/** Состояние кампании и отдельно — идёт ли показ. */
function railStatus(block: CampaignDraftBlock): { state: string; shown: string | null } {
  const draft = block.draft;
  if (draft.savedCampaignId == null) {
    return {
      state: block.readyToSave
        ? "Не сохранена · можно сохранить"
        : "Не сохранена · есть пустые поля",
      shown: null,
    };
  }
  const state = STATES[draft.knownState ?? "DRAFT"] ?? draft.knownState ?? "Сохранена";
  const live = draft.knownState === "ACTIVE" || draft.knownState === "ACTIVE_EDITING";
  return {
    state: `№${draft.savedCampaignId} · ${state}`,
    shown: live ? "в показе" : "не в показе",
  };
}

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
            <p className={styles.status}>
              {railStatus(block).state}
              {railStatus(block).shown ? (
                <span className={styles.shown} data-live={railStatus(block).shown === "в показе"}>
                  {railStatus(block).shown}
                </span>
              ) : null}
            </p>
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
