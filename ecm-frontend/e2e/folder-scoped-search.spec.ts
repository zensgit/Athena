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
  token: string,
) {
  const uploadRes = await request.post(`${baseApiUrl}/api/v1/documents/upload?folderId=${folderId}`,
    {
      headers: { Authorization: `Bearer ${token}` },
      multipart: {
        file: {
          name: filename,
          mimeType: 'text/plain',
          buffer: Buffer.from(`folder-scoped-search-${Date.now()}`),
        },
      },
    });
  expect(uploadRes.ok()).toBeTruthy();
  const uploadJson = (await uploadRes.json()) as { documentId?: string; id?: string };
  const documentId = uploadJson.documentId ?? uploadJson.id;
  if (!documentId) {
    throw new Error('Upload did not return document id');
  }
  return documentId;
}

test('Folder-scoped search defaults to current folder and can be cleared', async ({ page, request }) => {
  test.setTimeout(300_000);

  await waitForApiReady(request, { apiUrl: baseApiUrl });
  const token = await fetchAccessToken(request, defaultUsername, defaultPassword);
  const rootId = await getRootFolderId(request, token, { apiUrl: baseApiUrl });
  const documentsId = await findChildFolderId(request, rootId, 'Documents', token, { apiUrl: baseApiUrl });

  const runId = Date.now();
  const folderAName = `e2e-scope-a-${runId}`;
  const folderBName = `e2e-scope-b-${runId}`;
  const folderAId = await createFolder(request, documentsId, folderAName, token);
  const folderBId = await createFolder(request, documentsId, folderBName, token);

  const filenameA = `e2e-folder-scope-${runId}-a.txt`;
  const filenameB = `e2e-folder-scope-${runId}-b.txt`;
  const docAId = await uploadTextFile(request, folderAId, filenameA, token);
  const docBId = await uploadTextFile(request, folderBId, filenameB, token);

  const indexA = await request.post(`${baseApiUrl}/api/v1/search/index/${docAId}`,
    { headers: { Authorization: `Bearer ${token}` } });
  expect(indexA.ok()).toBeTruthy();
  const indexB = await request.post(`${baseApiUrl}/api/v1/search/index/${docBId}`,
    { headers: { Authorization: `Bearer ${token}` } });
  expect(indexB.ok()).toBeTruthy();

  await waitForSearchIndex(request, filenameA, token, { apiUrl: baseApiUrl, maxAttempts: 40 });
  await waitForSearchIndex(request, filenameB, token, { apiUrl: baseApiUrl, maxAttempts: 40 });

  await gotoWithAuthE2E(page, `/browse/${folderAId}`, defaultUsername, defaultPassword, { token });

  await page.getByLabel('Search').click();
  const dialog = page.locator('[role="dialog"]').filter({ hasText: 'Advanced Search' }).first();
  await expect(dialog).toBeVisible();
  await expect(dialog.getByText('Scope: This folder')).toBeVisible();

  await dialog.getByLabel('Name contains').fill(`e2e-folder-scope-${runId}`);
  await dialog.getByRole('button', { name: 'Search', exact: true }).click();

  await expect(page).toHaveURL(/\/search-results/);
  await expect(page.locator('.MuiCard-root').filter({ hasText: filenameA }).first()).toBeVisible({ timeout: 60_000 });
  await expect(page.locator('.MuiCard-root').filter({ hasText: filenameB })).toHaveCount(0);

  const scopeChip = page.locator('.MuiChip-root').filter({ hasText: 'Scope: This folder' }).first();
  await expect(scopeChip).toBeVisible();
  await scopeChip.locator('.MuiChip-deleteIcon').click();

  await expect(scopeChip).toHaveCount(0);
  await expect(page.locator('.MuiCard-root').filter({ hasText: filenameA }).first()).toBeVisible({ timeout: 60_000 });
  await expect(page.locator('.MuiCard-root').filter({ hasText: filenameB }).first()).toBeVisible({ timeout: 60_000 });

  await request.delete(`${baseApiUrl}/api/v1/nodes/${folderAId}`,
    { headers: { Authorization: `Bearer ${token}` } }).catch(() => null);
  await request.delete(`${baseApiUrl}/api/v1/nodes/${folderBId}`,
    { headers: { Authorization: `Bearer ${token}` } }).catch(() => null);
});
