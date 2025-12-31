import { APIRequestContext, expect, Page, test } from '@playwright/test';

const baseApiUrl = process.env.ECM_API_URL || 'http://localhost:7700';
const baseUiUrl = process.env.ECM_UI_URL || 'http://localhost:5500';
const defaultUsername = process.env.ECM_E2E_USERNAME || 'admin';
const defaultPassword = process.env.ECM_E2E_PASSWORD || 'admin';

async function fetchAccessToken(request: APIRequestContext, username: string, password: string) {
  const tokenRes = await request.post('http://localhost:8180/realms/ecm/protocol/openid-connect/token', {
    form: {
      grant_type: 'password',
      client_id: 'unified-portal',
      username,
      password,
    },
  });
  expect(tokenRes.ok()).toBeTruthy();
  const tokenJson = (await tokenRes.json()) as { access_token?: string };
  if (!tokenJson.access_token) {
    throw new Error('Failed to obtain access token for API calls');
  }
  return tokenJson.access_token;
}

async function loginWithCredentials(page: Page, username: string, password: string) {
  await page.goto(`${baseUiUrl}/`, { waitUntil: 'domcontentloaded' });

  if (page.url().endsWith('/login')) {
    await page.getByRole('button', { name: /sign in with keycloak/i }).click();
  }

  await page.waitForURL(/(\/browse\/|\/protocol\/openid-connect\/auth)/, { timeout: 60_000 });
  if (page.url().includes('/protocol/openid-connect/auth')) {
    await page.locator('#username').fill(username);
    await page.locator('#password').fill(password);
    await Promise.all([
      page.waitForNavigation({ waitUntil: 'domcontentloaded' }),
      page.locator('#kc-login').click(),
    ]);
  }

  await page.waitForURL(/\/browse\//, { timeout: 60_000 });
}

async function getRootFolderId(request: APIRequestContext, token: string) {
  const res = await request.get(`${baseApiUrl}/api/v1/folders/roots`, {
    headers: { Authorization: `Bearer ${token}` },
  });
  expect(res.ok()).toBeTruthy();
  const roots = (await res.json()) as Array<{ id: string; name: string; path?: string; folderType?: string }>;
  const preferred = roots.find((root) => {
    const isSystem = root.folderType?.toUpperCase() === 'SYSTEM';
    return isSystem || root.name === 'Root' || root.path === '/Root';
  });
  if (!preferred && roots.length === 0) {
    throw new Error('No root folders returned');
  }
  return (preferred ?? roots[0]).id;
}

async function findChildFolderId(
  request: APIRequestContext,
  parentId: string,
  folderName: string,
  token: string,
) {
  const response = await request.get(`${baseApiUrl}/api/v1/folders/${parentId}/contents`, {
    params: { page: 0, size: 200, sort: 'name,asc' },
    headers: { Authorization: `Bearer ${token}` },
  });
  expect(response.ok()).toBeTruthy();
  const payload = (await response.json()) as { content?: Array<{ id: string; name: string; nodeType: string }> };
  const match = payload.content?.find((node) => node.name === folderName && node.nodeType === 'FOLDER');
  if (!match?.id) {
    throw new Error(`Folder not found: ${folderName}`);
  }
  return match.id;
}

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
    headers: { Authorization: `Bearer ${token}` },
  });
  expect(indexRes.ok()).toBeTruthy();
  return documentId;
}

async function waitForSearchIndex(
  request: APIRequestContext,
  query: string,
  token: string,
) {
  for (let attempt = 0; attempt < 30; attempt += 1) {
    const res = await request.get(`${baseApiUrl}/api/v1/search`, {
      params: { q: query, page: 0, size: 10 },
      headers: { Authorization: `Bearer ${token}` },
    });
    if (res.ok()) {
      const payload = (await res.json()) as { content?: Array<{ name: string }> };
      if (payload.content?.some((item) => item.name === query)) {
        return;
      }
    }
    await new Promise((resolve) => setTimeout(resolve, 2000));
  }
  throw new Error(`Search index did not return ${query}`);
}

test('Search results view opens preview for documents', async ({ page, request }) => {
  test.setTimeout(240_000);

  const apiToken = await fetchAccessToken(request, defaultUsername, defaultPassword);
  const rootId = await getRootFolderId(request, apiToken);
  const documentsId = await findChildFolderId(request, rootId, 'Documents', apiToken);

  const folderName = `e2e-search-view-${Date.now()}`;
  const folderId = await createFolder(request, documentsId, folderName, apiToken);

  const filename = `e2e-view-${Date.now()}.txt`;
  await uploadDocument(request, folderId, filename, apiToken);
  await waitForSearchIndex(request, filename, apiToken);

  await loginWithCredentials(page, defaultUsername, defaultPassword);
  await page.goto(`${baseUiUrl}/search-results`, { waitUntil: 'domcontentloaded' });

  const quickSearchInput = page.getByPlaceholder('Quick search by name...');
  await quickSearchInput.fill(filename);
  await quickSearchInput.press('Enter');

  const resultCard = page.locator('.MuiCard-root').filter({ hasText: filename }).first();
  await expect(resultCard).toBeVisible({ timeout: 60_000 });
  await resultCard.getByRole('button', { name: 'View', exact: true }).click();

  await expect(page).toHaveURL(/\/search-results/);
  await expect(page.getByLabel('close')).toBeVisible({ timeout: 60_000 });

  await page.getByLabel('close').click();

  await request.delete(`${baseApiUrl}/api/v1/nodes/${folderId}`, {
    headers: { Authorization: `Bearer ${apiToken}` },
  }).catch(() => null);
});
