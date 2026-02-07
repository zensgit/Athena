import { APIRequestContext, expect, Page, test } from '@playwright/test';
import {
  fetchAccessToken,
  findChildFolderId,
  getRootFolderId,
  reindexByQuery,
  waitForApiReady,
  waitForSearchIndex,
} from './helpers/api';
import { loginWithCredentialsE2E } from './helpers/login';

const baseApiUrl = process.env.ECM_API_URL || 'http://localhost:7700';
const baseUiUrl = process.env.ECM_UI_URL || 'http://localhost:5500';
const defaultUsername = process.env.ECM_E2E_USERNAME || 'admin';
const defaultPassword = process.env.ECM_E2E_PASSWORD || 'admin';

async function loginWithCredentials(page: Page, username: string, password: string, token?: string) {
  await loginWithCredentialsE2E(page, username, password, { token });
}

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

async function uploadDocument(
  request: APIRequestContext,
  folderId: string,
  filename: string,
  token: string,
  content: string,
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

async function isSearchAvailable(request: APIRequestContext, token: string) {
  try {
    const res = await request.get(`${baseApiUrl}/api/v1/system/status`, {
      headers: { Authorization: `Bearer ${token}` },
    });
    if (!res.ok()) {
      return false;
    }
    const payload = (await res.json()) as { search?: { error?: string; searchEnabled?: boolean } };
    const search = payload.search;
    if (!search) {
      return false;
    }
    if (search.searchEnabled === false) {
      return false;
    }
    return !search.error;
  } catch {
    return false;
  }
}

test('Search results include highlight snippets', async ({ page, request }) => {
  test.setTimeout(240_000);

  await waitForApiReady(request, { apiUrl: baseApiUrl });
  const token = await fetchAccessToken(request, defaultUsername, defaultPassword);
  const searchAvailable = await isSearchAvailable(request, token);
  test.skip(!searchAvailable, 'Search is disabled');

  const rootId = await getRootFolderId(request, token, { apiUrl: baseApiUrl });
  const documentsId = await findChildFolderId(request, rootId, 'Documents', token, { apiUrl: baseApiUrl });

  const folderName = `e2e-highlight-${Date.now()}`;
  const folderId = await createFolder(request, documentsId, folderName, token);

  const highlightTerm = `highlight${Date.now()}`;
  const filename = `${highlightTerm}.txt`;
  await uploadDocument(
    request,
    folderId,
    filename,
    token,
    `This is a ${highlightTerm} test document used for e2e highlighting.\n${highlightTerm} appears twice.`,
  );

  await reindexByQuery(request, filename, token, { apiUrl: baseApiUrl, limit: 5, refresh: true });
  await waitForSearchIndex(request, filename, token, { apiUrl: baseApiUrl, maxAttempts: 60 });

  await loginWithCredentials(page, defaultUsername, defaultPassword, token);
  await page.goto(`${baseUiUrl}/search-results`, { waitUntil: 'domcontentloaded' });

  const quickSearchInput = page.getByPlaceholder('Quick search by name...');
  await quickSearchInput.fill(highlightTerm);
  await quickSearchInput.press('Enter');

  const resultCard = page.locator('.MuiCard-root').filter({ hasText: filename }).first();
  await expect(resultCard).toBeVisible({ timeout: 60_000 });
  await expect(resultCard.locator('em').first()).toBeVisible({ timeout: 60_000 });
  await expect(resultCard.getByText(/Matched in/i)).toBeVisible({ timeout: 60_000 });

  await request.delete(`${baseApiUrl}/api/v1/nodes/${folderId}`, {
    headers: { Authorization: `Bearer ${token}` },
  }).catch(() => null);
});
