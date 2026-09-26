"use client";

import { useQuery } from "@tanstack/react-query";
import clsx from "clsx";
import { setWorkerUrl, type StyleSpecification } from "maplibre-gl";
import { useTheme } from "next-themes";
import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import Map, { Layer, Marker, Source, type MapLayerMouseEvent, type MapRef, type ViewStateChangeEvent } from "react-map-gl/maplibre";
import { toast } from "sonner";
import { Button } from "@/components/ui/button";
import { Skeleton } from "@/components/ui/surface";
import type { GeoPoint } from "@/lib/api/types";
import { config } from "@/lib/config";
import { currentPosition, toPoint } from "@/lib/geolocation";
import { boundsOf, circlePolygon, splitRoute } from "@/lib/map/geometry";
import { themeStyle, type MapTheme } from "@/lib/map/style";
import { useMediaQuery } from "@/lib/useMediaQuery";
import { LiveCar } from "./LiveCar";
import { MapControls } from "./MapControls";

const DEFAULT_ZOOM = 12.5;
const FIT_PADDING_PX = 72;
const FIT_MAX_ZOOM = 15.5;
const CAMERA_DURATION_MS = 700;
const LOCATE_ZOOM = 15;
const ROUTE_WIDTH_PX = 5;
const ROUTE_CASING_PX = 9;

/** Space kept clear around fitted points, for panels and sheets that cover part of the map. */
export type MapPadding = { top: number; right: number; bottom: number; left: number };

export type MapViewProps = {
  /** Describes what the map shows, for screen readers. */
  label: string;
  center?: GeoPoint;
  pickup?: GeoPoint | null;
  dropoff?: GeoPoint | null;
  driver?: { point: GeoPoint; headingDeg?: number | null; stale?: boolean } | null;
  nearby?: GeoPoint[];
  route?: GeoPoint[] | null;
  /** Splits the route into driven and still to drive at this point: the car, during a trip. */
  progressAt?: GeoPoint | null;
  /** The camera fits these points when the {@link cameraKey} changes. */
  fitTo?: GeoPoint[];
  /** A phase of the ride: the camera refits only when it changes. Defaults to the fitted points themselves. */
  cameraKey?: string;
  /** Kept on screen: when it leaves the visible area, the camera refits. It is not re-centred on every update. */
  follow?: GeoPoint | null;
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

type Colors = { brand: string; casing: string; travelled: string; accuracy: string };

/** Paint properties need concrete colours, so the theme's CSS variables are resolved when the theme changes. */
function useTokenColors(theme: MapTheme): Colors {
  return useMemo(() => {
    const styles = getComputedStyle(document.documentElement);
    const read = (name: string, fallback: string) => styles.getPropertyValue(name).trim() || fallback;
    return {
      brand: read("--brand", "#e5561f"),
      casing: read("--surface", theme === "dark" ? "#171815" : "#ffffff"),
      travelled: read("--line-strong", "#c9c9c1"),
      accuracy: read("--fg", "#121311"),
    };
    // The values depend on the theme class next-themes puts on <html>, which `theme` follows.
  }, [theme]);
}

/** The base style, fetched once and restyled to Raido's palette for the current theme (lib/map/style.ts). */
function useMapStyle(theme: MapTheme) {
  const base = useQuery({
    queryKey: ["map-style", config.mapStyleUrl],
    queryFn: async (): Promise<StyleSpecification> => {
      const response = await fetch(config.mapStyleUrl);
      if (!response.ok) {
        throw new Error(`The map style could not be loaded (HTTP ${response.status})`);
      }
      return response.json() as Promise<StyleSpecification>;
    },
    staleTime: Infinity,
    gcTime: Infinity,
    retry: 1,
  });
  const style = useMemo(() => (base.data ? themeStyle(base.data, theme) : null), [base.data, theme]);
  return { style, error: base.error, retry: () => void base.refetch() };
}

function feature(points: GeoPoint[]) {
  return {
    type: "Feature" as const,
    properties: {},
    geometry: { type: "LineString" as const, coordinates: points.map((point) => [point.lng, point.lat]) },
  };
}

function PickupPin() {
  return <div role="img" aria-label="Pickup" className="size-5 rounded-full border-[5px] border-ink bg-surface shadow-raise" />;
}

function DestinationPin() {
  return (
    <div role="img" aria-label="Destination" className="flex flex-col items-center">
      <span className="flex size-6 items-center justify-center rounded-[6px] bg-ink shadow-raise">
        <span className="size-2 rounded-[2px] bg-surface" />
      </span>
      <span className="h-2 w-0.5 bg-ink" />
    </div>
  );
}

const pointKey = (point: GeoPoint) => `${point.lat.toFixed(5)},${point.lng.toFixed(5)}`;

/**
 * The only component that knows the map library (MapLibre GL through react-map-gl). The base style is
 * NEXT_PUBLIC_MAP_STYLE_URL (OpenFreeMap Positron by default), restyled per theme; changing the library only
 * touches this folder.
 *
 * <p>The camera fits the ride's points once per phase ({@link MapViewProps.cameraKey}) and afterwards only when
 * the followed car leaves the screen. Once the user pans or zooms, it stops moving on its own until they press
 * recenter or the phase changes.
 */
export default function MapView({ label, center = config.mapCenter, pickup, dropoff, driver, nearby = [], route, progressAt,
                                  fitTo = [], cameraKey, follow, padding, searching = false, onPick, className }: MapViewProps) {
  const mapRef = useRef<MapRef>(null);
  const { resolvedTheme } = useTheme();
  const theme: MapTheme = resolvedTheme === "dark" ? "dark" : "light";
  const colors = useTokenColors(theme);
  const { style, error: styleError, retry } = useMapStyle(theme);
  const reducedMotion = useMediaQuery("(prefers-reduced-motion: reduce)");
  // How far MapLibre got (loading, then its style loaded, then fully drawn once) and its last error, exposed as
  // data attributes: tests wait for "drawn", and a map that never gets there says why.
  const [stage, setStage] = useState<"loading" | "loaded" | "drawn">("loading");
  const [lastError, setLastError] = useState<string | null>(null);
  const [you, setYou] = useState<{ point: GeoPoint; accuracy: number } | null>(null);
  const [locating, setLocating] = useState(false);

  const fitKey = fitTo.map(pointKey).join("|");
  const phase = cameraKey ?? fitKey;
  const fitPadding = padding ?? { top: FIT_PADDING_PX, right: FIT_PADDING_PX, bottom: FIT_PADDING_PX, left: FIT_PADDING_PX };
  const paddingKey = `${fitPadding.top},${fitPadding.right},${fitPadding.bottom},${fitPadding.left}`;
  // The phase in which the user last moved the map; automatic camera moves wait until the phase changes.
  const [movedInPhase, setMovedInPhase] = useState<string | null>(null);
  const userMoved = movedInPhase === phase;
  const duration = reducedMotion ? 0 : CAMERA_DURATION_MS;
  const ready = stage !== "loading";

  const fit = useCallback((points: GeoPoint[]) => {
    const map = mapRef.current;
    const bounds = boundsOf(points);
    if (!map || !bounds) {
      return;
    }
    // Always fitBounds, even for one point: easeTo with padding would leave that padding on the camera, and
    // MapLibre adds the camera's padding to every later fit's own.
    map.fitBounds(bounds, { padding: fitPadding, maxZoom: FIT_MAX_ZOOM, duration });
    // paddingKey stands for fitPadding, a new object on every render.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [paddingKey, duration]);

  useEffect(() => {
    if (ready) {
      fit(fitTo);
    }
    // Refit on a new phase (the points themselves when no cameraKey is given), new padding, and once the map can
    // move; never because a followed car moved.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [phase, paddingKey, ready]);

  const followKey = follow ? pointKey(follow) : null;
  useEffect(() => {
    const map = mapRef.current;
    if (!map || !follow || userMoved || !ready) {
      return;
    }
    const { x, y } = map.project([follow.lng, follow.lat]);
    const canvas = map.getCanvas();
    const visible = x >= fitPadding.left && x <= canvas.clientWidth - fitPadding.right
      && y >= fitPadding.top && y <= canvas.clientHeight - fitPadding.bottom;
    if (!visible) {
      fit([follow, ...fitTo]);
    }
    // followKey stands for follow; only a move of the followed point matters.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [followKey, userMoved, ready]);

  const onMoveStart = (event: ViewStateChangeEvent) => {
    // Only a move the user made (with a pointer, wheel or keys) stops the automatic camera.
    if (event.originalEvent) {
      setMovedInPhase(phase);
    }
  };

  const recenter = () => {
    setMovedInPhase(null);
    fit(follow ? [follow, ...fitTo] : fitTo);
  };

  const locate = async () => {
    setLocating(true);
    try {
      const position = await currentPosition();
      const point = toPoint(position);
      setYou({ point, accuracy: position.coords.accuracy });
      setMovedInPhase(phase);
      mapRef.current?.fitBounds([[point.lng, point.lat], [point.lng, point.lat]], { padding: fitPadding, maxZoom: LOCATE_ZOOM, duration });
    } catch (error) {
      toast.error(error instanceof Error ? error.message : "Could not get your location.");
    } finally {
      setLocating(false);
    }
  };

  const segments = useMemo(() => {
    if (!route || route.length < 2) {
      return null;
    }
    const split = progressAt ? splitRoute(route, progressAt) : { travelled: [], remaining: route };
    return {
      whole: feature(route),
      remaining: split.remaining.length > 1 ? feature(split.remaining) : null,
      travelled: split.travelled.length > 1 ? feature(split.travelled) : null,
    };
  }, [route, progressAt]);

  const accuracy = useMemo(() => you ? {
    type: "Feature" as const,
    properties: {},
    geometry: { type: "Polygon" as const, coordinates: [circlePolygon(you.point, Math.max(10, you.accuracy)).map((point) => [point.lng, point.lat])] },
  } : null, [you]);

  const onClick = (event: MapLayerMouseEvent) => onPick?.({ lat: event.lngLat.lat, lng: event.lngLat.lng });

  return (
    <div role="region" aria-label={label} data-map-stage={stage} data-map-error={lastError ?? undefined} data-map-theme={theme}
      className={clsx("relative size-full min-h-72 overflow-hidden bg-surface-2", className)}>
      {style && (
        <Map ref={mapRef} mapStyle={style} initialViewState={{ latitude: center.lat, longitude: center.lng, zoom: DEFAULT_ZOOM }}
          dragRotate={false} touchPitch={false} pitchWithRotate={false} maxPitch={0}
          onLoad={() => setStage((current) => (current === "drawn" ? current : "loaded"))} onIdle={() => setStage("drawn")}
          onError={(event) => {
            console.error("Map error", event.error);
            setLastError(event.error?.message ?? String(event.error));
          }}
          onMoveStart={onMoveStart}
          onClick={onPick ? onClick : undefined} cursor={onPick ? "crosshair" : undefined} style={{ position: "absolute", inset: 0 }}>
          {accuracy && (
            <Source id="you-accuracy" type="geojson" data={accuracy}>
              <Layer id="you-accuracy-fill" type="fill" paint={{ "fill-color": colors.accuracy, "fill-opacity": 0.08 }} />
              <Layer id="you-accuracy-line" type="line" paint={{ "line-color": colors.accuracy, "line-opacity": 0.25, "line-width": 1 }} />
            </Source>
          )}
          {segments && (
            <Source id="route" type="geojson" data={segments.whole}>
              <Layer id="route-casing" type="line" layout={{ "line-cap": "round", "line-join": "round" }}
                paint={{ "line-color": colors.casing, "line-width": ROUTE_CASING_PX }} />
            </Source>
          )}
          {segments?.travelled && (
            <Source id="route-travelled" type="geojson" data={segments.travelled}>
              <Layer id="route-travelled-line" type="line" layout={{ "line-cap": "round", "line-join": "round" }}
                paint={{ "line-color": colors.travelled, "line-width": ROUTE_WIDTH_PX }} />
            </Source>
          )}
          {segments?.remaining && (
            <Source id="route-remaining" type="geojson" data={segments.remaining}>
              <Layer id="route-line" type="line" layout={{ "line-cap": "round", "line-join": "round" }}
                paint={{ "line-color": colors.brand, "line-width": ROUTE_WIDTH_PX }} />
            </Source>
          )}
          {nearby.map((point, index) => (
            <Marker key={`${point.lat},${point.lng},${index}`} latitude={point.lat} longitude={point.lng}>
              <span aria-hidden className="block size-3 rounded-full bg-fg/70 ring-2 ring-surface" />
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
          {pickup && <Marker latitude={pickup.lat} longitude={pickup.lng}><PickupPin /></Marker>}
          {dropoff && <Marker latitude={dropoff.lat} longitude={dropoff.lng} anchor="bottom"><DestinationPin /></Marker>}
          {you && (
            <Marker latitude={you.point.lat} longitude={you.point.lng}>
              <span role="img" aria-label="You are here" className="block size-3.5 rounded-full border-2 border-surface bg-ink shadow-raise" />
            </Marker>
          )}
          {driver && (
            <LiveCar point={driver.point} headingDeg={driver.headingDeg} stale={driver.stale} reducedMotion={reducedMotion} />
          )}
          <MapControls onLocate={locate} locating={locating} onRecenter={fitTo.length > 0 || follow ? recenter : undefined}
            recenterHighlighted={userMoved} />
        </Map>
      )}
      {!style && !styleError && <Skeleton className="absolute inset-0 rounded-none" />}
      {styleError && (
        <div role="alert" className="absolute inset-x-4 top-4 z-[var(--z-map-controls)] mx-auto flex max-w-sm flex-col gap-2 rounded-card border border-line bg-surface p-4 shadow-float">
          <p className="text-sm font-medium text-fg">The map could not load</p>
          <p className="text-sm text-fg-muted">Your ride is not affected: everything else on this screen keeps working.</p>
          <Button variant="secondary" size="sm" className="self-start" onClick={retry}>Try again</Button>
        </div>
      )}
    </div>
  );
}
