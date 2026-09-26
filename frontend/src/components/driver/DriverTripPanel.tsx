"use client";

import { useMutation, useQueryClient } from "@tanstack/react-query";
import { UserRound } from "lucide-react";
import { useState } from "react";
import { toast } from "sonner";
import { StatusBadge } from "@/components/ride/StatusBadge";
import { Button } from "@/components/ui/button";
import { Dialog } from "@/components/ui/dialog";
import { Card } from "@/components/ui/surface";
import { api, unwrap } from "@/lib/api/client";
import { errorMessage, isApiError } from "@/lib/api/errors";
import type { RideResponse } from "@/lib/api/types";
import { formatMoney, humanize } from "@/lib/format";
import { queryKeys } from "@/lib/queryKeys";
import { nextDriverAction, type DriverAction } from "@/lib/ride/status";

const ACTION_ERRORS: Record<string, string> = {
  NOT_AT_PICKUP: "You are not at the pickup point yet. Move closer and try again.",
  LOCATION_UNAVAILABLE: "We have no recent position for you. Check that location sharing is on.",
  NO_SHOW_WAIT_NOT_ELAPSED: "You can cancel for a no-show once you have waited 5 minutes at the pickup.",
};

/** One typed call per action, so each path is checked against the API contract. */
const ACTION_CALLS: Record<DriverAction["path"], (rideId: string) => Promise<RideResponse>> = {
  "en-route": (rideId) => unwrap(api.POST("/api/rides/{rideId}/en-route", { params: { path: { rideId } } })),
  arrive: (rideId) => unwrap(api.POST("/api/rides/{rideId}/arrive", { params: { path: { rideId } } })),
  start: (rideId) => unwrap(api.POST("/api/rides/{rideId}/start", { params: { path: { rideId } } })),
  complete: (rideId) => unwrap(api.POST("/api/rides/{rideId}/complete", { params: { path: { rideId } } })),
};

function describe(error: unknown): string {
  return isApiError(error) && ACTION_ERRORS[error.code] ? ACTION_ERRORS[error.code] : errorMessage(error);
}

export function DriverTripPanel({ ride }: { ride: RideResponse }) {
  const queryClient = useQueryClient();
  const [confirmCancel, setConfirmCancel] = useState(false);
  const action = nextDriverAction(ride.status);
  const beforeArrival = ride.status === "DRIVER_ASSIGNED" || ride.status === "DRIVER_ARRIVING";

  const apply = (updated: RideResponse) => queryClient.setQueryData(queryKeys.activeRide, updated);
  const advance = useMutation({
    mutationFn: (next: DriverAction) => ACTION_CALLS[next.path](ride.id),
    onSuccess: apply,
    onError: (error) => toast.error(describe(error)),
  });
  const cancel = useMutation({
    mutationFn: () => unwrap(api.POST("/api/rides/{rideId}/cancel", {
      params: { path: { rideId: ride.id } },
      body: { reason: beforeArrival ? "Driver released the ride" : "Passenger did not show up" },
    })),
    onSuccess: () => {
      setConfirmCancel(false);
      void queryClient.invalidateQueries({ queryKey: queryKeys.activeRide });
    },
    onError: (error) => toast.error(describe(error)),
  });

  return (
    <div className="flex flex-col gap-4">
      <div className="flex items-center justify-between gap-2">
        <h1 className="text-xl font-semibold tracking-[-0.02em]" aria-live="polite">{humanize(ride.status)}</h1>
        <StatusBadge status={ride.status} />
      </div>
      <Card className="flex items-center gap-3 p-4">
        <div className="flex size-11 items-center justify-center rounded-full bg-brand-soft text-brand-strong"><UserRound className="size-5" aria-hidden /></div>
        <div className="min-w-0 flex-1">
          <p className="font-semibold">{ride.passenger?.fullName ?? "Passenger"}</p>
          <p className="text-sm text-fg-muted">{humanize(ride.paymentMethod)} · est. {formatMoney(ride.estimate.fare)}</p>
        </div>
      </Card>
      <dl className="grid grid-cols-[auto_1fr] gap-x-4 gap-y-2 text-sm">
        <dt className="text-fg-muted">Pickup</dt><dd className="font-medium text-fg">{ride.pickup.address}</dd>
        <dt className="text-fg-muted">Drop</dt><dd className="text-fg">{ride.dropoff.address}</dd>
      </dl>
      {action && (
        <div>
          <Button size="lg" className="w-full" loading={advance.isPending} onClick={() => advance.mutate(action)}>{action.label}</Button>
          <p className="mt-1 text-center text-xs text-fg-muted">{action.hint}</p>
        </div>
      )}
      {ride.status !== "IN_PROGRESS" && (
        <Button variant="ghost" onClick={() => setConfirmCancel(true)}>
          {beforeArrival ? "Release this ride" : "Passenger did not show up"}
        </Button>
      )}
      <Dialog open={confirmCancel} onClose={() => setConfirmCancel(false)}
        title={beforeArrival ? "Release this ride?" : "Cancel for a no-show?"}>
        <p className="text-sm text-fg-muted">
          {beforeArrival
            ? "The ride goes back to matching and another driver is asked."
            : "Only after you have waited 5 minutes at the pickup. The ride is cancelled."}
        </p>
        <div className="mt-4 flex justify-end gap-2">
          <Button variant="secondary" onClick={() => setConfirmCancel(false)}>Keep ride</Button>
          <Button variant="danger" loading={cancel.isPending} onClick={() => cancel.mutate()}>Confirm</Button>
        </div>
      </Dialog>
    </div>
  );
}
