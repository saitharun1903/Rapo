import type { NextConfig } from "next";

/**
 * The browser calls the API on the frontend's own origin and Next forwards it to the backend. Same origin
 * keeps the refresh-token cookie (HttpOnly, SameSite=Lax, Path=/api/auth) first-party and avoids CORS for
 * REST. The WebSocket connects to the backend directly (NEXT_PUBLIC_WS_URL), whose Origin check allows
 * this frontend. This file is read by `next build` and serialized into the output, so BACKEND_URL is fixed
 * at build time (the Docker image takes it as a build argument).
 */
const backendUrl = process.env.BACKEND_URL ?? "http://localhost:8080";

// A hosted build (Vercel sets VERCEL=1) must say where the backend is: the localhost defaults would build a site
// that cannot reach it, and an https page may only open a secure WebSocket (docs/deployment.md).
if (process.env.VERCEL) {
  if (!process.env.BACKEND_URL?.startsWith("https://")) {
    throw new Error("Set BACKEND_URL to the backend's https:// URL for a Vercel build");
  }
  if (!process.env.NEXT_PUBLIC_WS_URL?.startsWith("wss://")) {
    throw new Error("Set NEXT_PUBLIC_WS_URL to the backend's wss://…/ws URL for a Vercel build");
  }
}

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
