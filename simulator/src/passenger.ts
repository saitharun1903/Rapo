import { ApiClient } from "./api.ts";
import type { Config } from "./config.ts";
import { distanceMeters, randomPointNear, type Point } from "./geo.ts";
import { log, sleep } from "./log.ts";

const MIN_TRIP_METERS = 1_500;
const POLL_MS = 5_000;
const TRIP_TIMEOUT_MS = 20 * 60_000;
const COORDINATE_DECIMALS = 5;
const TERMINAL = new Set(["COMPLETED", "CANCELLED", "EXPIRED"]);
const PAYMENT_METHODS = ["CASH", "CARD"] as const;

type Quote = { quoteId: string; vehicleCategory: string };
type Estimate = { quotes: Quote[] };
type Ride = { id: string; status: string };
type Place = { address: string };

async function describe(api: ApiClient, point: Point): Promise<{ point: Point; address: string }> {
  const place = await api.call<Place>("GET", `/api/geo/reverse?lat=${point.lat}&lng=${point.lng}`);
  return { point, address: place?.address ?? `${point.lat.toFixed(COORDINATE_DECIMALS)}, ${point.lng.toFixed(COORDINATE_DECIMALS)}` };
}

/**
 * Books `count` real rides as seeded passengers, one after another, and waits for each to finish, so that
 * trip history, earnings, analytics and AI comparisons have genuine data behind them.
 */
export async function runTrips(config: Config, count: number): Promise<void> {
  const passengers = config.passengers.map((email) => new ApiClient(config.apiBaseUrl, email, config.password));
  await Promise.all(passengers.map((passenger) => passenger.signIn()));
  for (let trip = 0; trip < count; trip++) {
    const passenger = passengers[trip % passengers.length];
    const actor = passenger.email.split("@")[0];
    const pickupPoint = randomPointNear(config.center, config.tripRadiusMeters);
    let dropoffPoint = randomPointNear(config.center, config.tripRadiusMeters);
    while (distanceMeters(pickupPoint, dropoffPoint) < MIN_TRIP_METERS) {
      dropoffPoint = randomPointNear(config.center, config.tripRadiusMeters);
    }
    const pickup = await describe(passenger, pickupPoint);
    const dropoff = await describe(passenger, dropoffPoint);
    const estimate = await passenger.post<Estimate>("/api/fares/estimate", { pickup: pickupPoint, dropoff: dropoffPoint });
    const quote = estimate.quotes.find((candidate) => candidate.vehicleCategory === "ECONOMY") ?? estimate.quotes[0];
    const ride = await passenger.post<Ride>("/api/rides", {
      quoteId: quote.quoteId, pickup, dropoff, paymentMethod: PAYMENT_METHODS[trip % PAYMENT_METHODS.length],
    });
    log(actor, `trip ${trip + 1}/${count}: booked ride ${ride.id} (${quote.vehicleCategory})`);
    const deadline = Date.now() + TRIP_TIMEOUT_MS;
    let status = ride.status;
    while (!TERMINAL.has(status)) {
      if (Date.now() > deadline) {
        throw new Error(`Ride ${ride.id} did not finish within ${TRIP_TIMEOUT_MS / 60_000} minutes (last status ${status})`);
      }
      await sleep(POLL_MS);
      status = (await passenger.get<Ride>(`/api/rides/${ride.id}`)).status;
    }
    log(actor, `trip ${trip + 1}/${count}: ride ${ride.id} ${status}`);
  }
}
