import { useEffect, useState } from "react";
import styles from "./PendingStatus.module.css";

const PHASES = [
  { after: 0, text: "Читаю сообщение" },
  { after: 3, text: "Разбираю бриф и сверяю со справочником" },
  { after: 10, text: "Модель думает над ответом" },
  { after: 30, text: "Модель отвечает дольше обычного, ещё немного" },
  { after: 90, text: "Всё ещё жду модель. Если долго — проверьте, что LLM-сервер доступен" },
];

/** Индикатор, пока ассистент собирает ответ: фраза по этапу, анимация и секундомер. */
export function PendingStatus() {
  const [seconds, setSeconds] = useState(0);
  useEffect(() => {
    const started = Date.now();
    const timer = window.setInterval(
      () => setSeconds(Math.floor((Date.now() - started) / 1000)),
      500,
    );
    return () => window.clearInterval(timer);
  }, []);
  const phase = [...PHASES].reverse().find((item) => seconds >= item.after) ?? PHASES[0];
  return (
    <div className={styles.status} role="status" aria-live="polite" data-testid="pending-status">
      <span className={styles.dots} aria-hidden="true">
        <i />
        <i />
        <i />
      </span>
      <span>{phase.text}</span>
      <span className={styles.timer}>{seconds} с</span>
    </div>
  );
}
