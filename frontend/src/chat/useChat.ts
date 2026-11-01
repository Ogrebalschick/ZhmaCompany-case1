import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import type { ChatClient } from "../api/client";
import { errorText } from "../api/errors";
import { normalizeReply } from "../api/guards";
import type { ChatMessage, IncomingBlock, JobView, Role } from "../api/types";
import { USER_LOGIN } from "./phrases";
import { latestDraftBlock, latestQuickReplies } from "./selectors";

const POLL_MS = 1000;
const POLL_LIMIT = Number(import.meta.env.VITE_TURN_TIMEOUT_SEC || 330);
const BOOT_RETRIES = 60;
const SESSION_KEY = "campaign.sessionId";
const BOOT_RETRY_MS = 3000;
const SESSIONS_KEY = "campaign.sessions";
const OPEN_COMMAND = /^открой кампанию (\d+)$/;

/** Черновик в работе: сессия чата, которую можно открыть из боковой панели. */
export interface SessionEntry {
  id: string;
  title: string;
  updatedAt: number;
  campaignId?: number | null;
  role?: Role;
  /** review — чат «открой кампанию»; work — диалог, в котором кампанию собирали. */
  kind?: "work" | "review";
}

interface OpenTarget {
  sessionId?: string;
  firstText?: string;
  campaignId?: number;
  /** Новая пустая сессия: историю не запрашивать. */
  fresh?: boolean;
}

function rank(item: SessionEntry): number {
  return (item.kind === "review" ? 0 : 1_000_000) + item.updatedAt;
}

function sameRole(item: SessionEntry, role: Role): boolean {
  return (item.role ?? "campaignManager") === role;
}

function normalize(list: SessionEntry[]): SessionEntry[] {
  const mapped = list.map((item) => {
    const match = OPEN_COMMAND.exec(item.title.trim());
    if (item.campaignId == null && match) {
      return {
        ...item,
        campaignId: Number(match[1]),
        kind: item.kind ?? ("review" as const),
      };
    }
    return item;
  });
  const best = new Map<string, SessionEntry>();
  const loose: SessionEntry[] = [];
  for (const item of mapped) {
    if (item.campaignId == null) {
      loose.push(item);
      continue;
    }
    const key = `${item.role ?? "campaignManager"}:${item.campaignId}`;
    const previous = best.get(key);
    if (!previous || rank(item) > rank(previous)) best.set(key, item);
  }
  return [...best.values(), ...loose].slice(0, 30);
}

function readSessions(): SessionEntry[] {
  try {
    const raw = JSON.parse(window.localStorage.getItem(SESSIONS_KEY) ?? "[]") as SessionEntry[];
    return normalize(Array.isArray(raw) ? raw : []);
  } catch {
    return [];
  }
}

function writeSessions(list: SessionEntry[]) {
  window.localStorage.setItem(SESSIONS_KEY, JSON.stringify(normalize(list).slice(0, 30)));
}

function pickSession(id: number, role: Role, list: SessionEntry[]): SessionEntry | null {
  const matches = list.filter((item) => item.campaignId === id && sameRole(item, role));
  if (matches.length === 0) return null;
  return matches.reduce((best, item) => (rank(item) > rank(best) ? item : best));
}

class Cancelled extends Error {}

function delay(ms: number): Promise<void> {
  return new Promise((resolve) => {
    window.setTimeout(resolve, ms);
  });
}

export function useChat(client: ChatClient) {
  const [role, setRole] = useState<Role>("campaignManager");
  const [sessionId, setSessionId] = useState<string | null>(null);
  const [messages, setMessages] = useState<ChatMessage[]>([]);
  const [pending, setPending] = useState(false);
  const [booting, setBooting] = useState(() => window.localStorage.getItem(SESSION_KEY) != null);
  const [generation, setGeneration] = useState(0);
  const [stored, setStored] = useState<SessionEntry[]>(() => {
    const list = readSessions();
    writeSessions(list);
    return list;
  });
  const [dismissed, setDismissed] = useState<string[]>([]);
  const [lockedRoles, setLockedRoles] = useState<Record<string, Role>>(() => {
    const map: Record<string, Role> = {};
    for (const item of readSessions()) {
      if (item.role) map[item.id] = item.role;
    }
    return map;
  });
  if (sessionId && lockedRoles[sessionId] == null) {
    setLockedRoles((prev) => (prev[sessionId] ? prev : { ...prev, [sessionId]: role }));
  }
  /** Что открыть при следующей загрузке: конкретную сессию и/или первое сообщение. */
  const target = useRef<OpenTarget | null>(null);
  const pendingOpen = useRef<OpenTarget | null>(null);
  const roleRef = useRef(role);
  const sessionRef = useRef(sessionId);
  const bootingRef = useRef(booting);
  const messagesRef = useRef(messages);
  const request = useRef(0);
  const sending = useRef(false);
  /** Ссылка на send для отложенной отправки первого сообщения после открытия сессии. */
  const sendRef = useRef<(text: string) => boolean>(() => false);
  const entryRef = useRef<SessionEntry | null>(null);

  const context = useCallback(() => ({ user: USER_LOGIN, role: roleRef.current }), []);

  useEffect(() => {
    roleRef.current = role;
    sessionRef.current = sessionId;
    bootingRef.current = booting;
    messagesRef.current = messages;
  }, [role, sessionId, booting, messages]);

  useEffect(() => {
    let cancelled = false;
    void (async () => {
      try {
        // Сессия переживает обновление страницы: id лежит в localStorage.
        const wanted = target.current;
        target.current = null;
        const saved =
          wanted?.sessionId ?? (wanted ? null : window.localStorage.getItem(SESSION_KEY));
        if (saved) {
          try {
            const history = await client.getMessages(context(), saved);
            if (cancelled) return;
            setSessionId(saved);
            sessionRef.current = saved;
            window.localStorage.setItem(SESSION_KEY, saved);
            setMessages(history);
            return;
          } catch {
            window.localStorage.removeItem(SESSION_KEY);
          }
        }
        // Сервер может ещё стартовать (первая сборка Gradle идёт минуты): пробуем несколько раз.
        let created: { sessionId: string } | null = null;
        for (let attempt = 0; ; attempt += 1) {
          try {
            created = await client.createSession(context());
            break;
          } catch (error) {
            if (cancelled || attempt >= BOOT_RETRIES) throw error;
            setMessages([{ role: "assistant", text: "Жду, пока запустится сервер…" }]);
            await delay(BOOT_RETRY_MS);
          }
        }
        if (!created || cancelled) return;
        setSessionId(created.sessionId);
        sessionRef.current = created.sessionId;
        window.localStorage.setItem(SESSION_KEY, created.sessionId);
        // Только что созданная сессия пустая: историю не запрашиваем.
        if (wanted?.campaignId != null) {
          const entry: SessionEntry = {
            id: created.sessionId,
            title: `Кампания № ${wanted.campaignId}`,
            updatedAt: 0,
            campaignId: wanted.campaignId,
            role: roleRef.current,
            kind: "review",
          };
          writeSessions([entry, ...readSessions().filter((item) => item.id !== entry.id)]);
          setStored(readSessions());
        }
        if (wanted?.firstText) sendRef.current(wanted.firstText);
        else setMessages([]);
      } catch (error) {
        if (cancelled) return;
        setMessages([{ role: "assistant", text: errorText(error) }]);
      } finally {
        if (!cancelled) {
          pendingOpen.current = null;
          bootingRef.current = false;
          setBooting(false);
        }
      }
    })();
    return () => {
      cancelled = true;
      request.current += 1;
    };
  }, [client, context, generation]);

  const reboot = useCallback((next: OpenTarget) => {
    if (
      next.campaignId != null &&
      pendingOpen.current?.campaignId === next.campaignId &&
      bootingRef.current
    ) {
      return;
    }
    if (next.sessionId && next.sessionId === sessionRef.current && !bootingRef.current) return;
    const entry = entryRef.current;
    if (entry) {
      writeSessions([entry, ...readSessions().filter((item) => item.id !== entry.id)]);
      setStored(readSessions());
    }
    pendingOpen.current = next;
    target.current = next;
    setStored(readSessions());
    request.current += 1;
    sending.current = false;
    const showProgress = !next.fresh;
    bootingRef.current = showProgress;
    setPending(false);
    setMessages([]);
    setSessionId(null);
    sessionRef.current = null;
    setBooting(showProgress);
    setGeneration((value) => value + 1);
  }, []);

  /** «Новая кампания»: чистый черновик. Повторное нажатие в пустом чате ничего не перезагружает. */
  const newSession = useCallback(() => {
    if (pendingOpen.current?.fresh) return;
    if (!bootingRef.current && sessionRef.current && messagesRef.current.length === 0) return;
    reboot({ fresh: true });
  }, [reboot]);

  /** Открыть черновик из списка. */
  const openSession = useCallback((id: string) => reboot({ sessionId: id }), [reboot]);

  /**
   * Открыть сохранённую кампанию в уже существующем чате этой роли.
   * У менеджера это диалог, в котором кампанию собирали; новый чат заводится только если своего ещё нет.
   */
  const openCampaign = useCallback(
    (id: number) => {
      if (pendingOpen.current?.campaignId === id && bootingRef.current) return;
      const current = entryRef.current;
      const pool = current
        ? [current, ...readSessions().filter((item) => item.id !== current.id)]
        : readSessions();
      const existing = pickSession(id, roleRef.current, pool);
      if (existing) {
        if (existing.id === sessionRef.current && !bootingRef.current) return;
        reboot({ sessionId: existing.id, campaignId: id });
        return;
      }
      reboot({ firstText: `открой кампанию ${id}`, campaignId: id });
    },
    [reboot],
  );

  const forgetSession = useCallback(
    (id: string) => {
      setDismissed((prev) => (prev.includes(id) ? prev : [...prev, id]));
      if (entryRef.current?.id === id) entryRef.current = null;
      const next = readSessions().filter((item) => item.id !== id);
      writeSessions(next);
      setStored(next);
      if (sessionRef.current === id) reboot({ fresh: true });
    },
    [reboot],
  );

  const pushAssistant = useCallback((text: string, blocks?: IncomingBlock[]) => {
    setMessages((prev) => [...prev, { role: "assistant", text, blocks }]);
  }, []);

  const waitForJob = useCallback(
    async (jobId: string, requestId: number): Promise<JobView> => {
      for (let attempt = 0; attempt < POLL_LIMIT; attempt += 1) {
        const job = await client.getJob(context(), jobId);
        if (job.status !== "PENDING") return job;
        await delay(POLL_MS);
        if (request.current !== requestId) throw new Cancelled();
      }
      throw new Error("Ответ собирается слишком долго. Попробуйте ещё раз.");
    },
    [client, context],
  );

  const send = useCallback(
    (text: string): boolean => {
      const trimmed = text.trim();
      const session = sessionRef.current;
      if (!trimmed || sending.current || !session) return false;
      sending.current = true;
      setPending(true);
      setMessages((prev) => [...prev, { role: "user", text: trimmed }]);
      const requestId = ++request.current;
      void (async () => {
        try {
          const accepted = await client.sendMessage(context(), session, trimmed);
          if (request.current !== requestId) throw new Cancelled();
          const job = await waitForJob(accepted.jobId, requestId);
          if (request.current !== requestId) throw new Cancelled();
          if (job.status === "FAILED") {
            pushAssistant(job.error?.trim() || "Не удалось собрать ответ.");
            return;
          }
          const reply = normalizeReply(job.result);
          if (!reply) {
            pushAssistant("Сервер завершил задачу без ответа.");
            return;
          }
          pushAssistant(reply.text, reply.blocks);
        } catch (error) {
          if (error instanceof Cancelled || request.current !== requestId) return;
          pushAssistant(errorText(error));
        } finally {
          if (request.current === requestId) {
            sending.current = false;
            setPending(false);
          }
        }
      })();
      return true;
    },
    [client, context, pushAssistant, waitForJob],
  );

  useEffect(() => {
    sendRef.current = send;
  }, [send]);

  const draftBlock = useMemo(() => latestDraftBlock(messages), [messages]);

  // Список черновиков: текущая сессия сверху, название — из карточки черновика.
  const currentEntry = useMemo<SessionEntry | null>(() => {
    if (!sessionId || messages.length === 0) return null;
    const userTexts = messages
      .filter((item) => item.role === "user")
      .map((item) => item.text.trim());
    const commandId = OPEN_COMMAND.exec(userTexts[0] ?? "")?.[1];
    const openedByCommand =
      userTexts.length > 0 && userTexts.every((text) => OPEN_COMMAND.test(text));
    const savedId = draftBlock?.draft.savedCampaignId ?? null;
    const campaignId = savedId ?? (openedByCommand && commandId ? Number(commandId) : null);
    const title =
      draftBlock?.draft.name?.trim() ||
      userTexts[0]?.slice(0, 48) ||
      (campaignId != null ? `Кампания № ${campaignId}` : "");
    if (!title) return null;
    return {
      id: sessionId,
      title,
      updatedAt: messages.length,
      campaignId,
      role: lockedRoles[sessionId] ?? role,
      kind: openedByCommand ? "review" : "work",
    };
  }, [sessionId, messages, draftBlock, role, lockedRoles]);
  useEffect(() => {
    entryRef.current = currentEntry;
  }, [currentEntry]);

  const sessions = useMemo(() => {
    const hidden = new Set(dismissed);
    const visible = stored.filter((item) => sameRole(item, role) && !hidden.has(item.id));
    if (!currentEntry || currentEntry.role !== role || hidden.has(currentEntry.id)) return visible;
    return [currentEntry, ...visible.filter((item) => item.id !== currentEntry.id)];
  }, [currentEntry, stored, role, dismissed]);

  useEffect(() => {
    if (!currentEntry || dismissed.includes(currentEntry.id)) return;
    const rest = readSessions().filter(
      (item) => item.id !== currentEntry.id && !dismissed.includes(item.id),
    );
    writeSessions([currentEntry, ...rest]);
  }, [currentEntry, dismissed]);

  const quickReplies = useMemo(() => latestQuickReplies(messages), [messages]);

  return {
    user: USER_LOGIN,
    role,
    setRole,
    sessionId,
    messages,
    pending,
    booting,
    draftBlock,
    quickReplies,
    send,
    newSession,
    openSession,
    openCampaign,
    forgetSession,
    sessions,
  };
}
