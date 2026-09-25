export type Point = { lat: number; lng: number };

const EARTH_RADIUS_METERS = 6_371_000;
const DEGREES_IN_CIRCLE = 360;
const toRadians = (degrees: number) => (degrees * Math.PI) / 180;
const toDegrees = (radians: number) => (radians * 180) / Math.PI;

/** Great-circle distance in metres. */
export function distanceMeters(a: Point, b: Point): number {
  const dLat = toRadians(b.lat - a.lat);
  const dLng = toRadians(b.lng - a.lng);
  const h = Math.sin(dLat / 2) ** 2 + Math.cos(toRadians(a.lat)) * Math.cos(toRadians(b.lat)) * Math.sin(dLng / 2) ** 2;
  return 2 * EARTH_RADIUS_METERS * Math.asin(Math.sqrt(h));
}

/** Initial compass bearing from a to b, 0–359 degrees. */
export function bearingDegrees(a: Point, b: Point): number {
  const y = Math.sin(toRadians(b.lng - a.lng)) * Math.cos(toRadians(b.lat));
  const x = Math.cos(toRadians(a.lat)) * Math.sin(toRadians(b.lat))
    - Math.sin(toRadians(a.lat)) * Math.cos(toRadians(b.lat)) * Math.cos(toRadians(b.lng - a.lng));
  // Rounded, then wrapped: 359.5 and above rounds to 360, which is north again (the backend accepts 0–359).
  return Math.round((toDegrees(Math.atan2(y, x)) + DEGREES_IN_CIRCLE) % DEGREES_IN_CIRCLE) % DEGREES_IN_CIRCLE;
}

/** The point `meters` from `origin` towards `bearing` (degrees). */
export function offset(origin: Point, meters: number, bearing: number): Point {
  const angular = meters / EARTH_RADIUS_METERS;
  const theta = toRadians(bearing);
  const lat1 = toRadians(origin.lat);
  const lng1 = toRadians(origin.lng);
  const lat2 = Math.asin(Math.sin(lat1) * Math.cos(angular) + Math.cos(lat1) * Math.sin(angular) * Math.cos(theta));
  const lng2 = lng1 + Math.atan2(Math.sin(theta) * Math.sin(angular) * Math.cos(lat1), Math.cos(angular) - Math.sin(lat1) * Math.sin(lat2));
  return { lat: toDegrees(lat2), lng: toDegrees(lng2) };
}

/** A random point within `radius` metres of `center`, uniform over the disc. */
export function randomPointNear(center: Point, radius: number, random: () => number = Math.random): Point {
  return offset(center, radius * Math.sqrt(random()), random() * DEGREES_IN_CIRCLE);
}

/**
 * Walks along a path: `advance(meters)` moves that far along it and returns the new position and heading,
 * never overshooting the end.
 */
export class PathWalker {
  private segment = 0;
  private position: Point;
  private readonly path: Point[];

  constructor(path: Point[]) {
    this.path = path;
    if (path.length === 0) {
      throw new Error("A path needs at least one point");
    }
    this.position = path[0];
  }

  get done(): boolean {
    return this.segment >= this.path.length - 1;
  }

  advance(meters: number): { position: Point; headingDeg: number | null } {
    let remaining = meters;
    let heading: number | null = null;
    while (remaining > 0 && !this.done) {
      const next = this.path[this.segment + 1];
      const left = distanceMeters(this.position, next);
      heading = left > 0 ? bearingDegrees(this.position, next) : heading;
      if (left <= remaining) {
        this.position = next;
        this.segment += 1;
        remaining -= left;
      } else {
        this.position = offset(this.position, remaining, bearingDegrees(this.position, next));
        remaining = 0;
      }
    }
    return { position: this.position, headingDeg: heading };
  }
}
