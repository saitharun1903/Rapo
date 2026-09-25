import * as Sentry from "@sentry/nextjs";
import { sentry } from "@/lib/monitoring/sentry";

// Runs before hydration, so errors during the first render are reported too.
if (sentry) {
  Sentry.init(sentry);
}
