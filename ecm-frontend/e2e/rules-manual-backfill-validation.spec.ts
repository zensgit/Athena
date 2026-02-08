import { expect, test } from '@playwright/test';
import { fetchAccessToken, resolveApiUrl, waitForApiReady } from './helpers/api';
import { gotoWithAuthE2E } from './helpers/login';

const baseApiUrl = resolveApiUrl();
const defaultUsername = process.env.ECM_E2E_USERNAME || 'admin';
const defaultPassword = process.env.ECM_E2E_PASSWORD || 'admin';

test.beforeEach(async ({ request }) => {
  await waitForApiReady(request, { apiUrl: baseApiUrl });
});

test('rules: manual backfill blocks out-of-range values before POST', async ({ page, request }) => {
  test.setTimeout(180_000);

  const token = await fetchAccessToken(request, defaultUsername, defaultPassword);
  await gotoWithAuthE2E(page, '/rules', defaultUsername, defaultPassword, { token });

  await page.getByRole('button', { name: 'New Rule' }).click();
  const dialog = page.getByRole('dialog').filter({ hasText: 'New Rule' });
  await expect(dialog).toBeVisible({ timeout: 60_000 });

  await dialog.getByLabel('Name').fill(`e2e-invalid-backfill-${Date.now()}`);

  await dialog.getByLabel('Trigger').click();
  await page.getByRole('option', { name: 'SCHEDULED' }).click();
  await expect(dialog.getByText('Schedule Configuration')).toBeVisible({ timeout: 60_000 });

  await dialog.getByLabel('Cron Preset').click();
  await page.getByRole('option', { name: 'Every hour' }).click();
  await expect(dialog.getByLabel('Cron Expression')).toHaveValue('0 0 * * * *', {
    timeout: 60_000,
  });

  await dialog.getByLabel('Manual Trigger Backfill (minutes)').fill('2000');

  const createRuleRequest = page
    .waitForRequest(
      (req) => req.url().includes('/api/v1/rules') && req.method() === 'POST',
      { timeout: 5_000 },
    )
    .then(() => true)
    .catch(() => false);

  await dialog.getByRole('button', { name: 'Save', exact: true }).click();

  await expect(page.getByText('Manual backfill minutes must be 1440 or less')).toBeVisible({
    timeout: 10_000,
  });
  await expect(dialog).toBeVisible();

  const requestSent = await createRuleRequest;
  expect(requestSent).toBeFalsy();
});
