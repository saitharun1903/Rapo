"use client";

import { useQuery, useQueryClient } from "@tanstack/react-query";
import { useEffect, useRef, useState } from "react";
import { api, unwrap, unwrapOptional } from "@/lib/api/client";
import type { RideResponse } from "@/lib/api/types";
import { queryKeys } from "@/lib/queryKeys";
import { REALTIME_SNAPSHOT, useRealtimeSubscription } from "@/lib/realtime/RealtimeProvider";
import { Destinations } from "@/lib/realtime/types";
import { isTerminal, newerRide } from "./status";

/**
 * The caller's ride in progress, kept current by pushes on /user/queue/rides and re-read from REST after a
 * reconnect. When the ride ends, `finished` holds its last state so the page can show the outcome (the
 * active-ride endpoint no longer returns it). If the push announcing the end was lost, the ride is read once
 * when it disappears from the active-ride endpoint.
 */
export function useActiveRide() {
  const queryClient = useQueryClient();
  const [finished, setFinished] = useState<RideResponse | null>(null);
  const lastShown = useRef<string | null>(null);
  const active = useQuery({
    queryKey: queryKeys.activeRide,
    queryFn: () => unwrapOptional(api.GET("/api/rides/active")),
    meta: REALTIME_SNAPSHOT,
  });

  useRealtimeSubscription(Destinations.rides, (ride) => {
    queryClient.setQueryData(queryKeys.ride(ride.id), (current: RideResponse | undefined) => newerRide(current, ride));
    const shown = queryClient.getQueryData<RideResponse | null>(queryKeys.activeRide);
    if (shown && shown.id === ride.id && newerRide(shown, ride) === shown) {
      return;
    }
    if (isTerminal(ride.status)) {
      if (!shown || shown.id === ride.id) {
        setFinished(ride);
        queryClient.setQueryData(queryKeys.activeRide, null);
      }
      return;
    }
    setFinished(null);
    queryClient.setQueryData(queryKeys.activeRide, ride);
  });

  useEffect(() => {
    if (active.data) {
      lastShown.current = active.data.id;
      return;
    }
    const rideId = lastShown.current;
    if (active.data !== null || rideId === null) {
      return;
    }
    // Forget it either way, so dismissing the outcome does not bring it back.
    lastShown.current = null;
    if (finished?.id === rideId) {
      return;
    }
    unwrap(api.GET("/api/rides/{rideId}", { params: { path: { rideId } } }))
      .then((ride) => {
        if (isTerminal(ride.status)) {
          setFinished(ride);
        }
      })
      .catch((error: unknown) => console.warn("Could not load the ride that just ended", error));
  }, [active.data, finished]);

  return { active, finished, dismissFinished: () => setFinished(null) };
}
