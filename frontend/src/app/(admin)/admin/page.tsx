"use client";

import { useQuery } from "@tanstack/react-query";
import Link from "next/link";
import { useState } from "react";
import { BarSeries } from "@/components/charts/BarSeries";
import { StatusBadge } from "@/components/ride/StatusBadge";
import { Segmented } from "@/components/ui/segmented";
import { Card, CardTitle, ErrorState, LoadingBlock, PageHeader, Stat } from "@/components/ui/surface";
import { api, unwrap } from "@/lib/api/client";
import { formatMoney, formatPercent, formatTime, humanize } from "@/lib/format";
import { queryKeys } from "@/lib/queryKeys";
import { RANGE_OPTIONS, windowFor, type RangeKey } from "@/lib/ranges";
import { useRealtimeSubscription } from "@/lib/realtime/RealtimeProvider";
import { Destinations, type AdminActivityMessage } from "@/lib/realtime/types";

const FEED_LENGTH = 15;
const LOCALE = "en-IN";
const SECONDS_PER_MINUTE = 60;

function formatSeconds(seconds: number | null | undefined): string {
  if (seconds === null || seconds === undefined) {
    return "—";
  }
  return seconds < SECONDS_PER_MINUTE ? `${Math.round(seconds)} s` : `${(seconds / SECONDS_PER_MINUTE).toFixed(1)} min`;
}

export default function AdminOverviewPage() {
  const [range, setRange] = useState<RangeKey>("7d");
  const [feed, setFeed] = useState<AdminActivityMessage[]>([]);
  const period = windowFor(range);

  const overview = useQuery({
    queryKey: queryKeys.admin.overview(period.from, period.to),
    queryFn: () => unwrap(api.GET("/api/admin/overview", { params: { query: { from: period.from, to: period.to } } })),
  });
  const activity = useQuery({
    queryKey: queryKeys.admin.activity(period.from, period.to, period.granularity),
    queryFn: () => unwrap(api.GET("/api/admin/analytics/rides", { params: { query: period } })),
  });
  useRealtimeSubscription(Destinations.adminActivity, (event) => setFeed((current) => [event, ...current].slice(0, FEED_LENGTH)));

  const data = overview.data;
  const hourly = activity.data?.granularity === "HOUR";
  const label = (iso: string) => new Intl.DateTimeFormat(LOCALE, hourly
    ? { hour: "numeric", timeZone: activity.data?.timeZone }
    : { day: "numeric", month: "short", timeZone: activity.data?.timeZone }).format(new Date(iso));

  return (
    <div className="mx-auto max-w-7xl px-4 py-8">
      <PageHeader title="Overview" description="Rides requested in the period, and money from rides completed in it."
        action={<Segmented label="Period" options={RANGE_OPTIONS} value={range} onChange={setRange} />} />
      {overview.isPending && <LoadingBlock label="Loading overview" />}
      {overview.isError && <ErrorState error={overview.error} onRetry={() => void overview.refetch()} />}
      {data && (
        <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
          <Stat label="Rides requested" value={data.ridesRequested} detail={`${data.ridesByStatus.COMPLETED ?? 0} completed`} />
          <Stat label="Completion rate" value={formatPercent(data.completionRate)} detail={`Cancelled ${formatPercent(data.cancellationRate)}`} />
          <Stat label="Median time to match" value={formatSeconds(data.medianSecondsToMatch)} detail="Request to driver accepting" />
          <Stat label="Gross fares" value={formatMoney(data.grossFares)} detail={`Platform fees ${formatMoney(data.platformFees)}`} />
          <Stat label="Drivers available" value={data.verifiedDrivers.AVAILABLE ?? 0}
            detail={`${data.verifiedDrivers.ON_TRIP ?? 0} on a trip · ${data.verifiedDrivers.OFFLINE ?? 0} offline`} />
        </div>
      )}
      <div className="mt-4 grid gap-4 lg:grid-cols-[1fr_22rem]">
        <Card>
          <CardTitle>Ride activity</CardTitle>
          {activity.isPending && <LoadingBlock label="Loading activity" rows={1} />}
          {activity.isError && <ErrorState error={activity.error} onRetry={() => void activity.refetch()} />}
          {activity.data && (
            <>
              <BarSeries label="Ride activity per period"
                data={activity.data.series.map((bucket) => ({
                  label: label(bucket.start), completed: bucket.completed, cancelled: bucket.cancelled, expired: bucket.expired,
                }))}
                bars={[
                  { key: "completed", name: "Completed", color: "var(--success)", stack: "outcome" },
                  { key: "cancelled", name: "Cancelled", color: "var(--danger)", stack: "outcome" },
                  { key: "expired", name: "Expired", color: "var(--fg-muted)", stack: "outcome" },
                ]} />
              <p className="mt-2 text-xs text-fg-muted">Times shown in {activity.data.timeZone}.</p>
            </>
          )}
        </Card>
        <Card>
          <CardTitle>Live status changes</CardTitle>
          {feed.length === 0 && <p className="text-sm text-fg-muted">Waiting for ride activity…</p>}
          <ul className="flex flex-col gap-2" aria-live="polite">
            {feed.map((event) => (
              <li key={`${event.rideId}-${event.rideVersion}`} className="flex items-center justify-between gap-2 text-sm">
                <Link href={`/admin/rides/${event.rideId}`} className="flex items-center gap-2 hover:underline">
                  <StatusBadge status={event.status} />
                  <span className="text-fg-muted">by {humanize(event.actor).toLowerCase()}</span>
                </Link>
                <span className="text-xs text-fg-muted">{formatTime(event.occurredAt)}</span>
              </li>
            ))}
          </ul>
        </Card>
      </div>
    </div>
  );
}
