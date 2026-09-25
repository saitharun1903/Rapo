/**
 * Where a build's /api rewrite and WebSocket point, decided when next.config.ts is read. Outside Vercel (local
 * runs, Docker, CI) the localhost defaults are right. On Vercel a build either names a public backend (an
 * https:// BACKEND_URL and a wss:// NEXT_PUBLIC_WS_URL) or names none at all: then it is a deployment still
 * waiting for its backend, which builds without the rewrite and says so on its pages rather than answering
 * every request with a 404. Anything in between is a configuration mistake and fails the build.
 */
export type HostedBackend =
  | { configured: true; backendUrl: string }
  | { configured: false; warning: string };

const LOCAL_BACKEND_URL = "http://localhost:8080";

export function hostedBackend(env: Record<string, string | undefined>): HostedBackend {
  const backendUrl = env.BACKEND_URL?.trim() ?? "";
  const wsUrl = env.NEXT_PUBLIC_WS_URL?.trim() ?? "";
  if (!env.VERCEL) {
    return { configured: true, backendUrl: backendUrl || LOCAL_BACKEND_URL };
  }
  if (backendUrl === "" && wsUrl === "") {
    return {
      configured: false,
      warning: "No BACKEND_URL or NEXT_PUBLIC_WS_URL: building without a backend. The pages will say that none "
        + "is configured; set both and redeploy once the backend is public (docs/deployment.md).",
    };
  }
  if (!backendUrl.startsWith("https://")) {
    throw new Error("Set BACKEND_URL to the backend's https:// URL for a Vercel build (or leave both URLs unset)");
  }
  if (!wsUrl.startsWith("wss://")) {
    throw new Error("Set NEXT_PUBLIC_WS_URL to the backend's wss://…/ws URL for a Vercel build (or leave both URLs unset)");
  }
  return { configured: true, backendUrl };
}
