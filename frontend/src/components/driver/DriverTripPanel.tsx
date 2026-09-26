"use client";

import { useMutation, useQueryClient } from "@tanstack/react-query";
import { ExternalLink } from "lucide-react";
import { useState } from "react";
import { toast } from "sonner";
import { initialsOf } from "@/components/ride/DriverCard";
import { RideChat, RideChatButton, useRideChat } from "@/components/ride/RideChat";
import { TripSummary } from "@/components/ride/TripSummary";
import { Button } from "@/components/ui/button";
import { Dialog } from "@/components/ui/dialog";
import { api, unwrap } from "@/lib/api/client";
import { errorMessage, isApiError } from "@/lib/api/errors";
import type { RideResponse } from "@/lib/api/types";
import { formatMoney, humanize } from "@/lib/format";
import { queryKeys } from "@/lib/queryKeys";
import { currentTarget, driverHeadline, nextDriverAction, type DriverAction } from "@/lib/ride/status";

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

/** Turn-by-turn directions are left to the driver's navigation app; this opens it at the next stop. */
function directionsUrl(point: { lat: number; lng: number }): string {
  return `https://www.google.com/maps/dir/?api=1&destination=${point.lat},${point.lng}&travelmode=driving`;
}

export function DriverTripPanel({ ride }: { ride: RideResponse }) {
  const queryClient = useQueryClient();
  const [confirmCancel, setConfirmCancel] = useState(false);
  const action = nextDriverAction(ride.status);
  const beforeArrival = ride.status === "DRIVER_ASSIGNED" || ride.status === "DRIVER_ARRIVING";
  const target = currentTarget(ride);
  const passengerName = ride.passenger?.fullName ?? "Passenger";
  const firstName = passengerName.split(" ")[0];
  const chat = useRideChat(ride.id, "DRIVER", true);

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
    <div key={ride.status} className="flex flex-col gap-5 animate-fade">
      <header>
        <p className="eyebrow">{humanize(ride.vehicleCategory)} · {ride.paymentMethod === "CASH" ? "Collect cash" : "Card, nothing to collect"}</p>
        <h1 className="mt-1 text-[1.35rem] font-semibold leading-tight tracking-[-0.02em]" aria-live="polite">{driverHeadline(ride.status)}</h1>
      </header>

      <div className="flex items-center gap-3.5">
        <div aria-hidden className="flex size-12 shrink-0 items-center justify-center rounded-control bg-surface-2 text-sm font-semibold tracking-wide text-fg">
          {initialsOf(passengerName)}
        </div>
        <div className="min-w-0 flex-1">
          <p className="truncate font-medium text-fg">{passengerName}</p>
          <p className="num text-sm text-fg-muted">Estimated {formatMoney(ride.estimate.fare)}</p>
        </div>
      </div>

      <div className="rounded-card bg-surface-2 p-4">
        <p className="eyebrow">{ride.status === "IN_PROGRESS" ? "Drop-off" : "Pickup"}</p>
        <p className="mt-1 text-sm font-medium text-fg">{target.address}</p>
        <a href={directionsUrl(target.point)} target="_blank" rel="noopener noreferrer"
          className="mt-2 inline-flex items-center gap-1.5 text-sm font-medium text-brand-strong hover:underline">
          Open directions <ExternalLink className="size-3.5" aria-hidden />
        </a>
      </div>

      <div className="flex gap-2">
        <RideChatButton chat={chat} label={`Message ${firstName}`} />
      </div>

      {action && (
        <div>
          <Button size="lg" className="w-full" loading={advance.isPending} onClick={() => advance.mutate(action)}>{action.label}</Button>
          <p className="mt-1.5 text-center text-xs text-fg-muted">{action.hint}</p>
        </div>
      )}

      <TripSummary pickup={ride.pickup} dropoff={ride.dropoff} />

      {ride.status !== "IN_PROGRESS" && (
        <Button variant="ghost" className="text-fg-muted" onClick={() => setConfirmCancel(true)}>
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
      <RideChat rideId={ride.id} me="DRIVER" otherName={firstName} canSend chat={chat} />
    </div>
  );
}
