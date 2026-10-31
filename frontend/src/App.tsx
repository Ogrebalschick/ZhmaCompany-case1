import { useEffect, useMemo, useState } from "react";
import { createChatClient, usesMock } from "./api/createClient";
import { CONFIRM_TEXT } from "./chat/phrases";
import { useChat } from "./chat/useChat";
import { Composer } from "./components/composer/Composer";
import { QuickReplyBar } from "./components/composer/QuickReplyBar";
import { DraftRail } from "./components/draft/DraftRail";
import { Sidebar } from "./components/sidebar/Sidebar";
import { AppShell } from "./components/layout/AppShell";
import { HeaderBar } from "./components/layout/HeaderBar";
import { MessageList } from "./components/thread/MessageList";

export function App() {
  const client = useMemo(() => createChatClient(), []);
  const chat = useChat(client);
  const inputLocked = chat.pending || chat.booting || !chat.sessionId;
  const [wide, setWide] = useState(() => window.matchMedia("(min-width: 761px)").matches);
  const [railWidth, setRailWidth] = useState(460);

  useEffect(() => {
    const query = window.matchMedia("(min-width: 761px)");
    const apply = () => setWide(query.matches);
    query.addEventListener("change", apply);
    return () => query.removeEventListener("change", apply);
  }, []);

  return (
    <AppShell
      header={
        <HeaderBar
          user={chat.user}
          role={chat.role}
          sessionId={chat.sessionId}
          booting={chat.booting}
          mock={usesMock()}
          onRoleChange={chat.setRole}
        />
      }
      sidebar={
        <Sidebar
          user={chat.user}
          role={chat.role}
          currentSession={chat.sessionId}
          sessions={chat.sessions}
          refreshKey={chat.messages.length}
          disabled={chat.pending}
          onNew={chat.newSession}
          onOpenSession={chat.openSession}
          onOpenCampaign={chat.openCampaign}
          onForget={chat.forgetSession}
        />
      }
      thread={
        <>
          <MessageList
            messages={chat.messages}
            booting={chat.booting}
            pending={chat.pending}
            disabled={inputLocked}
            onSend={chat.send}
            onSave={() => chat.send(CONFIRM_TEXT)}
          />
          <QuickReplyBar
            key={chat.messages.length}
            block={chat.quickReplies}
            disabled={inputLocked}
            onSend={chat.send}
          />
          <Composer pending={chat.pending} disabled={inputLocked} onSend={chat.send} />
        </>
      }
      rail={
        <DraftRail
          block={chat.draftBlock}
          disabled={inputLocked}
          onSave={() => chat.send(CONFIRM_TEXT)}
        />
      }
      railWidth={wide ? railWidth : null}
      onRailWidth={setRailWidth}
    />
  );
}
