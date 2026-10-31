import type { EntityProposal } from "../../api/types";
import { CANCEL_TEXT, CONFIRM_TEXT } from "../../chat/phrases";
import { orDash } from "../../labels/fields";
import controls from "../../styles/controls.module.css";
import styles from "./EntityProposalCard.module.css";

export function EntityProposalCard({
  proposal,
  disabled,
  onSend,
}: {
  proposal: EntityProposal;
  disabled: boolean;
  onSend: (text: string) => void;
}) {
  const title = proposal.kind === "TAG" ? "Создать тег" : proposal.kind === "PLACE" ? "Создать место" : "Создать сущность";
  return (
    <section className={styles.card} data-testid="entity-proposal">
      <h3>{title}</h3>
      <dl>
        <div>
          <dt>Имя</dt>
          <dd>{proposal.name}</dd>
        </div>
        <div>
          <dt>Код</dt>
          <dd>{orDash(proposal.code)}</dd>
        </div>
        <div>
          <dt>Описание</dt>
          <dd>{orDash(proposal.description)}</dd>
        </div>
      </dl>
      <div className={styles.actions}>
        <button type="button" className={controls.primary} disabled={disabled} onClick={() => onSend(CONFIRM_TEXT)}>
          подтверждаю
        </button>
        <button type="button" className={controls.quiet} disabled={disabled} onClick={() => onSend(CANCEL_TEXT)}>
          отмена
        </button>
      </div>
    </section>
  );
}
