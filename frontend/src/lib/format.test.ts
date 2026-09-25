import { describe, expect, it } from "vitest";
import { safeNextPath } from "./forms";
import { formatDistance, formatDuration, formatMoney, humanize } from "./format";
import { windowFor } from "./ranges";

describe("formatting", () => {
  it("formats money from its decimal string", () => {
    expect(formatMoney({ amount: "331.00", currency: "INR" })).toBe("₹331.00");
    expect(formatMoney(null)).toBe("—");
  });

  it("formats distances and durations for people", () => {
    expect(formatDistance(640)).toBe("640 m");
    expect(formatDistance(14_200)).toBe("14.2 km");
    expect(formatDuration(20)).toBe("1 min");
    expect(formatDuration(38 * 60)).toBe("38 min");
    expect(formatDuration(95 * 60)).toBe("1 h 35 min");
  });

  it("turns constants into words", () => {
    expect(humanize("DRIVER_ARRIVING")).toBe("Driver arriving");
  });
});

describe("safeNextPath", () => {
  it("allows same-site paths only", () => {
    expect(safeNextPath("/trips/abc")).toBe("/trips/abc");
    expect(safeNextPath("//evil.example")).toBeNull();
    expect(safeNextPath("/\\evil.example")).toBeNull();
    expect(safeNextPath("https://evil.example")).toBeNull();
    expect(safeNextPath(null)).toBeNull();
  });
});

describe("windowFor", () => {
  it("ends at the next whole hour, so it is stable between renders", () => {
    const now = Date.parse("2026-09-25T10:20:00Z");
    expect(windowFor("24h", now)).toEqual({ from: "2026-09-24T11:00:00.000Z", to: "2026-09-25T11:00:00.000Z", granularity: "HOUR" });
    expect(windowFor("7d", now).granularity).toBe("DAY");
  });
});
