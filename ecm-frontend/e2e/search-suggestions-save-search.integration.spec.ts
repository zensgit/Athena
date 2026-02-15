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
  const payload = (await res.json()) as { documentId?: string; id?: string };
  return payload.documentId ?? payload.id;
}

async function findSavedSearchIdByName(
  request: APIRequestContext,
  apiUrl: string,
  token: string,
  name: string,
) {
  const res = await request.get(`${apiUrl}/api/v1/search/saved`, {
    headers: { Authorization: `Bearer ${token}` },
  });
  expect(res.ok()).toBeTruthy();
  const list = (await res.json()) as Array<{ id?: string; name?: string; queryParams?: { query?: string } }>;
  return list.find((item) => item.name === name);
}

test('Phase 5 D6 integration: spellcheck suggestion + save search from advanced search', async ({ page, request }) => {
  test.setTimeout(240_000);

  const apiUrl = resolveApiUrl();
  await waitForApiReady(request, { apiUrl });

  const token = await fetchAccessToken(request, defaultUsername, defaultPassword);
  const rootId = await getRootFolderId(request, token, { apiUrl });

  const suffix = Date.now();
  const targetWord = 'spellcheck';
  const misspelled = 'spelcheck';
  const filename = `e2e-search-suggest-save-${suffix}-${targetWord}.txt`;
  const savedName = `e2e-suggest-save-${suffix}`;
  let savedId: string | undefined;

  try {
    const uploadedId = await uploadTextDocument({
      request,
      apiUrl,
      folderId: rootId,
      token,
      filename,
      content: `This file exists for ${targetWord} and saved-search integration.`,
    });
    expect(uploadedId).toBeTruthy();

    const indexRes = await request.post(`${apiUrl}/api/v1/search/index/${uploadedId}`, {
      headers: { Authorization: `Bearer ${token}` },
    });
    expect(indexRes.ok()).toBeTruthy();

    await waitForSearchIndex(request, filename, token, {
      apiUrl,
      maxAttempts: 30,
      delayMs: 1500,
    });

    await gotoWithAuthE2E(page, '/browse/root', defaultUsername, defaultPassword, { token });
    await page.getByRole('button', { name: 'Search', exact: true }).click();

    const searchDialog = page.getByRole('dialog').filter({ hasText: 'Advanced Search' });
    await expect(searchDialog).toBeVisible({ timeout: 60_000 });

    await searchDialog.getByLabel('Name contains').fill(misspelled);

    await searchDialog.getByRole('button', { name: 'Save Search', exact: true }).click();
    const saveDialog = page.getByRole('dialog').filter({ hasText: 'Save Search' });
    await expect(saveDialog).toBeVisible({ timeout: 60_000 });
    await saveDialog.getByLabel('Name').fill(savedName);
    await saveDialog.getByRole('button', { name: 'Save', exact: true }).click();
    await expect(page.getByText('Saved search created')).toBeVisible({ timeout: 60_000 });

    const savedSearch = await findSavedSearchIdByName(request, apiUrl, token, savedName);
    savedId = savedSearch?.id;
    expect(savedId).toBeTruthy();
    expect(savedSearch?.queryParams?.query).toBe(misspelled);

    const searchRequestPromise = page.waitForRequest(
      (req) => req.url().includes('/api/v1/search') && req.method() === 'GET',
      { timeout: 60_000 },
    );
    await searchDialog.getByRole('button', { name: 'Search', exact: true }).click();
    await searchRequestPromise;
    await expect(searchDialog).toBeHidden({ timeout: 60_000 });

    await expect(page.getByText('Did you mean')).toBeVisible({ timeout: 60_000 });
    const suggestionButton = page.getByRole('button', { name: targetWord }).first();
    await expect(suggestionButton).toBeVisible({ timeout: 60_000 });

    await suggestionButton.click();
    await expect(page.getByPlaceholder('Quick search by name...')).toHaveValue(targetWord, { timeout: 60_000 });
    await expect(page.getByRole('heading', { name: new RegExp(targetWord, 'i') }).first()).toBeVisible({
      timeout: 60_000,
    });
  } finally {
    if (savedId) {
      await request.delete(`${apiUrl}/api/v1/search/saved/${savedId}`, {
        headers: { Authorization: `Bearer ${token}` },
      }).catch(() => null);
    }
  }
});
