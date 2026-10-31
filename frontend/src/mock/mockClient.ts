import type { ChatClient } from "../api/client";
import type { AssistantReply, ChatMessage, DraftCampaign, JobView, RequestContext, SendAccepted } from "../api/types";
import { CANCEL_TEXT, CONFIRM_TEXT } from "../chat/phrases";
import {
  MOCK_SESSION_ID,
  campaignListReply,
  draftBlock,
  incompleteDraft,
  isDepositMoscowBrief,
  placeProposal,
  readyDraft,
  savedDraft,
  smartBanner,
  storyBanner,
  tagProposal,
  warningsReply,
} from "./fixtures";

type Phase = "empty" | "incomplete" | "ready" | "saved";

interface MockJob {
  seen: number;
  reply: AssistantReply | null;
  error: string | null;
}

export class MockChatClient implements ChatClient {
  private phase: Phase = "empty";
  private draft: DraftCampaign = {};
  private jobs = new Map<string, MockJob>();
  private seq = 0;

  async createSession(_ctx: RequestContext) {
    return { sessionId: MOCK_SESSION_ID };
  }

  async getMessages(_ctx: RequestContext, _sessionId: string): Promise<ChatMessage[]> {
    return [];
  }

  async sendMessage(_ctx: RequestContext, _sessionId: string, text: string): Promise<SendAccepted> {
    const jobId = `job-${++this.seq}`;
    const failed = text.trim().toLowerCase() === "ошибка";
    this.jobs.set(jobId, {
      seen: 0,
      reply: failed ? null : this.replyFor(text),
      error: failed ? "Не удалось разобрать реплику." : null,
    });
    return { jobId };
  }

  async getJob(_ctx: RequestContext, jobId: string): Promise<JobView> {
    const job = this.jobs.get(jobId);
    if (!job) return { status: "FAILED", result: null, error: "Задача не найдена" };
    job.seen += 1;
    if (job.seen < 2) return { status: "PENDING", result: null, error: null };
    if (job.error) return { status: "FAILED", result: null, error: job.error };
    return { status: "DONE", result: job.reply, error: null };
  }

  private replyFor(text: string): AssistantReply {
    const lower = text.trim().toLowerCase();
    if (lower === CONFIRM_TEXT) return this.confirm();
    if (lower === CANCEL_TEXT) return this.cancel();
    if (isDepositMoscowBrief(lower)) {
      this.phase = "incomplete";
      this.draft = incompleteDraft();
      return {
        text: "Собрал черновик кампании по вкладу для Москвы. Осталось уточнить время начала. Отправьте «подтверждаю», чтобы поставить 10:00 и открыть сохранение.",
        blocks: [draftBlock(this.draft, ["startTime"], false)],
      };
    }
    if (lower.includes("неизвестн")) {
      return {
        text: "Этот блок интерфейса мне незнаком, поэтому я его не показываю.",
        blocks: [{ type: "future_widget" }],
      };
    }
    if (lower.includes("предупреж")) return warningsReply();
    if (lower.includes("список") || lower.includes("покажи кампании")) return campaignListReply();
    if (lower.includes("тег")) {
      return {
        text: "Такого тега нет в справочнике. Можно создать его.",
        blocks: [{ type: "entity_proposal", proposal: tagProposal() }],
      };
    }
    if (lower.includes("место") && (lower.includes("созда") || lower.includes("нов"))) {
      return {
        text: "Такого места показа нет. Можно создать его.",
        blocks: [{ type: "entity_proposal", proposal: placeProposal() }],
      };
    }
    if (lower.includes("вариант")) {
      return {
        text: "Выберите следующий шаг.",
        blocks: [{ type: "quick_replies", options: [CONFIRM_TEXT, CANCEL_TEXT, "Покажи кампании"] }],
      };
    }
    if (lower.includes("смарт")) {
      return {
        text: "Так может выглядеть смарт-баннер.",
        blocks: [{ type: "creative_preview", banner: smartBanner() }],
      };
    }
    if (lower.includes("баннер") || lower.includes("креатив") || lower.includes("сторис")) {
      return {
        text: "Так история будет выглядеть у клиента.",
        blocks: [{ type: "creative_preview", banner: storyBanner() }],
      };
    }
    return {
      text: "Могу собрать кампанию по брифу. Напишите продукт, город и место показа или выберите пример в пустой ленте.",
      blocks: [],
    };
  }

  private confirm(): AssistantReply {
    if (this.phase === "ready" || this.phase === "saved") {
      this.phase = "saved";
      this.draft = savedDraft();
      return {
        text: "Кампания сохранена. Идентификатор 201.",
        blocks: [draftBlock(this.draft, [], true)],
      };
    }
    this.phase = "ready";
    this.draft = readyDraft();
    return {
      text: "Черновик готов к сохранению. Проверьте поля и нажмите «Сохранить».",
      blocks: [draftBlock(this.draft, [], true)],
    };
  }

  private cancel(): AssistantReply {
    return {
      text: "Предложение снято. Черновик кампании остался как был.",
      blocks: this.phase === "empty" ? [] : [draftBlock(this.draft, this.missing(), this.ready())],
    };
  }

  private missing(): string[] {
    return this.phase === "incomplete" ? ["startTime"] : [];
  }

  private ready(): boolean {
    return this.phase === "ready" || this.phase === "saved";
  }
}
