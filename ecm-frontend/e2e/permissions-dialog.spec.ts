import { APIRequestContext, expect, Page, test } from '@playwright/test';
import { fetchAccessToken, findChildFolderId, getRootFolderId, waitForApiReady } from './helpers/api';
import { loginWithCredentialsE2E } from './helpers/login';

const baseApiUrl = process.env.ECM_API_URL || 'http://localhost:7700';
const baseUiUrl = process.env.ECM_UI_URL || 'http://localhost:5500';
const defaultUsername = process.env.ECM_E2E_USERNAME || 'admin';
const defaultPassword = process.env.ECM_E2E_PASSWORD || 'admin';

async function loginWithCredentials(page: Page, username: string, password: string, token?: string) {
  await loginWithCredentialsE2E(page, username, password, { token });
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

test('Permissions dialog shows inheritance path and copy ACL action', async ({ page, request }) => {
  test.setTimeout(240_000);
  await waitForApiReady(request, { apiUrl: baseApiUrl });

  const token = await fetchAccessToken(request, defaultUsername, defaultPassword);
  const rootId = await getRootFolderId(request, token, { apiUrl: baseApiUrl });
  const documentsId = await findChildFolderId(request, rootId, 'Documents', token, { apiUrl: baseApiUrl });

  const folderName = `e2e-permissions-${Date.now()}`;
  const folderId = await createFolder(request, documentsId, folderName, token);

  await loginWithCredentials(page, defaultUsername, defaultPassword, token);
  await page.goto(`${baseUiUrl}/browse/${documentsId}`, { waitUntil: 'domcontentloaded' });
  await page.getByRole('button', { name: 'list view' }).click();

  const quickSearch = page.getByPlaceholder('Quick search by name...');
  if (await quickSearch.isVisible().catch(() => false)) {
    await quickSearch.fill(folderName);
    await quickSearch.press('Enter');
    await page.waitForTimeout(1000);
  }

  const createdRow = page.getByRole('row', { name: new RegExp(folderName) });
  if (!(await createdRow.isVisible().catch(() => false))) {
    await page.reload({ waitUntil: 'domcontentloaded' });
    await page.getByRole('button', { name: 'list view' }).click().catch(() => undefined);
    if (await quickSearch.isVisible().catch(() => false)) {
      await quickSearch.fill(folderName);
      await quickSearch.press('Enter');
      await page.waitForTimeout(1000);
    }
  }
  const fallbackRow = page.getByRole('row', { name: /e2e-permissions-\d+/ }).first();
  let targetRow = createdRow;
  if (!(await createdRow.isVisible().catch(() => false))) {
    targetRow = fallbackRow;
  }
  await expect(targetRow).toBeVisible({ timeout: 60_000 });

  await targetRow.getByRole('button', { name: /Actions for/i }).click();
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

  await request.delete(`${baseApiUrl}/api/v1/nodes/${folderId}`,
    { headers: { Authorization: `Bearer ${token}` } }).catch(() => null);
});
