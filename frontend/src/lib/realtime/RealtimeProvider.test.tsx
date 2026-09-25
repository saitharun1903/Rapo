import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { act, render, screen, waitFor } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { refreshSession } from "@/lib/api/client";
import { session } from "@/lib/auth/session";
import { auth, ride } from "@/test/fixtures";
import { REALTIME_SNAPSHOT, RealtimeProvider, useRealtime, useRealtimeSubscription } from "./RealtimeProvider";
import { Destinations } from "./types";

/** Stands in for @stomp/stompjs's Client: the test decides when a socket connects and when it drops. */
const { FakeClient } = vi.hoisted(() => {
  type Frame = { body: string };
  class FakeClient {
    static instances: FakeClient[] = [];
    connected = false;
    active = false;
    connectHeaders: Record<string, string> = {};
    beforeConnect: () => Promise<void> = async () => {};
    onConnect: () => void = () => {};
    onWebSocketClose: (event: { code: number }) => void = () => {};
    onStompError: (frame: { headers: Record<string, string> }) => void = () => {};
    subscriptions: { destination: string; callback: (frame: Frame) => void; unsubscribe: () => void }[] = [];
    published: { destination: string; body: string }[] = [];
    /** Resolves once beforeConnect has finished for the current attempt. */
    ready: Promise<void> = Promise.resolve();

    constructor() {
      FakeClient.instances.push(this);
    }

    static latest(): FakeClient {
      return FakeClient.instances[FakeClient.instances.length - 1];
    }

    activate(): void {
      this.active = true;
      this.ready = this.beforeConnect();
    }

    async deactivate(): Promise<void> {
      this.active = false;
      this.connected = false;
    }

    /** The server accepted CONNECT. */
    establish(): void {
      this.connected = true;
      this.onConnect();
    }

    /** The socket closed; stompjs then retries through beforeConnect while active. */
    drop(code: number): void {
      this.connected = false;
      this.onWebSocketClose({ code });
      if (this.active) {
        this.ready = this.beforeConnect();
      }
    }

    subscribe(destination: string, callback: (frame: Frame) => void) {
      const subscription = { destination, callback, unsubscribe: () => {} };
      this.subscriptions.push(subscription);
      return subscription;
    }

    publish(message: { destination: string; body: string }): void {
      this.published.push(message);
    }

    deliver(destination: string, body: string): void {
      this.subscriptions.filter((subscription) => subscription.destination === destination)
        .forEach((subscription) => subscription.callback({ body }));
    }
  }
  return { FakeClient };
});

vi.mock("@stomp/stompjs", () => ({ Client: FakeClient }));
vi.mock("@/lib/api/client", () => ({ refreshSession: vi.fn() }));

const received = vi.fn();
const sent = vi.fn();

function Listener() {
  const { state, sendLocation } = useRealtime();
  useRealtimeSubscription(Destinations.rides, received);
  return (
    <>
      <p data-testid="state">{state}</p>
      <button type="button" onClick={() => sent(sendLocation({
        location: { lat: 17.4, lng: 78.4 }, headingDeg: null, speedMps: null, accuracyMeters: null, recordedAt: "2026-09-25T10:00:00Z",
      }))}>send</button>
    </>
  );
}

function renderProvider() {
  const queryClient = new QueryClient();
  const invalidate = vi.spyOn(queryClient, "invalidateQueries");
  render(
    <QueryClientProvider client={queryClient}>
      <RealtimeProvider><Listener /></RealtimeProvider>
    </QueryClientProvider>,
  );
  return { queryClient, invalidate };
}

async function connected(client: InstanceType<typeof FakeClient>): Promise<void> {
  await act(async () => {
    await client.ready;
    client.establish();
  });
}

beforeEach(() => {
  FakeClient.instances = [];
  received.mockReset();
  sent.mockReset();
  vi.mocked(refreshSession).mockReset();
  session.signedIn(auth("token-1"));
  // No jitter: reconnect attempts run at once.
  vi.spyOn(Math, "random").mockReturnValue(0);
});

afterEach(() => {
  session.signedOut();
});

describe("RealtimeProvider", () => {
  it("shows that it is connecting until the server accepts", async () => {
    renderProvider();
    await waitFor(() => expect(screen.getByTestId("state")).toHaveTextContent("connecting"));
  });

  it("connects with the access token and delivers each message to the components listening", async () => {
    renderProvider();
    const client = FakeClient.latest();
    await connected(client);

    expect(client.connectHeaders).toEqual({ Authorization: "Bearer token-1" });
    expect(screen.getByTestId("state")).toHaveTextContent("connected");
    expect(client.subscriptions.map((subscription) => subscription.destination)).toEqual([Destinations.rides]);

    const update = ride({ status: "DRIVER_ASSIGNED", version: 3 });
    act(() => client.deliver(Destinations.rides, JSON.stringify(update)));
    expect(received).toHaveBeenCalledExactlyOnceWith(update);
  });

  it("ignores a message it cannot read instead of failing", async () => {
    const logged = vi.spyOn(console, "error").mockImplementation(() => {});
    renderProvider();
    const client = FakeClient.latest();
    await connected(client);

    act(() => client.deliver(Destinations.rides, "not json"));
    expect(received).not.toHaveBeenCalled();
    expect(logged).toHaveBeenCalledOnce();
  });

  it("after a reconnect subscribes again and refetches the realtime snapshots, but not on the first connect", async () => {
    const { queryClient, invalidate } = renderProvider();
    const client = FakeClient.latest();
    await connected(client);
    expect(invalidate).not.toHaveBeenCalled();

    act(() => client.drop(1006));
    expect(screen.getByTestId("state")).toHaveTextContent("reconnecting");
    await connected(client);

    expect(client.subscriptions.filter((subscription) => subscription.destination === Destinations.rides)).toHaveLength(2);
    expect(invalidate).toHaveBeenCalledOnce();
    const { predicate } = invalidate.mock.calls[0][0] as { predicate: (query: unknown) => boolean };
    const snapshot = queryClient.getQueryCache().build(queryClient, { queryKey: ["snapshot"], meta: REALTIME_SNAPSHOT });
    const other = queryClient.getQueryCache().build(queryClient, { queryKey: ["other"] });
    expect(predicate(snapshot)).toBe(true);
    expect(predicate(other)).toBe(false);
    expect(refreshSession).not.toHaveBeenCalled();
  });

  it("waits a jittered delay before the first retry after a drop, so clients do not all return at once", async () => {
    const JITTERED_DELAY_MS = 999;
    vi.spyOn(Math, "random").mockReturnValue(JITTERED_DELAY_MS / 1_000);
    renderProvider();
    const client = FakeClient.latest();
    await connected(client);

    vi.useFakeTimers({ toFake: ["setTimeout"] });
    try {
      let retried = false;
      act(() => client.drop(1006));
      void client.ready.then(() => { retried = true; });
      await act(() => vi.advanceTimersByTimeAsync(JITTERED_DELAY_MS - 1));
      expect(retried).toBe(false);
      await act(() => vi.advanceTimersByTimeAsync(1));
      expect(retried).toBe(true);
    } finally {
      vi.useRealTimers();
    }
  });

  it("refreshes the token before reconnecting when the server closed the socket because it expired", async () => {
    vi.mocked(refreshSession).mockImplementation(async () => {
      session.signedIn(auth("token-2"));
      return true;
    });
    renderProvider();
    const client = FakeClient.latest();
    await connected(client);

    act(() => client.drop(4001));
    await connected(client);

    expect(refreshSession).toHaveBeenCalledOnce();
    expect(client.connectHeaders).toEqual({ Authorization: "Bearer token-2" });
  });

  it("stops reconnecting once the user has signed out", async () => {
    renderProvider();
    const client = FakeClient.latest();
    await connected(client);

    session.signedOut();
    await act(async () => {
      client.drop(1006);
      await client.ready;
    });

    expect(client.active).toBe(false);
    expect(screen.getByTestId("state")).toHaveTextContent("idle");
  });

  it("sends a location only while connected, so the caller can fall back to REST", async () => {
    renderProvider();
    const client = FakeClient.latest();
    await act(async () => {
      await client.ready;
    });

    act(() => screen.getByRole("button", { name: "send" }).click());
    expect(sent).toHaveBeenLastCalledWith(false);

    await connected(client);
    act(() => screen.getByRole("button", { name: "send" }).click());
    expect(sent).toHaveBeenLastCalledWith(true);
    expect(client.published).toHaveLength(1);
    expect(JSON.parse(client.published[0].body)).toMatchObject({ location: { lat: 17.4, lng: 78.4 } });
    expect(client.published[0].destination).toBe(Destinations.driverLocation);
  });
});

describe("useRealtime", () => {
  it("must be used inside the provider", () => {
    vi.spyOn(console, "error").mockImplementation(() => {});
    expect(() => render(<Listener />)).toThrow("useRealtime must be used inside RealtimeProvider");
  });
});
