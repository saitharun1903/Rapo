import type { GeoPoint } from "@/lib/api/types";

const POSITION_TIMEOUT_MS = 10_000;
/** A fix up to this old is good enough for "use my location". */
const POSITION_MAX_AGE_MS = 30_000;

export class GeolocationUnavailableError extends Error {}

const REASONS: Record<number, string> = {
  1: "Location permission was denied.",
  2: "Your position is not available right now.",
  3: "Finding your position took too long.",
};

/** One position fix from the browser, as a promise. */
export function currentPosition(): Promise<GeolocationPosition> {
  return new Promise((resolve, reject) => {
    if (typeof navigator === "undefined" || !navigator.geolocation) {
      reject(new GeolocationUnavailableError("This browser cannot share its location."));
      return;
    }
    navigator.geolocation.getCurrentPosition(resolve,
      (error) => reject(new GeolocationUnavailableError(REASONS[error.code] ?? error.message)),
      { enableHighAccuracy: true, timeout: POSITION_TIMEOUT_MS, maximumAge: POSITION_MAX_AGE_MS });
  });
}

export function toPoint(position: GeolocationPosition): GeoPoint {
  return { lat: position.coords.latitude, lng: position.coords.longitude };
}
