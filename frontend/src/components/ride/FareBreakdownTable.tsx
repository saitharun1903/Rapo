import type { FareBreakdown } from "@/lib/api/types";
import { formatDistance, formatDuration } from "@/lib/format";

type Row = { label: string; value: (breakdown: FareBreakdown) => string };

const ROWS: Row[] = [
  { label: "Distance", value: (b) => formatDistance(b.distanceMeters) },
  { label: "Time", value: (b) => formatDuration(b.durationSeconds) },
  { label: "Base fare", value: (b) => b.baseFare },
  { label: "Distance charge", value: (b) => b.distanceCharge },
  { label: "Time charge", value: (b) => b.timeCharge },
  { label: "Demand multiplier", value: (b) => `${b.surgeMultiplier}×` },
  { label: "Booking fee", value: (b) => b.bookingFee },
  { label: "Minimum fare applied", value: (b) => (b.minimumFareApplied ? `Yes (${b.minimumFare})` : "No") },
  { label: "Total", value: (b) => `${b.total} ${b.currency}` },
];

/** The estimate next to the final fare, line by line, so a difference can be traced to its cause. */
export function FareBreakdownTable({ estimate, final }: { estimate: FareBreakdown | null | undefined; final?: FareBreakdown | null }) {
  if (!estimate && !final) {
    return <p className="text-sm text-fg-muted">No fare breakdown recorded.</p>;
  }
  return (
    <table className="w-full text-sm">
      <caption className="sr-only">Fare breakdown{final ? ", estimate and final" : ""}</caption>
      <thead>
        <tr className="text-left text-xs uppercase tracking-wide text-fg-muted">
          <th scope="col" className="py-2 font-medium">Item</th>
          <th scope="col" className="py-2 text-right font-medium">Estimate</th>
          {final && <th scope="col" className="py-2 text-right font-medium">Final</th>}
        </tr>
      </thead>
      <tbody>
        {ROWS.map((row) => (
          <tr key={row.label} className="border-t border-line last:font-semibold">
            <th scope="row" className="py-2 text-left font-normal text-fg-muted">{row.label}</th>
            <td className="py-2 text-right tabular-nums">{estimate ? row.value(estimate) : "—"}</td>
            {final && <td className="py-2 text-right tabular-nums">{row.value(final)}</td>}
          </tr>
        ))}
      </tbody>
    </table>
  );
}
