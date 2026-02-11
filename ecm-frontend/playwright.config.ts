import { defineConfig, devices } from '@playwright/test';
import { spawnSync } from 'child_process';

const parsedWorkers = Number(process.env.ECM_E2E_WORKERS);
const workerCount = Number.isFinite(parsedWorkers)
  ? parsedWorkers
  : (process.env.CI ? 2 : 1);

const isHttpReachable = (url: string): boolean => {
  const result = spawnSync(
    'curl',
    ['-sS', '-o', '/dev/null', '-w', '%{http_code}', '--max-time', '1.5', url],
    { encoding: 'utf-8' }
  );
  if (result.status !== 0) {
    return false;
  }
  const code = Number((result.stdout || '').trim());
  return Number.isFinite(code) && code >= 200 && code < 500;
};

const resolveBaseUrl = (): string => {
  if (process.env.ECM_UI_URL) {
    return process.env.ECM_UI_URL;
  }
  const candidates = ['http://localhost:3000', 'http://localhost:5500'];
  for (const candidate of candidates) {
    if (isHttpReachable(candidate)) {
      return candidate;
    }
  }
  return 'http://localhost:5500';
};

const baseURL = resolveBaseUrl();
if (!process.env.ECM_UI_URL) {
  // Keep local runs explicit to avoid testing stale deployed UI by accident.
  // eslint-disable-next-line no-console
  console.log(`[playwright] ECM_UI_URL not set, using auto-detected baseURL: ${baseURL}`);
}

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
    baseURL,
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
