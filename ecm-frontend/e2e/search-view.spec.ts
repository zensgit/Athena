import { APIRequestContext, expect, test } from '@playwright/test';
import {
  fetchAccessToken,
  findChildFolderId,
  getRootFolderId,
  reindexByQuery,
  waitForApiReady,
  waitForSearchIndex,
} from './helpers/api';
import { gotoWithAuthE2E } from './helpers/login';

const baseApiUrl = process.env.ECM_API_URL || 'http://localhost:7700';
const defaultUsername = process.env.ECM_E2E_USERNAME || 'admin';
const defaultPassword = process.env.ECM_E2E_PASSWORD || 'admin';
const viewerUsername = process.env.ECM_E2E_VIEWER_USERNAME || 'viewer';
const viewerPassword = process.env.ECM_E2E_VIEWER_PASSWORD || 'viewer';

test.beforeEach(async ({ request }) => {
  await waitForApiReady(request, { apiUrl: baseApiUrl });
});

async function createFolder(
  request: APIRequestContext,
  parentId: string,
  folderName: string,
  token: string,
) {
  const res = await request.post(`${baseApiUrl}/api/v1/folders`, {
    headers: { Authorization: `Bearer ${token}`, 'Content-Type': 'application/json' },
    data: { name: folderName, parentId, folderType: 'GENERAL', inheritPermissions: true },
  });
  expect(res.ok()).toBeTruthy();
  const payload = (await res.json()) as { id: string };
  if (!payload.id) {
    throw new Error('Failed to create folder');
  }
  return payload.id;
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

async function uploadDocument(
  request: APIRequestContext,
  folderId: string,
  filename: string,
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
          buffer: Buffer.from(`Search view e2e ${new Date().toISOString()}\n`),
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

  const indexRes = await request.post(`${baseApiUrl}/api/v1/search/index/${documentId}`, {
    params: { refresh: true },
    headers: { Authorization: `Bearer ${token}` },
  });
  expect(indexRes.ok()).toBeTruthy();
  return documentId;
}

async function setPermission(
  request: APIRequestContext,
  nodeId: string,
  token: string,
  options: {
    authority: string;
    authorityType: 'USER' | 'GROUP' | 'ROLE' | 'EVERYONE';
    permissionType: 'READ' | 'WRITE' | 'DELETE' | 'CHANGE_PERMISSIONS';
    allowed: boolean;
  },
) {
  const res = await request.post(`${baseApiUrl}/api/v1/security/nodes/${nodeId}/permissions`, {
    params: {
      authority: options.authority,
      authorityType: options.authorityType,
      permissionType: options.permissionType,
      allowed: options.allowed,
    },
    headers: { Authorization: `Bearer ${token}` },
  });
  expect(res.ok()).toBeTruthy();
}

async function waitForIndexStatus(
  request: APIRequestContext,
  documentId: string,
  token: string,
) {
  for (let attempt = 0; attempt < 30; attempt += 1) {
    const res = await request.get(`${baseApiUrl}/api/v1/search/index/${documentId}/status`, {
      headers: { Authorization: `Bearer ${token}` },
    });
    if (res.ok()) {
      const payload = (await res.json()) as { indexed?: boolean };
      if (payload.indexed) {
        return true;
      }
    }
    await new Promise((resolve) => setTimeout(resolve, 2000));
  }
  return false;
}

test('Search results view opens preview for documents', async ({ page, request }) => {
  test.setTimeout(240_000);

  const apiToken = await fetchAccessToken(request, defaultUsername, defaultPassword);
  const rootId = await getRootFolderId(request, apiToken, { apiUrl: baseApiUrl });
  const documentsId = await findChildFolderId(request, rootId, 'Documents', apiToken, { apiUrl: baseApiUrl });

  const folderName = `e2e-search-view-${Date.now()}`;
  const folderId = await createFolder(request, documentsId, folderName, apiToken);

  const filename = `e2e-view-${Date.now()}.txt`;
  const documentId = await uploadDocument(request, folderId, filename, apiToken);
  const indexed = await waitForIndexStatus(request, documentId, apiToken);
  if (!indexed) {
    console.log(`Index status not ready for ${documentId}; falling back to search polling`);
  }
  await reindexByQuery(request, filename, apiToken, { apiUrl: baseApiUrl, limit: 5, refresh: true });
  await waitForSearchIndex(request, filename, apiToken, { apiUrl: baseApiUrl, maxAttempts: 60 });

  await gotoWithAuthE2E(page, '/search-results', defaultUsername, defaultPassword, { token: apiToken });

  await expect(page.getByText(/Access scope/i)).toBeVisible({ timeout: 60_000 });
  await expect(page.getByText(/Index stats/i)).toBeVisible({ timeout: 60_000 });
  await expect(page.getByText(/Search enabled/i)).toBeVisible({ timeout: 60_000 });
  await expect(page.getByText(/Rebuild status/i)).toBeVisible({ timeout: 60_000 });

  const quickSearchInput = page.getByPlaceholder('Quick search by name...');
  await quickSearchInput.fill(filename);
  await quickSearchInput.press('Enter');

  const resultCard = page.locator('.MuiCard-root').filter({ hasText: filename }).first();
  await expect(resultCard).toBeVisible({ timeout: 60_000 });
  await resultCard.getByRole('button', { name: 'More like this' }).click();

  const backButton = page.getByRole('button', { name: 'Back to results' });
  const similarErrorAlert = page.getByText(/Failed to load similar documents|No similar documents found/i);
  let outcome: 'back' | 'error' | null = null;
  for (let attempt = 0; attempt < 30; attempt += 1) {
    if (await backButton.isVisible().catch(() => false)) {
      outcome = 'back';
      break;
    }
    if (await similarErrorAlert.isVisible().catch(() => false)) {
      outcome = 'error';
      break;
    }
    await page.waitForTimeout(1000);
  }
  if (outcome === 'back') {
    await backButton.click();
    await expect(page.locator('.MuiCard-root').filter({ hasText: filename }).first()).toBeVisible({ timeout: 60_000 });
  } else if (outcome === 'error') {
    await quickSearchInput.fill(filename);
    await quickSearchInput.press('Enter');
    await expect(page.locator('.MuiCard-root').filter({ hasText: filename }).first()).toBeVisible({ timeout: 60_000 });
  } else {
    await quickSearchInput.fill(filename);
    await quickSearchInput.press('Enter');
    await expect(page.locator('.MuiCard-root').filter({ hasText: filename }).first()).toBeVisible({ timeout: 60_000 });
  }

  const viewCard = page.locator('.MuiCard-root').filter({ hasText: filename }).first();
  await viewCard.getByRole('button', { name: 'View', exact: true }).click();

  await expect(page).toHaveURL(/\/search-results/);
  const previewCloseButton = page.locator('button[aria-label="close"]:not(.Toastify__close-button)').first();
  await expect(previewCloseButton).toBeVisible({ timeout: 60_000 });
  await previewCloseButton.click();

  await request.delete(`${baseApiUrl}/api/v1/nodes/${folderId}`, {
    headers: { Authorization: `Bearer ${apiToken}` },
  }).catch(() => null);
});

test('Search results hide unauthorized documents for viewer', async ({ page, request }) => {
  test.setTimeout(240_000);

  const apiToken = await fetchAccessToken(request, defaultUsername, defaultPassword);
  const searchAvailable = await isSearchAvailable(request, apiToken);
  test.skip(!searchAvailable, 'Search is disabled');

  const rootId = await getRootFolderId(request, apiToken, { apiUrl: baseApiUrl });
  const documentsId = await findChildFolderId(request, rootId, 'Documents', apiToken, { apiUrl: baseApiUrl });

  const folderName = `e2e-search-acl-${Date.now()}`;
  const folderId = await createFolder(request, documentsId, folderName, apiToken);

  const filename = `e2e-acl-${Date.now()}.txt`;
  const documentId = await uploadDocument(request, folderId, filename, apiToken);
  const indexed = await waitForIndexStatus(request, documentId, apiToken);
  if (!indexed) {
    console.log(`Index status not ready for ${documentId}; falling back to search polling`);
  }
  await waitForSearchIndex(request, filename, apiToken, { apiUrl: baseApiUrl, maxAttempts: 60 });

  await setPermission(request, documentId, apiToken, {
    authority: 'EVERYONE',
    authorityType: 'EVERYONE',
    permissionType: 'READ',
    allowed: false,
  });
  await reindexByQuery(request, filename, apiToken, { apiUrl: baseApiUrl, limit: 5, refresh: true });

  const viewerToken = await fetchAccessToken(request, viewerUsername, viewerPassword);
  await gotoWithAuthE2E(page, '/search-results', viewerUsername, viewerPassword, { token: viewerToken });

  const quickSearchInput = page.getByPlaceholder('Quick search by name...');
  await quickSearchInput.fill(filename);
  const searchResponsePromise = page.waitForResponse((response) => {
    if (!response.url().includes('/api/v1/search')) {
      return false;
    }
    try {
      const params = new URL(response.url()).searchParams;
      return params.get('q') === filename && response.status() === 200;
    } catch {
      return false;
    }
  });
  await quickSearchInput.press('Enter');
  const searchResponse = await searchResponsePromise;
  const searchPayload = (await searchResponse.json()) as { content?: Array<{ name?: string }> };
  const searchNames = searchPayload.content?.map((item) => item.name) ?? [];
  expect(searchNames).not.toContain(filename);

  await expect(page.locator('.MuiCard-root', { hasText: filename })).toHaveCount(0);

  await request.delete(`${baseApiUrl}/api/v1/nodes/${folderId}`, {
    headers: { Authorization: `Bearer ${apiToken}` },
  }).catch(() => null);
});
