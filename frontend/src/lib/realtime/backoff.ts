/** Reconnect policy from docs/events.md §2.5: exponential backoff with full jitter, 1 s to a 30 s cap. */
export const RECONNECT_BASE_MS = 1_000;
export const RECONNECT_CAP_MS = 30_000;

/**
 * Delay before reconnect attempt number `attempt` (1 for the first retry). Full jitter spreads clients that
 * lost the same server over the whole window, so they do not all come back at the same instant.
 */
export function reconnectDelay(attempt: number, random: () => number = Math.random): number {
  const ceiling = Math.min(RECONNECT_CAP_MS, RECONNECT_BASE_MS * 2 ** Math.max(0, attempt - 1));
  return Math.floor(random() * ceiling);
}
