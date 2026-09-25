import { expect, test } from "@playwright/test";
import { clickMap, DEMO, demoPassword, PUBLIC_SERVICES_TIMEOUT_MS, signIn } from "./support";

const BOOKING_MAP = "Map for choosing pickup and destination";
/** The simulator's drivers accept after a short delay, drive to the pickup, wait for boarding, then drive. */
const RIDE_TIMEOUT_MS = 6 * 60_000;
const MATCH_TIMEOUT_MS = 60_000;

test("a new passenger can register and reach the booking screen", async ({ page }) => {
  const email = `e2e.${Date.now()}@rideflow.example.com`;
  await page.goto("/register");
  await page.getByLabel("Full name").fill("E2E Passenger");
  await page.getByLabel("Email").fill(email);
  await page.getByLabel("Password").fill("e2e-password-2026");
  await page.getByRole("button", { name: "Create account" }).click();

  await expect(page).toHaveURL(/\/ride$/);
  await expect(page.getByRole("heading", { name: "Where to?" })).toBeVisible();
});

test("a passenger books a ride that a simulated driver completes, then sees the trip", async ({ page }) => {
  await signIn(page, DEMO.passenger, demoPassword(), /\/ride$/);
  await expect(page.getByRole("heading", { name: "Where to?" })).toBeVisible();

  const chooseOnMap = page.getByRole("button", { name: "Choose on map" });
  await chooseOnMap.first().click();
  await clickMap(page, BOOKING_MAP, 0.45, 0.55);
  // Both fields offer "Choose on map" again only once the pickup is set.
  await expect(chooseOnMap).toHaveCount(2, { timeout: PUBLIC_SERVICES_TIMEOUT_MS });
  await chooseOnMap.nth(1).click();
  await clickMap(page, BOOKING_MAP, 0.65, 0.3);

  const request = page.getByRole("button", { name: /^Request / });
  await expect(request).toBeEnabled({ timeout: PUBLIC_SERVICES_TIMEOUT_MS });
  await request.click();

  // Matching, then the live ride view with the assigned driver.
  await expect(page.getByRole("heading", { name: /Driver assigned|Your driver is on the way|Your driver has arrived|On the way/ }))
    .toBeVisible({ timeout: MATCH_TIMEOUT_MS });
  await expect(page.getByRole("heading", { name: "You have arrived" })).toBeVisible({ timeout: RIDE_TIMEOUT_MS });

  // Rate the driver, then open the trip with its computed observations.
  await page.getByRole("button", { name: "5 stars" }).click();
  await page.getByRole("button", { name: "Submit rating" }).click();
  await expect(page.getByText("Thanks, your rating is saved.")).toBeVisible();
  await page.getByRole("link", { name: "Trip details and insights" }).click();
  await expect(page.getByRole("heading", { name: "From your trip data" })).toBeVisible();
  await expect(page.getByRole("heading", { name: "Fare breakdown" })).toBeVisible();
});

test("an admin sees the platform's rides and system state", async ({ page }) => {
  await signIn(page, DEMO.admin, demoPassword(), /\/admin$/);
  await expect(page.getByText("Rides requested", { exact: true })).toBeVisible();

  await page.getByRole("link", { name: "Rides", exact: true }).click();
  await expect(page.getByRole("table", { name: "Rides" })).toBeVisible();
  await expect(page.getByRole("table", { name: "Rides" }).getByText("Completed").first()).toBeVisible();

  await page.getByRole("link", { name: "System" }).click();
  await expect(page.getByText("Health components")).toBeVisible();
});
