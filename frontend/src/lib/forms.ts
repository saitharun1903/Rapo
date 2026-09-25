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

/** Only same-site paths, so a crafted ?next= cannot send the user to another site after signing in. */
export function safeNextPath(next: string | null): string | null {
  return next !== null && next.startsWith("/") && !next.startsWith("//") && !next.startsWith("/\\") ? next : null;
}
