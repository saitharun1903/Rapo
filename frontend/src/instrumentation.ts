import * as Sentry from "@sentry/nextjs";
import { sentry } from "@/lib/monitoring/sentry";

/** Server-side error reporting: server rendering and the /api rewrite run here. */
export function register() {
  if (sentry) {
    Sentry.init(sentry);
  }
}

// A no-op when Sentry was not initialised.
export const onRequestError = Sentry.captureRequestError;
