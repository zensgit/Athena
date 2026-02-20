import { expect, test } from '@playwright/test';
import { seedBypassSessionE2E } from './helpers/login';

test('File browser: long loading shows watchdog actions and can recover via retry (mocked API)', async ({ page }) => {
  test.setTimeout(180_000);

  await seedBypassSessionE2E(page, 'admin', 'e2e-token');

  const now = new Date().toISOString();
  const rootFolderId = 'root-folder-id';
  const hangingRequestLimit = 2;
  const json = (body: unknown) => JSON.stringify(body);
  const fulfillJson = (route: any, body: unknown) =>
    route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: json(body),
    });

  let fileBrowserContentsRequestCount = 0;

  await page.route('**/api/v1/**', async (route: any) => {
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
      const size = requestUrl.searchParams.get('size');
      if (size === '50') {
        fileBrowserContentsRequestCount += 1;
        if (fileBrowserContentsRequestCount <= hangingRequestLimit) {
          await new Promise(() => {
            // Keep initial file-browser contents requests hanging to trigger watchdog
            // in both strict-mode (dev) and non-strict (static) runtimes.
          });
          return;
        }
      }

      await fulfillJson(route, {
        content: [],
        totalElements: 0,
        totalPages: 1,
        number: Number(requestUrl.searchParams.get('page') || '0'),
        size: Number(size || '50'),
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
  await expect(page.getByTestId('filebrowser-loading-watchdog-alert')).toBeVisible({ timeout: 90_000 });
  await expect(page.getByTestId('filebrowser-loading-watchdog-retry')).toBeVisible();
  await expect(page.getByTestId('filebrowser-loading-watchdog-back-root')).toBeVisible();

  const emptyState = page.getByText('This folder is empty');
  for (let attempt = 0; attempt < 3; attempt += 1) {
    if (await emptyState.isVisible().catch(() => false)) {
      break;
    }
    await page.getByTestId('filebrowser-loading-watchdog-retry').click();
    await page.waitForTimeout(250);
  }

  await expect(emptyState).toBeVisible({ timeout: 60_000 });
  await expect(page.getByTestId('filebrowser-loading-watchdog-alert')).toHaveCount(0);
  await expect.poll(() => fileBrowserContentsRequestCount, { timeout: 60_000 }).toBeGreaterThanOrEqual(hangingRequestLimit + 1);
});
