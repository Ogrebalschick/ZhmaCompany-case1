import type { Role } from "../../api/types";
import { RoleSelect } from "./RoleSelect";
import styles from "./HeaderBar.module.css";

export function HeaderBar({
  user,
  role,
  sessionId,
  booting,
  mock,
  onRoleChange,
}: {
  user: string;
  role: Role;
  sessionId: string | null;
  booting: boolean;
  mock: boolean;
  onRoleChange: (role: Role) => void;
}) {
  const sessionLabel = sessionId
    ? `Сессия ${sessionId}`
    : booting
      ? "Сессия создаётся"
      : "Сессия не создана";
  return (
    <header className={styles.bar}>
      <div>
        <p className={styles.product}>Сборка кампании</p>
        <p className={styles.session} data-testid="session-id">
          {sessionLabel}
        </p>
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
