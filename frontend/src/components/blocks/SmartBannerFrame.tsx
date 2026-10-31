import type { BannerDraft } from "../../api/types";
import styles from "./SmartBannerFrame.module.css";

export function SmartBannerFrame({
  banner,
  image,
}: {
  banner: BannerDraft;
  image?: string | null;
}) {
  return (
    <div
      className={styles.banner}
      data-testid="smart-banner"
      style={
        image
          ? {
              backgroundImage: `linear-gradient(90deg, rgba(0,0,0,0.62) 0%, rgba(0,0,0,0.25) 60%, rgba(0,0,0,0.05) 100%), url(${image})`,
              backgroundSize: "cover",
              backgroundPosition: "center",
            }
          : undefined
      }
    >
      <div className={styles.copy}>
        {banner.title ? <h3>{banner.title}</h3> : null}
        {banner.text ? <p>{banner.text}</p> : null}
      </div>
      {banner.cta ? <span className={styles.cta}>{banner.cta}</span> : null}
    </div>
  );
}
