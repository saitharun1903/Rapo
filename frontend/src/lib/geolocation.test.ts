import { describe, expect, it } from "vitest";
import { compassHeading } from "./geolocation";

describe("compassHeading", () => {
  it("rounds to whole degrees within 0–359", () => {
    expect(compassHeading(12.4)).toBe(12);
    expect(compassHeading(359.4)).toBe(359);
  });

  it("wraps a heading that rounds to 360 to north", () => {
    expect(compassHeading(359.6)).toBe(0);
  });

  it("has no heading when the device reports none", () => {
    expect(compassHeading(null)).toBeNull();
    expect(compassHeading(Number.NaN)).toBeNull();
  });
});
