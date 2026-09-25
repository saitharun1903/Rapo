"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import clsx from "clsx";
import { Car, Clock, SignalLow, Star } from "lucide-react";
import { useState } from "react";
import { toast } from "sonner";
import { LazyMap } from "@/components/map/LazyMap";
import { Button } from "@/components/ui/button";
import { Dialog } from "@/components/ui/dialog";
import { Textarea } from "@/components/ui/form";
import { Card } from "@/components/ui/surface";
import { api, unwrap } from "@/lib/api/client";
import { errorMessage } from "@/lib/api/errors";
import type { GeoPoint, RideResponse } from "@/lib/api/types";
import { formatDistance, formatDuration, formatMoney, formatRating, humanize } from "@/lib/format";
import { queryKeys } from "@/lib/queryKeys";
import { AWAITING_DRIVER, PASSENGER_CANCELLABLE, passengerHeadline } from "@/lib/ride/status";
import { useLiveDriver } from "@/lib/ride/useLiveDriver";
import { StatusBadge } from "./StatusBadge";

const STEPS = [
  { label: "Matched", statuses: ["DRIVER_ASSIGNED", "DRIVER_ARRIVING", "DRIVER_ARRIVED", "IN_PROGRESS"] },
  { label: "Arrived", statuses: ["DRIVER_ARRIVED", "IN_PROGRESS"] },
  { label: "On trip", statuses: ["IN_PROGRESS"] },
] as const;
const REASON_MAX = 255;

function CancelDialog({ ride, open, onClose }: { ride: RideResponse; open: boolean; onClose: () => void }) {
  const queryClient = useQueryClient();
  const [reason, setReason] = useState("");
  const cancel = useMutation({
    mutationFn: () => unwrap(api.POST("/api/rides/{rideId}/cancel", {
      params: { path: { rideId: ride.id } },
      body: { reason: reason.trim() === "" ? null : reason.trim() },
    })),
    onSuccess: (updated) => {
      queryClient.setQueryData(queryKeys.ride(updated.id), updated);
      void queryClient.invalidateQueries({ queryKey: queryKeys.activeRide });
      onClose();
    },
    onError: (error) => toast.error(errorMessage(error)),
  });
  return (
    <Dialog open={open} onClose={onClose} title="Cancel this ride?">
      <p className="mb-3 text-sm text-fg-muted">Your driver will be released. You can book again right away.</p>
      <Textarea aria-label="Reason (optional)" placeholder="Reason (optional)" maxLength={REASON_MAX} value={reason}
        onChange={(event) => setReason(event.target.value)} />
      <div className="mt-4 flex justify-end gap-2">
        <Button variant="secondary" onClick={onClose}>Keep ride</Button>
        <Button variant="danger" loading={cancel.isPending} onClick={() => cancel.mutate()}>Cancel ride</Button>
      </div>
    </Dialog>
  );
}

export function ActiveRideView({ ride }: { ride: RideResponse }) {
  const live = useLiveDriver(ride);
  const [cancelling, setCancelling] = useState(false);
  const inTrip = ride.status === "IN_PROGRESS";
  const route = useQuery({
    queryKey: queryKeys.route(`${ride.pickup.point.lat},${ride.pickup.point.lng}>${ride.dropoff.point.lat},${ride.dropoff.point.lng}`),
    queryFn: () => unwrap(api.GET("/api/geo/route", {
      params: { query: { fromLat: ride.pickup.point.lat, fromLng: ride.pickup.point.lng, toLat: ride.dropoff.point.lat, toLng: ride.dropoff.point.lng } },
    })),
    staleTime: Infinity,
  });

  const fitTo: GeoPoint[] = [inTrip ? ride.dropoff.point : ride.pickup.point, ...(live.point ? [live.point] : [])];
  const searching = AWAITING_DRIVER.includes(ride.status);

  return (
    <div className="grid h-[calc(100dvh-4rem)] grid-rows-[1fr_auto] lg:grid-cols-[26rem_1fr] lg:grid-rows-1">
      <section aria-label="Your ride" className="order-2 overflow-y-auto border-line bg-surface p-4 lg:order-1 lg:border-r">
        <div className="flex items-center justify-between gap-2">
          <h1 className="text-xl font-bold tracking-tight" aria-live="polite">{passengerHeadline(ride.status)}</h1>
          <StatusBadge status={ride.status} />
        </div>

        {searching && (
          <div className="mt-4 flex items-center gap-3 rounded-2xl bg-brand-soft p-4 text-sm text-brand">
            <span className="relative flex size-3"><span className="absolute inline-flex size-full animate-ping rounded-full bg-brand opacity-60" /><span className="relative inline-flex size-3 rounded-full bg-brand" /></span>
            {ride.matching.round > 0
              ? `Asking drivers within ${formatDistance(ride.matching.radiusMeters)} (round ${ride.matching.round})`
              : "Looking for drivers near your pickup"}
          </div>
        )}

        {!searching && (
          <ol className="mt-4 grid grid-cols-3 gap-2" aria-label="Progress">
            {STEPS.map((step) => {
              const done = (step.statuses as readonly string[]).includes(ride.status);
              return (
                <li key={step.label} className="flex flex-col gap-1">
                  <span className={clsx("h-1.5 rounded-full", done ? "bg-brand" : "bg-surface-2")} />
                  <span className={clsx("text-xs font-medium", done ? "text-fg" : "text-fg-muted")}>{step.label}</span>
                </li>
              );
            })}
          </ol>
        )}

        {live.eta && (
          <p className="mt-4 flex items-center gap-2 text-sm font-semibold text-fg">
            <Clock className="size-4 text-brand" aria-hidden />
            {live.eta.target === "PICKUP" ? "Arrives in" : "At destination in"} {formatDuration(live.eta.seconds)}
            <span className="font-normal text-fg-muted">· {formatDistance(live.eta.distanceMeters)}</span>
          </p>
        )}
        {live.signalLost && (
          <p role="status" className="mt-3 flex items-center gap-2 rounded-xl bg-warning-soft px-3 py-2 text-sm text-warning">
            <SignalLow className="size-4" aria-hidden /> Location signal lost. Showing the last known position.
          </p>
        )}

        {ride.driver && (
          <Card className="mt-4 flex items-center gap-4 p-4">
            <div className="flex size-12 items-center justify-center rounded-full bg-fg text-bg"><Car className="size-6" aria-hidden /></div>
            <div className="min-w-0 flex-1">
              <p className="font-semibold text-fg">{ride.driver.fullName}</p>
              <p className="flex items-center gap-1 text-xs text-fg-muted">
                <Star className="size-3.5 fill-warning text-warning" aria-hidden /> {formatRating(ride.driver.ratingAvg)}
                {ride.driver.ratingCount > 0 && ` (${ride.driver.ratingCount})`}
              </p>
              {ride.driver.vehicle && (
                <p className="text-sm text-fg-muted">{ride.driver.vehicle.color} {ride.driver.vehicle.make} {ride.driver.vehicle.model}</p>
              )}
            </div>
            {ride.driver.vehicle && (
              <span className="rounded-lg border-2 border-fg px-2 py-1 font-mono text-sm font-bold tracking-wider">{ride.driver.vehicle.plateNumber}</span>
            )}
          </Card>
        )}

        <dl className="mt-4 grid grid-cols-[auto_1fr] gap-x-4 gap-y-2 text-sm">
          <dt className="text-fg-muted">From</dt><dd className="text-fg">{ride.pickup.address}</dd>
          <dt className="text-fg-muted">To</dt><dd className="text-fg">{ride.dropoff.address}</dd>
          <dt className="text-fg-muted">Ride</dt><dd className="text-fg">{humanize(ride.vehicleCategory)} · {humanize(ride.paymentMethod)}</dd>
          <dt className="text-fg-muted">Estimate</dt><dd className="font-semibold text-fg">{formatMoney(ride.estimate.fare)}</dd>
        </dl>

        {PASSENGER_CANCELLABLE.includes(ride.status) && (
          <Button variant="secondary" className="mt-6 w-full" onClick={() => setCancelling(true)}>Cancel ride</Button>
        )}
        <CancelDialog ride={ride} open={cancelling} onClose={() => setCancelling(false)} />
      </section>
      <div className="order-1 lg:order-2">
        <LazyMap label="Live map of your ride" pickup={inTrip ? null : ride.pickup.point} dropoff={ride.dropoff.point}
          driver={live.point ? { point: live.point, headingDeg: live.headingDeg } : null}
          route={inTrip ? route.data?.path : null} fitTo={fitTo} />
      </div>
    </div>
  );
}
