// SavedSearchesPage (/saved-searches) tests.
//
// Smart folder notes (from source):
// - SavedSearch has no `isSmart` flag. Smart folder is a destination you CREATE
//   from a saved search via the per-row CreateNewFolder IconButton.
// - Page toolbar has Import JSON / Export JSON / Refresh — no "Create" button.
// - Smart-folder dialog has "Folder Name" and "Description (optional)" TextFields.
// - On successful create, page calls navigate('/browse/{folder.id}').
import { expect, test } from '@playwright/test';
import { seedBypassSessionE2E } from './helpers/login';
import { mockKeycloakUnreachable } from './helpers/keycloakMock';

const SAVED_SEARCHES = [
  {
    id: 'ss-1',
    userId: 'admin',
    name: 'All PDF Contracts',
    queryParams: { contentType: 'application/pdf', name: 'contract' },
    pinned: false,
    createdAt: '2026-04-20T10:00:00Z',
  },
  {
    id: 'ss-2',
    userId: 'admin',
    name: 'Recent Invoices',
    queryParams: { name: 'invoice', modifiedFrom: '2026-01-01' },
    pinned: true,
    createdAt: '2026-04-18T09:00:00Z',
  },
];

const EXECUTE_RESPONSE = {
  results: {
    content: [
      {
        id: 'node-1',
        name: 'contract-draft.pdf',
        path: '/root/contracts',
        nodeType: 'cm:content',
        score: 1.0,
      },
    ],
  },
  facets: {},
  totalHits: 1,
  queryTime: 12,
};

const SMART_FOLDER_RESPONSE = {
  id: 'sf-abc',
  name: 'PDF Contracts Folder',
  path: '/root/smart-folders/PDF Contracts Folder',
  parentId: 'root',
  smart: true,
  queryCriteria: { contentType: 'application/pdf', name: 'contract' },
};

test('shows saved searches list with search names', async ({ page }) => {
  test.setTimeout(60_000);
  await mockKeycloakUnreachable(page);
  await seedBypassSessionE2E(page, 'admin', 'e2e-token');

  await page.route('**/api/v1/search/saved**', async (route) => {
    const url = route.request().url();
    // Let execute/smart-folder sub-path calls through; only handle the list
    if (url.includes('/execute') || url.includes('/smart-folder') || url.includes('/pin')) {
      await route.continue();
      return;
    }
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify(SAVED_SEARCHES),
    });
  });

  await page.goto('/saved-searches');

  // Page heading
  await expect(page.getByText('Saved Searches')).toBeVisible();

  // Both saved search names must be visible in the DataGrid
  await expect(page.getByText('All PDF Contracts')).toBeVisible();
  await expect(page.getByText('Recent Invoices')).toBeVisible();

  // Toolbar buttons
  await expect(page.getByRole('button', { name: /Import JSON/i })).toBeVisible();
  await expect(page.getByRole('button', { name: /Export JSON/i })).toBeVisible();
  await expect(page.getByRole('button', { name: /Refresh/i })).toBeVisible();
});

test('run saved search navigates to /search-results', async ({ page }) => {
  test.setTimeout(60_000);
  await mockKeycloakUnreachable(page);
  await seedBypassSessionE2E(page, 'admin', 'e2e-token');

  await page.route('**/api/v1/search/saved/ss-1/execute', async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify(EXECUTE_RESPONSE),
    });
  });

  await page.route('**/api/v1/search/saved**', async (route) => {
    const url = route.request().url();
    if (url.includes('/execute') || url.includes('/smart-folder') || url.includes('/pin')) {
      await route.continue();
      return;
    }
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify(SAVED_SEARCHES),
    });
  });

  await page.goto('/saved-searches');

  // Wait for grid to populate
  await expect(page.getByText('All PDF Contracts')).toBeVisible();

  // Click the per-row "Run saved search" IconButton (PlayArrow icon)
  const runBtn = page.getByRole('button', {
    name: 'Run saved search All PDF Contracts',
  });
  await expect(runBtn).toBeVisible();
  await runBtn.click();

  // After execution the app navigates to /search-results
  await page.waitForURL(/\/search-results/, { timeout: 30_000 });
});

test('create smart folder dialog shows Folder Name and Description fields', async ({ page }) => {
  test.setTimeout(60_000);
  await mockKeycloakUnreachable(page);
  await seedBypassSessionE2E(page, 'admin', 'e2e-token');

  await page.route('**/api/v1/search/saved**', async (route) => {
    const url = route.request().url();
    if (url.includes('/execute') || url.includes('/smart-folder') || url.includes('/pin')) {
      await route.continue();
      return;
    }
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify(SAVED_SEARCHES),
    });
  });

  await page.goto('/saved-searches');
  await expect(page.getByText('All PDF Contracts')).toBeVisible();

  // Click the per-row CreateNewFolder IconButton
  const createSmartFolderBtn = page.getByRole('button', {
    name: 'Create smart folder from saved search All PDF Contracts',
  });
  await expect(createSmartFolderBtn).toBeVisible();
  await createSmartFolderBtn.click();

  // Dialog should open — match by dialog role name to avoid ambiguity with the submit button text
  await expect(page.getByRole('dialog', { name: 'Create Smart Folder' })).toBeVisible();

  // "Folder Name" field pre-filled with the saved search name
  await expect(page.getByLabel('Folder Name')).toBeVisible();

  // "Description (optional)" field is present
  await expect(page.getByLabel('Description (optional)')).toBeVisible();

  // Submit button is present (not yet submitting)
  await expect(page.getByRole('button', { name: 'Create Smart Folder' })).toBeVisible();
});

test('submitting smart folder dialog navigates to the new folder', async ({ page }) => {
  test.setTimeout(60_000);
  await mockKeycloakUnreachable(page);
  await seedBypassSessionE2E(page, 'admin', 'e2e-token');

  // Catch-all registered FIRST (lowest priority in LIFO). Passes /smart-folder requests
  // to route.fallback() so the specific handler below intercepts them.
  await page.route('**/api/v1/search/saved**', async (route) => {
    const url = route.request().url();
    if (url.includes('/execute') || url.includes('/smart-folder') || url.includes('/pin')) {
      await route.fallback();
      return;
    }
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify(SAVED_SEARCHES),
    });
  });

  // Specific smart-folder POST — registered LAST (highest priority in LIFO)
  await page.route('**/api/v1/search/saved/ss-1/smart-folder', async (route) => {
    await route.fulfill({
      status: 201,
      contentType: 'application/json',
      body: JSON.stringify(SMART_FOLDER_RESPONSE),
    });
  });

  await page.goto('/saved-searches');
  await expect(page.getByText('All PDF Contracts')).toBeVisible();

  // Open the smart-folder creation dialog from the first row
  await page.getByRole('button', {
    name: 'Create smart folder from saved search All PDF Contracts',
  }).click();

  await expect(page.getByRole('dialog')).toBeVisible();

  // Clear the pre-filled name and type the desired folder name
  const folderNameField = page.getByLabel('Folder Name');
  await folderNameField.clear();
  await folderNameField.fill('PDF Contracts Folder');

  // Submit
  await page.getByRole('button', { name: 'Create Smart Folder' }).click();

  // After successful creation the page navigates to /browse/{folder.id}
  await page.waitForURL(/\/browse\/sf-abc/, { timeout: 30_000 });
});
