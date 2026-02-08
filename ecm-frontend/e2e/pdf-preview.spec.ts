import { APIRequestContext, expect, test } from '@playwright/test';
import {
  fetchAccessToken,
  findChildFolderId,
  getRootFolderId,
  reindexByQuery,
  resolveApiUrl,
  waitForApiReady,
  waitForSearchIndex,
} from './helpers/api';
import { gotoWithAuthE2E } from './helpers/login';
import { PDF_SAMPLE_BASE64 } from './fixtures/pdfSample';

const baseApiUrl = resolveApiUrl();
const defaultUsername = process.env.ECM_E2E_USERNAME || 'admin';
const defaultPassword = process.env.ECM_E2E_PASSWORD || 'admin';

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
    params: { refresh: true },
    headers: { Authorization: `Bearer ${token}` },
  });
  if (!indexRes.ok()) {
    const body = await indexRes.text().catch(() => '');
    throw new Error(`Search index trigger failed (${indexRes.status()}): ${body}`);
  }
  await reindexByQuery(request, filename, token, { apiUrl: baseApiUrl, limit: 5, refresh: true });
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

  await waitForSearchIndex(request, filename, apiToken, { apiUrl: baseApiUrl, maxAttempts: 40 });
  await gotoWithAuthE2E(page, '/search-results', defaultUsername, defaultPassword, { token: apiToken });

  const quickSearchInput = page.getByPlaceholder('Quick search by name...');
  await quickSearchInput.fill(filename);
  await quickSearchInput.press('Enter');

  const resultCard = page.locator('.MuiCard-root').filter({ hasText: filename }).first();
  await expect(resultCard).toBeVisible({ timeout: 60_000 });
  await resultCard.getByRole('button', { name: 'View', exact: true }).click();

  const previewDialog = page.getByRole('dialog');
  await expect(previewDialog.getByLabel('close')).toBeVisible({ timeout: 60_000 });
  await page.waitForSelector('.react-pdf__Page__canvas, [data-testid="pdf-preview-fallback"]', { timeout: 60000 });
  await expect(previewDialog.getByText(/Source:/i)).toBeVisible({ timeout: 60_000 });
  await expect(page.locator('[data-testid=ZoomInIcon]')).toBeVisible();
  await expect(page.locator('[data-testid=ZoomOutIcon]')).toBeVisible();
  await expect(page.locator('[data-testid=RotateLeftIcon]')).toBeVisible();
  await expect(page.locator('[data-testid=RotateRightIcon]')).toBeVisible();
  await expect(page.getByRole('button', { name: /annotate/i })).toBeVisible();

  const queueResponse = page.waitForResponse((response) =>
    response.url().includes(`/api/v1/documents/${documentId}/preview/queue`)
      && response.status() < 500,
  );
  await page.getByRole('button', { name: 'More actions' }).click();
  await page.getByRole('menuitem', { name: 'Queue Preview' }).click();
  await queueResponse;
  await expect(page.getByText(/Preview generation is in progress/i)).toBeVisible({ timeout: 60_000 });
  await expect(previewDialog.getByText(/Preview: Processing/i)).toBeVisible({ timeout: 60_000 });

  await previewDialog.getByLabel('close').click();

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

  await waitForSearchIndex(request, filename, apiToken, { apiUrl: baseApiUrl, maxAttempts: 40 });

  const workerRoute = /pdf\.worker(\.min)?\.(mjs|js)(\?.*)?$/;
  await page.route(workerRoute, (route) => route.abort());

  try {
    await gotoWithAuthE2E(page, '/search-results', defaultUsername, defaultPassword, { token: apiToken });

    const quickSearchInput = page.getByPlaceholder('Quick search by name...');
    await quickSearchInput.fill(filename);
    await quickSearchInput.press('Enter');

    const resultCard = page.locator('.MuiCard-root').filter({ hasText: filename }).first();
    await expect(resultCard).toBeVisible({ timeout: 60_000 });
    await resultCard.getByRole('button', { name: 'View', exact: true }).click();

    const fallbackDialog = page.getByRole('dialog');
    await expect(fallbackDialog.getByLabel('close')).toBeVisible({ timeout: 60_000 });
    const fallbackPreview = page.getByTestId('pdf-preview-fallback');
    await expect(fallbackPreview).toBeVisible({ timeout: 60_000 });
    await expect(fallbackPreview.getByTestId('pdf-preview-fallback-banner')).toBeVisible();
    await expect(fallbackPreview.getByTestId('pdf-preview-fallback-message')).toBeVisible();
    await expect(fallbackPreview.getByTestId('pdf-preview-fallback-retry')).toBeVisible();
    await expect(fallbackPreview.getByTestId('pdf-preview-fallback-download')).toBeVisible();
  } finally {
    await page.unroute(workerRoute).catch(() => null);
  }

  await page.getByRole('dialog').getByLabel('close').click();

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

  await gotoWithAuthE2E(page, `/browse/${folderId}`, defaultUsername, defaultPassword, { token: apiToken });

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

  const browserDialog = page.getByRole('dialog');
  await expect(browserDialog.getByLabel('close')).toBeVisible({ timeout: 60_000 });
  await page.waitForSelector('.react-pdf__Page__canvas, [data-testid="pdf-preview-fallback"]', { timeout: 60_000 });

  await page.getByLabel('close').click();

  await request.delete(`${baseApiUrl}/api/v1/nodes/${folderId}`, {
    headers: { Authorization: `Bearer ${apiToken}` },
  }).catch(() => null);
});
