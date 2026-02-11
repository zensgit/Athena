import { APIRequestContext, expect, test } from '@playwright/test';
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
  request: APIRequestContext;
  apiUrl: string;
  folderId: string;
  token: string;
  filename: string;
  content: string;
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

test.describe('Saved Search Overwrite From Dialog', () => {
  test('Advanced Search save dialog can update existing saved search', async ({ page, request }) => {
    test.setTimeout(240_000);

    const apiUrl = resolveApiUrl();
    await waitForApiReady(request, { apiUrl });

    const token = await fetchAccessToken(request, defaultUsername, defaultPassword);
    const rootId = await getRootFolderId(request, token, { apiUrl });

    const docA = `e2e-overwrite-a-${Date.now()}.txt`;
    const docB = `e2e-overwrite-b-${Date.now()}.txt`;
    await uploadTextDocument({
      request,
      apiUrl,
      folderId: rootId,
      token,
      filename: docA,
      content: `content ${docA}`,
    });
    await uploadTextDocument({
      request,
      apiUrl,
      folderId: rootId,
      token,
      filename: docB,
      content: `content ${docB}`,
    });

    await waitForSearchIndex(request, docA, token, { apiUrl, maxAttempts: 40, delayMs: 1500 });
    await waitForSearchIndex(request, docB, token, { apiUrl, maxAttempts: 40, delayMs: 1500 });

    const savedName = `e2e-overwrite-${Date.now()}`;
    const createRes = await request.post(`${apiUrl}/api/v1/search/saved`, {
      headers: { Authorization: `Bearer ${token}` },
      data: {
        name: savedName,
        queryParams: {
          query: docA,
          filters: {
            folderId: rootId,
            includeChildren: false,
          },
          highlightEnabled: true,
          facetFields: ['mimeType', 'createdBy', 'tags', 'categories', 'correspondent'],
          pageable: { page: 0, size: 50 },
        },
      },
    });
    expect(createRes.ok()).toBeTruthy();
    const created = (await createRes.json()) as { id?: string };
    expect(created.id).toBeTruthy();

    await gotoWithAuthE2E(page, '/browse/root', defaultUsername, defaultPassword, { token });
    await page.getByRole('button', { name: 'Search', exact: true }).click();

    const searchDialog = page.getByRole('dialog').filter({ hasText: 'Advanced Search' });
    await expect(searchDialog).toBeVisible({ timeout: 60_000 });
    await searchDialog.getByLabel('Name contains').fill(docB);
    await searchDialog.getByRole('button', { name: 'Save Search', exact: true }).click();

    const saveDialog = page.getByRole('dialog').filter({ hasText: 'Save Search' });
    await expect(saveDialog).toBeVisible({ timeout: 30_000 });
    await saveDialog.getByLabel('Mode').click();
    await page.getByRole('option', { name: 'Update existing', exact: true }).click();
    await saveDialog.getByLabel('Saved Search').click();
    await page.getByRole('option', { name: savedName, exact: true }).click();
    await saveDialog.getByRole('button', { name: 'Update', exact: true }).click();
    await expect(page.getByText('Saved search updated')).toBeVisible({ timeout: 60_000 });

    await gotoWithAuthE2E(page, `/search-results?savedSearchId=${created.id}`, defaultUsername, defaultPassword, { token });
    await expect(page.getByRole('heading', { name: docB })).toBeVisible({ timeout: 60_000 });
  });
});
