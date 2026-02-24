import { expect, test } from '@playwright/test';

test('App error boundary: chunk-load failure shows asset-refresh recovery hint (mocked)', async ({ page }) => {
  test.setTimeout(120_000);

  await page.goto('/login', { waitUntil: 'domcontentloaded' });
  await expect(page.getByText('Sign in with your organization account')).toBeVisible({ timeout: 60_000 });

  await page.evaluate(() => {
    const rejectionEvent = new Event('unhandledrejection');
    Object.defineProperty(rejectionEvent, 'reason', {
      value: new Error('Loading chunk 99 failed.'),
      configurable: true,
    });
    window.dispatchEvent(rejectionEvent);
  });

  await expect(page.getByText('The page encountered an unexpected error. You can refresh and try again.'))
    .toBeVisible({ timeout: 60_000 });
  await expect(page.getByText('Application files may be outdated after an update. Reload to fetch the latest assets.'))
    .toBeVisible({ timeout: 60_000 });
  await expect(page.getByRole('button', { name: /reload/i })).toBeVisible();
  await expect(page.getByRole('button', { name: /back to login/i })).toBeVisible();
  console.log('recovery_event:chunk_load_hint_shown');
});

test('App error boundary: chunk-load reload uses cache-busting query (mocked)', async ({ page }) => {
  test.setTimeout(120_000);

  await page.goto('/login', { waitUntil: 'domcontentloaded' });
  await expect(page.getByText('Sign in with your organization account')).toBeVisible({ timeout: 60_000 });

  await page.evaluate(() => {
    const rejectionEvent = new Event('unhandledrejection');
    Object.defineProperty(rejectionEvent, 'reason', {
      value: new Error('ChunkLoadError: Loading chunk 12 failed.'),
      configurable: true,
    });
    window.dispatchEvent(rejectionEvent);
  });

  await expect(page.getByText('The page encountered an unexpected error. You can refresh and try again.'))
    .toBeVisible({ timeout: 60_000 });
  await page.getByRole('button', { name: /reload/i }).click();

  await expect(page).toHaveURL(/_ecm_reload=\d+/, { timeout: 60_000 });
  await expect(page.getByText('Sign in with your organization account')).toBeVisible({ timeout: 60_000 });
  console.log('recovery_event:chunk_load_reload_cache_bust');
});
