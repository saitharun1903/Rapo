"use client";

import { CheckCircle2, XCircle } from "lucide-react";
import { Button, ButtonLink } from "@/components/ui/button";
import { Card } from "@/components/ui/surface";
import type { RideResponse } from "@/lib/api/types";
import { formatDistance, formatDuration, formatMoney, humanize } from "@/lib/format";
import { passengerHeadline } from "@/lib/ride/status";
import { RatingForm } from "./RatingForm";

/** What the passenger sees when their ride ends: the fare and a rating, or why it did not happen. */
export function RideOutcome({ ride, onDone }: { ride: RideResponse; onDone: () => void }) {
  const completed = ride.status === "COMPLETED";
  return (
    <div className="mx-auto flex max-w-lg flex-col gap-4 px-4 py-10">
      <Card className="flex flex-col items-center gap-2 text-center">
        {completed
          ? <CheckCircle2 className="size-10 text-success" aria-hidden />
          : <XCircle className="size-10 text-fg-muted" aria-hidden />}
        <h1 className="text-xl font-semibold" aria-live="polite">{passengerHeadline(ride.status)}</h1>
        {completed && ride.actual && (
          <>
            <p className="text-3xl font-extrabold tabular-nums">{formatMoney(ride.actual.fare)}</p>
            <p className="text-sm text-fg-muted">
              {formatDistance(ride.actual.distanceMeters)} · {formatDuration(ride.actual.durationSeconds)} · {humanize(ride.paymentMethod)}
            </p>
          </>
        )}
        {ride.status === "CANCELLED" && ride.cancellation && (
          <p className="text-sm text-fg-muted">
            Cancelled by {humanize(ride.cancellation.cancelledBy).toLowerCase()}{ride.cancellation.reason ? `: ${ride.cancellation.reason}` : "."}
          </p>
        )}
        {ride.status === "EXPIRED" && <p className="text-sm text-fg-muted">No driver accepted in time. Please try again.</p>}
      </Card>
      {completed && ride.driver && <Card><RatingForm rideId={ride.id} subject="driver" /></Card>}
      <div className="flex flex-wrap justify-center gap-2">
        {completed && <ButtonLink href={`/trips/${ride.id}`} variant="secondary">Trip details and insights</ButtonLink>}
        <Button onClick={onDone}>Book another ride</Button>
      </div>
    </div>
  );
}
