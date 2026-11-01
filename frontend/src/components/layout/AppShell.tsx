import { useRef, type ReactNode } from "react";
import { SIDEBAR_COLLAPSED, clampSidebarWidth } from "./sidebarWidth";
import styles from "./AppShell.module.css";

export function AppShell({
  header,
  sidebar,
  thread,
  rail,
  railWidth,
  onRailWidth,
  sidebarWidth,
  sidebarCollapsed,
  onSidebarWidth,
}: {
  header: ReactNode;
  sidebar?: ReactNode;
  thread: ReactNode;
  rail: ReactNode;
  railWidth: number | null;
  onRailWidth: (width: number) => void;
  sidebarWidth: number | null;
  sidebarCollapsed: boolean;
  onSidebarWidth: (width: number) => void;
}) {
  const drag = useRef<{ x: number; width: number } | null>(null);
  const sideDrag = useRef<{ x: number; width: number } | null>(null);
  const frameStyle =
    sidebarWidth == null
      ? undefined
      : {
          gridTemplateColumns: `${
            sidebarCollapsed ? SIDEBAR_COLLAPSED : clampSidebarWidth(sidebarWidth)
          }px minmax(0, 1fr)`,
        };

  return (
    <div className={styles.frame} style={frameStyle}>
      {sidebar ? (
        <aside className={styles.sidebar}>
          {sidebar}
          {sidebarWidth != null && !sidebarCollapsed ? (
            <button
              type="button"
              className={styles.sideHandle}
              aria-label="Ширина списка кампаний"
              data-testid="sidebar-resize"
              onPointerDown={(event) => {
                sideDrag.current = { x: event.clientX, width: clampSidebarWidth(sidebarWidth) };
                event.currentTarget.setPointerCapture(event.pointerId);
              }}
              onPointerMove={(event) => {
                if (!sideDrag.current) return;
                const next = sideDrag.current.width + (event.clientX - sideDrag.current.x);
                onSidebarWidth(clampSidebarWidth(next));
              }}
              onPointerUp={() => {
                sideDrag.current = null;
              }}
            />
          ) : null}
        </aside>
      ) : null}
      <div className={styles.shell}>
        {header}
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
    </div>
  );
}
