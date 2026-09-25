import type { AuthResponse, RideResponse } from "@/lib/api/types";

/** A ride as the API returns it, with only what a test cares about overridden. */
export function ride(overrides: Partial<RideResponse> = {}): RideResponse {
  return {
    id: "r1",
    status: "MATCHING",
    version: 1,
    vehicleCategory: "ECONOMY",
    pickup: { point: { lat: 17.44, lng: 78.38 }, address: "Pickup" },
    dropoff: { point: { lat: 17.42, lng: 78.47 }, address: "Dropoff" },
    paymentMethod: "CASH",
    estimate: { distanceMeters: 1000, durationSeconds: 300, source: "ROUTED", fare: null, breakdown: null },
    matching: { round: 1, radiusMeters: 3000 },
    timestamps: { requestedAt: "2026-09-25T10:00:00Z" },
    ...overrides,
  };
}

const DEFAULT_EXPIRES_IN_SECONDS = 900;

export function auth(token: string, expiresIn = DEFAULT_EXPIRES_IN_SECONDS): AuthResponse {
  return {
    accessToken: token,
    tokenType: "Bearer",
    expiresIn,
    user: { id: "u1", email: "a@example.com", fullName: "Asha", role: "PASSENGER", status: "ACTIVE", createdAt: "2026-09-01T00:00:00Z" },
  };
}

export function json(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), { status, headers: { "Content-Type": "application/json" } });
}
