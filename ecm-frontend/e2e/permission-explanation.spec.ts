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

async function setPermission(
  request: APIRequestContext,
  nodeId: string,
  params: { authority: string; authorityType: 'USER' | 'GROUP' | 'ROLE' | 'EVERYONE'; permissionType: string; allowed: boolean },
  token: string,
) {
  const res = await request.post(`${baseApiUrl}/api/v1/security/nodes/${nodeId}/permissions`, {
    headers: { Authorization: `Bearer ${token}` },
    params,
  });
  expect(res.ok()).toBeTruthy();
}

test('Permission diagnostics explain matched grants with inheritance markers', async ({ page, request }) => {
  test.setTimeout(240_000);
  await waitForApiReady(request, { apiUrl: baseApiUrl });

  const token = await fetchAccessToken(request, defaultUsername, defaultPassword);
  const rootId = await getRootFolderId(request, token, { apiUrl: baseApiUrl });
  const documentsId = await findChildFolderId(request, rootId, 'Documents', token, { apiUrl: baseApiUrl });

  const parentName = `e2e-perm-explain-parent-${Date.now()}`;
  const parentId = await createFolder(request, documentsId, parentName, token);
  const childName = `e2e-perm-explain-child-${Date.now()}`;
  await createFolder(request, parentId, childName, token);

  // Grant viewer read on parent; child inherits.
  await setPermission(
    request,
    parentId,
    { authority: 'viewer', authorityType: 'USER', permissionType: 'READ', allowed: true },
    token,
  );

  await gotoWithAuthE2E(page, `/browse/${parentId}`, defaultUsername, defaultPassword, { token });
  const actionButton = page.getByLabel(`Actions for ${childName}`).first();

  let actionButtonVisible = false;
  for (let attempt = 0; attempt < 12; attempt += 1) {
    await page.getByRole('button', { name: 'list view' }).click().catch(() => undefined);

    const quickSearch = page.getByPlaceholder('Quick search by name...');
    if (await quickSearch.isVisible().catch(() => false)) {
      await quickSearch.fill(childName);
      await quickSearch.press('Enter');
    }

    if (await actionButton.isVisible().catch(() => false)) {
      actionButtonVisible = true;
      break;
    }

    await page.waitForTimeout(1500);
    if ((attempt + 1) % 3 === 0) {
      await page.reload({ waitUntil: 'domcontentloaded' });
    }
  }
  expect(actionButtonVisible).toBeTruthy();

  await actionButton.click();
  await page.getByRole('menuitem', { name: 'Permissions' }).click();

  const dialog = page.getByRole('dialog').filter({ hasText: 'Manage Permissions' });
  await expect(dialog).toBeVisible({ timeout: 60_000 });

  // Switch diagnostics to evaluate as viewer.
  const diagnoseInput = dialog.getByLabel(/Diagnose as/i);
  await expect(diagnoseInput).toBeVisible({ timeout: 30_000 });
  await diagnoseInput.fill('viewer');
  await diagnoseInput.press('Enter');

  // Wait for the diagnostics to refresh to the new user context.
  await expect(dialog.getByText(/User viewer/i)).toBeVisible({ timeout: 60_000 });
  await expect(dialog.getByText(/Reason ACL_(ALLOW|DENY)/i)).toBeVisible({ timeout: 60_000 });

  // New explain panel should show an inherited matched grant for viewer.
  await expect(dialog.getByText(/Matched grants/i)).toBeVisible({ timeout: 60_000 });
  await expect(dialog.getByRole('table', { name: /Matched permission grants/i })).toBeVisible({ timeout: 60_000 });
  await expect(dialog.getByText(/Inherited/i)).toBeVisible({ timeout: 60_000 });

  await dialog.getByRole('button', { name: /^Close$/ }).click();

  await request.delete(`${baseApiUrl}/api/v1/nodes/${parentId}`,
    { headers: { Authorization: `Bearer ${token}` } }).catch(() => null);
});

