import type { NextConfig } from "next";

/**
 * The browser calls the API on the frontend's own origin and Next forwards it to the backend. Same origin
 * keeps the refresh-token cookie (HttpOnly, SameSite=Lax, Path=/api/auth) first-party and avoids CORS for
 * REST. The WebSocket connects to the backend directly (NEXT_PUBLIC_WS_URL), whose Origin check allows
 * this frontend. BACKEND_URL is read when the server starts, and `next build` bakes it in for `next start`.
 */
const backendUrl = process.env.BACKEND_URL ?? "http://localhost:8080";

const nextConfig: NextConfig = {
  async rewrites() {
    return [{ source: "/api/:path*", destination: `${backendUrl}/api/:path*` }];
  },
  poweredByHeader: false,
};

export default nextConfig;
