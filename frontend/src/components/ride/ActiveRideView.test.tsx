import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { api } from "@/lib/api/client";
import type { RideResponse } from "@/lib/api/types";
import { json, ride } from "@/test/fixtures";
import { ActiveRideView } from "./ActiveRideView";

vi.mock("@/lib/api/client", async (importOriginal) => ({
  ...await importOriginal<typeof import("@/lib/api/client")>(),
  api: { GET: vi.fn(), POST: vi.fn() },
}));

// Pushes are not under test here: the hooks get their REST snapshots only.
vi.mock("@/lib/realtime/RealtimeProvider", () => ({
  REALTIME_SNAPSHOT: { realtime: true },
  useRealtimeSubscription: () => undefined,
}));

const mapProps = vi.hoisted(() => ({ last: null as Record<string, unknown> | null }));
vi.mock("@/components/map/LazyMap", () => ({
  LazyMap: (props: Record<string, unknown>) => {
    mapProps.last = props;
    return null;
  },
}));

const get = vi.mocked(api.GET);

const DRIVER: NonNullable<RideResponse["driver"]> = {
  id: "d1",
  fullName: "Ravi Kumar",
  ratingAvg: 4.8,
  ratingCount: 112,
  vehicle: { make: "Maruti", model: "Swift", color: "White", plateNumber: "TS09AB1234", category: "ECONOMY" },
};

function reply(body: unknown) {
  return Promise.resolve({ data: body, error: undefined, response: json(body, 200) });
}

function answerByPath(tracking: unknown) {
  get.mockImplementation(((path: string) => {
    switch (path) {
      case "/api/rides/{rideId}/tracking": return reply(tracking);
      case "/api/rides/{rideId}/messages": return reply([]);
      case "/api/drivers/nearby": return reply([{ position: { lat: 17.44, lng: 78.38 }, category: "ECONOMY" }]);
      case "/api/geo/route": return reply({ path: [], distanceMeters: 0, durationSeconds: 0, source: "ROUTED" });
      default: throw new Error(`Unexpected GET ${path}`);
    }
  }) as never);
}

function renderRide(value: RideResponse) {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(<QueryClientProvider client={client}><ActiveRideView ride={value} /></QueryClientProvider>);
}

beforeEach(() => {
  get.mockReset();
  mapProps.last = null;
});

describe("ActiveRideView", () => {
  it("while matching, says how wide the search is and pulses the pickup on the map", async () => {
    answerByPath(null);
    renderRide(ride({ status: "MATCHING", matching: { round: 2, radiusMeters: 4500 } }));

    expect(screen.getByRole("heading", { name: "Finding your ride" })).toBeInTheDocument();
    expect(screen.getByText("Asking drivers within 4.5 km")).toBeInTheDocument();
    expect(await screen.findByText(/1 car online nearby/)).toBeInTheDocument();
    expect(mapProps.last?.searching).toBe(true);
    expect(screen.getByRole("button", { name: "Cancel ride" })).toBeInTheDocument();
    // No driver yet: nobody to message.
    expect(screen.queryByRole("button", { name: /^Message/ })).not.toBeInTheDocument();
  });

  it("while the driver approaches, shows the ETA to the pickup, the driver and a way to message them", async () => {
    answerByPath({
      rideId: "r1", stale: false,
      driverLocation: { point: { lat: 17.45, lng: 78.39 }, headingDeg: 90, recordedAt: "2026-09-25T10:01:00Z" },
      eta: { target: "PICKUP", seconds: 240, distanceMeters: 1200, source: "ROUTED", computedAt: "2026-09-25T10:01:00Z" },
    });
    renderRide(ride({ status: "DRIVER_ARRIVING", driver: DRIVER }));

    expect(screen.getByRole("heading", { name: "Your driver is on the way" })).toBeInTheDocument();
    expect(await screen.findByText("4 min")).toBeInTheDocument();
    expect(screen.getByText(/to your pickup · 1.2 km/)).toBeInTheDocument();
    expect(screen.getByText("Ravi Kumar")).toBeInTheDocument();
    expect(screen.getByLabelText("Verified driver")).toBeInTheDocument();
    expect(screen.getByText("TS09AB1234")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Message Ravi" })).toBeInTheDocument();
    expect(mapProps.last?.searching).toBe(false);
  });

  it("when the driver is here, puts the plate to match first", async () => {
    answerByPath({ rideId: "r1", stale: false, driverLocation: null, eta: null });
    renderRide(ride({ status: "DRIVER_ARRIVED", driver: DRIVER }));

    expect(screen.getByRole("heading", { name: "Your driver is here" })).toBeInTheDocument();
    expect(screen.getByText("Look for the white Maruti Swift.")).toBeInTheDocument();
    const plateRow = screen.getByText("Match the plate").parentElement!;
    expect(within(plateRow).getByText("TS09AB1234")).toBeInTheDocument();
  });

  it("on the trip, shows the destination, the ETA and how much of the trip is done, and no cancel", async () => {
    answerByPath({
      rideId: "r1", stale: false,
      driverLocation: { point: { lat: 17.43, lng: 78.42 }, headingDeg: 90, recordedAt: "2026-09-25T10:10:00Z" },
      eta: { target: "DROPOFF", seconds: 600, distanceMeters: 2500, source: "ROUTED", computedAt: "2026-09-25T10:10:00Z" },
    });
    renderRide(ride({
      status: "IN_PROGRESS", driver: DRIVER,
      estimate: { distanceMeters: 10_000, durationSeconds: 1200, source: "ROUTED", fare: { amount: "180.00", currency: "INR" }, breakdown: null },
    }));

    expect(screen.getByRole("heading", { name: "On the way" })).toBeInTheDocument();
    expect(screen.getByText("To Dropoff")).toBeInTheDocument();
    const progress = await screen.findByRole("progressbar", { name: "Trip progress" });
    expect(progress).toHaveAttribute("aria-valuenow", "75");
    expect(screen.getByText("2.5 km to go of 10.0 km")).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Cancel ride" })).not.toBeInTheDocument();
  });

  it("asks before cancelling and says what cancelling does", async () => {
    answerByPath({ rideId: "r1", stale: false, driverLocation: null, eta: null });
    renderRide(ride({ status: "DRIVER_ASSIGNED", driver: DRIVER }));

    await userEvent.click(screen.getByRole("button", { name: "Cancel ride" }));

    const dialog = screen.getByRole("dialog", { name: "Cancel this ride?" });
    expect(within(dialog).getByText(/Your driver will be released/)).toBeInTheDocument();
  });
});
