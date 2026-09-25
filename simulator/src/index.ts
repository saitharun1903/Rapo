import { loadConfig } from "./config.ts";
import { DriverBot } from "./driver.ts";
import { logError, log } from "./log.ts";
import { runTrips } from "./passenger.ts";

/**
 * Usage: DEMO_USER_PASSWORD=... node src/index.ts [--trips N]
 * Without --trips the drivers stay online until stopped (Ctrl+C), serving rides booked in the web app.
 * With --trips N, seeded passengers also book N rides, and the simulator exits once they are done.
 */
function tripsArgument(args: string[]): number | null {
  const index = args.indexOf("--trips");
  if (index === -1) {
    return null;
  }
  const count = Number(args[index + 1]);
  if (!Number.isInteger(count) || count < 1) {
    throw new Error("--trips needs a positive whole number");
  }
  return count;
}

async function main(): Promise<void> {
  const config = loadConfig();
  const trips = tripsArgument(process.argv.slice(2));
  const drivers = config.drivers.map((email) => new DriverBot(config, email));
  await Promise.all(drivers.map((driver) => driver.start()));
  log("simulator", `${drivers.length} drivers online`);

  const shutdown = async () => {
    await Promise.all(drivers.map((driver) => driver.stop()));
  };
  process.once("SIGINT", () => void shutdown().then(() => process.exit(0)));
  process.once("SIGTERM", () => void shutdown().then(() => process.exit(0)));

  if (trips !== null) {
    try {
      await runTrips(config, trips);
    } finally {
      await shutdown();
    }
  }
}

main().catch((error: unknown) => {
  logError("simulator", "failed", error);
  process.exit(1);
});
