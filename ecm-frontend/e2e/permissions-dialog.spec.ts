import { APIRequestContext, expect, test } from '@playwright/test';
import { fetchAccessToken, findChildFolderId, getRootFolderId, waitForApiReady } from './helpers/api';
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

test('Permissions dialog shows inheritance path and copy ACL action', async ({ page, request }) => {
  test.setTimeout(240_000);
  await waitForApiReady(request, { apiUrl: baseApiUrl });

  const token = await fetchAccessToken(request, defaultUsername, defaultPassword);
  const rootId = await getRootFolderId(request, token, { apiUrl: baseApiUrl });
  const documentsId = await findChildFolderId(request, rootId, 'Documents', token, { apiUrl: baseApiUrl });

  const parentName = `e2e-permissions-parent-${Date.now()}`;
  const parentId = await createFolder(request, documentsId, parentName, token);
  const folderName = `e2e-permissions-${Date.now()}`;
  await createFolder(request, parentId, folderName, token);

  await gotoWithAuthE2E(page, `/browse/${parentId}`, defaultUsername, defaultPassword, { token });
  const actionButton = page.getByLabel(`Actions for ${folderName}`).first();

  let actionButtonVisible = false;
  for (let attempt = 0; attempt < 12; attempt += 1) {
    await page.getByRole('button', { name: 'list view' }).click().catch(() => undefined);

    const quickSearch = page.getByPlaceholder('Quick search by name...');
    if (await quickSearch.isVisible().catch(() => false)) {
      await quickSearch.fill(folderName);
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
  await expect(dialog.getByText('Inheritance path')).toBeVisible();
  await expect(dialog.getByText(/Explicit denies override/i)).toBeVisible();
  await expect(dialog.getByText(/Permission diagnostics/i)).toBeVisible();
  await expect(dialog.getByLabel(/Diagnose as/i)).toBeVisible();
  await expect(dialog.getByText(/Reason ADMIN/i)).toBeVisible();
  await expect(dialog.getByRole('button', { name: 'Copy ACL' })).toBeVisible();
  await dialog.getByRole('button', { name: 'Copy ACL' }).click();
  const toast = page.locator('.Toastify__toast').last();
  await expect(toast).toContainText(/copied|copy/i, { timeout: 30_000 });

  await dialog.getByRole('button', { name: /^Close$/ }).click();

  await request.delete(`${baseApiUrl}/api/v1/nodes/${parentId}`,
    { headers: { Authorization: `Bearer ${token}` } }).catch(() => null);
});
