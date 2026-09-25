import { expect, test, type Page } from "@playwright/test";
import { PassengerApi } from "./passengerApi";
import { DEMO, demoPassword, gps, north, OUTSKIRTS, signIn, unique, type Point } from "./support";

/**
 * A driver's whole first day in the browser: they register, submit their licence and vehicle, an admin verifies
 * them in the admin console, they go online from the device's GPS (emulated), accept a ride offered over the
 * socket, drive it step by step and see it in their earnings. They work in the city's outskirts, so the
 * simulator's drivers never get this ride first.
 */
const START = OUTSKIRTS.north;
/** Inside the 150 m arrival geofence from where the driver starts. */
const PICKUP = north(START, 60);
const DROPOFF = north(START, 2_500);
/** GPS fixes on the way; the console reports its latest fix every 4 s. */
const ROUTE_STEPS = 4;
const REPORT_INTERVAL_MS = 4_000;
const REPORT_MARGIN_MS = 1_500;
const OFFER_TIMEOUT_MS = 60_000;
const PAYMENT_TIMEOUT_MS = 60_000;

test.use({ geolocation: gps(START), permissions: ["geolocation"] });

/** Moves the emulated GPS and waits until the console has reported the new position at least once. */
async function driveTo(page: Page, point: Point): Promise<void> {
  await page.context().setGeolocation(gps(point));
  await page.waitForTimeout(REPORT_INTERVAL_MS + REPORT_MARGIN_MS);
}

async function step(page: Page, action: string, heading: string): Promise<void> {
  await page.getByRole("button", { name: action }).click();
  await expect(page.getByRole("heading", { name: heading })).toBeVisible();
}

test("a new driver is verified, goes online, and completes a ride from offer to earnings", async ({ page, browser, request, baseURL }) => {
  const id = unique();
  const name = `E2E Driver ${id}`;
  const email = `e2e.driver.${id}@rideflow.example.com`;

  // Registration leads straight to onboarding.
  await page.goto("/register?as=driver");
  await page.getByLabel("Full name").fill(name);
  await page.getByLabel("Email").fill(email);
  await page.getByLabel("Password").fill("e2e-password-2026");
  await page.getByRole("button", { name: "Create account" }).click();
  await expect(page).toHaveURL(/\/drive\/onboarding$/);

  await page.getByLabel("Driving licence number").fill(`E2E-${id}`);
  await page.getByLabel("Make").fill("Maruti Suzuki");
  await page.getByLabel("Model").fill("Dzire");
  await page.getByLabel("Colour").fill("White");
  await page.getByLabel("Plate number").fill(`E2E ${id.slice(-8)}`);
  await page.getByLabel("Model year").fill(String(new Date().getFullYear() - 1));
  await page.getByRole("button", { name: "Submit for verification" }).click();
  await expect(page.getByText("Submitted. An admin will review your profile.")).toBeVisible();

  // An admin verifies the application in their own session.
  const adminContext = await browser.newContext({ baseURL });
  try {
    const admin = await adminContext.newPage();
    await signIn(admin, DEMO.admin, demoPassword(), /\/admin$/);
    await admin.goto("/admin/drivers");
    const row = admin.getByRole("row").filter({ hasText: email });
    await row.getByRole("button", { name: "Verify" }).click();
    await expect(admin.getByText(`${name} is verified.`)).toBeVisible();
  } finally {
    await adminContext.close();
  }

  // Online from the device's position.
  await page.goto("/drive");
  await expect(page.getByText("From this device's GPS")).toBeVisible();
  await page.getByRole("button", { name: "Go online" }).click();
  await expect(page.getByRole("heading", { name: "You are online" })).toBeVisible();

  // A passenger books next to the driver; matching offers the ride over the driver's socket.
  const passenger = await PassengerApi.signIn(request, DEMO.driverTestPassenger, demoPassword());
  const rideId = await passenger.book(PICKUP, DROPOFF);
  const offers = page.getByRole("region", { name: "Ride offers" });
  await offers.getByRole("button", { name: "Accept" }).click({ timeout: OFFER_TIMEOUT_MS });
  await expect(page.getByRole("heading", { name: "Driver assigned" })).toBeVisible();

  await step(page, "Start driving to pickup", "Driver arriving");
  await driveTo(page, PICKUP);
  await step(page, "I have arrived", "Driver arrived");
  await step(page, "Start trip", "In progress");
  for (let index = 1; index <= ROUTE_STEPS; index++) {
    await driveTo(page, {
      lat: PICKUP.lat + ((DROPOFF.lat - PICKUP.lat) * index) / ROUTE_STEPS,
      lng: PICKUP.lng + ((DROPOFF.lng - PICKUP.lng) * index) / ROUTE_STEPS,
    });
  }
  await page.getByRole("button", { name: "Complete trip" }).click();
  await expect(page.getByRole("heading", { name: "Trip complete" })).toBeVisible();
  expect(await passenger.rideStatus(rideId)).toBe("COMPLETED");

  // The driver rates the passenger, then finds the trip in their earnings once the payment is settled.
  await page.getByRole("button", { name: "5 stars" }).click();
  await page.getByRole("button", { name: "Submit rating" }).click();
  await expect(page.getByText("Thanks, your rating is saved.")).toBeVisible();
  await page.goto("/drive/earnings");
  await expect(async () => {
    await page.reload();
    await expect(page.getByText("Earned")).toBeVisible();
    await expect(page.getByText("No completed trips in this period")).toHaveCount(0);
  }).toPass({ timeout: PAYMENT_TIMEOUT_MS });
});
