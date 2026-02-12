/* eslint-disable testing-library/prefer-screen-queries */
import { expect, test } from '@playwright/test';
import { fetchAccessToken, resolveApiUrl, waitForApiReady } from './helpers/api';
import { gotoWithAuthE2E } from './helpers/login';

const defaultUsername = process.env.ECM_E2E_USERNAME || 'admin';
const defaultPassword = process.env.ECM_E2E_PASSWORD || 'admin';

test.describe('Search dialog active criteria summary', () => {
  test('shows active criteria chips for non-empty advanced search filters', async ({ page, request }) => {
    test.setTimeout(180_000);

    const apiUrl = resolveApiUrl();
    await waitForApiReady(request, { apiUrl });
    const token = await fetchAccessToken(request, defaultUsername, defaultPassword);

    await gotoWithAuthE2E(page, '/browse/root', defaultUsername, defaultPassword, { token });
    await page.getByRole('button', { name: 'Search', exact: true }).click();

    const dialog = page.getByRole('dialog').filter({ hasText: 'Advanced Search' });
    await expect(dialog).toBeVisible({ timeout: 60_000 });

    await dialog.getByLabel('Name contains').fill('summary-check');
    await dialog.getByLabel('Preview Status').click();
    await page.getByRole('option', { name: 'Failed', exact: true }).click();
    await page.keyboard.press('Escape');

    await dialog.getByRole('button', { name: 'Aspects' }).click();
    await dialog.getByRole('checkbox', { name: 'Versionable' }).check();

    const summary = dialog.getByTestId('active-criteria-summary');
    await expect(summary).toBeVisible();
    await expect(summary).toContainText('Active Criteria');
    await expect(summary).toContainText('Name: summary-check');
    await expect(summary).toContainText('Preview: Failed');
    await expect(summary).toContainText('Aspect: Versionable');
  });

  test('prefills current quick search when opening advanced from search results', async ({ page, request }) => {
    test.setTimeout(180_000);

    const apiUrl = resolveApiUrl();
    await waitForApiReady(request, { apiUrl });
    const token = await fetchAccessToken(request, defaultUsername, defaultPassword);

    const query = `e2e-advanced-prefill-${Date.now()}`;

    await gotoWithAuthE2E(page, '/search-results', defaultUsername, defaultPassword, { token });

    const quickSearchInput = page.getByPlaceholder('Quick search by name...');
    await expect(quickSearchInput).toBeVisible({ timeout: 60_000 });
    await quickSearchInput.fill(query);
    await quickSearchInput.press('Enter');

    await page.getByRole('button', { name: 'Advanced' }).click();
    const dialog = page.getByRole('dialog').filter({ hasText: 'Advanced Search' });
    await expect(dialog).toBeVisible({ timeout: 60_000 });

    await expect(dialog.getByLabel('Name contains')).toHaveValue(query);
    await expect(dialog.getByTestId('active-criteria-summary')).toContainText(`Name: ${query}`);
  });
});
