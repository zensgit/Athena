import { expect, test } from '@playwright/test';

const E2E_FORCE_BOOTSTRAP_BLANK_KEY = 'ecm_e2e_force_bootstrap_blank';
const E2E_BOOTSTRAP_FALLBACK_MS_KEY = 'ecm_e2e_bootstrap_fallback_ms';

test('Startup fallback: forced blank bootstrap shows recovery overlay and can return to login', async ({ page }) => {
  test.setTimeout(120_000);

  await page.addInitScript(
    ({ forceKey, timeoutKey }) => {
      try {
        localStorage.setItem(forceKey, '1');
        localStorage.setItem(timeoutKey, '600');
      } catch {
        // Ignore storage restrictions for this test setup.
      }
    },
    {
      forceKey: E2E_FORCE_BOOTSTRAP_BLANK_KEY,
      timeoutKey: E2E_BOOTSTRAP_FALLBACK_MS_KEY,
    }
  );

  await page.goto('/login', { waitUntil: 'domcontentloaded' });

  const fallback = page.getByTestId('bootstrap-startup-fallback');
  await expect(fallback).toBeVisible({ timeout: 60_000 });
  await expect(page.getByTestId('bootstrap-startup-fallback-message'))
    .toContainText('Application startup is taking longer than expected');
  await expect(page.getByRole('button', { name: /reload/i })).toBeVisible();
  await expect(page.getByRole('button', { name: /back to login/i })).toBeVisible();
  console.log('recovery_event:startup_fallback_overlay_shown');

  await page.evaluate(
    ({ forceKey, timeoutKey }) => {
      try {
        localStorage.removeItem(forceKey);
        localStorage.removeItem(timeoutKey);
      } catch {
        // Ignore storage restrictions for this test cleanup.
      }
    },
    {
      forceKey: E2E_FORCE_BOOTSTRAP_BLANK_KEY,
      timeoutKey: E2E_BOOTSTRAP_FALLBACK_MS_KEY,
    }
  );

  await page.getByRole('button', { name: /back to login/i }).click();

  await expect(page).toHaveURL(/\/login(?:\?reason=startup_recovery)?$/, { timeout: 60_000 });
  await expect(page.getByTestId('login-auth-status-card')).toContainText('Recovered from startup timeout');
  console.log('recovery_event:startup_fallback_back_to_login');
});

test('Startup fallback: reload uses cache-busting query and restores login shell', async ({ page }) => {
  test.setTimeout(120_000);

  await page.addInitScript(
    ({ forceKey, timeoutKey }) => {
      try {
        localStorage.setItem(forceKey, '1');
        localStorage.setItem(timeoutKey, '600');
      } catch {
        // Ignore storage restrictions for this test setup.
      }
    },
    {
      forceKey: E2E_FORCE_BOOTSTRAP_BLANK_KEY,
      timeoutKey: E2E_BOOTSTRAP_FALLBACK_MS_KEY,
    }
  );

  await page.goto('/login', { waitUntil: 'domcontentloaded' });
  await expect(page.getByTestId('bootstrap-startup-fallback')).toBeVisible({ timeout: 60_000 });

  await page.evaluate(
    ({ forceKey, timeoutKey }) => {
      try {
        localStorage.removeItem(forceKey);
        localStorage.removeItem(timeoutKey);
      } catch {
        // Ignore storage restrictions for this test cleanup.
      }
    },
    {
      forceKey: E2E_FORCE_BOOTSTRAP_BLANK_KEY,
      timeoutKey: E2E_BOOTSTRAP_FALLBACK_MS_KEY,
    }
  );

  await page.getByRole('button', { name: /reload/i }).click();
  await page.waitForURL(/_ecm_reload=\d+/, { timeout: 60_000 });
  await expect.poll(() => page.url()).not.toContain('_ecm_reload=');
  await expect(page.getByText('Sign in with your organization account')).toBeVisible({ timeout: 60_000 });
  console.log('recovery_event:startup_fallback_reload_cache_bust');
});

test('Startup fallback: normal startup does not show fallback overlay', async ({ page }) => {
  test.setTimeout(120_000);

  await page.addInitScript(
    ({ forceKey, timeoutKey }) => {
      try {
        localStorage.removeItem(forceKey);
        localStorage.setItem(timeoutKey, '3000');
      } catch {
        // Ignore storage restrictions for this test setup.
      }
    },
    {
      forceKey: E2E_FORCE_BOOTSTRAP_BLANK_KEY,
      timeoutKey: E2E_BOOTSTRAP_FALLBACK_MS_KEY,
    }
  );

  await page.goto('/login', { waitUntil: 'domcontentloaded' });
  await expect(page.getByText('Sign in with your organization account')).toBeVisible({ timeout: 60_000 });

  await page.waitForTimeout(3800);
  await expect(page.getByTestId('bootstrap-startup-fallback')).toHaveCount(0);
  console.log('recovery_event:startup_fallback_not_shown_normal');

  await page.evaluate(({ timeoutKey }) => {
    try {
      localStorage.removeItem(timeoutKey);
    } catch {
      // Ignore storage restrictions for this test cleanup.
    }
  }, { timeoutKey: E2E_BOOTSTRAP_FALLBACK_MS_KEY });
});
