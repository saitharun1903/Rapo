import type { FieldValues, Path, UseFormSetError } from "react-hook-form";
import { ApiError } from "@/lib/api/errors";

/**
 * Puts the backend's field errors (VALIDATION_FAILED) on the matching form fields. Returns false when the
 * error was not field-specific, so the caller shows it another way.
 */
export function applyFieldErrors<T extends FieldValues>(error: unknown, setError: UseFormSetError<T>,
                                                        fields: readonly Path<T>[]): boolean {
  if (!(error instanceof ApiError) || error.fieldErrors.length === 0) {
    return false;
  }
  let applied = false;
  for (const { field, message } of error.fieldErrors) {
    const match = fields.find((name) => name === field);
    if (match) {
      setError(match, { type: "server", message });
      applied = true;
    }
  }
  return applied;
}

/** Any origin will do: only whether a path stays on it matters. */
const SAME_SITE = "https://rideflow.invalid";

/**
 * Only same-site paths, so a crafted ?next= cannot send the user to another site after signing in. The path is
 * resolved the way the browser will resolve it, which also drops tabs and newlines: a slash, a tab, then
 * "/evil.example" becomes "//evil.example", another host.
 */
export function safeNextPath(next: string | null): string | null {
  if (next === null || !next.startsWith("/")) {
    return null;
  }
  let url: URL;
  try {
    url = new URL(next, SAME_SITE);
  } catch {
    return null;
  }
  return url.origin === SAME_SITE ? `${url.pathname}${url.search}${url.hash}` : null;
}
