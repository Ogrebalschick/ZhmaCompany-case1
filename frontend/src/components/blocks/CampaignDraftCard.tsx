import type { CampaignDraftBlock } from "../../api/types";
import { formatPlaces, formatSegments, formatTags, topicLabel } from "../../labels/catalog";
import {
  ALL_USERS_HINT,
  audienceLabel,
  formatCampaignId,
  formatMetric,
  formatSchedule,
  orDash,
  placeReasons,
  segmentReasons,
} from "../../labels/fields";
import controls from "../../styles/controls.module.css";
import { DraftField } from "./DraftField";
import { MissingList } from "./MissingList";
import styles from "./CampaignDraftCard.module.css";

export function CampaignDraftCard({
  block,
  disabled,
  onSave,
  variant = "message",
}: {
  block: CampaignDraftBlock;
  disabled: boolean;
  onSave: () => void;
  variant?: "message" | "rail";
}) {
  const draft = block.draft;
  const tags = draft.tagIds ?? [];
  const segments = block.choices?.segments;
  const places = block.choices?.places;
  return (
    <section
      className={styles.card}
      data-testid={variant === "rail" ? "draft-card-rail" : "draft-card"}
      data-ready={block.readyToSave ? "yes" : "no"}
    >
      <header className={styles.header}>
        <h3>{draft.name?.trim() || "Черновик кампании"}</h3>
        <MissingList codes={block.missing} />
      </header>
      <dl className={styles.fields}>
        <DraftField label="Название" value={orDash(draft.name)} />
        <DraftField label="Маркетинговое имя" value={orDash(draft.marketingName)} />
        <DraftField label="Начало" value={formatSchedule(draft.startDate, draft.startTime)} />
        <DraftField label="Окончание" value={formatSchedule(draft.endDate, draft.endTime)} />
        <DraftField label="Тема" value={draft.topic ? topicLabel(draft.topic) : "—"} />
        <DraftField
          label="Аудитория"
          value={audienceLabel(draft.audienceType)}
          hint={draft.audienceType === "ALL_USERS" ? ALL_USERS_HINT : undefined}
        />
        <DraftField
          label="Сегменты включения"
          value={formatSegments(draft.includedSegments)}
          hint={segmentReasons(segments, "included", draft.includedSegments)}
        />
        <DraftField
          label="Сегменты исключения"
          value={formatSegments(draft.excludedSegments)}
          hint={segmentReasons(segments, "excluded", draft.excludedSegments)}
        />
        <DraftField
          label="Места показа"
          value={formatPlaces(draft.placeIds)}
          hint={placeReasons(places, draft.placeIds)}
        />
        {tags.length > 0 ? <DraftField label="Теги" value={formatTags(tags)} /> : null}
        <DraftField label="Вес" value={formatMetric(draft.weight)} />
        <DraftField label="Начальный CTR" value={formatMetric(draft.initialCtr)} />
        <DraftField label="ID кампании" value={formatCampaignId(draft.savedCampaignId)} />
      </dl>
      {block.readyToSave ? (
        <button
          type="button"
          className={controls.primary}
          data-testid={variant === "rail" ? "rail-save" : "save-campaign"}
          disabled={disabled}
          onClick={onSave}
        >
          Сохранить
        </button>
      ) : null}
    </section>
  );
}
