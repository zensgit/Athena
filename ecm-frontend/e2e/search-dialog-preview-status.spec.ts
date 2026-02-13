/* eslint-disable testing-library/prefer-screen-queries */
import { expect, test } from '@playwright/test';
import {
  fetchAccessToken,
  resolveApiUrl,
  waitForApiReady,
} from './helpers/api';
import { gotoWithAuthE2E } from './helpers/login';

const apiUrl = resolveApiUrl();
const defaultUsername = process.env.ECM_E2E_USERNAME || 'admin';
const defaultPassword = process.env.ECM_E2E_PASSWORD || 'admin';

async function suppressDevServerOverlay(page: import('@playwright/test').Page) {
  await page.addInitScript(() => {
    const hideOverlay = () => {
      const overlay = document.getElementById('webpack-dev-server-client-overlay');
      if (overlay) {
        (overlay as HTMLElement).style.pointerEvents = 'none';
        (overlay as HTMLElement).style.display = 'none';
      }
      const iframe = document.querySelector('iframe#webpack-dev-server-client-overlay');
      if (iframe) {
        (iframe as HTMLElement).style.pointerEvents = 'none';
        (iframe as HTMLElement).style.display = 'none';
      }
    };
    const observer = new MutationObserver(() => hideOverlay());
    observer.observe(document.documentElement, { childList: true, subtree: true });
    window.addEventListener('load', hideOverlay);
    hideOverlay();
  });
}

async function hideDevServerOverlay(page: import('@playwright/test').Page) {
  await page.addStyleTag({
    content: `
      #webpack-dev-server-client-overlay,
      iframe#webpack-dev-server-client-overlay {
        display: none !important;
        pointer-events: none !important;
      }
    `,
  }).catch(() => null);
  await page.evaluate(() => {
    const overlay = document.getElementById('webpack-dev-server-client-overlay');
    if (overlay && overlay.parentElement) {
      overlay.parentElement.removeChild(overlay);
    }
    const iframe = document.querySelector('iframe#webpack-dev-server-client-overlay');
    if (iframe && iframe.parentElement) {
      iframe.parentElement.removeChild(iframe);
    }
  }).catch(() => null);
}

test.describe('Search dialog preview status filter', () => {
  test('advanced search dialog sends preview status and persists it in saved search', async ({ page, request }) => {
    test.setTimeout(240_000);
    await waitForApiReady(request, { apiUrl });
    await suppressDevServerOverlay(page);

    const token = await fetchAccessToken(request, defaultUsername, defaultPassword);
    const query = `e2e-dialog-preview-${Date.now()}`;
    const savedName = `e2e-dialog-preview-saved-${Date.now()}`;
    let capturedPreviewStatus = '';
    let capturedSavedPreviewStatuses: string[] = [];

    await page.route('**/api/v1/search**', async (route) => {
      const requestUrl = route.request().url();
      const parsed = new URL(requestUrl);
      if (parsed.pathname !== '/api/v1/search') {
        await route.continue();
        return;
      }
      if ((parsed.searchParams.get('q') || '') !== query) {
        await route.continue();
        return;
      }
      capturedPreviewStatus = parsed.searchParams.get('previewStatus') || '';
      const size = Number(parsed.searchParams.get('size') || 20);
      const pageIndex = Number(parsed.searchParams.get('page') || 0);
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          content: [],
          totalElements: 0,
          totalPages: 0,
          size,
          number: pageIndex,
          first: true,
          last: true,
          empty: true,
        }),
      });
    });
    await page.route('**/api/v1/search/saved', async (route) => {
      if (route.request().method() !== 'POST') {
        await route.continue();
        return;
      }
      const payload = route.request().postDataJSON() as {
        queryParams?: { filters?: { previewStatuses?: string[] } };
      } | null;
      capturedSavedPreviewStatuses = payload?.queryParams?.filters?.previewStatuses || [];
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          id: `e2e-saved-${Date.now()}`,
          userId: 'admin',
          name: savedName,
          queryParams: payload?.queryParams || {},
          createdAt: new Date().toISOString(),
        }),
      });
    });

    try {
      await gotoWithAuthE2E(page, '/browse/root', defaultUsername, defaultPassword, { token });
      await hideDevServerOverlay(page);

      await page.getByRole('button', { name: 'Search', exact: true }).click();
      const searchDialog = page.getByRole('dialog').filter({ hasText: 'Advanced Search' });
      await expect(searchDialog).toBeVisible({ timeout: 60_000 });
      const searchButton = searchDialog.getByRole('button', { name: 'Search', exact: true });
      const saveSearchButton = searchDialog.getByRole('button', { name: 'Save Search', exact: true });
      const criteriaHint = searchDialog.getByText('Add at least one search criterion to enable Save Search and Search.');
      await searchDialog.getByRole('button', { name: 'Clear All', exact: true }).click();
      await expect(searchButton).toBeDisabled();
      await expect(saveSearchButton).toBeDisabled();
      await expect(criteriaHint).toBeVisible();

      await searchDialog.getByLabel('Name contains').fill(query);
      await searchDialog.getByLabel('Preview Status').click();
      await page.getByRole('option', { name: 'Failed', exact: true }).click();
      await page.keyboard.press('Escape');
      await expect(searchButton).toBeEnabled();
      await expect(saveSearchButton).toBeEnabled();
      await expect(criteriaHint).toHaveCount(0);

      await saveSearchButton.click();
      const saveDialog = page.getByRole('dialog').filter({ hasText: 'Save Search' });
      await expect(saveDialog).toBeVisible({ timeout: 30_000 });
      await saveDialog.getByLabel('Name').fill(savedName);
      await saveDialog.getByRole('button', { name: 'Save', exact: true }).click();
      await expect.poll(() => capturedSavedPreviewStatuses, { timeout: 30_000 }).toEqual(['FAILED']);

      await searchButton.click();
      await expect(page).toHaveURL(/\/search-results/, { timeout: 60_000 });
      await expect.poll(() => capturedPreviewStatus, { timeout: 60_000 }).toBe('FAILED');
    } finally {
      await page.unroute('**/api/v1/search**').catch(() => null);
      await page.unroute('**/api/v1/search/saved').catch(() => null);
    }
  });

  test('advanced search save payload keeps aspects and custom properties', async ({ page, request }) => {
    test.setTimeout(240_000);
    await waitForApiReady(request, { apiUrl });
    await suppressDevServerOverlay(page);

    const token = await fetchAccessToken(request, defaultUsername, defaultPassword);
    const savedName = `e2e-dialog-aspects-props-${Date.now()}`;
    let capturedSavedAspects: string[] = [];
    let capturedSavedProperties: Record<string, any> = {};

    await page.route('**/api/v1/search/saved', async (route) => {
      if (route.request().method() !== 'POST') {
        await route.continue();
        return;
      }
      const payload = route.request().postDataJSON() as {
        queryParams?: { filters?: { aspects?: string[]; properties?: Record<string, any> } };
      } | null;
      capturedSavedAspects = payload?.queryParams?.filters?.aspects || [];
      capturedSavedProperties = payload?.queryParams?.filters?.properties || {};
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          id: `e2e-saved-${Date.now()}`,
          userId: 'admin',
          name: savedName,
          queryParams: payload?.queryParams || {},
          createdAt: new Date().toISOString(),
        }),
      });
    });

    try {
      await gotoWithAuthE2E(page, '/browse/root', defaultUsername, defaultPassword, { token });
      await hideDevServerOverlay(page);

      await page.getByRole('button', { name: 'Search', exact: true }).click();
      const searchDialog = page.getByRole('dialog').filter({ hasText: 'Advanced Search' });
      await expect(searchDialog).toBeVisible({ timeout: 60_000 });

      await searchDialog.getByRole('button', { name: 'Aspects' }).click();
      await searchDialog.getByRole('checkbox', { name: 'Versionable' }).check();

      await searchDialog.getByRole('button', { name: 'Custom Properties' }).click();
      await searchDialog.getByLabel('Property Key').fill('mail:subject');
      await searchDialog.getByLabel('Property Value').fill('payload-test');
      await searchDialog.getByRole('button', { name: 'Add', exact: true }).click();

      const saveSearchButton = searchDialog.getByRole('button', { name: 'Save Search', exact: true });
      await expect(saveSearchButton).toBeEnabled();
      await saveSearchButton.click();

      const saveDialog = page.getByRole('dialog').filter({ hasText: 'Save Search' });
      await expect(saveDialog).toBeVisible({ timeout: 30_000 });
      await saveDialog.getByLabel('Name').fill(savedName);
      await saveDialog.getByRole('button', { name: 'Save', exact: true }).click();

      await expect.poll(() => capturedSavedAspects, { timeout: 30_000 }).toEqual(['cm:versionable']);
      await expect.poll(() => capturedSavedProperties, { timeout: 30_000 }).toEqual({
        'mail:subject': 'payload-test',
      });
    } finally {
      await page.unroute('**/api/v1/search/saved').catch(() => null);
    }
  });
});
