import { defineConfig, devices } from '@playwright/test';

const parsedWorkers = Number(process.env.ECM_E2E_WORKERS);
const workerCount = Number.isFinite(parsedWorkers)
  ? parsedWorkers
  : (process.env.CI ? 2 : 1);

export default defineConfig({
  testDir: './e2e',
  timeout: 120_000,
  expect: {
    timeout: 30_000,
  },
  retries: process.env.CI ? 2 : 0,
  workers: workerCount,
  reporter: [['list'], ['html', { open: 'never' }]],
  use: {
    baseURL: process.env.ECM_UI_URL || 'http://localhost:5500',
    screenshot: 'only-on-failure',
    trace: 'retain-on-failure',
    video: 'retain-on-failure',
  },
  projects: [
    {
      name: 'chromium',
      use: {
        ...devices['Desktop Chrome'],
      },
    },
  ],
});
