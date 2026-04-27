import { expect, test } from '@playwright/test';
import { seedBypassSessionE2E } from './helpers/login';
import { mockKeycloakUnreachable } from './helpers/keycloakMock';

const inv1 = {
  id: 'inv-1',
  siteId: 'site-abc',
  siteTitle: 'Finance Site',
  inviteeEmail: 'alice@example.com',
  inviteeUsername: null,
  invitedRole: 'CONSUMER',
  status: 'PENDING',
  message: null,
  invitedBy: 'admin',
  expiresAt: '2026-05-26T00:00:00',
  acceptedAt: null,
  createdDate: '2026-04-26T00:00:00',
};

const inv2 = {
  id: 'inv-2',
  siteId: 'site-abc',
  siteTitle: 'Finance Site',
  inviteeEmail: 'bob@example.com',
  inviteeUsername: 'bob',
  invitedRole: 'MANAGER',
  status: 'ACCEPTED',
  message: 'Welcome!',
  invitedBy: 'admin',
  expiresAt: '2026-05-01T00:00:00',
  acceptedAt: '2026-04-20T09:00:00',
  createdDate: '2026-04-15T00:00:00',
};

const newInvitation = {
  id: 'inv-3',
  siteId: 'site-abc',
  siteTitle: 'Finance Site',
  inviteeEmail: 'carol@example.com',
  inviteeUsername: null,
  invitedRole: 'CONSUMER',
  status: 'PENDING',
  message: null,
  invitedBy: 'admin',
  expiresAt: '2026-05-26T00:00:00',
  acceptedAt: null,
  createdDate: '2026-04-26T00:00:00',
};

async function setupRoutes(page: Parameters<typeof seedBypassSessionE2E>[0]) {
  await mockKeycloakUnreachable(page);
  await seedBypassSessionE2E(page, 'admin', 'e2e-token');

  await page.route('**/api/v1/sites/site-abc/invitations/inv-1', async (route) => {
    await route.fulfill({ status: 204, body: '' });
  });

  await page.route('**/api/v1/sites/site-abc/invitations', async (route) => {
    if (route.request().method() === 'POST') {
      await route.fulfill({
        status: 201,
        contentType: 'application/json',
        body: JSON.stringify(newInvitation),
      });
    } else {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify([inv1, inv2]),
      });
    }
  });
}

test('shows invitation list with status chips', async ({ page }) => {
  test.setTimeout(60_000);

  await setupRoutes(page);
  await page.goto('/admin/sites/site-abc/invitations');

  await expect(page.getByText('alice@example.com')).toBeVisible();
  await expect(page.getByText('PENDING')).toBeVisible();
  await expect(page.getByText('ACCEPTED')).toBeVisible();
});

test('PENDING invitations have a cancel button', async ({ page }) => {
  test.setTimeout(60_000);

  await setupRoutes(page);
  await page.goto('/admin/sites/site-abc/invitations');

  await expect(page.getByText('alice@example.com')).toBeVisible();

  const cancelButton = page.getByRole('button', { name: /cancel invitation/i });
  await expect(cancelButton).toBeVisible();
});

test('invite dialog opens', async ({ page }) => {
  test.setTimeout(60_000);

  await setupRoutes(page);
  await page.goto('/admin/sites/site-abc/invitations');

  await page.getByRole('button', { name: 'Invite' }).click();

  await expect(page.getByRole('dialog', { name: 'Invite to Site' })).toBeVisible();
  await expect(page.getByLabel('Email address')).toBeVisible();
  await page.getByRole('combobox', { name: 'Role' }).click();
  await expect(page.getByRole('option', { name: 'Contributor' })).toBeVisible();
});

test('authenticated non-admin users can reach invitation manager route', async ({ page }) => {
  test.setTimeout(60_000);

  await mockKeycloakUnreachable(page);
  await seedBypassSessionE2E(page, 'editor', 'e2e-token');

  await page.route('**/api/v1/sites/site-abc/invitations', async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify([inv1]),
    });
  });

  await page.goto('/admin/sites/site-abc/invitations');

  await expect(page.getByRole('heading', { name: 'Site Invitations' })).toBeVisible();
  await expect(page.getByText('alice@example.com')).toBeVisible();
});

test('cancelling a PENDING invitation calls DELETE', async ({ page }) => {
  test.setTimeout(60_000);

  await mockKeycloakUnreachable(page);
  await seedBypassSessionE2E(page, 'admin', 'e2e-token');

  let deleteCalled = false;
  await page.route('**/api/v1/sites/site-abc/invitations/inv-1', async (route) => {
    deleteCalled = true;
    await route.fulfill({ status: 204, body: '' });
  });

  await page.route('**/api/v1/sites/site-abc/invitations', async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify([inv1, inv2]),
    });
  });

  page.on('dialog', (dialog) => dialog.accept());

  await page.goto('/admin/sites/site-abc/invitations');

  await expect(page.getByText('alice@example.com')).toBeVisible();

  await page.getByRole('button', { name: /cancel invitation/i }).click();

  expect(deleteCalled).toBe(true);
});
