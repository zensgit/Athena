import { expect, test } from '@playwright/test';
import { seedBypassSessionE2E } from './helpers/login';

test('Preview diagnostics renders failures and gates retry actions (mocked API)', async ({ page }) => {
  test.setTimeout(120_000);

  const retryableId = '11111111-1111-1111-1111-111111111111';
  const unsupportedId = '22222222-2222-2222-2222-222222222222';
  const permanentId = '33333333-3333-3333-3333-333333333333';

  const retryableName = 'e2e-preview-diagnostics-retryable.pdf';
  const unsupportedName = 'e2e-preview-diagnostics-unsupported.bin';
  const permanentName = 'e2e-preview-diagnostics-permanent.pdf';

  await page.addInitScript(() => {
    // Avoid relying on system clipboard permissions in CI/local runs.
    (window as any).__copiedText = null;
    Object.defineProperty(navigator, 'clipboard', {
      value: {
        writeText: async (text: string) => {
          (window as any).__copiedText = text;
        },
      },
      configurable: true,
    });
  });

  await seedBypassSessionE2E(page, 'admin', 'e2e-token');

  await page.route('**/api/v1/folders/roots', async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify([
        {
          id: 'root-folder-id',
          name: 'Root',
          path: '/Root',
          folderType: 'SYSTEM',
          parentId: null,
          inheritPermissions: true,
          description: null,
          createdBy: 'admin',
          createdDate: new Date().toISOString(),
          lastModifiedBy: 'admin',
          lastModifiedDate: new Date().toISOString(),
        },
      ]),
    });
  });

  await page.route('**/api/v1/folders/*/contents**', async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({ content: [], totalElements: 0 }),
    });
  });

  await page.route('**/api/v1/folders/path**', async (route) => {
    const url = new URL(route.request().url());
    const requestedPath = url.searchParams.get('path');
    if (requestedPath !== '/Root/Documents/e2e-preview-diagnostics') {
      await route.fulfill({ status: 404, contentType: 'application/json', body: JSON.stringify({ message: 'Not found' }) });
      return;
    }
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        id: 'parent-folder-id',
        name: 'e2e-preview-diagnostics',
        path: '/Root/Documents/e2e-preview-diagnostics',
        folderType: 'USER',
        parentId: 'root-folder-id',
        inheritPermissions: true,
        description: null,
        createdBy: 'admin',
        createdDate: new Date().toISOString(),
        lastModifiedBy: 'admin',
        lastModifiedDate: new Date().toISOString(),
      }),
    });
  });

  await page.route('**/api/v1/nodes/parent-folder-id', async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        id: 'parent-folder-id',
        name: 'e2e-preview-diagnostics',
        path: '/Root/Documents/e2e-preview-diagnostics',
        nodeType: 'FOLDER',
        parentId: 'root-folder-id',
        size: 0,
        contentType: null,
        currentVersionLabel: null,
        correspondentId: null,
        correspondentName: null,
        properties: {},
        metadata: {},
        aspects: [],
        tags: [],
        categories: [],
        inheritPermissions: true,
        locked: false,
        lockedBy: null,
        previewStatus: null,
        previewFailureReason: null,
        previewFailureCategory: null,
        createdBy: 'admin',
        createdDate: new Date().toISOString(),
        lastModifiedBy: 'admin',
        lastModifiedDate: new Date().toISOString(),
      }),
    });
  });

  await page.route('**/api/v1/preview/diagnostics/failures**', async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify([
        {
          id: retryableId,
          name: retryableName,
          path: '/Root/Documents/e2e-preview-diagnostics/retryable.pdf',
          mimeType: 'application/pdf',
          previewStatus: 'FAILED',
          previewFailureCategory: 'TEMPORARY',
          previewFailureReason: 'Timeout contacting preview service',
          previewLastUpdated: new Date().toISOString(),
        },
        {
          id: unsupportedId,
          name: unsupportedName,
          path: '/Root/Documents/e2e-preview-diagnostics/unsupported.bin',
          mimeType: 'application/octet-stream',
          previewStatus: 'UNSUPPORTED',
          previewFailureCategory: 'UNSUPPORTED',
          previewFailureReason: 'Preview not supported for mime type application/octet-stream',
          previewLastUpdated: new Date().toISOString(),
        },
        {
          id: permanentId,
          name: permanentName,
          path: '/Root/Documents/e2e-preview-diagnostics/permanent.pdf',
          mimeType: 'application/pdf',
          previewStatus: 'FAILED',
          previewFailureCategory: 'PERMANENT',
          previewFailureReason: 'Error generating preview: Missing root object specification in trailer.',
          previewLastUpdated: new Date().toISOString(),
        },
      ]),
    });
  });

  await page.route('**/api/v1/search/faceted', async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        results: { content: [], totalElements: 0, totalPages: 0 },
        facets: { mimeType: [], createdBy: [], tags: [], categories: [], previewStatus: [] },
      }),
    });
  });

  await page.route('**/api/v1/documents/*/preview/queue**', async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({ status: 'QUEUED' }),
    });
  });

  // When running against a static build server (no SPA rewrite), avoid deep links.
  // Navigate from the app root instead.
  await page.goto('/', { waitUntil: 'domcontentloaded' });
  await expect(page.getByRole('button', { name: 'Account menu' })).toBeVisible();
  await page.getByRole('button', { name: 'Account menu' }).click();
  await page.getByRole('menuitem', { name: 'Preview Diagnostics' }).click();

  await expect(page.getByRole('heading', { name: 'Preview Diagnostics' })).toBeVisible();

  await expect(page.getByText('Total 3')).toBeVisible();
  await expect(page.getByText('Retryable 1')).toBeVisible();
  await expect(page.getByText('Permanent 1')).toBeVisible();
  await expect(page.getByText('Unsupported 1')).toBeVisible();

  const filter = page.getByPlaceholder('Filter by name, path, mime type...');
  await filter.fill(retryableName);

  const retryableRow = page.locator('tr', { hasText: retryableName });
  await expect(retryableRow).toBeVisible();

  await retryableRow.getByRole('button', { name: 'Copy document id' }).click();
  await expect(page.getByText('Document id copied')).toBeVisible();
  const copied = await page.evaluate(() => (window as any).__copiedText);
  expect(copied).toBe(retryableId);

  // Tooltip wrapper includes a <span aria-label="Retry preview"> plus the real button; use role=button.
  const retryButton = retryableRow.getByRole('button', { name: 'Retry preview' });
  await expect(retryButton).toBeEnabled();
  await retryButton.click();
  await expect(page.getByText('Preview retry queued')).toBeVisible();

  await retryableRow.getByRole('button', { name: 'Open in Advanced Search' }).click();
  await expect(page).toHaveURL(/\/search\?/);
  await expect(page.getByRole('heading', { name: 'Advanced Search' })).toBeVisible();
  await expect(page).toHaveURL(new RegExp(`previewStatus=FAILED`));
  await expect(page).toHaveURL(new RegExp(`q=${encodeURIComponent(retryableName)}`));
  await page.goBack();
  await expect(page.getByRole('heading', { name: 'Preview Diagnostics' })).toBeVisible();

  await filter.fill(unsupportedName);
  const unsupportedRow = page.locator('tr', { hasText: unsupportedName });
  await expect(unsupportedRow).toBeVisible();
  await expect(unsupportedRow.getByRole('button', { name: 'Retry preview' })).toBeDisabled();
  await expect(unsupportedRow.getByRole('button', { name: 'Force rebuild preview' })).toBeDisabled();

  await filter.fill(retryableName);
  await expect(retryableRow.getByRole('button', { name: 'Open parent folder' })).toBeEnabled();
  await retryableRow.getByRole('button', { name: 'Open parent folder' }).click();
  await expect(page).toHaveURL(/\/browse\/parent-folder-id$/);
});
