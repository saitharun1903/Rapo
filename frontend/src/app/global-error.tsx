"use client";

import * as Sentry from "@sentry/nextjs";
import { useEffect } from "react";
import { Button } from "@/components/ui/button";
import "./globals.css";

/** Errors in the root layout itself, which error.tsx cannot catch. Replaces the whole document. */
export default function GlobalError({ error, retry }: { error: Error & { digest?: string }; retry: () => void }) {
  useEffect(() => {
    console.error(error);
    // A no-op unless error reporting is configured (NEXT_PUBLIC_SENTRY_DSN).
    Sentry.captureException(error);
  }, [error]);
  return (
    <html lang="en">
      <body className="font-sans">
        <main role="alert" className="flex min-h-screen flex-col items-center justify-center gap-4 px-4 text-center">
          <title>Something went wrong · RideFlow</title>
          <h1 className="text-2xl font-bold">Something went wrong</h1>
          <p className="max-w-md text-fg-muted">RideFlow could not load. Trying again usually helps.</p>
          <Button onClick={retry}>Try again</Button>
        </main>
      </body>
    </html>
  );
}
