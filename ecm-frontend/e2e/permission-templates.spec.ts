import { APIRequestContext, expect, test } from '@playwright/test';
import { promises as fs } from 'fs';
import { fetchAccessToken, getRootFolderId, resolveApiUrl, waitForApiReady } from './helpers/api';
import { gotoWithAuthE2E } from './helpers/login';

const baseApiUrl = resolveApiUrl();
const defaultUsername = process.env.ECM_E2E_USERNAME || 'admin';
const defaultPassword = process.env.ECM_E2E_PASSWORD || 'admin';
const viewerUsername = process.env.ECM_E2E_VIEWER_USERNAME || 'viewer';

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

async function createPermissionTemplate(
  request: APIRequestContext,
  token: string,
  name: string,
  authority: string,
) {
  const res = await request.post(`${baseApiUrl}/api/v1/security/permission-templates`, {
    headers: { Authorization: `Bearer ${token}`, 'Content-Type': 'application/json' },
    data: {
      name,
      description: 'E2E permission template',
      entries: [
        {
          authority,
          authorityType: 'USER',
          permissionSet: 'CONSUMER',
        },
      ],
    },
  });
  expect(res.ok()).toBeTruthy();
  const payload = (await res.json()) as { id?: string };
  if (!payload.id) {
    throw new Error('Template creation did not return id');
  }
  return payload.id;
}

async function updatePermissionTemplate(
  request: APIRequestContext,
  token: string,
  templateId: string,
  description: string,
  authority: string,
  permissionSet: string,
) {
  const res = await request.put(`${baseApiUrl}/api/v1/security/permission-templates/${templateId}`, {
    headers: { Authorization: `Bearer ${token}`, 'Content-Type': 'application/json' },
    data: {
      description,
      entries: [
        {
          authority,
          authorityType: 'USER',
          permissionSet,
        },
      ],
    },
  });
  expect(res.ok()).toBeTruthy();
}

test('Admin can apply permission template from permissions dialog', async ({ page, request }) => {
  test.setTimeout(240_000);

  await waitForApiReady(request, { apiUrl: baseApiUrl });

  const token = await fetchAccessToken(request, defaultUsername, defaultPassword);
  const rootId = await getRootFolderId(request, token, { apiUrl: baseApiUrl });
  const parentName = `e2e-template-parent-${Date.now()}`;
  const parentId = await createFolder(request, rootId, parentName, token);
  const folderName = `e2e-template-child-${Date.now()}`;
  const folderId = await createFolder(request, parentId, folderName, token);

  const templateName = `e2e-template-${Date.now()}`;
  const templateId = await createPermissionTemplate(request, token, templateName, viewerUsername);

  await gotoWithAuthE2E(page, `/browse/${parentId}`, defaultUsername, defaultPassword, { token });
  await page.getByRole('button', { name: 'list view' }).click();

  const row = page.getByRole('row', { name: new RegExp(folderName) });
  await expect(row).toBeVisible({ timeout: 60_000 });
  await row.getByRole('button', { name: `Actions for ${folderName}` }).click();
  await page.getByRole('menuitem', { name: 'Permissions' }).click();

  const dialog = page.getByRole('dialog').filter({ hasText: 'Manage Permissions' });
  await expect(dialog).toBeVisible({ timeout: 60_000 });

  const templateSelect = dialog.getByLabel('Template');
  await templateSelect.click();
  await page.getByRole('option', { name: new RegExp(templateName) }).click();
  await dialog.getByRole('button', { name: 'Apply Template' }).click();

  const viewerRow = dialog.getByRole('row', { name: new RegExp(viewerUsername) });
  await expect(viewerRow).toBeVisible({ timeout: 60_000 });

  await dialog.getByRole('button', { name: /^Close$/ }).click();

  await request.delete(`${baseApiUrl}/api/v1/security/permission-templates/${templateId}`, {
    headers: { Authorization: `Bearer ${token}` },
  }).catch(() => null);

  await request.delete(`${baseApiUrl}/api/v1/nodes/${parentId}`, {
    headers: { Authorization: `Bearer ${token}` },
  }).catch(() => null);
});

test('Admin can view permission template history', async ({ page, request }) => {
  test.setTimeout(180_000);

  await waitForApiReady(request, { apiUrl: baseApiUrl });

  const token = await fetchAccessToken(request, defaultUsername, defaultPassword);
  const templateName = `e2e-template-history-${Date.now()}`;
  const templateId = await createPermissionTemplate(request, token, templateName, defaultUsername);
  await updatePermissionTemplate(request, token, templateId, 'Updated description', viewerUsername, 'EDITOR');
  const versionRes = await request.get(`${baseApiUrl}/api/v1/security/permission-templates/${templateId}/versions`, {
    headers: { Authorization: `Bearer ${token}` },
  });
  expect(versionRes.ok()).toBeTruthy();
  const versions = (await versionRes.json()) as Array<{ id: string }>;
  const latestVersionId = versions[0]?.id;
  if (!latestVersionId) {
    throw new Error('No permission template versions returned');
  }

  await gotoWithAuthE2E(page, '/admin/permission-templates', defaultUsername, defaultPassword, { token });

  const row = page.getByRole('row', { name: new RegExp(templateName) });
  await expect(row).toBeVisible({ timeout: 60_000 });
  await page.getByTestId(`permission-template-history-${templateId}`).click();

  const dialog = page.getByRole('dialog').filter({ hasText: 'Template History' });
  await expect(dialog).toBeVisible({ timeout: 30_000 });

  const historyRows = dialog.locator('tbody tr');
  await expect(historyRows).toHaveCount(2, { timeout: 30_000 });

  await dialog.getByTestId(`permission-template-compare-${latestVersionId}`).click();
  const compareDialog = page.getByRole('dialog').filter({ hasText: 'Template Version Comparison' });
  await expect(compareDialog).toBeVisible({ timeout: 30_000 });
  await expect(compareDialog.getByText(/Change Summary/i)).toBeVisible();
  const [download] = await Promise.all([
    page.waitForEvent('download', { timeout: 30_000 }),
    compareDialog.getByRole('button', { name: 'Export CSV' }).click(),
  ]);
  expect(download.suggestedFilename()).toMatch(/-diff-.*\.csv$/);

  const [jsonDownload] = await Promise.all([
    page.waitForEvent('download', { timeout: 30_000 }),
    compareDialog.getByRole('button', { name: 'Export JSON' }).click(),
  ]);
  expect(jsonDownload.suggestedFilename()).toMatch(/-diff-.*\.json$/);
  const jsonPath = await jsonDownload.path();
  if (!jsonPath) {
    throw new Error('JSON download path is unavailable');
  }
  const jsonRaw = await fs.readFile(jsonPath, 'utf-8');
  const payload = JSON.parse(jsonRaw) as Record<string, unknown>;
  expect(Array.isArray(payload.added)).toBeTruthy();
  expect(Array.isArray(payload.removed)).toBeTruthy();
  expect(Array.isArray(payload.changed)).toBeTruthy();

  await compareDialog.getByRole('button', { name: 'Close' }).click();

  await request.delete(`${baseApiUrl}/api/v1/security/permission-templates/${templateId}`, {
    headers: { Authorization: `Bearer ${token}` },
  }).catch(() => null);
});
