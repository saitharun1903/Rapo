import { describe, expect, it } from "vitest";
import { boundsOf, circlePolygon, easeOut, lerpHeading, lerpPoint, metersBetween, projectOnRoute, splitRoute } from "./geometry";

const A = { lat: 17.44, lng: 78.38 };
const B = { lat: 17.44, lng: 78.39 };
const C = { lat: 17.45, lng: 78.39 };

describe("lerpHeading", () => {
  it("turns the short way round through north", () => {
    expect(lerpHeading(350, 10, 0.5)).toBeCloseTo(0);
    expect(lerpHeading(10, 350, 0.5)).toBeCloseTo(0);
  });

  it("interpolates ordinary turns linearly and stays within 0-360", () => {
    expect(lerpHeading(90, 180, 0.5)).toBeCloseTo(135);
    expect(lerpHeading(0, 270, 0.5)).toBeCloseTo(315);
    expect(lerpHeading(45, 45, 0.7)).toBeCloseTo(45);
  });
});

describe("lerpPoint and easeOut", () => {
  it("moves along the straight line between two fixes", () => {
    expect(lerpPoint(A, B, 0)).toEqual(A);
    expect(lerpPoint(A, B, 1)).toEqual(B);
    expect(lerpPoint(A, B, 0.5).lng).toBeCloseTo(78.385);
  });

  it("starts fast, ends gently and is clamped", () => {
    expect(easeOut(0)).toBe(0);
    expect(easeOut(1)).toBe(1);
    expect(easeOut(0.5)).toBeGreaterThan(0.5);
    expect(easeOut(2)).toBe(1);
    expect(easeOut(-1)).toBe(0);
  });
});

describe("projectOnRoute and splitRoute", () => {
  const route = [A, B, C];

  it("finds the nearest point on the route and how far off it the car is", () => {
    const beside = { lat: 17.4405, lng: 78.385 };
    const projection = projectOnRoute(route, beside)!;
    expect(projection.index).toBe(0);
    expect(projection.point.lat).toBeCloseTo(17.44, 6);
    expect(projection.point.lng).toBeCloseTo(78.385, 6);
    expect(projection.distance).toBeCloseTo(metersBetween(projection.point, beside), 3);
    expect(projection.distance).toBeGreaterThan(50);
    expect(projection.distance).toBeLessThan(60);
  });

  it("cuts the route under the car, so the driven and remaining parts meet there", () => {
    const { travelled, remaining } = splitRoute(route, { lat: 17.445, lng: 78.3901 });
    expect(travelled[0]).toEqual(A);
    expect(travelled.at(-1)).toEqual(remaining[0]);
    expect(remaining.at(-1)).toEqual(C);
    expect(travelled).toHaveLength(3);
    expect(remaining).toHaveLength(2);
  });

  it("leaves a route it cannot cut untouched", () => {
    expect(splitRoute([A], B)).toEqual({ travelled: [], remaining: [A] });
    expect(projectOnRoute([], A)).toBeNull();
  });
});

describe("circlePolygon and boundsOf", () => {
  it("draws a closed ring at the given radius", () => {
    const ring = circlePolygon(A, 100, 16);
    expect(ring).toHaveLength(17);
    expect(ring[0].lat).toBeCloseTo(ring[16].lat, 9);
    for (const point of ring) {
      expect(metersBetween(A, point)).toBeCloseTo(100, 0);
    }
  });

  it("boxes the points west-south to east-north", () => {
    expect(boundsOf([A, C])).toEqual([[78.38, 17.44], [78.39, 17.45]]);
    expect(boundsOf([])).toBeNull();
  });
});
