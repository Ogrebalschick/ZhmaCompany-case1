import type { Role } from "../../api/types";
import { RoleSelect } from "./RoleSelect";
import styles from "./HeaderBar.module.css";

export function HeaderBar({
  user,
  role,
  sessionId,
  booting,
  mock,
  campaignsOpen,
  title,
  onToggleCampaigns,
  onRoleChange,
}: {
  user: string;
  role: Role;
  sessionId: string | null;
  booting: boolean;
  mock: boolean;
  campaignsOpen: boolean;
  title: string;
  onToggleCampaigns: () => void;
  onRoleChange: (role: Role) => void;
}) {
  const sessionLabel = sessionId
    ? `Сессия ${sessionId}`
    : booting
      ? "Сессия создаётся"
      : "Сессия не создана";
  return (
    <header className={styles.bar}>
      <div className={styles.brand}>
        <button
          type="button"
          className={styles.menu}
          aria-label="Кампании"
          aria-expanded={campaignsOpen}
          data-testid="campaign-menu"
          onClick={onToggleCampaigns}
        >
          <span className={styles.chevronWrap} data-open={campaignsOpen ? "yes" : "no"}>
            <span className={styles.chevron} data-open={campaignsOpen ? "yes" : "no"} />
          </span>
        </button>
        <div>
          <p className={styles.product}>{title}</p>
          <p className={styles.session} data-testid="session-id">
            {sessionLabel}
          </p>
        </div>
      </div>
      <div className={styles.actor}>
        {mock ? <span className={styles.badge}>Локальные ответы</span> : null}
        <p className={styles.user}>
          <span>Пользователь</span>
          <strong data-testid="current-user">{user}</strong>
        </p>
        <RoleSelect role={role} onChange={onRoleChange} />
      </div>
    </header>
  );
}
