import type { NextConfig } from "next";
import { hostedBackend } from "./src/lib/hostedBackend";

/**
 * The browser calls the API on the frontend's own origin and Next forwards it to the backend. Same origin
 * keeps the refresh-token cookie (HttpOnly, SameSite=Lax, Path=/api/auth) first-party and avoids CORS for
 * REST. The WebSocket connects to the backend directly (NEXT_PUBLIC_WS_URL), whose Origin check allows
 * this frontend. This file is read by `next build` and serialized into the output, so BACKEND_URL is fixed
 * at build time (the Docker image takes it as a build argument).
 */
const backend = hostedBackend(process.env);
if (!backend.configured) {
  console.warn(`⚠ ${backend.warning}`);
}

const nextConfig: NextConfig = {
  // The Docker image runs the minimal standalone server; `next start` (local runs, Playwright) needs the
  // regular output.
  output: process.env.NEXT_OUTPUT === "standalone" ? "standalone" : undefined,
  // Read by the pages (lib/config.ts): false on a Vercel deployment built without a backend.
  env: { NEXT_PUBLIC_BACKEND_CONFIGURED: String(backend.configured) },
  async rewrites() {
    return backend.configured ? [{ source: "/api/:path*", destination: `${backend.backendUrl}/api/:path*` }] : [];
  },
  // The pages must not be framed (clickjacking the admin console) or have their content type guessed. The
  // backend sets its own headers on /api responses.
  async headers() {
    return [{
      source: "/:path*",
      headers: [
        { key: "Content-Security-Policy", value: "frame-ancestors 'none'" },
        { key: "X-Frame-Options", value: "DENY" },
        { key: "X-Content-Type-Options", value: "nosniff" },
        { key: "Referrer-Policy", value: "strict-origin-when-cross-origin" },
      ],
    }];
  },
  poweredByHeader: false,
};

export default nextConfig;
