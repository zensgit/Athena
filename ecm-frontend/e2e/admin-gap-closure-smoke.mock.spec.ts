import { expect, test } from '@playwright/test';
import { seedBypassSessionE2E } from './helpers/login';
import { mockKeycloakUnreachable } from './helpers/keycloakMock';

// Smoke tests for all new admin pages added during the Athena ECM gap closure work.
// Each test mounts the page with mocked API responses and asserts the primary heading
// or a key always-visible UI element. No deep interaction — lightweight mount checks only.

test('legal holds page renders heading', async ({ page }) => {
  test.setTimeout(60_000);
  await mockKeycloakUnreachable(page);
  await seedBypassSessionE2E(page, 'admin', 'e2e-token');

  await page.route('**/api/v1/legal-holds', async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify([]),
    });
  });

  await page.goto('/admin/legal-holds');

  await expect(page.getByRole('heading', { name: 'Legal Holds' })).toBeVisible();
});

test('ldap admin page renders action buttons', async ({ page }) => {
  test.setTimeout(60_000);
  await mockKeycloakUnreachable(page);
  await seedBypassSessionE2E(page, 'admin', 'e2e-token');

  // LdapSyncPage does not call any endpoint on mount — buttons render immediately
  await page.route('**/api/v1/admin/ldap/test-connection', async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({ reachable: true, message: 'ok' }),
    });
  });
  await page.route('**/api/v1/admin/ldap/sync', async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({}),
    });
  });

  await page.goto('/admin/ldap');

  await expect(page.getByRole('button', { name: 'Test Connection' })).toBeVisible();
  await expect(page.getByRole('button', { name: 'Sync Now' })).toBeVisible();
});

test('disposition schedules page renders heading', async ({ page }) => {
  test.setTimeout(60_000);
  await mockKeycloakUnreachable(page);
  await seedBypassSessionE2E(page, 'admin', 'e2e-token');

  await page.route('**/api/v1/disposition-schedules', async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify([]),
    });
  });

  await page.goto('/admin/disposition-schedules');

  await expect(page.getByRole('heading', { name: 'Disposition Schedules' })).toBeVisible();
});

test('multilingual content page renders node id field', async ({ page }) => {
  test.setTimeout(60_000);
  await mockKeycloakUnreachable(page);
  await seedBypassSessionE2E(page, 'admin', 'e2e-token');

  // LocalizedContentPage makes no API call on mount — field renders immediately
  await page.goto('/admin/localized-content');

  await expect(page.getByLabel('Node ID (UUID)')).toBeVisible();
});

test('notifications page renders heading', async ({ page }) => {
  test.setTimeout(60_000);
  await mockKeycloakUnreachable(page);
  await seedBypassSessionE2E(page, 'admin', 'e2e-token');

  await page.route('**/api/v1/notifications/unread-count**', async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({ count: 0 }),
    });
  });
  await page.route('**/api/v1/notifications/unread**', async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({ content: [], totalElements: 0, totalPages: 0, number: 0, size: 20 }),
    });
  });

  await page.goto('/notifications');

  // Typography variant="h5" heading renders unconditionally
  await expect(page.getByRole('heading', { name: 'Notifications' })).toBeVisible();
});

test('sites page renders heading', async ({ page }) => {
  test.setTimeout(60_000);
  await mockKeycloakUnreachable(page);
  await seedBypassSessionE2E(page, 'admin', 'e2e-token');

  await page.route('**/api/v1/sites', async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify([]),
    });
  });
  await page.route('**/api/v1/followings', async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify([]),
    });
  });

  await page.goto('/sites');

  // Typography variant="h5" heading renders unconditionally
  await expect(page.getByRole('heading', { name: 'Sites' })).toBeVisible();
});

test('content models page renders heading', async ({ page }) => {
  test.setTimeout(60_000);
  await mockKeycloakUnreachable(page);
  await seedBypassSessionE2E(page, 'admin', 'e2e-token');

  await page.route('**/api/v1/content-models', async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify([]),
    });
  });
  await page.route('**/api/v1/dictionary/types', async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify([]),
    });
  });
  await page.route('**/api/v1/dictionary/aspects', async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify([]),
    });
  });

  await page.goto('/admin/content-models');

  // Typography variant="h5" heading renders unconditionally
  await expect(page.getByRole('heading', { name: 'Content Models' })).toBeVisible();
});

test('automation rules page renders new rule button', async ({ page }) => {
  test.setTimeout(60_000);
  await mockKeycloakUnreachable(page);
  await seedBypassSessionE2E(page, 'admin', 'e2e-token');

  // RulesPage fires 6 endpoints on mount across loadAll(), loadRunLedger(), loadRuleAuditTimeline()
  await page.route('**/api/v1/rules/actions/definitions', async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({ actions: [] }),
    });
  });
  await page.route('**/api/v1/rules/executions/timeline*', async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify([]),
    });
  });
  await page.route('**/api/v1/rules/executions/audit*', async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify([]),
    });
  });
  await page.route('**/api/v1/rules/templates', async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify([]),
    });
  });
  await page.route('**/api/v1/rules/stats', async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({}),
    });
  });
  // Register the base /rules route last so it does not shadow the more specific sub-paths above
  await page.route('**/api/v1/rules*', async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({ content: [], totalElements: 0, totalPages: 0, number: 0, size: 100 }),
    });
  });

  await page.goto('/rules');

  // "New Rule" button renders unconditionally on the RulesPage toolbar
  await expect(page.getByRole('button', { name: 'New Rule' })).toBeVisible();
});
