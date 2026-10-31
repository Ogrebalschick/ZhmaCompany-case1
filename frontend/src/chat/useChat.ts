import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import type { ChatClient } from "../api/client";
import { errorText } from "../api/errors";
import { normalizeReply } from "../api/guards";
import type { ChatMessage, IncomingBlock, JobView, Role } from "../api/types";
import { USER_LOGIN } from "./phrases";
import { latestDraftBlock, latestQuickReplies } from "./selectors";

const POLL_MS = 1000;
const POLL_LIMIT = 110;

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
  const roleRef = useRef(role);
  const sessionRef = useRef(sessionId);
  const request = useRef(0);
  const sending = useRef(false);

  const context = useCallback(() => ({ user: USER_LOGIN, role: roleRef.current }), []);

  useEffect(() => {
    roleRef.current = role;
    sessionRef.current = sessionId;
  }, [role, sessionId]);

  useEffect(() => {
    let cancelled = false;
    void (async () => {
      try {
        const created = await client.createSession(context());
        if (cancelled) return;
        setSessionId(created.sessionId);
        const history = await client.getMessages(context(), created.sessionId);
        if (cancelled) return;
        setMessages(history);
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
  }, [client, context]);

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

  const draftBlock = useMemo(() => latestDraftBlock(messages), [messages]);
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
  };
}
