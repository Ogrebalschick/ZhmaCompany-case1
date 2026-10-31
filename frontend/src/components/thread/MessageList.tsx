import { useLayoutEffect, useRef } from "react";
import { asCampaignDraft } from "../../api/guards";
import type { CampaignDraftBlock, ChatMessage } from "../../api/types";
import { AssistantMessage } from "./AssistantMessage";
import { EmptyThread } from "./EmptyThread";
import { UserMessage } from "./UserMessage";
import styles from "./MessageList.module.css";

export function MessageList({
  messages,
  booting,
  userFlight,
  disabled,
  onSend,
  onSave,
}: {
  messages: ChatMessage[];
  booting: boolean;
  userFlight: number;
  disabled: boolean;
  onSend: (text: string) => void;
  onSave: () => void;
}) {
  const endRef = useRef<HTMLDivElement>(null);
  const listRef = useRef<HTMLDivElement>(null);
  const flown = useRef(0);

  useLayoutEffect(() => {
    endRef.current?.scrollIntoView({ block: "end" });
    if (userFlight === 0 || flown.current === userFlight) return;
    if (window.matchMedia("(prefers-reduced-motion: reduce)").matches) {
      flown.current = userFlight;
      return;
    }
    const list = listRef.current;
    const bubble = list?.querySelector<HTMLElement>("[data-entering]");
    const field = document.getElementById("composer");
    if (!list || !bubble || !field) return;
    const from = field.getBoundingClientRect();
    const to = bubble.getBoundingClientRect();
    const dx = from.left + from.width / 2 - (to.left + to.width / 2);
    const dy = from.top + from.height / 2 - (to.top + to.height / 2);
    list.style.overflow = "visible";
    bubble.style.transform = `translate(${dx}px, ${dy}px) scale(0.96)`;
    bubble.style.opacity = "0.45";
    let cancel = false;
    let inner = 0;
    const outer = window.requestAnimationFrame(() => {
      inner = window.requestAnimationFrame(() => {
        if (cancel) return;
        flown.current = userFlight;
        bubble.style.transition = "transform 420ms ease, opacity 420ms ease";
        bubble.style.transform = "";
        bubble.style.opacity = "";
      });
    });
    const restore = (event: Event) => {
      if (event instanceof TransitionEvent && event.propertyName !== "transform") return;
      list.style.overflow = "";
      bubble.removeEventListener("transitionend", restore);
    };
    bubble.addEventListener("transitionend", restore);
    return () => {
      cancel = true;
      window.cancelAnimationFrame(outer);
      window.cancelAnimationFrame(inner);
      bubble.removeEventListener("transitionend", restore);
      list.style.overflow = "";
      bubble.style.transition = "";
      bubble.style.transform = "";
      bubble.style.opacity = "";
    };
  }, [messages, booting, userFlight]);

  return (
    <div className={styles.list} ref={listRef} data-testid="message-list">
      <div className={styles.stream}>
        {booting ? <p className={styles.boot}>Открываю сессию</p> : null}
        {!booting && messages.length === 0 ? (
          <EmptyThread disabled={disabled} onSend={onSend} />
        ) : null}
        {messages.map((message, index) =>
          message.role === "user" ? (
            <UserMessage
              key={`${message.role}-${index}`}
              text={message.text}
              entering={userFlight > 0 && index === messages.length - 1}
            />
          ) : (
            <AssistantMessage
              key={`${message.role}-${index}`}
              text={message.text}
              blocks={message.blocks}
              previousDraft={previousDraft(messages, index)}
              disabled={disabled}
              onSend={onSend}
              onSave={onSave}
            />
          ),
        )}
        <div ref={endRef} />
      </div>
    </div>
  );
}

function previousDraft(messages: ChatMessage[], index: number): CampaignDraftBlock | null {
  for (let messageIndex = index - 1; messageIndex >= 0; messageIndex -= 1) {
    const blocks = messages[messageIndex].blocks ?? [];
    for (let blockIndex = blocks.length - 1; blockIndex >= 0; blockIndex -= 1) {
      const draft = asCampaignDraft(blocks[blockIndex]);
      if (draft) return draft;
    }
  }
  return null;
}
