export type Role = "campaignManager" | "campaignModerator";

export interface RequestContext {
  user: string;
  role: Role;
}

export interface BannerDraft {
  bannerType: string;
  channel: string;
  title?: string | null;
  text?: string | null;
  actionType?: string | null;
  link?: string | null;
  cta?: string | null;
  imageId?: string | null;
  imageUrl?: string | null;
  imageStatus?: string | null;
}

export interface EntityProposal {
  kind: string;
  name: string;
  code?: string | null;
  description?: string | null;
}

export interface DraftCampaign {
  name?: string | null;
  marketingName?: string | null;
  startDate?: string | null;
  startTime?: string | null;
  endDate?: string | null;
  endTime?: string | null;
  topic?: string | null;
  tagIds?: number[];
  keywords?: string | null;
  audienceType?: string | null;
  includedSegments?: string[];
  excludedSegments?: string[];
  placeIds?: number[];
  weight?: number | null;
  initialCtr?: number | null;
  creativeBrief?: string | null;
  banner?: BannerDraft | null;
  pendingProposal?: EntityProposal | null;
  savedCampaignId?: number | null;
  /** Состояние сохранённой кампании: DRAFT, ON_MODERATION, NEEDS_WORK, APPROVED, ACTIVE… */
  knownState?: string | null;
}

export interface CampaignListItem {
  id: number;
  name: string;
  state: string;
  published: boolean;
  topic?: string | null;
  startDate: string;
  endDate?: string | null;
  lastComment?: string | null;
}

export interface SegmentChoice {
  code: string;
  name?: string;
  list: "included" | "excluded";
  type: string;
  count: number;
  state: string;
}

export interface PlaceChoice {
  id: number;
  state: string;
  defaultBanner: string | null;
}

export interface DraftChoices {
  segments?: SegmentChoice[];
  places?: PlaceChoice[];
  reachMin?: number | null;
  reachMax?: number | null;
  /** Объяснение охвата от сервера: «от 400 тыс. до 42,4 млн, сегменты по ИЛИ». */
  reachText?: string | null;
}

export interface CampaignDraftBlock {
  type: "campaign_draft";
  draft: DraftCampaign;
  missing: string[];
  readyToSave: boolean;
  choices?: DraftChoices;
}

export interface ReplyOption {
  label: string;
  value: string;
  hint?: string | null;
  recommended?: boolean;
}

export interface QuickRepliesBlock {
  type: "quick_replies";
  /** Тексты, которые уходят сообщением. */
  options: string[];
  /** Про какое поле вопрос: topic, segments, startDate, next, ask… */
  field?: string | null;
  question?: string | null;
  /** Можно выбрать несколько вариантов: уходят одной строкой через запятую. */
  multi?: boolean;
  allowCustom?: boolean;
  /** Подписи, подсказки и рекомендованный вариант. Если пусто — подписи = options. */
  items?: ReplyOption[];
}

export interface EntityProposalBlock {
  type: "entity_proposal";
  proposal: EntityProposal;
}

export interface CreativePreviewBlock {
  type: "creative_preview";
  banner: BannerDraft;
}

export interface CampaignListBlock {
  type: "campaign_list";
  items: CampaignListItem[];
}

export interface ComplianceItem {
  rule: string;
  article: string;
  status: "OK" | "INFO" | "WARN" | "FAIL" | string;
  message: string;
  fix?: string | null;
  quote?: string | null;
  source?: string;
}

/** Проверка баннера на закон «О рекламе» (38-ФЗ). */
export interface ComplianceBlock {
  type: "compliance";
  report: {
    verdict: "OK" | "WARN" | "FAIL" | string;
    items: ComplianceItem[];
    aiChecked?: boolean;
  };
}

export interface WarningsBlock {
  type: "warnings";
  items: string[];
}

export type UiBlock =
  | CampaignDraftBlock
  | QuickRepliesBlock
  | EntityProposalBlock
  | CreativePreviewBlock
  | CampaignListBlock
  | ComplianceBlock
  | WarningsBlock;

/** Блок с провода: известный type или любой другой, который лента пропускает. */
export type IncomingBlock = { type: string } & Record<string, unknown>;

export interface AssistantReply {
  text: string;
  blocks: IncomingBlock[];
}

export interface ChatMessage {
  role: "user" | "assistant";
  text: string;
  blocks?: IncomingBlock[];
}

export type JobStatus = "PENDING" | "DONE" | "FAILED";

export interface JobView {
  status: JobStatus;
  result: AssistantReply | null;
  error: string | null;
}

export interface SessionCreated {
  sessionId: string;
}

export interface SendAccepted {
  jobId: string;
}
