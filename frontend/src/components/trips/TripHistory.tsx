"use client";

import { keepPreviousData, useQuery } from "@tanstack/react-query";
import { ChevronRight } from "lucide-react";
import Link from "next/link";
import { useState, type ReactNode } from "react";
import { StatusBadge } from "@/components/ride/StatusBadge";
import { Pager } from "@/components/ui/pager";
import { Segmented } from "@/components/ui/segmented";
import { EmptyState, ErrorState, LoadingBlock, PageHeader } from "@/components/ui/surface";
import { api, unwrap } from "@/lib/api/client";
import type { RideStatus, RideSummary } from "@/lib/api/types";
import { formatDateTime, formatMoney } from "@/lib/format";
import { queryKeys } from "@/lib/queryKeys";

const PAGE_SIZE = 10;
type Filter = "ALL" | Extract<RideStatus, "COMPLETED" | "CANCELLED">;
const FILTERS = [
  { value: "ALL", label: "All" },
  { value: "COMPLETED", label: "Completed" },
  { value: "CANCELLED", label: "Cancelled" },
] as const;

type TripHistoryProps = {
  title: string;
  description: string;
  empty: { title: string; body: string; action?: ReactNode };
  /** Where a trip links to, if it has a detail page for this viewer. */
  hrefFor?: (trip: RideSummary) => string;
};

function TripRow({ trip }: { trip: RideSummary }) {
  return (
    <>
      <div className="min-w-0 flex-1">
        <div className="flex flex-wrap items-center gap-2">
          <StatusBadge status={trip.status} />
          <span className="num text-xs text-fg-muted">{formatDateTime(trip.requestedAt)}</span>
        </div>
        <p className="mt-2 truncate text-sm font-medium text-fg">{trip.pickupAddress}</p>
        <p className="truncate text-sm text-fg-muted">→ {trip.dropoffAddress}</p>
      </div>
      <div className="text-right">
        <p className="num font-semibold text-fg">{formatMoney(trip.fare)}</p>
        <p className="text-xs text-fg-muted">{trip.fareIsFinal ? "Final" : "Estimate"}</p>
      </div>
    </>
  );
}

/**
 * The caller's rides, newest first, from GET /api/rides: rides booked for a passenger, rides driven for a driver.
 */
export function TripHistory({ title, description, empty, hrefFor }: TripHistoryProps) {
  const [page, setPage] = useState(0);
  const [filter, setFilter] = useState<Filter>("ALL");
  const trips = useQuery({
    queryKey: queryKeys.rideHistory(page, filter),
    queryFn: () => unwrap(api.GET("/api/rides", {
      params: { query: { page, size: PAGE_SIZE, sort: "requestedAt,desc", status: filter === "ALL" ? undefined : filter } },
    })),
    placeholderData: keepPreviousData,
  });

  return (
    <div className="mx-auto max-w-3xl px-4 py-10">
      <PageHeader title={title} description={description}
        action={<Segmented label="Filter trips" options={FILTERS} value={filter} onChange={(value) => { setFilter(value); setPage(0); }} />} />
      {trips.isPending && <LoadingBlock label="Loading trips" rows={4} />}
      {trips.isError && <ErrorState error={trips.error} onRetry={() => void trips.refetch()} />}
      {trips.data && trips.data.content.length === 0 && (
        <EmptyState title={empty.title} action={empty.action}>{empty.body}</EmptyState>
      )}
      {trips.data && trips.data.content.length > 0 && (
        <>
          <ul className="flex flex-col divide-y divide-line overflow-hidden rounded-card border border-line bg-surface">
            {trips.data.content.map((trip) => (
              <li key={trip.id}>
                {hrefFor ? (
                  <Link href={hrefFor(trip)} className="flex items-center gap-4 p-4 transition-colors hover:bg-surface-2">
                    <TripRow trip={trip} />
                    <ChevronRight className="size-5 text-fg-muted" aria-hidden />
                  </Link>
                ) : (
                  <div className="flex items-center gap-4 p-4"><TripRow trip={trip} /></div>
                )}
              </li>
            ))}
          </ul>
          <Pager page={page} totalPages={trips.data.totalPages} onChange={setPage} />
        </>
      )}
    </div>
  );
}
