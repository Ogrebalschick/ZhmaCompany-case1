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
}

export interface CampaignListItem {
  id: number;
  name: string;
  state: string;
  published: boolean;
  topic?: string | null;
  startDate: string;
  endDate?: string | null;
}

export interface CampaignDraftBlock {
  type: "campaign_draft";
  draft: DraftCampaign;
  missing: string[];
  readyToSave: boolean;
}

export interface QuickRepliesBlock {
  type: "quick_replies";
  options: string[];
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
