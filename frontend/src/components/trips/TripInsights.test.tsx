import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { api } from "@/lib/api/client";
import type { TripAnalysis } from "@/lib/api/types";
import { json } from "@/test/fixtures";
import { TripInsightsPanel } from "./TripInsightsPanel";
import { TripQuestions } from "./TripQuestions";

vi.mock("@/lib/api/client", async (importOriginal) => ({
  ...await importOriginal<typeof import("@/lib/api/client")>(),
  api: { GET: vi.fn(), POST: vi.fn() },
}));

const get = vi.mocked(api.GET);

function analysis(overrides: Partial<TripAnalysis>): TripAnalysis {
  return {
    rideId: "r1", status: "UNAVAILABLE", failureCode: "UNAVAILABLE", insights: null,
    observations: [{ key: "fare.minimum", text: "The minimum fare of 80.00 INR applied." }],
    provider: "disabled", model: "none", promptVersion: "trip-analysis/v1", updatedAt: "2026-09-26T10:00:00Z",
    ...overrides,
  } as TripAnalysis;
}

function answer(value: TripAnalysis) {
  get.mockImplementation(((path: string) => {
    const body = path.endsWith("/questions") ? [] : value;
    return Promise.resolve({ data: body, error: undefined, response: json(body) });
  }) as never);
}

function renderWith(node: React.ReactNode) {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(<QueryClientProvider client={client}>{node}</QueryClientProvider>);
}

// A block body: a function returned from beforeEach would be run as a teardown hook.
beforeEach(() => {
  get.mockReset();
});

describe("trip insights with AI switched off", () => {
  it("says so plainly, shows the trip's own observations and offers no retry that could only fail", async () => {
    answer(analysis({}));
    renderWith(<TripInsightsPanel rideId="r1" />);

    expect(await screen.findByText(/switched off on this Raido deployment/)).toBeInTheDocument();
    expect(screen.getByText("The minimum fare of 80.00 INR applied.")).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: /Try again/ })).not.toBeInTheDocument();
  });

  it("replaces the question box with an explanation", async () => {
    answer(analysis({}));
    renderWith(<TripQuestions rideId="r1" />);

    expect(await screen.findByText(/AI assistant, which is switched off/)).toBeInTheDocument();
    expect(screen.queryByLabelText("Your question")).not.toBeInTheDocument();
  });
});

describe("trip insights when AI is on but unavailable for now", () => {
  it("offers to try again, and keeps the question box", async () => {
    answer(analysis({ provider: "local", model: "llama3.2" }));
    renderWith(<><TripInsightsPanel rideId="r1" /><TripQuestions rideId="r1" /></>);

    expect(await screen.findByRole("button", { name: /Try again/ })).toBeInTheDocument();
    expect(await screen.findByLabelText("Your question")).toBeInTheDocument();
  });
});
