"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Calculator, RefreshCw, Sparkles } from "lucide-react";
import { toast } from "sonner";
import { Button } from "@/components/ui/button";
import { Badge, Card, ErrorState, Skeleton } from "@/components/ui/surface";
import { api, unwrap } from "@/lib/api/client";
import { errorMessage, isApiError } from "@/lib/api/errors";
import type { TripAnalysis } from "@/lib/api/types";
import { humanize } from "@/lib/format";
import { queryKeys } from "@/lib/queryKeys";

/** How often to look again while the analysis is being written (a local model can take minutes). */
const PENDING_POLL_MS = 4_000;
const MS_PER_MINUTE = 60_000;

const FAILURE_MESSAGES: Record<string, string> = {
  TIMEOUT: "The AI model took too long to answer.",
  PROVIDER_ERROR: "The AI provider returned an error.",
  RATE_LIMITED: "The AI provider is rate limiting requests.",
  INVALID_RESPONSE: "The AI answer did not pass our checks, so it was not shown.",
  REFUSED: "The AI model declined to answer.",
  BUSY: "Too many AI requests are running right now.",
  UNAVAILABLE: "AI insights are not available right now.",
};

/** AI is switched off on this deployment (AI_PROVIDER=disabled), as opposed to failing for a while. */
export function aiSwitchedOff(analysis: TripAnalysis | undefined): boolean {
  return analysis?.provider === "disabled";
}

/** The analysis query, shared with the questions panel so both read one request. */
export function analysisQuery(rideId: string) {
  return {
    queryKey: queryKeys.analysis(rideId),
    queryFn: () => unwrap(api.GET("/api/trips/{rideId}/ai-analysis", { params: { path: { rideId } } })),
    refetchInterval: (query: { state: { data?: TripAnalysis } }) => (query.state.data?.status === "PENDING" ? PENDING_POLL_MS : false),
  };
}

function statusNote(analysis: TripAnalysis): string {
  if (aiSwitchedOff(analysis)) {
    return "AI summaries are switched off on this Raido deployment. Everything above comes from your trip's recorded data.";
  }
  if (analysis.status === "UNAVAILABLE") {
    return "AI insights are switched off or unavailable. The figures below come straight from your trip.";
  }
  return analysis.failureCode ? FAILURE_MESSAGES[analysis.failureCode] ?? "The AI summary failed." : "The AI summary failed.";
}

export function TripInsightsPanel({ rideId }: { rideId: string }) {
  const queryClient = useQueryClient();
  const analysis = useQuery(analysisQuery(rideId));
  const regenerate = useMutation({
    mutationFn: () => unwrap(api.POST("/api/trips/{rideId}/ai-analysis/regenerate", { params: { path: { rideId } } })),
    onSuccess: (pending) => queryClient.setQueryData(queryKeys.analysis(rideId), pending),
    onError: (error) => {
      if (isApiError(error, "RATE_LIMITED") && error.retryAfterMs !== null) {
        toast.error(`You can try again in ${Math.ceil(error.retryAfterMs / MS_PER_MINUTE)} min.`);
      } else {
        toast.error(errorMessage(error));
      }
    },
  });

  if (analysis.isPending) {
    return <Card><Skeleton className="h-40" /></Card>;
  }
  if (analysis.isError) {
    return <ErrorState error={analysis.error} onRetry={() => void analysis.refetch()} title="Could not load trip insights" />;
  }
  const data = analysis.data;
  const insights = data.insights;

  return (
    <div className="flex flex-col gap-4">
      <Card>
        <h2 className="mb-3 flex items-center gap-2 text-base font-semibold">
          <Calculator className="size-4 text-fg-muted" aria-hidden /> From your trip data
        </h2>
        {data.observations.length === 0
          ? <p className="text-sm text-fg-muted">Nothing unusual about this trip.</p>
          : (
            <ul className="flex list-disc flex-col gap-1.5 pl-5 text-sm text-fg">
              {data.observations.map((observation) => <li key={observation.key}>{observation.text}</li>)}
            </ul>
          )}
        <p className="mt-3 text-xs text-fg-muted">Calculated by Raido from the recorded trip, not by AI.</p>
      </Card>

      <Card aria-busy={data.status === "PENDING"}>
        <div className="mb-3 flex flex-wrap items-center justify-between gap-2">
          <h2 className="flex items-center gap-2 text-base font-semibold">
            <Sparkles className="size-4 text-brand-strong" aria-hidden /> AI summary
          </h2>
          <Badge tone={data.status === "COMPLETED" ? "brand" : data.status === "PENDING" ? "warning" : "neutral"}>
            {data.status === "PENDING" ? "Writing…" : humanize(data.status)}
          </Badge>
        </div>
        {data.status === "PENDING" && (
          <div role="status" aria-label="The AI summary is being written" className="flex flex-col gap-2">
            <Skeleton className="h-4 w-11/12" /><Skeleton className="h-4 w-10/12" /><Skeleton className="h-4 w-8/12" />
          </div>
        )}
        {data.status === "COMPLETED" && insights && (
          <div className="flex flex-col gap-3 text-sm">
            <p className="text-fg">{insights.summary}</p>
            <div>
              <h3 className="eyebrow">Your fare</h3>
              <p className="mt-1 text-fg">{insights.fareExplanation}</p>
            </div>
            {insights.observations.length > 0 && (
              <ul className="flex flex-col gap-1.5">
                {insights.observations.map((observation) => (
                  <li key={observation.text} className="flex gap-2">
                    <Badge tone="neutral" className="shrink-0">{humanize(observation.type)}</Badge>
                    <span className="text-fg">{observation.text}</span>
                  </li>
                ))}
              </ul>
            )}
            {insights.comparison && (
              <div>
                <h3 className="eyebrow">Compared with your usual trips</h3>
                <p className="mt-1 text-fg">{insights.comparison}</p>
              </div>
            )}
            {insights.recommendations.length > 0 && (
              <div>
                <h3 className="eyebrow">Tips</h3>
                <ul className="mt-1 list-disc pl-5 text-fg">{insights.recommendations.map((tip) => <li key={tip}>{tip}</li>)}</ul>
              </div>
            )}
            <p className="text-xs text-fg-muted">
              Written by AI ({data.provider} · {data.model}) using only this trip&apos;s figures, which are checked before
              you see them. The reasoning can still be wrong; the figures above are the record.
            </p>
          </div>
        )}
        {(data.status === "FAILED" || data.status === "UNAVAILABLE") && (
          <div className="flex flex-col items-start gap-3">
            <p className="text-sm text-fg-muted">{statusNote(data)}</p>
            {!aiSwitchedOff(data) && (
              <Button variant="secondary" size="sm" loading={regenerate.isPending} onClick={() => regenerate.mutate()}>
                <RefreshCw className="size-4" aria-hidden /> Try again
              </Button>
            )}
          </div>
        )}
      </Card>
    </div>
  );
}
