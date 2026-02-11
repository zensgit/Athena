import { expect, test } from '@playwright/test';
import { promises as fs } from 'fs';
import path from 'path';
import {
  fetchAccessToken,
  resolveApiUrl,
  waitForApiReady,
} from './helpers/api';
import { gotoWithAuthE2E } from './helpers/login';

const defaultUsername = process.env.ECM_E2E_USERNAME || 'admin';
const defaultPassword = process.env.ECM_E2E_PASSWORD || 'admin';

test.describe('Saved Search Import Export', () => {
  test('Saved searches can be exported and imported back from JSON', async ({ page, request }) => {
    test.setTimeout(300_000);

    const apiUrl = resolveApiUrl();
    await waitForApiReady(request, { apiUrl });

    const token = await fetchAccessToken(request, defaultUsername, defaultPassword);
    const targetName = `e2e-import-export-${Date.now()}`;
    const targetQueryParams = {
      query: `q-${targetName}`,
      filters: { createdBy: 'admin' },
      highlightEnabled: true,
      facetFields: ['mimeType', 'createdBy'],
      pageable: { page: 0, size: 50 },
    };

    const createRes = await request.post(`${apiUrl}/api/v1/search/saved`, {
      headers: { Authorization: `Bearer ${token}` },
      data: { name: targetName, queryParams: targetQueryParams },
    });
    expect(createRes.ok()).toBeTruthy();
    const created = (await createRes.json()) as { id?: string };
    expect(created.id).toBeTruthy();

    await gotoWithAuthE2E(page, '/saved-searches', defaultUsername, defaultPassword, { token });
    await expect(page.getByText('Saved Searches')).toBeVisible({ timeout: 60_000 });
    await expect(page.getByText(targetName)).toBeVisible({ timeout: 60_000 });

    const [download] = await Promise.all([
      page.waitForEvent('download'),
      page.getByRole('button', { name: 'Export JSON' }).click(),
    ]);
    const downloadPath = await download.path();
    expect(downloadPath).toBeTruthy();

    const exportedRaw = await fs.readFile(downloadPath as string, 'utf-8');
    const exported = JSON.parse(exportedRaw) as { savedSearches?: Array<{ name?: string; queryParams?: Record<string, any> }> };
    const exportedItem = (exported.savedSearches || []).find((item) => item.name === targetName);
    expect(exportedItem).toBeTruthy();
    expect(exportedItem?.queryParams?.query).toBe(targetQueryParams.query);

    await request.delete(`${apiUrl}/api/v1/search/saved/${created.id}`, {
      headers: { Authorization: `Bearer ${token}` },
    });

    const importDir = path.join(process.cwd(), 'output', 'playwright', 'saved-search-import-export');
    await fs.mkdir(importDir, { recursive: true });
    const importPath = path.join(importDir, `${targetName}.json`);
    await fs.writeFile(
      importPath,
      JSON.stringify({
        version: 1,
        exportedAt: new Date().toISOString(),
        savedSearches: [exportedItem],
      }),
      'utf-8'
    );

    await page.getByTestId('saved-search-import-input').setInputFiles(importPath);
    await expect(page.getByText('Import complete: 1 imported, 0 skipped, 0 failed')).toBeVisible({ timeout: 60_000 });
    await expect(page.getByText(targetName)).toBeVisible({ timeout: 60_000 });

    await page.getByTestId('saved-search-import-input').setInputFiles(importPath);
    await expect(page.getByText('Import complete: 0 imported, 1 skipped, 0 failed')).toBeVisible({ timeout: 60_000 });

    const listRes = await request.get(`${apiUrl}/api/v1/search/saved`, {
      headers: { Authorization: `Bearer ${token}` },
    });
    expect(listRes.ok()).toBeTruthy();
    const all = (await listRes.json()) as Array<{ id: string; name: string }>;
    const imported = all.find((item) => item.name === targetName);
    if (imported?.id) {
      await request.delete(`${apiUrl}/api/v1/search/saved/${imported.id}`, {
        headers: { Authorization: `Bearer ${token}` },
      });
    }
  });
});
