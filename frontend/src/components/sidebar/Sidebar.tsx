import { useEffect, useState } from "react";
import type { Role } from "../../api/types";
import type { SessionEntry } from "../../chat/useChat";
import styles from "./Sidebar.module.css";

interface CampaignRow {
  id: number;
  name: string;
  state: string;
  author?: string | null;
  startDate?: string;
}

const STATE: Record<string, { label: string; tone: string }> = {
  DRAFT: { label: "Черновик", tone: "grey" },
  ON_MODERATION: { label: "На модерации", tone: "blue" },
  NEEDS_WORK: { label: "На доработке", tone: "orange" },
  APPROVED: { label: "Одобрена", tone: "green" },
  ACTIVE: { label: "Активна", tone: "green" },
  ACTIVE_EDITING: { label: "Правится", tone: "orange" },
  STOPPED: { label: "Остановлена", tone: "grey" },
};

/**
 * Левая панель: новая кампания, черновики в работе (сессии этого браузера)
 * и сохранённые кампании из базы со статусами. Клик открывает кампанию в чате.
 */
export function Sidebar({
  user,
  role,
  currentSession,
  sessions,
  refreshKey,
  disabled,
  onNew,
  onOpenSession,
  onOpenCampaign,
  onForget,
}: {
  user: string;
  role: Role;
  currentSession: string | null;
  sessions: SessionEntry[];
  refreshKey: number;
  disabled: boolean;
  onNew: () => void;
  onOpenSession: (id: string) => void;
  onOpenCampaign: (id: number) => void;
  onForget: (id: string) => void;
}) {
  const [campaigns, setCampaigns] = useState<CampaignRow[]>([]);
  const [onlyMine, setOnlyMine] = useState(true);

  useEffect(() => {
    let stopped = false;
    void (async () => {
      try {
        const response = await fetch("/api/campaigns", {
          headers: { "X-User": user, "X-Role": role },
        });
        if (!response.ok) return;
        const rows = (await response.json()) as CampaignRow[];
        if (!stopped) setCampaigns(rows.slice().reverse());
      } catch {
        // сервер ещё стартует — список подтянется со следующим ответом
      }
    })();
    return () => {
      stopped = true;
    };
  }, [user, role, refreshKey]);

  const moderator = role === "campaignModerator";
  const shown = campaigns.filter((row) =>
    moderator ? true : !onlyMine || !row.author || row.author === user,
  );
  const drafts = sessions.filter((item) => !item.campaignId);

  return (
    <nav className={styles.side} aria-label="Кампании">
      <div className={styles.brand}>
        <span className={styles.logo} aria-hidden="true" />
        <div>
          <strong>Кампании</strong>
          <small>ассистент менеджера</small>
        </div>
      </div>

      <button type="button" className={styles.newButton} disabled={disabled} onClick={onNew}>
        <span>＋</span> Новая кампания
      </button>

      {drafts.length > 0 ? (
        <section className={styles.group}>
          <h4>Черновики</h4>
          <ul>
            {drafts.map((item) => (
              <li key={item.id}>
                <button
                  type="button"
                  className={item.id === currentSession ? styles.active : undefined}
                  disabled={disabled}
                  onClick={() => onOpenSession(item.id)}
                  title={item.title}
                >
                  <span className={styles.title}>{item.title}</span>
                  <span className={`${styles.chip} ${styles.grey}`}>не сохранён</span>
                </button>
                <button
                  type="button"
                  className={styles.forget}
                  aria-label="Убрать из списка"
                  title="Убрать из списка"
                  onClick={() => onForget(item.id)}
                >
                  ×
                </button>
              </li>
            ))}
          </ul>
        </section>
      ) : null}

      <section className={styles.group}>
        <div className={styles.groupHead}>
          <h4>{moderator ? "Все кампании" : "Мои кампании"}</h4>
          {moderator ? null : (
            <label className={styles.toggle}>
              <input
                type="checkbox"
                checked={!onlyMine}
                onChange={(e) => setOnlyMine(!e.target.checked)}
              />
              все
            </label>
          )}
        </div>
        {shown.length === 0 ? <p className={styles.empty}>Пока нет сохранённых кампаний</p> : null}
        <ul>
          {shown.map((row) => {
            const state = STATE[row.state] ?? { label: row.state, tone: "grey" };
            const current =
              sessions.find((item) => item.id === currentSession)?.campaignId === row.id;
            return (
              <li key={row.id}>
                <button
                  type="button"
                  className={current ? styles.active : undefined}
                  disabled={disabled}
                  onClick={() => onOpenCampaign(row.id)}
                  title={`№${row.id} · ${row.name}`}
                >
                  <span className={styles.title}>
                    <em>№{row.id}</em> {row.name}
                  </span>
                  <span className={`${styles.chip} ${styles[state.tone]}`}>{state.label}</span>
                </button>
              </li>
            );
          })}
        </ul>
      </section>
    </nav>
  );
}
