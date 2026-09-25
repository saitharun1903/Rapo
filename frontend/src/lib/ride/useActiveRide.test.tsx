import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { act, renderHook, waitFor } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { api } from "@/lib/api/client";
import type { RideResponse } from "@/lib/api/types";
import { queryKeys } from "@/lib/queryKeys";
import { json, ride } from "@/test/fixtures";
import { useActiveRide } from "./useActiveRide";

vi.mock("@/lib/api/client", async (importOriginal) => ({
  ...await importOriginal<typeof import("@/lib/api/client")>(),
  api: { GET: vi.fn() },
}));

/** The handler the hook registered for /user/queue/rides, so a test can push to it. */
let push: (ride: RideResponse) => void = () => {};
vi.mock("@/lib/realtime/RealtimeProvider", () => ({
  REALTIME_SNAPSHOT: { realtime: true },
  useRealtimeSubscription: (_: string, handler: (ride: RideResponse) => void) => {
    push = handler;
  },
}));

const NO_CONTENT = 204;
const get = vi.mocked(api.GET);

/** Answers GET /api/rides/active with `active` (204 when null) and GET /api/rides/{id} with `byId`. */
function serve(active: RideResponse | null, byId?: RideResponse) {
  get.mockImplementation(((path: string) => {
    if (path === "/api/rides/active") {
      return Promise.resolve(active === null
        ? { data: undefined, response: new Response(null, { status: NO_CONTENT }) }
        : { data: active, response: json(active) });
    }
    return Promise.resolve({ data: byId, response: json(byId) });
  }) as unknown as typeof api.GET);
}

function renderActiveRide() {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  const hook = renderHook(() => useActiveRide(), {
    wrapper: ({ children }) => <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>,
  });
  return { queryClient, ...hook };
}

beforeEach(() => {
  get.mockReset();
  push = () => {};
});

describe("useActiveRide", () => {
  it("shows the active ride and applies newer pushes, ignoring late ones", async () => {
    serve(ride({ status: "DRIVER_ASSIGNED", version: 3 }));
    const { result } = renderActiveRide();
    await waitFor(() => expect(result.current.active.data?.status).toBe("DRIVER_ASSIGNED"));

    act(() => push(ride({ status: "DRIVER_ARRIVING", version: 4 })));
    expect(result.current.active.data?.status).toBe("DRIVER_ARRIVING");
    act(() => push(ride({ status: "DRIVER_ASSIGNED", version: 3 })));
    expect(result.current.active.data?.status).toBe("DRIVER_ARRIVING");
  });

  it("keeps the ended ride as the outcome when its final push arrives", async () => {
    serve(ride({ status: "IN_PROGRESS", version: 6 }));
    const { result } = renderActiveRide();
    await waitFor(() => expect(result.current.active.data?.status).toBe("IN_PROGRESS"));

    act(() => push(ride({ status: "COMPLETED", version: 7 })));

    expect(result.current.active.data).toBeNull();
    expect(result.current.finished?.status).toBe("COMPLETED");
    act(() => result.current.dismissFinished());
    expect(result.current.finished).toBeNull();
  });

  it("reads the ride once when it disappears without the push announcing its end", async () => {
    serve(ride({ status: "IN_PROGRESS", version: 6 }));
    const { result, queryClient } = renderActiveRide();
    await waitFor(() => expect(result.current.active.data?.status).toBe("IN_PROGRESS"));

    serve(null, ride({ status: "COMPLETED", version: 7 }));
    await act(() => queryClient.refetchQueries({ queryKey: queryKeys.activeRide }));

    await waitFor(() => expect(result.current.finished?.status).toBe("COMPLETED"));
    expect(get).toHaveBeenCalledWith("/api/rides/{rideId}", { params: { path: { rideId: "r1" } } });
  });

  it("does not look the ride up again when the push already showed how it ended", async () => {
    serve(ride({ status: "IN_PROGRESS", version: 6 }));
    const { result, queryClient } = renderActiveRide();
    await waitFor(() => expect(result.current.active.data?.status).toBe("IN_PROGRESS"));

    act(() => push(ride({ status: "CANCELLED", version: 7 })));
    serve(null);
    await act(() => queryClient.refetchQueries({ queryKey: queryKeys.activeRide }));

    expect(result.current.finished?.status).toBe("CANCELLED");
    expect(get).not.toHaveBeenCalledWith("/api/rides/{rideId}", expect.anything());
  });

  it("starts showing a new ride pushed by the server", async () => {
    serve(null);
    const { result } = renderActiveRide();
    await waitFor(() => expect(result.current.active.isSuccess).toBe(true));

    act(() => push(ride({ id: "r2", status: "MATCHING", version: 1 })));
    expect(result.current.active.data?.id).toBe("r2");
  });
});
