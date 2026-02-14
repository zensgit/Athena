import { expect, test } from '@playwright/test';
import { seedBypassSessionE2E } from './helpers/login';

test('Version history supports paging and major-only toggle (mocked API)', async ({ page }) => {
  test.setTimeout(120_000);

  await seedBypassSessionE2E(page, 'admin', 'e2e-token');

  const rootFolderId = 'root-folder-id';
  const docId = '22222222-2222-2222-2222-222222222222';
  const now = new Date().toISOString();

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
      await fulfillJson(route, {
        content: [
          {
            id: docId,
            name: 'demo.txt',
            description: null,
            path: '/Root/Documents/demo.txt',
            nodeType: 'DOCUMENT',
            parentId: rootFolderId,
            size: 200,
            createdBy: 'admin',
            createdDate: now,
            lastModifiedBy: 'admin',
            lastModifiedDate: now,
            contentType: 'text/plain',
          },
        ],
        totalElements: 1,
        totalPages: 1,
        number: 0,
        size: 50,
      });
      return;
    }

    if (pathname.endsWith('/favorites/batch/check')) {
      await fulfillJson(route, { favoritedNodeIds: [] });
      return;
    }

    if (pathname.endsWith(`/documents/${docId}/versions/paged`)) {
      const pageParam = requestUrl.searchParams.get('page');
      const sizeParam = requestUrl.searchParams.get('size');
      const majorOnlyParam = requestUrl.searchParams.get('majorOnly');

      expect(sizeParam).toBeTruthy();

      const pageNum = pageParam ? Number.parseInt(pageParam, 10) : 0;
      const majorOnly = majorOnlyParam === 'true';

      // When majorOnly is enabled we only return major versions.
      if (majorOnly) {
        expect(pageNum).toBe(0);
        await fulfillJson(route, {
          content: [
            {
              id: 'v10',
              documentId: docId,
              versionLabel: '1.0',
              comment: 'Major milestone',
              createdDate: now,
              creator: 'admin',
              size: 100,
              major: true,
              mimeType: 'text/plain',
              contentHash: 'hash-v10',
              contentId: 'content-v10',
              status: 'OK',
            },
          ],
          totalElements: 1,
          totalPages: 1,
          number: 0,
          size: Number.parseInt(sizeParam || '20', 10),
        });
        return;
      }

      if (pageNum === 0) {
        await fulfillJson(route, {
          content: [
            {
              id: 'v12',
              documentId: docId,
              versionLabel: '1.2',
              comment: 'Minor update',
              createdDate: now,
              creator: 'admin',
              size: 120,
              major: false,
              mimeType: 'text/plain',
              contentHash: 'hash-v12',
              contentId: 'content-v12',
              status: 'OK',
            },
            {
              id: 'v11',
              documentId: docId,
              versionLabel: '1.1',
              comment: 'Minor update',
              createdDate: now,
              creator: 'admin',
              size: 110,
              major: false,
              mimeType: 'text/plain',
              contentHash: 'hash-v11',
              contentId: 'content-v11',
              status: 'OK',
            },
          ],
          totalElements: 3,
          totalPages: 1,
          number: 0,
          size: Number.parseInt(sizeParam || '20', 10),
        });
        return;
      }

      // Page 1 appends older versions
      await fulfillJson(route, {
        content: [
          {
            id: 'v10',
            documentId: docId,
            versionLabel: '1.0',
            comment: 'Major milestone',
            createdDate: now,
            creator: 'admin',
            size: 100,
            major: true,
            mimeType: 'text/plain',
            contentHash: 'hash-v10',
            contentId: 'content-v10',
            status: 'OK',
          },
        ],
        totalElements: 3,
        totalPages: 1,
        number: 1,
        size: Number.parseInt(sizeParam || '20', 10),
      });
      return;
    }

    await route.fulfill({
      status: 404,
      contentType: 'application/json',
      body: json({ message: `Not mocked: ${pathname}` }),
    });
  });

  // When running against a static build server (no SPA rewrite), avoid deep links.
  await page.goto('/', { waitUntil: 'domcontentloaded' });
  await page.waitForURL(/\/browse\//, { timeout: 60_000 });

  await expect(page.getByText('demo.txt')).toBeVisible({ timeout: 60_000 });
  await page.getByRole('button', { name: 'Actions for demo.txt' }).click();
  await page.getByRole('menuitem', { name: 'Version History' }).click();

  const versionsDialog = page.getByRole('dialog').filter({ hasText: 'Version History' });
  await expect(versionsDialog).toBeVisible({ timeout: 60_000 });

  await expect(versionsDialog.getByText('1.2')).toBeVisible();

  await versionsDialog.getByRole('button', { name: 'Load more' }).click();
  await expect(versionsDialog.getByText('1.0')).toBeVisible();
  await expect(versionsDialog.getByText('Major').first()).toBeVisible();

  await versionsDialog.getByRole('checkbox', { name: 'Major versions only' }).click();
  await expect(versionsDialog.getByText('1.2')).toHaveCount(0);
  await expect(versionsDialog.getByText('1.0')).toBeVisible();
});

