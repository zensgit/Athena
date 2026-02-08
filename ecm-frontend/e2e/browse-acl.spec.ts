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
          buffer: Buffer.from(`Browse ACL e2e ${new Date().toISOString()}\n`),
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

test('Browse view hides unauthorized documents for viewer', async ({ page, request }) => {
  test.setTimeout(240_000);

  const apiToken = await fetchAccessToken(request, defaultUsername, defaultPassword);
  const rootId = await getRootFolderId(request, apiToken, { apiUrl: baseApiUrl });
  const documentsId = await findChildFolderId(request, rootId, 'Documents', apiToken, { apiUrl: baseApiUrl });

  const folderName = `e2e-browse-acl-${Date.now()}`;
  const folderId = await createFolder(request, documentsId, folderName, apiToken);

  const filename = `e2e-browse-${Date.now()}.txt`;
  const documentId = await uploadDocument(request, folderId, filename, apiToken);

  await setPermission(request, folderId, apiToken, {
    authority: 'EVERYONE',
    authorityType: 'EVERYONE',
    permissionType: 'READ',
    allowed: true,
  });

  await setPermission(request, documentId, apiToken, {
    authority: 'EVERYONE',
    authorityType: 'EVERYONE',
    permissionType: 'READ',
    allowed: false,
  });

  const viewerToken = await fetchAccessToken(request, viewerUsername, viewerPassword);
  await gotoWithAuthE2E(page, `/browse/${folderId}`, viewerUsername, viewerPassword, { token: viewerToken });

  await page.getByRole('button', { name: 'list view' }).click();

  await expect(page.getByText('This folder is empty')).toBeVisible({ timeout: 60_000 });
  await expect(page.getByText(filename)).toHaveCount(0);

  await request.delete(`${baseApiUrl}/api/v1/nodes/${folderId}`, {
    headers: { Authorization: `Bearer ${apiToken}` },
  }).catch(() => null);
});
