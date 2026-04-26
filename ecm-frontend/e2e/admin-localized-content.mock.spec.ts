import { expect, test } from '@playwright/test';
import { seedBypassSessionE2E } from './helpers/login';
import { mockKeycloakUnreachable } from './helpers/keycloakMock';

const LOCALIZATIONS = [
  {
    id: 'lc-1',
    nodeId: 'node-uuid-1',
    locale: 'en',
    title: 'Annual Report',
    description: 'FY2025 Annual Report',
    createdBy: 'admin',
    createdDate: '2026-04-01T00:00:00',
    lastModifiedDate: '2026-04-01T00:00:00',
  },
  {
    id: 'lc-2',
    nodeId: 'node-uuid-1',
    locale: 'zh',
    title: 'Annual Report CN',
    description: 'FY2025 Annual Report CN',
    createdBy: 'admin',
    createdDate: '2026-04-01T00:00:00',
    lastModifiedDate: '2026-04-01T00:00:00',
  },
];

test('node ID lookup shows localizations table', async ({ page }) => {
  test.setTimeout(60_000);
  await mockKeycloakUnreachable(page);
  await seedBypassSessionE2E(page, 'admin', 'e2e-token');

  await page.route('**/api/v1/nodes/*/localizations', async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify(LOCALIZATIONS),
    });
  });
  await page.route('**/api/v1/nodes/*/localizations/**', async (route) => {
    if (route.request().method() === 'PUT') {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify(LOCALIZATIONS[0]),
      });
    } else if (route.request().method() === 'DELETE') {
      await route.fulfill({ status: 204 });
    } else {
      await route.continue();
    }
  });

  await page.goto('/admin/localized-content');

  // Fill in the Node ID input
  await page.getByLabel('Node ID (UUID)').fill('node-uuid-1');

  // Click Load (exact: true prevents partial match against "Load saved search ..." buttons in MainLayout)
  await page.getByRole('button', { name: 'Load', exact: true }).click();

  // Table should appear with locale chips
  await expect(page.getByText('en', { exact: true })).toBeVisible();
  await expect(page.getByText('zh', { exact: true })).toBeVisible();

  // Title for the first localization should be visible (exact: true avoids matching FY2025 Annual Report, Annual Report CN, etc.)
  await expect(page.getByText('Annual Report', { exact: true })).toBeVisible();
});

test('add locale dialog opens', async ({ page }) => {
  test.setTimeout(60_000);
  await mockKeycloakUnreachable(page);
  await seedBypassSessionE2E(page, 'admin', 'e2e-token');

  await page.route('**/api/v1/nodes/*/localizations', async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify(LOCALIZATIONS),
    });
  });
  await page.route('**/api/v1/nodes/*/localizations/**', async (route) => {
    if (route.request().method() === 'PUT') {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify(LOCALIZATIONS[0]),
      });
    } else if (route.request().method() === 'DELETE') {
      await route.fulfill({ status: 204 });
    } else {
      await route.continue();
    }
  });

  await page.goto('/admin/localized-content');

  // Load a node first so the Add Locale button becomes available
  await page.getByLabel('Node ID (UUID)').fill('node-uuid-1');
  await page.getByRole('button', { name: 'Load', exact: true }).click();
  await expect(page.getByText('Annual Report', { exact: true })).toBeVisible();

  // Click Add Locale
  await page.getByRole('button', { name: 'Add Locale' }).click();

  // The AddLocaleDialog should appear with its title
  await expect(page.getByRole('dialog', { name: 'Add Localization' })).toBeVisible();

  // The locale selector should be visible inside the dialog
  await expect(page.getByLabel('Locale')).toBeVisible();
});

test('inline delete confirm appears', async ({ page }) => {
  test.setTimeout(60_000);
  await mockKeycloakUnreachable(page);
  await seedBypassSessionE2E(page, 'admin', 'e2e-token');

  await page.route('**/api/v1/nodes/*/localizations', async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify(LOCALIZATIONS),
    });
  });
  await page.route('**/api/v1/nodes/*/localizations/**', async (route) => {
    if (route.request().method() === 'PUT') {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify(LOCALIZATIONS[0]),
      });
    } else if (route.request().method() === 'DELETE') {
      await route.fulfill({ status: 204 });
    } else {
      await route.continue();
    }
  });

  await page.goto('/admin/localized-content');

  // Load node
  await page.getByLabel('Node ID (UUID)').fill('node-uuid-1');
  await page.getByRole('button', { name: 'Load', exact: true }).click();
  await expect(page.getByText('Annual Report', { exact: true })).toBeVisible();

  // Click the delete icon on the first row (IconButton has aria-label="Delete")
  await page.getByRole('button', { name: 'Delete' }).first().click();

  // LocalizedContentPage shows inline "Confirm delete?" text and Yes/No buttons
  await expect(page.getByText('Confirm delete?')).toBeVisible();
  await expect(page.getByRole('button', { name: 'Yes', exact: true })).toBeVisible();
  await expect(page.getByRole('button', { name: 'No', exact: true })).toBeVisible();
});

test('shows empty state before node loaded', async ({ page }) => {
  test.setTimeout(60_000);
  await mockKeycloakUnreachable(page);
  await seedBypassSessionE2E(page, 'admin', 'e2e-token');

  await page.route('**/api/v1/nodes/*/localizations', async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify(LOCALIZATIONS),
    });
  });

  await page.goto('/admin/localized-content');

  // Before any node is loaded:
  // - The lookup form (Node ID input) is visible
  await expect(page.getByLabel('Node ID (UUID)')).toBeVisible();

  // - No table headers should be visible (table only renders when activeNodeId is set)
  await expect(page.getByRole('columnheader', { name: 'Locale' })).not.toBeVisible();

  // - The "Add Locale" button is not rendered (requires activeNodeId)
  await expect(page.getByRole('button', { name: 'Add Locale' })).not.toBeVisible();
});
