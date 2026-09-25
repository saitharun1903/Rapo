import { describe, expect, it } from "vitest";
import { RECONNECT_BASE_MS, RECONNECT_CAP_MS, reconnectDelay } from "./backoff";

const ALMOST_ONE = 0.999_999;

describe("reconnectDelay", () => {
  it("doubles the window with each attempt, from the base", () => {
    expect(reconnectDelay(1, () => ALMOST_ONE)).toBe(RECONNECT_BASE_MS - 1);
    expect(reconnectDelay(2, () => ALMOST_ONE)).toBe(2 * RECONNECT_BASE_MS - 1);
    expect(reconnectDelay(4, () => ALMOST_ONE)).toBe(8 * RECONNECT_BASE_MS - 1);
  });

  it("never exceeds the cap", () => {
    expect(reconnectDelay(50, () => ALMOST_ONE)).toBe(RECONNECT_CAP_MS - 1);
  });

  it("spreads retries over the whole window (full jitter)", () => {
    expect(reconnectDelay(3, () => 0)).toBe(0);
    expect(reconnectDelay(3, () => 0.5)).toBe(2 * RECONNECT_BASE_MS);
  });
});
