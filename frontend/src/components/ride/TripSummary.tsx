import type { Money, RideResponse } from "@/lib/api/types";
import { formatMoney } from "@/lib/format";

type Place = RideResponse["pickup"];

type TripSummaryProps = {
  pickup: Place;
  dropoff: Place;
  /** Shown under the places when given, with its label. */
  fare?: { label: string; amount: Money | null | undefined };
};

/** Where from and where to, and optionally the price: the facts of the booking, the same in every state. */
export function TripSummary({ pickup, dropoff, fare }: TripSummaryProps) {
  return (
    <div className="rounded-card border border-line">
      <ol className="relative flex flex-col gap-3 p-4 text-sm">
        <span aria-hidden className="absolute bottom-[1.6rem] left-[1.3rem] top-[1.6rem] w-px bg-line-strong" />
        <li className="relative flex items-start gap-3">
          <span aria-hidden className="mt-1 size-2.5 shrink-0 rounded-full border-2 border-ink bg-surface" />
          <span className="min-w-0"><span className="sr-only">From </span><span className="line-clamp-2 text-fg">{pickup.address}</span></span>
        </li>
        <li className="relative flex items-start gap-3">
          <span aria-hidden className="mt-1 size-2.5 shrink-0 rounded-[2px] bg-ink" />
          <span className="min-w-0"><span className="sr-only">To </span><span className="line-clamp-2 text-fg">{dropoff.address}</span></span>
        </li>
      </ol>
      {fare && (
        <div className="flex items-baseline justify-between border-t border-line px-4 py-3 text-sm">
          <span className="text-fg-muted">{fare.label}</span>
          <span className="num font-semibold text-fg">{formatMoney(fare.amount)}</span>
        </div>
      )}
    </div>
  );
}
