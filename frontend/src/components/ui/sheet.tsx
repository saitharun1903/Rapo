"use client";

import clsx from "clsx";
import { useEffect, useId, useRef, useState, type PointerEvent, type ReactNode } from "react";

/** A drag shorter than this is treated as a tap on the handle. */
const TAP_SLOP_PX = 6;
/** A flick faster than this (px per ms) snaps in its direction whatever the distance. */
const FLICK_VELOCITY = 0.5;

type BottomSheetProps = {
  /** Names the sheet for screen readers. */
  label: string;
  /** Visible height when collapsed, in pixels, handle included. */
  peek: number;
  children: ReactNode;
  className?: string;
};

/**
 * The mobile ride panel: a sheet over the map that rests at {@code peek} pixels and expands to most of the
 * screen. It can be dragged or flicked by its handle, and the handle is also a button, so it works from the
 * keyboard and with screen readers. The map stays interactive above it.
 */
export function BottomSheet({ label, peek, children, className }: BottomSheetProps) {
  const sheetRef = useRef<HTMLElement>(null);
  const contentId = useId();
  const [height, setHeight] = useState(0);
  const [expanded, setExpanded] = useState(false);
  const [dragOffset, setDragOffset] = useState<number | null>(null);
  const gesture = useRef<{ startY: number; startOffset: number; lastY: number; lastTime: number; velocity: number } | null>(null);

  // Without ResizeObserver (tests), the sheet simply stays open.
  const [measurable] = useState(() => typeof ResizeObserver !== "undefined");

  useEffect(() => {
    const sheet = sheetRef.current;
    if (!sheet || !measurable) {
      return;
    }
    const observer = new ResizeObserver(([entry]) => setHeight(entry.contentRect.height));
    observer.observe(sheet);
    return () => observer.disconnect();
  }, [measurable]);

  const collapsedOffset = Math.max(0, height - peek);
  const offset = dragOffset ?? (expanded ? 0 : collapsedOffset);

  const onPointerDown = (event: PointerEvent<HTMLButtonElement>) => {
    // Missing in some test environments; the drag still works without capture, only less reliably.
    event.currentTarget.setPointerCapture?.(event.pointerId);
    gesture.current = { startY: event.clientY, startOffset: offset, lastY: event.clientY, lastTime: event.timeStamp, velocity: 0 };
  };

  const onPointerMove = (event: PointerEvent<HTMLButtonElement>) => {
    const current = gesture.current;
    if (!current) {
      return;
    }
    const elapsed = Math.max(1, event.timeStamp - current.lastTime);
    current.velocity = (event.clientY - current.lastY) / elapsed;
    current.lastY = event.clientY;
    current.lastTime = event.timeStamp;
    const moved = event.clientY - current.startY;
    if (Math.abs(moved) >= TAP_SLOP_PX || dragOffset !== null) {
      setDragOffset(Math.min(collapsedOffset, Math.max(0, current.startOffset + moved)));
    }
  };

  const onPointerUp = () => {
    const current = gesture.current;
    gesture.current = null;
    if (!current || dragOffset === null) {
      return;
    }
    const next = Math.abs(current.velocity) > FLICK_VELOCITY ? current.velocity < 0 : dragOffset < collapsedOffset / 2;
    setExpanded(next);
    setDragOffset(null);
  };

  const onClick = () => {
    // A drag ends with a click event too; only a tap toggles.
    if (dragOffset === null) {
      setExpanded((value) => !value);
    }
  };

  const visible = height - offset;
  return (
    <section ref={sheetRef} aria-label={label}
      className={clsx("absolute inset-x-0 bottom-0 z-[var(--z-sheet)] flex h-[88%] flex-col rounded-t-sheet border-t border-line bg-surface shadow-float",
        dragOffset === null && "transition-transform duration-300 ease-out-soft", className)}
      style={{ transform: `translateY(${offset}px)`, visibility: measurable && height === 0 ? "hidden" : undefined }}>
      <button type="button" aria-expanded={expanded} aria-controls={contentId}
        aria-label={expanded ? "Collapse ride panel" : "Expand ride panel"}
        onPointerDown={onPointerDown} onPointerMove={onPointerMove} onPointerUp={onPointerUp} onPointerCancel={onPointerUp}
        onClick={onClick}
        className="flex h-7 w-full shrink-0 touch-none cursor-grab items-center justify-center active:cursor-grabbing">
        <span className="h-1 w-10 rounded-full bg-line-strong" aria-hidden />
      </button>
      <div id={contentId} className="min-h-0 overflow-y-auto overscroll-contain px-4 pb-6"
        style={measurable ? { height: Math.max(0, visible - 28) } : undefined}>
        {children}
      </div>
    </section>
  );
}
