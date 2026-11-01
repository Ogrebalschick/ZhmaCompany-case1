export const SIDEBAR_COLLAPSED = 64;

export function clampSidebarWidth(width: number): number {
  const max = Math.min(440, Math.max(280, Math.round(window.innerWidth * 0.42)));
  return Math.min(max, Math.max(220, Math.round(width)));
}
