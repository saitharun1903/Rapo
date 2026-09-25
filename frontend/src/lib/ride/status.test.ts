import { describe, expect, it } from "vitest";
import { ride } from "@/test/fixtures";
import { currentTarget, newerRide, nextDriverAction } from "./status";

describe("newerRide", () => {
  it("applies an update with a higher version", () => {
    expect(newerRide(ride({ version: 2 }), ride({ version: 3, status: "DRIVER_ASSIGNED" })).status).toBe("DRIVER_ASSIGNED");
  });

  it("ignores an update that arrives late or twice", () => {
    const current = ride({ version: 4, status: "IN_PROGRESS" });
    expect(newerRide(current, ride({ version: 3, status: "DRIVER_ARRIVED" }))).toBe(current);
    expect(newerRide(current, ride({ version: 4, status: "IN_PROGRESS" }))).toBe(current);
  });

  it("takes any update for a different ride or when nothing is shown", () => {
    expect(newerRide(null, ride({ version: 1 })).version).toBe(1);
    expect(newerRide(ride({ id: "other", version: 9 }), ride({ version: 1 })).id).toBe("r1");
  });
});

describe("nextDriverAction", () => {
  it("moves the ride forward one step at a time", () => {
    expect(nextDriverAction("DRIVER_ASSIGNED")?.path).toBe("en-route");
    expect(nextDriverAction("DRIVER_ARRIVING")?.path).toBe("arrive");
    expect(nextDriverAction("DRIVER_ARRIVED")?.path).toBe("start");
    expect(nextDriverAction("IN_PROGRESS")?.path).toBe("complete");
    expect(nextDriverAction("COMPLETED")).toBeNull();
    expect(nextDriverAction("MATCHING")).toBeNull();
  });
});

describe("currentTarget", () => {
  it("is the pickup until the trip starts, then the dropoff", () => {
    expect(currentTarget(ride({ status: "DRIVER_ARRIVING" })).address).toBe("Pickup");
    expect(currentTarget(ride({ status: "IN_PROGRESS" })).address).toBe("Dropoff");
  });
});
