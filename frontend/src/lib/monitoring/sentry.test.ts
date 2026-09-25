import type { ErrorEvent } from "@sentry/nextjs";
import { describe, expect, it } from "vitest";
import { REDACTED, mask, scrubBreadcrumb, scrubEvent, sentrySettings, withoutQuery } from "./sentry";

const JWT = "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIxMjMifQ.c2lnbmF0dXJl";
const EMAIL = "ananya@rideflow.example.com";

describe("sentrySettings", () => {
  it("is off without a DSN", () => {
    expect(sentrySettings(undefined, "production")).toBeUndefined();
    expect(sentrySettings("", "production")).toBeUndefined();
  });

  it("never sends default PII and always scrubs", () => {
    const settings = sentrySettings("https://key@o1.ingest.sentry.io/2", undefined);
    expect(settings).toMatchObject({ dsn: "https://key@o1.ingest.sentry.io/2", environment: "local", sendDefaultPii: false });
    expect(settings?.beforeSend).toBe(scrubEvent);
    expect(settings?.beforeBreadcrumb).toBe(scrubBreadcrumb);
  });
});

describe("scrubEvent", () => {
  it("keeps the method, path and user agent of a request and nothing else", () => {
    const event: ErrorEvent = {
      type: undefined,
      request: {
        method: "GET",
        url: "https://rideflow.example.com/book?pickup=Banjara%20Hills#map",
        query_string: "pickup=Banjara%20Hills",
        cookies: { rf_refresh: "opaque" },
        data: { password: "hunter2" },
        headers: { "User-Agent": "Mozilla/5.0", Authorization: `Bearer ${JWT}`, Cookie: "rf_refresh=opaque" },
      },
    };

    expect(scrubEvent(event).request).toEqual({
      method: "GET",
      url: "https://rideflow.example.com/book",
      headers: { "User-Agent": "Mozilla/5.0" },
    });
  });

  it("reduces the user to its id and drops an anonymous one", () => {
    expect(scrubEvent({ type: undefined, user: { id: "u-1", email: EMAIL, ip_address: "203.0.113.9" } }).user)
      .toEqual({ id: "u-1" });
    expect(scrubEvent({ type: undefined, user: { ip_address: "{{auto}}" } }).user).toBeUndefined();
  });

  it("masks emails and tokens in messages, exceptions and breadcrumbs", () => {
    const scrubbed = scrubEvent({
      type: undefined,
      message: `Login failed for ${EMAIL}`,
      exception: { values: [{ type: "Error", value: `Token ${JWT} rejected` }] },
      breadcrumbs: [{ category: "fetch", data: { url: `/api/geo/search?q=${EMAIL}`, status_code: 200 } }],
    });

    expect(scrubbed.message).toBe(`Login failed for ${REDACTED}`);
    expect(scrubbed.exception?.values?.[0]).toEqual({ type: "Error", value: `Token ${REDACTED} rejected` });
    expect(scrubbed.breadcrumbs?.[0].data).toEqual({ url: "/api/geo/search", status_code: 200 });
  });
});

describe("scrubBreadcrumb", () => {
  it("strips query strings from navigation URLs and masks free text", () => {
    expect(scrubBreadcrumb({
      category: "navigation",
      message: `Signed in as ${EMAIL}`,
      data: { from: "/login?next=%2Fbook", to: "/book?pickup=home", note: `Bearer ${JWT}` },
    })).toEqual({
      category: "navigation",
      message: `Signed in as ${REDACTED}`,
      data: { from: "/login", to: "/book", note: REDACTED },
    });
  });
});

describe("console breadcrumbs", () => {
  it("mask the logged arguments, which arrive as an array", () => {
    expect(scrubBreadcrumb({
      category: "console",
      message: `Refresh failed for ${EMAIL}`,
      data: { logger: "console", arguments: [`Refresh failed for ${EMAIL}`, { token: JWT }] },
    }).data).toEqual({ logger: "console", arguments: [`Refresh failed for ${REDACTED}`, { token: REDACTED }] });
  });
});

describe("mask and withoutQuery", () => {
  it("leave ordinary text and bare paths alone", () => {
    const text = "Ride 7ec03945-094a-48d0-b030-e3731c05ae42 completed: 5230 m, fare 212.00 INR";
    expect(mask(text)).toBe(text);
    expect(mask(undefined)).toBeUndefined();
    expect(withoutQuery("/rides/7ec03945")).toBe("/rides/7ec03945");
  });
});
