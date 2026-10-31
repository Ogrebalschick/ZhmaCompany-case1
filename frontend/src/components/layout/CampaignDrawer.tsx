import { useEffect, useRef } from "react";
import type { CampaignListItem } from "../../api/types";
import { CampaignListRow } from "../blocks/CampaignListRow";
import styles from "./CampaignDrawer.module.css";

export const CAMPAIGN_DRAWER_WIDTH = 320;

export function CampaignDrawer({
  reveal,
  dragging,
  campaigns,
  onReveal,
  onDragging,
  onNew,
  onSelect,
}: {
  reveal: number;
  dragging: boolean;
  campaigns: CampaignListItem[];
  onReveal: (width: number) => void;
  onDragging: (dragging: boolean) => void;
  onNew: () => void;
  onSelect: (id: number) => void;
}) {
  const drag = useRef<{ x: number; start: number; latest: number } | null>(null);
  const open = reveal > 0;

  useEffect(() => {
    if (!open) return;
    const onKey = (event: KeyboardEvent) => {
      if (event.key === "Escape") onReveal(0);
    };
    window.addEventListener("keydown", onKey);
    return () => window.removeEventListener("keydown", onKey);
  }, [open, onReveal]);

  return (
    <div className={styles.layer} style={{ ["--reveal" as string]: `${reveal}px` }}>
      <button
        type="button"
        className={styles.scrim}
        data-open={open ? "yes" : "no"}
        data-dragging={dragging ? "yes" : "no"}
        aria-label="Закрыть список кампаний"
        tabIndex={open ? 0 : -1}
        onClick={() => onReveal(0)}
      />
      <aside
        className={styles.drawer}
        data-testid="campaign-drawer"
        data-open={reveal >= CAMPAIGN_DRAWER_WIDTH ? "yes" : "no"}
        inert={reveal === 0 ? true : undefined}
        data-dragging={dragging ? "yes" : "no"}
        aria-hidden={open ? undefined : true}
      >
        <div className={styles.head}>
          <h2>Кампании</h2>
        </div>
        <ul>
          <li>
            <button
              type="button"
              className={styles.newCampaign}
              data-testid="campaign-new"
              onClick={onNew}
            >
              <span className={styles.plus} aria-hidden="true" />
              Новая кампания
            </button>
          </li>
          {campaigns.map((item) => (
            <CampaignListRow key={item.id} item={item} onSelect={() => onSelect(item.id)} />
          ))}
        </ul>
        {campaigns.length === 0 ? <p className={styles.empty}>Список пуст</p> : null}
      </aside>
      <button
        type="button"
        className={styles.handle}
        aria-label="Потянуть список кампаний"
        data-testid="campaign-drawer-handle"
        onPointerDown={(event) => {
          drag.current = { x: event.clientX, start: reveal, latest: reveal };
          onDragging(true);
          event.currentTarget.setPointerCapture(event.pointerId);
        }}
        onPointerMove={(event) => {
          if (!drag.current) return;
          const next = drag.current.start + (event.clientX - drag.current.x);
          const clamped = Math.min(CAMPAIGN_DRAWER_WIDTH, Math.max(0, next));
          drag.current.latest = clamped;
          onReveal(clamped);
        }}
        onPointerUp={() => {
          if (!drag.current) return;
          const latest = drag.current.latest;
          drag.current = null;
          onDragging(false);
          onReveal(latest > CAMPAIGN_DRAWER_WIDTH / 2 ? CAMPAIGN_DRAWER_WIDTH : 0);
        }}
      />
    </div>
  );
}
