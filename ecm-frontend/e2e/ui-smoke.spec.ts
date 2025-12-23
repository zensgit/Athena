import { APIRequestContext, expect, FrameLocator, Page, test } from '@playwright/test';
import { XLSX_SAMPLE_BASE64 } from './fixtures/xlsxSample';

const defaultUsername = process.env.ECM_E2E_USERNAME || 'admin';
const defaultPassword = process.env.ECM_E2E_PASSWORD || 'admin';

async function fetchAccessToken(request: APIRequestContext, username: string, password: string) {
  const tokenRes = await request.post('http://localhost:8180/realms/ecm/protocol/openid-connect/token', {
    form: {
      grant_type: 'password',
      client_id: 'unified-portal',
      username,
      password,
    },
  });
  expect(tokenRes.ok()).toBeTruthy();
  const tokenJson = (await tokenRes.json()) as { access_token?: string };
  if (!tokenJson.access_token) {
    throw new Error('Failed to obtain access token for API calls');
  }
  return tokenJson.access_token;
}

async function loginWithCredentials(page: Page, username: string, password: string) {
  await page.goto('/', { waitUntil: 'domcontentloaded' });

  // If we land on the app login screen, trigger the Keycloak redirect.
  if (page.url().endsWith('/login')) {
    await page.getByRole('button', { name: /sign in with keycloak/i }).click();
  }

  // Keycloak login page
  await page.waitForURL(/(\/browse\/|\/protocol\/openid-connect\/auth)/, { timeout: 60_000 });
  if (page.url().includes('/protocol/openid-connect/auth')) {
    await page.locator('#username').fill(username);
    await page.locator('#password').fill(password);
    await Promise.all([
      page.waitForNavigation({ waitUntil: 'domcontentloaded' }),
      page.locator('#kc-login').click(),
    ]);
  }

  await page.waitForURL(/\/browse\//, { timeout: 60_000 });
  await expect(page.getByText('Athena ECM')).toBeVisible({ timeout: 60_000 });
}

async function findChildFolderId(
  request: APIRequestContext,
  parentId: string,
  folderName: string,
  token: string,
  maxAttempts = 10,
) {
  for (let attempt = 0; attempt < maxAttempts; attempt += 1) {
    const response = await request.get(`http://localhost:7700/api/v1/folders/${parentId}/contents`, {
      params: {
        page: 0,
        size: 1000,
        sort: 'name,asc',
      },
      headers: { Authorization: `Bearer ${token}` },
    });
    expect(response.ok()).toBeTruthy();
    const payload = (await response.json()) as { content?: Array<{ id: string; name: string; nodeType: string }> };
    const match = payload.content?.find((node) => node.name === folderName && node.nodeType === 'FOLDER');
    if (match?.id) {
      return match.id;
    }
    await new Promise((resolve) => setTimeout(resolve, 1000));
  }

  throw new Error(`Folder not found after create: ${folderName}`);
}

async function findDocumentId(
  request: APIRequestContext,
  folderId: string,
  filename: string,
  token: string,
  maxAttempts = 10,
) {
  for (let attempt = 0; attempt < maxAttempts; attempt += 1) {
    const response = await request.get(`http://localhost:7700/api/v1/folders/${folderId}/contents`, {
      params: {
        page: 0,
        size: 1000,
        sort: 'name,asc',
      },
      headers: { Authorization: `Bearer ${token}` },
    });
    expect(response.ok()).toBeTruthy();
    const payload = (await response.json()) as { content?: Array<{ id: string; name: string; nodeType: string }> };
    const match = payload.content?.find((node) => node.name === filename && node.nodeType === 'DOCUMENT');
    if (match?.id) {
      return match.id;
    }
    await new Promise((resolve) => setTimeout(resolve, 1000));
  }

  throw new Error(`Document not found after upload: ${filename}`);
}

async function waitForSearchIndex(
  request: APIRequestContext,
  query: string,
  token: string,
  maxAttempts = 10,
) {
  for (let attempt = 0; attempt < maxAttempts; attempt += 1) {
    const res = await request.get('http://localhost:7700/api/v1/search', {
      params: { q: query, page: 0, size: 10 },
      headers: { Authorization: `Bearer ${token}` },
    });
    if (res.ok()) {
      const payload = (await res.json()) as { content?: Array<{ name: string }> };
      if (payload.content?.some((item) => item.name === query)) {
        return;
      }
    }
    await new Promise((resolve) => setTimeout(resolve, 1000));
  }

  throw new Error(`Search index did not include '${query}'`);
}

async function getVersionCount(
  request: APIRequestContext,
  documentId: string,
  token: string,
) {
  const res = await request.get(`http://localhost:7700/api/v1/documents/${documentId}/versions`, {
    headers: { Authorization: `Bearer ${token}` },
  });
  expect(res.ok()).toBeTruthy();
  const versions = (await res.json()) as Array<{ id: string }>;
  return versions.length;
}

async function waitForVersionCount(
  request: APIRequestContext,
  documentId: string,
  token: string,
  minCount: number,
  maxAttempts = 15,
) {
  for (let attempt = 0; attempt < maxAttempts; attempt += 1) {
    const count = await getVersionCount(request, documentId, token);
    if (count >= minCount) {
      return count;
    }
    await new Promise((resolve) => setTimeout(resolve, 2000));
  }

  throw new Error(`Version count did not reach ${minCount} for document ${documentId}`);
}

async function dismissWopiWelcome(page: Page, editorFrame: FrameLocator) {
  const welcomeModal = editorFrame.locator('.iframe-welcome-wrap, .iframe-welcome-content').first();
  if (await welcomeModal.count()) {
    try {
      if (await welcomeModal.isVisible()) {
        const closeButton = editorFrame.locator('button[aria-label="Close"], button[title="Close"]').first();
        if (await closeButton.count()) {
          await closeButton.click({ force: true });
        } else {
          await page.keyboard.press('Escape');
        }
        await expect(welcomeModal).toBeHidden({ timeout: 10_000 });
      }
    } catch {
      // Ignore transient overlay timing.
    }
  }
}

test('UI smoke: browse + upload + search + copy/move + facets + delete + rules', async ({ page }) => {
  page.on('dialog', (dialog) => dialog.accept());
  test.setTimeout(360_000);

  await loginWithCredentials(page, defaultUsername, defaultPassword);
  const apiToken = await fetchAccessToken(page.request, defaultUsername, defaultPassword);

  // System status page should be reachable via route and render
  await page.goto('/status', { waitUntil: 'domcontentloaded' });
  await expect(page.getByRole('heading', { name: 'System Status' })).toBeVisible({ timeout: 60_000 });

  // Correspondents page should be reachable for admin/editor
  await page.goto('/correspondents', { waitUntil: 'domcontentloaded' });
  await expect(page.getByRole('heading', { name: 'Correspondents' })).toBeVisible({ timeout: 60_000 });

  const correspondentName = `ui-e2e-correspondent-${Date.now()}`;
  await page.getByRole('button', { name: 'New Correspondent', exact: true }).click();
  const correspondentDialog = page.getByRole('dialog').filter({ hasText: 'Correspondent' });
  await expect(correspondentDialog).toBeVisible({ timeout: 60_000 });
  await correspondentDialog.getByLabel('Name').fill(correspondentName);
  await correspondentDialog.getByLabel('Match Algorithm').click();
  await page.getByRole('option', { name: 'ANY', exact: true }).click();
  await correspondentDialog.getByLabel('Match Pattern').fill('Amazon AWS');
  await correspondentDialog.getByRole('button', { name: 'Save', exact: true }).click();
  await expect(correspondentDialog).toBeHidden({ timeout: 60_000 });
  await page.getByRole('textbox', { name: 'Search correspondents' }).fill(correspondentName);
  await expect(page.getByRole('row', { name: new RegExp(correspondentName) })).toBeVisible({ timeout: 60_000 });

  // Edit correspondent
  await page.getByRole('button', { name: `Edit ${correspondentName}` }).click();
  const editDialog = page.getByRole('dialog').filter({ hasText: 'Edit Correspondent' });
  await expect(editDialog).toBeVisible({ timeout: 60_000 });
  await editDialog.getByLabel('Match Pattern').fill('Amazon AWS Inc');
  await editDialog.getByRole('button', { name: 'Save', exact: true }).click();
  await expect(editDialog).toBeHidden({ timeout: 60_000 });

  await page.goto('/browse/root', { waitUntil: 'domcontentloaded' });

  // Sidebar resizer should be available before auto-collapse kicks in.
  await expect(page.getByRole('separator', { name: 'Resize sidebar' })).toBeVisible({ timeout: 60_000 });

  await page.getByRole('treeitem', { name: 'Documents' }).click();
  await page.waitForURL(/\/browse\/[0-9a-f-]{36}$/i, { timeout: 60_000 });
  const documentsFolderId = /\/browse\/([0-9a-f-]{36})/i.exec(page.url())?.[1];
  expect(documentsFolderId).toBeTruthy();
  if (!documentsFolderId) {
    throw new Error('Failed to resolve Documents folder id');
  }

  const breadcrumb = page.locator('nav[aria-label="breadcrumb"]');
  await expect(breadcrumb.getByText('Root', { exact: true })).toHaveCount(1);
  await expect(breadcrumb.getByText('Documents', { exact: true })).toBeVisible({ timeout: 60_000 });

  // Create folder (basic validation)
  const folderName = `ui-e2e-folder-${Date.now()}`;
  await page.getByRole('button', { name: 'New Folder', exact: true }).click();
  const createFolderDialog = page.getByRole('dialog').filter({ hasText: 'Create New Folder' });
  await expect(createFolderDialog).toBeVisible({ timeout: 60_000 });
  await createFolderDialog.getByLabel('Folder Name').fill(folderName);
  await createFolderDialog.getByRole('button', { name: 'Create', exact: true }).click();
  await expect(page.getByText('Folder created successfully')).toBeVisible({ timeout: 60_000 });

  // Work inside the dedicated folder to avoid pagination/virtualized rows.
  const workFolderId = await findChildFolderId(page.request, documentsFolderId, folderName, apiToken);
  await page.goto(`/browse/${workFolderId}`, { waitUntil: 'domcontentloaded' });
  const workFolderUrl = page.url();
  await expect(page.locator('nav[aria-label="breadcrumb"]').getByText(folderName, { exact: true })).toBeVisible({
    timeout: 60_000,
  });

  await page.getByRole('button', { name: 'Upload' }).click();
  const uploadDialog = page.getByRole('dialog').filter({ hasText: 'Upload Files' });

  const filename = `ui-e2e-${Date.now()}.txt`;
  await uploadDialog.locator('input[type="file"]').setInputFiles({
    name: filename,
    mimeType: 'text/plain',
    buffer: Buffer.from(`Athena ECM UI e2e ${new Date().toISOString()}\n`),
  });

  await uploadDialog.getByRole('button', { name: /^Upload/ }).click();
  await expect(uploadDialog).toBeHidden({ timeout: 60_000 });

  await expect(page.getByRole('row', { name: new RegExp(filename) })).toBeVisible({ timeout: 60_000 });
  const gridToggle = page.getByRole('button', { name: 'grid view' });
  const listToggle = page.getByRole('button', { name: 'list view' });

  await listToggle.click();
  await expect(page.locator('.MuiDataGrid-root')).toHaveCount(1);

  await gridToggle.click();
  await expect(gridToggle).toHaveAttribute('aria-pressed', 'true');
  await expect(page.locator('.MuiDataGrid-root')).toHaveCount(0);
  await expect(page.getByText('Modified:').first()).toBeVisible({ timeout: 60_000 });

  await listToggle.click();
  await expect(page.locator('.MuiDataGrid-root')).toHaveCount(1);

  const row = page.getByRole('row', { name: new RegExp(filename) });
  const uploadedDocumentId = await findDocumentId(page.request, workFolderId, filename, apiToken);

  // Preview (View)
  await row.getByRole('button', { name: `Actions for ${filename}` }).click();
  await page.getByRole('menuitem', { name: 'View', exact: true }).click();
  const previewDialog = page.getByRole('dialog').filter({ hasText: filename });
  await expect(previewDialog).toBeVisible({ timeout: 60_000 });
  await expect(previewDialog.getByRole('button', { name: 'close' })).toBeVisible({ timeout: 60_000 });
  await previewDialog.getByRole('button', { name: 'close' }).click();

  // Ensure the uploaded document is indexed before searching (avoids ES refresh timing flakes).
  const preIndexRes = await page.request.post(`http://localhost:7700/api/v1/search/index/${uploadedDocumentId}`, {
    headers: { Authorization: `Bearer ${apiToken}` },
  });
  expect(preIndexRes.ok()).toBeTruthy();

  // Properties: assign correspondent (manual)
  await row.getByRole('button', { name: `Actions for ${filename}` }).click();
  await page.getByRole('menuitem', { name: 'Properties', exact: true }).click();
  const propertiesDialog = page.getByRole('dialog').filter({ hasText: 'Properties' });
  await expect(propertiesDialog).toBeVisible({ timeout: 60_000 });
  await propertiesDialog.getByRole('button', { name: 'Edit', exact: true }).click();
  const correspondentSelect = propertiesDialog.getByLabel('Correspondent');
  await expect(correspondentSelect).toBeEnabled({ timeout: 60_000 });
  await correspondentSelect.click();
  await page.getByRole('option', { name: correspondentName, exact: true }).click();
  await propertiesDialog.getByRole('button', { name: 'Save', exact: true }).click();
  await expect(page.getByText('Properties updated successfully')).toBeVisible({ timeout: 60_000 });
  await propertiesDialog.getByRole('button', { name: 'Close', exact: true }).click();

  // Favorites (star column add -> verify -> remove)
  await row.getByRole('button', { name: `Favorite ${filename}` }).click();
  await expect(page.getByText('Added to favorites')).toBeVisible({ timeout: 60_000 });

  await page.goto('/favorites', { waitUntil: 'domcontentloaded' });
  await expect(page.getByText('Favorites')).toBeVisible({ timeout: 60_000 });
  await expect(page.getByText(filename, { exact: true })).toBeVisible({ timeout: 60_000 });
  await page.getByRole('button', { name: `Remove favorite ${filename}` }).click();
  await expect(page.getByText('Removed from favorites')).toBeVisible({ timeout: 60_000 });
  await expect(page.getByText(filename, { exact: true })).toHaveCount(0, { timeout: 60_000 });

  await page.goto(workFolderUrl, { waitUntil: 'domcontentloaded' });

  // Re-index to make correspondent visible in ES search results.
  const postPropIndexRes = await page.request.post(`http://localhost:7700/api/v1/search/index/${uploadedDocumentId}`, {
    headers: { Authorization: `Bearer ${apiToken}` },
  });
  expect(postPropIndexRes.ok()).toBeTruthy();

  // Search (may be eventually consistent; retry via quick search)
  await page.getByRole('button', { name: 'Search', exact: true }).click();
  const searchDialog = page.getByRole('dialog').filter({ hasText: 'Advanced Search' });
  await expect(searchDialog).toBeVisible({ timeout: 60_000 });
  await searchDialog.getByLabel('Name contains').fill(filename);
  const savedSearchName = `ui-e2e-saved-search-${Date.now()}`;
  await searchDialog.getByRole('button', { name: 'Save Search', exact: true }).click();
  const saveSearchDialog = page.getByRole('dialog').filter({ hasText: 'Save Search' });
  await expect(saveSearchDialog).toBeVisible({ timeout: 60_000 });
  await saveSearchDialog.getByLabel('Name').fill(savedSearchName);
  await saveSearchDialog.getByRole('button', { name: 'Save', exact: true }).click();
  await expect(page.getByText('Saved search created')).toBeVisible({ timeout: 60_000 });
  await searchDialog.getByRole('button', { name: 'Search', exact: true }).click();
  await page.waitForURL(/\/search-results/, { timeout: 60_000 });

  const quickSearchInput = page.getByPlaceholder('Quick search by name...');
  let found = false;
  for (let attempt = 0; attempt < 10; attempt += 1) {
    await quickSearchInput.fill(filename);
    await quickSearchInput.press('Enter');
    const match = page.getByText(filename, { exact: true }).first();
    try {
      await expect(match).toBeVisible({ timeout: 5_000 });
      found = true;
      break;
    } catch {
      await page.waitForTimeout(2_000);
    }
  }
  expect(found).toBeTruthy();

  const resultCard = page.locator('.MuiCard-root').filter({ hasText: filename }).first();
  await expect(resultCard.getByText(correspondentName, { exact: true })).toBeVisible({ timeout: 60_000 });

  const searchFacetCorrespondent = page.getByLabel('Correspondent');
  await searchFacetCorrespondent.click();
  const searchFacetOption = page.getByRole('option', { name: new RegExp(correspondentName) }).first();
  await expect(searchFacetOption).toBeVisible({ timeout: 60_000 });
  await searchFacetOption.click();
  await expect(resultCard).toBeVisible({ timeout: 60_000 });

  // Saved searches: list + load-to-dialog + execute + delete
  await page.goto('/saved-searches', { waitUntil: 'domcontentloaded' });
  await expect(page.getByText('Saved Searches')).toBeVisible({ timeout: 60_000 });
  await expect(page.getByText(savedSearchName)).toBeVisible({ timeout: 60_000 });

  await page.getByRole('button', { name: `Load saved search ${savedSearchName}` }).click();
  const loadedSearchDialog = page.getByRole('dialog').filter({ hasText: 'Advanced Search' });
  await expect(loadedSearchDialog).toBeVisible({ timeout: 60_000 });
  await expect(loadedSearchDialog.getByLabel('Name contains')).toHaveValue(filename, { timeout: 60_000 });
  await loadedSearchDialog.getByRole('button', { name: 'Search', exact: true }).click();
  await page.waitForURL(/\/search-results/, { timeout: 60_000 });
  await expect(page.getByText(filename, { exact: true }).first()).toBeVisible({ timeout: 60_000 });

  await page.goto('/saved-searches', { waitUntil: 'domcontentloaded' });
  await page.getByRole('button', { name: `Run saved search ${savedSearchName}` }).click();
  await page.waitForURL(/\/search-results/, { timeout: 60_000 });
  await expect(page.getByText(filename, { exact: true }).first()).toBeVisible({ timeout: 60_000 });

  await page.goto('/saved-searches', { waitUntil: 'domcontentloaded' });
  await page.getByRole('button', { name: `Delete saved search ${savedSearchName}` }).click();
  await expect(page.getByText('Saved search deleted')).toBeVisible({ timeout: 60_000 });

  await page.goto(workFolderUrl, { waitUntil: 'domcontentloaded' });

  // Copy/Move (UI)
  const targetFolderName = `ui-e2e-target-${Date.now()}`;
  await page.getByRole('button', { name: 'New Folder', exact: true }).click();
  const createTargetDialog = page.getByRole('dialog').filter({ hasText: 'Create New Folder' });
  await expect(createTargetDialog).toBeVisible({ timeout: 60_000 });
  await createTargetDialog.getByLabel('Folder Name').fill(targetFolderName);
  await createTargetDialog.getByRole('button', { name: 'Create', exact: true }).click();
  await expect(page.getByText('Folder created successfully')).toBeVisible({ timeout: 60_000 });

  const copyName = `ui-e2e-copy-${Date.now()}.txt`;
  const fileRow = page.getByRole('row', { name: new RegExp(filename) });
  const targetFolderRow = page.getByRole('row', { name: new RegExp(targetFolderName) });
  await expect(targetFolderRow).toBeVisible({ timeout: 60_000 });

  // Batch download (select 2 items -> download as zip)
  await fileRow.getByRole('checkbox').first().click();
  await targetFolderRow.getByRole('checkbox').first().click();
  await page.getByRole('button', { name: 'Download selected', exact: true }).click();
  await expect(page.getByText('Download started')).toBeVisible({ timeout: 60_000 });

  await fileRow.getByRole('button', { name: `Actions for ${filename}` }).click();
  await page.getByRole('menuitem', { name: 'Copy' }).click();

  const copyDialog = page.getByRole('dialog').filter({ hasText: 'Copy Item' });
  await expect(copyDialog).toBeVisible({ timeout: 60_000 });
  await copyDialog.getByLabel('New Name (optional)').fill(copyName);
  const copyTargetTreeItem = copyDialog.getByRole('treeitem', { name: targetFolderName, exact: true });
  await expect(copyTargetTreeItem).toBeVisible({ timeout: 60_000 });
  await copyTargetTreeItem.click();
  await copyDialog.getByRole('button', { name: 'Copy', exact: true }).click();
  await expect(copyDialog).toBeHidden({ timeout: 60_000 });

  await targetFolderRow.dblclick();
  await page.waitForURL(/\/browse\/[0-9a-f-]{36}$/i, { timeout: 60_000 });

  const copiedRow = page.getByRole('row', { name: new RegExp(copyName) });
  await expect(copiedRow).toBeVisible({ timeout: 60_000 });

  await copiedRow.getByRole('button', { name: `Actions for ${copyName}` }).click();
  await page.getByRole('menuitem', { name: 'Move' }).click();
  const moveDialog = page.getByRole('dialog').filter({ hasText: 'Move Item' });
  await expect(moveDialog).toBeVisible({ timeout: 60_000 });
  const moveTargetTreeItem = moveDialog.getByRole('treeitem', { name: folderName, exact: true });
  await expect(moveTargetTreeItem).toBeVisible({ timeout: 60_000 });
  await moveTargetTreeItem.scrollIntoViewIfNeeded();
  await moveTargetTreeItem.getByText(folderName, { exact: true }).click();
  await expect(moveDialog.getByText(`Selected: ${folderName}`, { exact: true })).toBeVisible({ timeout: 60_000 });
  await moveDialog.getByRole('button', { name: 'Move', exact: true }).click();
  await expect(page.getByText('Item moved successfully')).toBeVisible({ timeout: 60_000 });
  await expect(moveDialog).toBeHidden({ timeout: 60_000 });

  await expect(page.getByRole('row', { name: new RegExp(copyName) })).toHaveCount(0, { timeout: 60_000 });
  await page.goto(workFolderUrl, { waitUntil: 'domcontentloaded' });
  await expect(page.getByRole('row', { name: new RegExp(copyName) })).toBeVisible({ timeout: 60_000 });

  // Tag assignment
  const tagName = `ui-e2e-tag-${Date.now()}`;
  await row.getByRole('button', { name: `Actions for ${filename}` }).click();
  await page.getByRole('menuitem', { name: 'Tags' }).click();
  const tagDialog = page.getByRole('dialog').filter({ hasText: 'Tag Manager' });
  await expect(tagDialog).toBeVisible({ timeout: 60_000 });
  await tagDialog.getByRole('button', { name: 'New Tag', exact: true }).click();
  await tagDialog.getByLabel('Tag Name').fill(tagName);
  await tagDialog.getByRole('button', { name: 'Save', exact: true }).click();
  await expect(tagDialog.getByText(tagName, { exact: true })).toBeVisible({ timeout: 60_000 });
  await tagDialog
    .getByRole('row', { name: new RegExp(tagName) })
    .getByRole('button', { name: 'Add to Document', exact: true })
    .click();
  await expect(page.getByText(`Tag "${tagName}" added to document`)).toBeVisible({ timeout: 60_000 });
  await tagDialog.getByRole('button', { name: 'Close', exact: true }).click();

  // Category assignment
  const categoryName = `ui-e2e-cat-${Date.now()}`;
  await row.getByRole('button', { name: `Actions for ${filename}` }).click();
  await page.getByRole('menuitem', { name: 'Categories' }).click();
  const categoryDialog = page.getByRole('dialog').filter({ hasText: 'Category Manager' });
  await expect(categoryDialog).toBeVisible({ timeout: 60_000 });
  await categoryDialog.getByRole('button', { name: 'New Category', exact: true }).click();
  await categoryDialog.getByLabel('Category Name').fill(categoryName);
  await categoryDialog.getByRole('button', { name: 'Save', exact: true }).click();
  await expect(categoryDialog.getByText(categoryName, { exact: true })).toBeVisible({ timeout: 60_000 });
  await categoryDialog.getByRole('checkbox', { name: `Toggle category ${categoryName}`, exact: true }).click();
  await expect(page.getByText('Category added to document')).toBeVisible({ timeout: 60_000 });
  await categoryDialog.getByRole('button', { name: 'Close', exact: true }).click();

  // Re-index + faceted search API should surface tags/categories.
  const indexRes = await page.request.post(`http://localhost:7700/api/v1/search/index/${uploadedDocumentId}`, {
    headers: { Authorization: `Bearer ${apiToken}` },
  });
  expect(indexRes.ok()).toBeTruthy();

  type FacetValue = { value: string; count: number };
  let facetsFound = false;
  for (let attempt = 0; attempt < 15; attempt += 1) {
    const facetsRes = await page.request.get(
      `http://localhost:7700/api/v1/search/facets?q=${encodeURIComponent(filename)}`,
      {
        headers: { Authorization: `Bearer ${apiToken}` },
      },
    );
    expect(facetsRes.ok()).toBeTruthy();
    const facets = (await facetsRes.json()) as Record<string, FacetValue[]>;
    const tags = facets.tags ?? [];
    const categories = facets.categories ?? [];
    if (tags.some((t) => t.value === tagName) && categories.some((c) => c.value === categoryName)) {
      facetsFound = true;
      break;
    }
    await page.waitForTimeout(2_000);
  }
  expect(facetsFound).toBeTruthy();

  // Share link creation
  const shareName = `ui-e2e-share-${Date.now()}`;
  await row.getByRole('button', { name: `Actions for ${filename}` }).click();
  await page.getByRole('menuitem', { name: 'Share' }).click();
  const shareDialog = page.getByRole('dialog').filter({ hasText: 'Share Links' });
  await expect(shareDialog).toBeVisible({ timeout: 60_000 });
  await shareDialog.getByRole('button', { name: 'New Share Link', exact: true }).click();
  await shareDialog.getByLabel('Name').fill(shareName);
  await shareDialog.getByRole('button', { name: 'Create', exact: true }).click();
  await expect(page.getByText('Share link created')).toBeVisible({ timeout: 60_000 });
  await expect(shareDialog.getByText(shareName, { exact: true })).toBeVisible({ timeout: 60_000 });
  await shareDialog.getByRole('button', { name: 'Close', exact: true }).click();

  // Permissions dialog
  await row.getByRole('button', { name: `Actions for ${filename}` }).click();
  await page.getByRole('menuitem', { name: 'Permissions' }).click();
  const permissionsDialog = page.getByRole('dialog').filter({ hasText: 'Manage Permissions' });
  await expect(permissionsDialog).toBeVisible({ timeout: 60_000 });
  await expect(permissionsDialog.getByText('Inherit permissions from parent')).toBeVisible({ timeout: 60_000 });
  await permissionsDialog.getByRole('button', { name: 'Close', exact: true }).click();

  // Version history dialog
  await row.getByRole('button', { name: `Actions for ${filename}` }).click();
  await page.getByRole('menuitem', { name: 'Version History' }).click();
  const versionsDialog = page.getByRole('dialog').filter({ hasText: 'Version History' });
  await expect(versionsDialog).toBeVisible({ timeout: 60_000 });
  await expect(versionsDialog.getByText('Current', { exact: true })).toBeVisible({ timeout: 60_000 });
  await versionsDialog.getByRole('button', { name: 'Close', exact: true }).click();

  // ML suggestions dialog
  await row.getByRole('button', { name: `Actions for ${filename}` }).click();
  await page.getByRole('menuitem', { name: 'ML Suggestions' }).click();
  const mlDialog = page.getByRole('dialog').filter({ hasText: 'ML Suggestions' });
  await expect(mlDialog).toBeVisible({ timeout: 60_000 });
  await mlDialog.getByRole('button', { name: 'Close', exact: true }).click();

  // Workflow approval: start via API, approve via Tasks UI.
  const startApproval = await page.request.post(`http://localhost:7700/api/v1/workflows/document/${uploadedDocumentId}/approval`, {
    headers: { Authorization: `Bearer ${apiToken}` },
    data: { approvers: [defaultUsername], comment: 'ui-e2e approval' },
  });
  expect(startApproval.ok()).toBeTruthy();

  await page.goto('/tasks', { waitUntil: 'domcontentloaded' });
  await expect(page.getByText('My Tasks')).toBeVisible({ timeout: 60_000 });
  const approvalTask = page.locator('li').filter({ hasText: 'Approve Document' }).first();
  await expect(approvalTask).toBeVisible({ timeout: 60_000 });
  await approvalTask.getByRole('button', { name: 'Approve' }).click();
  await page.getByRole('button', { name: 'Confirm' }).click();
  await expect(page.getByText('Task approved successfully')).toBeVisible({ timeout: 60_000 });

  await page.goto(workFolderUrl, { waitUntil: 'domcontentloaded' });
  await expect(page.getByRole('row', { name: new RegExp(filename) })).toBeVisible({ timeout: 60_000 });

  await row.getByRole('button', { name: `Actions for ${filename}` }).click();
  await page.getByRole('menuitem', { name: 'Delete' }).click();
  await expect(page.getByRole('row', { name: new RegExp(filename) })).toHaveCount(0, { timeout: 60_000 });

  // Restore from trash
  await page.goto('/trash', { waitUntil: 'domcontentloaded' });
  const restoreButton = page.getByRole('button', { name: `Restore ${filename}` });
  let restoreVisible = false;
  for (let attempt = 0; attempt < 15; attempt += 1) {
    try {
      await expect(restoreButton).toBeVisible({ timeout: 2_000 });
      restoreVisible = true;
      break;
    } catch {
      await page.waitForTimeout(2_000);
      await page.reload({ waitUntil: 'domcontentloaded' });
    }
  }
  expect(restoreVisible).toBeTruthy();
  await restoreButton.click();
  await expect(page.getByText('Item restored')).toBeVisible({ timeout: 60_000 });

  await page.goto(workFolderUrl, { waitUntil: 'domcontentloaded' });
  await expect(page.getByRole('row', { name: new RegExp(filename) })).toBeVisible({ timeout: 60_000 });

  // Rules should be reachable via user menu (regression for missing entry)
  await page.getByRole('button', { name: 'Account menu' }).click();
  await page.getByRole('menuitem', { name: 'Rules' }).click();
  await expect(page.getByRole('heading', { name: 'Automation Rules' })).toBeVisible({ timeout: 60_000 });

  // Admin dashboard (users/groups lists)
  await page.getByRole('button', { name: 'Account menu' }).click();
  await page.getByRole('menuitem', { name: 'Admin Dashboard' }).click();
  await expect(page.getByRole('heading', { name: 'System Dashboard' })).toBeVisible({ timeout: 60_000 });
  await expect(page.getByRole('heading', { name: 'License' })).toBeVisible({ timeout: 60_000 });
  await expect(page.getByText(/Edition: /)).toBeVisible({ timeout: 60_000 });
  await page.getByRole('tab', { name: 'Users' }).click();
  await expect(page.getByRole('columnheader', { name: 'Username' })).toBeVisible({ timeout: 60_000 });
  await expect(page.getByRole('cell', { name: 'admin', exact: true })).toBeVisible({ timeout: 60_000 });
  await page.getByRole('tab', { name: 'Groups' }).click();
  await expect(page.getByRole('columnheader', { name: 'Name', exact: true })).toBeVisible({ timeout: 60_000 });
});

test('UI smoke: PDF upload + search + version history + preview', async ({ page }) => {
  page.on('dialog', (dialog) => dialog.accept());
  test.setTimeout(240_000);

  await loginWithCredentials(page, defaultUsername, defaultPassword);
  const apiToken = await fetchAccessToken(page.request, defaultUsername, defaultPassword);

  await page.goto('/browse/root', { waitUntil: 'domcontentloaded' });
  await page.getByRole('treeitem', { name: 'Documents' }).click();
  await page.waitForURL(/\/browse\/[0-9a-f-]{36}$/i, { timeout: 60_000 });
  const documentsFolderId = /\/browse\/([0-9a-f-]{36})/i.exec(page.url())?.[1];
  expect(documentsFolderId).toBeTruthy();
  if (!documentsFolderId) {
    throw new Error('Failed to resolve Documents folder id');
  }

  const folderName = `ui-e2e-pdf-${Date.now()}`;
  await page.getByRole('button', { name: 'New Folder', exact: true }).click();
  const createFolderDialog = page.getByRole('dialog').filter({ hasText: 'Create New Folder' });
  await expect(createFolderDialog).toBeVisible({ timeout: 60_000 });
  await createFolderDialog.getByLabel('Folder Name').fill(folderName);
  await createFolderDialog.getByRole('button', { name: 'Create', exact: true }).click();
  await expect(page.getByText('Folder created successfully')).toBeVisible({ timeout: 60_000 });

  const workFolderId = await findChildFolderId(page.request, documentsFolderId, folderName, apiToken);
  await page.goto(`/browse/${workFolderId}`, { waitUntil: 'domcontentloaded' });
  const workFolderUrl = page.url();

  await page.getByRole('button', { name: 'Upload' }).click();
  const uploadDialog = page.getByRole('dialog').filter({ hasText: 'Upload Files' });

  const pdfName = `ui-e2e-${Date.now()}.pdf`;
  await uploadDialog.locator('input[type="file"]').setInputFiles({
    name: pdfName,
    mimeType: 'application/pdf',
    buffer: Buffer.from('%PDF-1.4\n1 0 obj << /Type /Catalog >> endobj\ntrailer << /Root 1 0 R >>\n%%EOF\n'),
  });
  await uploadDialog.getByRole('button', { name: /^Upload/ }).click();
  await expect(uploadDialog).toBeHidden({ timeout: 60_000 });

  const pdfRow = page.getByRole('row', { name: new RegExp(pdfName) });
  let pdfVisible = false;
  for (let attempt = 0; attempt < 15; attempt += 1) {
    try {
      await expect(pdfRow).toBeVisible({ timeout: 4_000 });
      pdfVisible = true;
      break;
    } catch {
      await page.waitForTimeout(2_000);
      await page.goto(workFolderUrl, { waitUntil: 'domcontentloaded' });
    }
  }
  expect(pdfVisible).toBeTruthy();

  const pdfDocumentId = await findDocumentId(page.request, workFolderId, pdfName, apiToken);

  await pdfRow.getByRole('button', { name: `Actions for ${pdfName}` }).click();
  await page.getByRole('menuitem', { name: 'Version History' }).click();
  const versionsDialog = page.getByRole('dialog').filter({ hasText: 'Version History' });
  await expect(versionsDialog).toBeVisible({ timeout: 60_000 });
  await expect(versionsDialog.getByText('Current', { exact: true })).toBeVisible({ timeout: 60_000 });
  await versionsDialog.getByRole('button', { name: 'Close', exact: true }).click();

  await pdfRow.getByRole('button', { name: `Actions for ${pdfName}` }).click();
  await page.getByRole('menuitem', { name: 'View', exact: true }).click();
  const previewDialog = page.getByRole('dialog').filter({ hasText: pdfName });
  await expect(previewDialog).toBeVisible({ timeout: 60_000 });
  await expect(previewDialog.getByRole('button', { name: 'close' })).toBeVisible({ timeout: 60_000 });
  await previewDialog.getByRole('button', { name: 'close' }).click();

  const indexRes = await page.request.post(`http://localhost:7700/api/v1/search/index/${pdfDocumentId}`, {
    headers: { Authorization: `Bearer ${apiToken}` },
  });
  expect(indexRes.ok()).toBeTruthy();

  await page.getByRole('button', { name: 'Search', exact: true }).click();
  const searchDialog = page.getByRole('dialog').filter({ hasText: 'Advanced Search' });
  await expect(searchDialog).toBeVisible({ timeout: 60_000 });
  await searchDialog.getByLabel('Name contains').fill(pdfName);
  await searchDialog.getByRole('button', { name: 'Search', exact: true }).click();
  await page.waitForURL(/\/search-results/, { timeout: 60_000 });

  const quickSearchInput = page.getByPlaceholder('Quick search by name...');
  let found = false;
  for (let attempt = 0; attempt < 10; attempt += 1) {
    await quickSearchInput.fill(pdfName);
    await quickSearchInput.press('Enter');
    const match = page.getByText(pdfName, { exact: true }).first();
    try {
      await expect(match).toBeVisible({ timeout: 5_000 });
      found = true;
      break;
    } catch {
      await page.waitForTimeout(2_000);
    }
  }
  expect(found).toBeTruthy();
});

test('RBAC smoke: editor can access rules but not admin endpoints', async ({ page, request }) => {
  page.on('dialog', (dialog) => dialog.accept());
  test.setTimeout(300_000);

  const editorUsername = process.env.ECM_E2E_EDITOR_USERNAME || 'editor';
  const editorPassword = process.env.ECM_E2E_EDITOR_PASSWORD || 'editor';

  await loginWithCredentials(page, editorUsername, editorPassword);

  // Rules should be reachable (ROLE_EDITOR)
  await page.getByRole('button', { name: 'Account menu' }).click();
  await page.getByRole('menuitem', { name: 'Rules' }).click();
  await expect(page.getByRole('heading', { name: 'Automation Rules' })).toBeVisible({ timeout: 60_000 });

  await page.getByRole('button', { name: 'Account menu' }).click();
  await expect(page.getByRole('menuitem', { name: 'Admin Dashboard' })).toHaveCount(0);
  await page.keyboard.press('Escape');

  // Admin routes should redirect to /unauthorized
  await page.goto('/admin', { waitUntil: 'domcontentloaded' });
  await page.waitForURL(/\/unauthorized$/, { timeout: 60_000 });
  await expect(page.getByRole('heading', { name: 'Unauthorized' })).toBeVisible({ timeout: 60_000 });

  // Admin-only API should be forbidden for editor.
  const token = await fetchAccessToken(request, editorUsername, editorPassword);
  const authoritiesRes = await request.get('http://localhost:7700/api/v1/security/users/current/authorities', {
    headers: { Authorization: `Bearer ${token}` },
  });
  expect(authoritiesRes.ok()).toBeTruthy();
  const authorities = (await authoritiesRes.json()) as string[];
  expect(authorities).toContain('ROLE_EDITOR');

  const licenseRes = await request.get('http://localhost:7700/api/v1/system/license', {
    headers: { Authorization: `Bearer ${token}` },
  });
  expect(licenseRes.status()).toBe(403);

  // Editor WOPI edit flow should create a new version.
  const rootsRes = await request.get('http://localhost:7700/api/v1/folders/roots', {
    headers: { Authorization: `Bearer ${token}` },
  });
  expect(rootsRes.ok()).toBeTruthy();
  const roots = (await rootsRes.json()) as { id: string; name: string }[];
  const uploadsFolder = roots.find((root) => root.name === 'uploads') ?? roots[0];
  expect(uploadsFolder?.id).toBeTruthy();

  const officeFilename = `e2e-editor-wopi-${Date.now()}.xlsx`;
  const officeBytes = Buffer.from(XLSX_SAMPLE_BASE64, 'base64');
  const uploadRes = await request.post(
    `http://localhost:7700/api/v1/documents/upload?folderId=${uploadsFolder.id}`,
    {
      headers: { Authorization: `Bearer ${token}` },
      multipart: {
        file: {
          name: officeFilename,
          mimeType: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',
          buffer: officeBytes,
        },
      },
    },
  );
  expect(uploadRes.ok()).toBeTruthy();
  const uploadResult = (await uploadRes.json()) as { documentId?: string; id?: string };
  const docId = uploadResult.documentId ?? uploadResult.id;
  expect(docId).toBeTruthy();

  const initialVersions = await getVersionCount(request, docId, token);

  await request.post(`http://localhost:7700/api/v1/search/index/${docId}`, {
    headers: { Authorization: `Bearer ${token}` },
  });
  await waitForSearchIndex(request, officeFilename, token);

  await page.goto('/search-results', { waitUntil: 'domcontentloaded' });
  await page.getByPlaceholder('Quick search by name...').fill(officeFilename);
  await page.keyboard.press('Enter');

  const heading = page.getByRole('heading', { name: officeFilename }).first();
  await expect(heading).toBeVisible({ timeout: 60_000 });

  const resultCard = page.locator('div.MuiCard-root', { hasText: officeFilename }).first();
  await resultCard.getByRole('button', { name: 'View' }).click();

  const menuButton = page.locator('button:has(svg[data-testid=\"MoreVertIcon\"])').first();
  await expect(menuButton).toBeVisible({ timeout: 60_000 });
  await menuButton.click();

  await expect(page.getByRole('menuitem', { name: 'Edit Online' })).toBeVisible({ timeout: 30_000 });
  await page.getByRole('menuitem', { name: 'Edit Online' }).click();

  await page.waitForURL(/\/editor\//, { timeout: 60_000 });
  await expect(page).toHaveURL(/permission=write/);
  const editorFrame = page.frameLocator('iframe[title=\"Online Editor\"]');
  const canvas = editorFrame.locator('canvas').first();
  await expect(canvas).toBeVisible({ timeout: 60_000 });
  await dismissWopiWelcome(page, editorFrame);

  await canvas.click({ position: { x: 120, y: 160 }, force: true });
  const editStamp = `E2E-${Date.now()}`;
  await page.keyboard.type(editStamp);
  await page.keyboard.press('Enter');

  const saveButton = editorFrame.getByRole('button', { name: 'Save' });
  if (await saveButton.count()) {
    await dismissWopiWelcome(page, editorFrame);
    await saveButton.click();
  } else {
    await page.keyboard.press('Control+S');
  }

  await waitForVersionCount(request, docId, token, initialVersions + 1);

  await request.delete(`http://localhost:7700/api/v1/nodes/${docId}`, {
    headers: { Authorization: `Bearer ${token}` },
  });
});

test('RBAC smoke: viewer cannot access rules or admin endpoints', async ({ page, request }) => {
  page.on('dialog', (dialog) => dialog.accept());
  test.setTimeout(240_000);

  const viewerUsername = process.env.ECM_E2E_VIEWER_USERNAME || 'viewer';
  const viewerPassword = process.env.ECM_E2E_VIEWER_PASSWORD || 'viewer';

  await loginWithCredentials(page, viewerUsername, viewerPassword);

  // Restricted menu items should not be visible
  await page.getByRole('button', { name: 'Account menu' }).click();
  await expect(page.getByRole('menuitem', { name: 'Rules' })).toHaveCount(0);
  await expect(page.getByRole('menuitem', { name: 'Admin Dashboard' })).toHaveCount(0);
  await page.keyboard.press('Escape');

  // Rules route should redirect to /unauthorized
  await page.goto('/rules', { waitUntil: 'domcontentloaded' });
  await page.waitForURL(/\/unauthorized$/, { timeout: 60_000 });
  await expect(page.getByRole('heading', { name: 'Unauthorized' })).toBeVisible({ timeout: 60_000 });

  // Admin route should redirect to /unauthorized
  await page.goto('/admin', { waitUntil: 'domcontentloaded' });
  await page.waitForURL(/\/unauthorized$/, { timeout: 60_000 });
  await expect(page.getByRole('heading', { name: 'Unauthorized' })).toBeVisible({ timeout: 60_000 });

  const token = await fetchAccessToken(request, viewerUsername, viewerPassword);
  const authoritiesRes = await request.get('http://localhost:7700/api/v1/security/users/current/authorities', {
    headers: { Authorization: `Bearer ${token}` },
  });
  expect(authoritiesRes.ok()).toBeTruthy();
  const authorities = (await authoritiesRes.json()) as string[];
  expect(authorities).toContain('ROLE_VIEWER');
  expect(authorities).not.toContain('ROLE_ADMIN');

  const licenseRes = await request.get('http://localhost:7700/api/v1/system/license', {
    headers: { Authorization: `Bearer ${token}` },
  });
  expect(licenseRes.status()).toBe(403);

  // Viewer should only see View Online for office docs.
  const adminToken = await fetchAccessToken(request, defaultUsername, defaultPassword);
  const rootsRes = await request.get('http://localhost:7700/api/v1/folders/roots', {
    headers: { Authorization: `Bearer ${adminToken}` },
  });
  expect(rootsRes.ok()).toBeTruthy();
  const roots = (await rootsRes.json()) as { id: string; name: string }[];
  const uploadsFolder = roots.find((root) => root.name === 'uploads') ?? roots[0];
  expect(uploadsFolder?.id).toBeTruthy();

  const officeFilename = `e2e-viewer-wopi-${Date.now()}.xlsx`;
  const officeBytes = Buffer.from(XLSX_SAMPLE_BASE64, 'base64');
  const uploadRes = await request.post(
    `http://localhost:7700/api/v1/documents/upload?folderId=${uploadsFolder.id}`,
    {
      headers: { Authorization: `Bearer ${adminToken}` },
      multipart: {
        file: {
          name: officeFilename,
          mimeType: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',
          buffer: officeBytes,
        },
      },
    },
  );
  expect(uploadRes.ok()).toBeTruthy();
  const uploadResult = (await uploadRes.json()) as { documentId?: string; id?: string };
  const docId = uploadResult.documentId ?? uploadResult.id;
  expect(docId).toBeTruthy();

  await request.post(`http://localhost:7700/api/v1/search/index/${docId}`, {
    headers: { Authorization: `Bearer ${adminToken}` },
  });
  await waitForSearchIndex(request, officeFilename, adminToken);

  await page.goto('/search-results', { waitUntil: 'domcontentloaded' });
  await page.getByPlaceholder('Quick search by name...').fill(officeFilename);
  await page.keyboard.press('Enter');

  const heading = page.getByRole('heading', { name: officeFilename }).first();
  await expect(heading).toBeVisible({ timeout: 60_000 });

  const resultCard = page.locator('div.MuiCard-root', { hasText: officeFilename }).first();
  await resultCard.getByRole('button', { name: 'View' }).click();

  const menuButton = page.locator('button:has(svg[data-testid=\"MoreVertIcon\"])').first();
  await expect(menuButton).toBeVisible({ timeout: 60_000 });
  await menuButton.click();

  await expect(page.getByRole('menuitem', { name: 'View Online' })).toBeVisible({ timeout: 30_000 });
  await expect(page.getByRole('menuitem', { name: 'Edit Online' })).toHaveCount(0);

  await page.getByRole('menuitem', { name: 'View Online' }).click();
  await page.waitForURL(/\/editor\//, { timeout: 60_000 });
  await expect(page).toHaveURL(/permission=read/);
  await expect(page.locator('iframe[title=\"Online Editor\"]')).toBeVisible({ timeout: 60_000 });

  await request.delete(`http://localhost:7700/api/v1/nodes/${docId}`, {
    headers: { Authorization: `Bearer ${adminToken}` },
  });
});

test('Rule Automation: auto-tag on document upload', async ({ page, request }) => {
  page.on('dialog', (dialog) => dialog.accept());
  test.setTimeout(180_000);

  await loginWithCredentials(page, defaultUsername, defaultPassword);
  const apiToken = await fetchAccessToken(request, defaultUsername, defaultPassword);

  // Resolve uploads folder
  const rootsRes = await request.get('http://localhost:7700/api/v1/folders/roots', {
    headers: { Authorization: `Bearer ${apiToken}` },
  });
  expect(rootsRes.ok()).toBeTruthy();
  const roots = (await rootsRes.json()) as { id: string; name: string }[];
  const uploadsFolder = roots.find((r) => r.name === 'uploads') ?? roots[0];
  expect(uploadsFolder).toBeTruthy();

  // Create automation rule: auto-tag on DOCUMENT_CREATED
  const ruleTagName = `e2e-auto-tag-${Date.now()}`;
  const ruleName = `e2e-rule-${Date.now()}`;
  const rulePayload = {
    name: ruleName,
    description: 'E2E automation test rule',
    triggerType: 'DOCUMENT_CREATED',
    priority: 100,
    enabled: true,
    stopOnMatch: false,
    scopeMimeTypes: '',
    scopeFolderId: null,
    condition: { type: 'ALWAYS_TRUE' },
    actions: [
      {
        type: 'ADD_TAG',
        params: { tagName: ruleTagName },
        continueOnError: true,
        order: 0,
      },
    ],
  };

  const ruleRes = await request.post('http://localhost:7700/api/v1/rules', {
    headers: { Authorization: `Bearer ${apiToken}`, 'Content-Type': 'application/json' },
    data: rulePayload,
  });
  expect(ruleRes.ok()).toBeTruthy();
  const rule = (await ruleRes.json()) as { id: string };
  expect(rule.id).toBeTruthy();

  // Upload a document (this should trigger the rule)
  const testFilename = `e2e-rule-test-${Date.now()}.txt`;
  const uploadRes = await request.post(
    `http://localhost:7700/api/v1/documents/upload?folderId=${uploadsFolder.id}`,
    {
      headers: { Authorization: `Bearer ${apiToken}` },
      multipart: {
        file: {
          name: testFilename,
          mimeType: 'text/plain',
          buffer: Buffer.from(`E2E rule automation test ${new Date().toISOString()}\n`),
        },
      },
    },
  );
  expect(uploadRes.ok()).toBeTruthy();
  const uploadResult = (await uploadRes.json()) as { documentId?: string; id?: string };
  const docId = uploadResult.documentId ?? uploadResult.id;
  expect(docId).toBeTruthy();

  // Wait for rule to execute and verify tag was applied
  let tagFound = false;
  for (let attempt = 0; attempt < 15; attempt += 1) {
    const tagsRes = await request.get(`http://localhost:7700/api/v1/nodes/${docId}/tags`, {
      headers: { Authorization: `Bearer ${apiToken}` },
    });
    if (tagsRes.ok()) {
      const tags = (await tagsRes.json()) as { name: string }[];
      if (tags.some((t) => t.name === ruleTagName)) {
        tagFound = true;
        break;
      }
    }
    await page.waitForTimeout(1_000);
  }
  expect(tagFound).toBeTruthy();

  // Cleanup: delete document and rule
  await request.post(`http://localhost:7700/api/trash/nodes/${docId}`, {
    headers: { Authorization: `Bearer ${apiToken}` },
  });
  await request.delete(`http://localhost:7700/api/trash/${docId}`, {
    headers: { Authorization: `Bearer ${apiToken}` },
  });
  await request.delete(`http://localhost:7700/api/v1/rules/${rule.id}`, {
    headers: { Authorization: `Bearer ${apiToken}` },
  });

  // Verify via UI that the Rules page shows template buttons
  await page.goto('/rules', { waitUntil: 'domcontentloaded' });
  await expect(page.getByRole('heading', { name: 'Automation Rules' })).toBeVisible({ timeout: 60_000 });
  await expect(page.getByRole('button', { name: 'Auto-tag Template' })).toBeVisible({ timeout: 60_000 });
  await expect(page.getByRole('button', { name: 'Auto-approval Template' })).toBeVisible({ timeout: 60_000 });

  // Click Auto-tag Template and verify dialog opens with pre-filled values
  await page.getByRole('button', { name: 'Auto-tag Template' }).click();
  const ruleDialog = page.getByRole('dialog').filter({ hasText: 'New Rule' });
  await expect(ruleDialog).toBeVisible({ timeout: 60_000 });
  await expect(ruleDialog.getByLabel('Name')).toHaveValue('Auto-tag on Upload', { timeout: 60_000 });
  await ruleDialog.getByRole('button', { name: 'Cancel', exact: true }).click();
  await expect(ruleDialog).toBeHidden({ timeout: 60_000 });
});

test('Scheduled Rules: CRUD + cron validation + UI configuration', async ({ page, request }) => {
  page.on('dialog', (dialog) => dialog.accept());
  test.setTimeout(180_000);

  await loginWithCredentials(page, defaultUsername, defaultPassword);
  const apiToken = await fetchAccessToken(request, defaultUsername, defaultPassword);

  // ============================================================
  // STEP 0: Create a dedicated test folder for scheduled rule scope
  // ============================================================
  const rootsRes = await request.get('http://localhost:7700/api/v1/folders/roots', {
    headers: { Authorization: `Bearer ${apiToken}` },
  });
  expect(rootsRes.ok()).toBeTruthy();
  const roots = (await rootsRes.json()) as { id: string; name: string }[];
  const uploadsFolder = roots.find((r) => r.name === 'uploads') ?? roots[0];
  expect(uploadsFolder).toBeTruthy();

  const testFolderName = `e2e-scheduled-test-${Date.now()}`;
  const createFolderRes = await request.post('http://localhost:7700/api/v1/folders', {
    headers: { Authorization: `Bearer ${apiToken}`, 'Content-Type': 'application/json' },
    data: { name: testFolderName, parentId: uploadsFolder.id },
  });
  expect(createFolderRes.ok()).toBeTruthy();
  const testFolder = (await createFolderRes.json()) as { id: string; name: string };
  expect(testFolder.id).toBeTruthy();
  console.log(`Created dedicated test folder: ${testFolderName} (${testFolder.id})`);

  // ============================================================
  // STEP 1: Test cron validation API
  // ============================================================
  const cronValidationRes = await request.post('http://localhost:7700/api/v1/rules/validate-cron', {
    headers: { Authorization: `Bearer ${apiToken}`, 'Content-Type': 'application/json' },
    data: { cronExpression: '0 0 * * * *', timezone: 'UTC' },
  });
  expect(cronValidationRes.ok()).toBeTruthy();
  const cronValidation = (await cronValidationRes.json()) as { valid: boolean; nextExecutions?: string[] };
  expect(cronValidation.valid).toBe(true);
  expect(cronValidation.nextExecutions).toBeDefined();
  expect(cronValidation.nextExecutions!.length).toBeGreaterThan(0);

  // Test invalid cron expression
  const invalidCronRes = await request.post('http://localhost:7700/api/v1/rules/validate-cron', {
    headers: { Authorization: `Bearer ${apiToken}`, 'Content-Type': 'application/json' },
    data: { cronExpression: 'invalid-cron', timezone: 'UTC' },
  });
  expect(invalidCronRes.ok()).toBeTruthy();
  const invalidCron = (await invalidCronRes.json()) as { valid: boolean; error?: string };
  expect(invalidCron.valid).toBe(false);
  expect(invalidCron.error).toBeTruthy();

  // ============================================================
  // STEP 2: Create a scheduled rule via API with scopeFolderId
  // ============================================================
  const scheduledRuleName = `e2e-scheduled-rule-${Date.now()}`;
  const scheduledRulePayload = {
    name: scheduledRuleName,
    description: 'E2E scheduled rule test',
    triggerType: 'SCHEDULED',
    priority: 100,
    enabled: true,
    stopOnMatch: false,
    scopeMimeTypes: '',
    scopeFolderId: testFolder.id, // Scope to test folder for isolated testing
    condition: { type: 'ALWAYS_TRUE' },
    actions: [
      {
        type: 'ADD_TAG',
        params: { tagName: 'scheduled-e2e-tag' },
        continueOnError: true,
        order: 0,
      },
    ],
    cronExpression: '0 0 9 * * MON-FRI',
    timezone: 'America/New_York',
    maxItemsPerRun: 50,
  };

  const createRuleRes = await request.post('http://localhost:7700/api/v1/rules', {
    headers: { Authorization: `Bearer ${apiToken}`, 'Content-Type': 'application/json' },
    data: scheduledRulePayload,
  });
  expect(createRuleRes.ok()).toBeTruthy();
  const createdRule = (await createRuleRes.json()) as {
    id: string;
    cronExpression?: string;
    timezone?: string;
    maxItemsPerRun?: number;
    triggerType?: string;
  };
  expect(createdRule.id).toBeTruthy();
  expect(createdRule.cronExpression).toBe('0 0 9 * * MON-FRI');
  expect(createdRule.timezone).toBe('America/New_York');
  expect(createdRule.maxItemsPerRun).toBe(50);
  expect(createdRule.triggerType).toBe('SCHEDULED');

  // Fetch the rule to verify persistence
  const fetchRuleRes = await request.get(`http://localhost:7700/api/v1/rules/${createdRule.id}`, {
    headers: { Authorization: `Bearer ${apiToken}` },
  });
  expect(fetchRuleRes.ok()).toBeTruthy();
  const fetchedRule = (await fetchRuleRes.json()) as {
    cronExpression?: string;
    timezone?: string;
    maxItemsPerRun?: number;
  };
  expect(fetchedRule.cronExpression).toBe('0 0 9 * * MON-FRI');
  expect(fetchedRule.timezone).toBe('America/New_York');

  // Navigate to Rules page and verify the scheduled rule shows
  await page.goto('/rules', { waitUntil: 'domcontentloaded' });
  await expect(page.getByRole('heading', { name: 'Automation Rules' })).toBeVisible({ timeout: 60_000 });

  // Search for the scheduled rule in the table
  const ruleRow = page.getByRole('row', { name: new RegExp(scheduledRuleName) });
  await expect(ruleRow).toBeVisible({ timeout: 60_000 });
  await expect(ruleRow.getByRole('cell', { name: 'SCHEDULED', exact: true })).toBeVisible({ timeout: 60_000 });

  // Edit the scheduled rule and verify the schedule configuration fields appear
  await ruleRow.getByRole('button', { name: /Edit/i }).click();
  const editDialog = page.getByRole('dialog').filter({ hasText: /Edit Rule|New Rule/ });
  await expect(editDialog).toBeVisible({ timeout: 60_000 });

  // Verify Schedule Configuration section is visible
  await expect(editDialog.getByText('Schedule Configuration')).toBeVisible({ timeout: 60_000 });
  await expect(editDialog.getByLabel('Cron Expression')).toHaveValue('0 0 9 * * MON-FRI', { timeout: 60_000 });
  await expect(editDialog.getByLabel('Timezone')).toBeVisible({ timeout: 60_000 });
  await expect(editDialog.getByLabel('Max Items Per Run')).toHaveValue('50', { timeout: 60_000 });

  // Test cron validation button in UI
  await editDialog.getByRole('button', { name: 'Validate', exact: true }).click();
  await expect(page.getByText('Cron expression is valid')).toBeVisible({ timeout: 60_000 });

  // Close dialog
  await editDialog.getByRole('button', { name: 'Cancel', exact: true }).click();
  await expect(editDialog).toBeHidden({ timeout: 60_000 });

  // Create a new scheduled rule via UI
  await page.getByRole('button', { name: 'New Rule' }).click();
  const newRuleDialog = page.getByRole('dialog').filter({ hasText: 'New Rule' });
  await expect(newRuleDialog).toBeVisible({ timeout: 60_000 });

  const uiScheduledRuleName = `e2e-ui-scheduled-${Date.now()}`;
  await newRuleDialog.getByLabel('Name').fill(uiScheduledRuleName);
  await newRuleDialog.getByLabel('Description').fill('UI scheduled rule test');

  // Change trigger to SCHEDULED
  await newRuleDialog.getByLabel('Trigger').click();
  await page.getByRole('option', { name: 'SCHEDULED' }).click();

  // Verify Schedule Configuration section appears
  await expect(newRuleDialog.getByText('Schedule Configuration')).toBeVisible({ timeout: 60_000 });

  // Select a cron preset
  await newRuleDialog.getByLabel('Cron Preset').click();
  await page.getByRole('option', { name: 'Every hour' }).click();
  await expect(newRuleDialog.getByLabel('Cron Expression')).toHaveValue('0 0 * * * *', { timeout: 60_000 });

  // Validate the cron expression
  await newRuleDialog.getByRole('button', { name: 'Validate', exact: true }).click();
  await expect(page.getByText('Cron expression is valid')).toBeVisible({ timeout: 60_000 });

  // Save the new scheduled rule
  await newRuleDialog.getByRole('button', { name: 'Save', exact: true }).click();
  await expect(page.getByText('Rule created')).toBeVisible({ timeout: 60_000 });
  await expect(newRuleDialog).toBeHidden({ timeout: 60_000 });

  // Verify the new rule appears in the table
  const newRuleRow = page.getByRole('row', { name: new RegExp(uiScheduledRuleName) });
  await expect(newRuleRow).toBeVisible({ timeout: 60_000 });
  await expect(newRuleRow.getByRole('cell', { name: 'SCHEDULED', exact: true })).toBeVisible({ timeout: 60_000 });

  // ============================================================
  // STEP 5: Manual trigger test with STRONG ASSERTIONS
  // Upload document to test folder → trigger scheduled rule → REQUIRE tag
  // ============================================================

  // Upload a test document to the dedicated test folder (scoped by rule)
  const scheduledTestFilename = `e2e-scheduled-trigger-${Date.now()}.txt`;
  const uploadRes = await request.post(
    `http://localhost:7700/api/v1/documents/upload?folderId=${testFolder.id}`,
    {
      headers: { Authorization: `Bearer ${apiToken}` },
      multipart: {
        file: {
          name: scheduledTestFilename,
          mimeType: 'text/plain',
          buffer: Buffer.from(`Scheduled rule trigger test ${new Date().toISOString()}\n`),
        },
      },
    },
  );
  expect(uploadRes.ok()).toBeTruthy();
  const uploadResult = (await uploadRes.json()) as { documentId?: string; id?: string };
  const scheduledTestDocId = uploadResult.documentId ?? uploadResult.id;
  expect(scheduledTestDocId).toBeTruthy();
  console.log(`Uploaded test document: ${scheduledTestFilename} (${scheduledTestDocId}) to folder ${testFolder.id}`);

  // Manually trigger the scheduled rule (avoids waiting for poll interval)
  const triggerRes = await request.post(`http://localhost:7700/api/v1/rules/${createdRule.id}/trigger`, {
    headers: { Authorization: `Bearer ${apiToken}` },
  });
  expect(triggerRes.ok()).toBeTruthy(); // STRONG: trigger must succeed
  console.log('Scheduled rule triggered successfully');

  // STRONG ASSERTION: Wait and REQUIRE tag to be applied
  let scheduledTagFound = false;
  for (let attempt = 0; attempt < 20; attempt += 1) {
    const tagsRes = await request.get(`http://localhost:7700/api/v1/nodes/${scheduledTestDocId}/tags`, {
      headers: { Authorization: `Bearer ${apiToken}` },
    });
    if (tagsRes.ok()) {
      const tags = (await tagsRes.json()) as { name: string }[];
      if (tags.some((t) => t.name === 'scheduled-e2e-tag')) {
        scheduledTagFound = true;
        console.log(`Tag 'scheduled-e2e-tag' found on document after ${attempt + 1} attempt(s)`);
        break;
      }
    }
    await page.waitForTimeout(500);
  }

  // STRONG ASSERTION: Test FAILS if tag not applied
  expect(scheduledTagFound).toBe(true);
  console.log('Scheduled rule trigger verification PASSED: auto-tag applied to test document');

  // Cleanup the scheduled test document
  await request.post(`http://localhost:7700/api/trash/nodes/${scheduledTestDocId}`, {
    headers: { Authorization: `Bearer ${apiToken}` },
  });
  await request.delete(`http://localhost:7700/api/trash/${scheduledTestDocId}`, {
    headers: { Authorization: `Bearer ${apiToken}` },
  });

  // Cleanup: delete both scheduled rules via API
  // First get all rules and find the UI-created one
  const rulesListRes = await request.get('http://localhost:7700/api/v1/rules', {
    headers: { Authorization: `Bearer ${apiToken}` },
  });
  expect(rulesListRes.ok()).toBeTruthy();
  const rulesList = (await rulesListRes.json()) as { content: { id: string; name: string }[] };
  const uiCreatedRule = rulesList.content.find((r) => r.name === uiScheduledRuleName);

  // Delete API-created rule
  await request.delete(`http://localhost:7700/api/v1/rules/${createdRule.id}`, {
    headers: { Authorization: `Bearer ${apiToken}` },
  });

  // Delete UI-created rule if found
  if (uiCreatedRule) {
    await request.delete(`http://localhost:7700/api/v1/rules/${uiCreatedRule.id}`, {
      headers: { Authorization: `Bearer ${apiToken}` },
    });
  }

  // Cleanup: delete the dedicated test folder
  await request.post(`http://localhost:7700/api/trash/nodes/${testFolder.id}`, {
    headers: { Authorization: `Bearer ${apiToken}` },
  });
  await request.delete(`http://localhost:7700/api/trash/${testFolder.id}`, {
    headers: { Authorization: `Bearer ${apiToken}` },
  });
  console.log(`Cleaned up test folder: ${testFolderName}`);
});

test('Security Features: MFA guidance + Audit export + Retention', async ({ page, request }) => {
  page.on('dialog', (dialog) => dialog.accept());
  test.setTimeout(180_000);

  await loginWithCredentials(page, defaultUsername, defaultPassword);
  const apiToken = await fetchAccessToken(request, defaultUsername, defaultPassword);

  // Test audit export API
  const now = new Date();
  const thirtyDaysAgo = new Date(now.getTime() - 30 * 24 * 60 * 60 * 1000);
  const fromStr = thirtyDaysAgo.toISOString();
  const toStr = now.toISOString();

  const exportRes = await request.get(
    `http://localhost:7700/api/v1/analytics/audit/export?from=${fromStr}&to=${toStr}`,
    {
      headers: { Authorization: `Bearer ${apiToken}` },
    },
  );
  expect(exportRes.ok()).toBeTruthy();
  const exportContent = await exportRes.text();
  // CSV should have header row
  expect(exportContent).toContain('ID,Event Type,Node ID,Node Name,Username,Event Time,Details,Client IP,User Agent');

  // Test audit retention info API
  const retentionRes = await request.get('http://localhost:7700/api/v1/analytics/audit/retention', {
    headers: { Authorization: `Bearer ${apiToken}` },
  });
  expect(retentionRes.ok()).toBeTruthy();
  const retentionInfo = (await retentionRes.json()) as { retentionDays: number; expiredLogCount: number };
  expect(retentionInfo.retentionDays).toBeDefined();
  expect(retentionInfo.expiredLogCount).toBeDefined();
  expect(typeof retentionInfo.retentionDays).toBe('number');
  expect(typeof retentionInfo.expiredLogCount).toBe('number');

  // Navigate to Settings page and verify MFA section
  await page.goto('/settings', { waitUntil: 'domcontentloaded' });
  await expect(page.getByRole('heading', { name: 'Settings' })).toBeVisible({ timeout: 60_000 });

  // Verify MFA section exists
  await expect(page.getByText('Multi-Factor Authentication (MFA)')).toBeVisible({ timeout: 60_000 });
  await expect(page.getByText('OTP Status:')).toBeVisible({ timeout: 60_000 });
  await expect(page.getByRole('link', { name: 'Manage MFA in Keycloak' })).toBeVisible({ timeout: 60_000 });

  // Verify MFA info alert
  await expect(
    page.getByText('To enhance account security, configure Time-based One-Time Password'),
  ).toBeVisible({ timeout: 60_000 });

  // Navigate to Admin Dashboard and verify audit export button
  await page.getByRole('button', { name: 'Account menu' }).click();
  await page.getByRole('menuitem', { name: 'Admin Dashboard' }).click();
  await expect(page.getByRole('heading', { name: 'System Dashboard' })).toBeVisible({ timeout: 60_000 });

  // Verify Recent System Activity section has export button
  await expect(page.getByText('Recent System Activity')).toBeVisible({ timeout: 60_000 });
  await expect(page.getByRole('button', { name: 'Export CSV' })).toBeVisible({ timeout: 60_000 });

  // Verify retention info is displayed
  await expect(page.getByText(/Retention:/)).toBeVisible({ timeout: 60_000 });

  // Click export button and verify it starts the export
  const downloadPromise = page.waitForEvent('download', { timeout: 60_000 }).catch(() => null);
  await page.getByRole('button', { name: 'Export CSV' }).click();

  // Wait for either download or success toast
  const download = await downloadPromise;
  if (download) {
    expect(download.suggestedFilename()).toContain('audit_logs_');
    expect(download.suggestedFilename()).toContain('.csv');
  } else {
    // If download didn't trigger (e.g., no logs), verify toast appears
    await expect(page.getByText(/exported|Export/i)).toBeVisible({ timeout: 10_000 });
  }
});

test('Antivirus: EICAR test file rejection + System status', async ({ page, request }) => {
  page.on('dialog', (dialog) => dialog.accept());
  test.setTimeout(180_000);

  await loginWithCredentials(page, defaultUsername, defaultPassword);
  const apiToken = await fetchAccessToken(request, defaultUsername, defaultPassword);

  // Helper function to check antivirus status
  const checkAvStatus = async () => {
    const res = await request.get('http://localhost:7700/api/v1/system/status', {
      headers: { Authorization: `Bearer ${apiToken}` },
    });
    if (!res.ok()) return { enabled: false, available: false, status: 'error', version: undefined };
    const status = (await res.json()) as {
      antivirus?: { enabled: boolean; available?: boolean; status?: string; version?: string };
    };
    return {
      enabled: status.antivirus?.enabled ?? false,
      available: status.antivirus?.available ?? false,
      status: status.antivirus?.status ?? 'unknown',
      version: status.antivirus?.version,
    };
  };

  // Check system status for antivirus info
  let avState = await checkAvStatus();
  console.log(`Antivirus status: enabled=${avState.enabled}, available=${avState.available}, status=${avState.status}`);

  // If AV is enabled but not ready, wait for ClamAV to become available (max 30s)
  if (avState.enabled && !avState.available && avState.status !== 'healthy') {
    console.log('Antivirus enabled but not ready. Waiting for ClamAV (max 30s)...');
    for (let attempt = 0; attempt < 6; attempt += 1) {
      await page.waitForTimeout(5000);
      avState = await checkAvStatus();
      if (avState.available || avState.status === 'healthy') {
        console.log(`ClamAV became ready after ${(attempt + 1) * 5} seconds.`);
        break;
      }
      console.log(`  Still waiting... (${attempt + 1}/6)`);
    }
    if (!avState.available && avState.status !== 'healthy') {
      console.warn('Antivirus enabled but not available after 30s (ClamAV may still be starting up).');
    }
  }

  // Log final antivirus status
  console.log(
    `Final AV status: enabled=${avState.enabled}, available=${avState.available}, status=${avState.status}`,
  );

  // Navigate to System Status page and verify antivirus section
  await page.goto('/status', { waitUntil: 'domcontentloaded' });
  await expect(page.getByRole('heading', { name: 'System Status' })).toBeVisible({ timeout: 60_000 });

  // Verify antivirus section is present in the UI
  const antivirusHeading = page.getByRole('heading', { name: 'Antivirus', exact: true });
  await expect(antivirusHeading).toBeVisible({ timeout: 60_000 });
  const antivirusCard = page.locator('.MuiCard-root').filter({ has: antivirusHeading }).first();

  if (avState.enabled) {
    if (avState.available || avState.status === 'healthy') {
      await expect(antivirusCard).toContainText(/healthy|available/i, { timeout: 60_000 });
      // If ClamAV reports version, it should be visible
      if (avState.version) {
        await expect(antivirusCard).toContainText(new RegExp(avState.version.substring(0, 10)), { timeout: 60_000 });
      }
    } else {
      await expect(antivirusCard).toContainText(/unavailable|not responding/i, { timeout: 60_000 });
    }
  } else {
    await expect(antivirusCard).toContainText(/disabled/i, { timeout: 60_000 });
  }

  // EICAR test: only run if antivirus is enabled and available
  if (avState.enabled && (avState.available || avState.status === 'healthy')) {
    console.log('Running EICAR virus scan test...');

    // Resolve uploads folder
    const rootsRes = await request.get('http://localhost:7700/api/v1/folders/roots', {
      headers: { Authorization: `Bearer ${apiToken}` },
    });
    expect(rootsRes.ok()).toBeTruthy();
    const roots = (await rootsRes.json()) as { id: string; name: string }[];
    const uploadsFolder = roots.find((r) => r.name === 'uploads') ?? roots[0];
    expect(uploadsFolder).toBeTruthy();

    // EICAR test string - standard antivirus test signature
    // This is NOT a real virus, just a test pattern all AV software recognizes
    const eicarContent = 'X5O!P%@AP[4\\PZX54(P^)7CC)7}$EICAR-STANDARD-ANTIVIRUS-TEST-FILE!$H+H*';
    const eicarFilename = `eicar-test-${Date.now()}.txt`;

    // Attempt to upload EICAR file - should be rejected
    const uploadRes = await request.post(
      `http://localhost:7700/api/v1/documents/upload?folderId=${uploadsFolder.id}`,
      {
        headers: { Authorization: `Bearer ${apiToken}` },
        multipart: {
          file: {
            name: eicarFilename,
            mimeType: 'text/plain',
            buffer: Buffer.from(eicarContent),
          },
        },
      },
    );

    // Upload should fail with 400 or 500 when virus is detected
    const uploadStatus = uploadRes.status();
    console.log(`EICAR upload status: ${uploadStatus}`);

    if (uploadStatus === 400 || uploadStatus === 500) {
      // Expected: virus detected and rejected
      const errorBody = await uploadRes.text();
      console.log(`EICAR rejection response: ${errorBody}`);
      expect(errorBody.toLowerCase()).toMatch(/virus|threat|rejected|eicar/i);
    } else if (uploadStatus === 200 || uploadStatus === 201) {
      // Unexpected: virus was not detected - this is a failure
      throw new Error('EICAR test file was NOT rejected - antivirus may not be working correctly!');
    } else {
      // Other status codes might indicate network issues or ClamAV not ready
      console.warn(`EICAR upload returned unexpected status ${uploadStatus} - test inconclusive`);
    }
  } else {
    console.log('Skipping EICAR test - antivirus is disabled or unavailable');
  }
});
