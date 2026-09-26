"use client";

import type { ReactNode } from "react";
import type { MapPadding } from "@/components/map/MapView";
import { BottomSheet } from "@/components/ui/sheet";
import { DESKTOP_QUERY, useMediaQuery } from "@/lib/useMediaQuery";

const PANEL_WIDTH_PX = 400;
const PANEL_GAP_PX = 16;
const EDGE_PX = 56;
/** The map controls sit at the right edge; fitted points stay clear of them. */
const CONTROLS_PX = 72;

type RideScreenProps = {
  /** Names the panel for screen readers. */
  label: string;
  /** Renders the map, told how much of it the panel covers so fitted routes stay visible. */
  map: (padding: MapPadding) => ReactNode;
  /** How much of the mobile sheet shows before it is expanded. */
  peek?: number;
  children: ReactNode;
};

/**
 * The layout of every ride screen: the map fills the space under the header. From 1024 px the panel floats
 * over the map's left side; below that it is a bottom sheet the rider can drag open.
 */
export function RideScreen({ label, map, peek = 340, children }: RideScreenProps) {
  const desktop = useMediaQuery(DESKTOP_QUERY);
  const padding: MapPadding = desktop
    ? { top: EDGE_PX, right: CONTROLS_PX, bottom: EDGE_PX, left: PANEL_WIDTH_PX + PANEL_GAP_PX + EDGE_PX }
    : { top: EDGE_PX, right: CONTROLS_PX, bottom: peek + 24, left: 32 };
  return (
    <div className="relative h-[calc(100dvh-3.5rem)] overflow-hidden bg-surface-2">
      <div className="absolute inset-0">{map(padding)}</div>
      {desktop ? (
        <section aria-label={label}
          className="absolute left-4 top-4 z-[var(--z-float)] flex max-h-[calc(100%-2rem)] flex-col overflow-hidden rounded-sheet border border-line bg-surface shadow-float animate-rise"
          style={{ width: PANEL_WIDTH_PX }}>
          <div className="min-h-0 flex-1 overflow-y-auto p-5">{children}</div>
        </section>
      ) : (
        <BottomSheet label={label} peek={peek}>{children}</BottomSheet>
      )}
    </div>
  );
}
