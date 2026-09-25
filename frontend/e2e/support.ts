import { expect, type Page } from "@playwright/test";

/** The password the backend's demo profile gave every seeded account (env DEMO_USER_PASSWORD). */
export function demoPassword(): string {
  const password = process.env.DEMO_USER_PASSWORD;
  if (!password) {
    throw new Error("Set DEMO_USER_PASSWORD to the backend's demo password");
  }
  return password;
}

export const DEMO = {
  admin: "admin@rideflow.example.com",
  passenger: "ananya@rideflow.example.com",
} as const;

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
