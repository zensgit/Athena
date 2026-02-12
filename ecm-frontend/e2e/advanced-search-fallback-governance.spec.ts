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

test.describe('Advanced search fallback governance', () => {
  test('hides retry actions when failed previews are all unsupported', async ({ page, request }) => {
    test.setTimeout(180_000);
    await waitForApiReady(request, { apiUrl });
    const token = await fetchAccessToken(request, defaultUsername, defaultPassword);

    const query = `e2e-unsupported-only-${Date.now()}`;
    await gotoWithAuthE2E(page, '/search', defaultUsername, defaultPassword, { token });
    await page.waitForURL(/\/search/, { timeout: 60_000 });
    await expect(page.getByRole('heading', { name: /Advanced Search/i })).toBeVisible({ timeout: 60_000 });

    await page.route('**/api/v1/search/faceted', async (route) => {
      const requestData = route.request().postDataJSON() as { query?: string } | null;
      if (!requestData || requestData.query !== query) {
        await route.continue();
        return;
      }
      const now = new Date().toISOString();
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          results: {
            content: [
              {
                id: `unsupported-1-${Date.now()}`,
                name: `${query}-a.bin`,
                mimeType: 'application/octet-stream',
                fileSize: 128,
                score: 10.5,
                createdDate: now,
                path: '/Root/Documents',
                nodeType: 'DOCUMENT',
                parentId: 'root',
                previewStatus: 'FAILED',
                previewFailureReason: 'unsupported media type',
              },
              {
                id: `unsupported-2-${Date.now()}`,
                name: `${query}-b.bin`,
                mimeType: 'application/octet-stream',
                fileSize: 256,
                score: 9.8,
                createdDate: now,
                path: '/Root/Documents',
                nodeType: 'DOCUMENT',
                parentId: 'root',
                previewStatus: 'FAILED',
                previewFailureCategory: 'UNSUPPORTED',
                previewFailureReason: 'unsupported mime',
              },
            ],
            totalElements: 2,
            totalPages: 1,
          },
          facets: {
            mimeType: [],
            createdBy: [],
            tags: [],
            categories: [],
          },
        }),
      });
    });

    try {
      const searchQueryInput = page.getByLabel('Search query');
      await searchQueryInput.fill(query);
      await searchQueryInput.press('Enter');

      await expect(page.getByText('Preview issues on current page: 2')).toBeVisible({ timeout: 60_000 });
      await expect(page.getByText('Retryable 0')).toBeVisible({ timeout: 60_000 });
      await expect(page.getByText('Unsupported 2')).toBeVisible({ timeout: 60_000 });
      await expect(page.getByRole('button', { name: 'Retry failed previews' })).toHaveCount(0);
      await expect(page.getByRole('button', { name: 'Force rebuild failed previews' })).toHaveCount(0);
      await expect(
        page.getByText('All preview issues on this page are unsupported; retry actions are hidden.')
      ).toBeVisible({ timeout: 60_000 });
    } finally {
      await page.unroute('**/api/v1/search/faceted').catch(() => null);
    }
  });

  test('advanced search supports hide fallback results and retry backoff messaging', async ({ page, request }) => {
    test.setTimeout(240_000);
    await waitForApiReady(request, { apiUrl });

    const token = await fetchAccessToken(request, defaultUsername, defaultPassword);
    const rootId = await getRootFolderId(request, token, { apiUrl });
    const documentsId = await findChildFolderId(request, rootId, 'Documents', token, { apiUrl });

    const filename = `e2e-advanced-fallback-${Date.now()}.txt`;
    const content = `advanced fallback governance ${filename}`;
    const documentId = await uploadTextDocument({
      request,
      token,
      folderId: documentsId,
      filename,
      content,
    });
    expect(documentId).toBeTruthy();
    await waitForSearchIndex(request, filename, token, { apiUrl, maxAttempts: 60, delayMs: 1500 });

    await gotoWithAuthE2E(page, '/search', defaultUsername, defaultPassword, { token });
    await page.waitForURL(/\/search/, { timeout: 60_000 });
    await expect(page.getByRole('heading', { name: /Advanced Search/i })).toBeVisible({ timeout: 60_000 });

    const advancedDialog = page.getByRole('dialog').filter({ hasText: 'Advanced Search' });
    if (await advancedDialog.count()) {
      const cancelButton = advancedDialog.getByRole('button', { name: /^cancel$/i });
      if (await cancelButton.count()) {
        await cancelButton.click();
      } else {
        const closeIconButton = advancedDialog.locator('button').first();
        await closeIconButton.click();
      }
      await advancedDialog.first().waitFor({ state: 'hidden', timeout: 10_000 });
    }

    const searchQueryInput = page.getByLabel('Search query');

    await searchQueryInput.fill(filename);
    await searchQueryInput.press('Enter');
    await expect(page.getByText(filename).first()).toBeVisible({ timeout: 60_000 });

    let forcedEmptyCount = 0;
    await page.route('**/api/v1/search/faceted', async (route) => {
      const requestData = route.request().postDataJSON() as { query?: string } | null;
      if (!requestData || requestData.query !== filename) {
        await route.continue();
        return;
      }
      forcedEmptyCount += 1;
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          results: {
            content: [],
            totalElements: 0,
            totalPages: 0,
          },
          facets: {
            mimeType: [],
            createdBy: [],
            tags: [],
            categories: [],
          },
        }),
      });
    });

    await searchQueryInput.fill(filename);
    await searchQueryInput.press('Enter');

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
    await expect(page.getByText(filename)).toHaveCount(0);
    await expect(page.getByText('No results found.')).toBeVisible({ timeout: 60_000 });

    const countAfterHide = forcedEmptyCount;
    await page.waitForTimeout(2200);
    expect(forcedEmptyCount).toBe(countAfterHide);

    await page.unroute('**/api/v1/search/faceted');
    await request.delete(`${apiUrl}/api/v1/nodes/${documentId}`, {
      headers: { Authorization: `Bearer ${token}` },
    }).catch(() => null);
  });

  test('advanced search suppresses stale fallback by default for exact binary-like query and supports opt-in reveal', async ({ page, request }) => {
    test.setTimeout(180_000);
    await waitForApiReady(request, { apiUrl });

    const token = await fetchAccessToken(request, defaultUsername, defaultPassword);
    await gotoWithAuthE2E(page, '/search', defaultUsername, defaultPassword, { token });
    await page.waitForURL(/\/search/, { timeout: 60_000 });

    const seedQuery = `e2e-advanced-seed-${Date.now()}.txt`;
    const exactQuery = `e2e-advanced-preview-failure-${Date.now()}.bin`;
    const now = new Date().toISOString();

    await page.route('**/api/v1/search/faceted', async (route) => {
      const requestData = route.request().postDataJSON() as { query?: string } | null;
      if (!requestData) {
        await route.continue();
        return;
      }

      if (requestData.query === seedQuery) {
        await route.fulfill({
          status: 200,
          contentType: 'application/json',
          body: JSON.stringify({
            results: {
              content: [
                {
                  id: `seed-${Date.now()}`,
                  name: seedQuery,
                  mimeType: 'text/plain',
                  fileSize: 128,
                  score: 11.4,
                  createdDate: now,
                  path: '/Root/Documents',
                  nodeType: 'DOCUMENT',
                  parentId: 'root',
                },
              ],
              totalElements: 1,
              totalPages: 1,
            },
            facets: {
              mimeType: [],
              createdBy: [],
              tags: [],
              categories: [],
            },
          }),
        });
        return;
      }

      if (requestData.query === exactQuery) {
        await route.fulfill({
          status: 200,
          contentType: 'application/json',
          body: JSON.stringify({
            results: {
              content: [],
              totalElements: 0,
              totalPages: 0,
            },
            facets: {
              mimeType: [],
              createdBy: [],
              tags: [],
              categories: [],
            },
          }),
        });
        return;
      }

      await route.continue();
    });

    try {
      const searchQueryInput = page.getByLabel('Search query');

      await searchQueryInput.fill(seedQuery);
      await searchQueryInput.press('Enter');
      await expect(page.getByText(seedQuery).first()).toBeVisible({ timeout: 60_000 });

      await searchQueryInput.fill(exactQuery);
      await searchQueryInput.press('Enter');

      await expect(page.getByText(`Search results may still be indexing for exact query "${exactQuery}"`)).toBeVisible({
        timeout: 60_000,
      });
      await expect(page.getByRole('button', { name: 'Show previous results' })).toBeVisible({ timeout: 60_000 });
      await expect(page.getByText(seedQuery)).toHaveCount(0);

      await page.getByRole('button', { name: 'Show previous results' }).click();
      await expect(page.getByText('Search results may still be indexing. Showing previous results')).toBeVisible({
        timeout: 60_000,
      });
      await expect(page.getByText(seedQuery).first()).toBeVisible({ timeout: 60_000 });
    } finally {
      await page.unroute('**/api/v1/search/faceted').catch(() => null);
    }
  });
});
