"use client";

import { useCallback, useSyncExternalStore } from "react";

/** Whether a CSS media query matches, kept current as the window changes. False where matchMedia is missing. */
export function useMediaQuery(query: string): boolean {
  const subscribe = useCallback((onChange: () => void) => {
    if (typeof window === "undefined" || !window.matchMedia) {
      return () => undefined;
    }
    const list = window.matchMedia(query);
    list.addEventListener("change", onChange);
    return () => list.removeEventListener("change", onChange);
  }, [query]);
  return useSyncExternalStore(
    subscribe,
    () => typeof window !== "undefined" && Boolean(window.matchMedia?.(query).matches),
    () => false,
  );
}

/** The width from which ride screens put their panel beside the map instead of in a bottom sheet. */
export const DESKTOP_QUERY = "(min-width: 1024px)";
