"use client";

import { useEffect, useState } from "react";

const DEFAULT_TICK_MS = 1_000;

/** The current time, updated every `tickMs` while mounted (for countdowns). */
export function useNow(tickMs: number = DEFAULT_TICK_MS): number {
  const [now, setNow] = useState(() => Date.now());
  useEffect(() => {
    const timer = setInterval(() => setNow(Date.now()), tickMs);
    return () => clearInterval(timer);
  }, [tickMs]);
  return now;
}
