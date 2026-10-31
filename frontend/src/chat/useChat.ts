import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import type { ChatClient } from "../api/client";
import { errorText } from "../api/errors";
import { normalizeReply } from "../api/guards";
import type {
  CampaignListItem,
  ChatMessage,
  DraftCampaign,
  IncomingBlock,
  JobView,
  Role,
} from "../api/types";
import { draftFromBlocks, withCurrentDraft } from "./campaignList";
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
  const [campaigns, setCampaigns] = useState<CampaignListItem[]>([]);
  const [campaignMessages, setCampaignMessages] = useState<ChatMessage[] | null>(null);
  const [pending, setPending] = useState(false);
  const [userFlight, setUserFlight] = useState(0);
  const [booting, setBooting] = useState(true);
  const roleRef = useRef(role);
  const sessionRef = useRef(sessionId);
  const request = useRef(0);
  const sending = useRef(false);
  const viewing = useRef(false);
  const workingDraft = useRef<DraftCampaign | null>(null);

  const context = useCallback(() => ({ user: USER_LOGIN, role: roleRef.current }), []);

  const publishList = useCallback(
    async (draft: DraftCampaign | null) => {
      try {
        const list = await client.listCampaigns(context());
        setCampaigns(withCurrentDraft(list, draft));
      } catch {
        setCampaigns(withCurrentDraft([], draft));
      }
    },
    [client, context],
  );

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
        workingDraft.current = latestDraftBlock(history)?.draft ?? null;
        if (!cancelled) await publishList(workingDraft.current);
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
  }, [client, context, publishList]);

  const append = useCallback((message: ChatMessage) => {
    if (viewing.current) {
      setCampaignMessages((prev) => [...(prev ?? []), message]);
      return;
    }
    setMessages((prev) => [...prev, message]);
  }, []);

  const pushAssistant = useCallback(
    (text: string, blocks?: IncomingBlock[]) => {
      append({ role: "assistant", text, blocks });
    },
    [append],
  );

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
      append({ role: "user", text: trimmed });
      setUserFlight((value) => value + 1);
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
          const fromReply = draftFromBlocks(reply.blocks);
          if (!viewing.current && fromReply) workingDraft.current = fromReply;
          pushAssistant(reply.text, reply.blocks);
          if (request.current === requestId) await publishList(workingDraft.current);
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
    [append, client, context, publishList, pushAssistant, waitForJob],
  );

  const stopRequest = useCallback(() => {
    request.current += 1;
    sending.current = false;
    setPending(false);
  }, []);

  const openCampaign = useCallback(
    (id: number) => {
      stopRequest();
      viewing.current = true;
      void (async () => {
        try {
          const next = await client.openCampaign(context(), id);
          if (!viewing.current) return;
          setCampaignMessages(next);
        } catch (error) {
          if (!viewing.current) return;
          setCampaignMessages([{ role: "assistant", text: errorText(error) }]);
        }
      })();
    },
    [client, context, stopRequest],
  );

  const closeCampaign = useCallback(() => {
    stopRequest();
    viewing.current = false;
    setCampaignMessages(null);
    void client.closeCampaign();
  }, [client, stopRequest]);

  const shown = campaignMessages ?? messages;
  const draftBlock = useMemo(() => latestDraftBlock(shown), [shown]);
  const quickReplies = useMemo(() => latestQuickReplies(shown), [shown]);

  return {
    user: USER_LOGIN,
    role,
    setRole,
    sessionId,
    messages: shown,
    campaigns,
    pending,
    booting,
    userFlight,
    draftBlock,
    quickReplies,
    send,
    openCampaign,
    closeCampaign,
  };
}
