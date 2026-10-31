import { useRef, type ReactNode } from "react";
import styles from "./AppShell.module.css";

export function AppShell({
  header,
  thread,
  rail,
  railWidth,
  onRailWidth,
}: {
  header: ReactNode;
  thread: ReactNode;
  rail: ReactNode;
  railWidth: number | null;
  onRailWidth: (width: number) => void;
}) {
  const drag = useRef<{ x: number; width: number } | null>(null);

  return (
    <div className={styles.shell}>
      <div className={styles.enter}>{header}</div>
      <div
        className={styles.body}
        style={
          railWidth == null ? undefined : { gridTemplateColumns: `minmax(0, 1fr) ${railWidth}px` }
        }
      >
        <main className={styles.thread}>{thread}</main>
        <div className={styles.railSlot}>
          {railWidth == null ? null : (
            <button
              type="button"
              className={styles.handle}
              aria-label="Ширина черновика"
              data-testid="rail-resize"
              onPointerDown={(event) => {
                drag.current = { x: event.clientX, width: railWidth };
                event.currentTarget.setPointerCapture(event.pointerId);
              }}
              onPointerMove={(event) => {
                if (!drag.current) return;
                const max = Math.max(320, Math.round(window.innerWidth / 2));
                const next = drag.current.width + (drag.current.x - event.clientX);
                onRailWidth(Math.min(max, Math.max(320, next)));
              }}
              onPointerUp={() => {
                drag.current = null;
              }}
            />
          )}
          {rail}
        </div>
      </div>
    </div>
  );
}
