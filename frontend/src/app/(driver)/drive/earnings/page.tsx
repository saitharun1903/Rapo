"use client";

import { useQuery } from "@tanstack/react-query";
import { useState } from "react";
import { BarSeries } from "@/components/charts/BarSeries";
import { Segmented } from "@/components/ui/segmented";
import { Card, CardTitle, EmptyState, ErrorState, LoadingBlock, PageHeader, Stat } from "@/components/ui/surface";
import { api, unwrap } from "@/lib/api/client";
import { formatMoney } from "@/lib/format";
import { queryKeys } from "@/lib/queryKeys";
import { RANGE_OPTIONS, windowFor, type RangeKey } from "@/lib/ranges";

const LOCALE = "en-IN";

export default function EarningsPage() {
  const [range, setRange] = useState<RangeKey>("7d");
  const period = windowFor(range);
  const earnings = useQuery({
    queryKey: queryKeys.earnings(period.from, period.to, period.granularity),
    queryFn: () => unwrap(api.GET("/api/drivers/me/earnings", { params: { query: period } })),
  });

  const data = earnings.data;
  const hourly = data?.granularity === "HOUR";
  const bucketLabel = (iso: string) => new Intl.DateTimeFormat(LOCALE, hourly
    ? { hour: "numeric", timeZone: data?.timeZone }
    : { day: "numeric", month: "short", timeZone: data?.timeZone }).format(new Date(iso));

  return (
    <div className="mx-auto max-w-5xl px-4 py-8">
      <PageHeader title="Earnings" description="Your share of fares from completed rides, after the platform fee."
        action={<Segmented label="Period" options={RANGE_OPTIONS} value={range} onChange={setRange} />} />
      {earnings.isPending && <LoadingBlock label="Loading earnings" />}
      {earnings.isError && <ErrorState error={earnings.error} onRetry={() => void earnings.refetch()} />}
      {data && (
        <div className="flex flex-col gap-4">
          <div className="grid gap-4 sm:grid-cols-3">
            <Stat label="Earned" value={formatMoney(data.total)} />
            <Stat label="Trips" value={data.tripCount} />
            <Stat label="Per trip" value={data.tripCount === 0 ? "—"
              : formatMoney({ amount: (Number(data.total.amount) / data.tripCount).toFixed(2), currency: data.total.currency })} />
          </div>
          <Card>
            <CardTitle>By {hourly ? "hour" : "day"}</CardTitle>
            {data.tripCount === 0 ? (
              <EmptyState title="No completed trips in this period">Go online to start receiving ride offers.</EmptyState>
            ) : (
              <BarSeries label={`Earnings by ${hourly ? "hour" : "day"}`}
                data={data.series.map((bucket) => ({ label: bucketLabel(bucket.start), earnings: Number(bucket.earnings.amount) }))}
                bars={[{ key: "earnings", name: "Earnings", color: "var(--brand)" }]}
                valueFormatter={(value) => formatMoney({ amount: String(value), currency: data.total.currency })} />
            )}
            <p className="mt-2 text-xs text-fg-muted">Times shown in {data.timeZone}.</p>
          </Card>
        </div>
      )}
    </div>
  );
}
