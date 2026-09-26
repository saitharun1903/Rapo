"use client";

import { Marker } from "maplibre-gl";
import { useEffect, useRef } from "react";
import { useMap } from "react-map-gl/maplibre";
import type { GeoPoint } from "@/lib/api/types";
import { easeOut, lerpHeading, lerpPoint } from "@/lib/map/geometry";

/** Glide between fixes over the time that separated them, within these bounds. */
const MIN_GLIDE_MS = 400;
const MAX_GLIDE_MS = 3_000;
const FIRST_GLIDE_MS = 1_000;

type LiveCarProps = {
  point: GeoPoint;
  headingDeg: number | null | undefined;
  /** No fix for a while: the car is drawn grey. */
  stale?: boolean;
  label?: string;
  reducedMotion?: boolean;
};

function carElement(label: string): HTMLDivElement {
  const element = document.createElement("div");
  element.className = "raido-car";
  element.setAttribute("role", "img");
  element.setAttribute("aria-label", label);
  element.innerHTML = '<span class="raido-car__halo"></span><span class="raido-car__body">'
    + '<svg viewBox="0 0 24 24" aria-hidden="true"><path d="M12 4 18 18 12 15 6 18Z"/></svg></span>';
  return element;
}

/**
 * The moving car. It is a MapLibre marker driven directly: each new fix starts a glide from where the car is
 * drawn now to the new position, over about the time between the two fixes, so the car moves continuously
 * instead of jumping. Nothing in React or the map re-renders per frame; only the marker's position and rotation
 * change. With reduced motion the car moves straight to each fix.
 */
export function LiveCar({ point, headingDeg, stale = false, label = "Driver", reducedMotion = false }: LiveCarProps) {
  const { current: mapRef } = useMap();
  const marker = useRef<Marker | null>(null);
  const shown = useRef<{ point: GeoPoint; heading: number } | null>(null);
  const lastFixAt = useRef<number | null>(null);
  const frame = useRef<number | null>(null);

  useEffect(() => {
    const map = mapRef?.getMap();
    if (!map) {
      return;
    }
    const created = new Marker({ element: carElement(label), rotationAlignment: "map", pitchAlignment: "map" })
      .setLngLat([point.lng, point.lat])
      .setRotation(headingDeg ?? 0)
      .addTo(map);
    marker.current = created;
    shown.current = { point, heading: headingDeg ?? 0 };
    return () => {
      if (frame.current !== null) {
        cancelAnimationFrame(frame.current);
      }
      created.remove();
      marker.current = null;
    };
    // Created once per map; later fixes animate the same marker.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [mapRef]);

  useEffect(() => {
    const current = marker.current;
    const from = shown.current;
    if (!current || !from) {
      return;
    }
    const now = performance.now();
    const toHeading = headingDeg ?? from.heading;
    const duration = reducedMotion ? 0
      : Math.min(MAX_GLIDE_MS, Math.max(MIN_GLIDE_MS, lastFixAt.current === null ? FIRST_GLIDE_MS : now - lastFixAt.current));
    lastFixAt.current = now;
    if (frame.current !== null) {
      cancelAnimationFrame(frame.current);
    }
    const step = (time: number) => {
      const t = duration === 0 ? 1 : easeOut((time - now) / duration);
      const position = lerpPoint(from.point, point, t);
      const heading = lerpHeading(from.heading, toHeading, t);
      current.setLngLat([position.lng, position.lat]).setRotation(heading);
      shown.current = { point: position, heading };
      frame.current = t < 1 ? requestAnimationFrame(step) : null;
    };
    frame.current = requestAnimationFrame(step);
    // A new fix is new coordinates, not a new object with the same ones.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [point.lat, point.lng, headingDeg, reducedMotion]);

  useEffect(() => {
    marker.current?.getElement().classList.toggle("raido-car--stale", stale);
  }, [stale]);

  return null;
}
