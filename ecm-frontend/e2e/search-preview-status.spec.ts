import { APIRequestContext, expect, test } from '@playwright/test';
import {
  fetchAccessToken,
  findChildFolderId,
  getRootFolderId,
  waitForApiReady,
  waitForSearchIndex,
} from './helpers/api';
import { gotoWithAuthE2E } from './helpers/login';

const baseApiUrl = process.env.ECM_API_URL || 'http://localhost:7700';
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
          buffer: Buffer.from(`search-preview-status-${Date.now()}`),
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

async function uploadBinaryFile(
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
          mimeType: 'application/octet-stream',
          buffer: Buffer.from([1, 2, 3, 4, 5]),
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

test('Search preview status filters are visible and selectable', async ({ page, request }) => {
  test.setTimeout(240_000);
  await waitForApiReady(request, { apiUrl: baseApiUrl });
  const token = await fetchAccessToken(request, defaultUsername, defaultPassword);
  const rootId = await getRootFolderId(request, token, { apiUrl: baseApiUrl });
  const documentsId = await findChildFolderId(request, rootId, 'Documents', token, { apiUrl: baseApiUrl });

  const folderName = `e2e-search-preview-${Date.now()}`;
  const folderId = await createFolder(request, documentsId, folderName, token);
  const filename = `e2e-preview-status-${Date.now()}.txt`;
  const documentId = await uploadTextFile(request, folderId, filename, token);

  const indexRes = await request.post(`${baseApiUrl}/api/v1/search/index/${documentId}`,
    { headers: { Authorization: `Bearer ${token}` } });
  expect(indexRes.ok()).toBeTruthy();
  await waitForSearchIndex(request, filename, token, { apiUrl: baseApiUrl, maxAttempts: 40 });

  await gotoWithAuthE2E(page, '/search-results', defaultUsername, defaultPassword, { token });

  const quickSearchInput = page.getByPlaceholder('Quick search by name...');
  await quickSearchInput.fill(filename);
  await quickSearchInput.press('Enter');

  const resultCard = page.locator('.MuiCard-root').filter({ hasText: filename }).first();
  await expect(resultCard).toBeVisible({ timeout: 60_000 });
  await expect(resultCard.locator('.MuiChip-label', { hasText: /Preview/i }).first()).toBeVisible();

  await expect(page.getByText('Preview Status')).toBeVisible();
  const pendingChip = page.getByRole('button', { name: /Pending \(\d+\)/ }).first();
  await expect(pendingChip).toBeVisible();

  await request.delete(`${baseApiUrl}/api/v1/nodes/${folderId}`,
    { headers: { Authorization: `Bearer ${token}` } }).catch(() => null);
});

test('Unsupported preview shows neutral status without retry in search results', async ({ page, request }) => {
  test.setTimeout(240_000);
  await waitForApiReady(request, { apiUrl: baseApiUrl });
  const token = await fetchAccessToken(request, defaultUsername, defaultPassword);
  const rootId = await getRootFolderId(request, token, { apiUrl: baseApiUrl });
  const documentsId = await findChildFolderId(request, rootId, 'Documents', token, { apiUrl: baseApiUrl });

  const folderName = `e2e-preview-failure-${Date.now()}`;
  const folderId = await createFolder(request, documentsId, folderName, token);
  const filename = `e2e-preview-failure-${Date.now()}.bin`;
  const documentId = await uploadBinaryFile(request, folderId, filename, token);

  const previewRes = await request.get(`${baseApiUrl}/api/v1/documents/${documentId}/preview`, {
    headers: { Authorization: `Bearer ${token}` },
  });
  expect(previewRes.ok()).toBeTruthy();

  const indexRes = await request.post(`${baseApiUrl}/api/v1/search/index/${documentId}`,
    { headers: { Authorization: `Bearer ${token}` } });
  expect(indexRes.ok()).toBeTruthy();
  await waitForSearchIndex(request, filename, token, { apiUrl: baseApiUrl, maxAttempts: 40 });

  await gotoWithAuthE2E(page, '/search-results', defaultUsername, defaultPassword, { token });

  const quickSearchInput = page.getByPlaceholder('Quick search by name...');
  await quickSearchInput.fill(filename);
  await quickSearchInput.press('Enter');

  const resultCard = page.locator('.MuiCard-root').filter({ hasText: filename }).first();
  await expect(resultCard).toBeVisible({ timeout: 60_000 });
  await expect(resultCard.getByText(/Preview unsupported/i)).toBeVisible();
  await expect(resultCard.getByRole('button', { name: /Preview failure reason/i })).toHaveCount(0);
  await expect(resultCard.getByRole('button', { name: /Retry preview/i })).toHaveCount(0);

  await request.delete(`${baseApiUrl}/api/v1/nodes/${folderId}`,
    { headers: { Authorization: `Bearer ${token}` } }).catch(() => null);
});

test('Advanced search keeps failed filter but hides retry actions for unsupported previews', async ({ page, request }) => {
  test.setTimeout(240_000);
  await waitForApiReady(request, { apiUrl: baseApiUrl });
  const token = await fetchAccessToken(request, defaultUsername, defaultPassword);
  const rootId = await getRootFolderId(request, token, { apiUrl: baseApiUrl });
  const documentsId = await findChildFolderId(request, rootId, 'Documents', token, { apiUrl: baseApiUrl });

  const folderName = `e2e-advanced-preview-failure-${Date.now()}`;
  const folderId = await createFolder(request, documentsId, folderName, token);
  const filename = `e2e-advanced-preview-failure-${Date.now()}.bin`;
  const documentId = await uploadBinaryFile(request, folderId, filename, token);

  const previewRes = await request.get(`${baseApiUrl}/api/v1/documents/${documentId}/preview`, {
    headers: { Authorization: `Bearer ${token}` },
  });
  expect(previewRes.ok()).toBeTruthy();

  const indexRes = await request.post(`${baseApiUrl}/api/v1/search/index/${documentId}`,
    { headers: { Authorization: `Bearer ${token}` } });
  expect(indexRes.ok()).toBeTruthy();
  await waitForSearchIndex(request, filename, token, { apiUrl: baseApiUrl, maxAttempts: 40 });

  const advancedSearchPath = `/search?q=${encodeURIComponent(filename)}&dateRange=week&minSize=1&previewStatus=FAILED`;
  await gotoWithAuthE2E(page, advancedSearchPath, defaultUsername, defaultPassword, { token });

  const advancedSearchInput = page.getByLabel('Search query');
  await expect(advancedSearchInput).toHaveValue(filename, { timeout: 60_000 });
  await expect(page.getByLabel('Min size')).toHaveValue('1');
  await expect.poll(() => new URL(page.url()).searchParams.get('dateRange')).toBe('week');
  await expect.poll(() => new URL(page.url()).searchParams.get('minSize')).toBe('1');
  await expect.poll(() => new URL(page.url()).searchParams.get('previewStatus')).toBe('FAILED');

  const resultCard = page.locator('.MuiPaper-root').filter({ hasText: filename }).first();
  await expect(resultCard).toBeVisible({ timeout: 60_000 });
  await expect(resultCard.getByText(/Preview unsupported/i)).toBeVisible();
  const previewStatusPanel = page.locator('.MuiPaper-root').filter({ hasText: 'Preview Status' }).first();
  await expect(previewStatusPanel).toBeVisible();
  const failedStatusChip = previewStatusPanel.getByRole('button', { name: /Failed \(\d+\)/i }).first();
  await expect(failedStatusChip).toBeVisible();
  await expect(failedStatusChip).toHaveClass(/MuiChip-filled/);
  await expect(previewStatusPanel.getByText(/Preview status filters apply to the current page only/i)).toBeVisible();
  await expect.poll(() => new URL(page.url()).searchParams.get('previewStatus')).toBe('FAILED');
  await expect.poll(() => new URL(page.url()).searchParams.get('q')).toBe(filename);

  await page.reload({ waitUntil: 'domcontentloaded' });
  await expect(page.getByLabel('Search query')).toHaveValue(filename, { timeout: 30_000 });
  await expect(page.getByLabel('Min size')).toHaveValue('1');
  await expect.poll(() => new URL(page.url()).searchParams.get('previewStatus')).toBe('FAILED');
  await expect.poll(() => new URL(page.url()).searchParams.get('dateRange')).toBe('week');
  await expect.poll(() => new URL(page.url()).searchParams.get('minSize')).toBe('1');
  const reloadedPreviewStatusPanel = page.locator('.MuiPaper-root').filter({ hasText: 'Preview Status' }).first();
  const reloadedFailedChip = reloadedPreviewStatusPanel.getByRole('button', { name: /Failed \(\d+\)/i }).first();
  await expect(reloadedFailedChip).toHaveClass(/MuiChip-filled/);

  await expect(page.getByRole('button', { name: /Retry failed previews/i })).toHaveCount(0);
  await expect(page.getByRole('button', { name: /Retry \".+\" \(\d+\)/i })).toHaveCount(0);
  await expect(resultCard.getByRole('button', { name: /Retry preview/i })).toHaveCount(0);

  await request.delete(`${baseApiUrl}/api/v1/nodes/${folderId}`,
    { headers: { Authorization: `Bearer ${token}` } }).catch(() => null);
});
