import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { toast } from "sonner";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { api } from "@/lib/api/client";
import type { RideResponse } from "@/lib/api/types";
import { queryKeys } from "@/lib/queryKeys";
import { answer } from "@/test/api";
import { ride } from "@/test/fixtures";
import { DriverTripPanel } from "./DriverTripPanel";

vi.mock("@/lib/api/client", async (importOriginal) => ({
  ...await importOriginal<typeof import("@/lib/api/client")>(),
  api: { GET: vi.fn(), POST: vi.fn() },
}));
vi.mock("sonner", () => ({ toast: Object.assign(vi.fn(), { error: vi.fn() }) }));
// The chat's pushes are not under test here.
vi.mock("@/lib/realtime/RealtimeProvider", () => ({
  REALTIME_SNAPSHOT: { realtime: true },
  useRealtimeSubscription: () => undefined,
}));

const post = vi.mocked(api.POST);

function renderPanel(current: RideResponse) {
  const queryClient = new QueryClient();
  render(
    <QueryClientProvider client={queryClient}>
      <DriverTripPanel ride={current} />
    </QueryClientProvider>,
  );
  return queryClient;
}

beforeEach(() => {
  post.mockReset();
  answer(vi.mocked(api.GET), []);
  vi.mocked(toast.error).mockReset();
});

describe("DriverTripPanel", () => {
  it("offers the next step and shows the ride the server returns", async () => {
    const arriving = ride({ status: "DRIVER_ARRIVING", version: 5 });
    answer(post, arriving);
    const queryClient = renderPanel(ride({ status: "DRIVER_ASSIGNED", version: 4 }));

    await userEvent.click(screen.getByRole("button", { name: "Start driving to pickup" }));

    expect(post).toHaveBeenCalledWith("/api/rides/{rideId}/en-route", { params: { path: { rideId: "r1" } } });
    await waitFor(() => expect(queryClient.getQueryData(queryKeys.activeRide)).toEqual(arriving));
  });

  it.each([
    ["DRIVER_ARRIVING", "I have arrived"],
    ["DRIVER_ARRIVED", "Start trip"],
    ["IN_PROGRESS", "Complete trip"],
  ] as const)("in %s the step is '%s'", (status, label) => {
    renderPanel(ride({ status }));
    expect(screen.getByRole("button", { name: label })).toBeInTheDocument();
  });

  it("explains a refused arrival in terms the driver can act on", async () => {
    answer(post, { code: "NOT_AT_PICKUP", message: "Driver is 800 m from the pickup" }, 409);
    renderPanel(ride({ status: "DRIVER_ARRIVING" }));

    await userEvent.click(screen.getByRole("button", { name: "I have arrived" }));

    await waitFor(() => expect(toast.error).toHaveBeenCalledWith(
      "You are not at the pickup point yet. Move closer and try again."));
  });

  it("releases the ride only after confirmation, with a reason for the audit trail", async () => {
    answer(post, ride({ status: "MATCHING" }));
    const queryClient = renderPanel(ride({ status: "DRIVER_ASSIGNED" }));
    const invalidate = vi.spyOn(queryClient, "invalidateQueries");

    await userEvent.click(screen.getByRole("button", { name: "Release this ride" }));
    await userEvent.click(within(screen.getByRole("dialog", { name: "Release this ride?" })).getByRole("button", { name: "Keep ride" }));
    expect(post).not.toHaveBeenCalled();

    await userEvent.click(screen.getByRole("button", { name: "Release this ride" }));
    await userEvent.click(within(screen.getByRole("dialog")).getByRole("button", { name: "Confirm" }));

    expect(post).toHaveBeenCalledWith("/api/rides/{rideId}/cancel",
      { params: { path: { rideId: "r1" } }, body: { reason: "Driver released the ride" } });
    await waitFor(() => expect(invalidate).toHaveBeenCalledWith({ queryKey: queryKeys.activeRide }));
  });

  it("cannot be cancelled once the trip has started", () => {
    renderPanel(ride({ status: "IN_PROGRESS" }));
    expect(screen.queryByRole("button", { name: /Release|did not show up/ })).not.toBeInTheDocument();
  });

  it("sends the driver to the pickup, then to the drop-off, in their navigation app", () => {
    const { unmount } = render(
      <QueryClientProvider client={new QueryClient()}><DriverTripPanel ride={ride({ status: "DRIVER_ASSIGNED" })} /></QueryClientProvider>,
    );
    expect(screen.getByRole("link", { name: /Open directions/ }))
      .toHaveAttribute("href", expect.stringContaining("destination=17.44,78.38"));
    unmount();

    renderPanel(ride({ status: "IN_PROGRESS" }));
    expect(screen.getByText("Drop-off")).toBeInTheDocument();
    expect(screen.getByRole("link", { name: /Open directions/ }))
      .toHaveAttribute("href", expect.stringContaining("destination=17.42,78.47"));
  });
});
