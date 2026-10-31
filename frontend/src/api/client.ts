import type { ChatMessage, JobView, RequestContext, SendAccepted, SessionCreated } from "./types";

export interface ChatClient {
  createSession(ctx: RequestContext): Promise<SessionCreated>;
  getMessages(ctx: RequestContext, sessionId: string): Promise<ChatMessage[]>;
  sendMessage(ctx: RequestContext, sessionId: string, text: string): Promise<SendAccepted>;
  getJob(ctx: RequestContext, jobId: string): Promise<JobView>;
}
