import { expect, test } from '@playwright/test';
import { seedBypassSessionE2E } from './helpers/login';

test('Search: spellcheck "Did you mean" + Save Search (mocked API)', async ({ page }) => {
  test.setTimeout(120_000);

  await seedBypassSessionE2E(page, 'admin', 'e2e-token');

  const now = new Date().toISOString();
  const rootFolderId = 'root-folder-id';
  const misspelling = 'teest';
  const suggestion = 'test';

  const json = (body: unknown) => JSON.stringify(body);
  const fulfillJson = (route: any, body: unknown) => route.fulfill({
    status: 200,
    contentType: 'application/json',
    body: json(body),
  });

  await page.route('**/api/v1/**', async (route) => {
    const requestUrl = new URL(route.request().url());
    const pathname = requestUrl.pathname;

    // File browser boot (app root)
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

    // Search dialog / saved searches
    if (pathname.endsWith('/search/suggestions')) {
      await fulfillJson(route, []);
      return;
    }
    if (pathname.endsWith('/search/saved') && route.request().method() === 'GET') {
      await fulfillJson(route, []);
      return;
    }
    if (pathname.endsWith('/search/saved') && route.request().method() === 'POST') {
      const payload = route.request().postDataJSON?.() as any;
      expect(payload?.name).toBe('e2e-saved-search');
      expect(payload?.queryParams).toBeTruthy();
      await fulfillJson(route, {
        id: 'saved-1',
        userId: 'e2e-admin',
        name: payload.name,
        queryParams: payload.queryParams,
        pinned: false,
        createdAt: now,
      });
      return;
    }

    // Search results
    if (pathname.endsWith('/search') && route.request().method() === 'GET') {
      const q = requestUrl.searchParams.get('q') || '';
      if (q === misspelling) {
        await fulfillJson(route, { content: [], totalElements: 0 });
        return;
      }
      if (q === suggestion) {
        await fulfillJson(route, {
          content: [
            {
              id: 'doc-1',
              name: 'demo.txt',
              path: '/Root/Documents/demo.txt',
              nodeType: 'DOCUMENT',
              parentId: rootFolderId,
              mimeType: 'text/plain',
              fileSize: 123,
              createdBy: 'admin',
              createdDate: now,
              lastModifiedBy: 'admin',
              lastModifiedDate: now,
              score: 1.0,
              highlights: { name: ['demo.txt'] },
              matchFields: ['name'],
            },
          ],
          totalElements: 1,
        });
        return;
      }
      await fulfillJson(route, { content: [], totalElements: 0 });
      return;
    }
    if (pathname.endsWith('/search/facets')) {
      await fulfillJson(route, {});
      return;
    }
    if (pathname.endsWith('/search/filters/suggested')) {
      await fulfillJson(route, []);
      return;
    }
    if (pathname.endsWith('/search/spellcheck')) {
      const q = requestUrl.searchParams.get('q') || '';
      if (q === misspelling) {
        await fulfillJson(route, [suggestion]);
        return;
      }
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

  await page.goto('/', { waitUntil: 'domcontentloaded' });
  await expect(page.getByRole('button', { name: 'Search' })).toBeVisible();
  await page.getByRole('button', { name: 'Search' }).click();

  const searchDialog = page.getByRole('dialog', { name: 'Advanced Search' });
  await expect(searchDialog).toBeVisible();

  await searchDialog.getByLabel('Name contains').fill(misspelling);

  // Save current criteria as a saved search.
  await searchDialog.getByRole('button', { name: 'Save Search' }).click();
  const saveDialog = page.getByRole('dialog', { name: 'Save Search' });
  await expect(saveDialog).toBeVisible();
  await saveDialog.getByLabel('Name').fill('e2e-saved-search');
  await saveDialog.getByRole('button', { name: 'Save' }).click();
  await expect(saveDialog).toBeHidden({ timeout: 60_000 });

  // Run the search and assert spellcheck suggestions are surfaced.
  await searchDialog.getByRole('button', { name: 'Search', exact: true }).click();

  await expect(page.getByText('Did you mean')).toBeVisible({ timeout: 60_000 });
  await expect(page.getByRole('button', { name: suggestion })).toBeVisible();

  await page.getByRole('button', { name: suggestion }).click();
  await expect(page.getByRole('heading', { name: 'demo.txt' })).toBeVisible({ timeout: 60_000 });
});
