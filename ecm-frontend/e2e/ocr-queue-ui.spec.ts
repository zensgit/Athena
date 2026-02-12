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
    throw new Error(`PDF upload failed (${uploadRes.status()}): ${body}`);
  }
  const uploadJson = (await uploadRes.json()) as { documentId?: string; id?: string };
  const documentId = uploadJson.documentId ?? uploadJson.id;
  if (!documentId) {
    throw new Error('PDF upload missing document id');
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

test('Document preview menu exposes OCR queue actions', async ({ page, request }) => {
  test.setTimeout(240_000);

  const apiToken = await fetchAccessToken(request, defaultUsername, defaultPassword);
  const rootId = await getRootFolderId(request, apiToken, { apiUrl: baseApiUrl });
  const documentsId = await findChildFolderId(request, rootId, 'Documents', apiToken, { apiUrl: baseApiUrl });

  const folderName = `e2e-ocr-queue-${Date.now()}`;
  const folderId = await createFolder(request, documentsId, folderName, apiToken);

  const filename = `e2e-ocr-${Date.now()}.pdf`;
  const documentId = await uploadPdf(request, folderId, filename, apiToken);
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
  await page.waitForSelector('.react-pdf__Page__canvas, [data-testid="pdf-preview-fallback"]', { timeout: 60_000 });

  const queueOcrResponse = page.waitForResponse((response) =>
    response.url().includes(`/api/v1/documents/${documentId}/ocr/queue`)
      && response.status() < 500,
  );
  await page.getByRole('button', { name: 'More actions' }).click();
  await page.getByRole('menuitem', { name: 'Queue OCR' }).click();
  await queueOcrResponse;
  await expect(previewDialog.getByText(/OCR: (Processing|Disabled|Ready|Failed|Skipped|Unavailable)/i)).toBeVisible({
    timeout: 60_000,
  });

  const forceOcrResponse = page.waitForResponse((response) =>
    response.url().includes(`/api/v1/documents/${documentId}/ocr/queue`)
      && response.status() < 500,
  );
  await page.getByRole('button', { name: 'More actions' }).click();
  await page.getByRole('menuitem', { name: 'Force OCR Rebuild' }).click();
  await forceOcrResponse;
  await expect(previewDialog.getByText(/OCR:/i)).toBeVisible({ timeout: 60_000 });

  await previewDialog.getByLabel('close').click();

  await request.delete(`${baseApiUrl}/api/v1/nodes/${folderId}`, {
    headers: { Authorization: `Bearer ${apiToken}` },
  }).catch(() => null);
});

