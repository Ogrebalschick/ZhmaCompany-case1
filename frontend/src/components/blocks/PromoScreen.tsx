import { useState } from "react";
import type { BannerDraft } from "../../api/types";
import styles from "./PromoScreen.module.css";

/** Тот же набор полей, из которого сервер собирает дерево PROMO при сохранении баннера. */
export function PromoScreen({ banner, image }: { banner: BannerDraft; image?: string | null }) {
  const [note, setNote] = useState<string | null>(null);

  function onCta() {
    const action = banner.actionType ?? "default";
    const link = banner.link?.trim();
    if (action === "externalLink" || action === "openSmartLink") {
      if (!link) {
        setNote("Ссылка не задана");
        return;
      }
      window.open(link, "_blank", "noopener,noreferrer");
      setNote("Открыта ссылка");
      return;
    }
    if (action === "closeCampaign") {
      setNote("Кампания закрыта");
      return;
    }
    if (action === "doneCampaign") {
      setNote("Кампания завершена");
      return;
    }
    setNote("Кнопка нажата");
  }

  return (
    <article className={styles.screen} data-testid="promo-screen">
      <p className={styles.kicker}>Промо-экран</p>
      {banner.title ? <h3>{banner.title}</h3> : null}
      {image ? <img className={styles.image} src={image} alt="" /> : null}
      {banner.text ? <p>{banner.text}</p> : null}
      {banner.cta ? (
        <button type="button" className={styles.cta} onClick={onCta}>
          {banner.cta}
        </button>
      ) : null}
      {note ? <p className={styles.note}>{note}</p> : null}
    </article>
  );
}
