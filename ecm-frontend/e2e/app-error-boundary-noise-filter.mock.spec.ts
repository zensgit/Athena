import { expect, test } from '@playwright/test';

test('App error boundary: ignores ResizeObserver global error noise (mocked)', async ({ page }) => {
  test.setTimeout(120_000);

  await page.goto('/login', { waitUntil: 'domcontentloaded' });
  await expect(page.getByText('Sign in with your organization account')).toBeVisible({ timeout: 60_000 });

  await page.evaluate(() => {
    window.dispatchEvent(new ErrorEvent('error', { message: 'ResizeObserver loop limit exceeded' }));
  });

  await expect(page.getByText('The page encountered an unexpected error. You can refresh and try again.')).toHaveCount(0);
  await expect(page.getByText('Sign in with your organization account')).toBeVisible({ timeout: 60_000 });
  console.log('recovery_event:app_error_noise_resize_observer_ignored');
});

test('App error boundary: ignores abort-like unhandled rejection noise (mocked)', async ({ page }) => {
  test.setTimeout(120_000);

  await page.goto('/login', { waitUntil: 'domcontentloaded' });
  await expect(page.getByText('Sign in with your organization account')).toBeVisible({ timeout: 60_000 });

  await page.evaluate(() => {
    const rejectionEvent = new Event('unhandledrejection');
    Object.defineProperty(rejectionEvent, 'reason', {
      value: { name: 'AbortError', message: 'The operation was canceled', code: 'ERR_CANCELED' },
      configurable: true,
    });
    window.dispatchEvent(rejectionEvent);
  });

  await expect(page.getByText('The page encountered an unexpected error. You can refresh and try again.')).toHaveCount(0);
  await expect(page.getByText('Sign in with your organization account')).toBeVisible({ timeout: 60_000 });
  console.log('recovery_event:app_error_noise_abort_rejection_ignored');
});
