import { expect, test } from '@playwright/test';

const E2E_FORCE_AUTH_BOOT_HANG_KEY = 'ecm_e2e_force_auth_boot_hang';
const E2E_AUTH_BOOT_WATCHDOG_MS_KEY = 'ecm_e2e_auth_boot_watchdog_ms';

test('Auth boot watchdog: forced startup hang can recover via continue-to-login action (mocked)', async ({ page }) => {
  test.setTimeout(120_000);

  await page.addInitScript(
    ({ forceHangKey, watchdogMsKey }) => {
      try {
        localStorage.setItem(forceHangKey, '1');
        localStorage.setItem(watchdogMsKey, '1200');
      } catch {
        // Best effort in restrictive browser contexts.
      }
    },
    {
      forceHangKey: E2E_FORCE_AUTH_BOOT_HANG_KEY,
      watchdogMsKey: E2E_AUTH_BOOT_WATCHDOG_MS_KEY,
    }
  );

  await page.goto('/browse/root', { waitUntil: 'domcontentloaded' });
  await expect(page.getByTestId('auth-booting-screen')).toBeVisible({ timeout: 60_000 });
  await expect(page.getByTestId('auth-booting-watchdog-alert')).toBeVisible({ timeout: 60_000 });
  await expect(page.getByTestId('auth-booting-watchdog-reload')).toBeVisible();
  await expect(page.getByTestId('auth-booting-watchdog-continue-login')).toBeVisible();

  await page.getByTestId('auth-booting-watchdog-continue-login').click();

  await expect(page).toHaveURL(/\/login($|[?#])/, { timeout: 60_000 });
  await expect(page.getByRole('heading', { name: /Athena ECM/i })).toBeVisible({ timeout: 60_000 });
  await expect(page.getByText('Sign-in initialization timed out. Please retry.')).toBeVisible({ timeout: 60_000 });
});
