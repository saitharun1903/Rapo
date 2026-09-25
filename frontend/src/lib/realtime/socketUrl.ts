/**
 * Why the browser could not open a WebSocket to `url` from a page served over `pageProtocol`, or null if it can.
 * Both are build-time mistakes (NEXT_PUBLIC_WS_URL) that retrying cannot fix: the WebSocket constructor throws,
 * and stompjs would then stop without retrying or telling anyone.
 */
export function socketUrlProblem(url: string, pageProtocol: string): string | null {
  let parsed: URL;
  try {
    parsed = new URL(url);
  } catch {
    return `"${url}" is not a WebSocket URL`;
  }
  if (parsed.protocol !== "ws:" && parsed.protocol !== "wss:") {
    return `"${url}" is not a WebSocket URL (ws:// or wss://)`;
  }
  if (pageProtocol === "https:" && parsed.protocol === "ws:") {
    return "a page served over https may only open a secure WebSocket (wss://)";
  }
  return null;
}
