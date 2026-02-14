import { expect, test } from '@playwright/test';
import { waitForApiReady } from './helpers/api';
import { loginWithCredentialsE2E } from './helpers/login';

test('Phase 5 full-stack: admin pages render (Mail Automation + Preview Diagnostics)', async ({ page }) => {
  test.setTimeout(180_000);

  const username = process.env.ECM_E2E_USERNAME || 'admin';
  const password = process.env.ECM_E2E_PASSWORD || 'admin';

  // Avoid flakiness when the stack is still starting.
  await waitForApiReady(page.request);

  await loginWithCredentialsE2E(page, username, password);

  await page.goto('/admin/mail', { waitUntil: 'domcontentloaded' });
  await expect(page.getByRole('heading', { name: 'Mail Automation' })).toBeVisible();

  await page.goto('/admin/preview-diagnostics', { waitUntil: 'domcontentloaded' });
  await expect(page.getByRole('heading', { name: 'Preview Diagnostics' })).toBeVisible();
  await expect(page.getByRole('button', { name: 'Refresh' })).toBeVisible();
});

