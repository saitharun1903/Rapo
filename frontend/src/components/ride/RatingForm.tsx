"use client";

import { useMutation } from "@tanstack/react-query";
import clsx from "clsx";
import { Star } from "lucide-react";
import { useState } from "react";
import { Button } from "@/components/ui/button";
import { Textarea } from "@/components/ui/form";
import { api, unwrap } from "@/lib/api/client";
import { errorMessage, isApiError } from "@/lib/api/errors";

const SCORES = [1, 2, 3, 4, 5] as const;
const COMMENT_MAX = 500;

/** Rate the other side of a completed ride, once. */
export function RatingForm({ rideId, subject }: { rideId: string; subject: "driver" | "passenger" }) {
  const [score, setScore] = useState<number | null>(null);
  const [comment, setComment] = useState("");
  const rate = useMutation({
    mutationFn: () => unwrap(api.POST("/api/rides/{rideId}/rating", {
      params: { path: { rideId } },
      body: { score: score ?? 0, comment: comment.trim() === "" ? null : comment.trim() },
    })),
  });

  if (rate.isSuccess || isApiError(rate.error, "ALREADY_RATED")) {
    return <p role="status" className="text-sm font-medium text-success">Thanks, your rating is saved.</p>;
  }

  return (
    <form className="flex flex-col gap-3" onSubmit={(event) => { event.preventDefault(); rate.mutate(); }}>
      <fieldset>
        <legend className="mb-2 text-sm font-semibold text-fg">Rate your {subject}</legend>
        <div className="flex gap-1">
          {SCORES.map((value) => (
            <button key={value} type="button" onClick={() => setScore(value)} aria-pressed={score === value}
              aria-label={`${value} star${value === 1 ? "" : "s"}`} className="rounded-control p-1 hover:bg-surface-2">
              <Star className={clsx("size-7", score !== null && value <= score ? "fill-warning text-warning" : "text-fg-muted")} aria-hidden />
            </button>
          ))}
        </div>
      </fieldset>
      <Textarea aria-label="Comment (optional)" placeholder="Anything to add? (optional)" maxLength={COMMENT_MAX}
        value={comment} onChange={(event) => setComment(event.target.value)} />
      {rate.isError && <p role="alert" className="text-sm text-danger">{errorMessage(rate.error)}</p>}
      <Button type="submit" disabled={score === null} loading={rate.isPending}>Submit rating</Button>
    </form>
  );
}
