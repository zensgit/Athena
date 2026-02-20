import { expect, Page, test } from '@playwright/test';
import { waitForApiReady } from './helpers/api';

const AUTH_REDIRECT_FAILURE_COUNT_KEY = 'ecm_auth_redirect_failure_count';
const AUTH_REDIRECT_LAST_FAILURE_AT_KEY = 'ecm_auth_redirect_last_failure_at';
const AUTH_INIT_STATUS_KEY = 'ecm_auth_init_status';
const AUTH_REDIRECT_REASON_KEY = 'ecm_auth_redirect_reason';
const LOGIN_IN_PROGRESS_KEY = 'ecm_kc_login_in_progress';
const LOGIN_IN_PROGRESS_STARTED_AT_KEY = 'ecm_kc_login_in_progress_started_at';
const AUTH_REDIRECT_MAX_AUTO_ATTEMPTS = 2;

const REDIRECT_PAUSE_MESSAGE_PATTERN = /Automatic sign-in (is paused after repeated failures|redirect failed)/i;

const seedRedirectPauseState = async (page: Page) => {
  await page.addInitScript(
    ({ countKey, countValue, initStatusKey, lastFailureKey }) => {
      try {
        sessionStorage.removeItem(initStatusKey);
        sessionStorage.setItem(countKey, String(countValue));
        sessionStorage.setItem(lastFailureKey, String(Date.now()));
      } catch {
        // Best effort in restrictive browser contexts.
      }
    },
    {
      countKey: AUTH_REDIRECT_FAILURE_COUNT_KEY,
      countValue: AUTH_REDIRECT_MAX_AUTO_ATTEMPTS,
      initStatusKey: AUTH_INIT_STATUS_KEY,
      lastFailureKey: AUTH_REDIRECT_LAST_FAILURE_AT_KEY,
    }
  );
};

const injectSessionStorageRemoveGuard = async (page: Page, blockedKey: string) => {
  await page.addInitScript(
    ({ key }) => {
      const storageProto = Object.getPrototypeOf(window.sessionStorage) as Storage & {
        removeItem: (storageKey: string) => void;
      };
      const originalRemove = storageProto.removeItem;
      storageProto.removeItem = function removeItemWithGuard(storageKey: string) {
        if (storageKey === key) {
          throw new DOMException('Storage blocked for test', 'SecurityError');
        }
        return originalRemove.call(this, storageKey);
      };
    },
    { key: blockedKey }
  );
};

const injectLocalStorageGetGuard = async (page: Page, blockedKey: string) => {
  await page.addInitScript(
    ({ key }) => {
      const storageProto = Object.getPrototypeOf(window.localStorage) as Storage & {
        getItem: (storageKey: string) => string | null;
      };
      const originalGet = storageProto.getItem;
      storageProto.getItem = function getItemWithGuard(storageKey: string) {
        if (storageKey === key) {
          throw new DOMException('Storage blocked for test', 'SecurityError');
        }
        return originalGet.call(this, storageKey);
      };
    },
    { key: blockedKey }
  );
};

const seedStaleLoginInProgress = async (page: Page) => {
  await page.addInitScript(
    ({ loginInProgressKey, loginStartedAtKey }) => {
      try {
        sessionStorage.setItem(loginInProgressKey, '1');
        sessionStorage.setItem(loginStartedAtKey, String(Date.now() - 5_000));
      } catch {
        // Best effort in restrictive browser contexts.
      }
    },
    {
      loginInProgressKey: LOGIN_IN_PROGRESS_KEY,
      loginStartedAtKey: LOGIN_IN_PROGRESS_STARTED_AT_KEY,
    }
  );
};

test.describe('Auth/Route recovery matrix', () => {
  test('matrix: login shows session-expired guidance from URL reason', async ({ page, request }) => {
    await waitForApiReady(request);
    await page.goto('/login?reason=session_expired', { waitUntil: 'domcontentloaded' });

    await expect(page.getByRole('heading', { name: /Athena ECM/i })).toBeVisible({ timeout: 60_000 });
    await expect(page.getByText('Your session expired. Please sign in again.')).toBeVisible({ timeout: 60_000 });
    await expect(page).toHaveURL(/\/login$/, { timeout: 60_000 });
  });

  test('matrix: login reason fallback remains visible when localStorage redirect reason read throws', async ({ page, request }) => {
    await waitForApiReady(request);
    await injectLocalStorageGetGuard(page, AUTH_REDIRECT_REASON_KEY);
    await page.goto('/login?reason=session_expired', { waitUntil: 'domcontentloaded' });

    await expect(page.getByRole('heading', { name: /Athena ECM/i })).toBeVisible({ timeout: 60_000 });
    await expect(page.getByText('Your session expired. Please sign in again.')).toBeVisible({ timeout: 60_000 });
    await expect(page).toHaveURL(/\/login$/, { timeout: 60_000 });
  });

  test('matrix: login shows redirect-pause guidance when auto-login exceeded limit', async ({ page, request }) => {
    await waitForApiReady(request);
    await seedRedirectPauseState(page);
    await page.goto('/browse/root', { waitUntil: 'domcontentloaded' });

    await expect(page).toHaveURL(/\/login($|[?#])/, { timeout: 60_000 });
    await expect(page.getByRole('heading', { name: /Athena ECM/i })).toBeVisible({ timeout: 60_000 });
    await expect(page.getByText(REDIRECT_PAUSE_MESSAGE_PATTERN)).toBeVisible({ timeout: 60_000 });
    await expect(page.getByRole('button', { name: /Sign in with Keycloak/i })).toBeVisible();
  });

  test('matrix: direct login shows redirect-pause guidance from fallback markers', async ({ page, request }) => {
    await waitForApiReady(request);
    await seedRedirectPauseState(page);
    await page.goto('/login', { waitUntil: 'domcontentloaded' });

    await expect(page).toHaveURL(/\/login($|[?#])/, { timeout: 60_000 });
    await expect(page.getByRole('heading', { name: /Athena ECM/i })).toBeVisible({ timeout: 60_000 });
    await expect(page.getByText(REDIRECT_PAUSE_MESSAGE_PATTERN)).toBeVisible({ timeout: 60_000 });
    await expect(page.getByRole('button', { name: /Sign in with Keycloak/i })).toBeVisible({ timeout: 60_000 });
  });

  test('matrix: unknown route falls back to login with no blank page under redirect-pause state', async ({ page, request }) => {
    await waitForApiReady(request);
    await seedRedirectPauseState(page);
    await page.goto(`/matrix-unknown-route-${Date.now()}`, { waitUntil: 'domcontentloaded' });

    await expect(page).toHaveURL(/\/login($|[?#])/, { timeout: 60_000 });
    await expect(page.getByRole('heading', { name: /Athena ECM/i })).toBeVisible({ timeout: 60_000 });
    await expect(page.getByRole('button', { name: /Sign in with Keycloak/i })).toBeVisible({ timeout: 60_000 });
  });

  test('matrix: startup remains recoverable when sessionStorage remove throws', async ({ page, request }) => {
    await waitForApiReady(request);
    await injectSessionStorageRemoveGuard(page, AUTH_INIT_STATUS_KEY);
    await page.goto('/browse/root', { waitUntil: 'domcontentloaded' });
    await expect
      .poll(async () => {
        const url = page.url();
        if (/\/realms\/ecm\/protocol\/openid-connect\/auth/.test(url)) {
          return 'keycloak';
        }
        const loginHeadingVisible = await page.getByRole('heading', { name: /Athena ECM/i }).isVisible().catch(() => false);
        const loginButtonVisible = await page.getByRole('button', { name: /Sign in with Keycloak/i }).isVisible().catch(() => false);
        if (loginHeadingVisible && loginButtonVisible) {
          return 'login';
        }
        return 'pending';
      }, { timeout: 60_000 })
      .not.toBe('pending');
  });

  test('matrix: login clears stale in-progress markers under redirect timing jitter', async ({ page, request }) => {
    await waitForApiReady(request);
    await seedStaleLoginInProgress(page);
    await page.goto('/login', { waitUntil: 'domcontentloaded' });

    await expect(page.getByRole('heading', { name: /Athena ECM/i })).toBeVisible({ timeout: 60_000 });
    await expect
      .poll(
        () =>
          page.evaluate(
            ({ loginInProgressKey, loginStartedAtKey }) => ({
              inProgress: sessionStorage.getItem(loginInProgressKey),
              startedAt: sessionStorage.getItem(loginStartedAtKey),
            }),
            {
              loginInProgressKey: LOGIN_IN_PROGRESS_KEY,
              loginStartedAtKey: LOGIN_IN_PROGRESS_STARTED_AT_KEY,
            }
          ),
        { timeout: 60_000 }
      )
      .toEqual({ inProgress: null, startedAt: null });
  });

  test('matrix: login CTA reaches keycloak auth endpoint as terminal redirect state', async ({ page, request }) => {
    await waitForApiReady(request);
    await page.goto('/login', { waitUntil: 'domcontentloaded' });

    await expect(page.getByRole('button', { name: /Sign in with Keycloak/i })).toBeVisible({ timeout: 60_000 });
    await page.getByRole('button', { name: /Sign in with Keycloak/i }).click({ noWaitAfter: true });
    await page.waitForURL(/\/realms\/ecm\/protocol\/openid-connect\/auth/, { timeout: 60_000 });
    await expect(page).toHaveURL(/client_id=unified-portal/);
  });
});
