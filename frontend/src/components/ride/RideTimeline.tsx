import type { RideTimelineEntry } from "@/lib/api/types";
import { formatTime, humanize } from "@/lib/format";

export function RideTimeline({ entries }: { entries: RideTimelineEntry[] }) {
  return (
    <ol className="relative flex flex-col gap-4 border-l-2 border-line pl-5">
      {entries.map((entry) => (
        <li key={entry.rideVersion} className="relative">
          <span aria-hidden className="absolute -left-[27px] top-1 size-3 rounded-full border-2 border-surface bg-brand" />
          <p className="text-sm font-semibold text-fg">{humanize(entry.to)}</p>
          <p className="text-xs text-fg-muted">
            {formatTime(entry.occurredAt)} · by {humanize(entry.actor).toLowerCase()}
            {entry.reason ? ` · ${entry.reason}` : ""}
          </p>
        </li>
      ))}
    </ol>
  );
}
