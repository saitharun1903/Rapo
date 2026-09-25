import { describe, expect, it } from "vitest";
import { hostedBackend } from "./hostedBackend";

describe("hostedBackend", () => {
  it("uses the local backend outside Vercel", () => {
    expect(hostedBackend({})).toEqual({ configured: true, backendUrl: "http://localhost:8080" });
    expect(hostedBackend({ BACKEND_URL: "http://backend:8080" })).toEqual({ configured: true, backendUrl: "http://backend:8080" });
  });

  it("builds a Vercel deployment with no backend named, and says so", () => {
    const result = hostedBackend({ VERCEL: "1" });
    expect(result.configured).toBe(false);
    expect(result.configured === false && result.warning).toMatch(/building without a backend/);
  });

  it("uses a public backend on Vercel", () => {
    expect(hostedBackend({ VERCEL: "1", BACKEND_URL: "https://api.example.com", NEXT_PUBLIC_WS_URL: "wss://api.example.com/ws" }))
      .toEqual({ configured: true, backendUrl: "https://api.example.com" });
  });

  it.each([
    ["only the backend", { BACKEND_URL: "https://api.example.com" }, /NEXT_PUBLIC_WS_URL/],
    ["only the socket", { NEXT_PUBLIC_WS_URL: "wss://api.example.com/ws" }, /BACKEND_URL/],
    ["an http backend", { BACKEND_URL: "http://api.example.com", NEXT_PUBLIC_WS_URL: "wss://api.example.com/ws" }, /https:\/\//],
    ["an insecure socket", { BACKEND_URL: "https://api.example.com", NEXT_PUBLIC_WS_URL: "ws://api.example.com/ws" }, /wss:\/\//],
  ])("refuses %s on Vercel", (_, env, message) => {
    expect(() => hostedBackend({ VERCEL: "1", ...env })).toThrow(message);
  });
});
