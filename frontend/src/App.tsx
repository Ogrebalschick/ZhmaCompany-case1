import { useEffect, useMemo, useState } from "react";
import { createChatClient, usesMock } from "./api/createClient";
import { CONFIRM_TEXT } from "./chat/phrases";
import { useChat } from "./chat/useChat";
import { Composer } from "./components/composer/Composer";
import { QuickReplyBar } from "./components/composer/QuickReplyBar";
import { DraftRail } from "./components/draft/DraftRail";
import { AppShell } from "./components/layout/AppShell";
import { CAMPAIGN_DRAWER_WIDTH, CampaignDrawer } from "./components/layout/CampaignDrawer";
import { HeaderBar } from "./components/layout/HeaderBar";
import { MessageList } from "./components/thread/MessageList";

export function App() {
  const client = useMemo(() => createChatClient(), []);
  const chat = useChat(client);
  const inputLocked = chat.pending || chat.booting || !chat.sessionId;
  const [wide, setWide] = useState(() => window.matchMedia("(min-width: 761px)").matches);
  const [railWidth, setRailWidth] = useState(460);
  const [reveal, setReveal] = useState(0);
  const [dragging, setDragging] = useState(false);

  useEffect(() => {
    const query = window.matchMedia("(min-width: 761px)");
    const apply = () => setWide(query.matches);
    query.addEventListener("change", apply);
    return () => query.removeEventListener("change", apply);
  }, []);

  return (
    <>
      <AppShell
        header={
          <HeaderBar
            user={chat.user}
            role={chat.role}
            sessionId={chat.sessionId}
            booting={chat.booting}
            mock={usesMock()}
            campaignsOpen={reveal > 0}
            onToggleCampaigns={() => setReveal((value) => (value > 0 ? 0 : CAMPAIGN_DRAWER_WIDTH))}
            onRoleChange={chat.setRole}
          />
        }
        thread={
          <>
            <MessageList
              messages={chat.messages}
              booting={chat.booting}
              disabled={inputLocked}
              onSend={chat.send}
              onSave={() => chat.send(CONFIRM_TEXT)}
            />
            <QuickReplyBar options={chat.quickReplies} disabled={inputLocked} onSend={chat.send} />
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
      <CampaignDrawer
        reveal={reveal}
        dragging={dragging}
        campaigns={chat.campaigns}
        onReveal={setReveal}
        onDragging={setDragging}
        onNew={() => {
          setReveal(0);
          chat.closeCampaign();
        }}
        onSelect={(id) => {
          setReveal(0);
          chat.openCampaign(id);
        }}
      />
    </>
  );
}
