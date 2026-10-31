import { useEffect, useRef } from "react";
import { asCampaignDraft } from "../../api/guards";
import type { CampaignDraftBlock, ChatMessage } from "../../api/types";
import { AssistantMessage } from "./AssistantMessage";
import { EmptyThread } from "./EmptyThread";
import { UserMessage } from "./UserMessage";
import styles from "./MessageList.module.css";

export function MessageList({
  messages,
  booting,
  disabled,
  onSend,
  onSave,
}: {
  messages: ChatMessage[];
  booting: boolean;
  disabled: boolean;
  onSend: (text: string) => void;
  onSave: () => void;
}) {
  const endRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    endRef.current?.scrollIntoView({ block: "end" });
  }, [messages, booting]);

  return (
    <div className={styles.list} data-testid="message-list">
      <div className={styles.stream}>
        {booting ? <p className={styles.boot}>Открываю сессию</p> : null}
        {!booting && messages.length === 0 ? (
          <EmptyThread disabled={disabled} onSend={onSend} />
        ) : null}
        {messages.map((message, index) =>
          message.role === "user" ? (
            <UserMessage key={`${message.role}-${index}`} text={message.text} />
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
