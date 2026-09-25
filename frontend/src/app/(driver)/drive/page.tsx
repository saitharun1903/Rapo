"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { CheckCircle2, Crosshair, LocateFixed, Power } from "lucide-react";
import { useState } from "react";
import { toast } from "sonner";
import { DriverTripPanel } from "@/components/driver/DriverTripPanel";
import { OfferCard } from "@/components/driver/OfferCard";
import { LazyMap } from "@/components/map/LazyMap";
import { RatingForm } from "@/components/ride/RatingForm";
import { Button, ButtonLink } from "@/components/ui/button";
import { Badge, Card, EmptyState, ErrorState, LoadingBlock } from "@/components/ui/surface";
import { api, unwrap } from "@/lib/api/client";
import { errorMessage, isApiError } from "@/lib/api/errors";
import type { DriverResponse, GeoPoint, RideOffer, RideResponse } from "@/lib/api/types";
import { useDriverLocation } from "@/lib/driver/useDriverLocation";
import { formatDistance, formatDuration, formatMoney } from "@/lib/format";
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

function TripSummary({ ride, onDone }: { ride: RideResponse; onDone: () => void }) {
  return (
    <div className="flex flex-col gap-4">
      <Card className="flex flex-col items-center gap-2 text-center">
        <CheckCircle2 className="size-10 text-success" aria-hidden />
        <h1 className="text-xl font-bold">{ride.status === "COMPLETED" ? "Trip complete" : "Ride ended"}</h1>
        {ride.actual && (
          <>
            <p className="text-3xl font-extrabold tabular-nums">{formatMoney(ride.actual.fare)}</p>
            <p className="text-sm text-fg-muted">
              {formatDistance(ride.actual.distanceMeters)} · {formatDuration(ride.actual.durationSeconds)} · collect {ride.paymentMethod === "CASH" ? "cash" : "nothing (card)"}
            </p>
          </>
        )}
      </Card>
      {ride.status === "COMPLETED" && <Card><RatingForm rideId={ride.id} subject="passenger" /></Card>}
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

  return (
    <div className="grid h-[calc(100dvh-4rem)] grid-rows-[1fr_minmax(0,1.2fr)] lg:grid-cols-[26rem_1fr] lg:grid-rows-1">
      <section aria-label="Driver console" className="order-2 flex flex-col gap-4 overflow-y-auto border-line bg-surface p-4 lg:order-1 lg:border-r">
        {summary ? (
          <TripSummary ride={summary} onDone={() => {
            dismissFinished();
            queryClient.setQueryData(queryKeys.activeRide, null);
            void queryClient.invalidateQueries({ queryKey: queryKeys.driverProfile });
          }} />
        ) : ride ? (
          <DriverTripPanel ride={ride} />
        ) : (
          <>
            <div className="flex items-center justify-between">
              <h1 className="text-xl font-bold tracking-tight">{online ? "You are online" : "You are offline"}</h1>
              <Badge tone={online ? "success" : "neutral"}>{online ? "Online" : "Offline"}</Badge>
            </div>
            {online ? (
              <Button variant="secondary" size="lg" loading={goOffline.isPending} onClick={() => goOffline.mutate()}>
                <Power className="size-5" aria-hidden /> Go offline
              </Button>
            ) : (
              <Button size="lg" disabled={!here} loading={goOnline.isPending} onClick={() => here && goOnline.mutate(here)}>
                <Power className="size-5" aria-hidden /> Go online
              </Button>
            )}
            {!online && !here && <p className="text-sm text-fg-muted">Waiting for your position before you can go online.</p>}
            {online && (
              <section aria-label="Ride offers" aria-live="polite" className="flex flex-col gap-3">
                {openOffers.length === 0
                  ? <p className="rounded-2xl bg-surface-2 p-4 text-center text-sm text-fg-muted">Waiting for ride requests nearby…</p>
                  : openOffers.map((offer) => (
                    <OfferCard key={offer.offerId} offer={offer} now={now}
                      onAccept={() => accept.mutate(offer.rideId)} onDecline={() => decline.mutate(offer.rideId)}
                      accepting={accept.isPending && accept.variables === offer.rideId}
                      declining={decline.isPending && decline.variables === offer.rideId} />
                  ))}
              </section>
            )}
          </>
        )}

        <Card className="mt-auto p-4">
          <p className="text-xs font-semibold uppercase tracking-wide text-fg-muted">Your position</p>
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
        </Card>
      </section>
      <div className="order-1 lg:order-2">
        <LazyMap label="Your position and trip" driver={here ? { point: here, headingDeg: location.position?.headingDeg } : null}
          pickup={ride && ride.status !== "IN_PROGRESS" && !isTerminal(ride.status) ? ride.pickup.point : null}
          dropoff={ride && !isTerminal(ride.status) ? ride.dropoff.point : null}
          route={route.data?.path} fitTo={[...(here ? [here] : []), ...(target ? [target.point] : [])]}
          onPick={placing ? onPick : undefined} />
      </div>
    </div>
  );
}
