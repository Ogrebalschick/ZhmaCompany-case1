import { errorText } from "./errors";
import { requestHeaders } from "./headers";
import { asCampaignItem, normalizeMessage, normalizeReply } from "./guards";
import type { ChatClient } from "./client";
import type {
  CampaignListItem,
  ChatMessage,
  DraftCampaign,
  JobStatus,
  JobView,
  RequestContext,
  SendAccepted,
  SessionCreated,
} from "./types";

const API = "/api";

export class HttpChatClient implements ChatClient {
  async createSession(ctx: RequestContext): Promise<SessionCreated> {
    const body = await this.request(ctx, `${API}/sessions`, { method: "POST" });
    if (
      !body ||
      typeof body !== "object" ||
      typeof (body as { sessionId?: unknown }).sessionId !== "string"
    ) {
      throw new Error("Сервер не вернул сессию.");
    }
    return { sessionId: (body as { sessionId: string }).sessionId };
  }

  async getMessages(ctx: RequestContext, sessionId: string): Promise<ChatMessage[]> {
    const body = await this.request(
      ctx,
      `${API}/sessions/${encodeURIComponent(sessionId)}/messages`,
    );
    const list = Array.isArray(body)
      ? body
      : body && typeof body === "object" && Array.isArray((body as { messages?: unknown }).messages)
        ? (body as { messages: unknown[] }).messages
        : null;
    if (!list) throw new Error("Сервер вернул историю в неожиданном виде.");
    return list.flatMap((item) => {
      const message = normalizeMessage(item);
      return message ? [message] : [];
    });
  }

  async sendMessage(ctx: RequestContext, sessionId: string, text: string): Promise<SendAccepted> {
    const body = await this.request(
      ctx,
      `${API}/sessions/${encodeURIComponent(sessionId)}/messages`,
      {
        method: "POST",
        body: JSON.stringify({ text }),
      },
    );
    if (
      !body ||
      typeof body !== "object" ||
      typeof (body as { jobId?: unknown }).jobId !== "string"
    ) {
      throw new Error("Сервер не принял сообщение.");
    }
    return { jobId: (body as { jobId: string }).jobId };
  }

  async listCampaigns(ctx: RequestContext): Promise<CampaignListItem[]> {
    const body = await this.request(ctx, `${API}/campaigns`);
    if (!Array.isArray(body)) throw new Error("Сервер вернул список кампаний в неожиданном виде.");
    return body.flatMap((item) => {
      const row = asCampaignItem(item);
      return row ? [row] : [];
    });
  }

  async openCampaign(ctx: RequestContext, id: number): Promise<ChatMessage[]> {
    const items = await this.listCampaigns(ctx);
    const item = items.find((row) => row.id === id);
    if (!item) return [{ role: "assistant", text: "Такой кампании нет в списке." }];
    const draft: DraftCampaign = {
      name: item.name,
      marketingName: item.name,
      startDate: item.startDate,
      endDate: item.endDate ?? null,
      topic: item.topic ?? null,
      savedCampaignId: item.id,
    };
    return [
      {
        role: "assistant",
        text: `Кампания «${item.name}». Отдельной переписки нет: сессия одна и не привязана к кампании.`,
        blocks: [{ type: "campaign_draft", draft, missing: [], readyToSave: false }],
      },
    ];
  }

  async closeCampaign(): Promise<void> {}

  async getJob(ctx: RequestContext, jobId: string): Promise<JobView> {
    const body = await this.request(ctx, `${API}/jobs/${encodeURIComponent(jobId)}`);
    if (!body || typeof body !== "object") throw new Error("Сервер вернул пустой статус задачи.");
    const raw = body as { status?: unknown; result?: unknown; error?: unknown };
    const status = raw.status;
    if (!isJobStatus(status)) throw new Error("Сервер вернул неизвестный статус задачи.");
    return {
      status,
      result: raw.result == null ? null : normalizeReply(raw.result),
      error: typeof raw.error === "string" ? raw.error : null,
    };
  }

  private async request(
    ctx: RequestContext,
    path: string,
    init: RequestInit = {},
  ): Promise<unknown> {
    let response: Response;
    try {
      response = await fetch(path, {
        ...init,
        headers: { ...requestHeaders(ctx), ...(init.headers ?? {}) },
      });
    } catch (error) {
      throw new TypeError(errorText(error), { cause: error });
    }
    if (!response.ok) throw new Error(await readError(response));
    if (response.status === 204) return null;
    try {
      return (await response.json()) as unknown;
    } catch {
      throw new Error("Сервер вернул неразборчивый ответ.");
    }
  }
}

function isJobStatus(value: unknown): value is JobStatus {
  return value === "PENDING" || value === "DONE" || value === "FAILED";
}

async function readError(response: Response): Promise<string> {
  try {
    const body = (await response.json()) as {
      error?: unknown;
      message?: unknown;
      errors?: unknown;
    };
    if (typeof body.error === "string" && body.error.trim()) return body.error;
    if (typeof body.message === "string" && body.message.trim()) return body.message;
    const fromList = firstErrorMessage(body.errors);
    if (fromList) return fromList;
  } catch {
    return `Сервер ответил с ошибкой ${response.status}.`;
  }
  return `Сервер ответил с ошибкой ${response.status}.`;
}

function firstErrorMessage(errors: unknown): string | null {
  if (!Array.isArray(errors)) return null;
  for (const item of errors) {
    if (!item || typeof item !== "object") continue;
    const message = (item as { message?: unknown }).message;
    if (typeof message === "string" && message.trim()) return message;
  }
  return null;
}
