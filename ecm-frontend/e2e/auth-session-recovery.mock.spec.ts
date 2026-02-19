import { expect, test } from '@playwright/test';
import { seedBypassSessionE2E } from './helpers/login';

type SearchMockResponse = {
  status: number;
  body: unknown;
};

const setupAuthRecoveryMocks = async (
  page: any,
  resolveSearchResponse: (requestUrl: URL) => SearchMockResponse
) => {
  const now = new Date().toISOString();
  const rootFolderId = 'root-folder-id';
  const json = (body: unknown) => JSON.stringify(body);
  const fulfillJson = (route: any, body: unknown) =>
    route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: json(body),
    });

  await page.route('**/api/v1/**', async (route: any) => {
    const requestUrl = new URL(route.request().url());
    const pathname = requestUrl.pathname;
    const method = route.request().method();

    if (pathname.endsWith('/folders/roots')) {
      await fulfillJson(route, [
        {
          id: rootFolderId,
          name: 'Root',
          path: '/Root',
          folderType: 'SYSTEM',
          parentId: null,
          inheritPermissions: true,
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

    if (pathname.endsWith('/search') && method === 'GET') {
      const response = resolveSearchResponse(requestUrl);
      await route.fulfill({
        status: response.status,
        contentType: 'application/json',
        body: json(response.body),
      });
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
      await fulfillJson(route, { indexName: 'ecm_documents', documentCount: 1, searchEnabled: true });
      return;
    }
    if (pathname.endsWith('/search/index/rebuild/status')) {
      await fulfillJson(route, { inProgress: false, documentsIndexed: 1 });
      return;
    }

    if (pathname.endsWith('/tags')) {
      await fulfillJson(route, []);
      return;
    }

    await route.fulfill({
      status: 404,
      contentType: 'application/json',
      body: json({ message: `Not mocked: ${pathname}` }),
    });
  });
};

test('Auth session recovery: transient 401 retries once and stays in search results', async ({ page }) => {
  test.setTimeout(120_000);

  await seedBypassSessionE2E(page, 'admin', 'e2e-token');

  const query = `e2e-auth-recovery-transient-${Date.now()}.txt`;
  let queryAttemptCount = 0;
  let query401Count = 0;

  await setupAuthRecoveryMocks(page, (requestUrl) => {
    const q = requestUrl.searchParams.get('q') || '';
    if (q !== query) {
      return { status: 200, body: { content: [], totalElements: 0, totalPages: 0, number: 0, size: 20, empty: true } };
    }
    queryAttemptCount += 1;
    if (queryAttemptCount === 1) {
      query401Count += 1;
      return {
        status: 401,
        body: { message: 'Unauthorized (simulated first attempt)' },
      };
    }
    return {
      status: 200,
      body: {
        content: [
          {
            id: 'doc-auth-recovery-1',
            name: query,
            path: `/Root/Documents/${query}`,
            nodeType: 'DOCUMENT',
            parentId: 'root-folder-id',
            mimeType: 'text/plain',
            fileSize: 128,
            createdBy: 'admin',
            createdDate: new Date().toISOString(),
            lastModifiedBy: 'admin',
            lastModifiedDate: new Date().toISOString(),
            score: 1.0,
            highlights: { name: [query] },
            matchFields: ['name'],
          },
        ],
        totalElements: 1,
      },
    };
  });

  await page.goto('/search-results', { waitUntil: 'domcontentloaded' });
  const quickSearchInput = page.getByPlaceholder('Quick search by name...');
  await expect(quickSearchInput).toBeVisible({ timeout: 60_000 });

  await quickSearchInput.fill(query);
  await quickSearchInput.press('Enter');

  await expect(page).toHaveURL(/\/search-results/, { timeout: 60_000 });
  await expect(page.getByRole('heading', { name: query })).toBeVisible({ timeout: 60_000 });
  await expect(page.getByText('Your session expired. Please sign in again.')).toHaveCount(0);
  await expect.poll(() => queryAttemptCount, { timeout: 60_000 }).toBe(2);
  await expect.poll(() => query401Count, { timeout: 60_000 }).toBe(1);
});

test('Auth session recovery: unrecoverable 401 redirects to login with session-expired guidance', async ({ page }) => {
  test.setTimeout(120_000);

  await seedBypassSessionE2E(page, 'admin', 'e2e-token');

  const query = `e2e-auth-recovery-terminal-${Date.now()}.txt`;
  let query401Count = 0;

  await setupAuthRecoveryMocks(page, (requestUrl) => {
    const q = requestUrl.searchParams.get('q') || '';
    if (q === query) {
      query401Count += 1;
      return {
        status: 401,
        body: { message: 'Unauthorized (simulated terminal failure)' },
      };
    }
    return { status: 200, body: { content: [], totalElements: 0, totalPages: 0, number: 0, size: 20, empty: true } };
  });

  await page.goto('/search-results', { waitUntil: 'domcontentloaded' });
  const quickSearchInput = page.getByPlaceholder('Quick search by name...');
  await expect(quickSearchInput).toBeVisible({ timeout: 60_000 });

  await quickSearchInput.fill(query);
  await quickSearchInput.press('Enter');

  await page.waitForURL(/\/login(\?.*)?$/, { timeout: 60_000 });
  await expect(page.getByText('Your session expired. Please sign in again.')).toBeVisible({ timeout: 60_000 });
  await expect.poll(() => query401Count, { timeout: 60_000 }).toBe(2);
});
