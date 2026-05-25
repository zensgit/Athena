import api from './api';
import mailAutomationService, {
  EmailTestSmtpResponse,
  MailAccount,
  MailConnectionTestResult,
  MailDiagnosticsResult,
  MailFetchDebugResult,
  MailFetchSummary,
  MailFetchSummaryStatus,
  MailProviderPreset,
  MailReplayResult,
  MailReportResponse,
  MailReportScheduleStatus,
  MailReportScheduledExportResult,
  MailRule,
  MailRulePreviewResult,
  MailRulePreviewExportResult,
  MailRuntimeMetrics,
  ProcessedMailRetentionStatus,
  MAIL_AUTOMATION_UNEXPECTED_RESPONSE_MESSAGE,
  TEST_SMTP_UNEXPECTED_RESPONSE_MESSAGE,
} from './mailAutomationService';

jest.mock('./api', () => ({
  __esModule: true,
  default: {
    get: jest.fn(),
    post: jest.fn(),
    put: jest.fn(),
    delete: jest.fn(),
    getBlob: jest.fn(),
  },
}));

const mockedApi = api as jest.Mocked<typeof api>;

const account: MailAccount = {
  id: 'acct-1',
  name: 'Primary Inbox',
  host: 'imap.example.com',
  port: 993,
  username: 'ops@example.com',
  security: 'SSL',
  enabled: true,
  pollIntervalMinutes: 10,
  oauthProvider: null,
  oauthTokenEndpoint: null,
  oauthTenantId: null,
  oauthScope: null,
  oauthCredentialKey: null,
  passwordConfigured: true,
  oauthEnvConfigured: false,
  oauthMissingEnvKeys: [],
  oauthConnected: null,
  lastFetchAt: null,
  lastFetchStatus: null,
  lastFetchError: null,
};

const rule: MailRule = {
  id: 'rule-1',
  name: 'Capture invoices',
  accountId: 'acct-1',
  priority: 5,
  enabled: true,
  folder: 'INBOX',
  subjectFilter: null,
  fromFilter: null,
  toFilter: null,
  bodyFilter: null,
  attachmentFilenameInclude: null,
  attachmentFilenameExclude: null,
  maxAgeDays: 30,
  includeInlineAttachments: false,
  actionType: 'ATTACHMENTS_ONLY',
  mailAction: 'MARK_READ',
  mailActionParam: null,
  assignTagId: null,
  assignFolderId: null,
};

const connectionTest: MailConnectionTestResult = {
  success: true,
  message: 'connected',
  durationMs: 120,
};

const fetchSummary: MailFetchSummary = {
  accounts: 1,
  attemptedAccounts: 1,
  skippedAccounts: 0,
  accountErrors: 0,
  foundMessages: 4,
  matchedMessages: 2,
  processedMessages: 2,
  skippedMessages: 0,
  errorMessages: 0,
  durationMs: 1500,
  runId: 'run-1',
};

const fetchSummaryStatus: MailFetchSummaryStatus = {
  summary: fetchSummary,
  fetchedAt: '2026-05-18T01:00:00Z',
};

const debugResult: MailFetchDebugResult = {
  summary: fetchSummary,
  maxMessagesPerFolder: 20,
  skipReasons: { duplicate: 1 },
  accounts: [
    {
      accountId: 'acct-1',
      accountName: 'Primary Inbox',
      attempted: true,
      skipReason: null,
      accountError: null,
      rules: 1,
      folders: 1,
      foundMessages: 4,
      scannedMessages: 4,
      matchedMessages: 2,
      processableMessages: 2,
      skippedMessages: 0,
      errorMessages: 0,
      skipReasons: { duplicate: 1 },
      ruleMatches: { 'rule-1': 2 },
      folderResults: [
        {
          folder: 'INBOX',
          rules: 1,
          foundMessages: 4,
          scannedMessages: 4,
          matchedMessages: 2,
          processableMessages: 2,
          skippedMessages: 0,
          errorMessages: 0,
          skipReasons: { duplicate: 1 },
        },
      ],
    },
  ],
};

const previewResult: MailRulePreviewResult = {
  accountId: 'acct-1',
  accountName: 'Primary Inbox',
  ruleId: 'rule-1',
  ruleName: 'Capture invoices',
  maxMessagesPerFolder: 20,
  foundMessages: 4,
  scannedMessages: 4,
  matchedMessages: 1,
  processableMessages: 1,
  skippedMessages: 0,
  errorMessages: 0,
  skipReasons: {},
  matches: [
    {
      folder: 'INBOX',
      uid: '42',
      subject: 'Invoice #200',
      from: 'billing@example.com',
      recipients: 'ops@example.com',
      receivedAt: '2026-05-18T00:00:00Z',
      attachmentCount: 1,
      processable: true,
    },
  ],
  runId: 'preview-1',
};

const diagnosticsResult: MailDiagnosticsResult = {
  limit: 25,
  recentProcessed: [
    {
      id: 'proc-1',
      processedAt: '2026-05-18T01:00:00Z',
      status: 'PROCESSED',
      accountId: 'acct-1',
      accountName: 'Primary Inbox',
      ruleId: 'rule-1',
      ruleName: 'Capture invoices',
      folder: 'INBOX',
      uid: '42',
      subject: 'Invoice #200',
      errorMessage: null,
    },
  ],
  recentDocuments: [
    {
      documentId: 'doc-1',
      name: 'Invoice-200.pdf',
      path: '/sites/finance/invoices',
      createdDate: '2026-05-18T01:00:01Z',
      createdBy: 'ops@example.com',
      mimeType: 'application/pdf',
      fileSize: 12345,
      accountId: 'acct-1',
      accountName: 'Primary Inbox',
      ruleId: 'rule-1',
      ruleName: 'Capture invoices',
      folder: 'INBOX',
      uid: '42',
    },
  ],
};

const reportResponse: MailReportResponse = {
  accountId: 'acct-1',
  ruleId: null,
  startDate: '2026-05-11',
  endDate: '2026-05-18',
  days: 7,
  totals: { processed: 12, errors: 1, total: 13 },
  accounts: [
    {
      accountId: 'acct-1',
      accountName: 'Primary Inbox',
      processed: 12,
      errors: 1,
      total: 13,
      lastProcessedAt: '2026-05-18T01:00:00Z',
      lastErrorAt: null,
    },
  ],
  rules: [
    {
      ruleId: 'rule-1',
      ruleName: 'Capture invoices',
      accountId: 'acct-1',
      accountName: 'Primary Inbox',
      processed: 12,
      errors: 1,
      total: 13,
      lastProcessedAt: '2026-05-18T01:00:00Z',
      lastErrorAt: null,
    },
  ],
  trend: [
    { date: '2026-05-17', processed: 6, errors: 0, total: 6 },
    { date: '2026-05-18', processed: 6, errors: 1, total: 7 },
  ],
};

const scheduledExport: MailReportScheduledExportResult = {
  attempted: true,
  success: true,
  status: 'OK',
  message: 'Export complete',
  manual: true,
  filename: 'mail-report.csv',
  folderId: 'folder-1',
  documentId: 'doc-export-1',
  startedAt: '2026-05-18T02:00:00Z',
  finishedAt: '2026-05-18T02:00:05Z',
  durationMs: 5000,
  days: 7,
};

const scheduleStatus: MailReportScheduleStatus = {
  enabled: true,
  cron: '0 0 * * *',
  folderId: 'folder-1',
  days: 7,
  accountId: null,
  ruleId: null,
  lastExport: scheduledExport,
};

const retentionStatus: ProcessedMailRetentionStatus = {
  retentionDays: 30,
  enabled: true,
  expiredCount: 5,
};

const replayResult: MailReplayResult = {
  processedMailId: 'proc-1',
  attempted: true,
  processed: true,
  message: 'Replayed',
  replayStatus: 'REPLAYED',
};

const runtimeMetrics: MailRuntimeMetrics = {
  windowMinutes: 15,
  attempts: 10,
  successes: 9,
  errors: 1,
  errorRate: 0.1,
  avgDurationMs: 250,
  lastSuccessAt: '2026-05-18T01:00:00Z',
  lastErrorAt: '2026-05-18T00:30:00Z',
  status: 'HEALTHY',
  topErrors: [
    { errorMessage: 'timeout', count: 1, lastSeenAt: '2026-05-18T00:30:00Z' },
  ],
  trend: {
    direction: 'STABLE',
    currentTotal: 10,
    previousTotal: 10,
    deltaTotal: 0,
    currentErrorRate: 0.1,
    previousErrorRate: 0.1,
    deltaErrorRate: 0,
    summary: 'no change',
  },
};

const providerPreset: MailProviderPreset = {
  id: 'ALIYUN_QIYE',
  label: 'Aliyun Enterprise',
  imapHost: 'imap.qiye.aliyun.com',
  imapPort: 993,
  imapSecurity: 'SSL',
  smtpHost: 'smtp.qiye.aliyun.com',
  smtpPort: 465,
  smtpSecurity: 'SSL',
};

const smtpResponse: EmailTestSmtpResponse = {
  ok: true,
  message: 'sent',
  smtpHost: 'smtp.example.com',
  smtpPort: 587,
  fromAddress: 'noreply@example.com',
  diagnostic: null,
};

const expectMailUnexpectedResponse = async (promise: Promise<unknown>) => {
  await expect(promise).rejects.toThrow(MAIL_AUTOMATION_UNEXPECTED_RESPONSE_MESSAGE);
};

describe('mailAutomationService response shape guards', () => {
  beforeEach(() => {
    jest.clearAllMocks();
  });

  it('guards account CRUD, OAuth reset, and connection test responses', async () => {
    mockedApi.get.mockResolvedValueOnce([account]);
    mockedApi.post
      .mockResolvedValueOnce(account)
      .mockResolvedValueOnce({ ...account, id: 'acct-2' })
      .mockResolvedValueOnce(connectionTest);
    mockedApi.put.mockResolvedValueOnce({ ...account, name: 'Renamed Inbox' });
    mockedApi.delete.mockResolvedValueOnce(undefined);

    await expect(mailAutomationService.listAccounts()).resolves.toEqual([account]);
    await expect(
      mailAutomationService.createAccount({
        name: account.name,
        host: account.host,
        port: account.port,
        username: account.username,
      }),
    ).resolves.toEqual(account);
    await expect(
      mailAutomationService.updateAccount('acct-1', {
        name: 'Renamed Inbox',
        host: account.host,
        port: account.port,
        username: account.username,
      }),
    ).resolves.toMatchObject({ name: 'Renamed Inbox' });
    await expect(mailAutomationService.resetOAuth('acct-1')).resolves.toMatchObject({ id: 'acct-2' });
    await expect(mailAutomationService.testConnection('acct-1')).resolves.toEqual(connectionTest);
    await expect(mailAutomationService.deleteAccount('acct-1')).resolves.toBeUndefined();

    expect(mockedApi.get).toHaveBeenCalledWith('/integration/mail/accounts');
    expect(mockedApi.post).toHaveBeenNthCalledWith(1, '/integration/mail/accounts', {
      name: account.name,
      host: account.host,
      port: account.port,
      username: account.username,
    });
    expect(mockedApi.put).toHaveBeenCalledWith('/integration/mail/accounts/acct-1', {
      name: 'Renamed Inbox',
      host: account.host,
      port: account.port,
      username: account.username,
    });
    expect(mockedApi.post).toHaveBeenNthCalledWith(2, '/integration/mail/accounts/acct-1/oauth/reset');
    expect(mockedApi.post).toHaveBeenNthCalledWith(3, '/integration/mail/accounts/acct-1/test');
    expect(mockedApi.delete).toHaveBeenCalledWith('/integration/mail/accounts/acct-1');
  });

  it('guards rule CRUD, OAuth authorize URL, folder list, and preview responses', async () => {
    mockedApi.get
      .mockResolvedValueOnce([rule])
      .mockResolvedValueOnce({ url: 'https://login.example.com/oauth', state: 'xyz' })
      .mockResolvedValueOnce(['INBOX', 'Sent']);
    mockedApi.post
      .mockResolvedValueOnce(rule)
      .mockResolvedValueOnce(previewResult);
    mockedApi.put.mockResolvedValueOnce({ ...rule, name: 'Capture invoices v2' });
    mockedApi.delete.mockResolvedValueOnce(undefined);

    await expect(mailAutomationService.listRules()).resolves.toEqual([rule]);
    await expect(
      mailAutomationService.createRule({
        name: rule.name,
        actionType: rule.actionType,
        priority: rule.priority,
      }),
    ).resolves.toEqual(rule);
    await expect(
      mailAutomationService.updateRule('rule-1', { name: 'Capture invoices v2' }),
    ).resolves.toMatchObject({ name: 'Capture invoices v2' });
    await expect(mailAutomationService.deleteRule('rule-1')).resolves.toBeUndefined();
    await expect(
      mailAutomationService.getOAuthAuthorizeUrl('acct-1', 'https://app/callback'),
    ).resolves.toEqual({ url: 'https://login.example.com/oauth', state: 'xyz' });
    await expect(mailAutomationService.listFolders('acct-1')).resolves.toEqual(['INBOX', 'Sent']);
    await expect(mailAutomationService.previewRule('rule-1', 'acct-1', 20)).resolves.toEqual(previewResult);

    expect(mockedApi.get).toHaveBeenNthCalledWith(1, '/integration/mail/rules');
    expect(mockedApi.post).toHaveBeenNthCalledWith(1, '/integration/mail/rules', {
      name: rule.name,
      actionType: rule.actionType,
      priority: rule.priority,
    });
    expect(mockedApi.put).toHaveBeenCalledWith('/integration/mail/rules/rule-1', {
      name: 'Capture invoices v2',
    });
    expect(mockedApi.delete).toHaveBeenCalledWith('/integration/mail/rules/rule-1');
    expect(mockedApi.get).toHaveBeenNthCalledWith(2, '/integration/mail/oauth/authorize', {
      params: { accountId: 'acct-1', redirectUrl: 'https://app/callback' },
    });
    expect(mockedApi.get).toHaveBeenNthCalledWith(3, '/integration/mail/accounts/acct-1/folders');
    expect(mockedApi.post).toHaveBeenNthCalledWith(2, '/integration/mail/rules/rule-1/preview', {
      accountId: 'acct-1',
      maxMessagesPerFolder: 20,
    });
  });

  it('guards diagnostics, report, schedule, retention, and runtime metrics responses', async () => {
    mockedApi.get
      .mockResolvedValueOnce(diagnosticsResult)
      .mockResolvedValueOnce(reportResponse)
      .mockResolvedValueOnce(scheduleStatus)
      .mockResolvedValueOnce(retentionStatus)
      .mockResolvedValueOnce(runtimeMetrics)
      .mockResolvedValueOnce(fetchSummaryStatus);
    mockedApi.post
      .mockResolvedValueOnce(scheduledExport)
      .mockResolvedValueOnce({ deleted: 3 })
      .mockResolvedValueOnce(replayResult)
      .mockResolvedValueOnce({ deleted: 9 })
      .mockResolvedValueOnce(fetchSummary)
      .mockResolvedValueOnce(debugResult);

    await expect(
      mailAutomationService.getDiagnostics(25, { accountId: 'acct-1', status: 'PROCESSED' }),
    ).resolves.toEqual(diagnosticsResult);
    await expect(
      mailAutomationService.getReport({ accountId: 'acct-1', from: '2026-05-11', to: '2026-05-18' }),
    ).resolves.toEqual(reportResponse);
    await expect(mailAutomationService.getReportSchedule()).resolves.toEqual(scheduleStatus);
    await expect(mailAutomationService.runReportScheduleNow()).resolves.toEqual(scheduledExport);
    await expect(mailAutomationService.bulkDeleteProcessedMail(['proc-1', 'proc-2'])).resolves.toEqual({ deleted: 3 });
    await expect(mailAutomationService.replayProcessedMail('proc-1')).resolves.toEqual(replayResult);
    await expect(mailAutomationService.getProcessedRetention()).resolves.toEqual(retentionStatus);
    await expect(mailAutomationService.cleanupProcessedRetention()).resolves.toEqual({ deleted: 9 });
    await expect(mailAutomationService.getRuntimeMetrics(15)).resolves.toEqual(runtimeMetrics);
    await expect(mailAutomationService.triggerFetch()).resolves.toEqual(fetchSummary);
    await expect(mailAutomationService.getFetchSummary()).resolves.toEqual(fetchSummaryStatus);
    await expect(mailAutomationService.triggerFetchDebug({ maxMessagesPerFolder: 20 })).resolves.toEqual(debugResult);

    expect(mockedApi.get).toHaveBeenNthCalledWith(1, '/integration/mail/diagnostics', {
      params: {
        limit: 25,
        accountId: 'acct-1',
        ruleId: undefined,
        status: 'PROCESSED',
        subject: undefined,
        errorContains: undefined,
        processedFrom: undefined,
        processedTo: undefined,
        sort: undefined,
        order: undefined,
      },
    });
    expect(mockedApi.get).toHaveBeenNthCalledWith(2, '/integration/mail/report', {
      params: {
        accountId: 'acct-1',
        ruleId: undefined,
        from: '2026-05-11',
        to: '2026-05-18',
        days: undefined,
      },
    });
    expect(mockedApi.get).toHaveBeenNthCalledWith(3, '/integration/mail/report/schedule');
    expect(mockedApi.get).toHaveBeenNthCalledWith(4, '/integration/mail/processed/retention');
    expect(mockedApi.get).toHaveBeenNthCalledWith(5, '/integration/mail/runtime-metrics', {
      params: { windowMinutes: 15 },
    });
    expect(mockedApi.get).toHaveBeenNthCalledWith(6, '/integration/mail/fetch/summary');

    expect(mockedApi.post).toHaveBeenNthCalledWith(1, '/integration/mail/report/schedule/run');
    expect(mockedApi.post).toHaveBeenNthCalledWith(2, '/integration/mail/processed/bulk-delete', {
      ids: ['proc-1', 'proc-2'],
    });
    expect(mockedApi.post).toHaveBeenNthCalledWith(3, '/integration/mail/processed/proc-1/replay');
    expect(mockedApi.post).toHaveBeenNthCalledWith(4, '/integration/mail/processed/cleanup');
    expect(mockedApi.post).toHaveBeenNthCalledWith(5, '/integration/mail/fetch');
    expect(mockedApi.post).toHaveBeenNthCalledWith(6, '/integration/mail/fetch/debug', undefined, {
      params: { force: true, maxMessagesPerFolder: 20 },
    });
  });

  it('returns processed mail documents list and preserves the limit param', async () => {
    mockedApi.get.mockResolvedValueOnce(diagnosticsResult.recentDocuments);

    await expect(mailAutomationService.listProcessedMailDocuments('proc-1', 5))
      .resolves.toEqual(diagnosticsResult.recentDocuments);

    expect(mockedApi.get).toHaveBeenCalledWith('/integration/mail/processed/proc-1/documents', {
      params: { limit: 5 },
    });
  });

  it('falls back to an empty preset list on HTML fallback but returns the array when valid', async () => {
    mockedApi.get
      .mockResolvedValueOnce([providerPreset])
      .mockResolvedValueOnce('<!doctype html>')
      .mockResolvedValueOnce([{ ...providerPreset, imapPort: 'no' }]);

    await expect(mailAutomationService.listProviderPresets()).resolves.toEqual([providerPreset]);
    await expect(mailAutomationService.listProviderPresets()).resolves.toEqual([]);
    await expect(mailAutomationService.listProviderPresets()).resolves.toEqual([]);

    expect(mockedApi.get).toHaveBeenCalledWith('/integration/mail/provider-presets');
  });

  it('guards testSmtp with the dedicated TEST_SMTP_UNEXPECTED_RESPONSE_MESSAGE sentinel', async () => {
    mockedApi.post
      .mockResolvedValueOnce(smtpResponse)
      .mockResolvedValueOnce('<!doctype html>');

    await expect(mailAutomationService.testSmtp({ to: 'ops@example.com' })).resolves.toEqual(smtpResponse);
    await expect(mailAutomationService.testSmtp({ to: 'ops@example.com' })).rejects.toThrow(
      TEST_SMTP_UNEXPECTED_RESPONSE_MESSAGE,
    );

    expect(mockedApi.post).toHaveBeenNthCalledWith(1, '/admin/email/test-smtp', { to: 'ops@example.com' });
    expect(mockedApi.post).toHaveBeenNthCalledWith(2, '/admin/email/test-smtp', { to: 'ops@example.com' });
  });

  it('rejects HTML fallback and malformed JSON for representative endpoints', async () => {
    mockedApi.get
      .mockResolvedValueOnce('<!doctype html>')
      .mockResolvedValueOnce([{ ...account, port: 'not-a-port' }])
      .mockResolvedValueOnce({ url: 123 })
      .mockResolvedValueOnce([{ name: 'INBOX' }])
      .mockResolvedValueOnce({ ...reportResponse, totals: null })
      .mockResolvedValueOnce({ ...retentionStatus, retentionDays: 'forever' });
    mockedApi.post
      .mockResolvedValueOnce({ ...rule, actionType: 42 })
      .mockResolvedValueOnce({ ...fetchSummary, durationMs: 'fast' })
      .mockResolvedValueOnce({ deletedCount: 1 });

    await expectMailUnexpectedResponse(mailAutomationService.listAccounts());
    await expectMailUnexpectedResponse(mailAutomationService.listAccounts());
    await expectMailUnexpectedResponse(mailAutomationService.getOAuthAuthorizeUrl('acct-1'));
    await expectMailUnexpectedResponse(mailAutomationService.listFolders('acct-1'));
    await expectMailUnexpectedResponse(mailAutomationService.getReport());
    await expectMailUnexpectedResponse(mailAutomationService.getProcessedRetention());
    await expectMailUnexpectedResponse(
      mailAutomationService.createRule({ name: rule.name, actionType: rule.actionType }),
    );
    await expectMailUnexpectedResponse(mailAutomationService.triggerFetch());
    await expectMailUnexpectedResponse(mailAutomationService.bulkDeleteProcessedMail(['proc-1']));
  });
});

describe('mailAutomationService.exportPreviewMatches', () => {
  beforeEach(() => {
    jest.clearAllMocks();
  });

  const exportRequest = {
    accountId: 'acct-1',
    targetFolderId: 'fold-1',
    selections: [{ folder: 'INBOX', uid: '1' }, { folder: 'INBOX', uid: '2' }],
  };

  it('posts to the export route and returns a parsed mixed-row result', async () => {
    const result: MailRulePreviewExportResult = {
      accountId: 'acct-1',
      ruleId: 'rule-1',
      targetFolderId: 'fold-1',
      exported: 1,
      skipped: 0,
      failed: 1,
      rows: [
        { folder: 'INBOX', uid: '1', status: 'EXPORTED', errorCategory: null, errorMessage: null },
        { folder: 'INBOX', uid: '2', status: 'FAILED', errorCategory: 'INTERNAL_ERROR', errorMessage: 'Failed to export the mail message into the target folder. (MessagingException).' },
      ],
    };
    mockedApi.post.mockResolvedValueOnce(result);

    await expect(mailAutomationService.exportPreviewMatches('rule-1', exportRequest)).resolves.toEqual(result);
    expect(mockedApi.post).toHaveBeenCalledWith('/integration/mail/rules/rule-1/preview/export', exportRequest);
  });

  it('accepts explicit JSON null on a non-failed row', async () => {
    mockedApi.post.mockResolvedValueOnce({
      accountId: 'acct-1', ruleId: 'rule-1', targetFolderId: 'fold-1',
      exported: 0, skipped: 1, failed: 0,
      rows: [{ folder: 'INBOX', uid: '1', status: 'SKIPPED_ALREADY_PROCESSED', errorCategory: null, errorMessage: null }],
    });
    await expect(mailAutomationService.exportPreviewMatches('rule-1', exportRequest)).resolves.toBeTruthy();
  });

  it('rejects an HTML/SPA fallback (no rows array) with the sentinel', async () => {
    mockedApi.post.mockResolvedValueOnce('<!doctype html><html><body>app</body></html>');
    await expectMailUnexpectedResponse(mailAutomationService.exportPreviewMatches('rule-1', exportRequest));
  });

  it('rejects an EXPORTED row carrying an error message (status-keyed invariant)', async () => {
    mockedApi.post.mockResolvedValueOnce({
      accountId: 'acct-1', ruleId: 'rule-1', targetFolderId: 'fold-1',
      exported: 1, skipped: 0, failed: 0,
      rows: [{ folder: 'INBOX', uid: '1', status: 'EXPORTED', errorCategory: null, errorMessage: 'leaked' }],
    });
    await expectMailUnexpectedResponse(mailAutomationService.exportPreviewMatches('rule-1', exportRequest));
  });

  it('rejects a FAILED row missing its error category', async () => {
    mockedApi.post.mockResolvedValueOnce({
      accountId: 'acct-1', ruleId: 'rule-1', targetFolderId: 'fold-1',
      exported: 0, skipped: 0, failed: 1,
      rows: [{ folder: 'INBOX', uid: '1', status: 'FAILED', errorCategory: null, errorMessage: 'boom' }],
    });
    await expectMailUnexpectedResponse(mailAutomationService.exportPreviewMatches('rule-1', exportRequest));
  });

  it('rejects an unknown row status', async () => {
    mockedApi.post.mockResolvedValueOnce({
      accountId: 'acct-1', ruleId: 'rule-1', targetFolderId: 'fold-1',
      exported: 0, skipped: 0, failed: 0,
      rows: [{ folder: 'INBOX', uid: '1', status: 'TELEPORTED', errorCategory: null, errorMessage: null }],
    });
    await expectMailUnexpectedResponse(mailAutomationService.exportPreviewMatches('rule-1', exportRequest));
  });
});
