import type {
  ComplianceBlock,
  AssistantReply,
  BannerDraft,
  CampaignDraftBlock,
  CampaignListBlock,
  CampaignListItem,
  ChatMessage,
  CreativePreviewBlock,
  DraftCampaign,
  DraftChoices,
  EntityProposal,
  EntityProposalBlock,
  IncomingBlock,
  PlaceChoice,
  QuickRepliesBlock,
  SegmentChoice,
  WarningsBlock,
} from "./types";

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === "object" && value !== null && !Array.isArray(value);
}

function asString(value: unknown): string | null {
  return typeof value === "string" ? value : null;
}

function asNumber(value: unknown): number | null {
  return typeof value === "number" ? value : null;
}

function asStrings(value: unknown): string[] {
  if (!Array.isArray(value)) return [];
  return value.filter((item): item is string => typeof item === "string");
}

function asNumbers(value: unknown): number[] {
  if (!Array.isArray(value)) return [];
  return value.filter((item): item is number => typeof item === "number");
}

export function normalizeBlocks(value: unknown): IncomingBlock[] {
  if (!Array.isArray(value)) return [];
  return value.flatMap((item) => {
    if (!isRecord(item) || typeof item.type !== "string") return [];
    return [normalizeBlock(item, item.type)];
  });
}

function normalizeBlock(block: Record<string, unknown>, type: string): IncomingBlock {
  if (type === "campaign_draft") {
    return {
      type,
      draft: isRecord(block.draft) ? block.draft : {},
      missing: asStrings(block.missing),
      readyToSave: block.readyToSave === true,
      ...(isRecord(block.choices) ? { choices: block.choices } : {}),
    };
  }
  if (type === "quick_replies") return { type, options: asStrings(block.options) };
  if (type === "warnings") return { type, items: asStrings(block.items) };
  return { ...block, type };
}

export function normalizeReply(value: unknown): AssistantReply | null {
  if (!isRecord(value) || typeof value.text !== "string") return null;
  return { text: value.text, blocks: normalizeBlocks(value.blocks) };
}

export function normalizeMessage(value: unknown): ChatMessage | null {
  if (!isRecord(value) || typeof value.text !== "string") return null;
  const role = String(value.role).toLowerCase();
  if (role !== "user" && role !== "assistant") return null;
  if (role === "user") return { role, text: value.text };
  return { role, text: value.text, blocks: normalizeBlocks(value.blocks) };
}

function asDraft(raw: Record<string, unknown>): DraftCampaign {
  return {
    name: asString(raw.name),
    marketingName: asString(raw.marketingName),
    startDate: asString(raw.startDate),
    startTime: asString(raw.startTime),
    endDate: asString(raw.endDate),
    endTime: asString(raw.endTime),
    topic: asString(raw.topic),
    tagIds: asNumbers(raw.tagIds),
    keywords: asString(raw.keywords),
    audienceType: asString(raw.audienceType),
    includedSegments: asStrings(raw.includedSegments),
    excludedSegments: asStrings(raw.excludedSegments),
    placeIds: asNumbers(raw.placeIds),
    weight: asNumber(raw.weight),
    initialCtr: asNumber(raw.initialCtr),
    creativeBrief: asString(raw.creativeBrief),
    savedCampaignId: asNumber(raw.savedCampaignId),
    dirty: raw.dirty === true,
    knownState: asString(raw.knownState),
  };
}

function asBanner(raw: Record<string, unknown>): BannerDraft | null {
  if (typeof raw.bannerType !== "string" || typeof raw.channel !== "string") return null;
  return {
    bannerType: raw.bannerType,
    channel: raw.channel,
    title: asString(raw.title),
    text: asString(raw.text),
    actionType: asString(raw.actionType),
    link: asString(raw.link),
    cta: asString(raw.cta),
    imageId: asString(raw.imageId),
    imageUrl: asString(raw.imageUrl),
    imageStatus: asString(raw.imageStatus),
  };
}

function asProposal(value: unknown): EntityProposal | null {
  if (!isRecord(value) || typeof value.kind !== "string" || typeof value.name !== "string")
    return null;
  return {
    kind: value.kind,
    name: value.name,
    code: asString(value.code),
    description: asString(value.description),
  };
}

function asSegmentChoice(value: unknown): SegmentChoice | null {
  if (!isRecord(value)) return null;
  if (
    typeof value.code !== "string" ||
    typeof value.type !== "string" ||
    typeof value.state !== "string"
  )
    return null;
  if (value.list !== "included" && value.list !== "excluded") return null;
  if (typeof value.count !== "number" || !Number.isFinite(value.count)) return null;
  return {
    code: value.code,
    name: typeof value.name === "string" ? value.name : undefined,
    list: value.list,
    type: value.type,
    count: value.count,
    state: value.state,
  };
}

function asPlaceChoice(value: unknown): PlaceChoice | null {
  if (!isRecord(value) || !("defaultBanner" in value)) return null;
  if (typeof value.id !== "number" || typeof value.state !== "string") return null;
  if (value.defaultBanner !== null && typeof value.defaultBanner !== "string") return null;
  return { id: value.id, state: value.state, defaultBanner: value.defaultBanner };
}

function asChoices(value: unknown): DraftChoices | undefined {
  if (!isRecord(value)) return undefined;
  const segments = Array.isArray(value.segments)
    ? value.segments.flatMap((item) => {
        const row = asSegmentChoice(item);
        return row ? [row] : [];
      })
    : [];
  const places = Array.isArray(value.places)
    ? value.places.flatMap((item) => {
        const row = asPlaceChoice(item);
        return row ? [row] : [];
      })
    : [];
  const reachText = typeof value.reachText === "string" ? value.reachText : null;
  if (segments.length === 0 && places.length === 0 && !reachText) return undefined;
  return { segments, places, reachText };
}

export function asCampaignDraft(block: IncomingBlock): CampaignDraftBlock | null {
  if (block.type !== "campaign_draft" || !isRecord(block.draft) || !Array.isArray(block.missing))
    return null;
  return {
    type: "campaign_draft",
    draft: asDraft(block.draft),
    missing: asStrings(block.missing),
    readyToSave: block.readyToSave === true,
    choices: asChoices(block.choices),
  };
}

export function asQuickReplies(block: IncomingBlock): QuickRepliesBlock | null {
  if (block.type !== "quick_replies" || !Array.isArray(block.options)) return null;
  const items = Array.isArray(block.items)
    ? block.items.flatMap((item) => {
        if (!isRecord(item) || typeof item.label !== "string" || typeof item.value !== "string")
          return [];
        return [
          {
            label: item.label,
            value: item.value,
            hint: asString(item.hint),
            recommended: item.recommended === true,
          },
        ];
      })
    : [];
  return {
    type: "quick_replies",
    options: asStrings(block.options),
    field: asString(block.field),
    question: asString(block.question),
    multi: block.multi === true,
    allowCustom: block.allowCustom !== false,
    items,
  };
}

export function asEntityProposal(block: IncomingBlock): EntityProposalBlock | null {
  const proposal = asProposal(block.proposal);
  if (block.type !== "entity_proposal" || !proposal) return null;
  return { type: "entity_proposal", proposal };
}

export function asCreativePreview(block: IncomingBlock): CreativePreviewBlock | null {
  if (block.type !== "creative_preview" || !isRecord(block.banner)) return null;
  const banner = asBanner(block.banner);
  if (!banner) return null;
  return { type: "creative_preview", banner };
}

export function asCampaignList(block: IncomingBlock): CampaignListBlock | null {
  if (block.type !== "campaign_list" || !Array.isArray(block.items)) return null;
  return {
    type: "campaign_list",
    items: block.items.flatMap((item) => {
      const row = asCampaignItem(item);
      return row ? [row] : [];
    }),
  };
}

export function asWarnings(block: IncomingBlock): WarningsBlock | null {
  if (block.type !== "warnings" || !Array.isArray(block.items)) return null;
  return { type: "warnings", items: asStrings(block.items) };
}

export function asCampaignItem(value: unknown): CampaignListItem | null {
  if (!isRecord(value) || typeof value.name !== "string" || typeof value.state !== "string")
    return null;
  if (typeof value.id !== "number" || typeof value.startDate !== "string") return null;
  return {
    id: value.id,
    name: value.name,
    state: value.state,
    published: value.published === true,
    topic: asString(value.topic),
    startDate: value.startDate,
    endDate: asString(value.endDate),
  };
}

export function asCompliance(block: IncomingBlock): ComplianceBlock | null {
  if (block.type !== "compliance" || !isRecord(block.report)) return null;
  const report = block.report;
  if (typeof report.verdict !== "string" || !Array.isArray(report.items)) return null;
  return {
    type: "compliance",
    report: {
      verdict: report.verdict,
      aiChecked: report.aiChecked === true,
      items: report.items.flatMap((item) =>
        isRecord(item) && typeof item.article === "string" && typeof item.message === "string"
          ? [
              {
                rule: typeof item.rule === "string" ? item.rule : "",
                article: item.article,
                status: typeof item.status === "string" ? item.status : "INFO",
                message: item.message,
                fix: asString(item.fix),
                quote: asString(item.quote),
                source: typeof item.source === "string" ? item.source : "code",
              },
            ]
          : [],
      ),
    },
  };
}
