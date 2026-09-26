"use client";

import { CheckCircle2, XCircle } from "lucide-react";
import { LazyMap } from "@/components/map/LazyMap";
import { Button, ButtonLink } from "@/components/ui/button";
import type { Money, RideResponse } from "@/lib/api/types";
import { formatDistance, formatDuration, formatMoney, humanize } from "@/lib/format";
import { passengerHeadline } from "@/lib/ride/status";
import { DriverCard } from "./DriverCard";
import { RatingForm } from "./RatingForm";
import { RideScreen } from "./RideScreen";
import { TripSummary } from "./TripSummary";

/**
 * Why the final fare differs from the estimate, in one sentence. The inputs are the recorded ones: the final
 * fare uses the GPS-measured distance and the time driven, at the surge locked when the ride was booked.
 */
export function fareChange(estimate: Money | null | undefined, final: Money, estimatedMeters: number, actualMeters: number): string {
  if (!estimate) {
    return "Calculated from the route driven.";
  }
  const difference = Number(final.amount) - Number(estimate.amount);
  if (Math.abs(difference) < 1) {
    return "The same as your estimate.";
  }
  const amount = formatMoney({ amount: Math.abs(difference).toFixed(2), currency: final.currency });
  const distance = actualMeters === estimatedMeters ? ""
    : ` The trip was ${formatDistance(actualMeters)}, against ${formatDistance(estimatedMeters)} estimated.`;
  return `${amount} ${difference > 0 ? "more" : "less"} than your estimate.${distance}`;
}

/** What the passenger sees when their ride ends: the fare and a rating, or why it did not happen. */
export function RideOutcome({ ride, onDone }: { ride: RideResponse; onDone: () => void }) {
  const completed = ride.status === "COMPLETED";
  return (
    <RideScreen label="Your ride" peek={completed ? 460 : 300} map={(padding) => (
      <LazyMap label="Map of your trip" pickup={ride.pickup.point} dropoff={ride.dropoff.point}
        fitTo={[ride.pickup.point, ride.dropoff.point]} padding={padding} />
    )}>
      <div className="flex flex-col gap-5 animate-rise">
        <header className="flex items-start gap-3">
          {completed
            ? <CheckCircle2 className="mt-1 size-6 shrink-0 text-success" aria-hidden />
            : <XCircle className="mt-1 size-6 shrink-0 text-fg-muted" aria-hidden />}
          <div>
            <h1 className="text-[1.35rem] font-semibold leading-tight tracking-[-0.02em]" aria-live="polite">{passengerHeadline(ride.status)}</h1>
            {ride.status === "CANCELLED" && ride.cancellation && (
              <p className="mt-0.5 text-sm text-fg-muted">
                Cancelled by {humanize(ride.cancellation.cancelledBy).toLowerCase()}{ride.cancellation.reason ? `: ${ride.cancellation.reason}` : "."}
              </p>
            )}
            {ride.status === "EXPIRED" && (
              <p className="mt-0.5 text-sm text-fg-muted">No driver took the ride after {ride.matching.round} rounds of searching. You were not charged.</p>
            )}
          </div>
        </header>

        {completed && ride.actual && (
          <div className="rounded-card bg-surface-2 p-4">
            <p className="num text-[2.25rem] font-semibold leading-none tracking-[-0.03em]">{formatMoney(ride.actual.fare)}</p>
            <p className="mt-2 text-sm text-fg-muted">
              {fareChange(ride.estimate.fare, ride.actual.fare, ride.estimate.distanceMeters, ride.actual.distanceMeters)}
            </p>
            <p className="num mt-3 text-xs text-fg-muted">
              {formatDistance(ride.actual.distanceMeters)} · {formatDuration(ride.actual.durationSeconds)} · {humanize(ride.paymentMethod)}
              {ride.actual.distanceSource === "ESTIMATED" && " · distance from the route estimate"}
            </p>
          </div>
        )}

        {completed && ride.driver && (
          <div className="flex flex-col gap-4 border-t border-line pt-5">
            <DriverCard driver={ride.driver} />
            <RatingForm rideId={ride.id} subject="driver" />
          </div>
        )}

        <TripSummary ride={ride} showFare={!completed} />

        <div className="flex flex-col gap-2">
          {completed && <ButtonLink href={`/trips/${ride.id}`} variant="secondary">Trip details and insights</ButtonLink>}
          <Button onClick={onDone}>Book another ride</Button>
        </div>
      </div>
    </RideScreen>
  );
}
