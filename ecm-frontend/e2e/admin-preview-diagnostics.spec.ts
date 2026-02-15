import { APIRequestContext, expect, test } from '@playwright/test';
import {
  fetchAccessToken,
  findChildFolderId,
  getRootFolderId,
  resolveApiUrl,
  waitForApiReady,
} from './helpers/api';
import { gotoWithAuthE2E } from './helpers/login';

const baseApiUrl = resolveApiUrl();
const defaultUsername = process.env.ECM_E2E_USERNAME || 'admin';
const defaultPassword = process.env.ECM_E2E_PASSWORD || 'admin';

function randomLetters(length: number) {
  let out = '';
  while (out.length < length) {
    out += Math.random().toString(36).replace(/[^a-z]+/g, '');
  }
  return out.slice(0, length);
}

async function createFolder(
  request: APIRequestContext,
  parentId: string,
  name: string,
  token: string
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

async function uploadBinaryFile(
  request: APIRequestContext,
  folderId: string,
  filename: string,
  token: string
) {
  const uploadRes = await request.post(`${baseApiUrl}/api/v1/documents/upload?folderId=${folderId}`, {
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

test('Admin preview diagnostics lists recent failures and supports filtering', async ({ page, request }) => {
  test.setTimeout(240_000);
  await waitForApiReady(request, { apiUrl: baseApiUrl });

  const token = await fetchAccessToken(request, defaultUsername, defaultPassword);
  const rootId = await getRootFolderId(request, token, { apiUrl: baseApiUrl });
  const documentsId = await findChildFolderId(request, rootId, 'Documents', token, { apiUrl: baseApiUrl });

  const folderName = `e2e-preview-diagnostics-${Date.now()}`;
  const folderId = await createFolder(request, documentsId, folderName, token);
  const filename = `e2e-preview-diagnostics-${Date.now()}-${randomLetters(8)}.bin`;
  const documentId = await uploadBinaryFile(request, folderId, filename, token);

  const previewRes = await request.get(`${baseApiUrl}/api/v1/documents/${documentId}/preview`, {
    headers: { Authorization: `Bearer ${token}` },
  });
  expect(previewRes.ok()).toBeTruthy();
  const previewJson = (await previewRes.json()) as { supported?: boolean; status?: string; failureCategory?: string };
  expect(previewJson.supported).toBe(false);
  expect(previewJson.status).toBe('UNSUPPORTED');
  expect(previewJson.failureCategory).toBe('UNSUPPORTED');

  await expect.poll(async () => {
    const diagRes = await request.get(`${baseApiUrl}/api/v1/preview/diagnostics/failures?limit=200`, {
      headers: { Authorization: `Bearer ${token}` },
    });
    if (!diagRes.ok()) {
      return false;
    }
    const payload = (await diagRes.json()) as Array<{ id?: string; name?: string }>;
    return payload.some((item) => item.id === documentId || item.name === filename);
  }, { timeout: 60_000, intervals: [500, 1000, 2000] }).toBeTruthy();

  await gotoWithAuthE2E(page, '/admin/preview-diagnostics', defaultUsername, defaultPassword, { token });
  await expect(page.getByRole('heading', { name: 'Preview Diagnostics' })).toBeVisible({ timeout: 60_000 });

  const filter = page.getByPlaceholder('Filter by name, path, mime type...');
  await filter.fill(filename);
  await expect(page.getByText(filename)).toBeVisible({ timeout: 60_000 });

  await request.delete(`${baseApiUrl}/api/v1/nodes/${folderId}`, {
    headers: { Authorization: `Bearer ${token}` },
  }).catch(() => null);
});

