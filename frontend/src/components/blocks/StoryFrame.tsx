import type { BannerDraft } from "../../api/types";
import styles from "./StoryFrame.module.css";

export function StoryFrame({ banner, image }: { banner: BannerDraft; image?: string | null }) {
  return (
    <div className={styles.phone} data-testid="story-frame">
      <div
        className={image ? `${styles.plate} ${styles.withImage}` : styles.plate}
        style={
          image
            ? {
                backgroundImage: `linear-gradient(180deg, rgba(0,0,0,0.45) 0%, rgba(0,0,0,0) 45%, rgba(0,0,0,0.55) 100%), url(${image})`,
              }
            : undefined
        }
      >
        {banner.title ? <h3>{banner.title}</h3> : null}
        {banner.text ? <p>{banner.text}</p> : null}
        {banner.cta ? <span className={styles.cta}>{banner.cta}</span> : null}
      </div>
    </div>
  );
}
