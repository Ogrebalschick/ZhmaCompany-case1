import type { CampaignDraftBlock } from "../../api/types";
import { changedFieldLabels } from "../../labels/draftRows";
import controls from "../../styles/controls.module.css";
import { MissingList } from "./MissingList";
import styles from "./DraftChangeNote.module.css";

export function DraftChangeNote({
  block,
  previous,
  disabled,
  onSave,
}: {
  block: CampaignDraftBlock;
  previous: CampaignDraftBlock | null;
  disabled: boolean;
  onSave: () => void;
}) {
  const changed = changedFieldLabels(block, previous);
  const name = block.draft.name?.trim() || "Черновик кампании";
  return (
    <section className={styles.note} data-testid="draft-summary">
      <h3>{name}</h3>
      {changed.length > 0 ? <p>Изменилось: {changed.join(", ")}</p> : null}
      <MissingList codes={block.missing} />
      {block.readyToSave ? (
        <button type="button" className={controls.primary} data-testid="save-campaign" disabled={disabled} onClick={onSave}>
          Сохранить
        </button>
      ) : null}
    </section>
  );
}
