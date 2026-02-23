import { expect, test } from '@playwright/test';
import { seedBypassSessionE2E } from './helpers/login';

const AUTH_INIT_STATUS_KEY = 'ecm_auth_init_status';
const AUTH_REDIRECT_FAILURE_COUNT_KEY = 'ecm_auth_redirect_failure_count';
const AUTH_REDIRECT_LAST_FAILURE_AT_KEY = 'ecm_auth_redirect_last_failure_at';
const LOGIN_IN_PROGRESS_KEY = 'ecm_kc_login_in_progress';
const LOGIN_IN_PROGRESS_STARTED_AT_KEY = 'ecm_kc_login_in_progress_started_at';
const AUTH_REDIRECT_REASON_KEY = 'ecm_auth_redirect_reason';

test('Auth recovery remains usable when auth storage reads are partially restricted (mocked API)', async ({ page }) => {
  test.setTimeout(120_000);

  await seedBypassSessionE2E(page, 'admin', 'e2e-token');

  await page.addInitScript(
    ({
      sessionKeys,
      localKeys,
    }) => {
      const sessionProto = Object.getPrototypeOf(window.sessionStorage) as Storage & {
        getItem: (key: string) => string | null;
      };
      const originalSessionGet = sessionProto.getItem;
      sessionProto.getItem = function patchedSessionGetItem(key: string) {
        if (sessionKeys.includes(key)) {
          throw new DOMException(`blocked sessionStorage key: ${key}`, 'SecurityError');
        }
        return originalSessionGet.call(this, key);
      };

      const localProto = Object.getPrototypeOf(window.localStorage) as Storage & {
        getItem: (key: string) => string | null;
      };
      const originalLocalGet = localProto.getItem;
      localProto.getItem = function patchedLocalGetItem(key: string) {
        if (localKeys.includes(key)) {
          throw new DOMException(`blocked localStorage key: ${key}`, 'SecurityError');
        }
        return originalLocalGet.call(this, key);
      };
    },
    {
      sessionKeys: [
        AUTH_INIT_STATUS_KEY,
        AUTH_REDIRECT_FAILURE_COUNT_KEY,
        AUTH_REDIRECT_LAST_FAILURE_AT_KEY,
        LOGIN_IN_PROGRESS_KEY,
        LOGIN_IN_PROGRESS_STARTED_AT_KEY,
      ],
      localKeys: [AUTH_REDIRECT_REASON_KEY],
    }
  );

  const now = new Date().toISOString();
  const rootFolderId = 'root-folder-id';
  const json = (body: unknown) => JSON.stringify(body);
  const fulfillJson = (route: any, body: unknown) => route.fulfill({
    status: 200,
    contentType: 'application/json',
    body: json(body),
  });

  await page.route('**/api/v1/**', async (route) => {
    const requestUrl = new URL(route.request().url());
    const pathname = requestUrl.pathname;

    if (pathname.endsWith('/folders/roots')) {
      await fulfillJson(route, [
        {
          id: rootFolderId,
          name: 'Root',
          path: '/Root',
          folderType: 'SYSTEM',
          parentId: null,
          inheritPermissions: true,
          description: null,
          createdBy: 'admin',
          createdDate: now,
          lastModifiedBy: 'admin',
          lastModifiedDate: now,
        },
      ]);
      return;
    }

    if (pathname.includes('/folders/') && pathname.endsWith('/contents')) {
      await fulfillJson(route, { content: [], totalElements: 0, totalPages: 1, number: 0, size: 50 });
      return;
    }

    if (pathname.endsWith('/favorites/batch/check')) {
      await fulfillJson(route, { favoritedNodeIds: [] });
      return;
    }

    if (pathname.endsWith('/search/suggestions')
      || pathname.endsWith('/search/saved')
      || pathname.endsWith('/search/facets')
      || pathname.endsWith('/search/filters/suggested')
      || pathname.endsWith('/search/spellcheck')) {
      await fulfillJson(route, []);
      return;
    }

    if (pathname.endsWith('/search/diagnostics')) {
      await fulfillJson(route, {
        username: 'admin',
        admin: true,
        readFilterApplied: false,
        authorityCount: 1,
        authoritySample: ['ROLE_ADMIN'],
        note: null,
        generatedAt: now,
      });
      return;
    }

    if (pathname.endsWith('/search/index/stats')) {
      await fulfillJson(route, { indexName: 'ecm_documents', documentCount: 0, searchEnabled: true });
      return;
    }

    if (pathname.endsWith('/search/index/rebuild/status')) {
      await fulfillJson(route, { inProgress: false, documentsIndexed: 0 });
      return;
    }

    await route.fulfill({
      status: 404,
      contentType: 'application/json',
      body: json({ message: `Not mocked: ${pathname}` }),
    });
  });

  await page.goto('/browse/root', { waitUntil: 'domcontentloaded' });
  await expect(page.getByText('Athena ECM')).toBeVisible({ timeout: 60_000 });
  await expect(page.getByText('Folders')).toBeVisible({ timeout: 60_000 });

  await page.goto('/login?reason=session_expired', { waitUntil: 'domcontentloaded' });
  await expect(page.getByRole('heading', { name: /Athena ECM/i })).toBeVisible({ timeout: 60_000 });
  await expect(page.getByText('Your session expired. Please sign in again.')).toBeVisible({ timeout: 60_000 });
  await expect(page.getByRole('button', { name: /Sign in with Keycloak/i })).toBeVisible({ timeout: 60_000 });
});
