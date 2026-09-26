"use client";

import { useQuery } from "@tanstack/react-query";
import { ArrowLeft } from "lucide-react";
import Link from "next/link";
import { useParams } from "next/navigation";
import { FareBreakdownTable } from "@/components/ride/FareBreakdownTable";
import { RideTimeline } from "@/components/ride/RideTimeline";
import { StatusBadge } from "@/components/ride/StatusBadge";
import { Badge, Card, CardTitle, ErrorState, LoadingBlock } from "@/components/ui/surface";
import { Table, Td, Th, Tr } from "@/components/ui/table";
import { api, unwrap } from "@/lib/api/client";
import { formatDateTime, formatDistance, formatMoney, formatTime, humanize } from "@/lib/format";
import { queryKeys } from "@/lib/queryKeys";

export default function AdminRideDetailPage() {
  const { rideId } = useParams<{ rideId: string }>();
  const detail = useQuery({
    queryKey: queryKeys.admin.ride(rideId),
    queryFn: () => unwrap(api.GET("/api/admin/rides/{rideId}", { params: { path: { rideId } } })),
  });

  if (detail.isPending) {
    return <div className="mx-auto max-w-7xl p-6"><LoadingBlock label="Loading ride" /></div>;
  }
  if (detail.isError) {
    return <div className="mx-auto max-w-7xl p-6"><ErrorState error={detail.error} onRetry={() => void detail.refetch()} /></div>;
  }
  const { ride, passenger, timeline, offers, analysis } = detail.data;

  return (
    <div className="mx-auto max-w-7xl px-4 py-8">
      <Link href="/admin/rides" className="mb-4 inline-flex items-center gap-1 text-sm font-medium text-fg-muted hover:text-fg">
        <ArrowLeft className="size-4" aria-hidden /> All rides
      </Link>
      <div className="mb-6 flex flex-wrap items-center gap-3">
        <h1 className="text-[1.75rem] font-semibold leading-tight tracking-[-0.03em]">Ride</h1>
        <StatusBadge status={ride.status} />
        <span className="font-mono text-xs text-fg-muted">{ride.id}</span>
      </div>
      <div className="grid gap-4 lg:grid-cols-3">
        <Card>
          <CardTitle>People</CardTitle>
          <dl className="grid grid-cols-[auto_1fr] gap-x-4 gap-y-2 text-sm">
            <dt className="text-fg-muted">Passenger</dt>
            <dd>{passenger ? <>{passenger.fullName}<span className="block text-xs text-fg-muted">{passenger.email}</span></> : "—"}</dd>
            <dt className="text-fg-muted">Driver</dt>
            <dd>{ride.driver ? <>{ride.driver.fullName}{ride.driver.vehicle && <span className="block text-xs text-fg-muted">{ride.driver.vehicle.plateNumber}</span>}</> : "—"}</dd>
          </dl>
        </Card>
        <Card>
          <CardTitle>Trip</CardTitle>
          <dl className="grid grid-cols-[auto_1fr] gap-x-4 gap-y-2 text-sm">
            <dt className="text-fg-muted">From</dt><dd>{ride.pickup.address}</dd>
            <dt className="text-fg-muted">To</dt><dd>{ride.dropoff.address}</dd>
            <dt className="text-fg-muted">Requested</dt><dd>{formatDateTime(ride.timestamps.requestedAt)}</dd>
            <dt className="text-fg-muted">Payment</dt>
            <dd>{ride.payment ? `${formatMoney(ride.payment.amount)} · ${humanize(ride.payment.status)} · ${ride.payment.provider}` : humanize(ride.paymentMethod)}</dd>
            {ride.cancellation && <><dt className="text-fg-muted">Cancelled</dt><dd>by {humanize(ride.cancellation.cancelledBy).toLowerCase()}{ride.cancellation.reason && `: ${ride.cancellation.reason}`}</dd></>}
          </dl>
        </Card>
        <Card>
          <CardTitle>AI analysis</CardTitle>
          {analysis ? (
            <div className="flex flex-col gap-1 text-sm">
              <Badge tone={analysis.status === "COMPLETED" ? "success" : analysis.status === "FAILED" ? "danger" : "neutral"}>{humanize(analysis.status)}</Badge>
              {analysis.failureCode && <p className="text-fg-muted">Failure: {humanize(analysis.failureCode)}</p>}
              <p className="text-xs text-fg-muted">Updated {formatDateTime(analysis.updatedAt)}</p>
            </div>
          ) : <p className="text-sm text-fg-muted">No analysis recorded (only completed rides are analysed).</p>}
        </Card>
      </div>
      <div className="mt-4 grid gap-4 lg:grid-cols-[1fr_22rem]">
        <div className="flex flex-col gap-4">
          <Card>
            <CardTitle>Offers</CardTitle>
            {offers.length === 0 ? <p className="text-sm text-fg-muted">No driver was offered this ride.</p> : (
              <Table caption="Offers made to drivers">
                <thead><tr><Th>Round</Th><Th>Driver</Th><Th>Distance</Th><Th>Status</Th><Th>Offered</Th><Th>Answered</Th></tr></thead>
                <tbody>
                  {offers.map((offer) => (
                    <Tr key={`${offer.driverId}-${offer.round}`}>
                      <Td>{offer.round}</Td>
                      <Td className="font-mono text-xs">{offer.driverId}</Td>
                      <Td>{formatDistance(offer.distanceMeters)}</Td>
                      <Td>{humanize(offer.status)}</Td>
                      <Td>{formatTime(offer.offeredAt)}</Td>
                      <Td>{formatTime(offer.respondedAt)}</Td>
                    </Tr>
                  ))}
                </tbody>
              </Table>
            )}
          </Card>
          <Card>
            <CardTitle>Fare</CardTitle>
            <FareBreakdownTable estimate={ride.estimate.breakdown} final={ride.actual?.breakdown} />
          </Card>
        </div>
        <Card>
          <CardTitle>Timeline</CardTitle>
          <RideTimeline entries={timeline} />
        </Card>
      </div>
    </div>
  );
}
