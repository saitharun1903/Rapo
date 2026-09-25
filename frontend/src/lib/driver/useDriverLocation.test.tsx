import { act, renderHook } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { REPORT_INTERVAL_MS, useDriverLocation } from "./useDriverLocation";

const sendLocation = vi.fn(() => true);

vi.mock("@/lib/realtime/RealtimeProvider", () => ({
  useRealtime: () => ({ sendLocation }),
  useRealtimeSubscription: () => {},
}));

type Success = (fix: GeolocationPosition) => void;
type Failure = (error: GeolocationPositionError) => void;
const PERMISSION_DENIED = 1;
const POSITION_UNAVAILABLE = 2;
const TIMEOUT = 3;
let onFix: Success = () => {};
let onError: Failure = () => {};

function fix(lat: number, lng: number): GeolocationPosition {
  return { coords: { latitude: lat, longitude: lng, heading: null, speed: null, accuracy: 5 }, timestamp: Date.now() } as GeolocationPosition;
}

function failure(code: number): GeolocationPositionError {
  return { code, message: "No position", PERMISSION_DENIED, POSITION_UNAVAILABLE, TIMEOUT } as GeolocationPositionError;
}

beforeEach(() => {
  vi.useFakeTimers();
  sendLocation.mockClear();
  Object.defineProperty(navigator, "geolocation", {
    configurable: true,
    value: {
      watchPosition: (success: Success, error: Failure) => {
        onFix = success;
        onError = error;
        return 1;
      },
      clearWatch: () => {},
    },
  });
});

afterEach(() => {
  vi.useRealTimers();
});

describe("useDriverLocation", () => {
  it("stops reporting the last fix once the device says it has no position, and resumes with the next fix", () => {
    renderHook(() => useDriverLocation(true));
    act(() => onFix(fix(17.4, 78.4)));
    act(() => vi.advanceTimersByTime(REPORT_INTERVAL_MS));
    const reportsWithGps = sendLocation.mock.calls.length;
    expect(reportsWithGps).toBeGreaterThan(0);

    act(() => onError(failure(POSITION_UNAVAILABLE)));
    act(() => vi.advanceTimersByTime(3 * REPORT_INTERVAL_MS));
    expect(sendLocation).toHaveBeenCalledTimes(reportsWithGps);

    act(() => onFix(fix(17.41, 78.41)));
    act(() => vi.advanceTimersByTime(REPORT_INTERVAL_MS));
    expect(sendLocation.mock.calls.length).toBeGreaterThan(reportsWithGps);
  });

  it("keeps reporting through a timeout, which a phone that is not moving can get", () => {
    renderHook(() => useDriverLocation(true));
    act(() => onFix(fix(17.4, 78.4)));
    act(() => vi.advanceTimersByTime(REPORT_INTERVAL_MS));
    const before = sendLocation.mock.calls.length;

    act(() => onError(failure(TIMEOUT)));
    act(() => vi.advanceTimersByTime(2 * REPORT_INTERVAL_MS));
    expect(sendLocation.mock.calls.length).toBe(before + 2);
  });
});
