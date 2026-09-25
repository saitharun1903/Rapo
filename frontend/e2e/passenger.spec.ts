import { expect, test } from "@playwright/test";
import { clickMap, DEMO, demoPassword, gps, OUTSKIRTS, PUBLIC_SERVICES_TIMEOUT_MS, signIn } from "./support";

const BOOKING_MAP = "Map for choosing pickup and destination";
const REASON = "Plans changed";

// Out where no driver works, so the ride is still being matched when the passenger cancels it.
test.use({ geolocation: gps(OUTSKIRTS.southWest), permissions: ["geolocation"] });

test("a passenger books from their own location, cancels while matching, and sees it in their history", async ({ page }) => {
  await signIn(page, DEMO.cancellingPassenger, demoPassword(), /\/ride$/);

  await page.getByRole("button", { name: "Use my location" }).click();
  const chooseOnMap = page.getByRole("button", { name: "Choose on map" });
  // Both fields offer "Choose on map" once the pickup is set.
  await expect(chooseOnMap).toHaveCount(2, { timeout: PUBLIC_SERVICES_TIMEOUT_MS });
  await chooseOnMap.nth(1).click();
  // The map has zoomed to the pickup; a corner is well over the 200 m minimum trip away.
  await clickMap(page, BOOKING_MAP, 0.9, 0.15);

  const request = page.getByRole("button", { name: /^Request / });
  await expect(request).toBeEnabled({ timeout: PUBLIC_SERVICES_TIMEOUT_MS });
  await request.click();
  await expect(page.getByRole("heading", { name: /Request received|Finding you a driver/ })).toBeVisible();

  await page.getByRole("button", { name: "Cancel ride" }).click();
  const dialog = page.getByRole("dialog", { name: "Cancel this ride?" });
  await dialog.getByLabel("Reason (optional)").fill(REASON);
  await dialog.getByRole("button", { name: "Cancel ride" }).click();

  await expect(page.getByRole("heading", { name: "Ride cancelled" })).toBeVisible();
  await expect(page.getByText(`Cancelled by passenger: ${REASON}`)).toBeVisible();

  // Newest first: the ride just cancelled heads the history.
  await page.goto("/trips");
  await expect(page.locator('a[href^="/trips/"]').first()).toContainText("Cancelled");
});
