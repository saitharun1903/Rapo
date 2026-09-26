"use client";

import { Navigation } from "lucide-react";
import { useState } from "react";
import { TripSummary } from "@/components/ride/TripSummary";
import { Button } from "@/components/ui/button";
import type { RideOffer } from "@/lib/api/types";
import { formatDistance, formatDuration, formatMoney, humanize } from "@/lib/format";

const MS_PER_SECOND = 1_000;
const RING_RADIUS = 15;
const RING_LENGTH = 2 * Math.PI * RING_RADIUS;

type OfferCardProps = {
  offer: RideOffer;
  now: number;
  onAccept: () => void;
  onDecline: () => void;
  accepting: boolean;
  declining: boolean;
};

/** The time left to answer, as a ring that empties; the number inside is what screen readers get. */
function Countdown({ secondsLeft, totalSeconds }: { secondsLeft: number; totalSeconds: number }) {
  const left = totalSeconds > 0 ? secondsLeft / totalSeconds : 0;
  return (
    <span className="relative flex size-10 items-center justify-center">
      <svg viewBox="0 0 36 36" className="absolute inset-0 -rotate-90" aria-hidden>
        <circle cx="18" cy="18" r={RING_RADIUS} fill="none" className="stroke-line" strokeWidth="3" />
        <circle cx="18" cy="18" r={RING_RADIUS} fill="none" strokeWidth="3" strokeLinecap="round"
          className={secondsLeft <= 5 ? "stroke-danger" : "stroke-brand"}
          strokeDasharray={RING_LENGTH} strokeDashoffset={RING_LENGTH * (1 - left)}
          style={{ transition: "stroke-dashoffset 1s linear" }} />
      </svg>
      <span className="num relative text-xs font-semibold text-fg">{secondsLeft}s</span>
    </span>
  );
}

/** A ride offered to this driver, with the time left to answer (the offer's expiry is set by the server). */
export function OfferCard({ offer, now, onAccept, onDecline, accepting, declining }: OfferCardProps) {
  const secondsLeft = Math.max(0, Math.ceil((Date.parse(offer.expiresAt) - now) / MS_PER_SECOND));
  // The ring measures the time left from when the offer appeared on this screen.
  const [totalSeconds] = useState(() => Math.max(1, secondsLeft));
  return (
    <article className="flex flex-col gap-4 rounded-card border border-brand/40 bg-surface p-4 shadow-raise animate-rise"
      aria-label={`Ride offer, ${secondsLeft} seconds to answer`}>
      <div className="flex items-start justify-between gap-3">
        <div>
          <p className="eyebrow">New ride · {humanize(offer.vehicleCategory)}</p>
          <p className="num mt-1 text-[1.9rem] font-semibold leading-none tracking-[-0.03em]">{formatMoney(offer.estimatedFare)}</p>
          <p className="num mt-1.5 text-sm text-fg-muted">
            {formatDistance(offer.estimatedDistanceMeters)} trip · about {formatDuration(offer.estimatedDurationSeconds)}
          </p>
        </div>
        <Countdown secondsLeft={secondsLeft} totalSeconds={totalSeconds} />
      </div>
      <p className="flex items-center gap-2 text-sm font-medium text-fg">
        <Navigation className="size-4 text-brand" aria-hidden /> {formatDistance(offer.distanceToPickupMeters)} to pickup
      </p>
      <TripSummary pickup={offer.pickup} dropoff={offer.dropoff} />
      <div className="grid grid-cols-[1fr_2fr] gap-2">
        <Button variant="secondary" size="lg" onClick={onDecline} loading={declining} disabled={accepting}>Decline</Button>
        <Button variant="signal" size="lg" onClick={onAccept} loading={accepting} disabled={declining || secondsLeft === 0}>Accept</Button>
      </div>
    </article>
  );
}
