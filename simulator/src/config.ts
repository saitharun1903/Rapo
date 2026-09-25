/**
 * Simulator settings, all from the environment. The only secret is DEMO_USER_PASSWORD, the password the
 * backend's demo profile gave the seeded accounts; it is never logged.
 */

/** Seeded, verified drivers (backend db/seed/R__demo_seed.sql). */
const DEFAULT_DRIVERS = [
  "driver.arjun@rideflow.example.com",
  "driver.farhan@rideflow.example.com",
  "driver.lakshmi@rideflow.example.com",
  "driver.vikram@rideflow.example.com",
  "driver.sneha@rideflow.example.com",
];
/** Seeded passengers, used only by --trips. */
const DEFAULT_PASSENGERS = ["ananya@rideflow.example.com", "rahul@rideflow.example.com", "meera@rideflow.example.com"];

function text(name: string, fallback?: string): string {
  const value = process.env[name];
  if (value !== undefined && value !== "") {
    return value;
  }
  if (fallback === undefined) {
    throw new Error(`Set ${name}`);
  }
  return fallback;
}

function number(name: string, fallback: number): number {
  const raw = process.env[name];
  if (raw === undefined || raw === "") {
    return fallback;
  }
  const value = Number(raw);
  if (!Number.isFinite(value)) {
    throw new Error(`${name} must be a number, got '${raw}'`);
  }
  return value;
}

function list(name: string, fallback: string[]): string[] {
  const raw = process.env[name];
  return raw === undefined || raw.trim() === "" ? fallback : raw.split(",").map((item) => item.trim()).filter(Boolean);
}

export type Config = ReturnType<typeof loadConfig>;

export function loadConfig() {
  return {
    apiBaseUrl: text("API_BASE_URL", "http://localhost:8080").replace(/\/+$/, ""),
    wsUrl: text("WS_URL", "ws://localhost:8080/ws"),
    password: text("DEMO_USER_PASSWORD"),
    drivers: list("SIM_DRIVERS", DEFAULT_DRIVERS),
    passengers: list("SIM_PASSENGERS", DEFAULT_PASSENGERS),
    /** Drivers start at random points within this radius of the centre (Hyderabad by default). */
    center: { lat: number("SIM_CENTER_LAT", 17.385), lng: number("SIM_CENTER_LNG", 78.4867) },
    startRadiusMeters: number("SIM_START_RADIUS_METERS", 3_000),
    /** About 40 km/h in city traffic. */
    speedMps: number("SIM_SPEED_MPS", 11),
    /** Report interval while moving and while waiting; the server accepts one report per second. */
    reportIntervalMs: number("SIM_REPORT_INTERVAL_MS", 2_000),
    acceptDelayMs: number("SIM_ACCEPT_DELAY_MS", 2_000),
    /** How long the "passenger" takes to get in at the pickup. */
    boardingMs: number("SIM_BOARDING_MS", 5_000),
    /** Passenger trips (--trips N) are booked this far from the centre at most. */
    tripRadiusMeters: number("SIM_TRIP_RADIUS_METERS", 6_000),
  };
}
