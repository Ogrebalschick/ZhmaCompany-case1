import type { BannerDraft } from "../../api/types";
import styles from "./PromoScreen.module.css";

/** Тот же набор полей, из которого сервер собирает дерево PROMO при сохранении баннера. */
export function PromoScreen({ banner, image }: { banner: BannerDraft; image?: string | null }) {
  return (
    <article className={styles.screen} data-testid="promo-screen">
      <p className={styles.kicker}>Промо-экран</p>
      {banner.title ? <h3>{banner.title}</h3> : null}
      {image ? <img className={styles.image} src={image} alt="" /> : null}
      {banner.text ? <p>{banner.text}</p> : null}
      {banner.cta ? <span className={styles.cta}>{banner.cta}</span> : null}
    </article>
  );
}
