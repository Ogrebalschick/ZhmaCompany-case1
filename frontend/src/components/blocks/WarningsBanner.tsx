import styles from "./WarningsBanner.module.css";

export function WarningsBanner({ items }: { items: string[] }) {
  if (items.length === 0) return null;
  return (
    <aside className={styles.banner} data-testid="warnings">
      <p>Предупреждения</p>
      <ul>
        {items.map((item) => (
          <li key={item}>{item}</li>
        ))}
      </ul>
    </aside>
  );
}
