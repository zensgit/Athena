import { expect, test } from '@playwright/test';
import { seedBypassSessionE2E } from './helpers/login';

test('Mail automation P1: account health, summary refresh, diagnostics filters, and preview dialog utilities (mocked API)', async ({
  page,
}) => {
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

  const accountA = 'gmail-imap';
  const accountB = 'gmail-graph';
  const accountC = 'legacy-imap';
  const ruleId = 'gmail-attachments';
  const staleAt = new Date(Date.now() - 24 * 60 * 60 * 1000).toISOString();
  const freshAt = new Date(Date.now() - 5 * 60 * 1000).toISOString();
  let fetchSummaryCallCount = 0;

  await page.route('**/api/v1/integration/mail/accounts**', async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify([
        {
          id: accountA,
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
          lastFetchAt: staleAt,
          lastFetchStatus: 'SUCCESS',
          lastFetchError: null,
        },
        {
          id: accountB,
          name: 'gmail-graph',
          host: 'outlook.office365.com',
          port: 993,
          username: 'ops@example.com',
          security: 'OAUTH2',
          enabled: false,
          pollIntervalMinutes: 10,
          oauthProvider: 'MICROSOFT',
          oauthConnected: false,
          passwordConfigured: false,
          oauthEnvConfigured: false,
          oauthMissingEnvKeys: ['client-id', 'client-secret'],
          lastFetchAt: freshAt,
          lastFetchStatus: 'ERROR',
          lastFetchError: 'invalid_grant',
        },
        {
          id: accountC,
          name: 'legacy-imap',
          host: 'mail.example.com',
          port: 993,
          username: 'legacy@example.com',
          security: 'SSL',
          enabled: true,
          pollIntervalMinutes: 15,
          oauthProvider: 'CUSTOM',
          oauthConnected: null,
          passwordConfigured: true,
          oauthEnvConfigured: true,
          oauthMissingEnvKeys: [],
          lastFetchAt: null,
          lastFetchStatus: 'SKIPPED',
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
          accountId: accountA,
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
    fetchSummaryCallCount += 1;
    const refreshed = fetchSummaryCallCount > 1;
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        summary: {
          accounts: 3,
          attemptedAccounts: 3,
          skippedAccounts: 0,
          accountErrors: 1,
          foundMessages: refreshed ? 5 : 4,
          matchedMessages: refreshed ? 4 : 3,
          processedMessages: refreshed ? 4 : 3,
          skippedMessages: 1,
          errorMessages: 0,
          durationMs: refreshed ? 4300 : 4200,
          runId: refreshed ? 'refresh-run-1234' : 'initial-run-1234',
        },
        fetchedAt: refreshed ? freshAt : staleAt,
      }),
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
        accountId: accountA,
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
        accountId: accountA,
        ruleId,
        startDate: '2026-02-01',
        endDate: '2026-02-15',
        days: 14,
        totals: { processed: 7, errors: 1, total: 8 },
        accounts: [],
        rules: [],
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
        attempts: 3,
        successes: 2,
        errors: 1,
        errorRate: 0.3333,
        avgDurationMs: 4200,
        lastSuccessAt: freshAt,
        lastErrorAt: freshAt,
        status: 'DEGRADED',
        topErrors: [],
        trend: null,
      }),
    });
  });

  const processedItems = [
    {
      id: 'processed-ok-1',
      processedAt: '2026-02-15T01:00:00Z',
      status: 'PROCESSED',
      accountId: accountA,
      accountName: 'gmail-imap',
      ruleId,
      ruleName: 'gmail-attachments',
      folder: 'INBOX',
      uid: '20001',
      subject: 'Invoice ready',
      errorMessage: null,
    },
    {
      id: 'processed-err-1',
      processedAt: '2026-02-15T01:03:00Z',
      status: 'ERROR',
      accountId: accountA,
      accountName: 'gmail-imap',
      ruleId,
      ruleName: 'gmail-attachments',
      folder: 'INBOX',
      uid: '20002',
      subject: 'Broken attachment',
      errorMessage: 'Failed to parse attachment metadata',
    },
  ];
  const recentDocuments = [
    {
      documentId: 'doc-1',
      name: 'invoice.pdf',
      path: '/Root/Documents/Invoices/invoice.pdf',
      createdDate: '2026-02-15T01:00:05Z',
      createdBy: 'admin',
      mimeType: 'application/pdf',
      fileSize: 12950,
      accountId: accountA,
      accountName: 'gmail-imap',
      ruleId,
      ruleName: 'gmail-attachments',
      folder: 'INBOX',
      uid: '20001',
    },
  ];

  await page.route('**/api/v1/integration/mail/diagnostics**', async (route) => {
    if (route.request().url().includes('/api/v1/integration/mail/diagnostics/export')) {
      await route.fallback();
      return;
    }
    const requestUrl = new URL(route.request().url());
    const status = requestUrl.searchParams.get('status') || '';
    const filteredProcessed = status
      ? processedItems.filter((item) => item.status === status)
      : processedItems;
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        limit: 25,
        recentProcessed: filteredProcessed,
        recentDocuments,
      }),
    });
  });

  await page.route('**/api/v1/integration/mail/rules/*/preview', async (route) => {
    if (route.request().method().toUpperCase() !== 'POST') {
      await route.fallback();
      return;
    }
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        accountId: accountA,
        accountName: 'gmail-imap',
        ruleId,
        ruleName: 'gmail-attachments',
        maxMessagesPerFolder: 25,
        foundMessages: 3,
        scannedMessages: 3,
        matchedMessages: 2,
        processableMessages: 1,
        skippedMessages: 1,
        errorMessages: 0,
        skipReasons: {
          mailbox_readonly: 2,
          already_processed: 1,
        },
        matches: [
          {
            folder: 'INBOX',
            uid: '20001',
            subject: 'Alpha',
            from: 'a@example.com',
            recipients: 'b@example.com',
            receivedAt: freshAt,
            attachmentCount: 1,
            processable: true,
          },
          {
            folder: 'INBOX',
            uid: '20002',
            subject: 'Beta',
            from: 'c@example.com',
            recipients: 'd@example.com',
            receivedAt: freshAt,
            attachmentCount: 0,
            processable: false,
          },
        ],
        runId: 'preview-run-1234',
      }),
    });
  });

  await page.route('**/api/v1/nodes/doc-1', async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        id: 'doc-1',
        parentId: 'target-folder-id',
      }),
    });
  });

  await page.goto('/', { waitUntil: 'domcontentloaded' });
  await expect(page.getByRole('button', { name: 'Account menu' })).toBeVisible();
  await page.getByRole('button', { name: 'Account menu' }).click();
  await page.getByRole('menuitem', { name: 'Mail Automation' }).click();

  await expect(page.getByRole('heading', { name: 'Mail Automation' })).toBeVisible();

  await expect(page.getByText('Total 3')).toBeVisible();
  await expect(page.getByText('Enabled 2')).toBeVisible();
  await expect(page.getByText('Disabled 1')).toBeVisible();
  await expect(page.getByText('Fetch OK 1')).toBeVisible();
  await expect(page.getByText('Fetch errors 1')).toBeVisible();
  await expect(page.getByText('Fetch other 1')).toBeVisible();
  await expect(page.getByText('Never fetched 1')).toBeVisible();
  await expect(page.getByText('Stale 1')).toBeVisible();
  await expect(page.getByText('OAuth 2')).toBeVisible();
  await expect(page.getByText('OAuth connected 1')).toBeVisible();
  await expect(page.getByText('OAuth not connected 1')).toBeVisible();
  await expect(page.getByText('OAuth env missing 1')).toBeVisible();

  await expect(page.getByText('Processed 3', { exact: true })).toBeVisible();
  await page.locator('#diagnostics').getByRole('button', { name: 'Refresh status' }).click();
  await expect.poll(() => fetchSummaryCallCount).toBeGreaterThan(1);

  const processedTable = page.locator('table').filter({ hasText: 'Linked Doc' }).first();
  await expect(processedTable.locator('tbody tr')).toHaveCount(2);
  await page.getByRole('button', { name: 'Error', exact: true }).first().click();
  await expect(processedTable.locator('tbody tr')).toHaveCount(1);
  await expect(processedTable.getByText('Broken attachment')).toBeVisible();
  await page.getByRole('button', { name: 'All', exact: true }).first().click();
  await expect(processedTable.locator('tbody tr')).toHaveCount(2);

  await page.getByLabel('Preview rule gmail-attachments').first().click();
  const previewDialog = page.getByRole('dialog', { name: 'Preview Mail Rule' });
  await expect(previewDialog).toBeVisible();
  await previewDialog.getByRole('button', { name: 'Run Preview' }).click();

  await expect(previewDialog.getByText('Processable 1')).toBeVisible();
  await expect(previewDialog.getByText('Skip reasons')).toBeVisible();
  await expect(previewDialog.getByText('mailbox readonly: 2')).toBeVisible();
  await expect(previewDialog.getByText('already processed: 1')).toBeVisible();
  await expect(previewDialog.locator('table tbody tr')).toHaveCount(2);

  await previewDialog.getByLabel('Processable').click();
  await page.getByRole('option', { name: 'Processable only' }).click();
  await expect(previewDialog.locator('table tbody tr')).toHaveCount(1);
  await expect(previewDialog.getByText('Alpha')).toBeVisible();

  await previewDialog.getByLabel('Processable').click();
  await page.getByRole('option', { name: 'Not processable' }).click();
  await expect(previewDialog.locator('table tbody tr')).toHaveCount(1);
  await expect(previewDialog.getByText('Beta')).toBeVisible();

  await previewDialog.getByRole('button', { name: 'Copy JSON' }).click();
  await expect(page.getByText('Preview JSON copied')).toBeVisible();
  const copied = await page.evaluate(() => (window as any).__copiedText);
  expect(typeof copied).toBe('string');
  expect(copied).toContain('"runId": "preview-run-1234"');

  await previewDialog.getByRole('button', { name: 'Close' }).click();
  await page.getByLabel('open mail document').first().click();
  await expect(page).toHaveURL(/\/browse\/target-folder-id$/);
});
