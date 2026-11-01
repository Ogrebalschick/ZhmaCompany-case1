import { useEffect, useState } from "react";
import type { BannerDraft } from "../../api/types";
import { bannerTypeLabel, orDash } from "../../labels/fields";
import { PromoScreen } from "./PromoScreen";
import { SmartBannerFrame } from "./SmartBannerFrame";
import { StoryFrame } from "./StoryFrame";
import { useBannerImage } from "./useBannerImage";
import styles from "./CreativePreview.module.css";

export function CreativePreview({ banner }: { banner: BannerDraft }) {
  const story = banner.bannerType === "STORY";
  const image = useBannerImage(banner);
  const [zoom, setZoom] = useState(false);
  const frame = (
    <span className={styles.frameWrap}>
      {story ? (
        <StoryFrame banner={banner} image={image.src} />
      ) : (
        <SmartBannerFrame banner={banner} image={image.src} />
      )}
      {image.status === "pending" ? (
        <span className={styles.generating}>
          <span className={styles.spinner} aria-hidden="true" />
          Идёт генерация картинки…
          <small>{formatTime(image.seconds)}</small>
        </span>
      ) : null}
    </span>
  );
  return (
    <section className={styles.wrap} data-testid="creative-preview">
      <p className={styles.caption}>Так это увидит клиент</p>
      <p className={styles.meta}>
        {bannerTypeLabel(banner.bannerType)} · {banner.channel}
      </p>
      <div className={styles.pair}>
        <button
          type="button"
          className={styles.zoomable}
          onClick={() => setZoom(true)}
          title="Открыть крупно"
        >
          {frame}
          <span className={styles.zoomHint}>⤢ Крупно</span>
        </button>
        <PromoScreen banner={banner} image={image.src} />
      </div>
      {zoom ? (
        <ZoomModal onClose={() => setZoom(false)}>
          <div className={story ? styles.zoomStory : styles.zoomBanner}>{frame}</div>
          <div className={styles.zoomActions}>
            {image.status === "done" && image.src && banner.imageId ? (
              <>
                <a href={image.src} target="_blank" rel="noreferrer">
                  Картинка отдельно
                </a>
                <a href={`/api/images/${banner.imageId}?download=1`} download>
                  Скачать PNG
                </a>
              </>
            ) : null}
            <button type="button" onClick={() => setZoom(false)}>
              Закрыть
            </button>
          </div>
        </ZoomModal>
      ) : null}
      {image.status === "done" && image.src && banner.imageId ? (
        <p className={styles.imageActions}>
          <a href={image.src} target="_blank" rel="noreferrer">
            Открыть картинку
          </a>
          <a href={`/api/images/${banner.imageId}?download=1`} download>
            Скачать PNG
          </a>
          <span>{image.watermarked ? "с водяным знаком до модерации" : "без водяного знака"}</span>
        </p>
      ) : null}
      {image.status === "pending" ? (
        <p className={styles.imageStatus}>
          <span className={styles.spinner} aria-hidden="true" /> Рисую картинку…{" "}
          {formatTime(image.seconds)}
        </p>
      ) : null}
      {image.status === "failed" ? (
        <p className={styles.imageError}>
          Картинку нарисовать не удалось{image.error ? `: ${image.error}` : ""}. Напишите «сделай
          картинку» ещё раз.
        </p>
      ) : null}
      {banner.link ? <p className={styles.link}>Ссылка: {banner.link}</p> : null}
      {banner.actionType && banner.actionType !== "default" ? (
        <p className={styles.link}>Действие: {orDash(banner.actionType)}</p>
      ) : null}
    </section>
  );
}

function formatTime(seconds: number): string {
  const m = Math.floor(seconds / 60);
  const s = seconds % 60;
  return m > 0 ? `${m}:${String(s).padStart(2, "0")}` : `${s} с`;
}

function ZoomModal({ children, onClose }: { children: React.ReactNode; onClose: () => void }) {
  useEffect(() => {
    const onKey = (event: KeyboardEvent) => {
      if (event.key === "Escape") onClose();
    };
    window.addEventListener("keydown", onKey);
    return () => window.removeEventListener("keydown", onKey);
  }, [onClose]);
  return (
    <div className={styles.overlay} role="dialog" aria-modal="true" onClick={onClose}>
      <div className={styles.modal} onClick={(event) => event.stopPropagation()}>
        {children}
      </div>
    </div>
  );
}
