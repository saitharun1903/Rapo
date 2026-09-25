import { defineConfig, devices } from "@playwright/test";

/**
 * End-to-end smoke tests against a running stack: backend (demo profile), infrastructure and the driver
 * simulator are started by the e2e workflow (or by hand, see docs/development.md). Playwright starts the
 * built frontend itself.
 */
const PORT = 3000;
const BASE_URL = `http://localhost:${PORT}`;
const SERVER_START_TIMEOUT_MS = 120_000;
const TEST_TIMEOUT_MS = 8 * 60_000;

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
  projects: [{ name: "chromium", use: { ...devices["Desktop Chrome"], viewport: { width: 1440, height: 900 } } }],
  webServer: {
    command: "npm run start",
    url: BASE_URL,
    reuseExistingServer: !process.env.CI,
    timeout: SERVER_START_TIMEOUT_MS,
  },
});
