import { createHmac } from "node:crypto";
import { isIP } from "node:net";

/**
 * Server-only (used by proxy.ts): the browser's address, signed for the backend. The backend does not trust
 * this server's forwarding headers, so without this every browser would share the frontend's rate-limit bucket
 * (docs/architecture.md section 9). The format is verified by the backend's SignedClientIp.java; both test
 * suites pin the same vector.
 */

/** Always removed from the browser's request first, so only this server can set them. */
export const CLIENT_IP_HEADER = "x-rideflow-client-ip";
export const CLIENT_IP_SIGNATURE_HEADER = "x-rideflow-client-ip-signature";

const VERSION = "v1";
const MILLIS_PER_SECOND = 1_000;
/** The backend refuses to start with a shorter key (an HMAC-SHA256 key below 256 bits). */
const MIN_SECRET_BYTES = 32;

export type ClientIpSigning = {
  secret: string;
  /** A header the hosting edge overwrites with the browser's address, e.g. Vercel's x-vercel-forwarded-for. */
  sourceHeader: string;
};

/** `v1.<unix seconds>.<base64url HMAC-SHA256 of "v1\n<seconds>\n<ip>">`. */
export function signClientIp(ip: string, secret: string, epochSeconds: number): string {
  const signature = createHmac("sha256", secret).update(`${VERSION}\n${epochSeconds}\n${ip}`).digest("base64url");
  return `${VERSION}.${epochSeconds}.${signature}`;
}

/** Exactly one IP address, or null: a list means something appended to the header, so it is not the edge's. */
export function clientIpFrom(value: string | null): string | null {
  const candidate = value?.trim() ?? "";
  return isIP(candidate) === 0 || candidate.includes("%") ? null : candidate;
}

/**
 * Signing settings from CLIENT_IP_SIGNING_SECRET and CLIENT_IP_SOURCE_HEADER: both or neither. Only one, or a
 * short secret, is a deployment mistake, so it throws rather than quietly leaving every browser in one bucket.
 */
export function signingFromEnv(env: Record<string, string | undefined>): ClientIpSigning | null {
  const secret = env.CLIENT_IP_SIGNING_SECRET ?? "";
  const sourceHeader = env.CLIENT_IP_SOURCE_HEADER ?? "";
  if (secret === "" && sourceHeader === "") {
    return null;
  }
  if (secret === "" || sourceHeader === "") {
    throw new Error("Set both CLIENT_IP_SIGNING_SECRET and CLIENT_IP_SOURCE_HEADER, or neither");
  }
  if (Buffer.byteLength(secret, "utf8") < MIN_SECRET_BYTES) {
    throw new Error(`CLIENT_IP_SIGNING_SECRET must be at least ${MIN_SECRET_BYTES} bytes`);
  }
  return { secret, sourceHeader };
}

/**
 * The headers to forward to the backend: the browser's own client-IP headers removed and, when signing is
 * configured and the edge supplied an address, a freshly signed pair.
 */
export function forwardedHeaders(incoming: Headers, signing: ClientIpSigning | null, nowMillis: number): Headers {
  const headers = new Headers(incoming);
  headers.delete(CLIENT_IP_HEADER);
  headers.delete(CLIENT_IP_SIGNATURE_HEADER);
  const ip = signing === null ? null : clientIpFrom(incoming.get(signing.sourceHeader));
  if (signing === null || ip === null) {
    return headers;
  }
  headers.set(CLIENT_IP_HEADER, ip);
  headers.set(CLIENT_IP_SIGNATURE_HEADER, signClientIp(ip, signing.secret, Math.floor(nowMillis / MILLIS_PER_SECOND)));
  return headers;
}
