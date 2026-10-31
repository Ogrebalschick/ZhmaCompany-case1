import type { ComplianceBlock } from "../../api/types";
import styles from "./ComplianceCard.module.css";

const VERDICT: Record<string, string> = {
  OK: "Соответствует закону «О рекламе»",
  WARN: "Есть замечания по закону «О рекламе»",
  FAIL: "Нарушение закона «О рекламе»",
};

const STATUS: Record<string, string> = { OK: "✓", INFO: "i", WARN: "!", FAIL: "✕" };

/** Форма проверки баннера по 38-ФЗ: итог и пункты «статья · что не так · как исправить». */
export function ComplianceCard({ report }: { report: ComplianceBlock["report"] }) {
  return (
    <section className={styles.card} data-verdict={report.verdict} data-testid="compliance">
      <header className={styles.head}>
        <strong>{VERDICT[report.verdict] ?? report.verdict}</strong>
        <span>{report.aiChecked ? "правила 38-ФЗ + ИИ-юрист" : "правила 38-ФЗ"}</span>
      </header>
      <ul className={styles.list}>
        {report.items.map((item, index) => (
          <li key={`${item.rule}-${index}`} data-status={item.status}>
            <span className={styles.icon}>{STATUS[item.status] ?? "·"}</span>
            <div>
              <p>
                <b>{item.article}</b> {item.message}
                {item.source === "ai" ? <em className={styles.ai}>ИИ</em> : null}
              </p>
              {item.fix ? <p className={styles.fix}>Как исправить: {item.fix}</p> : null}
            </div>
          </li>
        ))}
      </ul>
    </section>
  );
}
