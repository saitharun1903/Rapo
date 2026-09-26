"use client";

import { keepPreviousData, useQuery } from "@tanstack/react-query";
import Link from "next/link";
import { useState } from "react";
import { StatusBadge } from "@/components/ride/StatusBadge";
import { Field, Input, Select } from "@/components/ui/form";
import { Pager } from "@/components/ui/pager";
import { EmptyState, ErrorState, LoadingBlock, PageHeader } from "@/components/ui/surface";
import { Table, Td, Th, Tr } from "@/components/ui/table";
import { api, unwrap } from "@/lib/api/client";
import type { RideStatus } from "@/lib/api/types";
import { formatDateTime, formatMoney, humanize } from "@/lib/format";
import { queryKeys } from "@/lib/queryKeys";

const PAGE_SIZE = 20;
const STATUSES: RideStatus[] = ["REQUESTED", "MATCHING", "DRIVER_ASSIGNED", "DRIVER_ARRIVING", "DRIVER_ARRIVED", "IN_PROGRESS",
  "COMPLETED", "CANCELLED", "EXPIRED"];

/** A yyyy-mm-dd date input as the start of that day in the admin's browser zone, as an ISO instant. */
function dayStart(value: string): string | undefined {
  return value === "" ? undefined : new Date(`${value}T00:00:00`).toISOString();
}

function dayAfter(value: string): string | undefined {
  if (value === "") {
    return undefined;
  }
  const next = new Date(`${value}T00:00:00`);
  next.setDate(next.getDate() + 1);
  return next.toISOString();
}

export default function AdminRidesPage() {
  const [status, setStatus] = useState<RideStatus | "">("");
  const [from, setFrom] = useState("");
  const [to, setTo] = useState("");
  const [page, setPage] = useState(0);
  const query = { status: status === "" ? undefined : status, from: dayStart(from), to: dayAfter(to), page, size: PAGE_SIZE, sort: "requestedAt,desc" };
  const rides = useQuery({
    queryKey: queryKeys.admin.rides(JSON.stringify(query)),
    queryFn: () => unwrap(api.GET("/api/admin/rides", { params: { query } })),
    placeholderData: keepPreviousData,
  });
  const filter = <T,>(set: (value: T) => void) => (value: T) => { set(value); setPage(0); };

  return (
    <div className="mx-auto max-w-7xl px-4 py-8">
      <PageHeader title="Rides" description="Every ride on the platform, newest first." />
      <div className="mb-4 grid gap-3 sm:grid-cols-3">
        <Field label="Status">
          {({ id }) => (
            <Select id={id} value={status} onChange={(event) => filter(setStatus)(event.target.value as RideStatus | "")}>
              <option value="">Any status</option>
              {STATUSES.map((value) => <option key={value} value={value}>{humanize(value)}</option>)}
            </Select>
          )}
        </Field>
        <Field label="Requested from">
          {({ id }) => <Input id={id} type="date" value={from} onChange={(event) => filter(setFrom)(event.target.value)} />}
        </Field>
        <Field label="Requested until">
          {({ id }) => <Input id={id} type="date" value={to} onChange={(event) => filter(setTo)(event.target.value)} />}
        </Field>
      </div>
      {rides.isPending && <LoadingBlock label="Loading rides" rows={5} />}
      {rides.isError && <ErrorState error={rides.error} onRetry={() => void rides.refetch()} />}
      {rides.data && rides.data.content.length === 0 && <EmptyState title="No rides match these filters" />}
      {rides.data && rides.data.content.length > 0 && (
        <>
          <Table caption="Rides">
            <thead><tr><Th>Requested</Th><Th>Status</Th><Th>Category</Th><Th>From</Th><Th>To</Th><Th className="text-right">Fare</Th></tr></thead>
            <tbody>
              {rides.data.content.map((ride) => (
                <Tr key={ride.id}>
                  <Td><Link href={`/admin/rides/${ride.id}`} className="font-medium text-brand-strong hover:underline">{formatDateTime(ride.requestedAt)}</Link></Td>
                  <Td><StatusBadge status={ride.status} /></Td>
                  <Td>{humanize(ride.vehicleCategory)}</Td>
                  <Td className="max-w-56 truncate">{ride.pickupAddress}</Td>
                  <Td className="max-w-56 truncate">{ride.dropoffAddress}</Td>
                  <Td className="text-right tabular-nums">{formatMoney(ride.fare)}{!ride.fareIsFinal && <span className="text-xs text-fg-muted"> est.</span>}</Td>
                </Tr>
              ))}
            </tbody>
          </Table>
          <Pager page={page} totalPages={rides.data.totalPages} onChange={setPage} />
        </>
      )}
    </div>
  );
}
