import { useState } from "react";
import type { CampaignDraftBlock } from "../../api/types";
import { changedFieldKeys } from "../../labels/draftRows";
import { campaignNumber } from "../../labels/fields";
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
    state: `${campaignNumber(draft.savedCampaignId)} · ${state}`,
    shown: live ? "в показе" : "не в показе",
  };
}

export function DraftRail({
  block,
  sessionId,
  disabled,
  onSave,
}: {
  block: CampaignDraftBlock | null;
  sessionId: string | null;
  disabled: boolean;
  onSave: () => void;
}) {
  const [flash, setFlash] = useState<{
    sessionId: string | null;
    block: CampaignDraftBlock | null;
    keys: string[];
    token: number;
  }>({ sessionId: null, block: null, keys: [], token: 0 });
  if (flash.sessionId !== sessionId || flash.block !== block) {
    const sameChat = flash.sessionId === sessionId && flash.block != null && block != null;
    const keys = sameChat ? changedFieldKeys(block, flash.block) : [];
    setFlash({
      sessionId,
      block,
      keys,
      token: keys.length > 0 ? flash.token + 1 : flash.token,
    });
  }
  const flashKeys = flash.keys;
  const flashToken = flash.token;

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
