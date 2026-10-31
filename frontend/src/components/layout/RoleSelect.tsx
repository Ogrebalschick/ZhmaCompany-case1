import type { Role } from "../../api/types";
import styles from "./RoleSelect.module.css";

const ROLES: { value: Role; label: string }[] = [
  { value: "campaignManager", label: "campaignManager, менеджер" },
  { value: "campaignModerator", label: "campaignModerator, модератор" },
];

export function RoleSelect({ role, onChange }: { role: Role; onChange: (role: Role) => void }) {
  return (
    <label className={styles.label} htmlFor="role">
      Роль
      <select
        id="role"
        className={styles.select}
        data-testid="role-select"
        value={role}
        onChange={(event) => onChange(event.target.value as Role)}
      >
        {ROLES.map((item) => (
          <option key={item.value} value={item.value}>
            {item.label}
          </option>
        ))}
      </select>
    </label>
  );
}
