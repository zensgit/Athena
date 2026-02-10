import { test, expect } from '@playwright/test';
import {
  fetchAccessToken,
  getRootFolderId,
  resolveApiUrl,
  waitForApiReady,
  waitForSearchIndex,
} from './helpers/api';
import { gotoWithAuthE2E } from './helpers/login';

const defaultUsername = process.env.ECM_E2E_USERNAME || 'admin';
const defaultPassword = process.env.ECM_E2E_PASSWORD || 'admin';

test.describe('Saved Search Share Link', () => {
  test('SearchResults can execute a saved search from URL param', async ({ page, request }) => {
    const apiUrl = resolveApiUrl();
    await waitForApiReady(request, { apiUrl });

    const token = await fetchAccessToken(request, defaultUsername, defaultPassword);
    const rootId = await getRootFolderId(request, token, { apiUrl });

    const filename = `ui-e2e-saved-link-${Date.now()}.txt`;
    const uploadRes = await request.post(`${apiUrl}/api/v1/documents/upload`, {
      params: { folderId: rootId },
      headers: { Authorization: `Bearer ${token}` },
      multipart: {
        file: { name: filename, mimeType: 'text/plain', buffer: Buffer.from(`hello ${filename}`) },
      },
    });
    expect(uploadRes.ok()).toBeTruthy();

    await waitForSearchIndex(request, filename, token, { apiUrl, maxAttempts: 40, delayMs: 1500 });

    const savedName = `e2e-link-${Date.now()}`;
    const queryParams = {
      query: filename,
      filters: {
        folderId: rootId,
        includeChildren: false,
      },
      highlightEnabled: true,
      facetFields: ['mimeType', 'createdBy', 'tags', 'categories', 'correspondent'],
      pageable: { page: 0, size: 50 },
    };

    const createRes = await request.post(`${apiUrl}/api/v1/search/saved`, {
      headers: { Authorization: `Bearer ${token}` },
      data: { name: savedName, queryParams },
    });
    expect(createRes.ok()).toBeTruthy();
    const created = (await createRes.json()) as { id?: string };
    expect(created.id).toBeTruthy();

    await gotoWithAuthE2E(page, `/search-results?savedSearchId=${created.id}`, defaultUsername, defaultPassword, { token });

    await expect(page.getByRole('heading', { name: filename })).toBeVisible({ timeout: 60_000 });
    await expect(page.getByText('Scope: This folder (no subfolders)')).toBeVisible({ timeout: 30_000 });
  });
});

