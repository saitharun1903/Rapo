import { describe, expect, it, vi } from "vitest";
import { SessionStore } from "@/lib/auth/session";
import { auth, json } from "@/test/fixtures";
import { createAuthenticatedFetch, createRefresher, REFRESH_MARGIN_MS } from "./http";

const NOW = 1_000_000;
const EXPIRES_IN_SECONDS = 900;
const MS_PER_SECOND = 1_000;

function signedInStore(token = "old"): SessionStore {
  const store = new SessionStore(() => NOW);
  store.signedIn(auth(token));
  return store;
}

describe("createRefresher", () => {
  it("shares one refresh request between concurrent callers", async () => {
    const store = signedInStore();
    let release: (response: Response) => void = () => {};
    const fetchImpl = vi.fn(() => new Promise<Response>((resolve) => { release = resolve; }));
    const refresh = createRefresher("", fetchImpl, store);

    const first = refresh();
    const second = refresh();
    release(json(auth("new")));

    await expect(Promise.all([first, second])).resolves.toEqual([true, true]);
    expect(fetchImpl).toHaveBeenCalledTimes(1);
    expect(store.accessToken()).toBe("new");
  });

  it("takes turns with other tabs through a Web Lock", async () => {
    const request = vi.fn((_name: string, task: () => Promise<boolean>) => task());
    vi.stubGlobal("navigator", { ...navigator, locks: { request } });
    try {
      const store = signedInStore();
      await expect(createRefresher("", vi.fn(async () => json(auth("new"))), store)()).resolves.toBe(true);
      expect(request).toHaveBeenCalledWith("rideflow-token-refresh", expect.any(Function));
    } finally {
      vi.unstubAllGlobals();
    }
  });

  it("sends the CSRF header and signs out when the cookie is rejected", async () => {
    const store = signedInStore();
    const fetchImpl = vi.fn(async () => json({ code: "SESSION_REVOKED", message: "revoked" }, 401));
    await expect(createRefresher("", fetchImpl, store)()).resolves.toBe(false);

    expect(fetchImpl).toHaveBeenCalledWith("/api/auth/refresh", expect.objectContaining({
      method: "POST", headers: { "X-Requested-With": "rideflow" }, credentials: "same-origin",
    }));
    expect(store.get().status).toBe("anonymous");
  });

  it("keeps the session when offline, but ends the initial loading state", async () => {
    const offline = vi.fn(async () => { throw new TypeError("Failed to fetch"); });
    const signedIn = signedInStore();
    await expect(createRefresher("", offline, signedIn)()).resolves.toBe(false);
    expect(signedIn.get().status).toBe("authenticated");

    const loading = new SessionStore(() => NOW);
    await createRefresher("", offline, loading)();
    expect(loading.get().status).toBe("anonymous");
  });
});

describe("createAuthenticatedFetch", () => {
  it("adds the bearer token", async () => {
    const store = signedInStore("token-1");
    const fetchImpl = vi.fn(async (request: Request) => json({ auth: request.headers.get("Authorization") }));
    const response = await createAuthenticatedFetch(fetchImpl, store, vi.fn())(new Request("http://app/api/users/me"));
    await expect(response.json()).resolves.toEqual({ auth: "Bearer token-1" });
  });

  it("refreshes after a 401 and repeats the request once with the new token and the same body", async () => {
    const store = signedInStore("expired");
    const seen: { auth: string | null; body: string }[] = [];
    const fetchImpl = vi.fn(async (request: Request) => {
      seen.push({ auth: request.headers.get("Authorization"), body: await request.text() });
      return seen.length === 1 ? json({ code: "INVALID_TOKEN", message: "expired" }, 401) : json({ ok: true });
    });
    const refresh = vi.fn(async () => {
      store.signedIn(auth("fresh"));
      return true;
    });

    const response = await createAuthenticatedFetch(fetchImpl, store, refresh)(
      new Request("http://app/api/rides", { method: "POST", body: "{\"quoteId\":\"q\"}" }));

    expect(response.status).toBe(200);
    expect(refresh).toHaveBeenCalledTimes(1);
    expect(seen).toEqual([
      { auth: "Bearer expired", body: "{\"quoteId\":\"q\"}" },
      { auth: "Bearer fresh", body: "{\"quoteId\":\"q\"}" },
    ]);
  });

  it("returns the 401 when the refresh fails", async () => {
    const store = signedInStore();
    const fetchImpl = vi.fn(async () => json({ code: "INVALID_TOKEN", message: "expired" }, 401));
    const response = await createAuthenticatedFetch(fetchImpl, store, vi.fn(async () => false))(new Request("http://app/api/rides"));
    expect(response.status).toBe(401);
    expect(fetchImpl).toHaveBeenCalledTimes(1);
  });

  it("refreshes before sending when the token is about to expire", async () => {
    let now = NOW;
    const store = new SessionStore(() => now);
    store.signedIn(auth("old"));
    now += EXPIRES_IN_SECONDS * MS_PER_SECOND - REFRESH_MARGIN_MS + 1;
    const refresh = vi.fn(async () => {
      store.signedIn(auth("fresh"));
      return true;
    });
    const fetchImpl = vi.fn(async (request: Request) => json({ auth: request.headers.get("Authorization") }));

    const response = await createAuthenticatedFetch(fetchImpl, store, refresh)(new Request("http://app/api/rides/active"));

    expect(refresh).toHaveBeenCalledTimes(1);
    await expect(response.json()).resolves.toEqual({ auth: "Bearer fresh" });
  });

  it("leaves auth endpoints alone", async () => {
    const store = signedInStore();
    const fetchImpl = vi.fn(async () => json({ code: "INVALID_CREDENTIALS", message: "no" }, 401));
    const refresh = vi.fn();
    const response = await createAuthenticatedFetch(fetchImpl, store, refresh)(new Request("http://app/api/auth/login", { method: "POST" }));
    expect(response.status).toBe(401);
    expect(refresh).not.toHaveBeenCalled();
    const sent = (fetchImpl.mock.calls[0] as unknown as [Request])[0];
    expect(sent.headers.get("Authorization")).toBeNull();
  });
});
