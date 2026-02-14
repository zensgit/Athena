import { expect, test } from '@playwright/test';
import { seedBypassSessionE2E } from './helpers/login';

test('Mail automation: Trigger Fetch updates last fetch summary and copies run id (mocked API)', async ({ page }) => {
  test.setTimeout(120_000);

  await page.addInitScript(() => {
    // Avoid relying on system clipboard permissions in CI/local runs.
    (window as any).__copiedText = null;
    Object.defineProperty(navigator, 'clipboard', {
      value: {
        writeText: async (text: string) => {
          (window as any).__copiedText = text;
        },
      },
      configurable: true,
    });
  });

  await seedBypassSessionE2E(page, 'admin', 'e2e-token');

  await page.route('**/api/v1/folders/roots', async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify([
        {
          id: 'root-folder-id',
          name: 'Root',
          path: '/Root',
          folderType: 'SYSTEM',
          parentId: null,
          inheritPermissions: true,
          description: null,
          createdBy: 'admin',
          createdDate: new Date().toISOString(),
          lastModifiedBy: 'admin',
          lastModifiedDate: new Date().toISOString(),
        },
      ]),
    });
  });

  await page.route('**/api/v1/folders/*/contents**', async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({ content: [], totalElements: 0 }),
    });
  });

  await page.route('**/api/v1/tags**', async (route) => {
    await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify([]) });
  });

  const accountId = 'gmail-imap';
  const ruleId = 'gmail-attachments';
  const runId = '12345678abcdef';

  await page.route('**/api/v1/integration/mail/accounts**', async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify([
        {
          id: accountId,
          name: 'gmail-imap',
          host: 'imap.gmail.com',
          port: 993,
          username: 'admin@example.com',
          security: 'OAUTH2',
          enabled: true,
          pollIntervalMinutes: 10,
          oauthProvider: 'GOOGLE',
          oauthConnected: true,
          passwordConfigured: false,
          oauthEnvConfigured: true,
          oauthMissingEnvKeys: [],
          lastFetchAt: null,
          lastFetchStatus: null,
          lastFetchError: null,
        },
      ]),
    });
  });

  await page.route('**/api/v1/integration/mail/rules**', async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify([
        {
          id: ruleId,
          name: 'gmail-attachments',
          accountId,
          priority: 0,
          enabled: true,
          folder: 'INBOX',
          actionType: 'ATTACHMENTS_ONLY',
          mailAction: 'MARK_READ',
        },
      ]),
    });
  });

  await page.route('**/api/v1/integration/mail/fetch/summary**', async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({ summary: null, fetchedAt: null }),
    });
  });

  await page.route('**/api/v1/integration/mail/processed/retention**', async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({ retentionDays: 90, enabled: true, expiredCount: 0 }),
    });
  });

  await page.route('**/api/v1/integration/mail/report/schedule**', async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        enabled: true,
        cron: '0 */10 * * * *',
        folderId: null,
        days: 30,
        accountId,
        ruleId,
        lastExport: null,
      }),
    });
  });

  await page.route('**/api/v1/integration/mail/report**', async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        accountId,
        ruleId,
        startDate: '2026-01-14',
        endDate: '2026-02-12',
        days: 30,
        totals: { processed: 18, errors: 0, total: 18 },
        accounts: [
          {
            accountId,
            accountName: 'gmail-imap',
            processed: 18,
            errors: 0,
            total: 18,
            lastProcessedAt: '2026-01-28T00:31:54Z',
            lastErrorAt: null,
          },
        ],
        rules: [
          {
            ruleId,
            ruleName: 'gmail-attachments',
            accountId,
            accountName: 'gmail-imap',
            processed: 18,
            errors: 0,
            total: 18,
            lastProcessedAt: '2026-01-28T00:31:54Z',
            lastErrorAt: null,
          },
        ],
        trend: [],
      }),
    });
  });

  await page.route('**/api/v1/integration/mail/runtime-metrics**', async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        windowMinutes: 60,
        attempts: 1,
        successes: 1,
        errors: 0,
        errorRate: 0,
        avgDurationMs: 4100,
        lastSuccessAt: '2026-01-28T00:31:54Z',
        lastErrorAt: null,
        status: 'HEALTHY',
        topErrors: [],
        trend: null,
      }),
    });
  });

  await page.route('**/api/v1/integration/mail/diagnostics**', async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        limit: 25,
        recentProcessed: [],
        recentDocuments: [],
      }),
    });
  });

  await page.route('**/api/v1/integration/mail/fetch', async (route) => {
    if (route.request().method().toUpperCase() !== 'POST') {
      await route.fallback();
      return;
    }
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        accounts: 1,
        attemptedAccounts: 1,
        skippedAccounts: 0,
        accountErrors: 0,
        foundMessages: 1,
        matchedMessages: 1,
        processedMessages: 1,
        skippedMessages: 0,
        errorMessages: 0,
        durationMs: 4100,
        runId,
      }),
    });
  });

  await page.goto('/', { waitUntil: 'domcontentloaded' });
  await expect(page.getByRole('button', { name: 'Account menu' })).toBeVisible();
  await page.getByRole('button', { name: 'Account menu' }).click();
  await page.getByRole('menuitem', { name: 'Mail Automation' }).click();

  await expect(page.getByRole('heading', { name: 'Mail Automation' })).toBeVisible();
  await expect(page.getByText('No fetch summary yet. Use Trigger Fetch to capture the latest run.')).toBeVisible();

  await page.getByRole('button', { name: 'Trigger Fetch' }).click();
  await expect(page.getByText('Processed 1 of 1 matched')).toBeVisible();

  await expect(page.getByText('Accounts 1')).toBeVisible();
  await expect(page.getByText('Attempted 1')).toBeVisible();
  await expect(page.getByText('Found 1')).toBeVisible();
  await expect(page.getByText('Matched 1')).toBeVisible();
  await expect(page.getByText('Processed 1', { exact: true })).toBeVisible();
  await expect(page.getByText('Duration 4.1s')).toBeVisible();

  await page.getByText('Run 12345678').click();
  await expect(page.getByText('Run id copied')).toBeVisible();
  const copied = await page.evaluate(() => (window as any).__copiedText);
  expect(copied).toBe(runId);
});
