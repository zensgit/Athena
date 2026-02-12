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
    } finally {
      await page.unroute('**/api/v1/search/saved').catch(() => null);
    }
  });
});
