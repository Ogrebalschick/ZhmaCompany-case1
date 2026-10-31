import type { BannerDraft } from "../../api/types";
import styles from "./SmartBannerFrame.module.css";

export function SmartBannerFrame({ banner }: { banner: BannerDraft }) {
  return (
    <div className={styles.banner} data-testid="smart-banner">
      <div className={styles.copy}>
        {banner.title ? <h3>{banner.title}</h3> : null}
        {banner.text ? <p>{banner.text}</p> : null}
      </div>
      {banner.cta ? <span className={styles.cta}>{banner.cta}</span> : null}
    </div>
  );
}
