"use client";

import * as Sentry from "@sentry/nextjs";
import { useEffect } from "react";
import { Button } from "@/components/ui/button";

/** Last line of defence for rendering errors: a way back instead of a blank page. */
export default function ErrorBoundary({ error, retry }: { error: Error & { digest?: string }; retry: () => void }) {
  useEffect(() => {
    console.error(error);
    // A no-op unless error reporting is configured (NEXT_PUBLIC_SENTRY_DSN).
    Sentry.captureException(error);
  }, [error]);
  return (
    <main role="alert" className="flex min-h-full flex-col items-center justify-center gap-4 px-4 text-center">
      <h1 className="text-2xl font-semibold">Something went wrong</h1>
      <p className="max-w-md text-fg-muted">This screen hit an unexpected error. Trying again usually helps.</p>
      <Button onClick={retry}>Try again</Button>
    </main>
  );
}
