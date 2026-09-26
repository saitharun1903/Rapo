"use client";

import { useQuery } from "@tanstack/react-query";
import { ArrowLeft } from "lucide-react";
import Link from "next/link";
import { useParams } from "next/navigation";
import { FareBreakdownTable } from "@/components/ride/FareBreakdownTable";
import { RatingForm } from "@/components/ride/RatingForm";
import { RideTimeline } from "@/components/ride/RideTimeline";
import { StatusBadge } from "@/components/ride/StatusBadge";
import { TripInsightsPanel } from "@/components/trips/TripInsightsPanel";
import { TripQuestions } from "@/components/trips/TripQuestions";
import { Card, CardTitle, ErrorState, LoadingBlock } from "@/components/ui/surface";
import { api, unwrap } from "@/lib/api/client";
import { isApiError } from "@/lib/api/errors";
import { formatDateTime, formatMoney, humanize } from "@/lib/format";
import { queryKeys } from "@/lib/queryKeys";

export default function TripDetailPage() {
  const { rideId } = useParams<{ rideId: string }>();
  const ride = useQuery({
    queryKey: queryKeys.ride(rideId),
    queryFn: () => unwrap(api.GET("/api/rides/{rideId}", { params: { path: { rideId } } })),
  });
  const timeline = useQuery({
    queryKey: queryKeys.rideTimeline(rideId),
    queryFn: () => unwrap(api.GET("/api/rides/{rideId}/timeline", { params: { path: { rideId } } })),
  });

  if (ride.isPending) {
    return <div className="mx-auto max-w-5xl p-6"><LoadingBlock label="Loading trip" /></div>;
  }
  if (ride.isError) {
    return (
      <div className="mx-auto max-w-5xl p-6">
        <ErrorState error={ride.error} title={isApiError(ride.error, "RIDE_NOT_FOUND") ? "Trip not found" : "Could not load this trip"}
          onRetry={isApiError(ride.error, "RIDE_NOT_FOUND") ? undefined : () => void ride.refetch()} />
      </div>
    );
  }
  const trip = ride.data;
  const completed = trip.status === "COMPLETED";

  return (
    <div className="mx-auto max-w-5xl px-4 py-8">
      <Link href="/trips" className="mb-4 inline-flex items-center gap-1 text-sm font-medium text-fg-muted hover:text-fg">
        <ArrowLeft className="size-4" aria-hidden /> All trips
      </Link>
      <div className="mb-6 flex flex-wrap items-start justify-between gap-4">
        <div>
          <div className="flex items-center gap-2"><StatusBadge status={trip.status} /><span className="text-sm text-fg-muted">{formatDateTime(trip.timestamps.requestedAt)}</span></div>
          <h1 className="mt-2 text-xl font-semibold tracking-[-0.02em]">{trip.pickup.address}</h1>
          <p className="text-fg-muted">→ {trip.dropoff.address}</p>
        </div>
        <div className="text-right">
          <p className="text-3xl font-extrabold tabular-nums">{formatMoney(trip.actual?.fare ?? trip.estimate.fare)}</p>
          <p className="text-sm text-fg-muted">
            {trip.actual ? "Final fare" : "Estimate"} · {humanize(trip.vehicleCategory)} · {humanize(trip.paymentMethod)}
            {trip.payment && ` · ${trip.payment.provider === "SANDBOX" ? "Sandbox card, no real charge" : "Paid in cash"}`}
          </p>
        </div>
      </div>

      <div className="grid gap-4 lg:grid-cols-[1fr_22rem]">
        <div className="flex flex-col gap-4">
          {completed && <TripInsightsPanel rideId={trip.id} />}
          {completed && <TripQuestions rideId={trip.id} />}
          <Card>
            <CardTitle>Fare breakdown</CardTitle>
            <FareBreakdownTable estimate={trip.estimate.breakdown} final={trip.actual?.breakdown} />
          </Card>
        </div>
        <div className="flex flex-col gap-4">
          {trip.driver && (
            <Card>
              <CardTitle>Driver</CardTitle>
              <p className="font-semibold">{trip.driver.fullName}</p>
              {trip.driver.vehicle && (
                <p className="text-sm text-fg-muted">{trip.driver.vehicle.color} {trip.driver.vehicle.make} {trip.driver.vehicle.model} · {trip.driver.vehicle.plateNumber}</p>
              )}
            </Card>
          )}
          {completed && trip.driver && <Card><RatingForm rideId={trip.id} subject="driver" /></Card>}
          <Card>
            <CardTitle>Timeline</CardTitle>
            {timeline.data ? <RideTimeline entries={timeline.data} /> : <LoadingBlock label="Loading timeline" rows={2} />}
          </Card>
        </div>
      </div>
    </div>
  );
}
