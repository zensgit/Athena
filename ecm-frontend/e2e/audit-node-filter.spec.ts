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

test.beforeEach(async ({ request }) => {
  await waitForApiReady(request, { apiUrl: baseApiUrl });
});

async function createFolder(
  request: APIRequestContext,
  parentId: string,
  name: string,
  token: string,
) {
  const res = await request.post(`${baseApiUrl}/api/v1/folders`, {
    headers: { Authorization: `Bearer ${token}` },
    data: { name, parentId },
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
  content: string,
  token: string,
) {
  const res = await request.post(`${baseApiUrl}/api/v1/documents/upload?folderId=${folderId}`, {
    headers: { Authorization: `Bearer ${token}` },
    multipart: {
      file: {
        name: filename,
        mimeType: 'text/plain',
        buffer: Buffer.from(content),
      },
    },
    timeout: 60_000,
  });
  expect(res.ok()).toBeTruthy();
  const payload = (await res.json()) as { documentId?: string; id?: string };
  const documentId = payload.documentId ?? payload.id;
  if (!documentId) {
    throw new Error('Upload did not return document id');
  }
  return documentId;
}

async function waitForAuditLog(
  request: APIRequestContext,
  nodeId: string,
  token: string,
) {
  const deadline = Date.now() + 60_000;
  let lastError: string | undefined;

  while (Date.now() < deadline) {
    try {
      const res = await request.get(`${baseApiUrl}/api/v1/analytics/audit/search`, {
        headers: { Authorization: `Bearer ${token}` },
        params: { nodeId, page: 0, size: 5 },
        timeout: 30_000,
      });
      if (res.ok()) {
        const payload = (await res.json()) as { content?: Array<{ id: string }> };
        if ((payload.content ?? []).length > 0) {
          return;
        }
        lastError = 'empty result';
      } else {
        lastError = `status=${res.status()}`;
      }
    } catch (error) {
      lastError = error instanceof Error ? error.message : String(error);
    }

    await new Promise((resolve) => setTimeout(resolve, 2000));
  }

  throw new Error(`Audit log not found for node ${nodeId}: ${lastError ?? 'unknown error'}`);
}

test('View Audit deep-link filters audit logs by nodeId', async ({ page, request }) => {
  test.setTimeout(240_000);

  const token = await fetchAccessToken(request, defaultUsername, defaultPassword);
  const rootId = await getRootFolderId(request, token, { apiUrl: baseApiUrl });
  const documentsId = await findChildFolderId(request, rootId, 'Documents', token, { apiUrl: baseApiUrl });

  const folderName = `e2e-audit-node-${Date.now()}`;
  const folderId = await createFolder(request, documentsId, folderName, token);

  const filename = `e2e-audit-node-${Date.now()}.txt`;
  const documentId = await uploadTextFile(request, folderId, filename, `Audit e2e ${new Date().toISOString()}\n`, token);

  await waitForAuditLog(request, documentId, token);

  await gotoWithAuthE2E(page, `/browse/${folderId}`, defaultUsername, defaultPassword, { token });

  const row = page.getByRole('row', { name: new RegExp(filename) });
  await expect(row).toBeVisible({ timeout: 60_000 });
  await row.getByRole('button', { name: `Actions for ${filename}` }).click();
  await page.getByRole('menuitem', { name: 'View Audit' }).click();

  await page.waitForURL(new RegExp(`/admin\\?auditNodeId=${documentId}`), { timeout: 60_000 });

  const nodeIdInput = page.getByLabel('Node ID');
  await expect(nodeIdInput).toHaveValue(documentId, { timeout: 60_000 });

  await expect(page.getByText(filename).first()).toBeVisible({ timeout: 60_000 });

  await request.delete(`${baseApiUrl}/api/v1/nodes/${folderId}`, {
    headers: { Authorization: `Bearer ${token}` },
  }).catch(() => null);
});

