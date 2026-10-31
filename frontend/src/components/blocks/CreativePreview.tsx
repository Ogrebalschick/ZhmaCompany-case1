import type { BannerDraft } from "../../api/types";
import { bannerLimits, lengthLine, linkRule } from "../../labels/banner";
import { bannerTypeLabel } from "../../labels/fields";
import { SmartBannerFrame } from "./SmartBannerFrame";
import { StoryFrame } from "./StoryFrame";
import styles from "./CreativePreview.module.css";

export function CreativePreview({ banner }: { banner: BannerDraft }) {
  const story = banner.bannerType === "STORY";
  const limits = bannerLimits(banner.bannerType);
  const titleLine = limits ? lengthLine("Заголовок", banner.title, limits.title) : null;
  const textLine = limits ? lengthLine("Текст", banner.text, limits.text) : null;
  const rule = linkRule(banner.actionType, banner.link);
  return (
    <section className={styles.wrap} data-testid="creative-preview">
      <p className={styles.caption}>Так это увидит клиент</p>
      <p className={styles.meta}>
        {bannerTypeLabel(banner.bannerType)} · {banner.channel}
      </p>
      {story ? <StoryFrame banner={banner} /> : <SmartBannerFrame banner={banner} />}
      {titleLine ? (
        <p className={titleLine.over ? styles.warn : styles.meta}>{titleLine.text}</p>
      ) : null}
      {textLine ? (
        <p className={textLine.over ? styles.warn : styles.meta}>{textLine.text}</p>
      ) : null}
      {rule ? <p className={rule.warn ? styles.warn : styles.meta}>{rule.text}</p> : null}
      {banner.link ? <p className={styles.link}>Ссылка: {banner.link}</p> : null}
    </section>
  );
}
