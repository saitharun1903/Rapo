"use client";

import clsx from "clsx";
import { Focus, LocateFixed, Minus, Plus } from "lucide-react";
import { useMap } from "react-map-gl/maplibre";
import { IconButton } from "@/components/ui/button";

type MapControlsProps = {
  onLocate: () => void;
  locating: boolean;
  /** Puts the ride back in view; absent when there is nothing to recentre on. */
  onRecenter?: () => void;
  /** The user moved the map away from the ride: the recenter control is drawn as the way back. */
  recenterHighlighted: boolean;
};

/** Raido's map controls, top right, clear of the ride panel on every screen size. */
export function MapControls({ onLocate, locating, onRecenter, recenterHighlighted }: MapControlsProps) {
  const { current: map } = useMap();
  return (
    <div className="absolute right-3 top-3 z-[var(--z-map-controls)] flex flex-col gap-2">
      <div className="flex flex-col overflow-hidden rounded-control bg-surface shadow-raise">
        <IconButton label="Zoom in" icon={<Plus className="size-4" aria-hidden />} onClick={() => map?.zoomIn()} className="rounded-none" />
        <span aria-hidden className="h-px bg-line" />
        <IconButton label="Zoom out" icon={<Minus className="size-4" aria-hidden />} onClick={() => map?.zoomOut()} className="rounded-none" />
      </div>
      <IconButton label="Show my location" tone="raised" onClick={onLocate} disabled={locating}
        icon={<LocateFixed className={clsx("size-4", locating && "animate-pulse")} aria-hidden />} />
      {onRecenter && (
        <IconButton label="Recenter on the ride" tone="raised" onClick={onRecenter}
          className={clsx(recenterHighlighted && "bg-ink text-ink-fg hover:bg-ink hover:text-ink-fg")}
          icon={<Focus className="size-4" aria-hidden />} />
      )}
    </div>
  );
}
