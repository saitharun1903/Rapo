import { act, renderHook } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import type { ConnectionState } from "./types";
import { RESTORED_MIN_OUTAGE_MS, RESTORED_VISIBLE_MS, useConnectionNotice } from "./useConnectionNotice";

function setOnline(online: boolean) {
  Object.defineProperty(navigator, "onLine", { configurable: true, get: () => online });
  window.dispatchEvent(new Event(online ? "online" : "offline"));
}

describe("useConnectionNotice", () => {
  beforeEach(() => {
    vi.useFakeTimers();
    setOnline(true);
  });

  afterEach(() => {
    vi.useRealTimers();
    setOnline(true);
  });

  const render = (initial: ConnectionState) =>
    renderHook(({ state }) => useConnectionNotice(state), { initialProps: { state: initial } });

  it("says nothing on a first connection", () => {
    const { result, rerender } = render("connecting");
    rerender({ state: "connected" });
    expect(result.current).toBeNull();
  });

  it("reports a reconnecting socket and an unusable one", () => {
    const { result, rerender } = render("reconnecting");
    expect(result.current).toBe("reconnecting");
    rerender({ state: "unavailable" });
    expect(result.current).toBe("unavailable");
  });

  it("puts the device being offline above everything else", () => {
    const { result } = render("connected");
    act(() => setOnline(false));
    expect(result.current).toBe("offline");
  });

  it("announces a restored connection after a real outage, then clears it", () => {
    const { result, rerender } = render("connected");
    rerender({ state: "reconnecting" });
    act(() => vi.advanceTimersByTime(RESTORED_MIN_OUTAGE_MS + 500));
    rerender({ state: "connected" });
    expect(result.current).toBe("restored");
    act(() => vi.advanceTimersByTime(RESTORED_VISIBLE_MS));
    expect(result.current).toBeNull();
  });

  it("does not announce a blip", () => {
    const { result, rerender } = render("connected");
    rerender({ state: "reconnecting" });
    act(() => vi.advanceTimersByTime(RESTORED_MIN_OUTAGE_MS - 500));
    rerender({ state: "connected" });
    expect(result.current).toBeNull();
  });

  it("drops a restored notice when the connection fails again", () => {
    const { result, rerender } = render("connected");
    rerender({ state: "reconnecting" });
    act(() => vi.advanceTimersByTime(RESTORED_MIN_OUTAGE_MS + 500));
    rerender({ state: "connected" });
    rerender({ state: "reconnecting" });
    act(() => vi.advanceTimersByTime(100));
    rerender({ state: "connected" });
    expect(result.current).toBeNull();
  });
});
