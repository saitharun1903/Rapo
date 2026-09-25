/**
 * Browser-visible settings. NEXT_PUBLIC_ variables are inlined at build time; the defaults suit local
 * development against a backend on port 8080. None of these is a secret.
 */

const DEFAULT_WS_URL = "ws://localhost:8080/ws";
/** OpenFreeMap's hosted style: vector tiles without an API key. */
const DEFAULT_MAP_STYLE_URL = "https://tiles.openfreemap.org/styles/liberty";
/** Hyderabad, the demo city (matches the backend's default service area). */
const DEFAULT_CENTER = { lat: 17.385, lng: 78.4867 };

/**
 * A build-time setting, or the fallback when it is missing or empty. Empty matters: a Docker build argument
 * that is declared but not passed reaches `next build` as "", which `??` would keep.
 */
export function textFrom(value: string | undefined, fallback: string): string {
  return value === undefined || value.trim() === "" ? fallback : value;
}

export function numberFrom(value: string | undefined, fallback: number): number {
  const parsed = value === undefined || value === "" ? Number.NaN : Number(value);
  return Number.isFinite(parsed) ? parsed : fallback;
}

export const config = {
  /** Empty means same origin: requests go to /api on the frontend, which Next forwards to the backend. */
  apiBaseUrl: process.env.NEXT_PUBLIC_API_BASE_URL ?? "",
  wsUrl: textFrom(process.env.NEXT_PUBLIC_WS_URL, DEFAULT_WS_URL),
  mapStyleUrl: textFrom(process.env.NEXT_PUBLIC_MAP_STYLE_URL, DEFAULT_MAP_STYLE_URL),
  mapCenter: {
    lat: numberFrom(process.env.NEXT_PUBLIC_MAP_CENTER_LAT, DEFAULT_CENTER.lat),
    lng: numberFrom(process.env.NEXT_PUBLIC_MAP_CENTER_LNG, DEFAULT_CENTER.lng),
  },
} as const;
