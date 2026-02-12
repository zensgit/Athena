/* eslint-disable testing-library/prefer-screen-queries */
import { expect, test } from '@playwright/test';
import { fetchAccessToken, resolveApiUrl, waitForApiReady } from './helpers/api';
import { gotoWithAuthE2E } from './helpers/login';

const defaultUsername = process.env.ECM_E2E_USERNAME || 'admin';
const defaultPassword = process.env.ECM_E2E_PASSWORD || 'admin';

test.describe('Saved search load to advanced search prefill', () => {
  test('load action restores preview status and folder scope', async ({ page, request }) => {
    test.setTimeout(180_000);

    const apiUrl = resolveApiUrl();
    await waitForApiReady(request, { apiUrl });
    const token = await fetchAccessToken(request, defaultUsername, defaultPassword);

    const savedSearchName = `e2e-load-prefill-${Date.now()}`;
    const query = `e2e-load-query-${Date.now()}`;
    const scopeFolderId = '00000000-0000-4000-8000-000000000123';

    await page.route('**/api/v1/search/saved', async (route) => {
      if (route.request().method() !== 'GET') {
        await route.continue();
        return;
      }
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify([
          {
            id: `saved-${Date.now()}`,
            userId: 'admin',
            name: savedSearchName,
            queryParams: {
              query,
              filters: {
                previewStatuses: ['FAILED', 'PROCESSING'],
                folderId: scopeFolderId,
                includeChildren: false,
                path: '/Documents/ShouldNotApplyWhenScoped',
                aspects: ['cm:versionable', 'cm:taggable'],
                properties: {
                  'mail:subject': 'e2e subject',
                  'mail:uid': '12345',
                },
              },
            },
            pinned: false,
            createdAt: new Date().toISOString(),
          },
        ]),
      });
    });

    try {
      await gotoWithAuthE2E(page, '/saved-searches', defaultUsername, defaultPassword, { token });
      await expect(page.getByText('Saved Searches')).toBeVisible({ timeout: 60_000 });

      await page.getByRole('button', { name: `Load saved search ${savedSearchName}` }).click();
      await expect(page.getByText('Loaded saved search into Advanced Search')).toBeVisible({ timeout: 30_000 });

      const searchDialog = page.getByRole('dialog').filter({ hasText: 'Advanced Search' });
      await expect(searchDialog).toBeVisible({ timeout: 30_000 });
      await expect(searchDialog.getByLabel('Name contains')).toHaveValue(query);
      await expect(searchDialog.getByText('Scope: This folder')).toBeVisible();

      const includeSubfoldersCheckbox = searchDialog.getByRole('checkbox', { name: 'Include subfolders' });
      await expect(includeSubfoldersCheckbox).not.toBeChecked();
      await expect(searchDialog.getByLabel('Path starts with')).toBeDisabled();

      await searchDialog.getByLabel('Preview Status').click();
      await expect(page.getByRole('option', { name: 'Failed', exact: true })).toHaveAttribute('aria-selected', 'true');
      await expect(page.getByRole('option', { name: 'Processing', exact: true })).toHaveAttribute('aria-selected', 'true');
      await page.keyboard.press('Escape');

      await searchDialog.getByRole('button', { name: 'Aspects' }).click();
      await expect(searchDialog.getByRole('checkbox', { name: 'Versionable' })).toBeChecked();
      await expect(searchDialog.getByRole('checkbox', { name: 'Taggable' })).toBeChecked();

      await searchDialog.getByRole('button', { name: 'Custom Properties' }).click();
      await expect(searchDialog.getByText('mail:subject: e2e subject')).toBeVisible();
      await expect(searchDialog.getByText('mail:uid: 12345')).toBeVisible();
    } finally {
      await page.unroute('**/api/v1/search/saved').catch(() => null);
    }
  });

  test('load action supports legacy top-level queryParams format', async ({ page, request }) => {
    test.setTimeout(180_000);

    const apiUrl = resolveApiUrl();
    await waitForApiReady(request, { apiUrl });
    const token = await fetchAccessToken(request, defaultUsername, defaultPassword);

    const savedSearchName = `e2e-load-legacy-${Date.now()}`;
    const scopeFolderId = '00000000-0000-4000-8000-000000000124';

    await page.route('**/api/v1/search/saved', async (route) => {
      if (route.request().method() !== 'GET') {
        await route.continue();
        return;
      }
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify([
          {
            id: `saved-legacy-${Date.now()}`,
            userId: 'admin',
            name: savedSearchName,
            queryParams: {
              q: 'legacy-query',
              mimeTypes: ['application/pdf'],
              createdByList: ['legacy-user'],
              aspects: ['cm:auditable'],
              properties: { 'mail:subject': 'legacy subject' },
              folderId: scopeFolderId,
              includeChildren: false,
            },
            pinned: false,
            createdAt: new Date().toISOString(),
          },
        ]),
      });
    });

    try {
      await gotoWithAuthE2E(page, '/saved-searches', defaultUsername, defaultPassword, { token });
      await expect(page.getByText('Saved Searches')).toBeVisible({ timeout: 60_000 });

      await page.getByRole('button', { name: `Load saved search ${savedSearchName}` }).click();
      const searchDialog = page.getByRole('dialog').filter({ hasText: 'Advanced Search' });
      await expect(searchDialog).toBeVisible({ timeout: 30_000 });
      await expect(searchDialog.getByLabel('Name contains')).toHaveValue('legacy-query');
      await expect(searchDialog.getByText('Scope: This folder')).toBeVisible();
      await expect(searchDialog.getByRole('checkbox', { name: 'Include subfolders' })).not.toBeChecked();

      await searchDialog.getByRole('button', { name: 'Aspects' }).click();
      await expect(searchDialog.getByRole('checkbox', { name: 'Auditable' })).toBeChecked();

      await searchDialog.getByRole('button', { name: 'Custom Properties' }).click();
      await expect(searchDialog.getByText('mail:subject: legacy subject')).toBeVisible();
    } finally {
      await page.unroute('**/api/v1/search/saved').catch(() => null);
    }
  });

  test('load action auto-expands non-basic section when only aspects are prefilled', async ({ page, request }) => {
    test.setTimeout(180_000);

    const apiUrl = resolveApiUrl();
    await waitForApiReady(request, { apiUrl });
    const token = await fetchAccessToken(request, defaultUsername, defaultPassword);

    const savedSearchName = `e2e-load-aspects-only-${Date.now()}`;

    await page.route('**/api/v1/search/saved', async (route) => {
      if (route.request().method() !== 'GET') {
        await route.continue();
        return;
      }
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify([
          {
            id: `saved-aspects-${Date.now()}`,
            userId: 'admin',
            name: savedSearchName,
            queryParams: {
              query: '',
              filters: {
                aspects: ['cm:versionable'],
              },
            },
            pinned: false,
            createdAt: new Date().toISOString(),
          },
        ]),
      });
    });

    try {
      await gotoWithAuthE2E(page, '/saved-searches', defaultUsername, defaultPassword, { token });
      await expect(page.getByText('Saved Searches')).toBeVisible({ timeout: 60_000 });

      await page.getByRole('button', { name: `Load saved search ${savedSearchName}` }).click();
      const searchDialog = page.getByRole('dialog').filter({ hasText: 'Advanced Search' });
      await expect(searchDialog).toBeVisible({ timeout: 30_000 });

      // Aspects section should be auto-expanded when no basic criteria are present.
      await expect(searchDialog.getByRole('checkbox', { name: 'Versionable' })).toBeVisible();
      await expect(searchDialog.getByRole('checkbox', { name: 'Versionable' })).toBeChecked();
    } finally {
      await page.unroute('**/api/v1/search/saved').catch(() => null);
    }
  });
});
