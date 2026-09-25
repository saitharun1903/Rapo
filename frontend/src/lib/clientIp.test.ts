// @vitest-environment node
import { describe, expect, it } from "vitest";
import {
  CLIENT_IP_HEADER,
  CLIENT_IP_SIGNATURE_HEADER,
  clientIpFrom,
  forwardedHeaders,
  signClientIp,
  signingFromEnv,
} from "./clientIp";

/** Shared with the backend's SignedClientIpTest, which verifies the same signatures. */
const SECRET = "test-only-client-ip-signing-secret-32-bytes-min";
const SIGNED_AT = 1_790_000_000;
const SOURCE_HEADER = "x-vercel-forwarded-for";
const SIGNING = { secret: SECRET, sourceHeader: SOURCE_HEADER };

describe("signed client address", () => {
  it("signs exactly as the backend verifies", () => {
    expect(signClientIp("198.51.100.7", SECRET, SIGNED_AT)).toBe(
      "v1.1790000000.DEupF55QrrDrFosxmQOcm_qr5s4g8sI8ANYERZJLoCU",
    );
    expect(signClientIp("2001:db8::7", SECRET, SIGNED_AT)).toBe(
      "v1.1790000000.n0G0O11IB7I3gK-Ty2Qr1DnJ5XDSXm4K-J5TSHHRNxA",
    );
  });

  it("forwards the edge's address, signed at the current second", () => {
    const incoming = new Headers({ [SOURCE_HEADER]: "198.51.100.7", accept: "application/json" });

    const headers = forwardedHeaders(incoming, SIGNING, SIGNED_AT * 1_000 + 999);

    expect(headers.get(CLIENT_IP_HEADER)).toBe("198.51.100.7");
    expect(headers.get(CLIENT_IP_SIGNATURE_HEADER)).toBe(
      "v1.1790000000.DEupF55QrrDrFosxmQOcm_qr5s4g8sI8ANYERZJLoCU",
    );
    expect(headers.get("accept")).toBe("application/json");
  });

  it("never forwards a browser's own claim", () => {
    const forged = new Headers({ [CLIENT_IP_HEADER]: "203.0.113.9", [CLIENT_IP_SIGNATURE_HEADER]: "v1.1.x" });

    const unsigned = forwardedHeaders(forged, null, SIGNED_AT * 1_000);
    expect(unsigned.has(CLIENT_IP_HEADER)).toBe(false);
    expect(unsigned.has(CLIENT_IP_SIGNATURE_HEADER)).toBe(false);

    // Signing configured, but the edge supplied no address: still nothing of the browser's goes through.
    const noEdgeAddress = forwardedHeaders(forged, SIGNING, SIGNED_AT * 1_000);
    expect(noEdgeAddress.has(CLIENT_IP_HEADER)).toBe(false);
    expect(noEdgeAddress.has(CLIENT_IP_SIGNATURE_HEADER)).toBe(false);
  });

  it("accepts only a single address from the edge's header", () => {
    expect(clientIpFrom(" 198.51.100.7 ")).toBe("198.51.100.7");
    expect(clientIpFrom("2001:db8::7")).toBe("2001:db8::7");
    expect(clientIpFrom("203.0.113.9, 198.51.100.7")).toBeNull();
    expect(clientIpFrom("fe80::1%eth0")).toBeNull();
    expect(clientIpFrom("localhost")).toBeNull();
    expect(clientIpFrom("")).toBeNull();
    expect(clientIpFrom(null)).toBeNull();
  });

  it("is off unless both settings are given, and refuses half a configuration or a short secret", () => {
    expect(signingFromEnv({})).toBeNull();
    expect(signingFromEnv({ CLIENT_IP_SIGNING_SECRET: "", CLIENT_IP_SOURCE_HEADER: "" })).toBeNull();
    expect(signingFromEnv({ CLIENT_IP_SIGNING_SECRET: SECRET, CLIENT_IP_SOURCE_HEADER: SOURCE_HEADER })).toEqual(
      SIGNING,
    );
    expect(() => signingFromEnv({ CLIENT_IP_SIGNING_SECRET: SECRET })).toThrow(/both/);
    expect(() => signingFromEnv({ CLIENT_IP_SOURCE_HEADER: SOURCE_HEADER })).toThrow(/both/);
    expect(() =>
      signingFromEnv({ CLIENT_IP_SIGNING_SECRET: "too-short", CLIENT_IP_SOURCE_HEADER: SOURCE_HEADER }),
    ).toThrow(/32 bytes/);
  });
});
