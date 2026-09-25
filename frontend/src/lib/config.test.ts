import { describe, expect, it } from "vitest";
import { numberFrom, textFrom } from "./config";

describe("build-time settings", () => {
  it("fall back when missing or empty, as a Docker build argument that was not passed arrives", () => {
    expect(textFrom(undefined, "https://tiles.example/style")).toBe("https://tiles.example/style");
    expect(textFrom("", "https://tiles.example/style")).toBe("https://tiles.example/style");
    expect(textFrom("  ", "https://tiles.example/style")).toBe("https://tiles.example/style");
    expect(numberFrom("", 17.385)).toBe(17.385);
  });

  it("use the value when one is set", () => {
    expect(textFrom("wss://api.example/ws", "ws://localhost:8080/ws")).toBe("wss://api.example/ws");
    expect(numberFrom("12.5", 17.385)).toBe(12.5);
  });
});
