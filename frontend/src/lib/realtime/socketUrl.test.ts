import { describe, expect, it } from "vitest";
import { socketUrlProblem } from "./socketUrl";

describe("socketUrlProblem", () => {
  it("accepts ws:// on http and wss:// anywhere", () => {
    expect(socketUrlProblem("ws://localhost:8080/ws", "http:")).toBeNull();
    expect(socketUrlProblem("wss://api.example.com/ws", "https:")).toBeNull();
    expect(socketUrlProblem("wss://api.example.com/ws", "http:")).toBeNull();
  });

  it("refuses an insecure socket from an https page, which the browser blocks", () => {
    expect(socketUrlProblem("ws://localhost:8080/ws", "https:")).toMatch(/secure WebSocket/);
  });

  it("refuses something that is not a WebSocket URL", () => {
    expect(socketUrlProblem("", "http:")).toMatch(/not a WebSocket URL/);
    expect(socketUrlProblem("https://api.example.com/ws", "https:")).toMatch(/ws:\/\/ or wss:\/\//);
  });
});
