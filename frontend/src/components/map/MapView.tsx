"use client";

import clsx from "clsx";
import { Car } from "lucide-react";
import { useTheme } from "next-themes";
import { useEffect, useMemo, useRef, useState } from "react";
import Map, { Layer, Marker, NavigationControl, Source, type MapLayerMouseEvent, type MapRef } from "react-map-gl/maplibre";
import { setWorkerUrl } from "maplibre-gl";
import type { GeoPoint } from "@/lib/api/types";
import { config } from "@/lib/config";

const DEFAULT_ZOOM = 12.5;
const FIT_PADDING_PX = 72;

/** Space kept clear around fitted points, for panels and sheets that cover part of the map. */
export type MapPadding = { top: number; right: number; bottom: number; left: number };
const FIT_MAX_ZOOM = 15.5;
const FIT_DURATION_MS = 600;
const ROUTE_WIDTH_PX = 5;

export type MapViewProps = {
  /** Describes what the map shows, for screen readers. */
  label: string;
  center?: GeoPoint;
  pickup?: GeoPoint | null;
  dropoff?: GeoPoint | null;
  driver?: { point: GeoPoint; headingDeg?: number | null } | null;
  nearby?: GeoPoint[];
  route?: GeoPoint[] | null;
  /** The map zooms to show all of these whenever they change. */
  fitTo?: GeoPoint[];
  /** Kept clear of fitted points; defaults to an even margin. */
  padding?: MapPadding;
  /** Draws a searching pulse around the pickup while a driver is being found. */
  searching?: boolean;
  onPick?: (point: GeoPoint) => void;
  className?: string;
};

/**
 * MapLibre's worker, served from public/maplibre by scripts/copy-maplibre-worker.mjs. Left to itself, bundled
 * MapLibre looks for it next to its own chunk, where the build never puts it, and no map would ever draw.
 */
setWorkerUrl("/maplibre/maplibre-gl-worker.mjs");

/** Used until the stylesheet is readable; matches --brand in globals.css. */
const FALLBACK_BRAND = "#5b4bdb";

/** Paint properties need concrete colours, so the theme's CSS variables are resolved when the theme changes. */
function useTokenColors(): { brand: string } {
  const { resolvedTheme } = useTheme();
  return useMemo(() => {
    const brand = getComputedStyle(document.documentElement).getPropertyValue("--brand").trim();
    return { brand: brand || FALLBACK_BRAND };
    // The value depends on the theme class next-themes sets on <html>.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [resolvedTheme]);
}

function Pin({ tone, label }: { tone: "brand" | "accent"; label: string }) {
  return (
    <div aria-label={label} className={clsx("flex size-8 items-center justify-center rounded-full border-4 border-white shadow-float",
      tone === "brand" ? "bg-brand" : "bg-ink")}>
      <div className="size-2 rounded-full bg-white" />
    </div>
  );
}

/**
 * The only component that knows the map library (MapLibre GL through react-map-gl). Changing the tile
 * provider is a matter of NEXT_PUBLIC_MAP_STYLE_URL; changing the library only touches this folder.
 */
export default function MapView({ label, center = config.mapCenter, pickup, dropoff, driver, nearby = [], route,
                                  fitTo = [], padding, searching = false, onPick, className }: MapViewProps) {
  const mapRef = useRef<MapRef>(null);
  const colors = useTokenColors();
  // How far MapLibre got (loading, then its style loaded, then fully drawn once) and its last error, exposed as
  // data attributes: tests wait for "drawn", and a map that never gets there says why.
  const [stage, setStage] = useState<"loading" | "loaded" | "drawn">("loading");
  const [lastError, setLastError] = useState<string | null>(null);
  const fitKey = fitTo.map((point) => `${point.lat.toFixed(5)},${point.lng.toFixed(5)}`).join("|");
  const fitPadding = padding ?? { top: FIT_PADDING_PX, right: FIT_PADDING_PX, bottom: FIT_PADDING_PX, left: FIT_PADDING_PX };
  const paddingKey = `${fitPadding.top},${fitPadding.right},${fitPadding.bottom},${fitPadding.left}`;

  useEffect(() => {
    const map = mapRef.current;
    if (!map || fitTo.length === 0) {
      return;
    }
    if (fitTo.length === 1) {
      map.easeTo({ center: [fitTo[0].lng, fitTo[0].lat], padding: fitPadding, duration: FIT_DURATION_MS });
      return;
    }
    const lngs = fitTo.map((point) => point.lng);
    const lats = fitTo.map((point) => point.lat);
    map.fitBounds([[Math.min(...lngs), Math.min(...lats)], [Math.max(...lngs), Math.max(...lats)]],
      { padding: fitPadding, maxZoom: FIT_MAX_ZOOM, duration: FIT_DURATION_MS });
    // fitKey and paddingKey capture the values; the arrays and objects are new on every render.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [fitKey, paddingKey]);

  const routeData = useMemo(() => route && route.length > 1 ? {
    type: "Feature" as const,
    properties: {},
    geometry: { type: "LineString" as const, coordinates: route.map((point) => [point.lng, point.lat]) },
  } : null, [route]);

  const onClick = (event: MapLayerMouseEvent) => onPick?.({ lat: event.lngLat.lat, lng: event.lngLat.lng });

  return (
    <div role="region" aria-label={label} data-map-stage={stage} data-map-error={lastError ?? undefined}
      className={clsx("relative size-full min-h-72", className)}>
      <Map ref={mapRef} mapStyle={config.mapStyleUrl} initialViewState={{ latitude: center.lat, longitude: center.lng, zoom: DEFAULT_ZOOM }}
        onLoad={() => setStage((current) => (current === "drawn" ? current : "loaded"))} onIdle={() => setStage("drawn")}
        onError={(event) => {
          console.error("Map error", event.error);
          setLastError(event.error?.message ?? String(event.error));
        }}
        onClick={onPick ? onClick : undefined} cursor={onPick ? "crosshair" : undefined} style={{ position: "absolute", inset: 0 }}>
        <NavigationControl position="bottom-right" showCompass={false} />
        {routeData && (
          <Source id="route" type="geojson" data={routeData}>
            <Layer id="route-line" type="line" layout={{ "line-cap": "round", "line-join": "round" }}
              paint={{ "line-color": colors.brand, "line-width": ROUTE_WIDTH_PX, "line-opacity": 0.9 }} />
          </Source>
        )}
        {nearby.map((point, index) => (
          <Marker key={`${point.lat},${point.lng},${index}`} latitude={point.lat} longitude={point.lng}>
            <div className="flex size-6 items-center justify-center rounded-full bg-fg/80 text-bg shadow" aria-hidden>
              <Car className="size-3.5" />
            </div>
          </Marker>
        ))}
        {pickup && searching && (
          <Marker latitude={pickup.lat} longitude={pickup.lng}>
            <div aria-hidden className="pointer-events-none relative size-56">
              <span className="absolute inset-0 rounded-full border-2 border-brand/60 bg-brand/10 animate-search" />
              <span className="absolute inset-0 rounded-full border-2 border-brand/50 bg-brand/5 animate-search [animation-delay:1.2s]" />
            </div>
          </Marker>
        )}
        {pickup && <Marker latitude={pickup.lat} longitude={pickup.lng}><Pin tone="brand" label="Pickup" /></Marker>}
        {dropoff && <Marker latitude={dropoff.lat} longitude={dropoff.lng}><Pin tone="accent" label="Destination" /></Marker>}
        {driver && (
          <Marker latitude={driver.point.lat} longitude={driver.point.lng}>
            <div aria-label="Driver" className="flex size-10 items-center justify-center rounded-full border-4 border-white bg-fg text-bg shadow-float"
              style={driver.headingDeg === null || driver.headingDeg === undefined ? undefined : { transform: `rotate(${driver.headingDeg}deg)` }}>
              <Car className="size-5" aria-hidden />
            </div>
          </Marker>
        )}
      </Map>
    </div>
  );
}
