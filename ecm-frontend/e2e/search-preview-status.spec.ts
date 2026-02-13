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

function randomLetters(length: number) {
  let out = '';
  while (out.length < length) {
    // Keep only lowercase letters so the query doesn't get split into common tokens.
    out += Math.random().toString(36).replace(/[^a-z]+/g, '');
  }
  return out.slice(0, length);
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

async function uploadInvalidPdfFile(
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
          mimeType: 'application/pdf',
          buffer: Buffer.from([0x25, 0x50, 0x44, 0x46, 0x2d, 0x00, 0x01, 0x02, 0x03]), // looks like "%PDF-" then junk
        },
      },
    });
  // An invalid PDF can still create a document record but return 400 due to extraction errors.
  // Accept both (2xx) and 400 as long as we get a document id back.
  const status = uploadRes.status();
  const uploadJson = (await uploadRes.json().catch(() => ({}))) as { documentId?: string; id?: string };
  const documentId = uploadJson.documentId ?? uploadJson.id;
  if (!documentId) {
    throw new Error('Upload did not return document id');
  }
  if (!(uploadRes.ok() || status === 400)) {
    throw new Error(`Upload failed: status=${status}`);
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
  const previewJson = (await previewRes.json()) as { supported?: boolean; status?: string; failureCategory?: string };
  expect(previewJson.supported).toBe(false);
  expect(previewJson.failureCategory).toBe('UNSUPPORTED');
  expect(previewJson.status).toBe('UNSUPPORTED');

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
  await expect(resultCard.getByRole('button', { name: /Force rebuild preview/i })).toHaveCount(0);

  await request.delete(`${baseApiUrl}/api/v1/nodes/${folderId}`,
    { headers: { Authorization: `Bearer ${token}` } }).catch(() => null);
});

test('Advanced search preview status facet counts reflect full result set', async ({ page, request }) => {
  test.setTimeout(360_000);
  await waitForApiReady(request, { apiUrl: baseApiUrl });
  const token = await fetchAccessToken(request, defaultUsername, defaultPassword);
  const rootId = await getRootFolderId(request, token, { apiUrl: baseApiUrl });
  const documentsId = await findChildFolderId(request, rootId, 'Documents', token, { apiUrl: baseApiUrl });

  const queryKey = randomLetters(12);
  const folderName = `e2e-advanced-preview-facet-${Date.now()}`;
  const folderId = await createFolder(request, documentsId, folderName, token);
  const totalDocs = 12;

  try {
    const docIds: string[] = [];
    for (let i = 0; i < totalDocs; i += 1) {
      const filename = `${queryKey}-${String(i + 1).padStart(2, '0')}.bin`;
      const documentId = await uploadBinaryFile(request, folderId, filename, token);
      docIds.push(documentId);

      const previewRes = await request.get(`${baseApiUrl}/api/v1/documents/${documentId}/preview`, {
        headers: { Authorization: `Bearer ${token}` },
      });
      expect(previewRes.ok()).toBeTruthy();
      const previewJson = (await previewRes.json()) as { supported?: boolean; status?: string; failureCategory?: string };
      expect(previewJson.supported).toBe(false);
      expect(previewJson.failureCategory).toBe('UNSUPPORTED');
      expect(previewJson.status).toBe('UNSUPPORTED');

      const indexRes = await request.post(`${baseApiUrl}/api/v1/search/index/${documentId}`,
        { headers: { Authorization: `Bearer ${token}` } });
      expect(indexRes.ok()).toBeTruthy();
    }

    await waitForSearchIndex(request, queryKey, token, { apiUrl: baseApiUrl, maxAttempts: 60, minResults: totalDocs });

    await gotoWithAuthE2E(page, '/search', defaultUsername, defaultPassword, { token });
    await page.getByLabel('Search query').fill(queryKey);
    await page.getByRole('button', { name: /^Search$/ }).filter({ hasText: /^Search$/ }).click();

    await expect(page.getByText(`Found ${totalDocs} results`)).toBeVisible({ timeout: 60_000 });
    await expect(page.getByRole('button', { name: `Unsupported (${totalDocs})` }).first()).toBeVisible({ timeout: 60_000 });
  } finally {
    await request.delete(`${baseApiUrl}/api/v1/nodes/${folderId}`,
      { headers: { Authorization: `Bearer ${token}` } }).catch(() => null);
  }
});

test('Permanent preview failure hides retry and force rebuild actions in search results', async ({ page, request }) => {
  test.setTimeout(240_000);
  await waitForApiReady(request, { apiUrl: baseApiUrl });
  const token = await fetchAccessToken(request, defaultUsername, defaultPassword);
  const rootId = await getRootFolderId(request, token, { apiUrl: baseApiUrl });
  const documentsId = await findChildFolderId(request, rootId, 'Documents', token, { apiUrl: baseApiUrl });

  const folderName = `e2e-preview-failed-${Date.now()}`;
  const folderId = await createFolder(request, documentsId, folderName, token);
  const filename = `e2e-preview-failed-${Date.now()}.pdf`;
  const documentId = await uploadInvalidPdfFile(request, folderId, filename, token);

  const previewRes = await request.get(`${baseApiUrl}/api/v1/documents/${documentId}/preview`, {
    headers: { Authorization: `Bearer ${token}` },
  });
  expect(previewRes.ok()).toBeTruthy();
  const previewJson = (await previewRes.json()) as { supported?: boolean; status?: string; failureCategory?: string };
  expect(previewJson.supported).toBe(false);
  expect((previewJson.status || '').toUpperCase()).toBe('FAILED');
  expect((previewJson.failureCategory || '').toUpperCase()).toBe('PERMANENT');

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
  await expect(resultCard.getByText(/Preview failed/i)).toBeVisible();

  await expect(resultCard.getByRole('button', { name: /Retry preview/i })).toHaveCount(0);
  await expect(resultCard.getByRole('button', { name: /Force rebuild preview/i })).toHaveCount(0);
  await expect(page.getByRole('button', { name: /Retry failed previews/i })).toHaveCount(0);

  await gotoWithAuthE2E(page, '/search', defaultUsername, defaultPassword, { token });
  await page.getByLabel('Search query').fill(filename);
  // The page has both a top nav search icon and the primary submit button.
  // Click the primary button (has visible text) to avoid strict mode ambiguity.
  await page.getByRole('button', { name: /^Search$/ }).filter({ hasText: /^Search$/ }).click();
  const advancedResult = page.locator('.MuiPaper-root').filter({ hasText: filename }).first();
  await expect(advancedResult).toBeVisible({ timeout: 60_000 });
  await expect(advancedResult.getByRole('button', { name: /Retry preview/i })).toHaveCount(0);
  await expect(advancedResult.getByRole('button', { name: /Force rebuild preview/i })).toHaveCount(0);

  await request.delete(`${baseApiUrl}/api/v1/nodes/${folderId}`,
    { headers: { Authorization: `Bearer ${token}` } }).catch(() => null);
});

test('Preview status chips apply server-side filtering with correct totals', async ({ page, request }) => {
  test.setTimeout(240_000);
  await waitForApiReady(request, { apiUrl: baseApiUrl });
  const token = await fetchAccessToken(request, defaultUsername, defaultPassword);
  const rootId = await getRootFolderId(request, token, { apiUrl: baseApiUrl });
  const documentsId = await findChildFolderId(request, rootId, 'Documents', token, { apiUrl: baseApiUrl });

  const folderName = `e2e-preview-filter-${Date.now()}`;
  const folderId = await createFolder(request, documentsId, folderName, token);
  // Use an alpha-only token (then append digits in filenames) to:
  // 1) avoid fuzzy/n-gram matches with other e2e artifacts
  // 2) ensure the standard analyzer splits out the token (it won't for pure alpha + ".ext")
  const tokenPrefix = `pwsrv${randomLetters(16)}`;
  const uniqueSuffix = Date.now();

  const textFilename = `${tokenPrefix}-${uniqueSuffix}.txt`;
  const binFilename = `${tokenPrefix}-${uniqueSuffix}.bin`;
  const textId = await uploadTextFile(request, folderId, textFilename, token);
  const binId = await uploadBinaryFile(request, folderId, binFilename, token);

  // Ensure the binary upload is classified as UNSUPPORTED before validating server-side filtering.
  // This avoids flakiness from the async preview queue leaving the document in PROCESSING.
  const previewRes = await request.get(`${baseApiUrl}/api/v1/documents/${binId}/preview`, {
    headers: { Authorization: `Bearer ${token}` },
  });
  expect(previewRes.ok()).toBeTruthy();
  const previewJson = (await previewRes.json()) as { supported?: boolean; status?: string; failureCategory?: string };
  expect(previewJson.supported).toBe(false);
  expect(previewJson.failureCategory).toBe('UNSUPPORTED');
  expect(previewJson.status).toBe('UNSUPPORTED');

  for (const docId of [textId, binId]) {
    const indexRes = await request.post(`${baseApiUrl}/api/v1/search/index/${docId}`,
      { headers: { Authorization: `Bearer ${token}` } });
    expect(indexRes.ok()).toBeTruthy();
  }
  await waitForSearchIndex(request, textFilename, token, { apiUrl: baseApiUrl, maxAttempts: 40 });
  await waitForSearchIndex(request, binFilename, token, { apiUrl: baseApiUrl, maxAttempts: 40 });

  await gotoWithAuthE2E(page, '/search-results', defaultUsername, defaultPassword, { token });

  const quickSearchInput = page.getByPlaceholder('Quick search by name...');
  await quickSearchInput.fill(tokenPrefix);
  await quickSearchInput.press('Enter');

  await expect(page.getByText('Showing 2 of 2 results')).toBeVisible({ timeout: 60_000 });

  const unsupportedChip = page.getByRole('button', { name: /Unsupported \(\d+\)/i }).first();
  await expect(unsupportedChip).toBeVisible();

  const filteredResponse = page.waitForResponse((response) => (
    response.request().method() === 'GET'
    && response.url().includes('/api/v1/search')
    && response.url().includes('previewStatus=UNSUPPORTED')
    && response.status() === 200
  ));
  await unsupportedChip.click();
  await filteredResponse;

  await expect(page.getByText('Showing 1 of 1 results')).toBeVisible({ timeout: 60_000 });
  await expect(page.locator('.MuiCard-root').filter({ hasText: binFilename }).first()).toBeVisible();
  await expect(page.locator('.MuiCard-root').filter({ hasText: textFilename })).toHaveCount(0);

  await request.delete(`${baseApiUrl}/api/v1/nodes/${folderId}`,
    { headers: { Authorization: `Bearer ${token}` } }).catch(() => null);
});

test('Advanced search keeps unsupported filter and hides retry actions for unsupported previews', async ({ page, request }) => {
  test.setTimeout(240_000);
  await waitForApiReady(request, { apiUrl: baseApiUrl });
  const token = await fetchAccessToken(request, defaultUsername, defaultPassword);
  const rootId = await getRootFolderId(request, token, { apiUrl: baseApiUrl });
  const documentsId = await findChildFolderId(request, rootId, 'Documents', token, { apiUrl: baseApiUrl });

  const folderName = `e2e-advanced-preview-failure-${Date.now()}`;
  const folderId = await createFolder(request, documentsId, folderName, token);
  const queryKey = randomLetters(12);
  const filename = `${queryKey}.bin`;
  const documentId = await uploadBinaryFile(request, folderId, filename, token);

  const previewRes = await request.get(`${baseApiUrl}/api/v1/documents/${documentId}/preview`, {
    headers: { Authorization: `Bearer ${token}` },
  });
  expect(previewRes.ok()).toBeTruthy();
  const previewJson = (await previewRes.json()) as { supported?: boolean; status?: string; failureCategory?: string };
  expect(previewJson.supported).toBe(false);
  expect(previewJson.failureCategory).toBe('UNSUPPORTED');
  expect(previewJson.status).toBe('UNSUPPORTED');

  const indexRes = await request.post(`${baseApiUrl}/api/v1/search/index/${documentId}`,
    { headers: { Authorization: `Bearer ${token}` } });
  expect(indexRes.ok()).toBeTruthy();
  await waitForSearchIndex(request, filename, token, { apiUrl: baseApiUrl, maxAttempts: 40 });

  const advancedSearchPath = `/search?q=${encodeURIComponent(queryKey)}&dateRange=week&minSize=1&previewStatus=UNSUPPORTED`;
  await gotoWithAuthE2E(page, advancedSearchPath, defaultUsername, defaultPassword, { token });

  const advancedSearchInput = page.getByLabel('Search query');
  await expect(advancedSearchInput).toHaveValue(queryKey, { timeout: 60_000 });
  await expect(page.getByLabel('Min size')).toHaveValue('1');
  await expect.poll(() => new URL(page.url()).searchParams.get('dateRange')).toBe('week');
  await expect.poll(() => new URL(page.url()).searchParams.get('minSize')).toBe('1');
  await expect.poll(() => new URL(page.url()).searchParams.get('previewStatus')).toBe('UNSUPPORTED');

  const resultCard = page.locator('.MuiPaper-root').filter({ hasText: filename }).first();
  if (await resultCard.count()) {
    await expect(resultCard).toBeVisible({ timeout: 60_000 });
    await expect(resultCard.getByText(/Preview unsupported/i)).toBeVisible();
  } else {
    await expect(page.getByText('No results found.')).toBeVisible({ timeout: 60_000 });
  }
  const previewStatusPanel = page.locator('.MuiPaper-root').filter({ hasText: 'Preview Status' }).first();
  if (await previewStatusPanel.count()) {
    await expect(previewStatusPanel).toBeVisible();
    const unsupportedStatusChip = previewStatusPanel.getByRole('button', { name: /Unsupported \(\d+\)/i }).first();
    await expect(unsupportedStatusChip).toBeVisible();
    await expect(unsupportedStatusChip).toHaveClass(/MuiChip-filled/);
  }
  await expect.poll(() => new URL(page.url()).searchParams.get('previewStatus')).toBe('UNSUPPORTED');
  await expect.poll(() => new URL(page.url()).searchParams.get('q')).toBe(queryKey);

  await page.reload({ waitUntil: 'domcontentloaded' });
  await expect(page.getByLabel('Search query')).toHaveValue(queryKey, { timeout: 30_000 });
  await expect(page.getByLabel('Min size')).toHaveValue('1');
  await expect.poll(() => new URL(page.url()).searchParams.get('previewStatus')).toBe('UNSUPPORTED');
  await expect.poll(() => new URL(page.url()).searchParams.get('dateRange')).toBe('week');
  await expect.poll(() => new URL(page.url()).searchParams.get('minSize')).toBe('1');
  const reloadedPreviewStatusPanel = page.locator('.MuiPaper-root').filter({ hasText: 'Preview Status' }).first();
  if (await reloadedPreviewStatusPanel.count()) {
    const reloadedUnsupportedChip = reloadedPreviewStatusPanel.getByRole('button', { name: /Unsupported \(\d+\)/i }).first();
    await expect(reloadedUnsupportedChip).toHaveClass(/MuiChip-filled/);
  } else {
    await expect(page.getByText('No results found.')).toBeVisible({ timeout: 30_000 });
  }

  await expect(page.getByRole('button', { name: /Retry failed previews/i })).toHaveCount(0);
  await expect(page.getByRole('button', { name: /Retry \".+\" \(\d+\)/i })).toHaveCount(0);
  if (await resultCard.count()) {
    await expect(resultCard.getByRole('button', { name: /Retry preview/i })).toHaveCount(0);
  }

  await request.delete(`${baseApiUrl}/api/v1/nodes/${folderId}`,
    { headers: { Authorization: `Bearer ${token}` } }).catch(() => null);
});
