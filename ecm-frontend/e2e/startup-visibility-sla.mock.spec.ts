import { expect, test } from '@playwright/test';
import { mockKeycloakUnreachable } from './helpers/keycloakMock';
import { seedBypassSessionE2E } from './helpers/login';

const resolvePositiveInt = (rawValue: string | undefined, fallback: number): number => {
  const parsed = Number(rawValue);
  if (!Number.isFinite(parsed) || parsed <= 0) {
    return fallback;
  }
  return Math.floor(parsed);
};

const LOGIN_VISIBLE_SLA_MS = resolvePositiveInt(process.env.ECM_E2E_STARTUP_LOGIN_SLA_MS, 12_000);
const BROWSE_VISIBLE_SLA_MS = resolvePositiveInt(process.env.ECM_E2E_STARTUP_BROWSE_SLA_MS, 15_000);

const setupBrowseMocks = async (page: any) => {
  const now = new Date().toISOString();
  const rootFolderId = 'root-folder-id';
  const json = (body: unknown) => JSON.stringify(body);

  await page.route('**/api/v1/**', async (route: any) => {
    const requestUrl = new URL(route.request().url());
    const pathname = requestUrl.pathname;

    if (pathname.endsWith('/folders/roots')) {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: json([
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
        ]),
      });
      return;
    }

    if (pathname.includes('/folders/') && pathname.endsWith('/contents')) {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: json({
          content: [],
          totalElements: 0,
          totalPages: 1,
          number: Number(requestUrl.searchParams.get('page') || '0'),
          size: Number(requestUrl.searchParams.get('size') || '50'),
        }),
      });
      return;
    }

    if (pathname.includes('/nodes/') && pathname.endsWith('/children')) {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: json({ content: [] }),
      });
      return;
    }

    if (pathname.endsWith('/favorites/batch/check')) {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: json({ favoritedNodeIds: [] }),
      });
      return;
    }

    await route.fulfill({
      status: 404,
      contentType: 'application/json',
      body: json({ message: `Not mocked: ${pathname}` }),
    });
  });
};

test('Startup SLA: login route visible under threshold (mocked)', async ({ page }) => {
  test.setTimeout(120_000);

  // Subject: /login renders within the SLA threshold. Phase 5 Mocked has
  // no Keycloak server; without this mock, keycloak-js's auth boot hangs
  // and the SLA threshold is exceeded by orders of magnitude — the
  // exception is not a perf regression, it's environment.
  // See `docs/P5_PHASE5_MOCKED_GATE_INVESTIGATION_DEV_VERIFICATION_20260426.md`.
  await mockKeycloakUnreachable(page);
  const startedAt = Date.now();
  await page.goto('/login', { waitUntil: 'domcontentloaded' });
  await expect(page.getByText('Sign in with your organization account')).toBeVisible({ timeout: 60_000 });
  const elapsedMs = Date.now() - startedAt;
  console.log(`startup_sla:login_visible_ms=${elapsedMs}:threshold_ms=${LOGIN_VISIBLE_SLA_MS}`);
  expect(elapsedMs).toBeLessThanOrEqual(LOGIN_VISIBLE_SLA_MS);
});

test('Startup SLA: browse root visible under threshold (mocked)', async ({ page }) => {
  test.setTimeout(120_000);

  await seedBypassSessionE2E(page, 'admin', 'e2e-token');
  await setupBrowseMocks(page);

  const startedAt = Date.now();
  await page.goto('/browse/root', { waitUntil: 'domcontentloaded' });
  await expect(page.getByText('Folders')).toBeVisible({ timeout: 60_000 });
  await expect(page.getByText('This folder is empty')).toBeVisible({ timeout: 60_000 });
  const elapsedMs = Date.now() - startedAt;
  console.log(`startup_sla:browse_visible_ms=${elapsedMs}:threshold_ms=${BROWSE_VISIBLE_SLA_MS}`);
  expect(elapsedMs).toBeLessThanOrEqual(BROWSE_VISIBLE_SLA_MS);
});
