import type { ChatClient } from "../api/client";
import type {
  AssistantReply,
  CampaignListItem,
  ChatMessage,
  DraftCampaign,
  IncomingBlock,
  JobView,
  RequestContext,
  SendAccepted,
} from "../api/types";
import { CANCEL_TEXT, CONFIRM_TEXT } from "../chat/phrases";
import {
  AGE_26_REPLY,
  AGE_46_REPLY,
  MOCK_SESSION_ID,
  TOPIC_EVENT_REPLY,
  TOPIC_LIFESTYLE_REPLY,
  WEIGHT_OFFER,
  ageCodeFromText,
  applyServerMetrics,
  CAMPAIGN_ROWS,
  campaignListReply,
  CITY_MSK_REASON,
  FEMALE_REASON,
  INACTIVE_PLACE_WARNING,
  PLACE_MAIN_REASON,
  choicesFor,
  draftBlock,
  incompleteDraft,
  isDepositMoscowBrief,
  isOwnBrief,
  ownBriefDraft,
  ownMissing,
  premiumCardDraft,
  ctrFromText,
  isWideReach,
  placeProposal,
  readyDraft,
  resolveSchedule,
  savedDraft,
  smartBanner,
  storyBanner,
  tagProposal,
  topicCodeFromText,
  utcToday,
  warningsReply,
  weightFromText,
} from "./fixtures";

type Phase = "empty" | "incomplete" | "ready" | "saved";
type DraftKind = "none" | "deposit" | "own" | "card";

interface DraftSnapshot {
  phase: Phase;
  kind: DraftKind;
  draft: DraftCampaign;
}

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
  private snapshot: DraftSnapshot | null = null;

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

  async listCampaigns(_ctx: RequestContext): Promise<CampaignListItem[]> {
    return CAMPAIGN_ROWS;
  }

  async openCampaign(_ctx: RequestContext, id: number): Promise<ChatMessage[]> {
    this.remember();
    if (id === 201) {
      this.kind = "deposit";
      this.phase = "incomplete";
      this.draft = incompleteDraft();
      const reply = this.depositOpening();
      return [{ role: "assistant", text: reply.text, blocks: reply.blocks }];
    }
    if (id === 318) {
      this.kind = "card";
      this.phase = "incomplete";
      this.draft = premiumCardDraft();
      return [
        {
          role: "assistant",
          text: "Кампания «Премиальная карта» уже идёт, показ включён.",
          blocks: this.draftBlocks([], false),
        },
      ];
    }
    return [{ role: "assistant", text: "Такой кампании нет в списке." }];
  }

  async closeCampaign(): Promise<void> {
    if (!this.snapshot) return;
    this.phase = this.snapshot.phase;
    this.kind = this.snapshot.kind;
    this.draft = this.snapshot.draft;
    this.snapshot = null;
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
      return this.depositOpening();
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
        blocks: [
          { type: "quick_replies", options: [CONFIRM_TEXT, CANCEL_TEXT, "Покажи кампании"] },
        ],
      };
    }
    if (lower.includes("длинн")) {
      return {
        text: "Заголовок длиннее 100 символов для STORY. Макет не показываю и в черновик не пишу.",
        blocks: [],
      };
    }
    if (lower.includes("внешн")) {
      return {
        text: "Для externalLink нужна ссылка. Макет не показываю и в черновик не пишу.",
        blocks: [],
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
    if (this.kind === "card") {
      return {
        text: "Эта кампания уже сохранена. Новая собирается пунктом «Новая кампания» слева.",
        blocks: this.draftBlocks([], false),
      };
    }
    if (this.phase === "saved") {
      return {
        text: "Кампания уже сохранена. Идентификатор 201.",
        blocks: [],
      };
    }
    if (this.phase === "ready") {
      const serverNote = serverMetricNote(this.draft);
      this.phase = "saved";
      const saved = this.kind === "own" ? { ...this.draft, savedCampaignId: 201 } : savedDraft();
      this.draft = applyServerMetrics(saved);
      return {
        text: `Кампания сохранена. Идентификатор 201.${serverNote}`,
        blocks: this.draftBlocks([], false),
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
        blocks: this.draftBlocks([], true),
      };
    }
    this.kind = "deposit";
    this.phase = "ready";
    this.draft = readyDraft();
    return {
      text: "Черновик готов к сохранению. Проверьте поля и нажмите «Сохранить».",
      blocks: this.draftBlocks([], true),
    };
  }

  private applyOwnDetails(text: string): AssistantReply | null {
    const lower = text.trim().toLowerCase();
    const age = ageCodeFromText(lower);
    const topic = topicCodeFromText(lower);
    const time = text.match(/(?:^|[^\d])(\d{2}:\d{2})/);
    const schedule = resolveSchedule(text, utcToday(), this.draft);
    const weight = weightFromText(lower);
    const ctr = ctrFromText(lower);
    const reach = isWideReach(lower) && weight === null;
    if (
      !age &&
      !topic &&
      !time &&
      !schedule.touched &&
      schedule.notes.length === 0 &&
      weight === null &&
      ctr === null &&
      !reach
    ) {
      return null;
    }

    let segments = [...(this.draft.includedSegments ?? [])];
    if (age) {
      segments = segments.filter((code) => code !== "AGE_26_45" && code !== "AGE_46_60");
      segments.push(age);
    }
    this.draft = {
      ...this.draft,
      includedSegments: segments,
      topic: topic ?? this.draft.topic,
      startDate:
        schedule.touched || schedule.notes.length > 0 ? schedule.startDate : this.draft.startDate,
      endDate:
        schedule.touched || schedule.notes.length > 0 ? schedule.endDate : this.draft.endDate,
      startTime: time ? time[1] : this.draft.startTime,
      weight: weight ?? this.draft.weight,
      initialCtr: ctr ?? this.draft.initialCtr,
    };
    this.phase = "incomplete";
    const reply = this.ownReply();
    let message =
      schedule.notes.length > 0 ? `${schedule.notes.join(" ")} ${reply.text}` : reply.text;
    if (reach && this.draft.weight == null) {
      const hasButtons = reply.blocks.some((block) => block.type === "quick_replies");
      message = hasButtons
        ? `${message} Широкий охват сам вес не ставит. Предлагаю вес 10. Напишите «вес 10».`
        : `${message} Широкий охват сам вес не ставит. Предлагаю вес 10.`;
      if (!hasButtons) reply.blocks.push({ type: "quick_replies", options: [WEIGHT_OFFER] });
    }
    return { ...reply, text: message };
  }

  private ownReply(): AssistantReply {
    const current = ownMissing(this.draft)[0];
    const blocks = this.draftBlocks(current ? [current] : [], false);
    if (current === "startDate") {
      return {
        text: `Собрал черновик «Баня и аквапарк» для женщин в Москве, место показа — промо на главном. ${FEMALE_REASON}. ${CITY_MSK_REASON}. ${PLACE_MAIN_REASON}. Напишите дату начала в виде ГГГГ-ММ-ДД. Понятны «с завтра», «на две недели» и «до конца года». Сам дату не выдумаю.`,
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
      blocks: this.phase === "empty" ? [] : this.draftBlocks(this.missing(), this.ready()),
    };
  }

  private remember(): void {
    if (this.snapshot) return;
    this.snapshot = {
      phase: this.phase,
      kind: this.kind,
      draft: {
        ...this.draft,
        tagIds: [...(this.draft.tagIds ?? [])],
        includedSegments: [...(this.draft.includedSegments ?? [])],
        excludedSegments: [...(this.draft.excludedSegments ?? [])],
        placeIds: [...(this.draft.placeIds ?? [])],
      },
    };
  }

  private depositOpening(): AssistantReply {
    return {
      text: `Собрал черновик кампании по вкладу для Москвы. ${CITY_MSK_REASON}. ${PLACE_MAIN_REASON}. Осталось уточнить время начала. Отправьте «подтверждаю», чтобы поставить 10:00 и открыть сохранение.`,
      blocks: this.draftBlocks(["startTime"], false),
    };
  }

  private draftBlocks(missing: string[], readyToSave: boolean): IncomingBlock[] {
    const blocks: IncomingBlock[] = [draftBlock(this.draft, missing, readyToSave)];
    const inactive = choicesFor(this.draft)?.places?.some((place) => place.state === "INACTIVE");
    if (inactive) blocks.push({ type: "warnings", items: [INACTIVE_PLACE_WARNING] });
    return blocks;
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
    return this.phase === "ready";
  }
}

function serverMetricNote(draft: DraftCampaign): string {
  const parts: string[] = [];
  if (draft.weight == null) parts.push("вес 0");
  if (draft.initialCtr == null) parts.push("CTR 15");
  if (parts.length === 0) return "";
  return ` Сервер записал ${parts.join(" и ")}, в черновике этого не было.`;
}
