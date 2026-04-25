import { expect, test } from '@playwright/test';
import { seedBypassSessionE2E } from './helpers/login';

// The global error boundary noise filter is a page-agnostic behaviour
// (it filters specific noise event types regardless of the route the
// user is on). Phase 5 Mocked runs `serve -s build` with no Keycloak,
// so navigating to `/login` and waiting for the unauth login text
// never resolves — the auth boot path hangs. Using `seedBypassSessionE2E`
// puts us on an authenticated route immediately; the noise filter
// behaviour under test is identical.
//
// See `docs/P5_PHASE5_MOCKED_GATE_INVESTIGATION_DEV_VERIFICATION_20260426.md`
// for the systemic cause and the multi-spec rollout plan.

test('App error boundary: ignores ResizeObserver global error noise (mocked)', async ({ page }) => {
  test.setTimeout(120_000);

  await seedBypassSessionE2E(page, 'admin', 'e2e-token');
  await page.goto('/', { waitUntil: 'domcontentloaded' });
  await expect(page.getByRole('button', { name: 'Account menu' })).toBeVisible({ timeout: 60_000 });

  await page.evaluate(() => {
    window.dispatchEvent(new ErrorEvent('error', { message: 'ResizeObserver loop limit exceeded' }));
  });

  await expect(page.getByText('The page encountered an unexpected error. You can refresh and try again.')).toHaveCount(0);
  await expect(page.getByRole('button', { name: 'Account menu' })).toBeVisible();
  console.log('recovery_event:app_error_noise_resize_observer_ignored');
});

test('App error boundary: ignores abort-like unhandled rejection noise (mocked)', async ({ page }) => {
  test.setTimeout(120_000);

  await seedBypassSessionE2E(page, 'admin', 'e2e-token');
  await page.goto('/', { waitUntil: 'domcontentloaded' });
  await expect(page.getByRole('button', { name: 'Account menu' })).toBeVisible({ timeout: 60_000 });

  await page.evaluate(() => {
    const rejectionEvent = new Event('unhandledrejection');
    Object.defineProperty(rejectionEvent, 'reason', {
      value: { name: 'AbortError', message: 'The operation was canceled', code: 'ERR_CANCELED' },
      configurable: true,
    });
    window.dispatchEvent(rejectionEvent);
  });

  await expect(page.getByText('The page encountered an unexpected error. You can refresh and try again.')).toHaveCount(0);
  await expect(page.getByRole('button', { name: 'Account menu' })).toBeVisible();
  console.log('recovery_event:app_error_noise_abort_rejection_ignored');
});
