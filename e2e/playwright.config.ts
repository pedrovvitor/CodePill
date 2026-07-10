import { defineConfig, devices } from '@playwright/test'

/**
 * CodePill E2E (TESTING_QUALITY.md §3.3): critical user journeys against a
 * real stack — Keycloak + PostgreSQL + Redis (docker compose) and the catalog
 * service on :8080 must be running; the frontend preview server is started
 * here. Selectors are getByRole/getByLabel only; no fixed sleeps.
 */
export default defineConfig({
  testDir: './tests',
  timeout: 90_000,
  expect: { timeout: 15_000 },
  // Serial on purpose: Keycloak's brute-force quick-login check rejects
  // logins for the same user arriving <1s apart from one IP, so parallel
  // workers signing in as dev-author are inherently racy.
  fullyParallel: false,
  workers: 1,
  // Flaky tests are quarantined/fixed, not retried into green (§3.3).
  retries: 0,
  reporter: [['list'], ['html', { open: 'never' }]],
  use: {
    baseURL: process.env.E2E_BASE_URL ?? 'http://localhost:5173',
    trace: 'retain-on-failure',
    screenshot: 'only-on-failure',
  },
  projects: [
    {
      // The UI is mobile-first — run the journeys in a mobile viewport.
      name: 'mobile-chromium',
      use: { ...devices['Pixel 7'] },
    },
  ],
  webServer: {
    // Production build served by vite preview on the OIDC-registered port.
    command: 'pnpm --dir ../frontend build && pnpm --dir ../frontend preview --port 5173 --strictPort',
    url: 'http://localhost:5173',
    reuseExistingServer: !process.env.CI,
    timeout: 180_000,
  },
})
