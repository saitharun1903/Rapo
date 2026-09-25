"use client";

import { useQuery } from "@tanstack/react-query";
import { useEffect, useState } from "react";
import { api, unwrap } from "@/lib/api/client";
import type { Eta, GeoPoint, RideResponse } from "@/lib/api/types";
import { queryKeys } from "@/lib/queryKeys";
import { REALTIME_SNAPSHOT, useRealtimeSubscription } from "@/lib/realtime/RealtimeProvider";
import { Destinations } from "@/lib/realtime/types";
import { TRACKABLE } from "@/lib/ride/status";

/** docs/events.md §2.5: warn when no position arrived for this long. */
export const SIGNAL_LOST_AFTER_MS = 15_000;
const SIGNAL_CHECK_MS = 1_000;

export type LiveDriver = {
  point: GeoPoint | null;
  headingDeg: number | null;
  eta: Eta | null;
  signalLost: boolean;
};

type Fix = { point: GeoPoint; headingDeg: number | null; recordedAt: string; eta: Eta | null; receivedAt: number };

/**
 * The assigned driver's position for a passenger: the REST snapshot first, then pushed reports, ordered by
 * recordedAt so a late message never moves the car backwards.
 */
export function useLiveDriver(ride: RideResponse): LiveDriver {
  const trackable = TRACKABLE.includes(ride.status);
  const snapshot = useQuery({
    queryKey: queryKeys.tracking(ride.id),
    queryFn: () => unwrap(api.GET("/api/rides/{rideId}/tracking", { params: { path: { rideId: ride.id } } })),
    enabled: trackable,
    meta: REALTIME_SNAPSHOT,
  });
  const [pushed, setPushed] = useState<Fix | null>(null);
  const [now, setNow] = useState(() => Date.now());

  useRealtimeSubscription(Destinations.rideLocation, (message) => {
    if (message.rideId !== ride.id) {
      return;
    }
    setPushed((current) => current && current.recordedAt >= message.recordedAt ? current : {
      point: message.location,
      headingDeg: message.headingDeg,
      recordedAt: message.recordedAt,
      eta: message.eta,
      receivedAt: Date.now(),
    });
  }, trackable);

  useEffect(() => {
    if (!trackable) {
      return undefined;
    }
    const timer = setInterval(() => setNow(Date.now()), SIGNAL_CHECK_MS);
    return () => clearInterval(timer);
  }, [trackable]);

  const fromSnapshot = snapshot.data?.driverLocation;
  const snapshotNewer = fromSnapshot && (!pushed || fromSnapshot.recordedAt > pushed.recordedAt);
  const point = snapshotNewer ? fromSnapshot.point : pushed?.point ?? null;
  const headingDeg = snapshotNewer ? fromSnapshot.headingDeg ?? null : pushed?.headingDeg ?? null;
  const eta = pushed?.eta ?? snapshot.data?.eta ?? null;
  const lastHeard = pushed?.receivedAt ?? snapshot.dataUpdatedAt;
  const signalLost = trackable && (snapshot.data?.stale === true && !pushed
    || (lastHeard > 0 && now - lastHeard > SIGNAL_LOST_AFTER_MS));

  return { point, headingDeg, eta, signalLost };
}
