import { APIRequestContext, expect, Page, test } from '@playwright/test';
import {
  fetchAccessToken,
  findChildFolderId,
  getRootFolderId,
  waitForApiReady,
  waitForSearchIndex,
} from './helpers/api';
import { PDF_SAMPLE_BASE64 } from './fixtures/pdfSample';

const baseApiUrl = process.env.ECM_API_URL || 'http://localhost:7700';
const baseUiUrl = process.env.ECM_UI_URL || 'http://localhost:5500';
const defaultUsername = process.env.ECM_E2E_USERNAME || 'admin';
const defaultPassword = process.env.ECM_E2E_PASSWORD || 'admin';

async function loginWithCredentials(page: Page, username: string, password: string) {
  const authPattern = /\/protocol\/openid-connect\/auth/;
  const browsePattern = /\/browse\//;

  await page.goto(`${baseUiUrl}/login`, { waitUntil: 'domcontentloaded' });

  for (let attempt = 0; attempt < 3; attempt += 1) {
    await page.waitForURL(/(\/login$|\/browse\/|\/protocol\/openid-connect\/auth|login_required)/, { timeout: 60_000 });

    if (page.url().endsWith('/login')) {
      const keycloakButton = page.getByRole('button', { name: /sign in with keycloak/i });
      try {
        await keycloakButton.waitFor({ state: 'visible', timeout: 30_000 });
        await keycloakButton.click();
      } catch {
        // Retry loop if login screen is not ready yet.
      }
      continue;
    }

    if (page.url().includes('login_required')) {
      await page.goto(`${baseUiUrl}/login`, { waitUntil: 'domcontentloaded' });
      continue;
    }

    if (authPattern.test(page.url())) {
      await page.locator('#username').fill(username);
      await page.locator('#password').fill(password);
      await Promise.all([
        page.waitForNavigation({ waitUntil: 'domcontentloaded' }),
        page.locator('#kc-login').click(),
      ]);
    }

    if (browsePattern.test(page.url())) {
      break;
    }
  }

  if (!browsePattern.test(page.url())) {
    await page.goto(`${baseUiUrl}/browse/root`, { waitUntil: 'domcontentloaded' });
  }
  await page.waitForURL(browsePattern, { timeout: 60_000 });
}

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

async function uploadPdf(
  request: APIRequestContext,
  folderId: string,
  filename: string,
  token: string,
) {
  const pdfBytes = Buffer.from(PDF_SAMPLE_BASE64, 'base64');
  const deadline = Date.now() + 90_000;
  let documentId: string | undefined;
  let lastError: string | undefined;

  while (Date.now() < deadline && !documentId) {
    try {
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
          timeout: 60_000,
        },
      );
      if (!uploadRes.ok()) {
        const body = await uploadRes.text().catch(() => '');
        lastError = `upload status=${uploadRes.status()} ${body}`;
      } else {
        const uploadJson = (await uploadRes.json()) as { documentId?: string; id?: string };
        documentId = uploadJson.documentId ?? uploadJson.id;
        if (!documentId) {
          lastError = 'upload missing document id';
        }
      }
    } catch (error) {
      lastError = error instanceof Error ? error.message : String(error);
    }

    if (!documentId) {
      await new Promise((resolve) => setTimeout(resolve, 2000));
    }
  }

  if (!documentId) {
    throw new Error(`PDF upload failed: ${lastError ?? 'unknown error'}`);
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

  await waitForApiReady(request, { apiUrl: baseApiUrl });
  const apiToken = await fetchAccessToken(request, defaultUsername, defaultPassword);
  const rootId = await getRootFolderId(request, apiToken, { apiUrl: baseApiUrl });
  const documentsId = await findChildFolderId(request, rootId, 'Documents', apiToken, { apiUrl: baseApiUrl });

  const folderName = `e2e-pdf-preview-${Date.now()}`;
  const folderId = await createFolder(request, documentsId, folderName, apiToken);

  const filename = `e2e-preview-${Date.now()}.pdf`;
  const documentId = await uploadPdf(request, folderId, filename, apiToken);

  const indexed = await waitForIndexStatus(request, documentId, apiToken);
  if (!indexed) {
    console.log(`Index status not ready for ${documentId}; falling back to search polling`);
  }
  await waitForSearchIndex(request, filename, apiToken, { apiUrl: baseApiUrl, maxAttempts: 40 });

  await loginWithCredentials(page, defaultUsername, defaultPassword);
  await waitForSearchIndex(request, filename, apiToken, { apiUrl: baseApiUrl, maxAttempts: 40 });

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

  await waitForApiReady(request, { apiUrl: baseApiUrl });
  const apiToken = await fetchAccessToken(request, defaultUsername, defaultPassword);
  const rootId = await getRootFolderId(request, apiToken, { apiUrl: baseApiUrl });
  const documentsId = await findChildFolderId(request, rootId, 'Documents', apiToken, { apiUrl: baseApiUrl });

  const folderName = `e2e-pdf-fallback-${Date.now()}`;
  const folderId = await createFolder(request, documentsId, folderName, apiToken);

  const filename = `e2e-preview-fallback-${Date.now()}.pdf`;
  const documentId = await uploadPdf(request, folderId, filename, apiToken);
  const indexed = await waitForIndexStatus(request, documentId, apiToken);
  if (!indexed) {
    console.log(`Index status not ready for ${documentId}; falling back to search polling`);
  }
  await waitForSearchIndex(request, filename, apiToken, { apiUrl: baseApiUrl, maxAttempts: 40 });

  await loginWithCredentials(page, defaultUsername, defaultPassword);
  await waitForSearchIndex(request, filename, apiToken, { apiUrl: baseApiUrl, maxAttempts: 40 });

  const workerRoute = /pdf\.worker(\.min)?\.(mjs|js)(\?.*)?$/;
  await page.route(workerRoute, (route) => route.abort());

  try {
    await page.goto(`${baseUiUrl}/search-results`, { waitUntil: 'domcontentloaded' });

    const quickSearchInput = page.getByPlaceholder('Quick search by name...');
    await quickSearchInput.fill(filename);
    await quickSearchInput.press('Enter');

    const resultCard = page.locator('.MuiCard-root').filter({ hasText: filename }).first();
    await expect(resultCard).toBeVisible({ timeout: 60_000 });
    await resultCard.getByRole('button', { name: 'View', exact: true }).click();

    await expect(page.getByLabel('close')).toBeVisible({ timeout: 60_000 });
    const fallbackPreview = page.getByTestId('pdf-preview-fallback');
    await expect(fallbackPreview).toBeVisible({ timeout: 60_000 });
    await expect(fallbackPreview.getByTestId('pdf-preview-fallback-banner')).toBeVisible();
    await expect(fallbackPreview.getByTestId('pdf-preview-fallback-message')).toBeVisible();
    await expect(fallbackPreview.getByTestId('pdf-preview-fallback-retry')).toBeVisible();
    await expect(fallbackPreview.getByTestId('pdf-preview-fallback-download')).toBeVisible();
  } finally {
    await page.unroute(workerRoute).catch(() => null);
  }

  await page.getByLabel('close').click();

  await request.delete(`${baseApiUrl}/api/v1/nodes/${folderId}`, {
    headers: { Authorization: `Bearer ${apiToken}` },
  }).catch(() => null);
});

test('File browser view action opens preview', async ({ page, request }) => {
  test.setTimeout(240_000);

  await waitForApiReady(request, { apiUrl: baseApiUrl });
  const apiToken = await fetchAccessToken(request, defaultUsername, defaultPassword);
  const rootId = await getRootFolderId(request, apiToken, { apiUrl: baseApiUrl });
  const documentsId = await findChildFolderId(request, rootId, 'Documents', apiToken, { apiUrl: baseApiUrl });

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
