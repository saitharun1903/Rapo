import type { Breadcrumb, ErrorEvent } from "@sentry/nextjs";

/**
 * Sentry settings shared by the browser and the Next server. Without NEXT_PUBLIC_SENTRY_DSN (inlined at build
 * time; a DSN is public by design) nothing is initialised and nothing is sent. Tracing and session replay stay
 * off: the backend's Prometheus metrics cover latency, and replays would record what users type.
 *
 * Scrubbing, on top of sendDefaultPii: false: requests keep their method, path and user agent only (no query
 * string, cookies, body or other headers); the user is reduced to its id; email addresses, JWTs and bearer
 * tokens are masked in messages, exception messages and breadcrumbs; URLs in breadcrumbs lose their query
 * strings, which carry searched addresses.
 */

export const REDACTED = "[redacted]";
const KEPT_HEADERS = new Set(["user-agent"]);
const URL_KEYS = new Set(["url", "from", "to"]);
const SECRETS = [
  /bearer\s+[A-Za-z0-9._~+/=-]+/gi,
  // A JWT: three base64url segments, the first a JSON header ("eyJ" is base64 for '{"').
  /eyJ[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+\.[A-Za-z0-9_-]*/g,
  /[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}/g,
];

export function mask(text: string): string;
export function mask(text: string | undefined): string | undefined;
export function mask(text: string | undefined): string | undefined {
  return text === undefined ? undefined : SECRETS.reduce((masked, secret) => masked.replace(secret, REDACTED), text);
}

export function withoutQuery(url: string): string {
  const query = url.search(/[?#]/);
  return query < 0 ? url : url.slice(0, query);
}

/** Masks every string, however deeply nested: console breadcrumbs keep their arguments in an array. */
function scrubData(value: unknown, key?: string): unknown {
  if (typeof value === "string") {
    return mask(key !== undefined && URL_KEYS.has(key) ? withoutQuery(value) : value);
  }
  if (Array.isArray(value)) {
    return value.map((item) => scrubData(item));
  }
  if (value !== null && typeof value === "object") {
    return Object.fromEntries(Object.entries(value).map(([name, item]) => [name, scrubData(item, name)]));
  }
  return value;
}

export function scrubBreadcrumb(breadcrumb: Breadcrumb): Breadcrumb {
  return {
    ...breadcrumb,
    message: mask(breadcrumb.message),
    data: breadcrumb.data && (scrubData(breadcrumb.data) as Breadcrumb["data"]),
  };
}

export function scrubEvent(event: ErrorEvent): ErrorEvent {
  const { request, user } = event;
  return {
    ...event,
    request: request && {
      method: request.method,
      url: request.url === undefined ? undefined : withoutQuery(request.url),
      headers: request.headers && Object.fromEntries(
        Object.entries(request.headers).filter(([name]) => KEPT_HEADERS.has(name.toLowerCase()))),
    },
    user: user?.id === undefined ? undefined : { id: user.id },
    message: mask(event.message),
    exception: event.exception && {
      ...event.exception,
      values: event.exception.values?.map((value) => ({ ...value, value: mask(value.value) })),
    },
    breadcrumbs: event.breadcrumbs?.map(scrubBreadcrumb),
  };
}

export interface SentrySettings {
  dsn: string;
  environment: string;
  sendDefaultPii: false;
  beforeSend: (event: ErrorEvent) => ErrorEvent;
  beforeBreadcrumb: (breadcrumb: Breadcrumb) => Breadcrumb;
}

const DEFAULT_ENVIRONMENT = "local";

/** `undefined` when no DSN is configured: error reporting is off. */
export function sentrySettings(dsn: string | undefined, environment: string | undefined): SentrySettings | undefined {
  if (!dsn) {
    return undefined;
  }
  return {
    dsn,
    environment: environment || DEFAULT_ENVIRONMENT,
    sendDefaultPii: false,
    beforeSend: scrubEvent,
    beforeBreadcrumb: scrubBreadcrumb,
  };
}

/** The deployment's settings; NEXT_PUBLIC_ variables must be read literally to be inlined. */
export const sentry = sentrySettings(process.env.NEXT_PUBLIC_SENTRY_DSN, process.env.NEXT_PUBLIC_SENTRY_ENVIRONMENT);
