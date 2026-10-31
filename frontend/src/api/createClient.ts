import type { ChatClient } from "./client";
import { HttpChatClient } from "./httpClient";
import { MockChatClient } from "../mock/mockClient";

export function createChatClient(): ChatClient {
  if (import.meta.env.VITE_USE_MOCK === "true") return new MockChatClient();
  return new HttpChatClient();
}

export function usesMock(): boolean {
  return import.meta.env.VITE_USE_MOCK === "true";
}
