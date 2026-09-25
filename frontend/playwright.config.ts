import { defineConfig, devices } from "@playwright/test";

/**
 * End-to-end tests against a running stack: infrastructure, the backend (demo profile) and the driver
 * simulator. With PLAYWRIGHT_BASE_URL set, the frontend is already running there too (the e2e workflow tests
 * the Docker Compose stack this way); otherwise Playwright starts the built frontend itself.
 */
const LOCAL_PORT = 3000;
const EXTERNAL_BASE_URL = process.env.PLAYWRIGHT_BASE_URL;
const BASE_URL = EXTERNAL_BASE_URL ?? `http://localhost:${LOCAL_PORT}`;
const SERVER_START_TIMEOUT_MS = 120_000;
const TEST_TIMEOUT_MS = 8 * 60_000;
const DESKTOP = { ...devices["Desktop Chrome"], viewport: { width: 1440, height: 900 } };
/** The Grafana dashboards, after the app's tests have made rides; only where Grafana runs (the e2e workflow). */
const DASHBOARDS = /dashboards\.spec\.ts/;

export default defineConfig({
  testDir: "./e2e",
  timeout: TEST_TIMEOUT_MS,
  fullyParallel: false,
  workers: 1,
  retries: 0,
  forbidOnly: Boolean(process.env.CI),
  // In CI, "github" turns each failure into an annotation on the run page.
  reporter: process.env.CI ? [["list"], ["github"], ["html", { open: "never" }]] : "list",
  use: {
    baseURL: BASE_URL,
    trace: "retain-on-failure",
    screenshot: "only-on-failure",
    viewport: { width: 1440, height: 900 },
  },
  projects: [
    { name: "chromium", use: DESKTOP, testIgnore: DASHBOARDS },
    ...(process.env.GRAFANA_URL
      ? [{ name: "dashboards", use: DESKTOP, testMatch: DASHBOARDS, dependencies: ["chromium"] }]
      : []),
  ],
  webServer: EXTERNAL_BASE_URL ? undefined : {
    command: "npm run start",
    url: BASE_URL,
    reuseExistingServer: !process.env.CI,
    timeout: SERVER_START_TIMEOUT_MS,
  },
});
