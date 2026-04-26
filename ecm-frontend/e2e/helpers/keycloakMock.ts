import type { Page } from '@playwright/test';

/**
 * Short-circuit Keycloak network calls so static-serve e2e specs
 * (Phase 5 Mocked Regression Gate) don't hang waiting for a Keycloak
 * server that doesn't exist in their environment.
 *
 * Returns immediate 401 to any request under `/realms/**`. The
 * keycloak-js adapter sees that on `.well-known/openid-configuration`
 * (and any subsequent endpoint) and gives up fast, leaving the app
 * in the unauthenticated boot state. /login then renders its sign-in
 * shell within seconds instead of timing out at 60s.
 *
 * Usage:
 *
 *   import { mockKeycloakUnreachable } from './helpers/keycloakMock';
 *   await mockKeycloakUnreachable(page);
 *   await page.goto('/login', { waitUntil: 'domcontentloaded' });
 *   // /login now renders fast; tests exercising the unauth flow work.
 *
 * Use this for tests where the unauth /login flow IS the subject under
 * test. For tests where the host page is incidental, prefer
 * `seedBypassSessionE2E` (sets `ecm_e2e_bypass=1` so auth init is
 * skipped entirely and the user lands on the authenticated home).
 *
 * See `docs/P5_PHASE5_MOCKED_GATE_INVESTIGATION_DEV_VERIFICATION_20260426.md`
 * for the systemic rationale.
 */
export async function mockKeycloakUnreachable(page: Page): Promise<void> {
  await page.route('**/realms/**', async (route) => {
    await route.fulfill({
      status: 401,
      contentType: 'application/json',
      body: JSON.stringify({ error: 'unauthorized', error_description: 'no session' }),
    });
  });
}
