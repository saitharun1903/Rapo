import type { NextConfig } from "next";

/**
 * The browser calls the API on the frontend's own origin and Next forwards it to the backend. Same origin
 * keeps the refresh-token cookie (HttpOnly, SameSite=Lax, Path=/api/auth) first-party and avoids CORS for
 * REST. The WebSocket connects to the backend directly (NEXT_PUBLIC_WS_URL), whose Origin check allows
 * this frontend. This file is read by `next build` and serialized into the output, so BACKEND_URL is fixed
 * at build time (the Docker image takes it as a build argument).
 */
const backendUrl = process.env.BACKEND_URL ?? "http://localhost:8080";

const nextConfig: NextConfig = {
  // The Docker image runs the minimal standalone server; `next start` (local runs, Playwright) needs the
  // regular output.
  output: process.env.NEXT_OUTPUT === "standalone" ? "standalone" : undefined,
  async rewrites() {
    return [{ source: "/api/:path*", destination: `${backendUrl}/api/:path*` }];
  },
  poweredByHeader: false,
};

export default nextConfig;
