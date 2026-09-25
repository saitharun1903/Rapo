"use client";

import { useQuery } from "@tanstack/react-query";
import { Badge, Card, CardTitle, ErrorState, LoadingBlock, PageHeader, Stat, type Tone } from "@/components/ui/surface";
import { api, unwrap } from "@/lib/api/client";
import { formatTime } from "@/lib/format";
import { queryKeys } from "@/lib/queryKeys";

const REFRESH_SECONDS = 10;
const REFRESH_MS = REFRESH_SECONDS * 1_000;

function healthTone(status: string): Tone {
  return status === "UP" ? "success" : status === "DOWN" || status === "OUT_OF_SERVICE" ? "danger" : "warning";
}

function flag(value: boolean | null | undefined, whenTrue: string, whenFalse: string): string {
  return value === null || value === undefined ? "Not reported" : value ? whenTrue : whenFalse;
}

export default function AdminSystemPage() {
  const system = useQuery({
    queryKey: queryKeys.admin.system,
    queryFn: () => unwrap(api.GET("/api/admin/system")),
    refetchInterval: REFRESH_MS,
  });

  return (
    <div className="mx-auto max-w-7xl px-4 py-8">
      <PageHeader title="System" description={`Live state of the backend instance that answered, refreshed every ${REFRESH_SECONDS} s.`}
        action={system.dataUpdatedAt > 0 && <span className="text-sm text-fg-muted">Updated {formatTime(new Date(system.dataUpdatedAt).toISOString())}</span>} />
      {system.isPending && <LoadingBlock label="Loading system status" />}
      {system.isError && <ErrorState error={system.error} onRetry={() => void system.refetch()} />}
      {system.data && (
        <div className="flex flex-col gap-4">
          <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
            <Stat label="Health" value={<Badge tone={healthTone(system.data.health)} className="text-base">{system.data.health}</Badge>} />
            <Stat label="Outbox backlog" value={system.data.outboxPending ?? "—"} detail="Events waiting for Kafka" />
            <Stat label="WebSocket sessions" value={system.data.webSocketSessions ?? "—"} detail="On this instance" />
            <Stat label="AI calls in flight" value={system.data.aiCallsActive ?? "—"}
              detail={flag(system.data.aiCircuitOpen, "Circuit open: AI calls paused", "Circuit closed")} />
          </div>
          <div className="grid gap-4 lg:grid-cols-2">
            <Card>
              <CardTitle>Health components</CardTitle>
              <ul className="flex flex-col gap-2">
                {Object.entries(system.data.components).map(([name, status]) => (
                  <li key={name} className="flex items-center justify-between text-sm">
                    <span className="font-mono">{name}</span><Badge tone={healthTone(status)}>{status}</Badge>
                  </li>
                ))}
              </ul>
              <p className="mt-3 text-sm text-fg-muted">Redis: {flag(system.data.redisAvailable, "available", "bypassed (caches and rate limits fail open)")}</p>
            </Card>
            <Card>
              <CardTitle>Dead letters since start</CardTitle>
              {Object.keys(system.data.deadLettersByTopic).length === 0
                ? <p className="text-sm text-fg-muted">None. Every Kafka record was processed.</p>
                : (
                  <ul className="flex flex-col gap-2">
                    {Object.entries(system.data.deadLettersByTopic).map(([topic, count]) => (
                      <li key={topic} className="flex items-center justify-between text-sm">
                        <span className="font-mono">{topic}</span><Badge tone="danger">{count}</Badge>
                      </li>
                    ))}
                  </ul>
                )}
            </Card>
          </div>
        </div>
      )}
    </div>
  );
}
