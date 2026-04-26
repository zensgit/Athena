import { expect, test } from '@playwright/test';
import { mockKeycloakUnreachable } from './helpers/keycloakMock';
import { seedBypassSessionE2E } from './helpers/login';

// Chunk-load error handling is a page-agnostic global behaviour
// (the unhandledrejection listener catches ChunkLoadError on every
// route). Phase 5 Mocked runs `serve -s build` with no Keycloak,
// so navigating to `/login` and waiting for the unauth login text
// never resolves — see
// `docs/P5_PHASE5_MOCKED_GATE_INVESTIGATION_DEV_VERIFICATION_20260426.md`.
//
// The hint test uses `seedBypassSessionE2E` because the host page is
// incidental. The cache-bust test uses `mockKeycloakUnreachable`
// because its assertions hinge on the natural unauth /login timing —
// bypass makes `/` load fast enough that the `_ecm_reload=<ts>` query
// param can be stripped by the index.html cleanup before
// `waitForURL` catches it (8410eaf evidence: cache-bust failed 3×
// at 1.1m while the hint test passed in 762ms).

test('App error boundary: chunk-load failure shows asset-refresh recovery hint (mocked)', async ({ page }) => {
  test.setTimeout(120_000);

  await seedBypassSessionE2E(page, 'admin', 'e2e-token');
  await page.goto('/', { waitUntil: 'domcontentloaded' });
  await expect(page.getByRole('button', { name: 'Account menu' })).toBeVisible({ timeout: 60_000 });

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

  await mockKeycloakUnreachable(page);
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

  await page.waitForURL(/_ecm_reload=\d+/, { timeout: 60_000 });
  await expect.poll(() => page.url()).not.toContain('_ecm_reload=');
  await expect(page.getByText('Sign in with your organization account')).toBeVisible({ timeout: 60_000 });
  console.log('recovery_event:chunk_load_reload_cache_bust');
});
