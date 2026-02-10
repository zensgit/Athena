import { test, expect } from '@playwright/test';
import {
  fetchAccessToken,
  getRootFolderId,
  resolveApiUrl,
  waitForApiReady,
  waitForSearchIndex,
} from './helpers/api';
import { gotoWithAuthE2E } from './helpers/login';

const defaultUsername = process.env.ECM_E2E_USERNAME || 'admin';
const defaultPassword = process.env.ECM_E2E_PASSWORD || 'admin';

test.describe('Search Autocomplete Suggestions', () => {
  test('Quick search shows suggestions and selecting one searches the document', async ({ page, request }) => {
    const apiUrl = resolveApiUrl();
    await waitForApiReady(request, { apiUrl });

    const token = await fetchAccessToken(request, defaultUsername, defaultPassword);
    const rootId = await getRootFolderId(request, token, { apiUrl });

    const filename = `ui-e2e-autocomplete-${Date.now()}.txt`;

    const uploadRes = await request.post(`${apiUrl}/api/v1/documents/upload`, {
      params: { folderId: rootId },
      headers: { Authorization: `Bearer ${token}` },
      multipart: {
        file: { name: filename, mimeType: 'text/plain', buffer: Buffer.from(`hello ${filename}`) },
      },
    });
    expect(uploadRes.ok()).toBeTruthy();

    // Ensure search index (and therefore suggestions) can see the uploaded document.
    await waitForSearchIndex(request, filename, token, { apiUrl, maxAttempts: 40, delayMs: 1500 });

    await gotoWithAuthE2E(page, '/search-results', defaultUsername, defaultPassword, { token });

    const prefix = filename.slice(0, 16);
    const quickInput = page.getByPlaceholder('Quick search by name...');
    await quickInput.fill(prefix);

    const option = page.getByRole('option', { name: filename });
    await expect(option).toBeVisible({ timeout: 30_000 });
    await option.click();

    // Selecting an option updates the input value and triggers the debounced search.
    await expect(quickInput).toHaveValue(filename, { timeout: 30_000 });
    await expect(page.getByRole('heading', { name: filename })).toBeVisible({ timeout: 60_000 });
  });
});
