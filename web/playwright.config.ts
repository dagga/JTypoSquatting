import { defineConfig, devices } from "@playwright/test";

// End-to-end tests against a production build. The backend is simulated in each test (e2e/mock-backend.ts).
export default defineConfig({
  testDir: "./e2e",
  fullyParallel: true,
  forbidOnly: !!process.env.CI,
  retries: process.env.CI ? 2 : 0,
  reporter: process.env.CI ? "github" : "list",
  use: {
    baseURL: "http://localhost:3100",
    trace: "retain-on-failure",
  },
  projects: [{ name: "chromium", use: { ...devices["Desktop Chrome"] } }],
  webServer: {
    command: "npm run build && npx next start -p 3100",
    url: "http://localhost:3100/fr",
    reuseExistingServer: !process.env.CI,
    timeout: 180_000,
  },
});
