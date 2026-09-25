"use client";

import { useEffect, useRef, useState } from "react";
import { api, unwrap } from "@/lib/api/client";
import { errorMessage } from "@/lib/api/errors";
import type { GeoPoint } from "@/lib/api/types";
import { compassHeading } from "@/lib/geolocation";
import { useRealtime, useRealtimeSubscription } from "@/lib/realtime/RealtimeProvider";
import { Destinations, type LocationReport } from "@/lib/realtime/types";

/**
 * How often an online driver reports. The server accepts one per second; two minutes of silence takes a
 * driver offline and matching ignores positions older than 30 s, so a few seconds keeps well inside both.
 */
export const REPORT_INTERVAL_MS = 4_000;
const GPS_MAX_AGE_MS = 5_000;
const GPS_TIMEOUT_MS = 20_000;

const NO_GPS = "This browser cannot share its location. Place yourself on the map instead.";

function gpsSupported(): boolean {
  return typeof navigator !== "undefined" && Boolean(navigator.geolocation);
}

export type DriverPosition = {
  point: GeoPoint;
  headingDeg: number | null;
  speedMps: number | null;
  accuracyMeters: number | null;
};

export type LocationMode = "gps" | "manual";

/**
 * The driver's position and, while online, its reports: over the WebSocket, or the REST endpoint when the
 * socket is down. Manual mode is for testing without GPS (a desktop browser): the driver places themself on
 * the map, and that position is reported like any other.
 */
export function useDriverLocation(online: boolean) {
  const { sendLocation } = useRealtime();
  const [mode, setMode] = useState<LocationMode>("gps");
  const [position, setPosition] = useState<DriverPosition | null>(null);
  const [gpsError, setGpsError] = useState<string | null>(null);
  const [reportError, setReportError] = useState<string | null>(null);
  const latest = useRef<DriverPosition | null>(null);

  useEffect(() => {
    latest.current = position;
  }, [position]);

  useEffect(() => {
    if (mode !== "gps" || !gpsSupported()) {
      return undefined;
    }
    const watch = navigator.geolocation.watchPosition((fix) => {
      setGpsError(null);
      setPosition({
        point: { lat: fix.coords.latitude, lng: fix.coords.longitude },
        headingDeg: compassHeading(fix.coords.heading),
        speedMps: fix.coords.speed,
        accuracyMeters: fix.coords.accuracy,
      });
    }, (error) => setGpsError(`${error.message}. Place yourself on the map instead.`),
    { enableHighAccuracy: true, maximumAge: GPS_MAX_AGE_MS, timeout: GPS_TIMEOUT_MS });
    return () => navigator.geolocation.clearWatch(watch);
  }, [mode]);

  // A report sent over the socket that the server refuses comes back on the error queue, not as a failed request.
  useRealtimeSubscription(Destinations.errors, (message) => {
    if (message.destination === Destinations.driverLocation) {
      setReportError(message.message);
    }
  }, online);

  useEffect(() => {
    if (!online) {
      return undefined;
    }
    const report = () => {
      const current = latest.current;
      if (!current) {
        return;
      }
      // The device is at this position now, even if the fix itself is older (a parked phone reports no change).
      const body: LocationReport = {
        location: current.point,
        headingDeg: current.headingDeg,
        speedMps: current.speedMps,
        accuracyMeters: current.accuracyMeters,
        recordedAt: new Date().toISOString(),
      };
      if (sendLocation(body)) {
        setReportError(null);
        return;
      }
      unwrap(api.POST("/api/drivers/location", { body }))
        .then(() => setReportError(null))
        .catch((error: unknown) => setReportError(errorMessage(error)));
    };
    report();
    const timer = setInterval(report, REPORT_INTERVAL_MS);
    return () => clearInterval(timer);
  }, [online, sendLocation]);

  return {
    position,
    mode,
    gpsError: mode !== "gps" ? null : gpsSupported() ? gpsError : NO_GPS,
    reportError,
    placeManually: (point: GeoPoint) => {
      setMode("manual");
      setPosition({ point, headingDeg: null, speedMps: null, accuracyMeters: null });
    },
    useGps: () => setMode("gps"),
  };
}
