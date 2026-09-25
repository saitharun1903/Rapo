"use client";

import { useEffect } from "react";
import { Button } from "@/components/ui/button";

/** Last line of defence for rendering errors: a way back instead of a blank page. */
export default function ErrorBoundary({ error, reset }: { error: Error & { digest?: string }; reset: () => void }) {
  useEffect(() => {
    // Reported to the console now; Phase 11 sends it to Sentry.
    console.error(error);
  }, [error]);
  return (
    <main role="alert" className="flex min-h-full flex-col items-center justify-center gap-4 px-4 text-center">
      <h1 className="text-2xl font-bold">Something went wrong</h1>
      <p className="max-w-md text-fg-muted">This screen hit an unexpected error. Trying again usually helps.</p>
      <Button onClick={reset}>Try again</Button>
    </main>
  );
}
