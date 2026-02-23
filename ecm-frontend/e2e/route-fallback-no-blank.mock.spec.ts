import { expect, test } from '@playwright/test';
import { seedBypassSessionE2E } from './helpers/login';

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

test('Route fallback: unknown route redirects unauthenticated users to login without blank page (mocked)', async ({ page }) => {
  test.setTimeout(120_000);

  await page.goto('/definitely-not-a-real-route', { waitUntil: 'domcontentloaded' });

  await expect(page).toHaveURL(/\/login(?:\?.*)?$/, { timeout: 60_000 });
  await expect(page.getByText('Sign in with your organization account')).toBeVisible({ timeout: 60_000 });
  await expect(page.getByText('The page encountered an unexpected error. You can refresh and try again.')).toHaveCount(0);
});

test('Route fallback: unknown route redirects authenticated users to browse root without blank page (mocked)', async ({ page }) => {
  test.setTimeout(120_000);

  await seedBypassSessionE2E(page, 'admin', 'e2e-token');
  await setupBrowseMocks(page);

  await page.goto('/definitely-not-a-real-route', { waitUntil: 'domcontentloaded' });

  await expect(page).toHaveURL(/\/browse\/root(?:\?.*)?$/, { timeout: 60_000 });
  await expect(page.getByText('Folders')).toBeVisible({ timeout: 60_000 });
  await expect(page.getByText('This folder is empty')).toBeVisible({ timeout: 60_000 });
  await expect(page.getByText('Sign in with your organization account')).toHaveCount(0);
  await expect(page.getByText('The page encountered an unexpected error. You can refresh and try again.')).toHaveCount(0);
});
