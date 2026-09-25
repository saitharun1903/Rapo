import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { act } from "react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { api } from "@/lib/api/client";
import type { GeoPoint } from "@/lib/api/types";
import { currentPosition } from "@/lib/geolocation";
import { json } from "@/test/fixtures";
import { BookingView } from "./BookingView";

const HERE = { lat: 17.26, lng: 78.36 };
const THERE = { lat: 17.3, lng: 78.4 };

vi.mock("@/lib/api/client", async (importOriginal) => ({
  ...await importOriginal<typeof import("@/lib/api/client")>(),
  api: { GET: vi.fn(), POST: vi.fn() },
}));

vi.mock("@/lib/geolocation", async (importOriginal) => ({
  ...await importOriginal<typeof import("@/lib/geolocation")>(),
  currentPosition: vi.fn(),
}));

// MapLibre needs WebGL; a click on "the map" is a button that picks THERE, present only while picking.
vi.mock("@/components/map/LazyMap", () => ({
  LazyMap: ({ onPick }: { onPick?: (point: GeoPoint) => void }) =>
    onPick ? <button type="button" onClick={() => onPick(THERE)}>Click the stand-in map</button> : null,
}));

const get = vi.mocked(api.GET);
const post = vi.mocked(api.POST);
const position = vi.mocked(currentPosition);

function reply(body: unknown) {
  return Promise.resolve({ data: body, error: undefined, response: json(body, 200) });
}

beforeEach(() => {
  get.mockReset();
  post.mockReset();
  position.mockReset();
  get.mockImplementation(((path: string, options: { params: { query: GeoPoint } }) => path === "/api/geo/reverse"
    ? reply({ point: options.params.query, address: `Address at ${options.params.query.lat}` })
    : reply([])) as unknown as typeof api.GET);
  // The fare estimate is not what these tests are about.
  post.mockImplementation((() => new Promise(() => {})) as unknown as typeof api.POST);
});

describe("BookingView", () => {
  it("keeps choosing the destination on the map when the pickup's location arrives meanwhile", async () => {
    let locate: (fix: GeolocationPosition) => void = () => {};
    position.mockImplementation(() => new Promise((resolve) => { locate = resolve; }));
    render(
      <QueryClientProvider client={new QueryClient()}>
        <BookingView />
      </QueryClientProvider>,
    );

    await userEvent.click(screen.getByRole("button", { name: "Use my location" }));
    const [, destinationOnMap] = screen.getAllByRole("button", { name: "Choose on map" });
    await userEvent.click(destinationOnMap);
    await act(async () => locate({ coords: { latitude: HERE.lat, longitude: HERE.lng } } as GeolocationPosition));

    expect(await screen.findByText(`Address at ${HERE.lat}`)).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Click the map…" })).toHaveAttribute("aria-pressed", "true");

    await userEvent.click(screen.getByRole("button", { name: "Click the stand-in map" }));

    expect(await screen.findByText(`Address at ${THERE.lat}`)).toBeInTheDocument();
    expect(screen.getAllByRole("button", { name: "Choose on map" })).toHaveLength(2);
  });
});
