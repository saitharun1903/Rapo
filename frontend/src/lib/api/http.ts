import type { SessionStore } from "@/lib/auth/session";
import type { AuthResponse } from "./types";

/** Refresh a little before expiry, so a request never leaves with a token that dies in flight. */
export const REFRESH_MARGIN_MS = 30_000;
const UNAUTHORIZED = 401;
/** Required by the backend on the cookie-authenticated endpoints (its CSRF defence). */
export const CSRF_HEADER = { "X-Requested-With": "rideflow" } as const;
const AUTH_PATH = "/api/auth/";

type Fetch = (request: Request) => Promise<Response>;

export type Refresher = () => Promise<boolean>;

/**
 * Exchanges the HttpOnly refresh cookie for a new access token. Concurrent callers share one request:
 * the backend rotates refresh tokens and treats reuse of a rotated one as theft, so two parallel
 * refreshes would sign the user out.
 */
export function createRefresher(baseUrl: string, fetchImpl: typeof fetch, store: SessionStore): Refresher {
  let inFlight: Promise<boolean> | null = null;

  async function refresh(): Promise<boolean> {
    let response: Response;
    try {
      response = await fetchImpl(`${baseUrl}${AUTH_PATH}refresh`, {
        method: "POST",
        headers: CSRF_HEADER,
        credentials: "same-origin",
      });
    } catch {
      // Offline: keep whatever session there is; the next request will try again.
      if (store.get().status === "loading") {
        store.signedOut();
      }
      return false;
    }
    if (!response.ok) {
      store.signedOut();
      return false;
    }
    store.signedIn((await response.json()) as AuthResponse);
    return true;
  }

  return () => {
    inFlight ??= refresh().finally(() => {
      inFlight = null;
    });
    return inFlight;
  };
}

/**
 * fetch for the typed API client: adds the access token, refreshes it shortly before expiry, and after a
 * 401 refreshes once and repeats the request. Auth endpoints are sent as they are.
 */
export function createAuthenticatedFetch(fetchImpl: Fetch, store: SessionStore, refresh: Refresher): Fetch {
  const send = (request: Request): Promise<Response> => {
    const token = store.accessToken();
    if (token === null) {
      return fetchImpl(request);
    }
    const headers = new Headers(request.headers);
    headers.set("Authorization", `Bearer ${token}`);
    return fetchImpl(new Request(request, { headers }));
  };

  return async (request: Request) => {
    if (new URL(request.url, "http://relative").pathname.startsWith(AUTH_PATH)) {
      return fetchImpl(request);
    }
    if (store.get().status === "authenticated" && store.expiresWithin(REFRESH_MARGIN_MS)) {
      await refresh();
    }
    const retry = request.clone();
    const response = await send(request);
    if (response.status !== UNAUTHORIZED || store.get().status !== "authenticated") {
      return response;
    }
    return (await refresh()) ? send(retry) : response;
  };
}
