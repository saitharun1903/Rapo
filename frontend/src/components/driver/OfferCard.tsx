"use client";

import { Clock, Navigation } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Card } from "@/components/ui/surface";
import type { RideOffer } from "@/lib/api/types";
import { formatDistance, formatDuration, formatMoney, humanize } from "@/lib/format";

const MS_PER_SECOND = 1_000;

type OfferCardProps = {
  offer: RideOffer;
  now: number;
  onAccept: () => void;
  onDecline: () => void;
  accepting: boolean;
  declining: boolean;
};

/** A ride offered to this driver, with the time left to answer (the offer's expiry is set by the server). */
export function OfferCard({ offer, now, onAccept, onDecline, accepting, declining }: OfferCardProps) {
  const secondsLeft = Math.max(0, Math.ceil((Date.parse(offer.expiresAt) - now) / MS_PER_SECOND));
  return (
    <Card className="border-brand/40 p-4" aria-label={`Ride offer, ${secondsLeft} seconds to answer`}>
      <div className="flex items-center justify-between">
        <span className="text-xs font-semibold uppercase tracking-wide text-brand">New ride · {humanize(offer.vehicleCategory)}</span>
        <span className="flex items-center gap-1 text-sm font-bold tabular-nums text-fg" aria-live="off">
          <Clock className="size-4 text-fg-muted" aria-hidden /> {secondsLeft}s
        </span>
      </div>
      <p className="mt-2 text-2xl font-extrabold tabular-nums">{formatMoney(offer.estimatedFare)}</p>
      <p className="text-sm text-fg-muted">
        {formatDistance(offer.estimatedDistanceMeters)} trip · about {formatDuration(offer.estimatedDurationSeconds)}
      </p>
      <p className="mt-3 flex items-center gap-2 text-sm text-fg">
        <Navigation className="size-4 text-brand" aria-hidden /> {formatDistance(offer.distanceToPickupMeters)} to pickup
      </p>
      <dl className="mt-2 grid grid-cols-[auto_1fr] gap-x-3 gap-y-1 text-sm">
        <dt className="text-fg-muted">Pickup</dt><dd className="text-fg">{offer.pickup.address}</dd>
        <dt className="text-fg-muted">Drop</dt><dd className="text-fg">{offer.dropoff.address}</dd>
      </dl>
      <div className="mt-4 grid grid-cols-2 gap-2">
        <Button variant="secondary" onClick={onDecline} loading={declining} disabled={accepting}>Decline</Button>
        <Button onClick={onAccept} loading={accepting} disabled={declining || secondsLeft === 0}>Accept</Button>
      </div>
    </Card>
  );
}
