import type { RideResponse } from "@/lib/api/types";
import { formatMoney } from "@/lib/format";

/** Where from, where to and the price: the facts of the booking, the same in every state. */
export function TripSummary({ ride, showFare = true }: { ride: Pick<RideResponse, "pickup" | "dropoff" | "estimate" | "actual">; showFare?: boolean }) {
  return (
    <div className="rounded-card border border-line">
      <ol className="relative flex flex-col gap-3 p-4 text-sm">
        <span aria-hidden className="absolute bottom-[1.6rem] left-[1.3rem] top-[1.6rem] w-px bg-line-strong" />
        <li className="relative flex items-start gap-3">
          <span aria-hidden className="mt-1 size-2.5 shrink-0 rounded-full border-2 border-ink bg-surface" />
          <span className="min-w-0"><span className="sr-only">From </span><span className="line-clamp-2 text-fg">{ride.pickup.address}</span></span>
        </li>
        <li className="relative flex items-start gap-3">
          <span aria-hidden className="mt-1 size-2.5 shrink-0 rounded-[2px] bg-ink" />
          <span className="min-w-0"><span className="sr-only">To </span><span className="line-clamp-2 text-fg">{ride.dropoff.address}</span></span>
        </li>
      </ol>
      {showFare && (
        <div className="flex items-baseline justify-between border-t border-line px-4 py-3 text-sm">
          <span className="text-fg-muted">{ride.actual ? "Fare" : "Estimated fare"}</span>
          <span className="num font-semibold text-fg">{formatMoney(ride.actual?.fare ?? ride.estimate.fare)}</span>
        </div>
      )}
    </div>
  );
}
