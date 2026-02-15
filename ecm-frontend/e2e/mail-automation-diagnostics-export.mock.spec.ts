import { expect, test } from '@playwright/test';
import { seedBypassSessionE2E } from './helpers/login';

test('Mail automation: List folders, run diagnostics, export CSVs, and copy diagnostics link (mocked API)', async ({ page }) => {
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

    // Capture anchor-triggered blob downloads (Mail Automation exports use URL.createObjectURL + <a download>).
    (window as any).__downloads = [];
    const origClick = HTMLElement.prototype.click;
    HTMLElement.prototype.click = function clickPatched(this: HTMLElement) {
      try {
        if (this instanceof HTMLAnchorElement) {
          const href = this.href || '';
          const download = this.download || '';
          if (download) {
            (window as any).__downloads.push({ href, download });
            return;
          }
        }
      } catch {
        // Ignore cross-realm / prototype issues in unusual environments.
      }
      return origClick.call(this);
    };
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

  await page.route(`**/api/v1/integration/mail/accounts/${accountId}/folders`, async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify(['INBOX', 'ECM-TEST', '[Gmail]/Important']),
    });
  });

  await page.route('**/api/v1/integration/mail/fetch/debug**', async (route) => {
    if (route.request().method().toUpperCase() !== 'POST') {
      await route.fallback();
      return;
    }
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        summary: {
          accounts: 1,
          attemptedAccounts: 1,
          skippedAccounts: 0,
          accountErrors: 0,
          foundMessages: 3,
          matchedMessages: 2,
          processedMessages: 1,
          skippedMessages: 1,
          errorMessages: 0,
          durationMs: 4100,
          runId: 'debug-run-abcdef12',
        },
        maxMessagesPerFolder: 200,
        skipReasons: { already_processed: 1 },
        accounts: [
          {
            accountId,
            accountName: 'gmail-imap',
            attempted: true,
            rules: 1,
            folders: 1,
            foundMessages: 3,
            scannedMessages: 3,
            matchedMessages: 2,
            processableMessages: 1,
            skippedMessages: 1,
            errorMessages: 0,
            skipReasons: { already_processed: 1 },
            ruleMatches: { [ruleId]: 2 },
            folderResults: [
              {
                folder: 'INBOX',
                rules: 1,
                foundMessages: 3,
                scannedMessages: 3,
                matchedMessages: 2,
                processableMessages: 1,
                skippedMessages: 1,
                errorMessages: 0,
                skipReasons: { already_processed: 1 },
              },
            ],
          },
        ],
      }),
    });
  });

  await page.route('**/api/v1/integration/mail/report/export**', async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'text/csv',
      body: [
        'account,rule,processed,errors,total',
        'gmail-imap,gmail-attachments,18,0,18',
        '',
      ].join('\n'),
    });
  });

  await page.route('**/api/v1/integration/mail/diagnostics/export**', async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'text/csv',
      body: ['processedAt,status,account,rule,uid,subject,error', ''].join('\n'),
    });
  });

  await page.goto('/', { waitUntil: 'domcontentloaded' });
  await expect(page.getByRole('button', { name: 'Account menu' })).toBeVisible();
  await page.getByRole('button', { name: 'Account menu' }).click();
  await page.getByRole('menuitem', { name: 'Mail Automation' }).click();

  await expect(page.getByRole('heading', { name: 'Mail Automation' })).toBeVisible();

  const cardByHeading = (name: string) =>
    page.getByRole('heading', { name }).locator('xpath=ancestor::div[contains(@class,"MuiCard-root")][1]');

  const reportingCard = cardByHeading('Mail Reporting');
  const reportExportButton = reportingCard.getByRole('button', { name: 'Export CSV' });
  await expect(reportExportButton).toBeEnabled();
  const downloadsBeforeReport = await page.evaluate(() => ((window as any).__downloads || []).length);
  await reportExportButton.click();

  // Export fetch + blob conversion is async; wait for the programmatic anchor click to be captured.
  await page.waitForFunction((before) => ((window as any).__downloads || []).length > before, downloadsBeforeReport);
  const downloadsAfterReport = await page.evaluate(() => (window as any).__downloads || []);
  expect(downloadsAfterReport.some((item: any) => typeof item.download === 'string' && /^mail-report-/.test(item.download))).toBeTruthy();

  const diagnosticsCard = cardByHeading('Fetch Diagnostics (Dry Run)');
  const listFoldersButton = diagnosticsCard.getByRole('button', { name: 'List Folders' });
  await listFoldersButton.click();
  await expect(page.getByText('Found 3 folders')).toBeVisible();
  await expect(page.getByText('Available folders (3)')).toBeVisible();
  await expect(page.getByText('ECM-TEST')).toBeVisible();

  await diagnosticsCard.getByRole('button', { name: 'Run Diagnostics' }).click();
  await expect(page.getByText('Diagnostics complete: matched 2, processable 1 in 4.1s')).toBeVisible();
  await expect(diagnosticsCard.getByText('Attempted: 1').first()).toBeVisible();
  await expect(diagnosticsCard.getByText('Matched: 2').first()).toBeVisible();
  await expect(diagnosticsCard.getByText('Processable: 1').first()).toBeVisible();

  const recentActivityCard = cardByHeading('Recent Mail Activity');
  await recentActivityCard.getByRole('button', { name: 'Copy link' }).click();
  await expect(page.getByText('Diagnostics link copied')).toBeVisible();
  const copied = await page.evaluate(() => (window as any).__copiedText);
  expect(String(copied)).toContain('#diagnostics');

  const diagnosticsExportButton = recentActivityCard.getByRole('button', { name: 'Export CSV' });
  await expect(diagnosticsExportButton).toBeEnabled();
  const downloadsBeforeDiagnostics = await page.evaluate(() => ((window as any).__downloads || []).length);
  await diagnosticsExportButton.click();

  await page.waitForFunction(
    (before) => ((window as any).__downloads || []).length > before,
    downloadsBeforeDiagnostics
  );
  const downloadsAfterDiagnostics = await page.evaluate(() => (window as any).__downloads || []);
  expect(downloadsAfterDiagnostics.some((item: any) => typeof item.download === 'string' && /^mail-diagnostics-/.test(item.download))).toBeTruthy();
});
