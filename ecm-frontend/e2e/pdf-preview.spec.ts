import { APIRequestContext, expect, Page, test } from '@playwright/test';
import { PDF_SAMPLE_BASE64 } from './fixtures/pdfSample';

const baseApiUrl = process.env.ECM_API_URL || 'http://localhost:7700';
const baseUiUrl = process.env.ECM_UI_URL || 'http://localhost:5500';
const defaultUsername = process.env.ECM_E2E_USERNAME || 'admin';
const defaultPassword = process.env.ECM_E2E_PASSWORD || 'admin';

async function waitForApiReady(request: APIRequestContext) {
  const deadline = Date.now() + 60_000;
  let lastError: string | undefined;

  while (Date.now() < deadline) {
    try {
      const res = await request.get(`${baseApiUrl}/actuator/health`);
      if (res.ok()) {
        const payload = (await res.json()) as { status?: string };
        if (!payload?.status || payload.status.toUpperCase() !== 'DOWN') {
          return;
        }
        lastError = `health status=${payload.status}`;
      } else {
        lastError = `health status code=${res.status()}`;
      }
    } catch (error) {
      lastError = error instanceof Error ? error.message : String(error);
    }

    await new Promise((resolve) => setTimeout(resolve, 2000));
  }

  throw new Error(`API did not become ready: ${lastError ?? 'unknown error'}`);
}

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

  if (!/\/browse\//.test(page.url())) {
    await page.goto(`${baseUiUrl}/browse/root`, { waitUntil: 'domcontentloaded' });
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

async function uploadPdf(
  request: APIRequestContext,
  folderId: string,
  filename: string,
  token: string,
) {
  const pdfBytes = Buffer.from(PDF_SAMPLE_BASE64, 'base64');
  const uploadRes = await request.post(
    `${baseApiUrl}/api/v1/documents/upload?folderId=${folderId}`,
    {
      headers: { Authorization: `Bearer ${token}` },
      multipart: {
        file: {
          name: filename,
          mimeType: 'application/pdf',
          buffer: pdfBytes,
        },
      },
    },
  );
  if (!uploadRes.ok()) {
    const body = await uploadRes.text().catch(() => '');
    throw new Error(`PDF upload failed (${uploadRes.status()}): ${body}`);
  }
  const uploadJson = (await uploadRes.json()) as { documentId?: string; id?: string };
  const documentId = uploadJson.documentId ?? uploadJson.id;
  if (!documentId) {
    throw new Error('Upload did not return document id');
  }
  const indexRes = await request.post(`${baseApiUrl}/api/v1/search/index/${documentId}`, {
    headers: { Authorization: `Bearer ${token}` },
  });
  if (!indexRes.ok()) {
    const body = await indexRes.text().catch(() => '');
    throw new Error(`Search index trigger failed (${indexRes.status()}): ${body}`);
  }
  return documentId;
}

async function waitForSearchIndex(
  request: APIRequestContext,
  query: string,
  token: string,
) {
  let lastError = 'unknown error';
  for (let attempt = 0; attempt < 40; attempt += 1) {
    try {
      const res = await request.get(`${baseApiUrl}/api/v1/search`, {
        params: { q: query, page: 0, size: 10 },
        headers: { Authorization: `Bearer ${token}` },
      });
      if (res.ok()) {
        const payload = (await res.json()) as { content?: Array<{ name: string }> };
        if (payload.content?.some((item) => item.name === query)) {
          return;
        }
        lastError = `status=${res.status()}`;
      } else {
        lastError = `status=${res.status()}`;
      }
    } catch (error) {
      lastError = error instanceof Error ? error.message : String(error);
    }
    await new Promise((resolve) => setTimeout(resolve, 2000));
  }
  throw new Error(`Search index did not return ${query} (${lastError})`);
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

test('PDF preview shows dialog and controls', async ({ page, request }) => {
  test.setTimeout(240_000);

  await waitForApiReady(request);
  const apiToken = await fetchAccessToken(request, defaultUsername, defaultPassword);
  const rootId = await getRootFolderId(request, apiToken);
  const documentsId = await findChildFolderId(request, rootId, 'Documents', apiToken);

  const folderName = `e2e-pdf-preview-${Date.now()}`;
  const folderId = await createFolder(request, documentsId, folderName, apiToken);

  const filename = `e2e-preview-${Date.now()}.pdf`;
  const documentId = await uploadPdf(request, folderId, filename, apiToken);

  const indexed = await waitForIndexStatus(request, documentId, apiToken);
  if (!indexed) {
    console.log(`Index status not ready for ${documentId}; falling back to search polling`);
  }
  await waitForSearchIndex(request, filename, apiToken);

  await loginWithCredentials(page, defaultUsername, defaultPassword);
  await waitForSearchIndex(request, filename, apiToken);

  await page.goto(`${baseUiUrl}/search-results`, { waitUntil: 'domcontentloaded' });

  const quickSearchInput = page.getByPlaceholder('Quick search by name...');
  await quickSearchInput.fill(filename);
  await quickSearchInput.press('Enter');

  const resultCard = page.locator('.MuiCard-root').filter({ hasText: filename }).first();
  await expect(resultCard).toBeVisible({ timeout: 60_000 });
  await resultCard.getByRole('button', { name: 'View', exact: true }).click();

  await expect(page.getByLabel('close')).toBeVisible({ timeout: 60_000 });
  await page.waitForSelector('.react-pdf__Page__canvas, [data-testid="pdf-preview-fallback"]', { timeout: 60000 });
  await expect(page.locator('[data-testid=ZoomInIcon]')).toBeVisible();
  await expect(page.locator('[data-testid=ZoomOutIcon]')).toBeVisible();
  await expect(page.locator('[data-testid=RotateLeftIcon]')).toBeVisible();
  await expect(page.locator('[data-testid=RotateRightIcon]')).toBeVisible();
  await expect(page.getByRole('button', { name: /annotate/i })).toBeVisible();

  await page.getByLabel('close').click();

  await request.delete(`${baseApiUrl}/api/v1/nodes/${folderId}`, {
    headers: { Authorization: `Bearer ${apiToken}` },
  }).catch(() => null);
});

test('PDF preview falls back to server render when client PDF fails', async ({ page, request }) => {
  test.setTimeout(240_000);

  await waitForApiReady(request);
  const apiToken = await fetchAccessToken(request, defaultUsername, defaultPassword);
  const rootId = await getRootFolderId(request, apiToken);
  const documentsId = await findChildFolderId(request, rootId, 'Documents', apiToken);

  const folderName = `e2e-pdf-fallback-${Date.now()}`;
  const folderId = await createFolder(request, documentsId, folderName, apiToken);

  const filename = `e2e-preview-fallback-${Date.now()}.pdf`;
  const documentId = await uploadPdf(request, folderId, filename, apiToken);
  const indexed = await waitForIndexStatus(request, documentId, apiToken);
  if (!indexed) {
    console.log(`Index status not ready for ${documentId}; falling back to search polling`);
  }
  await waitForSearchIndex(request, filename, apiToken);

  await loginWithCredentials(page, defaultUsername, defaultPassword);
  await waitForSearchIndex(request, filename, apiToken);

  await page.route('**/pdf.worker.min.mjs', (route) => route.abort());

  await page.goto(`${baseUiUrl}/search-results`, { waitUntil: 'domcontentloaded' });

  const quickSearchInput = page.getByPlaceholder('Quick search by name...');
  await quickSearchInput.fill(filename);
  await quickSearchInput.press('Enter');

  const resultCard = page.locator('.MuiCard-root').filter({ hasText: filename }).first();
  await expect(resultCard).toBeVisible({ timeout: 60_000 });
  await resultCard.getByRole('button', { name: 'View', exact: true }).click();

  await expect(page.getByLabel('close')).toBeVisible({ timeout: 60_000 });
  await page.waitForSelector('[data-testid=\"pdf-preview-fallback\"] img', { timeout: 60_000 });

  await page.getByLabel('close').click();

  await request.delete(`${baseApiUrl}/api/v1/nodes/${folderId}`, {
    headers: { Authorization: `Bearer ${apiToken}` },
  }).catch(() => null);
});

test('File browser view action opens preview', async ({ page, request }) => {
  test.setTimeout(240_000);

  await waitForApiReady(request);
  const apiToken = await fetchAccessToken(request, defaultUsername, defaultPassword);
  const rootId = await getRootFolderId(request, apiToken);
  const documentsId = await findChildFolderId(request, rootId, 'Documents', apiToken);

  const folderName = `e2e-file-browser-view-${Date.now()}`;
  const folderId = await createFolder(request, documentsId, folderName, apiToken);

  const filename = `e2e-file-browser-view-${Date.now()}.pdf`;
  await uploadPdf(request, folderId, filename, apiToken);

  await loginWithCredentials(page, defaultUsername, defaultPassword);

  await page.goto(`${baseUiUrl}/browse/${folderId}`, { waitUntil: 'domcontentloaded' });

  const listToggle = page.getByLabel('list view');
  if (await listToggle.isVisible()) {
    await listToggle.click();
  }

  const actionsButton = page.getByLabel(`Actions for ${filename}`);
  await expect(actionsButton).toBeVisible({ timeout: 60_000 });
  await actionsButton.click();

  const viewItem = page.getByRole('menuitem', { name: /^View$/ });
  await expect(viewItem).toBeVisible();
  await viewItem.click();

  await expect(page.getByLabel('close')).toBeVisible({ timeout: 60_000 });
  await page.waitForSelector('.react-pdf__Page__canvas, [data-testid="pdf-preview-fallback"]', { timeout: 60_000 });

  await page.getByLabel('close').click();

  await request.delete(`${baseApiUrl}/api/v1/nodes/${folderId}`, {
    headers: { Authorization: `Bearer ${apiToken}` },
  }).catch(() => null);
});
