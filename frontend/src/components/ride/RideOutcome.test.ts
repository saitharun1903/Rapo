import { describe, expect, it } from "vitest";
import { fareChange } from "./RideOutcome";

const inr = (amount: string) => ({ amount, currency: "INR" });

describe("fareChange", () => {
  it("says when the final fare matches the estimate", () => {
    expect(fareChange(inr("180.00"), inr("180.00"), 8000, 8000)).toBe("The same as your estimate.");
  });

  it("explains a higher fare with the distance actually driven", () => {
    expect(fareChange(inr("180.00"), inr("204.00"), 8000, 9600))
      .toBe("₹24.00 more than your estimate. The trip was 9.6 km, against 8.0 km estimated.");
  });

  it("explains a lower fare", () => {
    expect(fareChange(inr("180.00"), inr("168.00"), 8000, 7200))
      .toBe("₹12.00 less than your estimate. The trip was 7.2 km, against 8.0 km estimated.");
  });

  it("does not invent a comparison when there was no estimate", () => {
    expect(fareChange(null, inr("168.00"), 8000, 7200)).toBe("Calculated from the route driven.");
  });
});
