"use client";

import { useQuery, useQueryClient } from "@tanstack/react-query";
import { useState } from "react";
import { api, unwrapOptional } from "@/lib/api/client";
import type { RideResponse } from "@/lib/api/types";
import { queryKeys } from "@/lib/queryKeys";
import { REALTIME_SNAPSHOT, useRealtimeSubscription } from "@/lib/realtime/RealtimeProvider";
import { Destinations } from "@/lib/realtime/types";
import { isTerminal, newerRide } from "./status";

/**
 * The caller's ride in progress, kept current by pushes on /user/queue/rides and re-read from REST after a
 * reconnect. When the ride ends, `finished` holds its last state so the page can show the outcome (the
 * active-ride endpoint no longer returns it).
 */
export function useActiveRide() {
  const queryClient = useQueryClient();
  const [finished, setFinished] = useState<RideResponse | null>(null);
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

  return { active, finished, dismissFinished: () => setFinished(null), showFinished: setFinished };
}
