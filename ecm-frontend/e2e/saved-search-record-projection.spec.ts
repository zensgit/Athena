import { expect, test } from '@playwright/test';
import {
  fetchAccessToken,
  findDocumentId,
  getRootFolderId,
  reindexByQuery,
  resolveApiUrl,
  waitForApiReady,
  waitForSearchIndex,
} from './helpers/api';
import { gotoWithAuthE2E } from './helpers/login';

const defaultUsername = process.env.ECM_E2E_USERNAME || 'admin';
const defaultPassword = process.env.ECM_E2E_PASSWORD || 'admin';

test.describe('Saved Search Record Projection', () => {
  test('savedSearchId results preserve record badge and category tooltip', async ({ page, request }) => {
    test.setTimeout(240_000);

    const apiUrl = resolveApiUrl();
    await waitForApiReady(request, { apiUrl });

    const token = await fetchAccessToken(request, defaultUsername, defaultPassword);
    const rootId = await getRootFolderId(request, token, { apiUrl });

    const filename = `ui-e2e-record-projection-${Date.now()}.txt`;
    const uploadRes = await request.post(`${apiUrl}/api/v1/documents/upload`, {
      params: { folderId: rootId },
      headers: { Authorization: `Bearer ${token}` },
      multipart: {
        file: {
          name: filename,
          mimeType: 'text/plain',
          buffer: Buffer.from(`record projection ${filename}`),
        },
      },
    });
    expect(uploadRes.ok()).toBeTruthy();

    await waitForSearchIndex(request, filename, token, { apiUrl, maxAttempts: 40, delayMs: 1500 });
    const documentId = await findDocumentId(request, rootId, filename, token, { apiUrl });

    const categoryName = `E2E Record Projection ${Date.now()}`;
    const createCategoryRes = await request.post(`${apiUrl}/api/v1/records/categories`, {
      headers: { Authorization: `Bearer ${token}` },
      data: {
        name: categoryName,
        description: 'Saved-search RM projection test category',
      },
    });
    expect(createCategoryRes.ok()).toBeTruthy();
    const category = (await createCategoryRes.json()) as { categoryId?: string; path?: string };
    expect(category.categoryId).toBeTruthy();
    expect(category.path).toBeTruthy();

    const declareRes = await request.put(`${apiUrl}/api/v1/nodes/${documentId}/record`, {
      headers: { Authorization: `Bearer ${token}` },
      data: {
        comment: 'Saved search RM projection check',
        categoryId: category.categoryId,
      },
    });
    expect(declareRes.ok()).toBeTruthy();

    await reindexByQuery(request, filename, token, { apiUrl, limit: 10, refresh: true });
    await waitForSearchIndex(request, filename, token, { apiUrl, maxAttempts: 20, delayMs: 1000 });

    const savedName = `e2e-record-projection-${Date.now()}`;
    const queryParams = {
      query: filename,
      filters: {
        folderId: rootId,
        includeChildren: false,
        recordOnly: true,
        recordCategoryPaths: [category.path],
      },
      highlightEnabled: true,
      facetFields: ['mimeType', 'createdBy', 'tags', 'categories', 'recordCategoryPath', 'correspondent'],
      pageable: { page: 0, size: 50 },
    };

    const createSavedSearchRes = await request.post(`${apiUrl}/api/v1/search/saved`, {
      headers: { Authorization: `Bearer ${token}` },
      data: { name: savedName, queryParams },
    });
    expect(createSavedSearchRes.ok()).toBeTruthy();
    const savedSearch = (await createSavedSearchRes.json()) as { id?: string };
    expect(savedSearch.id).toBeTruthy();

    await gotoWithAuthE2E(
      page,
      `/search-results?savedSearchId=${savedSearch.id}`,
      defaultUsername,
      defaultPassword,
      { token }
    );

    await expect(page.getByRole('heading', { name: filename })).toBeVisible({ timeout: 60_000 });

    const recordChip = page.getByText('Record', { exact: true }).first();
    await expect(recordChip).toBeVisible({ timeout: 30_000 });
    await recordChip.hover();
    await expect(page.getByText('Declared as a record')).toBeVisible({ timeout: 30_000 });
    await expect(page.getByText(`Category: ${category.path}`)).toBeVisible({ timeout: 30_000 });
  });
});
