import type { ChatClient } from "../api/client";
import type { AssistantReply, ChatMessage, DraftCampaign, JobView, RequestContext, SendAccepted } from "../api/types";
import { CANCEL_TEXT, CONFIRM_TEXT } from "../chat/phrases";
import {
  AGE_26_REPLY,
  AGE_46_REPLY,
  MOCK_SESSION_ID,
  TOPIC_EVENT_REPLY,
  TOPIC_LIFESTYLE_REPLY,
  ageCodeFromText,
  campaignListReply,
  draftBlock,
  incompleteDraft,
  isDepositMoscowBrief,
  isOwnBrief,
  ownBriefDraft,
  ownMissing,
  placeProposal,
  readyDraft,
  savedDraft,
  smartBanner,
  storyBanner,
  tagProposal,
  topicCodeFromText,
  warningsReply,
} from "./fixtures";

type Phase = "empty" | "incomplete" | "ready" | "saved";
type DraftKind = "none" | "deposit" | "own";

interface MockJob {
  seen: number;
  reply: AssistantReply | null;
  error: string | null;
}

export class MockChatClient implements ChatClient {
  private phase: Phase = "empty";
  private kind: DraftKind = "none";
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
    if (isOwnBrief(lower)) {
      this.kind = "own";
      this.phase = "incomplete";
      this.draft = ownBriefDraft();
      return this.ownReply();
    }
    if (isDepositMoscowBrief(lower)) {
      this.kind = "deposit";
      this.phase = "incomplete";
      this.draft = incompleteDraft();
      return {
        text: "Собрал черновик кампании по вкладу для Москвы. Осталось уточнить время начала. Отправьте «подтверждаю», чтобы поставить 10:00 и открыть сохранение.",
        blocks: [draftBlock(this.draft, ["startTime"], false)],
      };
    }
    if (this.kind === "own") {
      const followUp = this.applyOwnDetails(text);
      if (followUp) return followUp;
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
      this.draft = this.kind === "own" ? { ...this.draft, savedCampaignId: 201 } : savedDraft();
      return {
        text: "Кампания сохранена. Идентификатор 201.",
        blocks: [draftBlock(this.draft, [], true)],
      };
    }
    if (this.kind === "own") {
      const missing = ownMissing(this.draft);
      if (missing.length > 0) {
        const reply = this.ownReply();
        return { ...reply, text: `Сохранять пока рано. ${reply.text}` };
      }
      this.phase = "ready";
      return {
        text: "Черновик готов к сохранению. Проверьте поля и нажмите «Сохранить».",
        blocks: [draftBlock(this.draft, [], true)],
      };
    }
    this.kind = "deposit";
    this.phase = "ready";
    this.draft = readyDraft();
    return {
      text: "Черновик готов к сохранению. Проверьте поля и нажмите «Сохранить».",
      blocks: [draftBlock(this.draft, [], true)],
    };
  }

  private applyOwnDetails(text: string): AssistantReply | null {
    const lower = text.trim().toLowerCase();
    const age = ageCodeFromText(lower);
    const topic = topicCodeFromText(lower);
    const date = text.match(/\d{4}-\d{2}-\d{2}/);
    const time = text.match(/(?:^|[^\d])(\d{2}:\d{2})/);
    if (!age && !topic && !date && !time) return null;

    let segments = [...(this.draft.includedSegments ?? [])];
    if (age) {
      segments = segments.filter((code) => code !== "AGE_26_45" && code !== "AGE_46_60");
      segments.push(age);
    }
    this.draft = {
      ...this.draft,
      includedSegments: segments,
      topic: topic ?? this.draft.topic,
      startDate: date ? date[0] : this.draft.startDate,
      startTime: time ? time[1] : this.draft.startTime,
    };
    this.phase = "incomplete";
    return this.ownReply();
  }

  private ownReply(): AssistantReply {
    const current = ownMissing(this.draft)[0];
    const blocks: AssistantReply["blocks"] = [draftBlock(this.draft, current ? [current] : [], false)];
    if (current === "startDate") {
      return {
        text: "Собрал черновик «Баня и аквапарк» для женщин в Москве, место показа — промо на главном. Напишите дату начала в виде ГГГГ-ММ-ДД. Сам её не поставлю.",
        blocks,
      };
    }
    if (current === "startTime") {
      return {
        text: "Дата записана. Напишите время начала в виде ЧЧ:ММ. Сам его не поставлю.",
        blocks,
      };
    }
    if (current === "topic") {
      blocks.push({ type: "quick_replies", options: [TOPIC_LIFESTYLE_REPLY, TOPIC_EVENT_REPLY] });
      return {
        text: "Темы «аквапарк» в справочнике нет. Подойдёт образ жизни или событие?",
        blocks,
      };
    }
    if (current === "segments") {
      blocks.push({ type: "quick_replies", options: [AGE_26_REPLY, AGE_46_REPLY] });
      return {
        text: "Возраст «старше 35» лежит между двумя активными сегментами, поэтому код не выбираю. Возраст 26–45, тип AGE, 24 000 000 клиентов. Возраст 46–60, тип AGE, 18 000 000 клиентов.",
        blocks,
      };
    }
    return {
      text: "Обязательные поля собраны. Отправьте «подтверждаю», чтобы открыть сохранение.",
      blocks,
    };
  }

  private cancel(): AssistantReply {
    return {
      text: "Предложение снято. Черновик кампании остался как был.",
      blocks: this.phase === "empty" ? [] : [draftBlock(this.draft, this.missing(), this.ready())],
    };
  }

  private missing(): string[] {
    if (this.phase !== "incomplete") return [];
    if (this.kind === "own") {
      const current = ownMissing(this.draft)[0];
      return current ? [current] : [];
    }
    return ["startTime"];
  }

  private ready(): boolean {
    return this.phase === "ready" || this.phase === "saved";
  }
}
