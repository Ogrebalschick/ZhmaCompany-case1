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

/** Черновик в работе: сессия чата, которую можно открыть из боковой панели. */
export interface SessionEntry {
  id: string;
  title: string;
  updatedAt: number;
  campaignId?: number | null;
}

function readSessions(): SessionEntry[] {
  try {
    const raw = JSON.parse(window.localStorage.getItem(SESSIONS_KEY) ?? "[]") as SessionEntry[];
    return Array.isArray(raw) ? raw : [];
  } catch {
    return [];
  }
}

function writeSessions(list: SessionEntry[]) {
  window.localStorage.setItem(SESSIONS_KEY, JSON.stringify(list.slice(0, 30)));
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
  const [booting, setBooting] = useState(true);
  const [generation, setGeneration] = useState(0);
  const [stored, setStored] = useState<SessionEntry[]>(() => readSessions());
  /** Что открыть при следующей загрузке: конкретную сессию и/или первое сообщение. */
  const target = useRef<{ sessionId?: string; firstText?: string } | null>(null);
  const roleRef = useRef(role);
  const sessionRef = useRef(sessionId);
  const request = useRef(0);
  const sending = useRef(false);
  /** Ссылка на send для отложенной отправки первого сообщения после открытия сессии. */
  const sendRef = useRef<(text: string) => boolean>(() => false);

  const context = useCallback(() => ({ user: USER_LOGIN, role: roleRef.current }), []);

  useEffect(() => {
    roleRef.current = role;
    sessionRef.current = sessionId;
  }, [role, sessionId]);

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
        if (!created) return;
        if (cancelled) return;
        setSessionId(created.sessionId);
        window.localStorage.setItem(SESSION_KEY, created.sessionId);
        const history = await client.getMessages(context(), created.sessionId);
        if (cancelled) return;
        setMessages(history);
        if (wanted?.firstText) {
          sessionRef.current = created.sessionId;
          const text = wanted.firstText;
          window.setTimeout(() => sendRef.current(text), 0);
        }
      } catch (error) {
        if (cancelled) return;
        setMessages([{ role: "assistant", text: errorText(error) }]);
      } finally {
        if (!cancelled) setBooting(false);
      }
    })();
    return () => {
      cancelled = true;
      request.current += 1;
    };
  }, [client, context, generation]);

  const reboot = useCallback((next: { sessionId?: string; firstText?: string }) => {
    setStored(readSessions());
    target.current = next;
    request.current += 1;
    sending.current = false;
    setPending(false);
    setMessages([]);
    setSessionId(null);
    setBooting(true);
    setGeneration((value) => value + 1);
  }, []);

  /** «Новая кампания»: чистый черновик в новой сессии. */
  const newSession = useCallback(() => reboot({}), [reboot]);

  /** Открыть черновик из списка. */
  const openSession = useCallback((id: string) => reboot({ sessionId: id }), [reboot]);

  /** Открыть сохранённую кампанию: новая сессия и команда «открой кампанию N». */
  const openCampaign = useCallback(
    (id: number) => reboot({ firstText: `открой кампанию ${id}` }),
    [reboot],
  );

  const forgetSession = useCallback((id: string) => {
    const next = readSessions().filter((item) => item.id !== id);
    writeSessions(next);
    setStored(next);
  }, []);

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
    const title =
      draftBlock?.draft.name?.trim() ||
      messages.find((m) => m.role === "user")?.text.slice(0, 48) ||
      "";
    if (!title) return null;
    return {
      id: sessionId,
      title,
      updatedAt: messages.length,
      campaignId: draftBlock?.draft.savedCampaignId ?? null,
    };
  }, [sessionId, messages, draftBlock]);
  const sessions = useMemo(
    () =>
      currentEntry
        ? [currentEntry, ...stored.filter((item) => item.id !== currentEntry.id)]
        : stored,
    [currentEntry, stored],
  );
  useEffect(() => {
    if (currentEntry) writeSessions(sessions);
  }, [currentEntry, sessions]);
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
