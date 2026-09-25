import { describe, expect, it, vi } from "vitest";
import { ApiError } from "@/lib/api/errors";
import { applyFieldErrors, safeNextPath } from "./forms";

type Form = { email: string; password: string };

describe("safeNextPath", () => {
  it("keeps paths on this site", () => {
    expect(safeNextPath("/trips/r1?tab=insights")).toBe("/trips/r1?tab=insights");
  });

  it.each([
    ["another site", "https://evil.example.com/login"],
    ["a protocol-relative URL", "//evil.example.com"],
    ["a backslash the browser reads as a slash", "/\\evil.example.com"],
    ["a script URL", "javascript:alert(1)"],
    ["a relative path", "trips"],
    ["a tab the browser drops, leaving a protocol-relative URL", "/\t/evil.example.com"],
    ["a newline the browser drops", "/\n/evil.example.com"],
  ])("rejects %s", (_, next) => {
    expect(safeNextPath(next)).toBeNull();
  });

  it("is null when there is no next", () => {
    expect(safeNextPath(null)).toBeNull();
  });
});

describe("applyFieldErrors", () => {
  it("puts the backend's field errors on the matching fields", () => {
    const setError = vi.fn();
    const error = new ApiError(400, "VALIDATION_FAILED", "Invalid", [
      { field: "email", message: "must be a well-formed email address" },
      { field: "unknownField", message: "ignored" },
    ]);

    expect(applyFieldErrors<Form>(error, setError, ["email", "password"])).toBe(true);
    expect(setError).toHaveBeenCalledExactlyOnceWith("email", { type: "server", message: "must be a well-formed email address" });
  });

  it("leaves errors that are not about a known field to the caller", () => {
    const setError = vi.fn();
    const onlyUnknown = new ApiError(400, "VALIDATION_FAILED", "Invalid", [{ field: "other", message: "no" }]);

    expect(applyFieldErrors<Form>(onlyUnknown, setError, ["email"])).toBe(false);
    expect(applyFieldErrors<Form>(new ApiError(409, "EMAIL_TAKEN", "Taken"), setError, ["email"])).toBe(false);
    expect(applyFieldErrors<Form>(new Error("boom"), setError, ["email"])).toBe(false);
    expect(setError).not.toHaveBeenCalled();
  });
});
