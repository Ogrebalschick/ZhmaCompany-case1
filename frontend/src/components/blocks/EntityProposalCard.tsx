import type { EntityProposal } from "../../api/types";
import { orDash } from "../../labels/fields";
import styles from "./EntityProposalCard.module.css";

export function EntityProposalCard({
  proposal,
  disabled: _disabled,
  onSend: _onSend,
}: {
  proposal: EntityProposal;
  disabled: boolean;
  onSend: (text: string) => void;
}) {
  const title =
    proposal.kind === "TAG"
      ? "Создать тег"
      : proposal.kind === "PLACE"
        ? "Создать место"
        : "Создать сущность";
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
    </section>
  );
}
