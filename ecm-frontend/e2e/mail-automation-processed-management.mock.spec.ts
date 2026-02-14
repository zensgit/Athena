import { expect, test } from '@playwright/test';
import { seedBypassSessionE2E } from './helpers/login';

test('Mail automation: processed retention, cleanup, bulk delete, replay, and view ingested docs (mocked API)', async ({
  page,
}) => {
  test.setTimeout(120_000);

  // Accept confirm() prompts used by destructive actions (cleanup/delete).
  page.on('dialog', async (dialog) => {
    await dialog.accept();
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

  // Stateful retention + diagnostics so post-actions can observe updates.
  const retention = { retentionDays: 90, enabled: true, expiredCount: 2 };

  const processedOk = {
    id: 'processed-ok-1',
    processedAt: '2026-02-12T00:31:54Z',
    status: 'PROCESSED',
    accountId,
    accountName: 'gmail-imap',
    ruleId,
    ruleName: 'gmail-attachments',
    folder: 'INBOX',
    uid: '12351',
    subject: 'Invoice 2026-02',
    errorMessage: null,
  };

  const processedErr = {
    id: 'processed-err-1',
    processedAt: '2026-02-12T01:02:03Z',
    status: 'ERROR',
    accountId,
    accountName: 'gmail-imap',
    ruleId,
    ruleName: 'gmail-attachments',
    folder: 'INBOX',
    uid: '12352',
    subject: 'Broken attachment',
    errorMessage: 'Failed to parse attachment metadata',
  };

  const diagnosticsState: {
    limit: number;
    recentProcessed: any[];
    recentDocuments: any[];
  } = {
    limit: 25,
    recentProcessed: [processedOk, processedErr],
    recentDocuments: [
      {
        documentId: 'doc-1',
        name: 'invoice.pdf',
        path: '/Root/Documents/Invoices/invoice.pdf',
        createdDate: '2026-02-12T00:31:58Z',
        createdBy: 'admin',
        mimeType: 'application/pdf',
        fileSize: 12950,
        accountId,
        accountName: 'gmail-imap',
        ruleId,
        ruleName: 'gmail-attachments',
        folder: 'INBOX',
        uid: '12351',
      },
    ],
  };

  const processedDocsById: Record<string, any[]> = {
    [processedOk.id]: [
      {
        documentId: 'doc-1',
        name: 'invoice.pdf',
        path: '/Root/Documents/Invoices/invoice.pdf',
        createdDate: '2026-02-12T00:31:58Z',
        createdBy: 'admin',
        mimeType: 'application/pdf',
        fileSize: 12950,
        accountId,
        accountName: 'gmail-imap',
        ruleId,
        ruleName: 'gmail-attachments',
        folder: 'INBOX',
        uid: '12351',
      },
    ],
    [processedErr.id]: [],
  };

  await page.route('**/api/v1/integration/mail/processed/retention**', async (route) => {
    await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(retention) });
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
        totals: { processed: 18, errors: 1, total: 19 },
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
        attempts: 1,
        successes: 1,
        errors: 0,
        errorRate: 0,
        avgDurationMs: 4100,
        lastSuccessAt: '2026-02-12T00:31:54Z',
        lastErrorAt: null,
        status: 'HEALTHY',
        topErrors: [],
        trend: null,
      }),
    });
  });

  await page.route('**/api/v1/integration/mail/diagnostics**', async (route) => {
    // Avoid catching export endpoint (`/diagnostics/export`) here; that is covered in a dedicated spec.
    if (route.request().url().includes('/api/v1/integration/mail/diagnostics/export')) {
      await route.fallback();
      return;
    }
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify(diagnosticsState),
    });
  });

  await page.route('**/api/v1/integration/mail/processed/bulk-delete', async (route) => {
    if (route.request().method().toUpperCase() !== 'POST') {
      await route.fallback();
      return;
    }
    const body = route.request().postDataJSON() as { ids?: string[] };
    const ids = Array.isArray(body?.ids) ? body.ids : [];
    diagnosticsState.recentProcessed = diagnosticsState.recentProcessed.filter((item) => !ids.includes(item.id));
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({ deleted: ids.length }),
    });
  });

  await page.route('**/api/v1/integration/mail/processed/cleanup', async (route) => {
    if (route.request().method().toUpperCase() !== 'POST') {
      await route.fallback();
      return;
    }
    const deleted = retention.expiredCount;
    retention.expiredCount = 0;
    await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({ deleted }) });
  });

  await page.route('**/api/v1/integration/mail/processed/*/replay', async (route) => {
    if (route.request().method().toUpperCase() !== 'POST') {
      await route.fallback();
      return;
    }
    const url = route.request().url();
    const id = url.split('/processed/')[1]?.split('/replay')[0] || '';
    diagnosticsState.recentProcessed = diagnosticsState.recentProcessed.map((item) =>
      item.id === id
        ? {
            ...item,
            status: 'PROCESSED',
            errorMessage: null,
          }
        : item
    );
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        processedMailId: id,
        attempted: true,
        processed: true,
        message: 'ok',
        replayStatus: 'PROCESSED',
      }),
    });
  });

  await page.route('**/api/v1/integration/mail/processed/*/documents**', async (route) => {
    const url = route.request().url();
    const id = url.split('/processed/')[1]?.split('/documents')[0] || '';
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify(processedDocsById[id] || []),
    });
  });

  await page.goto('/', { waitUntil: 'domcontentloaded' });
  await expect(page.getByRole('button', { name: 'Account menu' })).toBeVisible();
  await page.getByRole('button', { name: 'Account menu' }).click();
  await page.getByRole('menuitem', { name: 'Mail Automation' }).click();

  await expect(page.getByRole('heading', { name: 'Mail Automation' })).toBeVisible();
  await expect(page.getByText('Retention 90d')).toBeVisible();
  await expect(page.getByText('Expired 2')).toBeVisible();

  const processedTable = page.locator('table').filter({ hasText: 'Linked Doc' }).first();
  await expect(processedTable.locator('tbody tr')).toHaveCount(2);

  const deleteSelected = page.getByRole('button', { name: 'Delete Selected', exact: true });
  await expect(deleteSelected).toBeDisabled();

  // Replay the failed item and verify the action is gated away after refresh.
  await processedTable
    .locator('tbody tr')
    .filter({ hasText: 'ERROR' })
    .getByRole('button', { name: 'Replay' })
    .click();
  await expect(page.getByText('Replay processed successfully')).toBeVisible();
  await expect(processedTable.getByRole('button', { name: 'Replay' })).toHaveCount(0);

  // View ingested docs for the processed item.
  await processedTable.getByLabel('view ingested documents').first().click();
  const docsDialog = page.getByTestId('processed-mail-docs-dialog');
  await expect(docsDialog).toBeVisible();
  await expect(docsDialog.getByRole('cell', { name: 'invoice.pdf', exact: true })).toBeVisible();
  await docsDialog.getByRole('button', { name: 'Close' }).click();
  await expect(docsDialog).toBeHidden();

  // Clean up expired processed records and verify retention updates.
  await page.getByRole('button', { name: 'Clean up expired' }).click();
  await expect(page.getByText('Deleted 2 expired processed record(s)')).toBeVisible();
  await expect(page.getByText('Expired 0')).toBeVisible();

  // Select a row and bulk delete.
  await processedTable.locator('tbody tr').nth(0).getByRole('checkbox').check();
  await expect(deleteSelected).toBeEnabled();
  await deleteSelected.click();
  await expect(page.getByText('Deleted 1 processed record(s)')).toBeVisible();
  await expect(processedTable.locator('tbody tr')).toHaveCount(1);
  await expect(deleteSelected).toBeDisabled();
});
