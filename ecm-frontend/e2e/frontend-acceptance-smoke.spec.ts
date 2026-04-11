import { expect, test } from '@playwright/test';
import { waitForApiReady } from './helpers/api';
import { loginWithCredentialsE2E } from './helpers/login';

const defaultUsername = process.env.ECM_E2E_USERNAME || 'admin';
const defaultPassword = process.env.ECM_E2E_PASSWORD || 'admin';

test.describe('Athena frontend acceptance smoke', () => {
  test.setTimeout(180_000);

  test.beforeEach(async ({ page, request }) => {
    await waitForApiReady(request);
    await loginWithCredentialsE2E(page, defaultUsername, defaultPassword);
  });

  test('renders Tenant Admin after authenticated navigation', async ({ page }) => {
    await page.goto('/admin/tenants', { waitUntil: 'domcontentloaded' });

    await expect(page).toHaveURL(/\/admin\/tenants$/);
    await expect(page.getByRole('heading', { name: 'Tenant Admin' })).toBeVisible();
    await expect(page.getByText('Current Request Tenant')).toBeVisible();
    await expect(page.getByRole('button', { name: 'New Tenant' })).toBeVisible();
  });

  test('renders Transfer Replication after authenticated navigation', async ({ page }) => {
    await page.goto('/admin/transfer-replication', { waitUntil: 'domcontentloaded' });

    await expect(page).toHaveURL(/\/admin\/transfer-replication$/);
    await expect(page.getByRole('heading', { name: 'Transfer Replication' })).toBeVisible();
    await expect(page.getByText('Transfer Targets')).toBeVisible();
    await expect(page.getByText('Replication Jobs')).toBeVisible();
    await expect(page.getByRole('button', { name: 'Refresh' })).toBeVisible();
  });

  test('renders CMIS Explorer after authenticated navigation', async ({ page }) => {
    await page.goto('/admin/cmis-explorer', { waitUntil: 'domcontentloaded' });

    await expect(page).toHaveURL(/\/admin\/cmis-explorer$/);
    await expect(page.getByRole('heading', { name: 'CMIS Explorer' })).toBeVisible();
    await expect(page.getByRole('tab', { name: 'Repository Info' })).toBeVisible();
    await expect(page.getByText('Repository ID')).toBeVisible();

    await page.getByRole('tab', { name: 'Type Browser' }).click();
    await expect(page.getByText('Display Name')).toBeVisible();
    await expect(page.getByRole('tab', { name: 'Query Console' })).toBeVisible();
  });
});
