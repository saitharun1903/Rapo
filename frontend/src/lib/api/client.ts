import createClient from "openapi-fetch";
import { config } from "@/lib/config";
import { session } from "@/lib/auth/session";
import { ApiError } from "./errors";
import { createAuthenticatedFetch, createRefresher, CSRF_HEADER } from "./http";
import type { paths } from "./schema";
import type { AuthResponse, RegisterRequest } from "./types";

const NO_CONTENT = 204;

/** Shared with the WebSocket client, which needs a fresh token before it (re)connects. */
export const refreshSession = createRefresher(config.apiBaseUrl, (...args) => fetch(...args), session);

/** Typed client for every REST endpoint in docs/openapi.json. */
export const api = createClient<paths>({
  baseUrl: config.apiBaseUrl,
  fetch: createAuthenticatedFetch((request) => fetch(request), session, refreshSession),
});

type Outcome<D> = { data?: D; error?: unknown; response: Response };

/** The response body, or an ApiError for a failed call (including network failures). */
export async function unwrap<D>(pending: Promise<Outcome<D>>): Promise<D> {
  const outcome = await settle(pending);
  if (!outcome.response.ok) {
    throw await ApiError.fromResponse(outcome.response, outcome.error);
  }
  return outcome.data as D;
}

/** Like {@link unwrap}, for endpoints that answer 204 when there is nothing (such as the active ride). */
export async function unwrapOptional<D>(pending: Promise<Outcome<D>>): Promise<D | null> {
  const outcome = await settle(pending);
  if (outcome.response.status === NO_CONTENT) {
    return null;
  }
  if (!outcome.response.ok) {
    throw await ApiError.fromResponse(outcome.response, outcome.error);
  }
  return outcome.data as D;
}

async function settle<D>(pending: Promise<Outcome<D>>): Promise<Outcome<D>> {
  try {
    return await pending;
  } catch (error) {
    throw ApiError.network(error);
  }
}

export async function login(email: string, password: string): Promise<AuthResponse> {
  const auth = await unwrap(api.POST("/api/auth/login", { body: { email, password } }));
  session.signedIn(auth);
  return auth;
}

export async function register(request: RegisterRequest): Promise<AuthResponse> {
  await unwrap(api.POST("/api/auth/register", { body: request }));
  return login(request.email, request.password);
}

/**
 * Revokes the refresh token family server-side and ends the local session either way. Returns whether the
 * server confirmed the revocation (it cannot when offline; the cookie then expires on its own).
 */
export async function logout(): Promise<boolean> {
  try {
    const response = await fetch(`${config.apiBaseUrl}/api/auth/logout`, {
      method: "POST",
      headers: CSRF_HEADER,
      credentials: "same-origin",
    });
    return response.ok;
  } catch {
    return false;
  } finally {
    session.signedOut();
  }
}
