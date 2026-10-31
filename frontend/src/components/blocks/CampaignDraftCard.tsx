import type { CampaignDraftBlock } from "../../api/types";
import type { SegmentChoice } from "../../api/types";
import { formatPlaces, formatSegments, formatTags, topicLabel } from "../../labels/catalog";
import {
  ALL_USERS_HINT,
  audienceLabel,
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
  flashKeys = [],
  flashToken = 0,
}: {
  block: CampaignDraftBlock;
  disabled: boolean;
  onSave: () => void;
  variant?: "message" | "rail";
  flashKeys?: string[];
  flashToken?: number;
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
        {block.choices?.reachText ? (
          <p className={styles.reach}>{block.choices.reachText}</p>
        ) : null}
      </header>
      <dl className={styles.fields}>
        <DraftField
          fieldKey="name"
          label="Название"
          value={orDash(draft.name)}
          flashToken={flashToken}
          flash={flashKeys.includes("name")}
        />
        <DraftField
          fieldKey="start"
          label="Начало"
          value={formatSchedule(draft.startDate, draft.startTime)}
          flashToken={flashToken}
          flash={flashKeys.includes("start")}
        />
        <DraftField
          fieldKey="end"
          label="Окончание"
          value={formatSchedule(draft.endDate, draft.endTime)}
          flashToken={flashToken}
          flash={flashKeys.includes("end")}
        />
        <DraftField
          fieldKey="topic"
          label="Тема"
          value={draft.topic ? topicLabel(draft.topic) : "—"}
          flashToken={flashToken}
          flash={flashKeys.includes("topic")}
        />
        <DraftField
          fieldKey="audience"
          label="Аудитория"
          value={audienceLabel(draft.audienceType)}
          hint={draft.audienceType === "ALL_USERS" ? ALL_USERS_HINT : undefined}
          flashToken={flashToken}
          flash={flashKeys.includes("audience")}
        />
        <DraftField
          fieldKey="included"
          label="Сегменты включения"
          value={segmentNames(segments, "included", draft.includedSegments)}
          hint={segmentReasons(segments, "included", draft.includedSegments)}
          flashToken={flashToken}
          flash={flashKeys.includes("included")}
        />
        <DraftField
          fieldKey="excluded"
          label="Сегменты исключения"
          value={segmentNames(segments, "excluded", draft.excludedSegments)}
          hint={segmentReasons(segments, "excluded", draft.excludedSegments)}
          flashToken={flashToken}
          flash={flashKeys.includes("excluded")}
        />
        <DraftField
          fieldKey="places"
          label="Места показа"
          value={formatPlaces(draft.placeIds)}
          hint={placeReasons(places, draft.placeIds)}
          flashToken={flashToken}
          flash={flashKeys.includes("places")}
        />
        {tags.length > 0 ? (
          <DraftField
            fieldKey="tags"
            label="Теги"
            value={formatTags(tags)}
            flashToken={flashToken}
            flash={flashKeys.includes("tags")}
          />
        ) : null}
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

/** Имена сегментов из ответа сервера (там есть и созданные в чате); коды справочника не показываем. */
function segmentNames(
  choices: SegmentChoice[] | undefined,
  list: "included" | "excluded",
  codes: string[] | undefined,
): string {
  if (!codes || codes.length === 0) return "—";
  if (!choices) return formatSegments(codes);
  return codes
    .map(
      (code) =>
        choices.find((item) => item.code === code && item.list === list)?.name ??
        formatSegments([code]),
    )
    .join(", ");
}
