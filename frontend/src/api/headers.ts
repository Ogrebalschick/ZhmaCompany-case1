import type { RequestContext } from "./types";

export function requestHeaders(ctx: RequestContext): HeadersInit {
  return {
    Accept: "application/json",
    "Content-Type": "application/json",
    "X-User": ctx.user,
    "X-Role": ctx.role,
  };
}
