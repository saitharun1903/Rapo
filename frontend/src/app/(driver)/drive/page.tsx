"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import clsx from "clsx";
import { CheckCircle2, Crosshair, LocateFixed, Power } from "lucide-react";
import { useState } from "react";
import { toast } from "sonner";
import { DriverTripPanel } from "@/components/driver/DriverTripPanel";
import { OfferCard } from "@/components/driver/OfferCard";
import { LazyMap } from "@/components/map/LazyMap";
import { RatingForm } from "@/components/ride/RatingForm";
import { RideScreen } from "@/components/ride/RideScreen";
import { Button, ButtonLink } from "@/components/ui/button";
import { EmptyState, ErrorState, LoadingBlock } from "@/components/ui/surface";
import { api, unwrap } from "@/lib/api/client";
import { errorMessage, isApiError } from "@/lib/api/errors";
import type { DriverResponse, GeoPoint, RideOffer, RideResponse } from "@/lib/api/types";
import { useDriverLocation } from "@/lib/driver/useDriverLocation";
import { formatDistance, formatDuration, formatMoney, humanize } from "@/lib/format";
import { queryKeys } from "@/lib/queryKeys";
import { REALTIME_SNAPSHOT, useRealtimeSubscription } from "@/lib/realtime/RealtimeProvider";
import { Destinations } from "@/lib/realtime/types";
import { currentTarget, isTerminal } from "@/lib/ride/status";
import { useActiveRide } from "@/lib/ride/useActiveRide";
import { useNow } from "@/lib/useNow";

/** Round the driver's position for the route query, so it is not re-fetched for every few metres moved. */
const ROUTE_PRECISION = 3;

const PRESENCE_REASONS: Record<string, string> = {
  LOCATION_TIMEOUT: "You were taken offline because no location arrived for 2 minutes.",
  ACCOUNT_SUSPENDED: "Your account was suspended, so you are offline.",
};

function VerificationNotice({ driver }: { driver: DriverResponse }) {
  const messages = {
    PENDING: { title: "Your profile is being reviewed", text: "An admin checks your licence and vehicle. You can go online once you are verified." },
    REJECTED: { title: "Your application was not approved", text: driver.rejectionReason ?? "Contact support for details." },
    SUSPENDED: { title: "Your account is suspended", text: driver.rejectionReason ?? "Contact support to have it reviewed." },
    VERIFIED: { title: "", text: "" },
  } as const;
  const message = messages[driver.verificationStatus];
  return (
    <div className="mx-auto max-w-lg px-4 py-10">
      <EmptyState title={message.title} action={<ButtonLink href="/drive/onboarding" variant="secondary">View profile</ButtonLink>}>
        {message.text}
      </EmptyState>
    </div>
  );
}

function TripDone({ ride, onDone }: { ride: RideResponse; onDone: () => void }) {
  const completed = ride.status === "COMPLETED";
  return (
    <div className="flex flex-col gap-5 animate-rise">
      <header className="flex items-start gap-3">
        <CheckCircle2 className={clsx("mt-1 size-6 shrink-0", completed ? "text-success" : "text-fg-muted")} aria-hidden />
        <div>
          <h1 className="text-[1.35rem] font-semibold leading-tight tracking-[-0.02em]">{completed ? "Trip complete" : "Ride ended"}</h1>
          {ride.cancellation && (
            <p className="mt-0.5 text-sm text-fg-muted">
              Cancelled by {humanize(ride.cancellation.cancelledBy).toLowerCase()}{ride.cancellation.reason ? `: ${ride.cancellation.reason}` : "."}
            </p>
          )}
        </div>
      </header>
      {ride.actual && (
        <div className="rounded-card bg-surface-2 p-4">
          <p className="num text-[2.25rem] font-semibold leading-none tracking-[-0.03em]">{formatMoney(ride.actual.fare)}</p>
          <p className="mt-2 text-sm font-medium text-fg">{ride.paymentMethod === "CASH" ? "Collect this in cash." : "Paid by card: nothing to collect."}</p>
          <p className="num mt-1 text-xs text-fg-muted">
            {formatDistance(ride.actual.distanceMeters)} · {formatDuration(ride.actual.durationSeconds)}
          </p>
        </div>
      )}
      {completed && <RatingForm rideId={ride.id} subject="passenger" />}
      <Button onClick={onDone}>Back to offers</Button>
    </div>
  );
}

export default function DrivePage() {
  const queryClient = useQueryClient();
  const now = useNow();
  const profile = useQuery({
    queryKey: queryKeys.driverProfile,
    queryFn: () => unwrap(api.GET("/api/drivers/me")),
    meta: REALTIME_SNAPSHOT,
  });
  const { active, finished, dismissFinished } = useActiveRide();
  const online = profile.data?.availability === "AVAILABLE" || profile.data?.availability === "ON_TRIP";
  const location = useDriverLocation(online);
  const [placing, setPlacing] = useState(false);

  const offers = useQuery({
    queryKey: queryKeys.offers,
    queryFn: () => unwrap(api.GET("/api/drivers/me/offers")),
    enabled: profile.data?.availability === "AVAILABLE",
    meta: REALTIME_SNAPSHOT,
  });
  const removeOffer = (rideId: string) =>
    queryClient.setQueryData(queryKeys.offers, (current: RideOffer[] | undefined) => (current ?? []).filter((offer) => offer.rideId !== rideId));

  useRealtimeSubscription(Destinations.rideOffers, (message) => {
    if (message.type === "OFFER") {
      queryClient.setQueryData(queryKeys.offers, (current: RideOffer[] | undefined) =>
        [message.offer, ...(current ?? []).filter((offer) => offer.rideId !== message.rideId)]);
    } else {
      removeOffer(message.rideId);
    }
  });
  useRealtimeSubscription(Destinations.presence, (message) => {
    queryClient.setQueryData(queryKeys.driverProfile, (current: DriverResponse | undefined) =>
      current ? { ...current, availability: message.availability } : current);
    toast.warning(PRESENCE_REASONS[message.reason] ?? "You are offline.");
  });

  const setAvailability = (driver: DriverResponse) => queryClient.setQueryData(queryKeys.driverProfile, driver);
  const goOnline = useMutation({
    mutationFn: (point: GeoPoint) => unwrap(api.POST("/api/drivers/online", { body: { location: point } })),
    onSuccess: setAvailability,
    onError: (error) => toast.error(errorMessage(error)),
  });
  const goOffline = useMutation({
    mutationFn: () => unwrap(api.POST("/api/drivers/offline")),
    onSuccess: setAvailability,
    onError: (error) => toast.error(errorMessage(error)),
  });
  const accept = useMutation({
    mutationFn: (rideId: string) => unwrap(api.POST("/api/rides/{rideId}/accept", { params: { path: { rideId } } })),
    onSuccess: (ride) => {
      queryClient.setQueryData(queryKeys.offers, []);
      queryClient.setQueryData(queryKeys.activeRide, ride);
      void queryClient.invalidateQueries({ queryKey: queryKeys.driverProfile });
    },
    onError: (error, rideId) => {
      toast.error(isApiError(error, "RIDE_ALREADY_ASSIGNED") ? "Another driver took this ride." : errorMessage(error));
      removeOffer(rideId);
    },
  });
  const decline = useMutation({
    mutationFn: (rideId: string) => unwrap(api.POST("/api/rides/{rideId}/reject", { params: { path: { rideId } } })),
    onSuccess: (_, rideId) => removeOffer(rideId),
    onError: (error, rideId) => {
      // An offer that expired or went to someone else is gone either way.
      toast.error(errorMessage(error));
      removeOffer(rideId);
    },
  });

  const ride = active.data ?? null;
  const target = ride && !isTerminal(ride.status) ? currentTarget(ride) : null;
  const here = location.position?.point ?? null;
  const routeKey = here && target
    ? `${here.lat.toFixed(ROUTE_PRECISION)},${here.lng.toFixed(ROUTE_PRECISION)}>${target.point.lat},${target.point.lng}` : null;
  const route = useQuery({
    queryKey: queryKeys.route(routeKey ?? "none"),
    queryFn: () => unwrap(api.GET("/api/geo/route", {
      params: { query: { fromLat: here!.lat, fromLng: here!.lng, toLat: target!.point.lat, toLng: target!.point.lng } },
    })),
    enabled: routeKey !== null,
  });

  if (profile.isPending) {
    return <div className="mx-auto max-w-lg p-6"><LoadingBlock label="Loading your driver profile" /></div>;
  }
  if (profile.isError) {
    if (isApiError(profile.error, "DRIVER_PROFILE_NOT_FOUND")) {
      return (
        <div className="mx-auto max-w-lg px-4 py-10">
          <EmptyState title="Finish setting up" action={<ButtonLink href="/drive/onboarding">Add licence and vehicle</ButtonLink>}>
            Add your driving licence and vehicle. An admin verifies them before your first ride.
          </EmptyState>
        </div>
      );
    }
    return <div className="mx-auto max-w-lg p-6"><ErrorState error={profile.error} onRetry={() => void profile.refetch()} /></div>;
  }
  const driver = profile.data;
  if (driver.verificationStatus !== "VERIFIED") {
    return <VerificationNotice driver={driver} />;
  }

  const openOffers = (offers.data ?? []).filter((offer) => Date.parse(offer.expiresAt) > now);
  const summary = ride && isTerminal(ride.status) ? ride : finished;
  const onPick = (point: GeoPoint) => {
    location.placeManually(point);
    setPlacing(false);
  };

  const fitTo = [...(here ? [here] : []), ...(target ? [target.point] : [])];
  return (
    <RideScreen label="Driver console" peek={ride || summary ? 380 : 300} map={(padding) => (
      <LazyMap label="Your position and trip" driver={here ? { point: here, headingDeg: location.position?.headingDeg } : null}
        pickup={ride && ride.status !== "IN_PROGRESS" && !isTerminal(ride.status) ? ride.pickup.point : null}
        dropoff={ride && !isTerminal(ride.status) ? ride.dropoff.point : null}
        route={route.data?.path} fitTo={fitTo} cameraKey={`${ride?.id ?? "idle"}:${ride?.status ?? ""}:${here !== null}`}
        follow={here} padding={padding}
        onPick={placing ? onPick : undefined} />
    )}>
      <div className="flex min-h-full flex-col gap-5">
        {summary ? (
          <TripDone ride={summary} onDone={() => {
            dismissFinished();
            queryClient.setQueryData(queryKeys.activeRide, null);
            void queryClient.invalidateQueries({ queryKey: queryKeys.driverProfile });
          }} />
        ) : ride ? (
          <DriverTripPanel ride={ride} />
        ) : (
          <>
            <header className="flex items-center justify-between gap-3">
              <div>
                <p className="eyebrow">{driver.vehicle ? `${driver.vehicle.color} ${driver.vehicle.make} ${driver.vehicle.model}` : "Driver"}</p>
                <h1 className="mt-1 text-[1.35rem] font-semibold tracking-[-0.02em]">{online ? "You are online" : "You are offline"}</h1>
              </div>
              <span className={clsx("flex items-center gap-1.5 rounded-full px-2.5 py-1 text-xs font-medium",
                online ? "bg-success-soft text-success" : "bg-surface-2 text-fg-muted")}>
                <span aria-hidden className={clsx("size-1.5 rounded-full", online ? "bg-success" : "bg-line-strong")} />
                {online ? "Online" : "Offline"}
              </span>
            </header>
            {online ? (
              <Button variant="secondary" size="lg" loading={goOffline.isPending} onClick={() => goOffline.mutate()}>
                <Power className="size-5" aria-hidden /> Go offline
              </Button>
            ) : (
              <Button variant="signal" size="lg" disabled={!here} loading={goOnline.isPending} onClick={() => here && goOnline.mutate(here)}>
                <Power className="size-5" aria-hidden /> Go online
              </Button>
            )}
            {!online && !here && <p className="text-sm text-fg-muted">Waiting for your position before you can go online.</p>}
            {online && (
              <section aria-label="Ride offers" aria-live="polite" className="flex flex-col gap-3">
                {openOffers.length === 0 ? (
                  <div className="flex items-center gap-3 rounded-card border border-dashed border-line-strong p-4 text-sm text-fg-muted">
                    <span aria-hidden className="relative flex size-2.5">
                      <span className="absolute inset-0 animate-ping rounded-full bg-success opacity-50 motion-reduce:hidden" />
                      <span className="relative size-2.5 rounded-full bg-success" />
                    </span>
                    Waiting for ride requests nearby…
                  </div>
                ) : openOffers.map((offer) => (
                  <OfferCard key={offer.offerId} offer={offer} now={now}
                    onAccept={() => accept.mutate(offer.rideId)} onDecline={() => decline.mutate(offer.rideId)}
                    accepting={accept.isPending && accept.variables === offer.rideId}
                    declining={decline.isPending && decline.variables === offer.rideId} />
                ))}
              </section>
            )}
          </>
        )}

        <div className="mt-auto border-t border-line pt-4">
          <p className="eyebrow">Your position</p>
          <p className="mt-1 text-sm text-fg">
            {location.mode === "manual" ? "Placed on the map (testing without GPS)" : here ? "From this device's GPS" : "Locating…"}
          </p>
          {location.gpsError && <p role="alert" className="mt-1 text-sm text-warning">{location.gpsError}</p>}
          {location.reportError && <p role="alert" className="mt-1 text-sm text-danger">Location not sent: {location.reportError}</p>}
          <div className="mt-2 flex flex-wrap gap-2">
            <Button size="sm" variant={placing ? "primary" : "secondary"} onClick={() => setPlacing(!placing)} aria-pressed={placing}>
              <Crosshair className="size-4" aria-hidden /> {placing ? "Click the map…" : "Place me on the map"}
            </Button>
            {location.mode === "manual" && (
              <Button size="sm" variant="ghost" onClick={location.useGps}><LocateFixed className="size-4" aria-hidden /> Use GPS</Button>
            )}
          </div>
        </div>
      </div>
    </RideScreen>
  );
}
