"use client";

import { keepPreviousData, useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useState } from "react";
import { toast } from "sonner";
import { ReasonDialog } from "@/components/admin/ReasonDialog";
import { Button } from "@/components/ui/button";
import { Pager } from "@/components/ui/pager";
import { Segmented } from "@/components/ui/segmented";
import { Badge, EmptyState, ErrorState, LoadingBlock, PageHeader, type Tone } from "@/components/ui/surface";
import { Table, Td, Th, Tr } from "@/components/ui/table";
import { api, unwrap } from "@/lib/api/client";
import { errorMessage } from "@/lib/api/errors";
import type { DriverResponse, DriverVerificationStatus } from "@/lib/api/types";
import { formatDateTime, formatRating, humanize } from "@/lib/format";
import { queryKeys } from "@/lib/queryKeys";

const PAGE_SIZE = 20;
type Filter = DriverVerificationStatus | "ALL";
const FILTERS = [
  { value: "PENDING", label: "Pending" },
  { value: "VERIFIED", label: "Verified" },
  { value: "REJECTED", label: "Rejected" },
  { value: "SUSPENDED", label: "Suspended" },
  { value: "ALL", label: "All" },
] as const;
const TONES: Record<DriverVerificationStatus, Tone> = { PENDING: "warning", VERIFIED: "success", REJECTED: "danger", SUSPENDED: "danger" };

type Decision = { driver: DriverResponse; kind: "reject" | "suspend" };

export default function AdminDriversPage() {
  const queryClient = useQueryClient();
  const [filter, setFilter] = useState<Filter>("PENDING");
  const [page, setPage] = useState(0);
  const [decision, setDecision] = useState<Decision | null>(null);
  const query = { verificationStatus: filter === "ALL" ? undefined : filter, page, size: PAGE_SIZE };
  const drivers = useQuery({
    queryKey: queryKeys.admin.drivers(JSON.stringify(query)),
    queryFn: () => unwrap(api.GET("/api/admin/drivers", { params: { query } })),
    placeholderData: keepPreviousData,
  });
  const refresh = () => queryClient.invalidateQueries({ queryKey: ["admin", "drivers"] });

  const verify = useMutation({
    mutationFn: (driverId: string) => unwrap(api.POST("/api/admin/drivers/{driverId}/verify", { params: { path: { driverId } } })),
    onSuccess: (driver) => { toast.success(`${driver.fullName} is verified.`); void refresh(); },
    onError: (error) => toast.error(errorMessage(error)),
  });
  const decide = useMutation({
    mutationFn: ({ driver, kind, reason }: Decision & { reason: string }) => kind === "reject"
      ? unwrap(api.POST("/api/admin/drivers/{driverId}/reject", { params: { path: { driverId: driver.id } }, body: { reason } }))
      : unwrap(api.POST("/api/admin/drivers/{driverId}/suspend", { params: { path: { driverId: driver.id } }, body: { reason } })),
    onSuccess: (driver) => { toast.success(`${driver.fullName}: ${humanize(driver.verificationStatus).toLowerCase()}.`); setDecision(null); void refresh(); },
    onError: (error) => toast.error(errorMessage(error)),
  });

  return (
    <div className="mx-auto max-w-7xl px-4 py-8">
      <PageHeader title="Drivers" description="Verify new drivers and act on existing ones. Every decision is audited."
        action={<Segmented label="Verification status" options={FILTERS} value={filter} onChange={(value) => { setFilter(value); setPage(0); }} />} />
      {drivers.isPending && <LoadingBlock label="Loading drivers" rows={5} />}
      {drivers.isError && <ErrorState error={drivers.error} onRetry={() => void drivers.refetch()} />}
      {drivers.data && drivers.data.content.length === 0 && (
        <EmptyState title={filter === "PENDING" ? "No applications waiting" : "No drivers here"} />
      )}
      {drivers.data && drivers.data.content.length > 0 && (
        <>
          <Table caption="Drivers">
            <thead><tr><Th>Driver</Th><Th>Licence</Th><Th>Vehicle</Th><Th>Status</Th><Th>Rating</Th><Th>Joined</Th><Th className="text-right">Actions</Th></tr></thead>
            <tbody>
              {drivers.data.content.map((driver) => (
                <Tr key={driver.id}>
                  <Td><span className="font-medium">{driver.fullName}</span><span className="block text-xs text-fg-muted">{driver.email}</span></Td>
                  <Td className="font-mono text-xs">{driver.licenseNumber}</Td>
                  <Td>{driver.vehicle ? <>{driver.vehicle.make} {driver.vehicle.model}<span className="block text-xs text-fg-muted">{driver.vehicle.plateNumber} · {humanize(driver.vehicle.category)}</span></> : "—"}</Td>
                  <Td><Badge tone={TONES[driver.verificationStatus]}>{humanize(driver.verificationStatus)}</Badge></Td>
                  <Td>{formatRating(driver.ratingAvg)}</Td>
                  <Td>{formatDateTime(driver.createdAt)}</Td>
                  <Td className="text-right">
                    <div className="flex justify-end gap-2">
                      {driver.verificationStatus === "PENDING" && (
                        <>
                          <Button size="sm" loading={verify.isPending && verify.variables === driver.id} onClick={() => verify.mutate(driver.id)}>Verify</Button>
                          <Button size="sm" variant="secondary" onClick={() => setDecision({ driver, kind: "reject" })}>Reject</Button>
                        </>
                      )}
                      {driver.verificationStatus === "VERIFIED" && (
                        <Button size="sm" variant="secondary" onClick={() => setDecision({ driver, kind: "suspend" })}>Suspend</Button>
                      )}
                    </div>
                  </Td>
                </Tr>
              ))}
            </tbody>
          </Table>
          <Pager page={page} totalPages={drivers.data.totalPages} onChange={setPage} />
        </>
      )}
      <ReasonDialog open={decision !== null} pending={decide.isPending} onClose={() => setDecision(null)}
        title={decision?.kind === "reject" ? `Reject ${decision.driver.fullName}?` : `Suspend ${decision?.driver.fullName ?? ""}?`}
        description={decision?.kind === "reject"
          ? "The driver sees this reason. They cannot go online."
          : "The driver is taken offline at once and cannot go online again."}
        confirmLabel={decision?.kind === "reject" ? "Reject" : "Suspend"}
        onConfirm={(reason) => decision && decide.mutate({ ...decision, reason })} />
    </div>
  );
}
