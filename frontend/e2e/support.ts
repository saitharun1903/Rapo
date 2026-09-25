import path from "node:path";
import { expect, type Page } from "@playwright/test";

/** The password the backend's demo profile gave every seeded account (env DEMO_USER_PASSWORD). */
export function demoPassword(): string {
  const password = process.env.DEMO_USER_PASSWORD;
  if (!password) {
    throw new Error("Set DEMO_USER_PASSWORD to the backend's demo password");
  }
  return password;
}

/** Seeded accounts (backend db/seed/R__demo_seed.sql). Each test uses its own passenger, so no ride is left over. */
export const DEMO = {
  admin: "admin@rideflow.example.com",
  passenger: "ananya@rideflow.example.com",
  driverTestPassenger: "rahul@rideflow.example.com",
  cancellingPassenger: "meera@rideflow.example.com",
} as const;

export type Point = { lat: number; lng: number };

/**
 * For steps that wait on the public OSRM and Nominatim servers (addresses, routes, fare estimates). They are
 * shared, rate-limited and sometimes slow; the backend falls back when they fail, but that can take a while.
 */
export const PUBLIC_SERVICES_TIMEOUT_MS = 30_000;

/**
 * Places for tests that must not meet the simulator's drivers, which start within 3 km of the city centre
 * (17.385, 78.4867). Both are 15 to 20 km out: beyond the widest matching radius (8 km) from anything the
 * simulator does, and inside the 40 km service area.
 */
export const OUTSKIRTS = {
  north: { lat: 17.52, lng: 78.4867 },
  southWest: { lat: 17.26, lng: 78.36 },
} as const;

const METERS_PER_DEGREE_LAT = 111_320;

/** Playwright's geolocation for a point. */
export function gps(point: Point): { latitude: number; longitude: number } {
  return { latitude: point.lat, longitude: point.lng };
}

/** A point `meters` north of `from`. */
export function north(from: Point, meters: number): Point {
  return { lat: from.lat + meters / METERS_PER_DEGREE_LAT, lng: from.lng };
}

/** A unique suffix for emails, licences and plates, so repeated runs against one database never collide. */
export function unique(): string {
  return `${Date.now()}`;
}

export async function signIn(page: Page, email: string, password: string, landing: RegExp): Promise<void> {
  await page.goto("/login");
  await page.getByLabel("Email").fill(email);
  await page.getByLabel("Password").fill(password);
  await page.getByRole("button", { name: "Sign in" }).click();
  await expect(page).toHaveURL(landing);
}

/** Clicks the map at a fraction of its width and height, once MapLibre has drawn its canvas. */
export async function clickMap(page: Page, mapName: string, x: number, y: number): Promise<void> {
  const map = page.getByRole("region", { name: mapName });
  await expect(map.locator("canvas")).toBeVisible();
  const box = await map.boundingBox();
  if (!box) {
    throw new Error("The map has no size");
  }
  await map.click({ position: { x: box.width * x, y: box.height * y } });
}

/** How long a capture waits for MapLibre to draw its first frame of style and tiles (data-drawn on the map). */
const MAP_DRAWN_TIMEOUT_MS = 20_000;
/** Markers and a route layer can follow the first frame; a short pause lets them settle. */
const CAPTURE_SETTLE_MS = 1_000;

/**
 * Saves what the page shows as SCREENSHOTS_DIR/<name>.png, for the README. The e2e workflow sets the directory,
 * so every screenshot comes from a passing run against the compose stack; without it this does nothing. A map
 * that never finishes drawing does not fail the test (it says nothing about the app); it becomes a CI warning
 * with the browser's WebGL renderer, and the screenshot is still taken.
 */
export async function capture(page: Page, name: string, options: { fullPage?: boolean } = {}): Promise<void> {
  const directory = process.env.SCREENSHOTS_DIR;
  if (!directory) {
    return;
  }
  const maps = page.locator("[data-drawn]");
  if (await maps.count() > 0) {
    const started = Date.now();
    try {
      await expect(maps.first()).toHaveAttribute("data-drawn", "true", { timeout: MAP_DRAWN_TIMEOUT_MS });
      console.log(`::notice title=Map in ${name}::drawn after ${Date.now() - started} ms`);
    } catch {
      const renderer = await page.evaluate(() => {
        const canvas = document.createElement("canvas");
        const gl = canvas.getContext("webgl2") ?? canvas.getContext("webgl");
        return gl ? String(gl.getParameter(gl.RENDERER)) : "no WebGL context";
      });
      console.log(`::warning title=Map in ${name}::not drawn within ${MAP_DRAWN_TIMEOUT_MS} ms; WebGL renderer: ${renderer}`);
    }
  }
  await page.waitForTimeout(CAPTURE_SETTLE_MS);
  await page.screenshot({ path: path.join(directory, `${name}.png`), fullPage: options.fullPage ?? false });
}
