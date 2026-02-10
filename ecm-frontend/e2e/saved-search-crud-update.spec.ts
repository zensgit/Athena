import { APIRequestContext, test, expect } from '@playwright/test';
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

async function uploadTextDocument(options: {
  apiUrl: string;
  folderId: string;
  filename: string;
  token: string;
  content: string;
  request: APIRequestContext;
}) {
  const res = await options.request.post(`${options.apiUrl}/api/v1/documents/upload`, {
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
}

test.describe('Saved Search CRUD Update', () => {
  test('Saved searches can be renamed/duplicated in UI and updated via API', async ({ page, request }) => {
    test.setTimeout(300_000);

    const apiUrl = resolveApiUrl();
    await waitForApiReady(request, { apiUrl });

    const token = await fetchAccessToken(request, defaultUsername, defaultPassword);
    const rootId = await getRootFolderId(request, token, { apiUrl });

    const docA = `e2e-saved-crud-a-${Date.now()}.txt`;
    const docB = `e2e-saved-crud-b-${Date.now()}.txt`;

    await uploadTextDocument({
      apiUrl,
      folderId: rootId,
      filename: docA,
      token,
      content: `hello ${docA}`,
      request,
    });
    await uploadTextDocument({
      apiUrl,
      folderId: rootId,
      filename: docB,
      token,
      content: `hello ${docB}`,
      request,
    });

    await waitForSearchIndex(request, docA, token, { apiUrl, maxAttempts: 40, delayMs: 1500 });
    await waitForSearchIndex(request, docB, token, { apiUrl, maxAttempts: 40, delayMs: 1500 });

    const savedName = `e2e-crud-${Date.now()}`;
    const queryParams = {
      query: docA,
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

    const renamedName = `${savedName}-renamed`;
    const duplicatedName = `${renamedName}-copy`;

    await gotoWithAuthE2E(page, '/saved-searches', defaultUsername, defaultPassword, { token });
    await expect(page.getByText('Saved Searches')).toBeVisible({ timeout: 60_000 });
    await expect(page.getByText(savedName)).toBeVisible({ timeout: 60_000 });

    await page.getByRole('button', { name: `Rename saved search ${savedName}` }).click();
    const renameDialog = page.getByRole('dialog');
    await expect(renameDialog).toBeVisible({ timeout: 30_000 });
    await renameDialog.getByLabel('Name').fill(renamedName);
    await renameDialog.getByRole('button', { name: /^Save$/ }).click();

    await expect(page.getByText('Saved search renamed')).toBeVisible({ timeout: 60_000 });
    await expect(page.getByText(renamedName)).toBeVisible({ timeout: 60_000 });

    await page.getByRole('button', { name: `Duplicate saved search ${renamedName}` }).click();
    const duplicateDialog = page.getByRole('dialog');
    await expect(duplicateDialog).toBeVisible({ timeout: 30_000 });
    await duplicateDialog.getByLabel('Name').fill(duplicatedName);
    await duplicateDialog.getByRole('button', { name: /^Save$/ }).click();

    await expect(page.getByText('Saved search duplicated')).toBeVisible({ timeout: 60_000 });
    await expect(page.getByText(duplicatedName)).toBeVisible({ timeout: 60_000 });

    const patchRes = await request.patch(`${apiUrl}/api/v1/search/saved/${created.id}`, {
      headers: { Authorization: `Bearer ${token}` },
      data: {
        queryParams: {
          ...queryParams,
          query: docB,
        },
      },
    });
    expect(patchRes.ok()).toBeTruthy();

    await gotoWithAuthE2E(page, `/search-results?savedSearchId=${created.id}`, defaultUsername, defaultPassword, { token });

    await expect(page.getByRole('heading', { name: docB })).toBeVisible({ timeout: 60_000 });
    await expect(page.getByText('Scope: This folder (no subfolders)')).toBeVisible({ timeout: 30_000 });

    await request.delete(`${apiUrl}/api/v1/search/saved/${created.id}`, {
      headers: { Authorization: `Bearer ${token}` },
    }).catch(() => null);
  });
});
