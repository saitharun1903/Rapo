"use client";

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import clsx from "clsx";
import { MessageCircleQuestion } from "lucide-react";
import { useState } from "react";
import { Button } from "@/components/ui/button";
import { Textarea } from "@/components/ui/form";
import { Card, CardTitle, Skeleton } from "@/components/ui/surface";
import { api, unwrap } from "@/lib/api/client";
import { errorMessage, isApiError } from "@/lib/api/errors";
import type { TripQuestion } from "@/lib/api/types";
import { formatTime } from "@/lib/format";
import { queryKeys } from "@/lib/queryKeys";

const QUESTION_MAX = 500;
const SUGGESTIONS = ["Why did I pay more than the estimate?", "How was my fare calculated?", "Was my route longer than expected?"];

function Answer({ item }: { item: TripQuestion }) {
  if (item.status !== "COMPLETED") {
    return <p className="text-sm text-fg-muted">No answer: AI answers were unavailable when you asked.</p>;
  }
  return <p className={clsx("text-sm", item.answerable ? "text-fg" : "text-fg-muted")}>{item.answer}</p>;
}

export function TripQuestions({ rideId }: { rideId: string }) {
  const queryClient = useQueryClient();
  const [question, setQuestion] = useState("");
  const history = useQuery({
    queryKey: queryKeys.questions(rideId),
    queryFn: () => unwrap(api.GET("/api/trips/{rideId}/ai-analysis/questions", { params: { path: { rideId } } })),
  });
  const ask = useMutation({
    mutationFn: (text: string) => unwrap(api.POST("/api/trips/{rideId}/ai-analysis/questions", {
      params: { path: { rideId } }, body: { question: text },
    })),
    onSuccess: (answered) => {
      queryClient.setQueryData(queryKeys.questions(rideId), (current: TripQuestion[] | undefined) => [...(current ?? []), answered]);
      setQuestion("");
    },
    // A failed question is still recorded server-side; reload the list so it shows.
    onError: () => void queryClient.invalidateQueries({ queryKey: queryKeys.questions(rideId) }),
  });

  const submit = (text: string) => {
    if (text.trim() !== "") {
      ask.mutate(text.trim());
    }
  };

  return (
    <Card>
      <CardTitle><span className="flex items-center gap-2"><MessageCircleQuestion className="size-4 text-brand-strong" aria-hidden /> Ask about this trip</span></CardTitle>
      {history.isPending && <Skeleton className="h-16" />}
      {history.data && history.data.length > 0 && (
        <ol className="mb-4 flex flex-col gap-3">
          {history.data.map((item) => (
            <li key={item.id} className="rounded-control bg-surface-2 p-3">
              <p className="text-sm font-semibold text-fg">{item.question}</p>
              <Answer item={item} />
              <p className="mt-1 text-xs text-fg-muted">{formatTime(item.askedAt)}</p>
            </li>
          ))}
        </ol>
      )}
      {history.data?.length === 0 && (
        <div className="mb-3 flex flex-wrap gap-2">
          {SUGGESTIONS.map((suggestion) => (
            <Button key={suggestion} size="sm" variant="secondary" onClick={() => submit(suggestion)} disabled={ask.isPending}>{suggestion}</Button>
          ))}
        </div>
      )}
      <form onSubmit={(event) => { event.preventDefault(); submit(question); }} className="flex flex-col gap-2">
        <Textarea aria-label="Your question" placeholder="Ask anything about this trip's fare, route or timing" maxLength={QUESTION_MAX}
          value={question} onChange={(event) => setQuestion(event.target.value)} />
        {ask.isError && (
          <p role="alert" className="text-sm text-danger">
            {isApiError(ask.error, "AI_UNAVAILABLE") ? "AI answers are unavailable right now. Please try again later." : errorMessage(ask.error)}
          </p>
        )}
        <div className="flex items-center justify-between gap-2">
          <span className="text-xs text-fg-muted">Answers use only this trip&apos;s recorded data.</span>
          <Button type="submit" loading={ask.isPending} disabled={question.trim() === ""}>Ask</Button>
        </div>
      </form>
    </Card>
  );
}
