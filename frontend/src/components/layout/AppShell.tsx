import type { ReactNode } from "react";
import styles from "./AppShell.module.css";

export function AppShell({
  header,
  thread,
  rail,
}: {
  header: ReactNode;
  thread: ReactNode;
  rail: ReactNode;
}) {
  return (
    <div className={styles.shell}>
      {header}
      <div className={styles.body}>
        <main className={styles.thread}>{thread}</main>
        {rail}
      </div>
    </div>
  );
}
