import { expect, test } from '@playwright/test';
import { seedBypassSessionE2E } from './helpers/login';
import { mockKeycloakUnreachable } from './helpers/keycloakMock';

const acceptedDto = {
  id: 'inv-1',
  siteId: 'site-abc',
  siteTitle: 'Finance Site',
  inviteeEmail: 'alice@example.com',
  inviteeUsername: null,
  invitedRole: 'CONSUMER',
  status: 'ACCEPTED',
  message: null,
  invitedBy: 'admin',
  expiresAt: '2026-05-26T00:00:00',
  acceptedAt: '2026-04-26T10:00:00',
  createdDate: '2026-04-26T00:00:00',
};

const rejectedDto = {
  ...acceptedDto,
  status: 'REJECTED',
  acceptedAt: null,
};

async function setupAuth(page: Parameters<typeof seedBypassSessionE2E>[0]) {
  await mockKeycloakUnreachable(page);
  await seedBypassSessionE2E(page, 'admin', 'e2e-token');
}

test('shows accept and decline buttons when token present', async ({ page }) => {
  test.setTimeout(60_000);

  await setupAuth(page);

  await page.route('**/api/v1/invitations/accept', async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify(acceptedDto),
    });
  });

  await page.route('**/api/v1/invitations/reject', async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify(rejectedDto),
    });
  });

  await page.goto('/invitations/accept?token=test-token-abc');

  await expect(page.getByRole('button', { name: 'Accept Invitation' })).toBeVisible();
  await expect(page.getByRole('button', { name: 'Decline' })).toBeVisible();
});

test('accepting invitation shows success state', async ({ page }) => {
  test.setTimeout(60_000);

  await setupAuth(page);

  await page.route('**/api/v1/invitations/accept', async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify(acceptedDto),
    });
  });

  await page.goto('/invitations/accept?token=test-token-abc');

  await page.getByRole('button', { name: 'Accept Invitation' }).click();

  await expect(page.getByText("You're in!", { exact: true })).toBeVisible();
  await expect(page.getByText('Finance Site', { exact: true })).toBeVisible();
});

test('declining invitation shows declined state', async ({ page }) => {
  test.setTimeout(60_000);

  await setupAuth(page);

  await page.route('**/api/v1/invitations/reject', async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify(rejectedDto),
    });
  });

  await page.goto('/invitations/accept?token=test-token-abc');

  await page.getByRole('button', { name: 'Decline' }).click();

  await expect(page.getByText('Invitation declined', { exact: true })).toBeVisible();
  await expect(page.getByText('Finance Site', { exact: true })).toBeVisible();
});

test('accepts a manually pasted token when the URL has no token parameter', async ({ page }) => {
  test.setTimeout(60_000);

  await setupAuth(page);

  let submittedToken = '';
  await page.route('**/api/v1/invitations/accept', async (route) => {
    submittedToken = route.request().postDataJSON().token;
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify(acceptedDto),
    });
  });

  await page.goto('/invitations/accept');

  await expect(page.getByText('Manual token entry')).toBeVisible();
  await page.getByLabel('Invitation token').fill('raw-token-123');
  await page.getByRole('button', { name: 'Accept Invitation' }).click();

  expect(submittedToken).toBe('raw-token-123');
  await expect(page.getByText("You're in!", { exact: true })).toBeVisible();
});
