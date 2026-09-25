import assert from "node:assert/strict";
import { describe, it } from "node:test";
import { bearingDegrees, distanceMeters, offset, PathWalker, randomPointNear } from "./geo.ts";

const CENTER = { lat: 17.385, lng: 78.4867 };
const TOLERANCE_METERS = 1;

describe("geo", () => {
  it("offset and distance agree", () => {
    const moved = offset(CENTER, 1_000, 90);
    assert.ok(Math.abs(distanceMeters(CENTER, moved) - 1_000) < TOLERANCE_METERS);
    assert.equal(bearingDegrees(CENTER, moved), 90);
  });

  it("a bearing just west of north rounds to 0, never 360", () => {
    assert.equal(bearingDegrees(CENTER, offset(CENTER, 1_000, 359.8)), 0);
  });

  it("random points stay inside the radius", () => {
    for (let i = 0; i < 200; i++) {
      assert.ok(distanceMeters(CENTER, randomPointNear(CENTER, 3_000)) <= 3_000 + TOLERANCE_METERS);
    }
  });

  it("walks a path at the requested pace and stops at its end", () => {
    const end = offset(CENTER, 250, 0);
    const walker = new PathWalker([CENTER, offset(CENTER, 100, 0), end]);
    const first = walker.advance(60);
    assert.ok(Math.abs(distanceMeters(CENTER, first.position) - 60) < TOLERANCE_METERS);
    assert.equal(first.headingDeg, 0);
    assert.equal(walker.done, false);
    walker.advance(1_000);
    assert.equal(walker.done, true);
    assert.ok(distanceMeters(walker.advance(10).position, end) < TOLERANCE_METERS);
  });
});
