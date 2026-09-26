import type { GeoPoint } from "@/lib/api/types";

const EARTH_RADIUS_METERS = 6_371_008.8;
const DEGREES = 180 / Math.PI;
const RADIANS = Math.PI / 180;

export function metersBetween(a: GeoPoint, b: GeoPoint): number {
  const dLat = (b.lat - a.lat) * RADIANS;
  const dLng = (b.lng - a.lng) * RADIANS;
  const h = Math.sin(dLat / 2) ** 2 + Math.cos(a.lat * RADIANS) * Math.cos(b.lat * RADIANS) * Math.sin(dLng / 2) ** 2;
  return 2 * EARTH_RADIUS_METERS * Math.asin(Math.min(1, Math.sqrt(h)));
}

/** Straight-line interpolation; fine over the few metres between two GPS fixes. */
export function lerpPoint(from: GeoPoint, to: GeoPoint, t: number): GeoPoint {
  return { lat: from.lat + (to.lat - from.lat) * t, lng: from.lng + (to.lng - from.lng) * t };
}

/** Interpolates a compass heading the short way round: 350° to 10° passes through 0°, not 180°. */
export function lerpHeading(from: number, to: number, t: number): number {
  const delta = ((((to - from) % 360) + 540) % 360) - 180;
  return (((from + delta * t) % 360) + 360) % 360;
}

/** An ease-out curve for marker movement: quick to start, gentle to settle. */
export function easeOut(t: number): number {
  const clamped = Math.min(1, Math.max(0, t));
  return 1 - (1 - clamped) ** 2;
}

type Projection = { index: number; t: number; point: GeoPoint; distance: number };

/**
 * The closest point on the route to {@code point}: which segment, how far along it, and how far away. Uses a local
 * equirectangular projection, accurate at city scale.
 */
export function projectOnRoute(route: GeoPoint[], point: GeoPoint): Projection | null {
  if (route.length < 2) {
    return null;
  }
  const scale = Math.cos(point.lat * RADIANS);
  let best: Projection | null = null;
  for (let index = 0; index < route.length - 1; index++) {
    const a = route[index];
    const b = route[index + 1];
    const ax = a.lng * scale;
    const ay = a.lat;
    const dx = b.lng * scale - ax;
    const dy = b.lat - ay;
    const lengthSquared = dx * dx + dy * dy;
    const t = lengthSquared === 0 ? 0
      : Math.min(1, Math.max(0, ((point.lng * scale - ax) * dx + (point.lat - ay) * dy) / lengthSquared));
    const projected = lerpPoint(a, b, t);
    const distance = metersBetween(projected, point);
    if (!best || distance < best.distance) {
      best = { index, t, point: projected, distance };
    }
  }
  return best;
}

/**
 * The route cut where the car is: what has been driven and what is left. The car's position is snapped onto the
 * line so the two halves meet exactly under the marker.
 */
export function splitRoute(route: GeoPoint[], at: GeoPoint): { travelled: GeoPoint[]; remaining: GeoPoint[] } {
  const projection = projectOnRoute(route, at);
  if (!projection) {
    return { travelled: [], remaining: route };
  }
  return {
    travelled: [...route.slice(0, projection.index + 1), projection.point],
    remaining: [projection.point, ...route.slice(projection.index + 1)],
  };
}

/** A polygon approximating a circle, for the location accuracy ring. */
export function circlePolygon(center: GeoPoint, radiusMeters: number, steps = 48): GeoPoint[] {
  const angular = radiusMeters / EARTH_RADIUS_METERS;
  const lat = center.lat * RADIANS;
  const lng = center.lng * RADIANS;
  const points: GeoPoint[] = [];
  for (let step = 0; step <= steps; step++) {
    const bearing = (2 * Math.PI * step) / steps;
    const pointLat = Math.asin(Math.sin(lat) * Math.cos(angular) + Math.cos(lat) * Math.sin(angular) * Math.cos(bearing));
    const pointLng = lng + Math.atan2(Math.sin(bearing) * Math.sin(angular) * Math.cos(lat),
      Math.cos(angular) - Math.sin(lat) * Math.sin(pointLat));
    points.push({ lat: pointLat * DEGREES, lng: pointLng * DEGREES });
  }
  return points;
}

/** The smallest box around the points, as MapLibre's [[west, south], [east, north]]. */
export function boundsOf(points: GeoPoint[]): [[number, number], [number, number]] | null {
  if (points.length === 0) {
    return null;
  }
  const lngs = points.map((point) => point.lng);
  const lats = points.map((point) => point.lat);
  return [[Math.min(...lngs), Math.min(...lats)], [Math.max(...lngs), Math.max(...lats)]];
}
