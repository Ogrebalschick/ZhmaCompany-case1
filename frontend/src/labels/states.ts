const STATE_HINTS: Record<string, string> = {
  DRAFT: "черновик",
  ON_MODERATION: "на модерации",
  NEEDS_WORK: "нужны правки",
  APPROVED: "одобрена",
  ACTIVE: "активна",
  ACTIVE_EDITING: "активна, правки ещё не в показе",
  STOPPED: "показ остановлен",
  ARCHIVED: "архив",
};

export function stateHint(state: string): string | null {
  return STATE_HINTS[state] ?? null;
}

export function publishedLabel(published: boolean): string {
  return published ? "показ идёт" : "показ выключен";
}
