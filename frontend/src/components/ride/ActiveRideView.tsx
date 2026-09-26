"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Radar, SignalLow } from "lucide-react";
import { useState } from "react";
import { toast } from "sonner";
import { LazyMap } from "@/components/map/LazyMap";
import { Button } from "@/components/ui/button";
import { Dialog } from "@/components/ui/dialog";
import { Textarea } from "@/components/ui/form";
import { api, unwrap } from "@/lib/api/client";
import { errorMessage } from "@/lib/api/errors";
import type { Eta, GeoPoint, RideResponse } from "@/lib/api/types";
import { formatDistance, formatDuration, humanize } from "@/lib/format";
import { queryKeys } from "@/lib/queryKeys";
import { AWAITING_DRIVER, DRIVER_EN_ROUTE, PASSENGER_CANCELLABLE, TRACKABLE, passengerHeadline } from "@/lib/ride/status";
import { useLiveDriver } from "@/lib/ride/useLiveDriver";
import { DriverCard, Plate } from "./DriverCard";
import { RideChat, RideChatButton, useRideChat } from "./RideChat";
import { RideScreen } from "./RideScreen";
import { TripSummary } from "./TripSummary";

const REASON_MAX = 255;
const NEARBY_REFRESH_MS = 15_000;
const NEARBY_MAX_RADIUS_METERS = 10_000;
/** The approach route is re-queried when the car has moved about this far (3 decimals of a degree, ~110 m). */
const APPROACH_ROUTE_DECIMALS = 3;
/** The camera refits to the car only when it moves about a kilometre, so it does not fight the rider's panning. */
const CAMERA_DECIMALS = 2;

function rounded(point: GeoPoint, decimals: number): GeoPoint {
  const factor = 10 ** decimals;
  return { lat: Math.round(point.lat * factor) / factor, lng: Math.round(point.lng * factor) / factor };
}

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
      <p className="mb-3 text-sm text-fg-muted">
        {ride.driver ? "Your driver will be released." : "The search for a driver stops."} You can book again right away.
      </p>
      <Textarea aria-label="Reason (optional)" placeholder="Reason (optional)" maxLength={REASON_MAX} value={reason}
        onChange={(event) => setReason(event.target.value)} />
      <div className="mt-4 flex justify-end gap-2">
        <Button variant="secondary" onClick={onClose}>Keep ride</Button>
        <Button variant="danger" loading={cancel.isPending} onClick={() => cancel.mutate()}>Cancel ride</Button>
      </div>
    </Dialog>
  );
}

/** The time that matters most right now, large, with what it is counting towards. */
function EtaHero({ eta, target }: { eta: Eta | null; target: "PICKUP" | "DROPOFF" }) {
  if (!eta || eta.target !== target) {
    return <p className="text-sm text-fg-muted">Working out the arrival time…</p>;
  }
  return (
    <p className="flex items-baseline gap-2">
      <span className="num text-[2.25rem] font-semibold leading-none tracking-[-0.03em] text-fg">{formatDuration(eta.seconds)}</span>
      <span className="text-sm text-fg-muted">
        to {target === "PICKUP" ? "your pickup" : "your destination"} · {formatDistance(eta.distanceMeters)}
        {eta.source === "APPROXIMATE" && " (approximate)"}
      </span>
    </p>
  );
}

function TripProgress({ ride, eta }: { ride: RideResponse; eta: Eta | null }) {
  if (!eta || eta.target !== "DROPOFF" || ride.estimate.distanceMeters <= 0) {
    return null;
  }
  const done = Math.min(1, Math.max(0, 1 - eta.distanceMeters / ride.estimate.distanceMeters));
  return (
    <div className="mt-3">
      <div className="h-1.5 overflow-hidden rounded-full bg-surface-2" role="progressbar" aria-label="Trip progress"
        aria-valuemin={0} aria-valuemax={100} aria-valuenow={Math.round(done * 100)}>
        <div className="h-full rounded-full bg-brand transition-[width] duration-700 ease-out-soft" style={{ width: `${done * 100}%` }} />
      </div>
      <p className="mt-1.5 text-xs text-fg-muted">{formatDistance(eta.distanceMeters)} to go of {formatDistance(ride.estimate.distanceMeters)}</p>
    </div>
  );
}

function Searching({ ride, nearbyCount }: { ride: RideResponse; nearbyCount: number | null }) {
  return (
    <div className="flex items-start gap-3 rounded-card bg-brand-soft p-4">
      <span className="relative mt-0.5 flex size-8 shrink-0 items-center justify-center rounded-full bg-brand text-brand-fg">
        <span className="absolute inset-0 rounded-full bg-brand/40 animate-search motion-reduce:hidden" aria-hidden />
        <Radar className="relative size-4" aria-hidden />
      </span>
      <div className="text-sm">
        <p className="font-medium text-fg">
          {ride.matching.round > 0
            ? `Asking drivers within ${formatDistance(ride.matching.radiusMeters)}`
            : "Looking for drivers near your pickup"}
        </p>
        <p className="text-fg-muted">
          {ride.matching.round > 1 ? `Search widened, round ${ride.matching.round}. ` : ""}
          {nearbyCount === null ? "" : nearbyCount === 0 ? "No cars are online nearby yet." : `${nearbyCount} car${nearbyCount === 1 ? "" : "s"} online nearby.`}
        </p>
      </div>
    </div>
  );
}

export function ActiveRideView({ ride }: { ride: RideResponse }) {
  const live = useLiveDriver(ride);
  const [cancelling, setCancelling] = useState(false);
  const searching = AWAITING_DRIVER.includes(ride.status);
  const approaching = DRIVER_EN_ROUTE.includes(ride.status);
  const inTrip = ride.status === "IN_PROGRESS";
  const engaged = TRACKABLE.includes(ride.status);
  const chat = useRideChat(ride.id, "PASSENGER", engaged);

  const nearby = useQuery({
    queryKey: queryKeys.nearby(ride.pickup.point.lat, ride.pickup.point.lng, ride.matching.radiusMeters),
    queryFn: () => unwrap(api.GET("/api/drivers/nearby", {
      params: { query: { lat: ride.pickup.point.lat, lng: ride.pickup.point.lng,
        radiusMeters: Math.min(NEARBY_MAX_RADIUS_METERS, Math.max(1_000, ride.matching.radiusMeters)) } },
    })),
    enabled: searching,
    refetchInterval: NEARBY_REFRESH_MS,
  });

  const tripRoute = useQuery({
    queryKey: queryKeys.route(`${ride.pickup.point.lat},${ride.pickup.point.lng}>${ride.dropoff.point.lat},${ride.dropoff.point.lng}`),
    queryFn: () => unwrap(api.GET("/api/geo/route", {
      params: { query: { fromLat: ride.pickup.point.lat, fromLng: ride.pickup.point.lng, toLat: ride.dropoff.point.lat, toLng: ride.dropoff.point.lng } },
    })),
    enabled: searching || inTrip,
    staleTime: Infinity,
  });

  const approachFrom = approaching && ride.status !== "DRIVER_ARRIVED" && live.point ? rounded(live.point, APPROACH_ROUTE_DECIMALS) : null;
  const approachRoute = useQuery({
    queryKey: queryKeys.route(approachFrom ? `${approachFrom.lat},${approachFrom.lng}>${ride.pickup.point.lat},${ride.pickup.point.lng}` : "none"),
    queryFn: () => unwrap(api.GET("/api/geo/route", {
      params: { query: { fromLat: approachFrom!.lat, fromLng: approachFrom!.lng, toLat: ride.pickup.point.lat, toLng: ride.pickup.point.lng } },
    })),
    enabled: approachFrom !== null,
    staleTime: 30_000,
    placeholderData: (previous) => previous,
  });

  const cameraCar = live.point ? rounded(live.point, CAMERA_DECIMALS) : null;
  const fitTo: GeoPoint[] = inTrip
    ? [ride.pickup.point, ride.dropoff.point, ...(cameraCar ? [cameraCar] : [])]
    : approaching
      ? [ride.pickup.point, ...(cameraCar ? [cameraCar] : [])]
      : [ride.pickup.point, ride.dropoff.point];

  const route = inTrip || searching ? tripRoute.data?.path : approachRoute.data?.path;
  const vehicle = ride.driver?.vehicle;
  const driverName = ride.driver?.fullName.split(" ")[0] ?? "your driver";

  return (
    <RideScreen label="Your ride" peek={searching ? 300 : 360} map={(padding) => (
      <LazyMap label="Live map of your ride" pickup={inTrip ? null : ride.pickup.point} dropoff={ride.dropoff.point}
        driver={live.point ? { point: live.point, headingDeg: live.headingDeg } : null}
        nearby={searching ? nearby.data?.map((driver) => driver.position) : undefined}
        route={route} fitTo={fitTo} padding={padding} searching={searching} />
    )}>
      <div key={ride.status} className="flex flex-col gap-5 animate-fade">
        <header>
          <p className="eyebrow">{vehicle ? humanize(vehicle.category) : humanize(ride.vehicleCategory)} · {humanize(ride.paymentMethod)}</p>
          <h1 className="mt-1 text-[1.35rem] font-semibold leading-tight tracking-[-0.02em]" aria-live="polite">
            {passengerHeadline(ride.status)}
          </h1>
          {inTrip && <p className="mt-0.5 truncate text-sm text-fg-muted">To {ride.dropoff.address}</p>}
          {ride.status === "DRIVER_ARRIVED" && vehicle && (
            <p className="mt-0.5 text-sm text-fg-muted">Look for the {vehicle.color.toLowerCase()} {vehicle.make} {vehicle.model}.</p>
          )}
        </header>

        {searching && <Searching ride={ride} nearbyCount={nearby.data ? nearby.data.length : null} />}

        {(approaching || inTrip) && ride.status !== "DRIVER_ARRIVED" && (
          <div>
            <EtaHero eta={live.eta} target={inTrip ? "DROPOFF" : "PICKUP"} />
            {inTrip && <TripProgress ride={ride} eta={live.eta} />}
          </div>
        )}

        {ride.status === "DRIVER_ARRIVED" && vehicle && (
          <div className="flex items-center justify-between rounded-card bg-surface-2 p-4">
            <span className="text-sm text-fg-muted">Match the plate</span>
            <Plate number={vehicle.plateNumber} size="lg" />
          </div>
        )}

        {live.signalLost && (
          <p role="status" className="flex items-center gap-2 rounded-control bg-warning-soft px-3 py-2 text-sm text-warning">
            <SignalLow className="size-4" aria-hidden /> Location signal lost. Showing the last known position.
          </p>
        )}

        {ride.driver && (
          <div className="flex flex-col gap-3 animate-rise">
            <DriverCard driver={ride.driver} />
            {engaged && (
              <div className="flex gap-2">
                <RideChatButton chat={chat} label={`Message ${driverName}`} />
              </div>
            )}
          </div>
        )}

        <TripSummary pickup={ride.pickup} dropoff={ride.dropoff} fare={{ label: "Estimated fare", amount: ride.estimate.fare }} />

        {PASSENGER_CANCELLABLE.includes(ride.status) && (
          <Button variant="ghost" className="w-full text-danger hover:bg-danger-soft" onClick={() => setCancelling(true)}>Cancel ride</Button>
        )}
      </div>
      <CancelDialog ride={ride} open={cancelling} onClose={() => setCancelling(false)} />
      {ride.driver && (
        <RideChat rideId={ride.id} me="PASSENGER" otherName={driverName} canSend={engaged} chat={chat} />
      )}
    </RideScreen>
  );
}

