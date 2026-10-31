import type { BannerDraft } from "../../api/types";
import { plateStyle } from "../../labels/banner";
import styles from "./StoryFrame.module.css";

export function StoryFrame({ banner }: { banner: BannerDraft }) {
  return (
    <div className={styles.phone} data-testid="story-frame">
      <div className={styles.plate} style={plateStyle(banner.backgroundUrl)}>
        {banner.title ? <h3>{banner.title}</h3> : null}
        {banner.text ? <p>{banner.text}</p> : null}
        {banner.cta ? <span className={styles.cta}>{banner.cta}</span> : null}
      </div>
    </div>
  );
}
