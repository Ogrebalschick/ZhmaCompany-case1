import { useEffect, useState } from "react";
import { PENDING_PHRASES, PENDING_STATUS } from "../../chat/phrases";
import styles from "./PendingStatus.module.css";

const PHRASE_MS = 2200;

export function PendingStatus() {
  const [index, setIndex] = useState(0);
  const [reduced, setReduced] = useState(
    () => window.matchMedia("(prefers-reduced-motion: reduce)").matches,
  );

  useEffect(() => {
    const query = window.matchMedia("(prefers-reduced-motion: reduce)");
    const apply = () => setReduced(query.matches);
    query.addEventListener("change", apply);
    return () => query.removeEventListener("change", apply);
  }, []);

  useEffect(() => {
    if (reduced) return;
    const timer = window.setInterval(() => {
      setIndex((value) => (value + 1) % PENDING_PHRASES.length);
    }, PHRASE_MS);
    return () => window.clearInterval(timer);
  }, [reduced]);

  if (reduced) {
    return (
      <p className={styles.status} role="status" aria-live="polite" data-testid="pending-status">
        {PENDING_STATUS}
      </p>
    );
  }

  return (
    <p className={styles.status} role="status" data-testid="pending-status">
      <span className={styles.stable}>{PENDING_STATUS}</span>
      <span key={index} className={styles.phrase} aria-hidden="true">
        {PENDING_PHRASES[index]}
      </span>
    </p>
  );
}
