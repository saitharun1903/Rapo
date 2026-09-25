import { expect, test } from "@playwright/test";
import { capture } from "./support";

/**
 * The provisioned Grafana dashboards as an operator sees them, after the other tests' rides: each one renders
 * in the browser. Whether every panel's query returns data is checked separately (check_dashboards.py). Runs
 * only where Grafana is (GRAFANA_URL and GRAFANA_PASSWORD, set by the e2e workflow); playwright.config.ts adds
 * this project then.
 */
const DASHBOARDS = [
  { uid: "rideflow-service", panel: "p95 latency", name: "grafana-service-overview" },
  { uid: "rideflow-rides", panel: "Time to match (p95)", name: "grafana-ride-pipeline" },
  { uid: "rideflow-realtime", panel: "WebSocket sessions by role", name: "grafana-realtime" },
] as const;
const RANGE = "from=now-30m&to=now";

function setting(name: string): string {
  const value = process.env[name];
  if (!value) {
    throw new Error(`Set ${name}`);
  }
  return value;
}

test("the provisioned dashboards render with the rides of this run", async ({ page }) => {
  const grafana = setting("GRAFANA_URL");
  await page.goto(`${grafana}/login`);
  await page.locator('input[name="user"]').fill("admin");
  await page.locator('input[name="password"]').fill(setting("GRAFANA_PASSWORD"));
  await page.getByRole("button", { name: "Log in" }).click();
  await expect(page).not.toHaveURL(/\/login/);

  for (const dashboard of DASHBOARDS) {
    // Kiosk mode: the panels only, as on a wall screen.
    await page.goto(`${grafana}/d/${dashboard.uid}?orgId=1&${RANGE}&kiosk`);
    await expect(page.getByText(dashboard.panel, { exact: true }).first()).toBeVisible();
    await capture(page, dashboard.name);
  }
});
