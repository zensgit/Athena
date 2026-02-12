/* eslint-disable testing-library/prefer-screen-queries */
import { APIRequestContext, expect, test } from '@playwright/test';
import {
  fetchAccessToken,
  findChildFolderId,
  getRootFolderId,
  resolveApiUrl,
  waitForApiReady,
  waitForSearchIndex,
} from './helpers/api';
import { gotoWithAuthE2E } from './helpers/login';

const apiUrl = resolveApiUrl();
const defaultUsername = process.env.ECM_E2E_USERNAME || 'admin';
const defaultPassword = process.env.ECM_E2E_PASSWORD || 'admin';

async function uploadTextDocument(options: {
  request: APIRequestContext;
  token: string;
  folderId: string;
  filename: string;
  content: string;
}) {
  const res = await options.request.post(`${apiUrl}/api/v1/documents/upload`, {
    params: { folderId: options.folderId },
    headers: { Authorization: `Bearer ${options.token}` },
    multipart: {
      file: {
        name: options.filename,
        mimeType: 'text/plain',
        buffer: Buffer.from(options.content),
      },
    },
  });
  expect(res.ok()).toBeTruthy();
  const payload = (await res.json()) as { documentId?: string; id?: string };
  return payload.documentId || payload.id || '';
}

test.describe('Search fallback governance', () => {
  test('fallback supports hide action and exponential auto-retry messaging', async ({ page, request }) => {
    test.setTimeout(240_000);
    await waitForApiReady(request, { apiUrl });

    const token = await fetchAccessToken(request, defaultUsername, defaultPassword);
    const rootId = await getRootFolderId(request, token, { apiUrl });
    const documentsId = await findChildFolderId(request, rootId, 'Documents', token, { apiUrl });

    const filename = `e2e-fallback-governance-${Date.now()}.txt`;
    const content = `fallback governance ${filename}`;
    const documentId = await uploadTextDocument({
      request,
      token,
      folderId: documentsId,
      filename,
      content,
    });
    expect(documentId).toBeTruthy();
    await waitForSearchIndex(request, filename, token, { apiUrl, maxAttempts: 60, delayMs: 1500 });

    await gotoWithAuthE2E(page, '/search-results', defaultUsername, defaultPassword, { token });
    const quickSearchInput = page.getByPlaceholder('Quick search by name...');
    await quickSearchInput.fill(filename);
    await quickSearchInput.press('Enter');
    await expect(page.locator('.MuiCard-root').filter({ hasText: filename }).first()).toBeVisible({ timeout: 60_000 });

    let forcedEmptyCount = 0;
    await page.route('**/api/v1/search**', async (route) => {
      const requestUrl = route.request().url();
      if (requestUrl.includes('/facets')) {
        await route.continue();
        return;
      }
      const parsed = new URL(requestUrl);
      if ((parsed.searchParams.get('q') || '') !== filename) {
        await route.continue();
        return;
      }
      forcedEmptyCount += 1;
      const size = Number(parsed.searchParams.get('size') || 20);
      const pageIndex = Number(parsed.searchParams.get('page') || 0);
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          content: [],
          totalElements: 0,
          totalPages: 0,
          size,
          number: pageIndex,
          first: true,
          last: true,
          empty: true,
        }),
      });
    });

    await quickSearchInput.fill(filename);
    await quickSearchInput.press('Enter');

    await expect(page.getByText('Search results may still be indexing')).toBeVisible({ timeout: 60_000 });
    await expect(page.getByRole('button', { name: 'Hide previous results' })).toBeVisible({ timeout: 60_000 });
    await expect(page.getByText('Auto-retry 0/3 (next in 1.5s).')).toBeVisible({ timeout: 60_000 });

    await expect.poll(() => forcedEmptyCount, { timeout: 30_000 }).toBeGreaterThanOrEqual(2);
    const fallbackAlert = page.getByRole('alert').filter({ hasText: 'Search results may still be indexing' });
    await expect
      .poll(async () => (await fallbackAlert.textContent()) || '', { timeout: 60_000 })
      .toMatch(/Auto-retry (1|2)\/3 \(next in (3\.0|6\.0)s\)\.|Auto-retry stopped after 3 attempts\./);
    await expect
      .poll(async () => (await fallbackAlert.textContent()) || '', { timeout: 60_000 })
      .toContain('Last retry:');

    await page.getByRole('button', { name: 'Hide previous results' }).click();
    await expect(page.getByText('Search results may still be indexing')).toHaveCount(0);
    await expect(page.locator('.MuiCard-root').filter({ hasText: filename })).toHaveCount(0);
    await expect(page.getByText('0 results found')).toBeVisible({ timeout: 60_000 });

    const countAfterHide = forcedEmptyCount;
    await page.waitForTimeout(2200);
    expect(forcedEmptyCount).toBe(countAfterHide);

    await page.unroute('**/api/v1/search**');
    await request.delete(`${apiUrl}/api/v1/nodes/${documentId}`, {
      headers: { Authorization: `Bearer ${token}` },
    }).catch(() => null);
  });

  test('suppresses stale fallback by default for exact binary-like queries and supports opt-in reveal', async ({ page, request }) => {
    test.setTimeout(180_000);
    await waitForApiReady(request, { apiUrl });

    const token = await fetchAccessToken(request, defaultUsername, defaultPassword);
    await gotoWithAuthE2E(page, '/search-results', defaultUsername, defaultPassword, { token });

    const seedQuery = `e2e-seed-${Date.now()}.txt`;
    const exactQuery = `e2e-preview-failure-${Date.now()}.bin`;
    const now = new Date().toISOString();

    await page.route('**/api/v1/search**', async (route) => {
      const requestUrl = route.request().url();
      if (requestUrl.includes('/facets')) {
        await route.continue();
        return;
      }
      const parsed = new URL(requestUrl);
      const q = parsed.searchParams.get('q') || '';
      const size = Number(parsed.searchParams.get('size') || 20);
      const pageIndex = Number(parsed.searchParams.get('page') || 0);

      if (q === seedQuery) {
        await route.fulfill({
          status: 200,
          contentType: 'application/json',
          body: JSON.stringify({
            content: [
              {
                id: `seed-${Date.now()}`,
                name: seedQuery,
                nodeType: 'DOCUMENT',
                parentId: 'root',
                path: '/Root/Documents',
                createdDate: now,
                creator: 'admin',
                contentType: 'text/plain',
                size: 128,
                score: 12.3,
              },
            ],
            totalElements: 1,
            totalPages: 1,
            size,
            number: pageIndex,
            first: true,
            last: true,
            empty: false,
          }),
        });
        return;
      }

      if (q === exactQuery) {
        await route.fulfill({
          status: 200,
          contentType: 'application/json',
          body: JSON.stringify({
            content: [],
            totalElements: 0,
            totalPages: 0,
            size,
            number: pageIndex,
            first: true,
            last: true,
            empty: true,
          }),
        });
        return;
      }

      await route.continue();
    });

    try {
      const quickSearchInput = page.getByPlaceholder('Quick search by name...');

      await quickSearchInput.fill(seedQuery);
      await quickSearchInput.press('Enter');
      await expect(page.locator('.MuiCard-root').filter({ hasText: seedQuery })).toHaveCount(1, { timeout: 60_000 });

      await quickSearchInput.fill(exactQuery);
      await quickSearchInput.press('Enter');

      await expect(page.getByText(`Search results may still be indexing for exact query "${exactQuery}"`)).toBeVisible({
        timeout: 60_000,
      });
      await expect(page.getByRole('button', { name: 'Show previous results' })).toBeVisible({ timeout: 60_000 });
      await expect(page.locator('.MuiCard-root').filter({ hasText: seedQuery })).toHaveCount(0);

      await page.getByRole('button', { name: 'Show previous results' }).click();
      await expect(page.getByText('Search results may still be indexing. Showing previous results')).toBeVisible({
        timeout: 60_000,
      });
      await expect(page.locator('.MuiCard-root').filter({ hasText: seedQuery })).toHaveCount(1, { timeout: 60_000 });
    } finally {
      await page.unroute('**/api/v1/search**').catch(() => null);
    }
  });
});
