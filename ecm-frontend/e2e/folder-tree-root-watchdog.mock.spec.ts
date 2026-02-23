import { expect, test } from '@playwright/test';
import { seedBypassSessionE2E } from './helpers/login';

const E2E_FOLDER_TREE_WATCHDOG_MS_KEY = 'ecm_e2e_folder_tree_watchdog_ms';

test('Folder tree: root loading watchdog shows retry and recovers (mocked API)', async ({ page }) => {
  test.setTimeout(180_000);

  await seedBypassSessionE2E(page, 'admin', 'e2e-token');

  await page.addInitScript(({ watchdogKey }) => {
    try {
      localStorage.setItem(watchdogKey, '1200');
    } catch {
      // Best effort for restrictive contexts.
    }
  }, { watchdogKey: E2E_FOLDER_TREE_WATCHDOG_MS_KEY });

  const now = new Date().toISOString();
  const rootFolderId = 'root-folder-id';
  const slowRootRequestLimit = 2;
  const slowRootResponseMs = 2_500;
  const json = (body: unknown) => JSON.stringify(body);
  const fulfillJson = (route: any, body: unknown) =>
    route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: json(body),
    });

  let rootRequestCount = 0;

  await page.route('**/api/v1/**', async (route: any) => {
    const requestUrl = new URL(route.request().url());
    const pathname = requestUrl.pathname;

    if (pathname.endsWith('/folders/roots')) {
      rootRequestCount += 1;
      if (rootRequestCount <= slowRootRequestLimit) {
        await new Promise((resolve) => {
          setTimeout(resolve, slowRootResponseMs);
        });
      }

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
      await fulfillJson(route, {
        content: [],
        totalElements: 0,
        totalPages: 1,
        number: Number(requestUrl.searchParams.get('page') || '0'),
        size: Number(requestUrl.searchParams.get('size') || '50'),
      });
      return;
    }

    if (pathname.includes('/nodes/') && pathname.endsWith('/children')) {
      await fulfillJson(route, { content: [] });
      return;
    }

    if (pathname.endsWith('/favorites/batch/check')) {
      await fulfillJson(route, { favoritedNodeIds: [] });
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
  await expect(page.getByTestId('folder-tree-loading-watchdog-alert')).toBeVisible({ timeout: 60_000 });
  await page.getByTestId('folder-tree-loading-watchdog-retry').click();

  await expect(page.getByRole('treeitem', { name: /Root/i })).toBeVisible({ timeout: 60_000 });
  await expect(page.getByTestId('folder-tree-loading-watchdog-alert')).toHaveCount(0);
  await expect.poll(() => rootRequestCount, { timeout: 60_000 }).toBeGreaterThan(slowRootRequestLimit);
});
