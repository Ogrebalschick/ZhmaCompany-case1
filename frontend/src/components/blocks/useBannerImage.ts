import { useEffect, useState } from "react";
import type { BannerDraft } from "../../api/types";

export interface BannerImageState {
  /** Адрес готовой картинки или null. */
  src: string | null;
  status: "none" | "pending" | "done" | "failed";
  /** Картинка отдаётся с водяным знаком: кампания ещё не прошла модерацию. */
  watermarked: boolean;
  error: string | null;
  seconds: number;
}

const POLL_MS = 4000;

/**
 * Следит за картинкой баннера: картинка рисуется в фоне минуты, поэтому опрашиваем
 * `GET /api/images/{id}/status`, пока она не будет готова.
 */
export function useBannerImage(banner: BannerDraft): BannerImageState {
  const id = banner.imageId ?? null;
  const url = banner.imageUrl ?? null;
  const [state, setState] = useState<BannerImageState>(() => ({
    src: id ? null : url,
    status: id ? "pending" : url ? "done" : "none",
    error: null,
    seconds: 0,
    watermarked: true,
  }));

  useEffect(() => {
    if (!id) return;
    let stopped = false;
    const started = Date.now();
    const tick = async () => {
      try {
        const response = await fetch(`/api/images/${id}/status`);
        if (stopped) return;
        if (response.ok) {
          const body = (await response.json()) as {
            status?: string;
            error?: string | null;
            watermarked?: boolean;
          };
          if (body.status === "DONE") {
            setState({
              src: `/api/images/${id}?v=${body.watermarked ? "w" : "c"}`,
              status: "done",
              error: null,
              seconds: 0,
              watermarked: body.watermarked !== false,
            });
            return;
          }
          if (body.status === "FAILED") {
            setState({
              src: null,
              status: "failed",
              error: body.error ?? null,
              seconds: 0,
              watermarked: true,
            });
            return;
          }
        } else if (response.status === 404) {
          setState({
            src: null,
            status: "failed",
            error: "Картинка не найдена (сервер перезапускался?)",
            seconds: 0,
            watermarked: true,
          });
          return;
        }
      } catch {
        // сеть моргнула — попробуем ещё раз
      }
      if (stopped) return;
      setState((prev) => ({
        ...prev,
        status: "pending",
        seconds: Math.floor((Date.now() - started) / 1000),
      }));
      window.setTimeout(() => void tick(), POLL_MS);
    };
    void tick();
    return () => {
      stopped = true;
    };
  }, [id, url]);

  return state;
}
