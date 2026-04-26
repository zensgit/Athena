import { expect, test } from '@playwright/test';
import { mockKeycloakUnreachable } from './helpers/keycloakMock';

const FORCE_RENDER_ERROR_KEY = 'ecm_e2e_force_render_error';

// This test deliberately exercises the unauth /login → forced render
// crash → "Back to Login" recovery flow. Phase 5 Mocked runs
// `serve -s build` with no Keycloak server, so a real /login boot
// hangs on Keycloak. seedBypassSessionE2E would change the test
// intent (user logged in on /, no login UI to return to). Instead,
// short-circuit Keycloak with mockKeycloakUnreachable so the auth
// boot resolves into the no-session state quickly and the unauth
// /login shell renders within seconds.
//
// See `docs/P5_PHASE5_MOCKED_GATE_INVESTIGATION_DEV_VERIFICATION_20260426.md`.

test('App error boundary: forced render crash can recover to login', async ({ page }) => {
  test.setTimeout(120_000);

  await mockKeycloakUnreachable(page);
  await page.goto('/login', { waitUntil: 'domcontentloaded' });
  await expect(page.getByText('Sign in with your organization account')).toBeVisible({ timeout: 60_000 });
  await page.evaluate((key) => {
    try {
      localStorage.setItem(key, '1');
    } catch {
      // Ignore storage restrictions in edge browser contexts.
    }
  }, FORCE_RENDER_ERROR_KEY);

  await page.goto('/browse/root', { waitUntil: 'domcontentloaded' });

  await expect(page.getByText('The page encountered an unexpected error. You can refresh and try again.'))
    .toBeVisible({ timeout: 60_000 });
  await expect(page.getByRole('button', { name: /back to login/i })).toBeVisible();
  console.log('recovery_event:app_error_overlay_shown');

  await page.getByRole('button', { name: /back to login/i }).click();

  await expect(page).toHaveURL(/\/login(?:\?.*)?$/, { timeout: 60_000 });
  await expect(page.getByText('The page encountered an unexpected error. You can refresh and try again.')).toHaveCount(0);
  await expect(page.getByText('Sign in with your organization account')).toBeVisible({ timeout: 60_000 });
  await expect(page.getByTestId('login-auth-status-card')).toContainText('Recovered from unexpected app error');
  await expect(page.getByTestId('login-auth-status-card')).toContainText('returned to sign-in');
  console.log('recovery_event:app_error_back_to_login');

  await expect.poll(async () => page.evaluate((key) => {
    try {
      return localStorage.getItem(key);
    } catch {
      return null;
    }
  }, FORCE_RENDER_ERROR_KEY), { timeout: 15_000 }).toBeNull();
});
