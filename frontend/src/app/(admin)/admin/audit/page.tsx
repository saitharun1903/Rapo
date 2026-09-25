"use client";

import { keepPreviousData, useQuery } from "@tanstack/react-query";
import { useState } from "react";
import { Field, Select } from "@/components/ui/form";
import { Pager } from "@/components/ui/pager";
import { EmptyState, ErrorState, LoadingBlock, PageHeader } from "@/components/ui/surface";
import { Table, Td, Th, Tr } from "@/components/ui/table";
import { api, unwrap } from "@/lib/api/client";
import type { AuditAction } from "@/lib/api/types";
import { formatDateTime, humanize } from "@/lib/format";
import { queryKeys } from "@/lib/queryKeys";

const PAGE_SIZE = 25;
/** Typed as a complete record, so a new backend action fails the typecheck until it is listed here. */
const ACTIONS: Record<AuditAction, true> = {
  LOGIN_FAILED: true,
  PASSWORD_CHANGED: true,
  REFRESH_TOKEN_REUSE_DETECTED: true,
  DRIVER_PROFILE_SUBMITTED: true,
  DRIVER_VERIFIED: true,
  DRIVER_REJECTED: true,
  DRIVER_SUSPENDED: true,
  DRIVER_VEHICLE_REPLACED: true,
  USER_STATUS_CHANGED: true,
  ADMIN_BOOTSTRAPPED: true,
};
const ENTITY_TYPES = ["USER", "DRIVER"] as const;

export default function AdminAuditPage() {
  const [action, setAction] = useState<AuditAction | "">("");
  const [entityType, setEntityType] = useState("");
  const [page, setPage] = useState(0);
  const query = { action: action === "" ? undefined : action, entityType: entityType === "" ? undefined : entityType, page, size: PAGE_SIZE };
  const logs = useQuery({
    queryKey: queryKeys.admin.audit(JSON.stringify(query)),
    queryFn: () => unwrap(api.GET("/api/admin/audit-logs", { params: { query } })),
    placeholderData: keepPreviousData,
  });

  return (
    <div className="mx-auto max-w-7xl px-4 py-8">
      <PageHeader title="Audit log" description="Security and administrative actions, newest first." />
      <div className="mb-4 grid gap-3 sm:grid-cols-2">
        <Field label="Action">
          {({ id }) => (
            <Select id={id} value={action} onChange={(event) => { setAction(event.target.value as AuditAction | ""); setPage(0); }}>
              <option value="">Any action</option>
              {(Object.keys(ACTIONS) as AuditAction[]).map((value) => <option key={value} value={value}>{humanize(value)}</option>)}
            </Select>
          )}
        </Field>
        <Field label="Entity">
          {({ id }) => (
            <Select id={id} value={entityType} onChange={(event) => { setEntityType(event.target.value); setPage(0); }}>
              <option value="">Any entity</option>
              {ENTITY_TYPES.map((value) => <option key={value} value={value}>{humanize(value)}</option>)}
            </Select>
          )}
        </Field>
      </div>
      {logs.isPending && <LoadingBlock label="Loading audit log" rows={5} />}
      {logs.isError && <ErrorState error={logs.error} onRetry={() => void logs.refetch()} />}
      {logs.data && logs.data.content.length === 0 && <EmptyState title="No entries match" />}
      {logs.data && logs.data.content.length > 0 && (
        <>
          <Table caption="Audit log entries">
            <thead><tr><Th>When</Th><Th>Action</Th><Th>Entity</Th><Th>Actor</Th><Th>Details</Th></tr></thead>
            <tbody>
              {logs.data.content.map((entry) => (
                <Tr key={entry.id}>
                  <Td className="whitespace-nowrap">{formatDateTime(entry.createdAt)}</Td>
                  <Td>{humanize(entry.action)}</Td>
                  <Td>{humanize(entry.entityType)}<span className="block font-mono text-xs text-fg-muted">{entry.entityId ?? "—"}</span></Td>
                  <Td className="font-mono text-xs">{entry.actorUserId ?? "system"}</Td>
                  <Td className="max-w-md">
                    {entry.details && Object.keys(entry.details).length > 0 ? (
                      <dl className="grid grid-cols-[auto_1fr] gap-x-2 text-xs">
                        {Object.entries(entry.details).map(([key, value]) => (
                          <div key={key} className="contents"><dt className="text-fg-muted">{key}</dt><dd className="break-words">{String(value)}</dd></div>
                        ))}
                      </dl>
                    ) : <span className="text-fg-muted">—</span>}
                  </Td>
                </Tr>
              ))}
            </tbody>
          </Table>
          <Pager page={page} totalPages={logs.data.totalPages} onChange={setPage} />
        </>
      )}
    </div>
  );
}
