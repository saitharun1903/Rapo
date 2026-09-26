"use client";

import { useEffect, useRef, useState, useSyncExternalStore } from "react";
import type { ConnectionState } from "./types";

export type ConnectionNotice = "offline" | "reconnecting" | "unavailable" | "restored" | null;

/** An outage shorter than this is not worth announcing when it ends. */
export const RESTORED_MIN_OUTAGE_MS = 2_000;
/** How long "Connection restored" stays up. */
export const RESTORED_VISIBLE_MS = 3_000;

function subscribeOnline(onChange: () => void): () => void {
  window.addEventListener("online", onChange);
  window.addEventListener("offline", onChange);
  return () => {
    window.removeEventListener("online", onChange);
    window.removeEventListener("offline", onChange);
  };
}

/** The browser's own view of the network; assumed online during server rendering. */
export function useOnline(): boolean {
  return useSyncExternalStore(subscribeOnline, () => navigator.onLine, () => true);
}

/**
 * What the connection banner should say. The device being offline outranks everything; a socket that is
 * reconnecting or unusable comes next. "restored" appears once, briefly, and only after an outage long enough
 * that the user may have noticed it: a first connect or a blip is not news.
 */
export function useConnectionNotice(state: ConnectionState, now: () => number = Date.now): ConnectionNotice {
  const online = useOnline();
  const outageStartedAt = useRef<number | null>(null);
  const [restored, setRestored] = useState(false);
  const troubled = !online || state === "reconnecting";

  useEffect(() => {
    if (troubled) {
      outageStartedAt.current ??= now();
      // A new outage replaces any "restored" notice still showing from the last one.
      // eslint-disable-next-line react-hooks/set-state-in-effect
      setRestored(false);
      return;
    }
    const startedAt = outageStartedAt.current;
    outageStartedAt.current = null;
    if (state !== "connected" || startedAt === null || now() - startedAt < RESTORED_MIN_OUTAGE_MS) {
      return;
    }
    setRestored(true);
    const timer = window.setTimeout(() => setRestored(false), RESTORED_VISIBLE_MS);
    return () => window.clearTimeout(timer);
  }, [troubled, state, now]);

  if (!online) {
    return "offline";
  }
  if (state === "unavailable") {
    return "unavailable";
  }
  if (state === "reconnecting") {
    return "reconnecting";
  }
  return restored ? "restored" : null;
}
