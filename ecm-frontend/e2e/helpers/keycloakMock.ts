import type { Page } from '@playwright/test';

// Short-circuit Keycloak network calls so static-serve e2e specs
// (Phase 5 Mocked Regression Gate) don't hang waiting for a Keycloak
// server that doesn't exist in their environment.
//
// Aborts every "/realms/" request - XHR, fetch, and top-level document
// navigations triggered by keycloak-js's window.location.href redirect
// to the auth URL. The browser treats the abort the same as a
// connection refused: the AJAX call fails fast (so keycloak-js's init
// rejects), and any redirect to the auth endpoint never completes.
//
// Earlier route.fulfill approach intercepted the top-level redirect
// and rendered the JSON body as the page (see PR-151's CI artifact
// error-context.md showing the unauthorized JSON as the page
// snapshot). Aborting matches "Keycloak truly unreachable" semantics.
//
// Usage:
//   import { mockKeycloakUnreachable } from './helpers/keycloakMock';
//   await mockKeycloakUnreachable(page);
//   await page.goto('/login', { waitUntil: 'domcontentloaded' });
//
// Use this for tests where the unauth /login flow IS the subject
// under test. For tests where the host page is incidental, prefer
// seedBypassSessionE2E (sets ecm_e2e_bypass=1 so auth init is
// skipped entirely and the user lands on the authenticated home).
//
// See docs/P5_PHASE5_MOCKED_GATE_INVESTIGATION_DEV_VERIFICATION_20260426.md
// for the systemic rationale.
//
// IMPORTANT: do NOT use a JSDoc /* * ... */ block here. The previous
// version had "*-asterisk-asterisk-/-realms-/-asterisk-asterisk*" as
// markdown emphasis around the Playwright glob; the inner asterisk-/
// inside the JSDoc TERMINATED the comment early, leaving "realms"
// dangling as an undefined identifier. The result was a runtime
// ReferenceError at module-import time, breaking EVERY spec that
// imported this helper (CI evidence: beca1cf Phase 5 Mocked
// completed in seconds with all specs failing to load).
export async function mockKeycloakUnreachable(page: Page): Promise<void> {
  await page.route('**/realms/**', async (route) => {
    await route.abort('connectionfailed');
  });
}
