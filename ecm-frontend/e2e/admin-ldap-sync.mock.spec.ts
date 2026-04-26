import { expect, test } from '@playwright/test';
import { seedBypassSessionE2E } from './helpers/login';
import { mockKeycloakUnreachable } from './helpers/keycloakMock';

const CONNECTION_OK = {
  reachable: true,
  message: 'Connected to LDAP server',
  userBaseDn: 'ou=users,dc=example,dc=com',
  groupBaseDn: 'ou=groups,dc=example,dc=com',
};

const SYNC_RESULT = {
  usersCreated: 5,
  usersUpdated: 2,
  usersDisabled: 0,
  usersSkipped: 10,
  groupsCreated: 1,
  groupsUpdated: 3,
  groupsDisabled: 0,
  groupsSkipped: 5,
  membershipsChanged: 8,
  unresolvedMembers: 0,
  warnings: [],
  syncedAt: '2026-04-26T10:00:00',
  trigger: 'manual',
};

test('shows connection status card and sync card', async ({ page }) => {
  test.setTimeout(60_000);
  await mockKeycloakUnreachable(page);
  await seedBypassSessionE2E(page, 'admin', 'e2e-token');

  // Route mocks are registered before navigation even if not clicked in this test
  await page.route('**/api/v1/admin/ldap/test-connection', async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify(CONNECTION_OK),
    });
  });
  await page.route('**/api/v1/admin/ldap/sync', async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify(SYNC_RESULT),
    });
  });

  await page.goto('/admin/ldap');

  // Both action buttons must be visible on the page
  await expect(page.getByRole('button', { name: 'Test Connection' })).toBeVisible();
  await expect(page.getByRole('button', { name: 'Sync Now' })).toBeVisible();
});

test('test connection shows result', async ({ page }) => {
  test.setTimeout(60_000);
  await mockKeycloakUnreachable(page);
  await seedBypassSessionE2E(page, 'admin', 'e2e-token');

  await page.route('**/api/v1/admin/ldap/test-connection', async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify(CONNECTION_OK),
    });
  });
  await page.route('**/api/v1/admin/ldap/sync', async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify(SYNC_RESULT),
    });
  });

  await page.goto('/admin/ldap');

  await page.getByRole('button', { name: 'Test Connection' }).click();

  // The ConnectionCard renders a "Reachable" chip when result.reachable is true
  await expect(page.getByText('Reachable')).toBeVisible();

  // The userBaseDn value is shown as body text
  await expect(page.getByText('ou=users,dc=example,dc=com')).toBeVisible();
});

test('sync now shows stats', async ({ page }) => {
  test.setTimeout(60_000);
  await mockKeycloakUnreachable(page);
  await seedBypassSessionE2E(page, 'admin', 'e2e-token');

  await page.route('**/api/v1/admin/ldap/test-connection', async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify(CONNECTION_OK),
    });
  });
  await page.route('**/api/v1/admin/ldap/sync', async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify(SYNC_RESULT),
    });
  });

  await page.goto('/admin/ldap');

  await page.getByRole('button', { name: 'Sync Now' }).click();

  // The SyncCard renders chips like "Created: 5" for users
  await expect(page.getByText('Created: 5')).toBeVisible();

  // Trigger chip is also shown
  await expect(page.getByText('Trigger: manual')).toBeVisible();
});

test('shows not-configured state on 404', async ({ page }) => {
  test.setTimeout(60_000);
  await mockKeycloakUnreachable(page);
  await seedBypassSessionE2E(page, 'admin', 'e2e-token');

  // Override test-connection to return 404 — triggers the "not configured" branch
  await page.route('**/api/v1/admin/ldap/test-connection', async (route) => {
    await route.fulfill({
      status: 404,
      contentType: 'application/json',
      body: JSON.stringify({ message: 'LDAP not configured' }),
    });
  });
  await page.route('**/api/v1/admin/ldap/sync', async (route) => {
    await route.fulfill({
      status: 404,
      contentType: 'application/json',
      body: JSON.stringify({ message: 'LDAP not configured' }),
    });
  });

  await page.goto('/admin/ldap');

  await page.getByRole('button', { name: 'Test Connection' }).click();

  // LdapSyncPage switches to an informational Alert when isLdapNotConfigured fires
  await expect(
    page.getByText('LDAP integration is not enabled for this Athena instance.')
  ).toBeVisible();
});
