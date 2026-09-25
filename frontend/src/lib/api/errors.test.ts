import { describe, expect, it } from "vitest";
import { ApiError, isApiError } from "./errors";

describe("ApiError.fromResponse", () => {
  it("keeps the backend's code, message and field errors", async () => {
    const body = { status: 400, code: "VALIDATION_FAILED", message: "Check the fields", fieldErrors: [{ field: "email", message: "invalid" }] };
    const error = await ApiError.fromResponse(new Response(JSON.stringify(body), { status: 400 }), body);
    expect(error.code).toBe("VALIDATION_FAILED");
    expect(error.fieldErrors).toEqual([{ field: "email", message: "invalid" }]);
    expect(isApiError(error, "VALIDATION_FAILED")).toBe(true);
  });

  it("reads Retry-After in seconds", async () => {
    const body = { code: "RATE_LIMITED", message: "Slow down" };
    const error = await ApiError.fromResponse(new Response(JSON.stringify(body), { status: 429, headers: { "Retry-After": "90" } }), body);
    expect(error.retryAfterMs).toBe(90_000);
  });

  it("explains a server error that did not come from the backend", async () => {
    const error = await ApiError.fromResponse(new Response("Internal Server Error", { status: 500 }), "Internal Server Error");
    expect(error.code).toBe("SERVER_UNAVAILABLE");
    expect(error.message).toMatch(/not responding/);
  });
});
