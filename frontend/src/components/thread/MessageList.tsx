import { useEffect, useRef } from "react";
import type { ChatMessage } from "../../api/types";
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
        {!booting && messages.length === 0 ? <EmptyThread disabled={disabled} onSend={onSend} /> : null}
        {messages.map((message, index) =>
          message.role === "user" ? (
            <UserMessage key={`${message.role}-${index}`} text={message.text} />
          ) : (
            <AssistantMessage
              key={`${message.role}-${index}`}
              text={message.text}
              blocks={message.blocks}
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
