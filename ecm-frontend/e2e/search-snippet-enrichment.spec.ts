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

const baseApiUrl = resolveApiUrl();
const defaultUsername = process.env.ECM_E2E_USERNAME || 'admin';
const defaultPassword = process.env.ECM_E2E_PASSWORD || 'admin';

async function createFolder(
  request: APIRequestContext,
  parentId: string,
  name: string,
  token: string,
) {
  const res = await request.post(`${baseApiUrl}/api/v1/folders`, {
    headers: { Authorization: `Bearer ${token}`, 'Content-Type': 'application/json' },
    data: { name, parentId, folderType: 'GENERAL', inheritPermissions: true },
  });
  expect(res.ok()).toBeTruthy();
  const payload = (await res.json()) as { id?: string };
  if (!payload.id) {
    throw new Error('Folder creation did not return id');
  }
  return payload.id;
}

async function uploadTextFile(
  request: APIRequestContext,
  folderId: string,
  filename: string,
  content: string,
  token: string,
) {
  const uploadRes = await request.post(
    `${baseApiUrl}/api/v1/documents/upload?folderId=${folderId}`,
    {
      headers: { Authorization: `Bearer ${token}` },
      multipart: {
        file: {
          name: filename,
          mimeType: 'text/plain',
          buffer: Buffer.from(content),
        },
      },
    },
  );
  expect(uploadRes.ok()).toBeTruthy();
  const uploadJson = (await uploadRes.json()) as { documentId?: string; id?: string };
  const documentId = uploadJson.documentId ?? uploadJson.id;
  if (!documentId) {
    throw new Error('Upload did not return document id');
  }
  return documentId;
}

test('Search snippets show path + creator + match fields (Search + Advanced Search)', async ({ page, request }) => {
  test.setTimeout(240_000);
  await waitForApiReady(request, { apiUrl: baseApiUrl });
  const token = await fetchAccessToken(request, defaultUsername, defaultPassword);
  const rootId = await getRootFolderId(request, token, { apiUrl: baseApiUrl });
  const documentsId = await findChildFolderId(request, rootId, 'Documents', token, { apiUrl: baseApiUrl });

  const folderName = `e2e-search-snippet-${Date.now()}`;
  const folderId = await createFolder(request, documentsId, folderName, token);
  const needle = `snip-${Date.now()}-${Math.random().toString(36).slice(2, 10)}`;
  const filename = `e2e-snippet-${needle}.txt`;
  const documentId = await uploadTextFile(
    request,
    folderId,
    filename,
    `Search snippet E2E\nfilename=${filename}\n`,
    token,
  );

  const indexRes = await request.post(`${baseApiUrl}/api/v1/search/index/${documentId}`,
    { headers: { Authorization: `Bearer ${token}` } });
  expect(indexRes.ok()).toBeTruthy();
  // Use filename for indexing checks to avoid analyzer/tokenization issues with random strings.
  await waitForSearchIndex(request, filename, token, { apiUrl: baseApiUrl, maxAttempts: 60 });

  await gotoWithAuthE2E(page, '/search-results', defaultUsername, defaultPassword, { token });
  const quickSearchInput = page.getByPlaceholder('Quick search by name...');
  await quickSearchInput.fill(filename);
  await quickSearchInput.press('Enter');

  const resultCard = page.locator('.MuiCard-root').filter({ hasText: filename }).first();
  await expect(resultCard).toBeVisible({ timeout: 60_000 });
  await expect(resultCard.getByText(folderName)).toBeVisible();
  await expect(resultCard.getByText(/By admin/i)).toBeVisible();
  await expect(resultCard.getByText('Matched in')).toBeVisible();

  await gotoWithAuthE2E(page, '/search', defaultUsername, defaultPassword, { token });
  await page.getByLabel('Search query').fill(filename);
  await page.getByRole('button', { name: /^Search$/ }).filter({ hasText: /^Search$/ }).click();

  const advancedResult = page.locator('.MuiPaper-root').filter({ hasText: filename }).first();
  await expect(advancedResult).toBeVisible({ timeout: 60_000 });
  await expect(advancedResult.getByText(folderName)).toBeVisible();
  await expect(advancedResult.getByText(/By admin/i)).toBeVisible();
  await expect(advancedResult.getByText('Matched in')).toBeVisible();

  await request.delete(`${baseApiUrl}/api/v1/nodes/${folderId}`,
    { headers: { Authorization: `Bearer ${token}` } }).catch(() => null);
});
