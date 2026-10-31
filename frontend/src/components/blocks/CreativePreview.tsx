import type { BannerDraft } from "../../api/types";
import { bannerTypeLabel, orDash } from "../../labels/fields";
import { SmartBannerFrame } from "./SmartBannerFrame";
import { StoryFrame } from "./StoryFrame";
import styles from "./CreativePreview.module.css";

export function CreativePreview({ banner }: { banner: BannerDraft }) {
  const story = banner.bannerType === "STORY";
  return (
    <section className={styles.wrap} data-testid="creative-preview">
      <p className={styles.caption}>Так это увидит клиент</p>
      <p className={styles.meta}>
        {bannerTypeLabel(banner.bannerType)} · {banner.channel}
      </p>
      {story ? <StoryFrame banner={banner} /> : <SmartBannerFrame banner={banner} />}
      {banner.link ? <p className={styles.link}>Ссылка: {banner.link}</p> : null}
      {banner.actionType && banner.actionType !== "default" ? (
        <p className={styles.link}>Действие: {orDash(banner.actionType)}</p>
      ) : null}
    </section>
  );
}
