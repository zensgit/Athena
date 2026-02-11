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
const randomAlpha = (length: number) => {
  const alphabet = 'abcdefghijklmnopqrstuvwxyz';
  return Array.from({ length }, () => alphabet[Math.floor(Math.random() * alphabet.length)]).join('');
};

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

test.describe('Search fallback criteria guard', () => {
  test('search query change should not keep showing stale fallback results', async ({ page, request }) => {
    test.setTimeout(240_000);
    await waitForApiReady(request, { apiUrl });

    const token = await fetchAccessToken(request, defaultUsername, defaultPassword);
    const rootId = await getRootFolderId(request, token, { apiUrl });
    const documentsId = await findChildFolderId(request, rootId, 'Documents', token, { apiUrl });

    const filename = `e2e-fallback-hit-${Date.now()}.txt`;
    const content = `fallback guard ${filename}`;
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

    const missingQuery = `nomatch${Date.now()}${randomAlpha(18)}`;
    const searchResponsePromise = page.waitForResponse((response) => {
      if (!response.url().includes('/api/v1/search')) {
        return false;
      }
      try {
        const params = new URL(response.url()).searchParams;
        return params.get('q') === missingQuery && response.status() === 200;
      } catch {
        return false;
      }
    });

    await quickSearchInput.fill(missingQuery);
    await quickSearchInput.press('Enter');
    await searchResponsePromise;

    await expect(page.locator('text=0 results found')).toBeVisible({ timeout: 60_000 });
    await expect(page.locator('text=No results found')).toBeVisible({ timeout: 60_000 });
    await expect(page.locator('text=Search results may still be indexing')).toHaveCount(0);
    await expect(page.locator('.MuiCard-root').filter({ hasText: filename })).toHaveCount(0);

    await request.delete(`${apiUrl}/api/v1/nodes/${documentId}`, {
      headers: { Authorization: `Bearer ${token}` },
    }).catch(() => null);
  });
});
