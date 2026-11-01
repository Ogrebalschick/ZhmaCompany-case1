import { useEffect, useState } from "react";
import type { Role } from "../../api/types";
import { campaignNumber } from "../../labels/fields";
import type { SessionEntry } from "../../chat/useChat";
import { ProgressIndicator } from "../ui/ProgressIndicator";
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
  collapsed,
  onToggleCollapsed,
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
  collapsed: boolean;
  onToggleCollapsed: () => void;
  onNew: () => void;
  onOpenSession: (id: string) => void;
  onOpenCampaign: (id: number) => void;
  onForget: (id: string) => void;
}) {
  const [campaigns, setCampaigns] = useState<CampaignRow[]>([]);
  const [readyScope, setReadyScope] = useState<string | null>(null);
  const [picked, setPicked] = useState<{ role: Role; mine: boolean } | null>(null);
  const scope = `${user}:${role}`;
  const onlyMine = picked?.role === role ? picked.mine : role !== "campaignModerator";

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
      } finally {
        if (!stopped) setReadyScope(scope);
      }
    })();
    return () => {
      stopped = true;
    };
  }, [user, role, refreshKey, scope]);

  if (collapsed) {
    return (
      <nav className={styles.sideCollapsed} aria-label="Кампании">
        <span className={styles.logo} aria-hidden="true" />
        <button
          type="button"
          className={styles.expand}
          onClick={onToggleCollapsed}
          aria-label="Показать список кампаний"
          title="Показать список кампаний"
        >
          <ExpandIcon />
        </button>
      </nav>
    );
  }

  const loading = readyScope !== scope;
  const shown = campaigns.filter((row) => !onlyMine || !row.author || row.author === user);
  const drafts = sessions.filter((item) => !item.campaignId || item.kind === "review");
  const others = campaigns.some((row) => row.author && row.author !== user);
  const sameList = picked?.role === role && !picked.mine && campaigns.length > 0 && !others;

  return (
    <nav className={styles.side} aria-label="Кампании">
      <div className={styles.brand}>
        <span className={styles.logo} aria-hidden="true" />
        <div className={styles.brandText}>
          <strong>Кампании</strong>
          <small>ассистент менеджера</small>
        </div>
        <button
          type="button"
          className={styles.collapse}
          onClick={onToggleCollapsed}
          aria-label="Скрыть список кампаний"
          title="Скрыть список кампаний"
        >
          <CollapseIcon />
        </button>
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
                  <span className={`${styles.chip} ${styles.grey}`}>
                    {item.campaignId ? "чат" : "не сохранён"}
                  </span>
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
          <h4>Кампании</h4>
          <div className={styles.scope} role="group" aria-label="Чьи кампании показать">
            <button
              type="button"
              className={onlyMine ? styles.scopeOn : undefined}
              aria-pressed={onlyMine}
              title="Только кампании, которые создал я"
              onClick={() => setPicked({ role, mine: true })}
            >
              Мои
            </button>
            <button
              type="button"
              className={!onlyMine ? styles.scopeOn : undefined}
              aria-pressed={!onlyMine}
              title="Кампании всех авторов"
              onClick={() => setPicked({ role, mine: false })}
            >
              Все
            </button>
          </div>
        </div>
        {!loading && sameList ? (
          <p className={styles.hint}>Чужих кампаний в базе нет — здесь только ваши.</p>
        ) : null}
        {loading ? <ProgressIndicator label="Загружаю кампании" /> : null}
        {!loading && shown.length === 0 ? (
          <p className={styles.empty}>Пока нет сохранённых кампаний</p>
        ) : null}
        {!loading ? (
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
                    title={`${campaignNumber(row.id)} · ${row.name}${row.author ? ` · ${row.author}` : ""}`}
                  >
                    <span className={styles.title}>
                      <em>{campaignNumber(row.id)}</em> {row.name}
                    </span>
                    {!onlyMine && row.author ? (
                      <span className={styles.author}>{row.author}</span>
                    ) : null}
                    <span className={`${styles.chip} ${styles[state.tone]}`}>{state.label}</span>
                  </button>
                </li>
              );
            })}
          </ul>
        ) : null}
      </section>
    </nav>
  );
}

function CollapseIcon() {
  return (
    <svg width="18" height="18" viewBox="0 0 18 18" aria-hidden="true">
      <path
        d="M11.5 4.5 7 9l4.5 4.5"
        fill="none"
        stroke="currentColor"
        strokeWidth="1.7"
        strokeLinecap="round"
        strokeLinejoin="round"
      />
    </svg>
  );
}

function ExpandIcon() {
  return (
    <svg width="18" height="18" viewBox="0 0 18 18" aria-hidden="true">
      <path
        d="M6.5 4.5 11 9l-4.5 4.5"
        fill="none"
        stroke="currentColor"
        strokeWidth="1.7"
        strokeLinecap="round"
        strokeLinejoin="round"
      />
    </svg>
  );
}
