import { useEffect, useState } from "react";
import styles from "./HeaderBar.module.css";

interface Health {
  llm: { ok: boolean; model?: string | null; note?: string | null; latencyMs?: number | null };
  images: { ok: boolean; model?: string | null; note?: string | null };
}

/** Значок в шапке: на связи ли модель и генерация картинок (`GET /api/health`, раз в 30 секунд). */
export function HealthBadge() {
  const [health, setHealth] = useState<Health | null>(null);
  const [down, setDown] = useState(false);
  useEffect(() => {
    let stopped = false;
    const load = async () => {
      try {
        const response = await fetch("/api/health");
        if (!response.ok) throw new Error(String(response.status));
        const body = (await response.json()) as Health;
        if (!stopped) {
          setHealth(body);
          setDown(false);
        }
      } catch {
        if (!stopped) setDown(true);
      }
    };
    void load();
    const timer = window.setInterval(() => void load(), 30000);
    return () => {
      stopped = true;
      window.clearInterval(timer);
    };
  }, []);
  if (down) return <span className={`${styles.health} ${styles.bad}`}>Сервер недоступен</span>;
  if (!health) return null;
  const llmTitle = health.llm.note ?? `${health.llm.model ?? ""} ${health.llm.latencyMs ?? ""} мс`;
  return (
    <span className={styles.healthGroup}>
      <span
        className={`${styles.health} ${health.llm.ok ? styles.good : styles.bad}`}
        title={llmTitle}
      >
        LLM {health.llm.ok ? "на связи" : "недоступна"}
      </span>
      <span
        className={`${styles.health} ${health.images.ok ? styles.good : styles.bad}`}
        title={health.images.note ?? health.images.model ?? ""}
      >
        Картинки {health.images.ok ? "on" : "off"}
      </span>
    </span>
  );
}
