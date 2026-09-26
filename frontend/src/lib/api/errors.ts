import type { ApiErrorBody } from "./types";

const NETWORK_STATUS = 0;
const SERVER_ERROR = 500;
const SECONDS_TO_MS = 1000;

/**
 * A failed API call. `code` is the backend's stable error code (docs/api.md), which is what the UI
 * branches on; `message` is safe to show. Network failures have status 0 and code NETWORK_ERROR.
 */
export class ApiError extends Error {
  readonly status: number;
  readonly code: string;
  readonly fieldErrors: { field: string; message: string }[];
  /** From the Retry-After header of 429 and 503 responses. */
  readonly retryAfterMs: number | null;

  constructor(status: number, code: string, message: string,
              fieldErrors: { field: string; message: string }[] = [], retryAfterMs: number | null = null) {
    super(message);
    this.name = "ApiError";
    this.status = status;
    this.code = code;
    this.fieldErrors = fieldErrors;
    this.retryAfterMs = retryAfterMs;
  }

  static network(cause: unknown): ApiError {
    const error = new ApiError(NETWORK_STATUS, "NETWORK_ERROR",
      "Cannot reach Raido right now. Check your connection and try again.");
    error.cause = cause;
    return error;
  }

  static async fromResponse(response: Response, body?: unknown): Promise<ApiError> {
    const parsed = isApiErrorBody(body) ? body : await readBody(response);
    const retryAfter = retryAfterMs(response.headers.get("Retry-After"));
    if (parsed) {
      return new ApiError(response.status, parsed.code, parsed.message, parsed.fieldErrors ?? [], retryAfter);
    }
    // No Raido error body: the answer came from something in between, such as the proxy failing to reach
    // the backend.
    if (response.status >= SERVER_ERROR) {
      return new ApiError(response.status, "SERVER_UNAVAILABLE",
        "Raido is not responding right now. Please try again in a moment.", [], retryAfter);
    }
    return new ApiError(response.status, "UNEXPECTED_RESPONSE",
      `The server answered ${response.status}. Please try again.`, [], retryAfter);
  }
}

export function isApiError(error: unknown, ...codes: string[]): error is ApiError {
  return error instanceof ApiError && (codes.length === 0 || codes.includes(error.code));
}

/** A message for the user, whatever was thrown. */
export function errorMessage(error: unknown): string {
  if (error instanceof ApiError) {
    return error.message;
  }
  return "Something went wrong. Please try again.";
}

function isApiErrorBody(value: unknown): value is ApiErrorBody {
  return typeof value === "object" && value !== null
    && typeof (value as ApiErrorBody).code === "string"
    && typeof (value as ApiErrorBody).message === "string";
}

async function readBody(response: Response): Promise<ApiErrorBody | null> {
  try {
    const body: unknown = await response.clone().json();
    return isApiErrorBody(body) ? body : null;
  } catch {
    // Not JSON (for example a proxy's HTML error page): the caller falls back to a generic message.
    return null;
  }
}

function retryAfterMs(header: string | null): number | null {
  if (header === null) {
    return null;
  }
  const seconds = Number(header);
  return Number.isFinite(seconds) && seconds >= 0 ? seconds * SECONDS_TO_MS : null;
}
