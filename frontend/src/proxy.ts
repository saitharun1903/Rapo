import { NextResponse, type NextRequest } from "next/server";
import { forwardedHeaders, signingFromEnv } from "@/lib/clientIp";

/**
 * Runs before the /api rewrite in next.config.ts (Proxy precedes `afterFiles` rewrites) and on the Node.js
 * runtime. It strips any client-IP headers the browser sent and, when CLIENT_IP_SIGNING_SECRET and
 * CLIENT_IP_SOURCE_HEADER are set, adds the browser's address signed for the backend (lib/clientIp.ts).
 */
export function proxy(request: NextRequest): NextResponse {
  const headers = forwardedHeaders(request.headers, signingFromEnv(process.env), Date.now());
  return NextResponse.next({ request: { headers } });
}

export const config = {
  matcher: "/api/:path*",
};
