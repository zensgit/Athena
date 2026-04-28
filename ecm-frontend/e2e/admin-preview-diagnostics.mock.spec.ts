/* eslint-disable testing-library/prefer-screen-queries */
import { expect, test } from '@playwright/test';
import { seedBypassSessionE2E } from './helpers/login';

test('Preview diagnostics renders failures and gates retry actions (mocked API)', async ({ page }) => {
  test.setTimeout(120_000);

  const retryableId = '11111111-1111-1111-1111-111111111111';
  const unsupportedId = '22222222-2222-2222-2222-222222222222';
  const permanentId = '33333333-3333-3333-3333-333333333333';

  const retryableName = 'e2e-preview-diagnostics-retryable.pdf';
  const unsupportedName = 'e2e-preview-diagnostics-unsupported.bin';
  const permanentName = 'e2e-preview-diagnostics-permanent.pdf';
  const retryableTwinId = '44444444-4444-4444-4444-444444444444';
  const retryableTwinName = 'e2e-preview-diagnostics-retryable-twin.pdf';
  const blockedPreventionId = '55555555-5555-5555-5555-555555555555';
  const blockedPreventionName = 'e2e-preview-prevention-blocked.bin';
  const deadLetterId = '66666666-6666-6666-6666-666666666666';
  const deadLetterName = 'e2e-preview-dead-letter-replay.pdf';
  const renditionResourceRetryId = '77777777-7777-7777-7777-777777777777';
  const renditionResourceUnsupportedId = '88888888-8888-8888-8888-888888888888';

  const queueCalls: Array<{ id: string; force: boolean }> = [];
  const preventionActionCalls: Array<{ id: string; action: 'unblock' | 'requeue'; force?: boolean }> = [];
  const preventionBatchCalls: Array<{ action: 'unblock' | 'requeue'; ids: string[]; force?: boolean }> = [];
  const deadLetterReplayBatchCalls: Array<{ ids: string[]; force: boolean }> = [];
  const deadLetterClearBatchCalls: Array<{ ids: string[] }> = [];
  const deadLetterClearByFilterCalls: Array<{
    reason: string;
    category: string;
    retryable: boolean;
    days: number;
    maxDocuments: number;
  }> = [];
  const deadLetterReplayByFilterCalls: Array<{
    reason: string;
    category: string;
    retryable: boolean;
    days: number;
    maxDocuments: number;
    force: boolean;
  }> = [];
  const deadLetterExportCalls: number[] = [];
  const failureLedgerRequestedDays: string[] = [];
  const failureLedgerResetCalls: string[] = [];
  const failureLedgerResetBatchCalls: string[][] = [];
  const failureLedgerResetByFilterCalls: Array<{
    reason: string;
    category: string;
    retryable: boolean;
    days: number;
    maxDocuments: number;
  }> = [];
  const failureLedgerExportCalls: Array<{ days: string; limit: string }> = [];
  const queueSummaryCalls: Array<{ limit: string; state: string; query: string }> = [];
  const queueSummaryExportCalls: Array<{ limit: string; state: string; query: string }> = [];
  const queueCancelActiveCalls: Array<{ limit: string; state: string; query: string }> = [];
  const queueDeclinedSummaryCalls: Array<{ limit: string; category: string; forceRequired: string; windowHours: string; query: string }> = [];
  const queueDeclinedExportCalls: Array<{ limit: string; category: string; forceRequired: string; windowHours: string; query: string }> = [];
  const queueDeclinedDryRunCalls: Array<{ limit: string; category: string; forceRequired: string; windowHours: string; query: string; force: boolean }> = [];
  const queueDeclinedDryRunExportCalls: Array<{ limit: string; category: string; forceRequired: string; windowHours: string; query: string; force: boolean }> = [];
  const queueDeclinedRequeueCalls: Array<{ limit: string; category: string; forceRequired: string; windowHours: string; query: string; force: boolean }> = [];
  const queueDeclinedClearCalls: Array<{ limit: string; category: string; forceRequired: string; windowHours: string; query: string }> = [];
  const queueDeclinedDryRunExportAsyncStartCalls: Array<{
    limit: string;
    category: string;
    forceRequired: string;
    windowHours: string;
    query: string;
    force: boolean;
  }> = [];
  const queueDeclinedDryRunExportAsyncListCalls: Array<{ limit: number; skipCount: number; status: string }> = [];
  const queueDeclinedDryRunExportAsyncSummaryCalls: Array<{ status: string }> = [];
  const queueDeclinedDryRunExportAsyncCleanupCalls: Array<{ status: string }> = [];
  const queueDeclinedDryRunExportAsyncCancelActiveCalls: Array<{ status: string }> = [];
  const queueDeclinedDryRunExportAsyncRetryTerminalDryRunCalls: Array<{ status: string; limit: number }> = [];
  const queueDeclinedDryRunExportAsyncRetryTerminalDryRunExportCalls: Array<{ status: string; limit: number }> = [];
  const queueDeclinedDryRunExportAsyncRetrySelectedCalls: Array<{ sourceTaskIds: string[] }> = [];
  const queueDeclinedDryRunExportAsyncRetryTerminalCalls: Array<{ status: string; limit: number }> = [];
  const queueDeclinedDryRunExportAsyncGetCalls: string[] = [];
  const queueDeclinedDryRunExportAsyncCancelCalls: string[] = [];
  const queueDeclinedDryRunExportAsyncRetryCalls: string[] = [];
  const queueDeclinedDryRunExportAsyncRetryDedupCalls: Array<{ sourceTaskId: string; reusedTaskId: string }> = [];
  const queueDeclinedDryRunExportAsyncDownloadCalls: string[] = [];
  const queueDeclinedDryRunExportAsyncStartedTaskId = 'queue-declined-dry-run-export-task-started-0001';
  const queueDeclinedDryRunExportAsyncCancelledTaskId = 'queue-declined-dry-run-export-task-started-0002';
  const queueDeclinedDryRunExportAsyncRetriedTaskId = 'queue-declined-dry-run-export-task-retried-0001';
  const queueDeclinedDryRunExportAsyncRetryTerminalTaskId = 'queue-declined-dry-run-export-task-retry-terminal-0001';
  const queueDeclinedDryRunExportAsyncTasks = new Map<string, {
    taskId: string;
    status: string;
    createdAt: string;
    finishedAt: string | null;
    filename: string | null;
    error: string | null;
    message: string | null;
    categoryFilter: string | null;
    forceRequiredFilter: string | null;
    queryFilter: string | null;
    windowHoursFilter: number | null;
    limit: number | null;
    forceFilter: boolean;
    listPollCount: number;
  }>();
  let queueDeclinedDryRunExportAsyncStartSequence = 0;
  let queueDeclinedDryRunExportAsyncRetryTerminalSequence = 0;
  let queueDeclinedDryRunExportAsyncRetrySelectedSequence = 0;
  let queueDeclinedDryRunAsyncTaskCenterCovered = false;
  const queueDeclinedExportAsyncStartCalls: Array<{
    limit: string;
    category: string;
    forceRequired: string;
    windowHours: string;
    query: string;
  }> = [];
  const queueDeclinedExportAsyncStartDedupCalls: Array<{ reusedTaskId: string }> = [];
  const queueDeclinedExportAsyncListCalls: Array<{ limit: number; skipCount: number; status: string }> = [];
  const queueDeclinedExportAsyncSummaryCalls: Array<{ status: string }> = [];
  const queueDeclinedExportAsyncCleanupCalls: Array<{ status: string }> = [];
  const queueDeclinedExportAsyncCancelActiveCalls: Array<{ status: string }> = [];
  const queueDeclinedExportAsyncRetryTerminalDryRunCalls: Array<{ status: string; limit: number }> = [];
  const queueDeclinedExportAsyncRetryTerminalDryRunExportCalls: Array<{ status: string; limit: number }> = [];
  const queueDeclinedExportAsyncRetrySelectedCalls: Array<{ sourceTaskIds: string[] }> = [];
  const queueDeclinedExportAsyncRetryTerminalCalls: Array<{ status: string; limit: number }> = [];
  const queueDeclinedExportAsyncGetCalls: string[] = [];
  const queueDeclinedExportAsyncCancelCalls: string[] = [];
  const queueDeclinedExportAsyncRetryCalls: string[] = [];
  const queueDeclinedExportAsyncRetryDedupCalls: Array<{ sourceTaskId: string; reusedTaskId: string }> = [];
  const queueDeclinedExportAsyncDownloadCalls: string[] = [];
  const queueDeclinedExportAsyncStartedTaskId = 'queue-declined-export-task-started-0001';
  const queueDeclinedExportAsyncCancelledTaskId = 'queue-declined-export-task-started-0002';
  const queueDeclinedExportAsyncRetriedTaskId = 'queue-declined-export-task-retried-0001';
  const queueDeclinedExportAsyncRetryTerminalTaskId = 'queue-declined-export-task-retry-terminal-0001';
  const queueDeclinedExportAsyncTasks = new Map<string, {
    taskId: string;
    status: string;
    createdAt: string;
    finishedAt: string | null;
    filename: string | null;
    error: string | null;
    message: string | null;
    categoryFilter: string | null;
    forceRequiredFilter: string | null;
    queryFilter: string | null;
    windowHoursFilter: number | null;
    limit: number | null;
    retrySourceTaskId?: string | null;
    listPollCount: number;
  }>();
  let queueDeclinedExportAsyncStartSequence = 0;
  let queueDeclinedExportAsyncRetrySequence = 0;
  let queueDeclinedExportAsyncRetryTerminalSequence = 0;
  let queueDeclinedExportAsyncRetrySelectedSequence = 0;
  const reasonDryRunCalls: Array<{
    mode: string;
    reason: string;
    category: string;
    retryable: boolean;
    days: number;
    maxDocuments: number;
    force: boolean;
  }> = [];
  const queueWindowCalls: Array<{
    reason: string;
    category: string;
    retryable: boolean;
    days: number;
    maxDocuments: number;
    force: boolean;
  }> = [];
  const policyRollbackCalls: number[] = [];
  const policyHistoryCalls: number[] = [];
  const recoveryHistoryCalls: Array<{
    days: string;
    mode: string;
    page: number;
    limit: number;
    actor: string;
    eventType: string;
  }> = [];
  const recoveryHistoryExportCalls: Array<{ days: string; mode: string; limit: number; actor: string; eventType: string }> = [];
  const recoveryHistoryExportAsyncStartCalls: Array<{
    exportType: string;
    days: number;
    mode: string;
    actor: string;
    eventType: string;
    limit: number;
    compareBreakdownLimit: number;
    compareBreakdownSort: string;
    compareActorLimit: number;
    compareActorSort: string;
  }> = [];
  const recoveryHistoryExportAsyncListCalls: Array<{ limit: number; skipCount: number; exportType: string; status: string }> = [];
  const recoveryHistoryExportAsyncSummaryCalls: Array<{ exportType: string; status: string }> = [];
  const recoveryHistoryExportAsyncCleanupCalls: Array<{ exportType: string; status: string }> = [];
  const recoveryHistoryExportAsyncRetryTerminalDryRunCalls: Array<{ exportType: string; status: string; limit: number }> = [];
  const recoveryHistoryExportAsyncRetryTerminalDryRunExportCalls: Array<{ exportType: string; status: string; limit: number }> = [];
  const recoveryHistoryExportAsyncRetrySelectedCalls: Array<{ exportType: string; sourceTaskIds: string[] }> = [];
  const recoveryHistoryExportAsyncGetCalls: string[] = [];
  const recoveryHistoryExportAsyncCancelCalls: string[] = [];
  const recoveryHistoryExportAsyncDownloadCalls: string[] = [];
  const recoveryHistorySummaryCalls: Array<{ days: string; mode: string; actor: string; eventType: string }> = [];
  const recoveryHistoryCompareCalls: Array<{ days: string; mode: string; actor: string; eventType: string }> = [];
  const recoveryHistoryCompareActorCalls: Array<{
    days: string;
    mode: string;
    actor: string;
    eventType: string;
    limit: number;
    sort: string;
  }> = [];
  const recoveryHistoryCompareBreakdownCalls: Array<{
    days: string;
    mode: string;
    actor: string;
    eventType: string;
    limit: number;
    sort: string;
  }> = [];
  const recoveryHistoryTrendCalls: Array<{ days: string; mode: string; actor: string; eventType: string }> = [];
  const recoveryHistorySummaryExportCalls: Array<{ days: string; mode: string; actor: string; eventType: string }> = [];
  const recoveryHistoryCompareExportCalls: Array<{ days: string; mode: string; actor: string; eventType: string }> = [];
  const recoveryHistoryCompareActorExportCalls: Array<{
    days: string;
    mode: string;
    actor: string;
    eventType: string;
    limit: number;
    sort: string;
  }> = [];
  const recoveryHistoryCompareBreakdownExportCalls: Array<{
    days: string;
    mode: string;
    actor: string;
    eventType: string;
    limit: number;
    sort: string;
  }> = [];
  const recoveryHistoryTrendExportCalls: Array<{ days: string; mode: string; actor: string; eventType: string }> = [];
  const reasonBatchCalls: Array<{
    reason: string;
    category: string;
    retryable: boolean;
    days: number;
    maxDocuments: number;
    force: boolean;
  }> = [];
  const requestedFailureDays: string[] = [];
  const requestedSummaryDays: string[] = [];
  const requestedRenditionSummaryDays: string[] = [];
  const requestedRenditionSummarySampleLimits: string[] = [];
  const requestedRenditionResourceDays: string[] = [];
  const requestedRenditionResourceLimits: string[] = [];
  const requestedRenditionResourceExportCalls: Array<{ days: string; limit: string }> = [];
  const renditionResourceExportAsyncStartCalls: Array<{ days: number; limit: number }> = [];
  const renditionResourceExportAsyncListCalls: Array<{ limit: number; skipCount: number; status: string }> = [];
  const renditionResourceExportAsyncSummaryCalls: Array<{ status: string }> = [];
  const renditionResourceExportAsyncCancelActiveCalls: Array<{ status: string }> = [];
  const renditionResourceExportAsyncCleanupCalls: Array<{ status: string }> = [];
  const renditionResourceExportAsyncRetryTerminalDryRunCalls: Array<{ status: string; limit: number }> = [];
  const renditionResourceExportAsyncRetryTerminalDryRunExportCalls: Array<{ status: string; limit: number }> = [];
  const renditionResourceExportAsyncRetrySelectedCalls: Array<{ sourceTaskIds: string[] }> = [];
  const renditionResourceExportAsyncGetCalls: string[] = [];
  const renditionResourceExportAsyncCancelCalls: string[] = [];
  const renditionResourceExportAsyncDownloadCalls: string[] = [];
  const renditionResourceExportAsyncStartedTaskId = 'rendition-export-task-started-0001';
  const renditionResourceExportAsyncCompletedTaskId = 'rendition-export-task-completed-0001';
  const renditionResourceExportAsyncRetriedTaskId = 'rendition-export-task-retried-0001';
  const renditionResourceExportAsyncTasks = new Map<string, {
    taskId: string;
    status: string;
    createdAt: string;
    finishedAt: string | null;
    filename: string | null;
    error: string | null;
    message: string | null;
    retrySourceTaskId?: string | null;
  }>();
  let renditionResourceExportAsyncRetrySelectedSequence = 0;
  renditionResourceExportAsyncTasks.set(renditionResourceExportAsyncCompletedTaskId, {
    taskId: renditionResourceExportAsyncCompletedTaskId,
    status: 'COMPLETED',
    createdAt: new Date(Date.now() - 90_000).toISOString(),
    finishedAt: new Date(Date.now() - 60_000).toISOString(),
    filename: 'preview_rendition_resources_async_completed.csv',
    error: null,
    message: null,
  });
  const recoveryHistoryExportAsyncStartedTaskId = 'ops-recovery-export-task-started-0001';
  const recoveryHistoryExportAsyncCompletedTaskId = 'ops-recovery-export-task-completed-0001';
  const recoveryHistoryExportAsyncRetriedTaskId = 'ops-recovery-export-task-retried-0001';
  const recoveryHistoryExportAsyncTasks = new Map<string, {
    taskId: string;
    exportType: string;
    status: string;
    error: string | null;
    createdAt: string;
    finishedAt: string | null;
    filename: string | null;
    message?: string | null;
    retrySourceTaskId?: string | null;
  }>();
  let recoveryHistoryExportAsyncRetrySelectedSequence = 0;
  recoveryHistoryExportAsyncTasks.set(recoveryHistoryExportAsyncCompletedTaskId, {
    taskId: recoveryHistoryExportAsyncCompletedTaskId,
    exportType: 'HISTORY_SUMMARY',
    status: 'COMPLETED',
    error: null,
    createdAt: new Date(Date.now() - 120_000).toISOString(),
    finishedAt: new Date(Date.now() - 90_000).toISOString(),
    filename: 'ops_recovery_history_summary_async.csv',
  });

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

  await page.route('**/api/v1/folders/path**', async (route) => {
    const url = new URL(route.request().url());
    const requestedPath = url.searchParams.get('path');
    if (requestedPath !== '/Root/Documents/e2e-preview-diagnostics') {
      await route.fulfill({ status: 404, contentType: 'application/json', body: JSON.stringify({ message: 'Not found' }) });
      return;
    }
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        id: 'parent-folder-id',
        name: 'e2e-preview-diagnostics',
        path: '/Root/Documents/e2e-preview-diagnostics',
        folderType: 'USER',
        parentId: 'root-folder-id',
        inheritPermissions: true,
        description: null,
        createdBy: 'admin',
        createdDate: new Date().toISOString(),
        lastModifiedBy: 'admin',
        lastModifiedDate: new Date().toISOString(),
      }),
    });
  });

  await page.route('**/api/v1/nodes/parent-folder-id', async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        id: 'parent-folder-id',
        name: 'e2e-preview-diagnostics',
        path: '/Root/Documents/e2e-preview-diagnostics',
        nodeType: 'FOLDER',
        parentId: 'root-folder-id',
        size: 0,
        contentType: null,
        currentVersionLabel: null,
        correspondentId: null,
        correspondentName: null,
        properties: {},
        metadata: {},
        aspects: [],
        tags: [],
        categories: [],
        inheritPermissions: true,
        locked: false,
        lockedBy: null,
        previewStatus: null,
        previewFailureReason: null,
        previewFailureCategory: null,
        createdBy: 'admin',
        createdDate: new Date().toISOString(),
        lastModifiedBy: 'admin',
        lastModifiedDate: new Date().toISOString(),
      }),
    });
  });

  await page.route('**/api/v1/preview/diagnostics/failures**', async (route) => {
    const requestUrl = new URL(route.request().url());
    const days = requestUrl.searchParams.get('days') || '7';
    requestedFailureDays.push(days);

    const baseRows = [
      {
        id: retryableId,
        name: retryableName,
        path: '/Root/Documents/e2e-preview-diagnostics/retryable.pdf',
        mimeType: 'application/pdf',
        previewStatus: 'FAILED',
        previewFailureCategory: 'TEMPORARY',
        previewFailureReason: 'Timeout contacting preview service',
        previewLastUpdated: new Date().toISOString(),
      },
      {
        id: unsupportedId,
        name: unsupportedName,
        path: '/Root/Documents/e2e-preview-diagnostics/unsupported.bin',
        mimeType: 'application/octet-stream',
        previewStatus: 'UNSUPPORTED',
        previewFailureCategory: 'UNSUPPORTED',
        previewFailureReason: 'Preview not supported for mime type application/octet-stream',
        previewLastUpdated: new Date().toISOString(),
      },
      {
        id: permanentId,
        name: permanentName,
        path: '/Root/Documents/e2e-preview-diagnostics/permanent.pdf',
        mimeType: 'application/pdf',
        previewStatus: 'FAILED',
        previewFailureCategory: 'PERMANENT',
        previewFailureReason: 'Error generating preview: Missing root object specification in trailer.',
        previewLastUpdated: new Date().toISOString(),
      },
    ];

    const rows = days === '30'
      ? [
          ...baseRows,
          {
            id: retryableTwinId,
            name: retryableTwinName,
            path: '/Root/Documents/e2e-preview-diagnostics/retryable-twin.pdf',
            mimeType: 'application/pdf',
            previewStatus: 'FAILED',
            previewFailureCategory: 'TEMPORARY',
            previewFailureReason: 'Timeout contacting preview service',
            previewLastUpdated: new Date().toISOString(),
          },
        ]
      : baseRows;

    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify(rows),
    });
  });

  await page.route('**/api/v1/preview/diagnostics/failures/summary**', async (route) => {
    const requestUrl = new URL(route.request().url());
    const days = requestUrl.searchParams.get('days') || '7';
    requestedSummaryDays.push(days);

    const summaryPayload = days === '30'
      ? {
          totalFailures: 4,
          sampledFailures: 4,
          sampleLimit: 500,
          windowDays: 30,
          windowStart: new Date().toISOString(),
          sampleTruncated: false,
          confidenceLevel: 'HIGH',
          confidenceReason: 'sample_complete',
          statusCounts: [
            { status: 'FAILED', count: 3 },
            { status: 'UNSUPPORTED', count: 1 },
          ],
          categoryCounts: [
            { category: 'TEMPORARY', retryable: true, count: 2 },
            { category: 'PERMANENT', retryable: false, count: 1 },
            { category: 'UNSUPPORTED', retryable: false, count: 1 },
          ],
          topReasons: [
            { reason: 'Timeout contacting preview service', category: 'TEMPORARY', retryable: true, count: 2 },
            { reason: 'Preview not supported for mime type application/octet-stream', category: 'UNSUPPORTED', retryable: false, count: 1 },
            { reason: 'Error generating preview: Missing root object specification in trailer.', category: 'PERMANENT', retryable: false, count: 1 },
          ],
        }
      : {
          totalFailures: 3,
          sampledFailures: 3,
          sampleLimit: 500,
          windowDays: 7,
          windowStart: new Date().toISOString(),
          sampleTruncated: false,
          confidenceLevel: 'HIGH',
          confidenceReason: 'sample_complete',
          statusCounts: [
            { status: 'FAILED', count: 2 },
            { status: 'UNSUPPORTED', count: 1 },
          ],
          categoryCounts: [
            { category: 'TEMPORARY', retryable: true, count: 1 },
            { category: 'PERMANENT', retryable: false, count: 1 },
            { category: 'UNSUPPORTED', retryable: false, count: 1 },
          ],
          topReasons: [
            { reason: 'Timeout contacting preview service', category: 'TEMPORARY', retryable: true, count: 1 },
            { reason: 'Preview not supported for mime type application/octet-stream', category: 'UNSUPPORTED', retryable: false, count: 1 },
            { reason: 'Error generating preview: Missing root object specification in trailer.', category: 'PERMANENT', retryable: false, count: 1 },
          ],
        };

    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify(summaryPayload),
    });
  });

  await page.route('**/api/v1/preview/diagnostics/queue/summary**', async (route) => {
    const requestUrl = new URL(route.request().url());
    const state = requestUrl.searchParams.get('state') || 'ALL';
    const query = (requestUrl.searchParams.get('query') || '').trim();
    const queryLower = query.toLowerCase();
    const allItems = [
      {
        documentId: retryableId,
        name: retryableName,
        path: '/Root/Documents/e2e-preview-diagnostics/retryable.pdf',
        mimeType: 'application/pdf',
        previewStatus: 'FAILED',
        queueState: 'RUNNING',
        governanceKey: `${retryableId}|preview|hash-a`,
        attempts: 1,
        nextAttemptAt: new Date().toISOString(),
        running: true,
        cancelRequested: false,
      },
      {
        documentId: retryableTwinId,
        name: retryableTwinName,
        path: '/Root/Documents/e2e-preview-diagnostics/retryable-twin.pdf',
        mimeType: 'application/pdf',
        previewStatus: 'FAILED',
        queueState: 'CANCEL_REQUESTED',
        governanceKey: `${retryableTwinId}|preview|hash-b`,
        attempts: 0,
        nextAttemptAt: new Date(Date.now() + 30_000).toISOString(),
        running: false,
        cancelRequested: true,
      },
    ];
    const filteredItems = allItems.filter((item) => {
      if (state !== 'ALL' && item.queueState !== state) {
        return false;
      }
      if (!queryLower) {
        return true;
      }
      return [
        item.documentId,
        item.name,
        item.path,
        item.mimeType,
        item.previewStatus,
        item.queueState,
        item.governanceKey,
      ].some((value) => String(value || '').toLowerCase().includes(queryLower));
    });

    if (requestUrl.pathname.endsWith('/queue/summary/export')) {
      const limit = requestUrl.searchParams.get('limit') || '200';
      queueSummaryExportCalls.push({ limit, state, query });
      const lines = filteredItems.length > 0
        ? filteredItems.map((item) => (
          `MEMORY,true,2,2,1,true,1,20,false,${state},${query},2,${filteredItems.length},${item.queueState},${item.documentId},${item.name},${item.path},${item.mimeType},${item.previewStatus},${item.attempts},${item.nextAttemptAt},${item.running},${item.cancelRequested},${item.governanceKey}`
        )).join('\n')
        : `MEMORY,true,2,2,1,true,1,20,false,${state},${query},2,0`;
      await route.fulfill({
        status: 200,
        contentType: 'text/csv; charset=UTF-8',
        headers: {
          'content-disposition': 'attachment; filename="preview_queue_diagnostics.csv"',
          'x-preview-queue-item-count': String(filteredItems.length),
        },
        body: `backend,queueEnabled,scheduledCount,governanceCount,runningCount,runningCountAccurate,cancellationRequestedCount,sampleLimit,sampleTruncated,stateFilter,queryFilter,totalSampledItems,filteredSampledItems,queueState,documentId,name,path,mimeType,previewStatus,attempts,nextAttemptAt,running,cancelRequested,governanceKey\n${lines}\n`,
      });
      return;
    }
    const limit = requestUrl.searchParams.get('limit') || '20';
    queueSummaryCalls.push({ limit, state, query });
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        backend: 'MEMORY',
        queueEnabled: true,
        scheduledCount: 2,
        governanceCount: 2,
        runningCount: 1,
        runningCountAccurate: true,
        cancellationRequestedCount: 1,
        sampleLimit: Number(limit),
        sampleTruncated: false,
        stateFilter: state,
        queryFilter: query || null,
        totalSampledItems: allItems.length,
        filteredSampledItems: filteredItems.length,
        items: filteredItems,
      }),
    });
  });

  await page.route('**/api/v1/preview/diagnostics/queue/cancel-active**', async (route) => {
    const requestUrl = new URL(route.request().url());
    const limit = requestUrl.searchParams.get('limit') || '200';
    const state = requestUrl.searchParams.get('state') || 'ALL';
    const query = (requestUrl.searchParams.get('query') || '').trim();
    queueCancelActiveCalls.push({ limit, state, query });

    const matchesRunningRetryable = state === 'RUNNING' && query === 'retryable';
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        stateFilter: state,
        queryFilter: query || null,
        limit: Number(limit),
        requested: matchesRunningRetryable ? 1 : 0,
        cancelled: matchesRunningRetryable ? 1 : 0,
        skipped: 0,
        failed: 0,
        results: matchesRunningRetryable
          ? [
              {
                documentId: retryableId,
                queueState: 'CANCEL_REQUESTED',
                outcome: 'CANCELLED',
                message: 'Cancellation requested for running preview task',
              },
            ]
          : [],
      }),
    });
  });

  await page.route('**/api/v1/preview/diagnostics/queue/declined**', async (route) => {
    const requestUrl = new URL(route.request().url());
    const method = route.request().method();
    const pathname = requestUrl.pathname;
    const limit = requestUrl.searchParams.get('limit') || '50';
    const category = (requestUrl.searchParams.get('category') || 'ANY').trim().toUpperCase();
    const forceRequired = (requestUrl.searchParams.get('forceRequired') || 'ANY').trim().toUpperCase();
    const windowHoursParam = (requestUrl.searchParams.get('windowHours') || '').trim();
    const windowHours = Number.isFinite(Number(windowHoursParam)) && Number(windowHoursParam) > 0
      ? Math.floor(Number(windowHoursParam))
      : 0;
    const query = (requestUrl.searchParams.get('query') || '').trim();
    const queryLower = query.toLowerCase();
    const nowMs = Date.now();
    const windowStartMs = windowHours > 0 ? nowMs - (windowHours * 60 * 60 * 1000) : null;
    const dryRunAsyncPrefix = '/api/v1/preview/diagnostics/queue/declined/requeue/dry-run/export-async';
    const asyncPrefix = '/api/v1/preview/diagnostics/queue/declined/export-async';

    if (pathname.startsWith(dryRunAsyncPrefix)) {
      const suffix = pathname.slice(dryRunAsyncPrefix.length);
      const parts = suffix.split('/').filter(Boolean);
      const statusFilter = (requestUrl.searchParams.get('status') || '').toUpperCase();
      const terminalStatuses = new Set(['COMPLETED', 'CANCELLED', 'FAILED']);
      const getRetrySourceTasks = (targetStatusFilter: string) => Array.from(queueDeclinedDryRunExportAsyncTasks.values())
        .filter((task) => {
          const taskStatus = (task.status || '').toUpperCase();
          if (!terminalStatuses.has(taskStatus)) {
            return false;
          }
          if (targetStatusFilter) {
            return taskStatus === targetStatusFilter;
          }
          return taskStatus === 'FAILED' || taskStatus === 'CANCELLED';
        })
        .sort((left, right) => (right.createdAt || '').localeCompare(left.createdAt || ''));

      if (parts.length === 0 && method === 'POST') {
        const requestBody = route.request().postDataJSON() as {
          limit?: number;
          category?: string;
          forceRequired?: string;
          windowHours?: number | null;
          query?: string | null;
          force?: boolean | null;
        } | null;
        const startLimit = String(requestBody?.limit ?? limit);
        const startCategory = String(requestBody?.category || category).trim().toUpperCase();
        const startForceRequired = String(requestBody?.forceRequired || forceRequired).trim().toUpperCase();
        const startWindowHours = Number.isFinite(Number(requestBody?.windowHours))
          && Number(requestBody?.windowHours) > 0
          ? Math.floor(Number(requestBody?.windowHours))
          : windowHours;
        const startQuery = String(requestBody?.query || query).trim();
        const startForce = typeof requestBody?.force === 'boolean'
          ? requestBody.force
          : (requestUrl.searchParams.get('force') || 'true').toLowerCase() !== 'false';
        queueDeclinedDryRunExportAsyncStartCalls.push({
          limit: startLimit,
          category: startCategory,
          forceRequired: startForceRequired,
          windowHours: startWindowHours > 0 ? String(startWindowHours) : '',
          query: startQuery,
          force: startForce,
        });
        const sequence = queueDeclinedDryRunExportAsyncStartSequence;
        queueDeclinedDryRunExportAsyncStartSequence += 1;
        const taskId = sequence === 0
          ? queueDeclinedDryRunExportAsyncStartedTaskId
          : sequence === 1
            ? queueDeclinedDryRunExportAsyncCancelledTaskId
            : `queue-declined-dry-run-export-task-started-${String(sequence + 1).padStart(4, '0')}`;
        const startedTask = {
          taskId,
          status: 'QUEUED',
          createdAt: new Date().toISOString(),
          finishedAt: null,
          filename: `preview_queue_declined_requeue_dry_run_async_${taskId}.csv`,
          error: null,
          message: null,
          categoryFilter: startCategory,
          forceRequiredFilter: startForceRequired,
          queryFilter: startQuery || null,
          windowHoursFilter: startWindowHours > 0 ? startWindowHours : null,
          limit: Number.isFinite(Number(startLimit)) ? Number(startLimit) : null,
          forceFilter: startForce,
          listPollCount: 0,
        };
        queueDeclinedDryRunExportAsyncTasks.set(taskId, startedTask);
        await route.fulfill({
          status: 202,
          contentType: 'application/json',
          headers: {
            location: `/api/v1/preview/diagnostics/queue/declined/requeue/dry-run/export-async/${taskId}`,
          },
          body: JSON.stringify(startedTask),
        });
        return;
      }

      if (parts.length === 0 && method === 'GET') {
        const rawMaxItems = Number(
          requestUrl.searchParams.get('maxItems')
          || requestUrl.searchParams.get('limit')
          || '20'
        );
        const listLimit = Number.isFinite(rawMaxItems) && rawMaxItems > 0 ? Math.floor(rawMaxItems) : 20;
        const rawSkipCount = Number(requestUrl.searchParams.get('skipCount') || '0');
        const skipCount = Number.isFinite(rawSkipCount) && rawSkipCount > 0 ? Math.floor(rawSkipCount) : 0;
        queueDeclinedDryRunExportAsyncListCalls.push({
          limit: listLimit,
          skipCount,
          status: statusFilter || 'ALL',
        });
        for (const [taskId, task] of queueDeclinedDryRunExportAsyncTasks.entries()) {
          const taskStatus = (task.status || '').toUpperCase();
          if (taskStatus === 'QUEUED') {
            queueDeclinedDryRunExportAsyncTasks.set(taskId, {
              ...task,
              status: 'RUNNING',
              message: 'Queue declined requeue dry-run async export is running',
              listPollCount: task.listPollCount + 1,
            });
            continue;
          }
          if (taskStatus === 'RUNNING' && task.listPollCount >= 1) {
            queueDeclinedDryRunExportAsyncTasks.set(taskId, {
              ...task,
              status: 'COMPLETED',
              message: 'Queue declined requeue dry-run async export completed',
              finishedAt: new Date().toISOString(),
              listPollCount: task.listPollCount + 1,
            });
            continue;
          }
          if (taskStatus === 'RUNNING') {
            queueDeclinedDryRunExportAsyncTasks.set(taskId, {
              ...task,
              listPollCount: task.listPollCount + 1,
            });
          }
        }
        const filteredItems = Array.from(queueDeclinedDryRunExportAsyncTasks.values())
          .filter((task) => {
            if (!statusFilter) {
              return true;
            }
            return (task.status || '').toUpperCase() === statusFilter;
          })
          .sort((left, right) => (right.createdAt || '').localeCompare(left.createdAt || ''));
        const items = filteredItems.slice(skipCount, skipCount + listLimit);
        const totalItems = filteredItems.length;
        await route.fulfill({
          status: 200,
          contentType: 'application/json',
          body: JSON.stringify({
            count: items.length,
            paging: {
              skipCount,
              maxItems: listLimit,
              totalItems,
              hasMoreItems: skipCount + items.length < totalItems,
            },
            items,
          }),
        });
        return;
      }

      if (parts.length === 2 && parts[0] === 'retry-terminal' && parts[1] === 'by-task-ids' && method === 'POST') {
        const requestBody = route.request().postDataJSON() as { sourceTaskIds?: string[] } | null;
        const sourceTaskIds = Array.from(new Set((requestBody?.sourceTaskIds || [])
          .map((value) => String(value || '').trim())
          .filter((value) => value.length > 0)));
        queueDeclinedDryRunExportAsyncRetrySelectedCalls.push({ sourceTaskIds });
        const requested = sourceTaskIds.length;
        let retried = 0;
        let skipped = 0;
        let failed = 0;
        const results: Array<{
          sourceTaskId: string;
          newTaskId: string | null;
          sourceStatus: string;
          outcome: string;
          message: string;
        }> = [];
        for (const sourceTaskId of sourceTaskIds) {
          const sourceTask = queueDeclinedDryRunExportAsyncTasks.get(sourceTaskId);
          if (!sourceTask) {
            skipped += 1;
            results.push({
              sourceTaskId,
              newTaskId: null,
              sourceStatus: 'NOT_FOUND',
              outcome: 'SKIPPED',
              message: 'Source task not found',
            });
            continue;
          }
          const sourceStatus = (sourceTask.status || '').toUpperCase();
          if (!terminalStatuses.has(sourceStatus)) {
            skipped += 1;
            results.push({
              sourceTaskId,
              newTaskId: null,
              sourceStatus: sourceStatus || 'UNKNOWN',
              outcome: 'SKIPPED',
              message: 'Source task is not terminal',
            });
            continue;
          }
          try {
            const sequence = queueDeclinedDryRunExportAsyncRetrySelectedSequence;
            queueDeclinedDryRunExportAsyncRetrySelectedSequence += 1;
            const retriedTaskId = sequence === 0
              ? queueDeclinedDryRunExportAsyncRetriedTaskId
              : `queue-declined-dry-run-export-task-retry-selected-${String(sequence + 1).padStart(4, '0')}`;
            queueDeclinedDryRunExportAsyncTasks.set(retriedTaskId, {
              ...sourceTask,
              taskId: retriedTaskId,
              status: 'QUEUED',
              createdAt: new Date().toISOString(),
              finishedAt: null,
              error: null,
              message: 'Retried selected terminal async export task',
              listPollCount: 0,
            });
            retried += 1;
            results.push({
              sourceTaskId,
              newTaskId: retriedTaskId,
              sourceStatus: sourceStatus || 'UNKNOWN',
              outcome: 'RETRIED',
              message: 'Retried selected terminal async export task',
            });
          } catch (error) {
            failed += 1;
            results.push({
              sourceTaskId,
              newTaskId: null,
              sourceStatus: sourceStatus || 'UNKNOWN',
              outcome: 'FAILED',
              message: error instanceof Error ? error.message : 'Retry failed',
            });
          }
        }
        await route.fulfill({
          status: 202,
          contentType: 'application/json',
          headers: {
            location: '/api/v1/preview/diagnostics/queue/declined/requeue/dry-run/export-async',
          },
          body: JSON.stringify({
            requested,
            retried,
            reused: 0,
            skipped,
            failed,
            limit: requested,
            statusFilter: 'BY_TASK_IDS',
            message: requested > 0
              ? `Retried ${retried}/${requested} selected terminal declined requeue dry-run async export tasks (reused=0, skipped=${skipped}, failed=${failed})`
              : 'No source task ids provided for terminal declined requeue dry-run async export retry',
            results,
          }),
        });
        return;
      }

      if (parts.length === 2 && parts[0] === 'retry-terminal' && parts[1] === 'dry-run' && method === 'POST') {
        const rawLimit = Number(requestUrl.searchParams.get('limit') || '20');
        const retryLimit = Number.isFinite(rawLimit) && rawLimit > 0 ? Math.floor(rawLimit) : 20;
        queueDeclinedDryRunExportAsyncRetryTerminalDryRunCalls.push({
          status: statusFilter || 'FAILED|CANCELLED',
          limit: retryLimit,
        });
        if (statusFilter === 'QUEUED' || statusFilter === 'RUNNING') {
          await route.fulfill({
            status: 400,
            contentType: 'application/json',
            body: JSON.stringify({
              message: 'status filter only supports terminal states: COMPLETED, CANCELLED, FAILED',
            }),
          });
          return;
        }
        const sourceTasks = getRetrySourceTasks(statusFilter).slice(0, retryLimit);
        const results = sourceTasks.map((task) => ({
          sourceTaskId: task.taskId,
          sourceStatus: task.status,
          outcome: 'RETRYABLE',
          reasonCode: 'TERMINAL_TASK_RETRYABLE',
          message: 'Terminal async export task can be retried',
        }));
        const retryableCount = results.length;
        await route.fulfill({
          status: 200,
          contentType: 'application/json',
          body: JSON.stringify({
            requested: sourceTasks.length,
            retryable: retryableCount,
            skipped: 0,
            limit: retryLimit,
            statusFilter: statusFilter || 'FAILED|CANCELLED',
            message: sourceTasks.length > 0
              ? `Dry-run identified ${retryableCount}/${sourceTasks.length} retryable terminal declined requeue dry-run async export tasks (skipped=0)`
              : 'No terminal declined requeue dry-run async export tasks matched retry dry-run filters',
            results,
            reasonBreakdown: retryableCount > 0
              ? [{ reasonCode: 'TERMINAL_TASK_RETRYABLE', outcome: 'RETRYABLE', count: retryableCount }]
              : [],
          }),
        });
        return;
      }

      if (parts.length === 3 && parts[0] === 'retry-terminal' && parts[1] === 'dry-run' && parts[2] === 'export' && method === 'GET') {
        const rawLimit = Number(requestUrl.searchParams.get('limit') || '20');
        const retryLimit = Number.isFinite(rawLimit) && rawLimit > 0 ? Math.floor(rawLimit) : 20;
        queueDeclinedDryRunExportAsyncRetryTerminalDryRunExportCalls.push({
          status: statusFilter || 'FAILED|CANCELLED',
          limit: retryLimit,
        });
        if (statusFilter === 'QUEUED' || statusFilter === 'RUNNING') {
          await route.fulfill({
            status: 400,
            contentType: 'application/json',
            body: JSON.stringify({
              message: 'status filter only supports terminal states: COMPLETED, CANCELLED, FAILED',
            }),
          });
          return;
        }
        const sourceTasks = getRetrySourceTasks(statusFilter).slice(0, retryLimit);
        const retryableCount = sourceTasks.length;
        const rows = sourceTasks.map((task) => (
          `${statusFilter || 'FAILED|CANCELLED'},${retryLimit},${sourceTasks.length},${retryableCount},0,${task.taskId},${task.status},RETRYABLE,TERMINAL_TASK_RETRYABLE,Terminal async export task can be retried`
        ));
        const csv = [
          'statusFilter,limit,requested,retryable,skipped,sourceTaskId,sourceStatus,outcome,reasonCode,message',
          ...rows,
          '',
          'reasonCode,outcome,count',
          `TERMINAL_TASK_RETRYABLE,RETRYABLE,${retryableCount}`,
        ].join('\n');
        await route.fulfill({
          status: 200,
          contentType: 'text/csv; charset=UTF-8',
          headers: {
            'content-disposition': 'attachment; filename="preview_queue_declined_requeue_dry_run_async_retry_dry_run.csv"',
          },
          body: csv,
        });
        return;
      }

      if (parts.length === 1 && parts[0] === 'retry-terminal' && method === 'POST') {
        const rawLimit = Number(requestUrl.searchParams.get('limit') || '20');
        const retryLimit = Number.isFinite(rawLimit) && rawLimit > 0 ? Math.floor(rawLimit) : 20;
        queueDeclinedDryRunExportAsyncRetryTerminalCalls.push({
          status: statusFilter || 'FAILED|CANCELLED',
          limit: retryLimit,
        });
        if (statusFilter === 'QUEUED' || statusFilter === 'RUNNING') {
          await route.fulfill({
            status: 400,
            contentType: 'application/json',
            body: JSON.stringify({
              message: 'status filter only supports terminal states: COMPLETED, CANCELLED, FAILED',
            }),
          });
          return;
        }
        const sourceTasks = getRetrySourceTasks(statusFilter).slice(0, retryLimit);
        const results = sourceTasks.map((task) => {
          const sequence = queueDeclinedDryRunExportAsyncRetryTerminalSequence;
          queueDeclinedDryRunExportAsyncRetryTerminalSequence += 1;
          const retriedTaskId = sequence === 0
            ? queueDeclinedDryRunExportAsyncRetryTerminalTaskId
            : `queue-declined-dry-run-export-task-retry-terminal-${String(sequence + 1).padStart(4, '0')}`;
          queueDeclinedDryRunExportAsyncTasks.set(retriedTaskId, {
            ...task,
            taskId: retriedTaskId,
            status: 'QUEUED',
            createdAt: new Date().toISOString(),
            finishedAt: null,
            error: null,
            message: 'Retried terminal async export task',
            listPollCount: 0,
          });
          return {
            sourceTaskId: task.taskId,
            newTaskId: retriedTaskId,
            sourceStatus: task.status,
            outcome: 'RETRIED',
            message: 'Retried terminal async export task',
          };
        });
        await route.fulfill({
          status: 202,
          contentType: 'application/json',
          headers: {
            location: '/api/v1/preview/diagnostics/queue/declined/requeue/dry-run/export-async',
          },
          body: JSON.stringify({
            requested: sourceTasks.length,
            retried: results.length,
            reused: 0,
            skipped: 0,
            failed: 0,
            limit: retryLimit,
            statusFilter: statusFilter || 'FAILED|CANCELLED',
            message: sourceTasks.length > 0
              ? `Retried ${results.length}/${sourceTasks.length} terminal declined requeue dry-run async export tasks (reused=0, skipped=0, failed=0)`
              : 'No terminal declined requeue dry-run async export tasks matched retry filters',
            results,
          }),
        });
        return;
      }

      if (parts.length === 1 && parts[0] === 'summary' && method === 'GET') {
        queueDeclinedDryRunExportAsyncSummaryCalls.push({
          status: statusFilter || 'ALL',
        });
        const filtered = Array.from(queueDeclinedDryRunExportAsyncTasks.values()).filter((task) => {
          if (!statusFilter) {
            return true;
          }
          return (task.status || '').toUpperCase() === statusFilter;
        });
        const queuedCount = filtered.filter((task) => (task.status || '').toUpperCase() === 'QUEUED').length;
        const runningCount = filtered.filter((task) => (task.status || '').toUpperCase() === 'RUNNING').length;
        const completedCount = filtered.filter((task) => (task.status || '').toUpperCase() === 'COMPLETED').length;
        const cancelledCount = filtered.filter((task) => (task.status || '').toUpperCase() === 'CANCELLED').length;
        const failedCount = filtered.filter((task) => (task.status || '').toUpperCase() === 'FAILED').length;
        await route.fulfill({
          status: 200,
          contentType: 'application/json',
          body: JSON.stringify({
            totalCount: filtered.length,
            queuedCount,
            runningCount,
            completedCount,
            cancelledCount,
            failedCount,
            activeCount: queuedCount + runningCount,
            terminalCount: completedCount + cancelledCount + failedCount,
          }),
        });
        return;
      }

      if (parts.length === 1 && parts[0] === 'cancel-active' && method === 'POST') {
        queueDeclinedDryRunExportAsyncCancelActiveCalls.push({
          status: statusFilter || 'ALL',
        });
        if (statusFilter && statusFilter !== 'QUEUED' && statusFilter !== 'RUNNING') {
          await route.fulfill({
            status: 400,
            contentType: 'application/json',
            body: JSON.stringify({
              message: `Unsupported cancel-active status filter: ${statusFilter}`,
            }),
          });
          return;
        }
        let cancelledCount = 0;
        for (const [taskId, task] of queueDeclinedDryRunExportAsyncTasks.entries()) {
          const taskStatus = (task.status || '').toUpperCase();
          const active = taskStatus === 'QUEUED' || taskStatus === 'RUNNING';
          if (!active) {
            continue;
          }
          if (statusFilter && taskStatus !== statusFilter) {
            continue;
          }
          queueDeclinedDryRunExportAsyncTasks.set(taskId, {
            ...task,
            status: 'CANCELLED',
            error: 'Cancelled by cancel-active',
            finishedAt: new Date().toISOString(),
          });
          cancelledCount += 1;
        }
        const remainingActiveCount = Array.from(queueDeclinedDryRunExportAsyncTasks.values()).filter((task) => {
          const normalized = (task.status || '').toUpperCase();
          return normalized === 'QUEUED' || normalized === 'RUNNING';
        }).length;
        await route.fulfill({
          status: 200,
          contentType: 'application/json',
          body: JSON.stringify({
            cancelledCount,
            remainingActiveCount,
            statusFilter: statusFilter || null,
            message: cancelledCount > 0
              ? `Cancelled ${cancelledCount} active queue declined requeue dry-run async export task(s)`
              : 'No active queue declined requeue dry-run async export tasks matched cancel-active filters.',
          }),
        });
        return;
      }

      if (parts.length === 1 && parts[0] === 'cleanup' && method === 'POST') {
        queueDeclinedDryRunExportAsyncCleanupCalls.push({
          status: statusFilter || 'ALL',
        });
        const terminalStatuses = new Set(['COMPLETED', 'CANCELLED', 'FAILED']);
        let deletedCount = 0;
        for (const [taskId, task] of queueDeclinedDryRunExportAsyncTasks.entries()) {
          const taskStatus = (task.status || '').toUpperCase();
          if (statusFilter) {
            if (taskStatus !== statusFilter) {
              continue;
            }
          } else if (!terminalStatuses.has(taskStatus)) {
            continue;
          }
          queueDeclinedDryRunExportAsyncTasks.delete(taskId);
          deletedCount += 1;
        }
        await route.fulfill({
          status: 200,
          contentType: 'application/json',
          body: JSON.stringify({
            deletedCount,
            remainingCount: queueDeclinedDryRunExportAsyncTasks.size,
            statusFilter: statusFilter || null,
            message: deletedCount > 0
              ? `Queue declined requeue dry-run async export cleanup removed ${deletedCount} task(s)`
              : 'No queue declined requeue dry-run async export tasks matched cleanup filter',
          }),
        });
        return;
      }

      if (parts.length >= 1) {
        const taskId = decodeURIComponent(parts[0]);
        const task = queueDeclinedDryRunExportAsyncTasks.get(taskId);
        if (!task) {
          await route.fulfill({
            status: 404,
            contentType: 'application/json',
            body: JSON.stringify({ message: `Task not found: ${taskId}` }),
          });
          return;
        }

        if (parts.length === 1 && method === 'GET') {
          queueDeclinedDryRunExportAsyncGetCalls.push(taskId);
          await route.fulfill({
            status: 200,
            contentType: 'application/json',
            body: JSON.stringify(task),
          });
          return;
        }

        if (parts.length === 2 && parts[1] === 'cancel' && method === 'POST') {
          queueDeclinedDryRunExportAsyncCancelCalls.push(taskId);
          const cancelledTask = {
            ...task,
            status: 'CANCELLED',
            finishedAt: new Date().toISOString(),
            message: 'Cancelled by user',
          };
          queueDeclinedDryRunExportAsyncTasks.set(taskId, cancelledTask);
          await route.fulfill({
            status: 200,
            contentType: 'application/json',
            body: JSON.stringify(cancelledTask),
          });
          return;
        }

        if (parts.length === 2 && parts[1] === 'retry' && method === 'POST') {
          queueDeclinedDryRunExportAsyncRetryCalls.push(taskId);
          const sourceStatus = (task.status || '').toUpperCase();
          if (sourceStatus === 'QUEUED' || sourceStatus === 'RUNNING') {
            await route.fulfill({
              status: 409,
              contentType: 'application/json',
              body: JSON.stringify({ message: 'Task is active and cannot be retried' }),
            });
            return;
          }
          const activeExistingTask = Array.from(queueDeclinedDryRunExportAsyncTasks.values())
            .find((existingTask) => {
              const existingStatus = (existingTask.status || '').toUpperCase();
              if (existingStatus !== 'QUEUED' && existingStatus !== 'RUNNING') {
                return false;
              }
              const existingLimit = Number.isFinite(Number(existingTask.limit))
                ? Number(existingTask.limit)
                : null;
              const taskLimit = Number.isFinite(Number(task.limit))
                ? Number(task.limit)
                : null;
              const existingCategory = String(existingTask.categoryFilter || 'ANY').trim().toUpperCase();
              const taskCategory = String(task.categoryFilter || 'ANY').trim().toUpperCase();
              const existingForceRequired = String(existingTask.forceRequiredFilter || 'ANY').trim().toUpperCase();
              const taskForceRequired = String(task.forceRequiredFilter || 'ANY').trim().toUpperCase();
              const existingWindowHours = Number.isFinite(Number(existingTask.windowHoursFilter))
                && Number(existingTask.windowHoursFilter) > 0
                ? Math.floor(Number(existingTask.windowHoursFilter))
                : 0;
              const taskWindowHours = Number.isFinite(Number(task.windowHoursFilter))
                && Number(task.windowHoursFilter) > 0
                ? Math.floor(Number(task.windowHoursFilter))
                : 0;
              const existingQuery = String(existingTask.queryFilter || '').trim();
              const taskQuery = String(task.queryFilter || '').trim();
              const existingForce = Boolean(existingTask.forceFilter);
              const taskForce = Boolean(task.forceFilter);
              return existingLimit === taskLimit
                && existingCategory === taskCategory
                && existingForceRequired === taskForceRequired
                && existingWindowHours === taskWindowHours
                && existingQuery === taskQuery
                && existingForce === taskForce;
            });
          if (activeExistingTask) {
            queueDeclinedDryRunExportAsyncRetryDedupCalls.push({
              sourceTaskId: taskId,
              reusedTaskId: activeExistingTask.taskId,
            });
            await route.fulfill({
              status: 202,
              contentType: 'application/json',
              headers: {
                location: `/api/v1/preview/diagnostics/queue/declined/requeue/dry-run/export-async/${activeExistingTask.taskId}`,
              },
              body: JSON.stringify({
                ...activeExistingTask,
                deduplicated: true,
                deduplicatedFromTaskId: activeExistingTask.taskId,
                message: 'Reused active queue declined requeue dry-run async export task with same filters',
              }),
            });
            return;
          }
          const retriedTask = {
            ...task,
            taskId: queueDeclinedDryRunExportAsyncRetriedTaskId,
            status: 'QUEUED',
            createdAt: new Date().toISOString(),
            finishedAt: null,
            error: null,
            message: 'Retried queue declined requeue dry-run async export task',
            deduplicated: false,
            deduplicatedFromTaskId: null,
            listPollCount: 0,
          };
          queueDeclinedDryRunExportAsyncTasks.set(queueDeclinedDryRunExportAsyncRetriedTaskId, retriedTask);
          await route.fulfill({
            status: 202,
            contentType: 'application/json',
            headers: {
              location: `/api/v1/preview/diagnostics/queue/declined/requeue/dry-run/export-async/${queueDeclinedDryRunExportAsyncRetriedTaskId}`,
            },
            body: JSON.stringify(retriedTask),
          });
          return;
        }

        if (parts.length === 2 && parts[1] === 'download' && method === 'GET') {
          queueDeclinedDryRunExportAsyncDownloadCalls.push(taskId);
          await route.fulfill({
            status: 200,
            contentType: 'text/csv; charset=UTF-8',
            headers: {
              'content-disposition': `attachment; filename="${task.filename || 'preview_queue_declined_requeue_dry_run_async.csv'}"`,
            },
            body: [
              'documentId,category,outcome,reasonCode,forceRequired,windowHours,query,force',
              `${retryableId},QUIET_PERIOD,${task.forceFilter ? 'QUEUED' : 'SKIPPED'},${task.forceFilter ? 'DECLINED_REQUEUE_ELIGIBLE' : 'DECLINED_QUIET_PERIOD_BLOCKED'},${task.forceRequiredFilter || 'ANY'},${task.windowHoursFilter || ''},${task.queryFilter || ''},${task.forceFilter}`,
              '',
            ].join('\n'),
          });
          return;
        }
      }
    }

    if (pathname.startsWith(asyncPrefix)) {
      const suffix = pathname.slice(asyncPrefix.length);
      const parts = suffix.split('/').filter(Boolean);
      const statusFilter = (requestUrl.searchParams.get('status') || '').toUpperCase();

      if (parts.length === 0 && method === 'POST') {
        const requestBody = route.request().postDataJSON() as {
          limit?: number;
          category?: string;
          forceRequired?: string;
          windowHours?: number | null;
          query?: string | null;
        } | null;
        const startLimit = String(requestBody?.limit ?? limit);
        const startCategory = String(requestBody?.category || category).trim().toUpperCase();
        const startForceRequired = String(requestBody?.forceRequired || forceRequired).trim().toUpperCase();
        const startWindowHours = Number.isFinite(Number(requestBody?.windowHours))
          && Number(requestBody?.windowHours) > 0
          ? Math.floor(Number(requestBody?.windowHours))
          : windowHours;
        const startQuery = String(requestBody?.query || query).trim();
        queueDeclinedExportAsyncStartCalls.push({
          limit: startLimit,
          category: startCategory,
          forceRequired: startForceRequired,
          windowHours: startWindowHours > 0 ? String(startWindowHours) : '',
          query: startQuery,
        });

        const activeExistingTask = Array.from(queueDeclinedExportAsyncTasks.values())
          .find((task) => {
            const normalizedStatus = (task.status || '').toUpperCase();
            if (normalizedStatus !== 'QUEUED' && normalizedStatus !== 'RUNNING') {
              return false;
            }
            const taskLimit = Number.isFinite(Number(task.limit)) ? Number(task.limit) : null;
            const expectedLimit = Number.isFinite(Number(startLimit)) ? Number(startLimit) : null;
            const taskCategory = String(task.categoryFilter || 'ANY').trim().toUpperCase();
            const taskForceRequired = String(task.forceRequiredFilter || 'ANY').trim().toUpperCase();
            const taskWindowHours = Number.isFinite(Number(task.windowHoursFilter))
              && Number(task.windowHoursFilter) > 0
              ? Math.floor(Number(task.windowHoursFilter))
              : 0;
            const taskQuery = String(task.queryFilter || '').trim();
            return taskLimit === expectedLimit
              && taskCategory === startCategory
              && taskForceRequired === startForceRequired
              && taskWindowHours === startWindowHours
              && taskQuery === startQuery;
          });
        if (activeExistingTask) {
          queueDeclinedExportAsyncStartDedupCalls.push({ reusedTaskId: activeExistingTask.taskId });
          await route.fulfill({
            status: 202,
            contentType: 'application/json',
            headers: {
              location: `/api/v1/preview/diagnostics/queue/declined/export-async/${activeExistingTask.taskId}`,
            },
            body: JSON.stringify({
              ...activeExistingTask,
              deduplicated: true,
              deduplicatedFromTaskId: activeExistingTask.taskId,
              message: 'Reused active queue declined async export task with same filters',
            }),
          });
          return;
        }

        const sequence = queueDeclinedExportAsyncStartSequence;
        queueDeclinedExportAsyncStartSequence += 1;
        const taskId = sequence === 0
          ? queueDeclinedExportAsyncStartedTaskId
          : sequence === 1
            ? queueDeclinedExportAsyncCancelledTaskId
            : `queue-declined-export-task-started-${String(sequence + 1).padStart(4, '0')}`;
        const startedTask = {
          taskId,
          status: 'QUEUED',
          createdAt: new Date().toISOString(),
          finishedAt: null,
          filename: `preview_queue_declined_async_${taskId}.csv`,
          error: null,
          message: null,
          deduplicated: false,
          deduplicatedFromTaskId: null,
          categoryFilter: startCategory,
          forceRequiredFilter: startForceRequired,
          queryFilter: startQuery || null,
          windowHoursFilter: startWindowHours > 0 ? startWindowHours : null,
          limit: Number.isFinite(Number(startLimit)) ? Number(startLimit) : null,
          listPollCount: 0,
        };
        queueDeclinedExportAsyncTasks.set(taskId, startedTask);
        await route.fulfill({
          status: 202,
          contentType: 'application/json',
          headers: {
            location: `/api/v1/preview/diagnostics/queue/declined/export-async/${taskId}`,
          },
          body: JSON.stringify(startedTask),
        });
        return;
      }

      if (parts.length === 0 && method === 'GET') {
        const rawMaxItems = Number(
          requestUrl.searchParams.get('maxItems')
          || requestUrl.searchParams.get('limit')
          || '20'
        );
        const listLimit = Number.isFinite(rawMaxItems) && rawMaxItems > 0 ? Math.floor(rawMaxItems) : 20;
        const rawSkipCount = Number(requestUrl.searchParams.get('skipCount') || '0');
        const skipCount = Number.isFinite(rawSkipCount) && rawSkipCount > 0 ? Math.floor(rawSkipCount) : 0;
        queueDeclinedExportAsyncListCalls.push({
          limit: listLimit,
          skipCount,
          status: statusFilter || 'ALL',
        });
        for (const [taskId, task] of queueDeclinedExportAsyncTasks.entries()) {
          const taskStatus = (task.status || '').toUpperCase();
          if (taskStatus === 'QUEUED') {
            queueDeclinedExportAsyncTasks.set(taskId, {
              ...task,
              status: 'RUNNING',
              message: 'Queue declined async export is running',
              listPollCount: task.listPollCount + 1,
            });
            continue;
          }
          if (taskStatus === 'RUNNING' && task.listPollCount >= 1) {
            queueDeclinedExportAsyncTasks.set(taskId, {
              ...task,
              status: 'COMPLETED',
              message: 'Queue declined async export completed',
              finishedAt: new Date().toISOString(),
              listPollCount: task.listPollCount + 1,
            });
            continue;
          }
          if (taskStatus === 'RUNNING') {
            queueDeclinedExportAsyncTasks.set(taskId, {
              ...task,
              listPollCount: task.listPollCount + 1,
            });
          }
        }
        const filteredItems = Array.from(queueDeclinedExportAsyncTasks.values())
          .filter((task) => {
            if (!statusFilter) {
              return true;
            }
            return (task.status || '').toUpperCase() === statusFilter;
          })
          .sort((left, right) => (right.createdAt || '').localeCompare(left.createdAt || ''));
        const items = filteredItems.slice(skipCount, skipCount + listLimit);
        const totalItems = filteredItems.length;
        await route.fulfill({
          status: 200,
          contentType: 'application/json',
          body: JSON.stringify({
            count: items.length,
            paging: {
              skipCount,
              maxItems: listLimit,
              totalItems,
              hasMoreItems: skipCount + items.length < totalItems,
            },
            items,
          }),
        });
        return;
      }

      if (parts.length === 1 && parts[0] === 'summary' && method === 'GET') {
        queueDeclinedExportAsyncSummaryCalls.push({
          status: statusFilter || 'ALL',
        });
        const filtered = Array.from(queueDeclinedExportAsyncTasks.values()).filter((task) => {
          if (!statusFilter) {
            return true;
          }
          return (task.status || '').toUpperCase() === statusFilter;
        });
        const queuedCount = filtered.filter((task) => (task.status || '').toUpperCase() === 'QUEUED').length;
        const runningCount = filtered.filter((task) => (task.status || '').toUpperCase() === 'RUNNING').length;
        const completedCount = filtered.filter((task) => (task.status || '').toUpperCase() === 'COMPLETED').length;
        const cancelledCount = filtered.filter((task) => (task.status || '').toUpperCase() === 'CANCELLED').length;
        const failedCount = filtered.filter((task) => (task.status || '').toUpperCase() === 'FAILED').length;
        await route.fulfill({
          status: 200,
          contentType: 'application/json',
          body: JSON.stringify({
            totalCount: filtered.length,
            queuedCount,
            runningCount,
            completedCount,
            cancelledCount,
            failedCount,
            activeCount: queuedCount + runningCount,
            terminalCount: completedCount + cancelledCount + failedCount,
          }),
        });
        return;
      }

      if (parts.length === 1 && parts[0] === 'cleanup' && method === 'POST') {
        queueDeclinedExportAsyncCleanupCalls.push({
          status: statusFilter || 'ALL',
        });
        const terminalStatuses = new Set(['COMPLETED', 'CANCELLED', 'FAILED']);
        let deletedCount = 0;
        for (const [taskId, task] of queueDeclinedExportAsyncTasks.entries()) {
          const taskStatus = (task.status || '').toUpperCase();
          if (statusFilter) {
            if (taskStatus !== statusFilter) {
              continue;
            }
          } else if (!terminalStatuses.has(taskStatus)) {
            continue;
          }
          queueDeclinedExportAsyncTasks.delete(taskId);
          deletedCount += 1;
        }
        await route.fulfill({
          status: 200,
          contentType: 'application/json',
          body: JSON.stringify({
            deletedCount,
            remainingCount: queueDeclinedExportAsyncTasks.size,
            statusFilter: statusFilter || null,
            message: deletedCount > 0
              ? `Queue declined async export cleanup removed ${deletedCount} task(s)`
              : 'No queue declined async export tasks matched cleanup filter',
          }),
        });
        return;
      }

      if (parts.length === 1 && parts[0] === 'cancel-active' && method === 'POST') {
        queueDeclinedExportAsyncCancelActiveCalls.push({
          status: statusFilter || 'ALL',
        });
        if (statusFilter && statusFilter !== 'QUEUED' && statusFilter !== 'RUNNING') {
          await route.fulfill({
            status: 400,
            contentType: 'application/json',
            body: JSON.stringify({
              message: `Unsupported cancel-active status filter: ${statusFilter}`,
            }),
          });
          return;
        }
        let cancelledCount = 0;
        for (const [taskId, task] of queueDeclinedExportAsyncTasks.entries()) {
          const taskStatus = (task.status || '').toUpperCase();
          const active = taskStatus === 'QUEUED' || taskStatus === 'RUNNING';
          if (!active) {
            continue;
          }
          if (statusFilter && taskStatus !== statusFilter) {
            continue;
          }
          queueDeclinedExportAsyncTasks.set(taskId, {
            ...task,
            status: 'CANCELLED',
            error: 'Cancelled by cancel-active',
            finishedAt: new Date().toISOString(),
          });
          cancelledCount += 1;
        }
        const remainingActiveCount = Array.from(queueDeclinedExportAsyncTasks.values()).filter((task) => {
          const normalized = (task.status || '').toUpperCase();
          return normalized === 'QUEUED' || normalized === 'RUNNING';
        }).length;
        await route.fulfill({
          status: 200,
          contentType: 'application/json',
          body: JSON.stringify({
            cancelledCount,
            remainingActiveCount,
            statusFilter: statusFilter || null,
            message: cancelledCount > 0
              ? `Cancelled ${cancelledCount} active queue declined async export task(s)`
              : 'No active queue declined async export tasks matched cancel-active filters.',
          }),
        });
        return;
      }

      if (parts.length === 2 && parts[0] === 'retry-terminal' && parts[1] === 'by-task-ids' && method === 'POST') {
        const requestBody = route.request().postDataJSON() as { sourceTaskIds?: string[] } | null;
        const sourceTaskIds = Array.from(new Set((requestBody?.sourceTaskIds || [])
          .map((taskId) => String(taskId || '').trim())
          .filter((taskId) => taskId.length > 0)));
        queueDeclinedExportAsyncRetrySelectedCalls.push({
          sourceTaskIds,
        });
        const results = sourceTaskIds.map((sourceTaskId) => {
          const sourceTask = queueDeclinedExportAsyncTasks.get(sourceTaskId);
          if (!sourceTask) {
            return {
              sourceTaskId,
              sourceStatus: 'NOT_FOUND',
              newTaskId: null,
              outcome: 'SKIPPED',
              message: 'Source task not found',
            };
          }
          const taskStatus = (sourceTask.status || '').toUpperCase();
          if (taskStatus !== 'FAILED' && taskStatus !== 'CANCELLED' && taskStatus !== 'COMPLETED') {
            return {
              sourceTaskId,
              sourceStatus: sourceTask.status,
              newTaskId: null,
              outcome: 'SKIPPED',
              message: 'Source task is not terminal',
            };
          }
          const sequence = queueDeclinedExportAsyncRetrySelectedSequence;
          queueDeclinedExportAsyncRetrySelectedSequence += 1;
          const retriedTaskId = `queue-declined-export-task-retry-selected-${String(sequence + 1).padStart(4, '0')}`;
          const retriedTask = {
            ...sourceTask,
            taskId: retriedTaskId,
            status: 'QUEUED',
            createdAt: new Date().toISOString(),
            finishedAt: null,
            filename: `preview_queue_declined_async_${retriedTaskId}.csv`,
            error: null,
            message: `Retried selected from ${sourceTaskId}`,
            listPollCount: 0,
          };
          queueDeclinedExportAsyncTasks.set(retriedTaskId, retriedTask);
          return {
            sourceTaskId,
            sourceStatus: sourceTask.status,
            newTaskId: retriedTaskId,
            outcome: 'RETRIED',
            message: 'Retried selected terminal async export task',
          };
        });
        const retried = results.filter((item) => item.outcome === 'RETRIED').length;
        const reused = results.filter((item) => item.outcome === 'REUSED').length;
        const skipped = results.filter((item) => item.outcome === 'SKIPPED').length;
        await route.fulfill({
          status: 202,
          contentType: 'application/json',
          headers: {
            location: '/api/v1/preview/diagnostics/queue/declined/export-async',
          },
          body: JSON.stringify({
            requested: sourceTaskIds.length,
            retried,
            reused,
            skipped,
            failed: 0,
            limit: sourceTaskIds.length,
            statusFilter: 'BY_TASK_IDS',
            message: sourceTaskIds.length > 0
              ? `Retried ${retried}/${sourceTaskIds.length} selected terminal async preview queue declined export tasks (reused=${reused}, skipped=${skipped}, failed=0)`
              : 'No source task ids provided for terminal async preview queue declined export retry',
            results,
          }),
        });
        return;
      }

      if (parts.length === 2 && parts[0] === 'retry-terminal' && parts[1] === 'dry-run' && method === 'POST') {
        const rawLimit = Number(requestUrl.searchParams.get('limit') || '20');
        const retryLimit = Number.isFinite(rawLimit) && rawLimit > 0 ? Math.floor(rawLimit) : 20;
        queueDeclinedExportAsyncRetryTerminalDryRunCalls.push({
          status: statusFilter || 'ALL',
          limit: retryLimit,
        });
        if (statusFilter && statusFilter !== 'COMPLETED' && statusFilter !== 'CANCELLED' && statusFilter !== 'FAILED') {
          await route.fulfill({
            status: 400,
            contentType: 'application/json',
            body: JSON.stringify({
              message: `Unsupported retry-terminal dry-run status filter: ${statusFilter}`,
            }),
          });
          return;
        }
        const allowedStatuses = statusFilter
          ? new Set([statusFilter])
          : new Set(['FAILED', 'CANCELLED']);
        const sourceTasks = Array.from(queueDeclinedExportAsyncTasks.values())
          .filter((task) => allowedStatuses.has((task.status || '').toUpperCase()))
          .sort((left, right) => (right.createdAt || '').localeCompare(left.createdAt || ''))
          .slice(0, retryLimit);
        const results = sourceTasks.map((task) => ({
          sourceTaskId: task.taskId,
          sourceStatus: task.status,
          outcome: 'RETRYABLE',
          message: 'Terminal async export task can be retried',
        }));
        await route.fulfill({
          status: 200,
          contentType: 'application/json',
          body: JSON.stringify({
            limit: retryLimit,
            requested: sourceTasks.length,
            retryable: results.length,
            skipped: 0,
            statusFilter: statusFilter || null,
            message: results.length > 0
              ? `Dry-run identified ${results.length}/${sourceTasks.length} retryable terminal async preview queue declined export tasks (skipped=0)`
              : 'No terminal async preview queue declined export tasks matched retry dry-run filters',
            results,
          }),
        });
        return;
      }

      if (parts.length === 3 && parts[0] === 'retry-terminal' && parts[1] === 'dry-run' && parts[2] === 'export' && method === 'GET') {
        const rawLimit = Number(requestUrl.searchParams.get('limit') || '20');
        const retryLimit = Number.isFinite(rawLimit) && rawLimit > 0 ? Math.floor(rawLimit) : 20;
        queueDeclinedExportAsyncRetryTerminalDryRunExportCalls.push({
          status: statusFilter || 'ALL',
          limit: retryLimit,
        });
        if (statusFilter && statusFilter !== 'COMPLETED' && statusFilter !== 'CANCELLED' && statusFilter !== 'FAILED') {
          await route.fulfill({
            status: 400,
            contentType: 'application/json',
            body: JSON.stringify({
              message: `Unsupported retry-terminal dry-run export status filter: ${statusFilter}`,
            }),
          });
          return;
        }
        const allowedStatuses = statusFilter
          ? new Set([statusFilter])
          : new Set(['FAILED', 'CANCELLED']);
        const sourceTasks = Array.from(queueDeclinedExportAsyncTasks.values())
          .filter((task) => allowedStatuses.has((task.status || '').toUpperCase()))
          .sort((left, right) => (right.createdAt || '').localeCompare(left.createdAt || ''))
          .slice(0, retryLimit);
        const retryableCount = sourceTasks.length;
        const rows = sourceTasks.map((task) => (
          `${statusFilter || 'FAILED|CANCELLED'},${retryLimit},${sourceTasks.length},${retryableCount},0,${task.taskId},${task.status},RETRYABLE,TERMINAL_TASK_RETRYABLE,Terminal async export task can be retried`
        )).join('\n');
        const payload = [
          'statusFilter,limit,requested,retryable,skipped,sourceTaskId,sourceStatus,outcome,reasonCode,message',
          rows || `${statusFilter || 'FAILED|CANCELLED'},${retryLimit},0,0,0,,,,,`,
          '',
          'reasonCode,outcome,count',
          retryableCount > 0 ? `TERMINAL_TASK_RETRYABLE,RETRYABLE,${retryableCount}` : 'NONE,UNKNOWN,0',
          '',
        ].join('\n');
        await route.fulfill({
          status: 200,
          contentType: 'text/csv; charset=UTF-8',
          headers: {
            'content-disposition': 'attachment; filename="preview_queue_declined_async_retry_dry_run.csv"',
            'x-preview-queue-declined-retry-dry-run-count': String(sourceTasks.length),
          },
          body: payload,
        });
        return;
      }

      if (parts.length === 1 && parts[0] === 'retry-terminal' && method === 'POST') {
        const rawLimit = Number(requestUrl.searchParams.get('limit') || '20');
        const retryLimit = Number.isFinite(rawLimit) && rawLimit > 0 ? Math.floor(rawLimit) : 20;
        queueDeclinedExportAsyncRetryTerminalCalls.push({
          status: statusFilter || 'ALL',
          limit: retryLimit,
        });
        if (statusFilter && statusFilter !== 'COMPLETED' && statusFilter !== 'CANCELLED' && statusFilter !== 'FAILED') {
          await route.fulfill({
            status: 400,
            contentType: 'application/json',
            body: JSON.stringify({
              message: `Unsupported retry-terminal status filter: ${statusFilter}`,
            }),
          });
          return;
        }

        const allowedStatuses = statusFilter
          ? new Set([statusFilter])
          : new Set(['FAILED', 'CANCELLED']);
        const sourceTasks = Array.from(queueDeclinedExportAsyncTasks.values())
          .filter((task) => allowedStatuses.has((task.status || '').toUpperCase()))
          .sort((left, right) => (right.createdAt || '').localeCompare(left.createdAt || ''))
          .slice(0, retryLimit);

        const results = sourceTasks.map((task) => {
          const sequence = queueDeclinedExportAsyncRetryTerminalSequence;
          queueDeclinedExportAsyncRetryTerminalSequence += 1;
          const retriedTaskId = sequence === 0
            ? queueDeclinedExportAsyncRetryTerminalTaskId
            : `queue-declined-export-task-retry-terminal-${String(sequence + 1).padStart(4, '0')}`;
          const retriedTask = {
            ...task,
            taskId: retriedTaskId,
            status: 'QUEUED',
            createdAt: new Date().toISOString(),
            finishedAt: null,
            filename: `preview_queue_declined_async_${retriedTaskId}.csv`,
            error: null,
            message: `Retried terminal from ${task.taskId}`,
            listPollCount: 0,
          };
          queueDeclinedExportAsyncTasks.set(retriedTaskId, retriedTask);
          return {
            sourceTaskId: task.taskId,
            sourceStatus: task.status,
            newTaskId: retriedTaskId,
            outcome: 'RETRIED',
            message: 'Retried terminal async export task',
          };
        });
        const reused = results.filter((item) => item.outcome === 'REUSED').length;

        await route.fulfill({
          status: 202,
          contentType: 'application/json',
          headers: {
            location: '/api/v1/preview/diagnostics/queue/declined/export-async',
          },
          body: JSON.stringify({
            limit: retryLimit,
            requested: sourceTasks.length,
            retried: results.length,
            reused,
            skipped: 0,
            failed: 0,
            statusFilter: statusFilter || null,
            message: results.length > 0
              ? `Retried ${results.length}/${sourceTasks.length} terminal async preview queue declined export tasks (reused=${reused}, skipped=0, failed=0)`
              : 'No terminal async preview queue declined export tasks matched retry filters',
            results,
          }),
        });
        return;
      }

      if (parts.length >= 1) {
        const taskId = decodeURIComponent(parts[0]);
        const task = queueDeclinedExportAsyncTasks.get(taskId);
        if (!task) {
          await route.fulfill({
            status: 404,
            contentType: 'application/json',
            body: JSON.stringify({ message: `Task not found: ${taskId}` }),
          });
          return;
        }

        if (parts.length === 1 && method === 'GET') {
          queueDeclinedExportAsyncGetCalls.push(taskId);
          await route.fulfill({
            status: 200,
            contentType: 'application/json',
            body: JSON.stringify(task),
          });
          return;
        }

        if (parts.length === 2 && parts[1] === 'cancel' && method === 'POST') {
          queueDeclinedExportAsyncCancelCalls.push(taskId);
          const cancelledTask = {
            ...task,
            status: 'CANCELLED',
            finishedAt: new Date().toISOString(),
            message: 'Cancelled by user',
          };
          queueDeclinedExportAsyncTasks.set(taskId, cancelledTask);
          await route.fulfill({
            status: 200,
            contentType: 'application/json',
            body: JSON.stringify(cancelledTask),
          });
          return;
        }

        if (parts.length === 2 && parts[1] === 'retry' && method === 'POST') {
          queueDeclinedExportAsyncRetryCalls.push(taskId);
          const activeExistingTask = Array.from(queueDeclinedExportAsyncTasks.values())
            .find((existingTask) => {
              const existingStatus = (existingTask.status || '').toUpperCase();
              if (existingStatus !== 'QUEUED' && existingStatus !== 'RUNNING') {
                return false;
              }
              return String(existingTask.retrySourceTaskId || '').trim() === taskId;
            });
          if (activeExistingTask) {
            queueDeclinedExportAsyncRetryDedupCalls.push({
              sourceTaskId: taskId,
              reusedTaskId: activeExistingTask.taskId,
            });
            await route.fulfill({
              status: 202,
              contentType: 'application/json',
              headers: {
                location: `/api/v1/preview/diagnostics/queue/declined/export-async/${activeExistingTask.taskId}`,
              },
              body: JSON.stringify({
                ...activeExistingTask,
                deduplicated: true,
                deduplicatedFromTaskId: activeExistingTask.taskId,
                message: 'Reused active queue declined async export retry task for same source',
              }),
            });
            return;
          }
          const retrySequence = queueDeclinedExportAsyncRetrySequence;
          queueDeclinedExportAsyncRetrySequence += 1;
          const retriedTaskId = retrySequence === 0
            ? queueDeclinedExportAsyncRetriedTaskId
            : `queue-declined-export-task-retried-${String(retrySequence + 1).padStart(4, '0')}`;
          const retriedTask = {
            ...task,
            taskId: retriedTaskId,
            status: 'QUEUED',
            createdAt: new Date().toISOString(),
            finishedAt: null,
            filename: `preview_queue_declined_async_${retriedTaskId}.csv`,
            error: null,
            message: `Retried from ${taskId}`,
            deduplicated: false,
            deduplicatedFromTaskId: null,
            retrySourceTaskId: taskId,
            listPollCount: 0,
          };
          queueDeclinedExportAsyncTasks.set(retriedTaskId, retriedTask);
          await route.fulfill({
            status: 202,
            contentType: 'application/json',
            headers: {
              location: `/api/v1/preview/diagnostics/queue/declined/export-async/${retriedTaskId}`,
            },
            body: JSON.stringify(retriedTask),
          });
          return;
        }

        if (parts.length === 2 && parts[1] === 'download' && method === 'GET') {
          queueDeclinedExportAsyncDownloadCalls.push(taskId);
          await route.fulfill({
            status: 200,
            contentType: 'text/csv; charset=UTF-8',
            headers: {
              'content-disposition': `attachment; filename="${task.filename || 'preview_queue_declined_async.csv'}"`,
            },
            body: `documentId,name,category,reason\n${retryableId},${retryableName},QUIET_PERIOD,Within quiet period for policy: default\n`,
          });
          return;
        }
      }
    }

    const allItems = [
      {
        documentId: retryableId,
        name: retryableName,
        path: '/Root/Documents/e2e-preview-diagnostics/retryable.pdf',
        mimeType: 'application/pdf',
        previewStatus: 'FAILED',
        reason: 'Within quiet period for policy: default',
        category: 'QUIET_PERIOD',
        governanceKey: `${retryableId}|preview|quiet-hash`,
        declinedAt: new Date(nowMs - (30 * 60 * 1000)).toISOString(),
        nextEligibleAt: new Date(Date.now() + 120_000).toISOString(),
        forceRequired: false,
      },
      {
        documentId: permanentId,
        name: permanentName,
        path: '/Root/Documents/e2e-preview-diagnostics/permanent.pdf',
        mimeType: 'application/pdf',
        previewStatus: 'FAILED',
        reason: 'Preview failed permanently; use force=true to rebuild',
        category: 'PERMANENT_FAILURE',
        governanceKey: `${permanentId}|preview|permanent-hash`,
        declinedAt: new Date(nowMs - (36 * 60 * 60 * 1000)).toISOString(),
        nextEligibleAt: null,
        forceRequired: true,
      },
    ];

    const filteredItems = allItems.filter((item) => {
      if (category !== 'ANY' && item.category !== category) {
        return false;
      }
      if (forceRequired === 'YES' && !item.forceRequired) {
        return false;
      }
      if (forceRequired === 'NO' && item.forceRequired) {
        return false;
      }
      if (windowStartMs !== null) {
        const declinedAtMs = Date.parse(item.declinedAt || '');
        if (!Number.isFinite(declinedAtMs) || declinedAtMs < windowStartMs) {
          return false;
        }
      }
      if (!queryLower) {
        return true;
      }
      return [
        item.documentId,
        item.name,
        item.path,
        item.mimeType,
        item.previewStatus,
        item.reason,
        item.category,
        item.governanceKey,
      ].some((value) => String(value || '').toLowerCase().includes(queryLower));
    });

    if (pathname.endsWith('/queue/declined/export') && method === 'GET') {
      queueDeclinedExportCalls.push({ limit, category, forceRequired, windowHours: windowHoursParam, query });
      const lines = filteredItems.length > 0
        ? filteredItems.map((item) => (
          `true,2,20,false,${category},${forceRequired},${query},${windowHours || ''},2,${filteredItems.length},${item.documentId},${item.name},${item.path},${item.mimeType},${item.previewStatus},${item.reason},${item.category},${item.governanceKey},${item.declinedAt},${item.nextEligibleAt || ''},${item.forceRequired}`
        )).join('\n')
        : `true,2,20,false,${category},${forceRequired},${query},${windowHours || ''},2,0`;
      await route.fulfill({
        status: 200,
        contentType: 'text/csv; charset=UTF-8',
        headers: {
          'content-disposition': 'attachment; filename="preview_queue_declined.csv"',
          'x-preview-queue-declined-count': String(filteredItems.length),
        },
        body: `queueEnabled,totalDeclined,sampleLimit,sampleTruncated,categoryFilter,forceRequiredFilter,queryFilter,windowHoursFilter,totalSampledItems,filteredSampledItems,documentId,name,path,mimeType,previewStatus,reason,category,governanceKey,declinedAt,nextEligibleAt,forceRequired\n${lines}\n`,
      });
      return;
    }

    if (pathname.endsWith('/queue/declined/requeue') && method === 'POST') {
      const force = (requestUrl.searchParams.get('force') || 'true').toLowerCase() !== 'false';
      queueDeclinedRequeueCalls.push({ limit, category, forceRequired, windowHours: windowHoursParam, query, force });
      const matchedItems = category === 'QUIET_PERIOD' && forceRequired === 'NO' && query === 'retryable'
        ? filteredItems
        : [];
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          categoryFilter: category,
          forceRequiredFilter: forceRequired,
          windowHoursFilter: windowHours || null,
          queryFilter: query || null,
          limit: Number(limit),
          force,
          requested: matchedItems.length,
          queued: matchedItems.length,
          skipped: 0,
          failed: 0,
          results: matchedItems.map((item) => ({
            documentId: item.documentId,
            category: item.category,
            outcome: 'QUEUED',
            message: 'Preview queued',
            previewStatus: item.previewStatus,
          })),
        }),
      });
      return;
    }

    if (pathname.endsWith('/queue/declined/requeue/dry-run/export') && method === 'GET') {
      const force = (requestUrl.searchParams.get('force') || 'true').toLowerCase() !== 'false';
      queueDeclinedDryRunExportCalls.push({ limit, category, forceRequired, windowHours: windowHoursParam, query, force });
      const matchedItems = category === 'QUIET_PERIOD' && forceRequired === 'NO' && query === 'retryable'
        ? filteredItems
        : [];
      const reasonCode = force ? 'DECLINED_REQUEUE_ELIGIBLE' : 'DECLINED_QUIET_PERIOD_BLOCKED';
      const preflightStatus = force ? 'ELIGIBLE' : 'SKIPPED';
      const preflightSkipReason = force ? '' : 'QUIET_PERIOD_ACTIVE';
      const preflightRoute = force ? 'FORCE_REQUEUE' : 'QUIET_PERIOD_GATE';
      const preflightPolicyProfile = 'policy-default';
      const preflightPipeline = 'preview-requeue';
      const rows = matchedItems.map((item) => (
        `${item.documentId},${item.category},${force ? 'QUEUED' : 'SKIPPED'},${reasonCode},${force ? 'Preview queued' : 'Within quiet period for policy: default'},${item.nextEligibleAt || ''},${preflightStatus},${preflightSkipReason},${preflightRoute},${preflightPolicyProfile},${preflightPipeline}`
      )).join('\n');
      await route.fulfill({
        status: 200,
        contentType: 'text/csv; charset=UTF-8',
        headers: {
          'content-disposition': 'attachment; filename="preview_queue_declined_requeue_dry_run.csv"',
          'x-preview-queue-declined-requeue-dry-run-count': String(matchedItems.length),
        },
        body: `documentId,category,outcome,reasonCode,message,nextAttemptAt,preflightStatus,preflightSkipReason,preflightRoute,preflightPolicyProfile,preflightPipeline\n${rows}\n`,
      });
      return;
    }

    if (pathname.endsWith('/queue/declined/requeue/dry-run') && method === 'POST') {
      const force = (requestUrl.searchParams.get('force') || 'true').toLowerCase() !== 'false';
      queueDeclinedDryRunCalls.push({ limit, category, forceRequired, windowHours: windowHoursParam, query, force });
      const matchedItems = category === 'QUIET_PERIOD' && forceRequired === 'NO' && query === 'retryable'
        ? filteredItems
        : [];
      const estimatedQueued = force ? matchedItems.length : 0;
      const estimatedSkipped = force ? 0 : matchedItems.length;
      const reasonCode = force ? 'DECLINED_REQUEUE_ELIGIBLE' : 'DECLINED_QUIET_PERIOD_BLOCKED';
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          categoryFilter: category,
          forceRequiredFilter: forceRequired,
          windowHoursFilter: windowHours || null,
          queryFilter: query || null,
          limit: Number(limit),
          force,
          requested: matchedItems.length,
          estimatedQueued,
          estimatedSkipped,
          estimatedFailed: 0,
          results: matchedItems.map((item) => ({
            documentId: item.documentId,
            category: item.category,
            outcome: force ? 'QUEUED' : 'SKIPPED',
            reasonCode,
            message: force ? 'Preview queued' : 'Within quiet period for policy: default',
            previewStatus: item.previewStatus,
            nextAttemptAt: item.nextEligibleAt,
            preflightStatus: force ? 'ELIGIBLE' : 'SKIPPED',
            preflightSkipReason: force ? null : 'QUIET_PERIOD_ACTIVE',
            preflightRoute: force ? 'FORCE_REQUEUE' : 'QUIET_PERIOD_GATE',
            preflightPolicyProfile: 'policy-default',
            preflightPipeline: 'preview-requeue',
          })),
          reasonBreakdown: matchedItems.length > 0
            ? [{ reasonCode, count: matchedItems.length }]
            : [],
        }),
      });
      return;
    }

    if (pathname.endsWith('/queue/declined/clear') && method === 'POST') {
      queueDeclinedClearCalls.push({ limit, category, forceRequired, windowHours: windowHoursParam, query });
      const matchedItems = category === 'QUIET_PERIOD' && forceRequired === 'NO' && query === 'retryable'
        ? filteredItems
        : [];
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          categoryFilter: category,
          forceRequiredFilter: forceRequired,
          windowHoursFilter: windowHours || null,
          queryFilter: query || null,
          limit: Number(limit),
          requested: matchedItems.length,
          cleared: matchedItems.length,
          skipped: 0,
          failed: 0,
          results: matchedItems.map((item) => ({
            documentId: item.documentId,
            category: item.category,
            outcome: 'CLEARED',
            message: 'Declined queue item cleared',
          })),
        }),
      });
      return;
    }

    queueDeclinedSummaryCalls.push({ limit, category, forceRequired, windowHours: windowHoursParam, query });
    const categoryCounts = Array.from(
      filteredItems.reduce((map, item) => {
        const existing = map.get(item.category) || { category: item.category, count: 0, forceRequiredCount: 0 };
        existing.count += 1;
        if (item.forceRequired) {
          existing.forceRequiredCount += 1;
        }
        map.set(item.category, existing);
        return map;
      }, new Map<string, { category: string; count: number; forceRequiredCount: number }>())
      .values()
    );
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        queueEnabled: true,
        totalDeclined: allItems.length,
        sampleLimit: Number(limit),
        sampleTruncated: false,
        categoryFilter: category,
        forceRequiredFilter: forceRequired,
        windowHoursFilter: windowHours || null,
        queryFilter: query || null,
        totalSampledItems: allItems.length,
        filteredSampledItems: filteredItems.length,
        forceRequiredCount: filteredItems.filter((item) => item.forceRequired).length,
        categoryCounts,
        items: filteredItems,
      }),
    });
  });

  await page.route('**/api/v1/preview/diagnostics/failures/ledger**', async (route) => {
    const requestUrl = new URL(route.request().url());
    const method = route.request().method();
    const pathname = requestUrl.pathname;

    if (pathname.endsWith('/export') && method === 'GET') {
      const days = requestUrl.searchParams.get('days') || '30';
      const limit = requestUrl.searchParams.get('limit') || '500';
      failureLedgerExportCalls.push({ days, limit });
      await route.fulfill({
        status: 200,
        contentType: 'text/csv; charset=UTF-8',
        headers: {
          'content-disposition': 'attachment; filename="preview_failure_ledger.csv"',
          'x-preview-failure-ledger-count': '2',
        },
        body: `documentId,name,path,mimeType,previewStatus,failureCount,failedAt,lastReason,category,retryable,previewLastUpdated,failureContentHash,currentContentHash,staleByContentChange\n${retryableId},${retryableName},/Root/Documents/e2e-preview-diagnostics/retryable.pdf,application/pdf,FAILED,2,2026-02-01T00:00:00Z,Timeout contacting preview service,TEMPORARY,true,2026-02-01T00:00:00Z,hash-a,hash-a,false\n`,
      });
      return;
    }

    if (pathname.endsWith('/reset-batch') && method === 'POST') {
      const payload = route.request().postDataJSON() as { documentIds?: string[] } | null;
      const documentIds = Array.isArray(payload?.documentIds) ? payload!.documentIds : [];
      failureLedgerResetBatchCalls.push(documentIds);
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          requested: documentIds.length,
          deduplicated: documentIds.length,
          reset: documentIds.length,
          failed: 0,
          results: documentIds.map((id) => ({
            documentId: id,
            name: id === retryableId ? retryableName : permanentName,
            previousFailureCount: 1,
            previousFailedAt: new Date().toISOString(),
            previousReason: 'Timeout contacting preview service',
            outcome: 'RESET',
            message: 'Failure ledger reset',
          })),
        }),
      });
      return;
    }

    if (pathname.endsWith('/reset-by-filter') && method === 'POST') {
      const payload = route.request().postDataJSON() as {
        reason?: string;
        category?: string;
        retryable?: boolean;
        days?: number;
        maxDocuments?: number;
      } | null;
      const reason = payload?.reason || 'UNSPECIFIED';
      const category = payload?.category || 'ANY';
      const retryable = payload?.retryable === true;
      const days = Number(payload?.days || 7);
      const maxDocuments = Number(payload?.maxDocuments || 100);
      failureLedgerResetByFilterCalls.push({ reason, category, retryable, days, maxDocuments });
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          reason,
          category,
          retryable,
          windowDays: days,
          maxDocuments,
          totalCandidates: 2,
          scanned: 2,
          matched: 1,
          truncated: false,
          reset: 1,
          skipped: 0,
          failed: 0,
          results: [
            {
              documentId: retryableId,
              name: retryableName,
              previousFailureCount: 2,
              previousFailedAt: new Date().toISOString(),
              previousReason: 'Timeout contacting preview service',
              outcome: 'RESET',
              message: 'Failure ledger reset',
            },
          ],
        }),
      });
      return;
    }

    if (pathname.includes('/reset') && method === 'POST') {
      const segments = pathname.split('/');
      const documentId = segments[segments.length - 2];
      failureLedgerResetCalls.push(documentId);
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          documentId,
          name: documentId === retryableId ? retryableName : permanentName,
          previousFailureCount: 2,
          previousFailedAt: new Date().toISOString(),
          previousReason: 'Timeout contacting preview service',
          outcome: 'RESET',
          message: 'Failure ledger reset',
        }),
      });
      return;
    }

    const days = requestUrl.searchParams.get('days') || '30';
    failureLedgerRequestedDays.push(days);
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        totalEntries: 2,
        sampledEntries: 2,
        limit: 100,
        windowDays: Number(days),
        windowStart: new Date().toISOString(),
        sampleTruncated: false,
        items: [
          {
            documentId: retryableId,
            name: retryableName,
            path: '/Root/Documents/e2e-preview-diagnostics/retryable.pdf',
            mimeType: 'application/pdf',
            previewStatus: 'FAILED',
            failureCount: 2,
            failedAt: new Date().toISOString(),
            lastReason: 'Timeout contacting preview service',
            category: 'TEMPORARY',
            retryable: true,
            previewLastUpdated: new Date().toISOString(),
            failureContentHash: 'hash-a',
            currentContentHash: 'hash-a',
            staleByContentChange: false,
          },
          {
            documentId: permanentId,
            name: permanentName,
            path: '/Root/Documents/e2e-preview-diagnostics/permanent.pdf',
            mimeType: 'application/pdf',
            previewStatus: 'FAILED',
            failureCount: 1,
            failedAt: new Date().toISOString(),
            lastReason: 'Error generating preview: Missing root object specification in trailer.',
            category: 'PERMANENT',
            retryable: false,
            previewLastUpdated: new Date().toISOString(),
            failureContentHash: 'hash-old',
            currentContentHash: 'hash-new',
            staleByContentChange: true,
          },
        ],
      }),
    });
  });

  await page.route('**/api/v1/preview/diagnostics/renditions/summary**', async (route) => {
    const requestUrl = new URL(route.request().url());
    const days = requestUrl.searchParams.get('days') || '7';
    const sampleLimit = requestUrl.searchParams.get('sampleLimit') || '500';
    requestedRenditionSummaryDays.push(days);
    requestedRenditionSummarySampleLimits.push(sampleLimit);

    const payload = days === '30'
      ? {
          totalResources: 140,
          sampledResources: 120,
          sampleLimit: Number(sampleLimit),
          windowDays: 30,
          windowStart: new Date().toISOString(),
          sampleTruncated: true,
          statusCounts: [
            { status: 'READY', count: 70 },
            { status: 'FAILED', count: 40 },
            { status: 'UNSUPPORTED', count: 10 },
          ],
          topReasons: [
            { reason: 'Top transient image rasterization timeout', count: 15 },
            { reason: 'Embedded font extraction failed', count: 11 },
            { reason: 'Encrypted archive requires password', count: 9 },
            { reason: 'Remote source download timeout', count: 8 },
            { reason: 'Page render memory pressure', count: 6 },
            { reason: 'Overflow reason should never be visible', count: 5 },
          ],
        }
      : {
          totalResources: 80,
          sampledResources: 80,
          sampleLimit: Number(sampleLimit),
          windowDays: 7,
          windowStart: new Date().toISOString(),
          sampleTruncated: false,
          statusCounts: [
            { status: 'READY', count: 53 },
            { status: 'FAILED', count: 21 },
            { status: 'UNSUPPORTED', count: 6 },
          ],
          topReasons: [
            { reason: 'Top transient image rasterization timeout', count: 9 },
            { reason: 'Embedded font extraction failed', count: 5 },
            { reason: 'Encrypted archive requires password', count: 3 },
            { reason: 'Remote source download timeout', count: 2 },
            { reason: 'Page render memory pressure', count: 1 },
            { reason: 'Overflow reason should never be visible', count: 1 },
          ],
        };

    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify(payload),
    });
  });

  await page.route('**/api/v1/preview/diagnostics/renditions/resources**', async (route) => {
    const requestUrl = new URL(route.request().url());
    const method = route.request().method().toUpperCase();
    const asyncPrefix = '/api/v1/preview/diagnostics/renditions/resources/export-async';
    if (requestUrl.pathname.startsWith(asyncPrefix)) {
      const suffix = requestUrl.pathname.slice(asyncPrefix.length);
      const parts = suffix.split('/').filter(Boolean);
      const statusFilter = (requestUrl.searchParams.get('status') || '').toUpperCase();
      const getRetryableTerminalRenditionTasks = (targetStatusFilter: string, limit: number) => {
        const allowedStatuses = targetStatusFilter
          ? new Set([targetStatusFilter])
          : new Set(['FAILED', 'CANCELLED']);
        return Array.from(renditionResourceExportAsyncTasks.values())
          .filter((task) => allowedStatuses.has((task.status || '').toUpperCase()))
          .sort((left, right) => (right.createdAt || '').localeCompare(left.createdAt || ''))
          .slice(0, limit);
      };

      if (parts.length === 0 && method === 'POST') {
        const payload = route.request().postDataJSON() as { days?: number; limit?: number } | null;
        const days = Number(payload?.days || 7);
        const limit = Number(payload?.limit || 500);
        renditionResourceExportAsyncStartCalls.push({ days, limit });
        const startedTask = {
          taskId: renditionResourceExportAsyncStartedTaskId,
          status: 'QUEUED',
          createdAt: new Date().toISOString(),
          finishedAt: null,
          filename: 'preview_rendition_resources_async_started.csv',
          error: null,
          message: null,
        };
        renditionResourceExportAsyncTasks.set(startedTask.taskId, startedTask);
        await route.fulfill({
          status: 200,
          contentType: 'application/json',
          body: JSON.stringify(startedTask),
        });
        return;
      }

      if (parts.length === 1 && parts[0] === 'summary' && method === 'GET') {
        renditionResourceExportAsyncSummaryCalls.push({
          status: statusFilter || 'ALL',
        });
        const filtered = Array.from(renditionResourceExportAsyncTasks.values()).filter((task) => {
          if (!statusFilter) {
            return true;
          }
          return (task.status || '').toUpperCase() === statusFilter;
        });
        const queuedCount = filtered.filter((task) => (task.status || '').toUpperCase() === 'QUEUED').length;
        const runningCount = filtered.filter((task) => (task.status || '').toUpperCase() === 'RUNNING').length;
        const completedCount = filtered.filter((task) => (task.status || '').toUpperCase() === 'COMPLETED').length;
        const cancelledCount = filtered.filter((task) => (task.status || '').toUpperCase() === 'CANCELLED').length;
        const failedCount = filtered.filter((task) => (task.status || '').toUpperCase() === 'FAILED').length;
        await route.fulfill({
          status: 200,
          contentType: 'application/json',
          body: JSON.stringify({
            totalCount: filtered.length,
            queuedCount,
            runningCount,
            completedCount,
            cancelledCount,
            failedCount,
            activeCount: queuedCount + runningCount,
            terminalCount: completedCount + cancelledCount + failedCount,
          }),
        });
        return;
      }

      if (parts.length === 1 && parts[0] === 'cleanup' && method === 'POST') {
        renditionResourceExportAsyncCleanupCalls.push({
          status: statusFilter || 'ALL',
        });
        const terminalStatuses = new Set(['COMPLETED', 'CANCELLED', 'FAILED']);
        let deletedCount = 0;
        for (const [taskId, task] of renditionResourceExportAsyncTasks.entries()) {
          const taskStatus = (task.status || '').toUpperCase();
          if (statusFilter) {
            if (taskStatus !== statusFilter) {
              continue;
            }
          } else if (!terminalStatuses.has(taskStatus)) {
            continue;
          }
          renditionResourceExportAsyncTasks.delete(taskId);
          deletedCount += 1;
        }
        await route.fulfill({
          status: 200,
          contentType: 'application/json',
          body: JSON.stringify({
            deletedCount,
            remainingCount: renditionResourceExportAsyncTasks.size,
            statusFilter: statusFilter || null,
            message: deletedCount > 0
              ? `Rendition async export cleanup removed ${deletedCount} task(s)`
              : 'No rendition async export tasks matched cleanup filters',
          }),
        });
        return;
      }

      if (parts.length === 1 && parts[0] === 'cancel-active' && method === 'POST') {
        renditionResourceExportAsyncCancelActiveCalls.push({
          status: statusFilter || 'ALL',
        });
        if (statusFilter && statusFilter !== 'QUEUED' && statusFilter !== 'RUNNING') {
          await route.fulfill({
            status: 400,
            contentType: 'application/json',
            body: JSON.stringify({
              message: `Unsupported cancel-active status filter: ${statusFilter}`,
            }),
          });
          return;
        }
        let cancelledCount = 0;
        for (const [taskId, task] of renditionResourceExportAsyncTasks.entries()) {
          const taskStatus = (task.status || '').toUpperCase();
          const active = taskStatus === 'QUEUED' || taskStatus === 'RUNNING';
          if (!active) {
            continue;
          }
          if (statusFilter && taskStatus !== statusFilter) {
            continue;
          }
          renditionResourceExportAsyncTasks.set(taskId, {
            ...task,
            status: 'CANCELLED',
            error: 'Cancelled by cancel-active',
            finishedAt: new Date().toISOString(),
          });
          cancelledCount += 1;
        }
        const remainingActiveCount = Array.from(renditionResourceExportAsyncTasks.values()).filter((task) => {
          const normalized = (task.status || '').toUpperCase();
          return normalized === 'QUEUED' || normalized === 'RUNNING';
        }).length;
        await route.fulfill({
          status: 200,
          contentType: 'application/json',
          body: JSON.stringify({
            cancelledCount,
            remainingActiveCount,
            statusFilter: statusFilter || null,
            message: cancelledCount > 0
              ? `Cancelled ${cancelledCount} active rendition async export task(s)`
              : 'No active rendition async export tasks matched cancel-active filters.',
          }),
        });
        return;
      }

      if (parts.length === 0 && method === 'GET') {
        const rawMaxItems = Number(
          requestUrl.searchParams.get('maxItems')
          || requestUrl.searchParams.get('limit')
          || '20'
        );
        const limit = Number.isFinite(rawMaxItems) && rawMaxItems > 0 ? Math.floor(rawMaxItems) : 20;
        const rawSkipCount = Number(requestUrl.searchParams.get('skipCount') || '0');
        const skipCount = Number.isFinite(rawSkipCount) && rawSkipCount > 0 ? Math.floor(rawSkipCount) : 0;
        renditionResourceExportAsyncListCalls.push({
          limit,
          skipCount,
          status: statusFilter || 'ALL',
        });
        const filteredItems = Array.from(renditionResourceExportAsyncTasks.values())
          .filter((task) => {
            if (!statusFilter) {
              return true;
            }
            return (task.status || '').toUpperCase() === statusFilter;
          })
          .sort((left, right) => (right.createdAt || '').localeCompare(left.createdAt || ''));
        const items = filteredItems.slice(skipCount, skipCount + Math.max(1, limit));
        const totalItems = filteredItems.length;
        await route.fulfill({
          status: 200,
          contentType: 'application/json',
          body: JSON.stringify({
            count: items.length,
            paging: {
              skipCount,
              maxItems: limit,
              totalItems,
              hasMoreItems: skipCount + items.length < totalItems,
            },
            items,
          }),
        });
        return;
      }

      if (parts.length === 2 && parts[0] === 'retry-terminal' && parts[1] === 'dry-run' && method === 'POST') {
        const rawLimit = Number(requestUrl.searchParams.get('limit') || '20');
        const retryLimit = Number.isFinite(rawLimit) && rawLimit > 0 ? Math.floor(rawLimit) : 20;
        renditionResourceExportAsyncRetryTerminalDryRunCalls.push({
          status: statusFilter || 'ALL',
          limit: retryLimit,
        });
        if (statusFilter && statusFilter !== 'COMPLETED' && statusFilter !== 'CANCELLED' && statusFilter !== 'FAILED') {
          await route.fulfill({
            status: 400,
            contentType: 'application/json',
            body: JSON.stringify({
              message: `Unsupported retry-terminal dry-run status filter: ${statusFilter}`,
            }),
          });
          return;
        }
        const sourceTasks = getRetryableTerminalRenditionTasks(statusFilter, retryLimit);
        const results = sourceTasks.map((task) => ({
          sourceTaskId: task.taskId,
          sourceStatus: task.status,
          outcome: 'RETRYABLE',
          reasonCode: 'TERMINAL_TASK_RETRYABLE',
          message: 'Terminal rendition async export task can be retried',
        }));
        await route.fulfill({
          status: 200,
          contentType: 'application/json',
          body: JSON.stringify({
            requested: sourceTasks.length,
            retryable: results.length,
            skipped: 0,
            limit: retryLimit,
            statusFilter: statusFilter || null,
            message: results.length > 0
              ? `Dry-run identified ${results.length}/${sourceTasks.length} retryable terminal rendition async export tasks (skipped=0)`
              : 'No terminal rendition async export tasks matched dry-run filters',
            results,
            reasonBreakdown: results.length > 0
              ? [{ reasonCode: 'TERMINAL_TASK_RETRYABLE', outcome: 'RETRYABLE', count: results.length }]
              : [],
          }),
        });
        return;
      }

      if (parts.length === 3 && parts[0] === 'retry-terminal' && parts[1] === 'dry-run' && parts[2] === 'export' && method === 'GET') {
        const rawLimit = Number(requestUrl.searchParams.get('limit') || '20');
        const retryLimit = Number.isFinite(rawLimit) && rawLimit > 0 ? Math.floor(rawLimit) : 20;
        renditionResourceExportAsyncRetryTerminalDryRunExportCalls.push({
          status: statusFilter || 'ALL',
          limit: retryLimit,
        });
        if (statusFilter && statusFilter !== 'COMPLETED' && statusFilter !== 'CANCELLED' && statusFilter !== 'FAILED') {
          await route.fulfill({
            status: 400,
            contentType: 'application/json',
            body: JSON.stringify({
              message: `Unsupported retry-terminal dry-run export status filter: ${statusFilter}`,
            }),
          });
          return;
        }
        const sourceTasks = getRetryableTerminalRenditionTasks(statusFilter, retryLimit);
        const retryableCount = sourceTasks.length;
        const rows = sourceTasks.map((task) => (
          `${statusFilter || 'FAILED|CANCELLED'},${retryLimit},${sourceTasks.length},${retryableCount},0,${task.taskId},${task.status},RETRYABLE,TERMINAL_TASK_RETRYABLE,Terminal rendition async export task can be retried`
        )).join('\n');
        const payload = [
          'statusFilter,limit,requested,retryable,skipped,sourceTaskId,sourceStatus,outcome,reasonCode,message',
          rows || `${statusFilter || 'FAILED|CANCELLED'},${retryLimit},0,0,0,,,,,`,
          '',
          'reasonCode,outcome,count',
          retryableCount > 0 ? `TERMINAL_TASK_RETRYABLE,RETRYABLE,${retryableCount}` : 'NONE,UNKNOWN,0',
          '',
        ].join('\n');
        await route.fulfill({
          status: 200,
          contentType: 'text/csv; charset=UTF-8',
          headers: {
            'content-disposition': 'attachment; filename="preview_rendition_resources_async_retry_dry_run.csv"',
            'x-preview-rendition-resource-async-retry-dry-run-count': String(sourceTasks.length),
          },
          body: payload,
        });
        return;
      }

      if (parts.length === 2 && parts[0] === 'retry-terminal' && parts[1] === 'by-task-ids' && method === 'POST') {
        const requestBody = route.request().postDataJSON() as { sourceTaskIds?: string[] } | null;
        const sourceTaskIds = Array.from(new Set((requestBody?.sourceTaskIds || [])
          .map((taskId) => String(taskId || '').trim())
          .filter((taskId) => taskId.length > 0)));
        renditionResourceExportAsyncRetrySelectedCalls.push({
          sourceTaskIds,
        });
        const results = sourceTaskIds.map((sourceTaskId) => {
          const sourceTask = renditionResourceExportAsyncTasks.get(sourceTaskId);
          if (!sourceTask) {
            return {
              sourceTaskId,
              sourceStatus: 'NOT_FOUND',
              newTaskId: null,
              outcome: 'SKIPPED',
              message: 'Source task not found',
            };
          }
          const taskStatus = (sourceTask.status || '').toUpperCase();
          if (taskStatus !== 'FAILED' && taskStatus !== 'CANCELLED' && taskStatus !== 'COMPLETED') {
            return {
              sourceTaskId,
              sourceStatus: sourceTask.status,
              newTaskId: null,
              outcome: 'SKIPPED',
              message: 'Source task is not terminal',
            };
          }
          const sequence = renditionResourceExportAsyncRetrySelectedSequence;
          renditionResourceExportAsyncRetrySelectedSequence += 1;
          const retriedTaskId = sequence === 0
            ? renditionResourceExportAsyncRetriedTaskId
            : `rendition-export-task-retried-${String(sequence + 1).padStart(4, '0')}`;
          const retriedTask = {
            ...sourceTask,
            taskId: retriedTaskId,
            status: 'QUEUED',
            createdAt: new Date().toISOString(),
            finishedAt: null,
            filename: `preview_rendition_resources_async_${retriedTaskId}.csv`,
            error: null,
            message: `Retried selected from ${sourceTaskId}`,
            retrySourceTaskId: sourceTaskId,
          };
          renditionResourceExportAsyncTasks.set(retriedTaskId, retriedTask);
          return {
            sourceTaskId,
            sourceStatus: sourceTask.status,
            newTaskId: retriedTaskId,
            outcome: 'RETRIED',
            message: 'Retried selected terminal rendition async export task',
          };
        });
        const retried = results.filter((item) => item.outcome === 'RETRIED').length;
        const skipped = results.filter((item) => item.outcome === 'SKIPPED').length;
        await route.fulfill({
          status: 202,
          contentType: 'application/json',
          headers: {
            location: '/api/v1/preview/diagnostics/renditions/resources/export-async',
          },
          body: JSON.stringify({
            requested: sourceTaskIds.length,
            retried,
            reused: 0,
            skipped,
            failed: 0,
            limit: sourceTaskIds.length,
            statusFilter: 'BY_TASK_IDS',
            message: sourceTaskIds.length > 0
              ? `Retried ${retried}/${sourceTaskIds.length} selected terminal rendition async export tasks (reused=0, skipped=${skipped}, failed=0)`
              : 'No source task ids provided for terminal rendition async export retry',
            results,
          }),
        });
        return;
      }

      if (parts.length >= 1) {
        const taskId = decodeURIComponent(parts[0]);
        const task = renditionResourceExportAsyncTasks.get(taskId);
        if (!task) {
          await route.fulfill({
            status: 404,
            contentType: 'application/json',
            body: JSON.stringify({ message: `Task not found: ${taskId}` }),
          });
          return;
        }

        if (parts.length === 1 && method === 'GET') {
          renditionResourceExportAsyncGetCalls.push(taskId);
          await route.fulfill({
            status: 200,
            contentType: 'application/json',
            body: JSON.stringify(task),
          });
          return;
        }

        if (parts.length === 2 && parts[1] === 'cancel' && method === 'POST') {
          renditionResourceExportAsyncCancelCalls.push(taskId);
          const cancelledTask = {
            ...task,
            status: 'CANCELLED',
            finishedAt: new Date().toISOString(),
            message: 'Cancelled by user',
          };
          renditionResourceExportAsyncTasks.set(taskId, cancelledTask);
          await route.fulfill({
            status: 200,
            contentType: 'application/json',
            body: JSON.stringify(cancelledTask),
          });
          return;
        }

        if (parts.length === 2 && parts[1] === 'download' && method === 'GET') {
          renditionResourceExportAsyncDownloadCalls.push(taskId);
          await route.fulfill({
            status: 200,
            contentType: 'text/csv; charset=UTF-8',
            headers: {
              'content-disposition': `attachment; filename="${task.filename || 'preview_rendition_resources_async.csv'}"`,
            },
            body: `documentId,name,status\n${renditionResourceRetryId},e2e-rendition-resource-7.pdf,FAILED\n`,
          });
          return;
        }
      }
    }

    if (requestUrl.pathname.endsWith('/renditions/resources/export')) {
      const days = requestUrl.searchParams.get('days') || '7';
      const limit = requestUrl.searchParams.get('limit') || '500';
      requestedRenditionResourceExportCalls.push({ days, limit });
      await route.fulfill({
        status: 200,
        contentType: 'text/csv; charset=UTF-8',
        headers: {
          'content-disposition': 'attachment; filename="preview_rendition_resources.csv"',
          'x-preview-rendition-resource-count': '1',
        },
        body: `documentId,name,path,mimeType,previewStatus,renditionStatus,previewFailureCategory,previewFailureReason,previewLastUpdated\n${renditionResourceRetryId},e2e-rendition-resource-7.pdf,/Root/Documents/e2e-rendition-resource-7.pdf,application/pdf,FAILED,FAILED,TEMPORARY,Resource-level rasterization timeout on page 1,2026-03-08T00:00:00Z\n`,
      });
      return;
    }

    const days = requestUrl.searchParams.get('days') || '7';
    const limit = requestUrl.searchParams.get('limit') || '500';
    requestedRenditionResourceDays.push(days);
    requestedRenditionResourceLimits.push(limit);

    const payload = days === '30'
      ? {
          totalResources: 140,
          sampledResources: 2,
          limit: Number(limit),
          windowDays: 30,
          windowStart: new Date().toISOString(),
          sampleTruncated: true,
          items: [
            {
              documentId: renditionResourceRetryId,
              name: 'e2e-rendition-resource-30.pdf',
              path: '/Root/Documents/e2e-rendition-resource-30.pdf',
              mimeType: 'application/pdf',
              previewStatus: 'FAILED',
              renditionStatus: 'FAILED',
              previewFailureReason: 'Embedded font extraction failed',
              previewFailureCategory: 'PERMANENT',
              previewLastUpdated: new Date().toISOString(),
            },
            {
              documentId: renditionResourceUnsupportedId,
              name: 'e2e-rendition-resource-30-secondary.bin',
              path: '/Root/Documents/e2e-rendition-resource-30-secondary.bin',
              mimeType: 'application/octet-stream',
              previewStatus: 'UNSUPPORTED',
              renditionStatus: 'UNSUPPORTED',
              previewFailureReason: 'Preview not supported for mime type application/octet-stream',
              previewFailureCategory: 'PERMANENT',
              previewLastUpdated: new Date().toISOString(),
            },
          ],
        }
      : {
          totalResources: 80,
          sampledResources: 1,
          limit: Number(limit),
          windowDays: 7,
          windowStart: new Date().toISOString(),
          sampleTruncated: false,
          items: [
            {
              documentId: renditionResourceRetryId,
              name: 'e2e-rendition-resource-7.pdf',
              path: '/Root/Documents/e2e-rendition-resource-7.pdf',
              mimeType: 'application/pdf',
              previewStatus: 'FAILED',
              renditionStatus: 'FAILED',
              previewFailureReason: 'Resource-level rasterization timeout on page 1',
              previewFailureCategory: 'TEMPORARY',
              previewLastUpdated: new Date().toISOString(),
            },
          ],
        };

    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify(payload),
    });
  });

  await page.route('**/api/v1/preview/diagnostics/cad-failover', async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        cadPreviewEnabled: true,
        configured: true,
        circuitBreakerEnabled: true,
        circuitFailureThreshold: 3,
        circuitOpenMs: 120000,
        halfOpenTrialTimeoutMs: 30000,
        endpoints: [
          'http://cad-render-primary.local/convert',
          'http://cad-render-fallback.local/convert',
        ],
        endpointStats: [
          {
            endpoint: 'http://cad-render-primary.local/convert',
            successCount: 8,
            failureCount: 2,
            lastSuccessAt: new Date().toISOString(),
            lastFailureAt: new Date().toISOString(),
            lastFailureReason: 'connection timeout',
            consecutiveFailureCount: 1,
            circuitState: 'CLOSED',
            circuitOpenUntil: null,
            lastCircuitOpenedAt: null,
            halfOpenInFlight: false,
          },
          {
            endpoint: 'http://cad-render-fallback.local/convert',
            successCount: 3,
            failureCount: 0,
            lastSuccessAt: new Date().toISOString(),
            lastFailureAt: null,
            lastFailureReason: null,
            consecutiveFailureCount: 0,
            circuitState: 'CLOSED',
            circuitOpenUntil: null,
            lastCircuitOpenedAt: null,
            halfOpenInFlight: false,
          },
        ],
      }),
    });
  });

  await page.route('**/api/v1/preview/diagnostics/traces**', async (route) => {
    const requestUrl = new URL(route.request().url());
    const requestId = (requestUrl.searchParams.get('requestId') || '').trim();
    const traces = [
      {
        requestId: 'pv-120',
        documentId: retryableId,
        mimeType: 'application/pdf',
        source: 'preview',
        startedAt: new Date(Date.now() - 5_000).toISOString(),
        finishedAt: new Date().toISOString(),
        status: 'READY',
        retryNeeded: false,
        failureReason: null,
        latestMessage: 'Preview completed',
        events: [
          {
            at: new Date(Date.now() - 4_000).toISOString(),
            stage: 'ROUTE',
            message: 'pdf',
          },
        ],
      },
      {
        requestId: 'pv-121',
        documentId: retryableTwinId,
        mimeType: 'application/pdf',
        source: 'preview',
        startedAt: new Date(Date.now() - 10_000).toISOString(),
        finishedAt: new Date(Date.now() - 8_000).toISOString(),
        status: 'FAILED',
        retryNeeded: true,
        failureReason: 'Timeout contacting preview service',
        latestMessage: 'Preview completed with retry hint',
        events: [
          {
            at: new Date(Date.now() - 9_000).toISOString(),
            stage: 'CAD_ENDPOINT_FAILURE',
            message: 'timeout',
          },
        ],
      },
    ];
    const filtered = requestId
      ? traces.filter((item) => item.requestId.includes(requestId))
      : traces;

    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify(filtered),
    });
  });

  const opsPolicies = [
    {
      key: 'default',
      label: 'Default',
      maxAttempts: 3,
      retryDelayMs: 60000,
      backoffMultiplier: 1.6,
      quietPeriodMs: 0,
      builtIn: true,
    },
    {
      key: 'cad',
      label: 'CAD',
      maxAttempts: 5,
      retryDelayMs: 60000,
      backoffMultiplier: 2.0,
      quietPeriodMs: 120000,
      builtIn: true,
    },
  ];

  let currentPolicyVersion = 3;

  const buildPolicyHistory = () => {
    const versions = [currentPolicyVersion, currentPolicyVersion - 1, currentPolicyVersion - 2]
      .filter((version) => version > 0);
    return versions.map((version, index) => ({
      version,
      updatedAt: new Date(Date.now() - index * 60_000).toISOString(),
      actor: 'admin',
      reason: index === 0 ? 'current_state' : `history_v${version}`,
    }));
  };

  await page.route('**/api/v1/ops/policies**', async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        domain: 'PREVIEW',
        currentVersion: currentPolicyVersion,
        updatedAt: new Date().toISOString(),
        actor: 'admin',
        reason: 'bootstrap',
        policies: opsPolicies,
      }),
    });
  });

  await page.route('**/api/v1/ops/policies/*/history**', async (route) => {
    policyHistoryCalls.push(Date.now());
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        domain: 'PREVIEW',
        currentVersion: currentPolicyVersion,
        history: buildPolicyHistory(),
      }),
    });
  });

  await page.route('**/api/v1/ops/policies/*', async (route) => {
    const payload = route.request().postDataJSON() as {
      profileKey?: string;
      maxAttempts?: number;
      retryDelayMs?: number;
      backoffMultiplier?: number;
      quietPeriodMs?: number;
      reason?: string;
    } | null;
    const profileKey = payload?.profileKey || 'default';
    const idx = opsPolicies.findIndex((item) => item.key === profileKey);
    const existing = idx >= 0
      ? opsPolicies[idx]
      : {
          key: profileKey,
          label: profileKey.toUpperCase(),
          maxAttempts: 3,
          retryDelayMs: 60000,
          backoffMultiplier: 1.6,
          quietPeriodMs: 0,
          builtIn: false,
        };
    const updated = {
      ...existing,
      maxAttempts: payload?.maxAttempts ?? existing.maxAttempts,
      retryDelayMs: payload?.retryDelayMs ?? existing.retryDelayMs,
      backoffMultiplier: payload?.backoffMultiplier ?? existing.backoffMultiplier,
      quietPeriodMs: payload?.quietPeriodMs ?? existing.quietPeriodMs,
    };
    if (idx >= 0) {
      opsPolicies[idx] = updated;
    } else {
      opsPolicies.push(updated);
    }

    currentPolicyVersion += 1;
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        domain: 'PREVIEW',
        currentVersion: currentPolicyVersion,
        updatedAt: new Date().toISOString(),
        actor: 'admin',
        reason: payload?.reason || 'ui_update',
        updatedPolicy: updated,
        policies: opsPolicies,
      }),
    });
  });

  await page.route('**/api/v1/ops/policies/*/rollback', async (route) => {
    const payload = route.request().postDataJSON() as { targetVersion?: number; reason?: string } | null;
    policyRollbackCalls.push(Date.now());
    const previousVersion = currentPolicyVersion;
    const explicitTargetVersion = Number(payload?.targetVersion);
    const rolledBackToVersion = Number.isFinite(explicitTargetVersion) && explicitTargetVersion > 0
      ? explicitTargetVersion
      : Math.max(1, previousVersion - 1);
    currentPolicyVersion += 1;
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        domain: 'PREVIEW',
        previousVersion,
        rolledBackToVersion,
        currentVersion: currentPolicyVersion,
        updatedAt: new Date().toISOString(),
        actor: 'admin',
        reason: payload?.reason || 'ui_rollback_latest',
        policies: opsPolicies,
      }),
    });
  });

  await page.route('**/api/v1/preview/diagnostics/prevention/blocked**', async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        enabled: true,
        blockedCount: 1,
        maxBlocked: 5000,
        autoBlockCategories: ['PERMANENT', 'UNSUPPORTED'],
        limit: 100,
        items: [
          {
            documentId: blockedPreventionId,
            name: blockedPreventionName,
            path: '/Root/Documents/e2e-preview-diagnostics/prevention-blocked.bin',
            mimeType: 'application/octet-stream',
            previewStatus: 'FAILED',
            category: 'UNSUPPORTED',
            reason: 'Preview not supported for mime type application/octet-stream',
            blockedAt: new Date().toISOString(),
            lastHitAt: new Date().toISOString(),
            hitCount: 6,
          },
        ],
      }),
    });
  });

  await page.route('**/api/v1/preview/diagnostics/prevention/*/unblock-requeue**', async (route) => {
    const url = new URL(route.request().url());
    const pathnameParts = url.pathname.split('/');
    const documentId = pathnameParts[pathnameParts.length - 2];
    const force = url.searchParams.get('force') === 'true';
    preventionActionCalls.push({ id: documentId, action: 'requeue', force });
    queueCalls.push({ id: documentId, force });
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        documentId,
        unblocked: true,
        queued: true,
        message: 'Preview queued',
        previewStatus: 'FAILED',
        attempts: 0,
        nextAttemptAt: null,
      }),
    });
  });

  await page.route('**/api/v1/preview/diagnostics/prevention/*/unblock', async (route) => {
    const pathnameParts = new URL(route.request().url()).pathname.split('/');
    const documentId = pathnameParts[pathnameParts.length - 2];
    preventionActionCalls.push({ id: documentId, action: 'unblock' });
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        documentId,
        unblocked: true,
        queued: false,
        message: 'Rendition prevention marker removed',
        previewStatus: null,
        attempts: 0,
        nextAttemptAt: null,
      }),
    });
  });

  await page.route('**/api/v1/preview/diagnostics/prevention/unblock-batch', async (route) => {
    const payload = route.request().postDataJSON() as { documentIds?: string[] } | null;
    const ids = Array.isArray(payload?.documentIds) ? payload!.documentIds : [];
    preventionBatchCalls.push({ action: 'unblock', ids });
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        requested: ids.length,
        deduplicated: ids.length,
        unblocked: ids.length,
        queued: 0,
        failed: 0,
        results: ids.map((id) => ({
          documentId: id,
          unblocked: true,
          queued: false,
          message: 'Rendition prevention marker removed',
          previewStatus: null,
          attempts: 0,
          nextAttemptAt: null,
        })),
      }),
    });
  });

  await page.route('**/api/v1/preview/diagnostics/prevention/unblock-requeue-batch', async (route) => {
    const payload = route.request().postDataJSON() as { documentIds?: string[]; force?: boolean } | null;
    const ids = Array.isArray(payload?.documentIds) ? payload!.documentIds : [];
    const force = payload?.force === true;
    preventionBatchCalls.push({ action: 'requeue', ids, force });
    ids.forEach((id) => queueCalls.push({ id, force }));
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        requested: ids.length,
        deduplicated: ids.length,
        unblocked: ids.length,
        queued: ids.length,
        failed: 0,
        results: ids.map((id) => ({
          documentId: id,
          unblocked: true,
          queued: true,
          message: 'Preview queued',
          previewStatus: 'FAILED',
          attempts: 0,
          nextAttemptAt: null,
        })),
      }),
    });
  });

  await page.route('**/api/v1/preview/diagnostics/dead-letter**', async (route) => {
    const routeUrl = new URL(route.request().url());
    if (routeUrl.pathname.endsWith('/dead-letter/export')) {
      deadLetterExportCalls.push(Date.now());
      await route.fulfill({
        status: 200,
        contentType: 'text/csv; charset=UTF-8',
        headers: {
          'content-disposition': 'attachment; filename="preview_dead_letter.csv"',
          'x-preview-dead-letter-count': '1',
        },
        body: 'documentId,name\\n66666666-6666-6666-6666-666666666666,e2e-preview-dead-letter-replay.pdf\\n',
      });
      return;
    }
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        enabled: true,
        backendMode: 'REDIS',
        redisEnabled: true,
        ttlMs: 300000,
        itemCount: 1,
        maxEntries: 5000,
        limit: 100,
        items: [
          {
            documentId: deadLetterId,
            name: deadLetterName,
            path: '/Root/Documents/e2e-preview-diagnostics/dead-letter.pdf',
            mimeType: 'application/pdf',
            previewStatus: 'FAILED',
            reason: 'Timeout contacting preview service',
            category: 'TEMPORARY',
            policyKey: 'pdf',
            sourceStage: 'QUEUE_RETRY_EXHAUSTED',
            failedAt: new Date().toISOString(),
            attempts: 3,
            occurrences: 2,
          },
        ],
      }),
    });
  });

  await page.route('**/api/v1/ops/recovery/replay-batch', async (route) => {
    const payload = route.request().postDataJSON() as { documentIds?: string[]; entryKeys?: string[]; force?: boolean } | null;
    const ids = Array.isArray(payload?.documentIds) ? payload!.documentIds : [];
    const entryKeys = Array.isArray(payload?.entryKeys) ? payload!.entryKeys : [];
    const replayTargets = ids.length > 0 ? ids : entryKeys;
    const force = payload?.force !== false;
    deadLetterReplayBatchCalls.push({ ids: replayTargets, force });
    replayTargets.forEach((id) => queueCalls.push({ id, force }));

    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        domain: 'PREVIEW',
        mode: 'REPLAY_BATCH',
        windowDays: 7,
        maxDocuments: replayTargets.length || 100,
        totalCandidates: replayTargets.length,
        scanned: replayTargets.length,
        matched: replayTargets.length,
        truncated: false,
        requested: replayTargets.length,
        deduplicated: replayTargets.length,
        queued: replayTargets.length,
        skipped: 0,
        failed: 0,
        results: replayTargets.map((id) => ({
          documentId: id,
          outcome: 'QUEUED',
          message: 'Preview queued',
          previewStatus: 'FAILED',
          attempts: 0,
          nextAttemptAt: null,
        })),
      }),
    });
  });

  await page.route('**/api/v1/ops/recovery/clear-batch', async (route) => {
    const payload = route.request().postDataJSON() as { documentIds?: string[]; entryKeys?: string[] } | null;
    const documentIds = Array.isArray(payload?.documentIds) ? payload!.documentIds : [];
    const entryKeys = Array.isArray(payload?.entryKeys) ? payload!.entryKeys : [];
    const clearTargets = entryKeys.length > 0 ? entryKeys : documentIds;
    deadLetterClearBatchCalls.push({ ids: clearTargets });

    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        domain: 'PREVIEW',
        mode: 'CLEAR_BATCH',
        windowDays: 0,
        maxDocuments: clearTargets.length || 100,
        totalCandidates: clearTargets.length,
        scanned: clearTargets.length,
        matched: clearTargets.length,
        truncated: false,
        requested: clearTargets.length,
        deduplicated: clearTargets.length,
        queued: clearTargets.length,
        skipped: 0,
        failed: 0,
        results: clearTargets.map((id) => ({
          documentId: id,
          jobState: 'CLEARED',
          outcome: 'CLEARED',
          message: 'Dead-letter entry cleared',
          previewStatus: 'UNSUPPORTED',
          failureCategory: 'UNSUPPORTED',
          attempts: 0,
          nextAttemptAt: null,
        })),
      }),
    });
  });

  await page.route('**/api/v1/ops/recovery/clear-by-filter', async (route) => {
    const payload = route.request().postDataJSON() as {
      reason?: string;
      category?: string;
      retryable?: boolean;
      days?: number;
      maxDocuments?: number;
    } | null;
    const reason = payload?.reason || '';
    const category = payload?.category || 'ANY';
    const retryable = Boolean(payload?.retryable);
    const days = Number(payload?.days || 7);
    const maxDocuments = Number(payload?.maxDocuments || 100);
    deadLetterClearByFilterCalls.push({
      reason,
      category,
      retryable,
      days,
      maxDocuments,
    });

    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        domain: 'PREVIEW',
        mode: 'CLEAR_BY_FILTER',
        windowDays: days,
        maxDocuments,
        totalCandidates: 1,
        scanned: 2,
        matched: 1,
        truncated: false,
        requested: 1,
        deduplicated: 1,
        queued: 1,
        skipped: 0,
        failed: 0,
        results: [
          {
            documentId: deadLetterId,
            jobState: 'CLEARED',
            outcome: 'CLEARED',
            message: 'Dead-letter entry cleared',
            previewStatus: 'FAILED',
            failureCategory: 'TEMPORARY',
            attempts: 0,
            nextAttemptAt: null,
          },
        ],
      }),
    });
  });

  await page.route('**/api/v1/ops/recovery/replay-by-filter', async (route) => {
    const payload = route.request().postDataJSON() as {
      reason?: string;
      category?: string;
      retryable?: boolean;
      days?: number;
      maxDocuments?: number;
      force?: boolean;
    } | null;
    const reason = payload?.reason || '';
    const category = payload?.category || 'ANY';
    const retryable = Boolean(payload?.retryable);
    const days = Number(payload?.days || 7);
    const maxDocuments = Number(payload?.maxDocuments || 100);
    const force = payload?.force !== false;
    deadLetterReplayByFilterCalls.push({
      reason,
      category,
      retryable,
      days,
      maxDocuments,
      force,
    });
    if (force) {
      queueCalls.push({ id: deadLetterId, force });
    }

    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        domain: 'PREVIEW',
        mode: 'REPLAY_BY_FILTER',
        windowDays: days,
        maxDocuments,
        totalCandidates: 1,
        scanned: 2,
        matched: 1,
        truncated: false,
        requested: 1,
        deduplicated: 1,
        queued: 1,
        skipped: 0,
        failed: 0,
        results: [
          {
            documentId: deadLetterId,
            jobState: 'QUEUED',
            outcome: 'QUEUED',
            message: 'Preview queued',
            previewStatus: 'FAILED',
            failureCategory: 'TEMPORARY',
            attempts: 0,
            nextAttemptAt: null,
          },
        ],
      }),
    });
  });

  await page.route('**/api/v1/ops/recovery/history**', async (route) => {
    const requestUrl = new URL(route.request().url());
    const method = route.request().method().toUpperCase();
    const days = requestUrl.searchParams.get('days') || '7';
    const mode = (requestUrl.searchParams.get('mode') || '').toUpperCase();
    const actor = requestUrl.searchParams.get('actor') || '';
    const eventType = (requestUrl.searchParams.get('eventType') || '').toUpperCase();
    const breakdownLimit = Number(requestUrl.searchParams.get('limit') || '10');
    const breakdownSort = (requestUrl.searchParams.get('sort') || 'DELTA_ABS_DESC').toUpperCase();
    const limit = Number(requestUrl.searchParams.get('limit') || '20');
    const pageIndex = Math.max(0, Number(requestUrl.searchParams.get('page') || '0'));

    const asyncPrefix = '/api/v1/ops/recovery/history/export-async';
    if (requestUrl.pathname.startsWith(asyncPrefix)) {
      const suffix = requestUrl.pathname.slice(asyncPrefix.length);
      const parts = suffix.split('/').filter(Boolean);
      const exportTypeFilter = (requestUrl.searchParams.get('exportType') || '').toUpperCase();
      const statusFilter = (requestUrl.searchParams.get('status') || '').toUpperCase();
      const listStatusFilter = (requestUrl.searchParams.get('status') || '').toUpperCase();
      const cleanupStatusFilter = (requestUrl.searchParams.get('status') || '').toUpperCase();
      const getRetryableTerminalRecoveryHistoryTasks = (
        targetExportTypeFilter: string,
        targetStatusFilter: string,
        targetLimit: number
      ) => {
        const allowedStatuses = targetStatusFilter
          ? new Set([targetStatusFilter])
          : new Set(['FAILED', 'CANCELLED']);
        return Array.from(recoveryHistoryExportAsyncTasks.values())
          .filter((task) => {
            if (targetExportTypeFilter && (task.exportType || '').toUpperCase() !== targetExportTypeFilter) {
              return false;
            }
            return allowedStatuses.has((task.status || '').toUpperCase());
          })
          .sort((left, right) => (right.createdAt || '').localeCompare(left.createdAt || ''))
          .slice(0, targetLimit);
      };

      if (parts.length === 1 && parts[0] === 'summary' && method === 'GET') {
        recoveryHistoryExportAsyncSummaryCalls.push({
          exportType: exportTypeFilter || 'ALL',
          status: statusFilter || 'ALL',
        });
        const filtered = Array.from(recoveryHistoryExportAsyncTasks.values()).filter((task) => {
          if (!exportTypeFilter) {
            if (!statusFilter) {
              return true;
            }
            return (task.status || '').toUpperCase() === statusFilter;
          }
          const typeMatched = (task.exportType || '').toUpperCase() === exportTypeFilter;
          if (!typeMatched) {
            return false;
          }
          if (!statusFilter) {
            return true;
          }
          return (task.status || '').toUpperCase() === statusFilter;
        });
        const queuedCount = filtered.filter((task) => task.status === 'QUEUED').length;
        const runningCount = filtered.filter((task) => task.status === 'RUNNING').length;
        const completedCount = filtered.filter((task) => task.status === 'COMPLETED').length;
        const cancelledCount = filtered.filter((task) => task.status === 'CANCELLED').length;
        const failedCount = filtered.filter((task) => task.status === 'FAILED').length;
        await route.fulfill({
          status: 200,
          contentType: 'application/json',
          body: JSON.stringify({
            totalCount: filtered.length,
            queuedCount,
            runningCount,
            completedCount,
            cancelledCount,
            failedCount,
            activeCount: queuedCount + runningCount,
            terminalCount: completedCount + cancelledCount + failedCount,
          }),
        });
        return;
      }

      if (parts.length === 1 && parts[0] === 'cleanup' && method === 'POST') {
        recoveryHistoryExportAsyncCleanupCalls.push({
          exportType: exportTypeFilter || 'ALL',
          status: cleanupStatusFilter || 'ALL',
        });
        const terminalStatuses = new Set(['COMPLETED', 'CANCELLED', 'FAILED']);
        let deletedCount = 0;
        for (const [taskId, task] of recoveryHistoryExportAsyncTasks.entries()) {
          const taskType = (task.exportType || '').toUpperCase();
          const taskStatus = (task.status || '').toUpperCase();
          if (exportTypeFilter && taskType !== exportTypeFilter) {
            continue;
          }
          if (cleanupStatusFilter) {
            if (taskStatus !== cleanupStatusFilter) {
              continue;
            }
          } else if (!terminalStatuses.has(taskStatus)) {
            continue;
          }
          recoveryHistoryExportAsyncTasks.delete(taskId);
          deletedCount += 1;
        }
        await route.fulfill({
          status: 200,
          contentType: 'application/json',
          body: JSON.stringify({
            deletedCount,
            remainingCount: recoveryHistoryExportAsyncTasks.size,
            exportTypeFilter: exportTypeFilter || null,
            statusFilter: cleanupStatusFilter || null,
            message: deletedCount > 0
              ? `Ops recovery async export cleanup removed ${deletedCount} task(s)`
              : 'No ops recovery async export tasks matched cleanup filters',
          }),
        });
        return;
      }

      if (parts.length === 0 && method === 'POST') {
        const payload = route.request().postDataJSON() as {
          exportType?: string;
          limit?: number;
          days?: number;
          mode?: string;
          actor?: string;
          eventType?: string;
          compareBreakdownLimit?: number;
          compareBreakdownSort?: string;
          compareActorLimit?: number;
          compareActorSort?: string;
        } | null;
        const exportType = (payload?.exportType || 'HISTORY').toUpperCase();
        const requestDays = Number(payload?.days || 7);
        const requestLimit = Number(payload?.limit || 500);
        const requestMode = (payload?.mode || '').toUpperCase() || 'ALL';
        const requestActor = payload?.actor || '';
        const requestEventType = (payload?.eventType || '').toUpperCase() || 'ALL';
        const requestCompareBreakdownLimit = Number(payload?.compareBreakdownLimit || 10);
        const requestCompareBreakdownSort = (payload?.compareBreakdownSort || 'DELTA_ABS_DESC').toUpperCase();
        const requestCompareActorLimit = Number(payload?.compareActorLimit || 10);
        const requestCompareActorSort = (payload?.compareActorSort || 'DELTA_ABS_DESC').toUpperCase();
        recoveryHistoryExportAsyncStartCalls.push({
          exportType,
          days: requestDays,
          mode: requestMode,
          actor: requestActor,
          eventType: requestEventType,
          limit: requestLimit,
          compareBreakdownLimit: requestCompareBreakdownLimit,
          compareBreakdownSort: requestCompareBreakdownSort,
          compareActorLimit: requestCompareActorLimit,
          compareActorSort: requestCompareActorSort,
        });
        const startedTask = {
          taskId: recoveryHistoryExportAsyncStartedTaskId,
          exportType,
          status: 'QUEUED',
          error: null,
          createdAt: new Date().toISOString(),
          finishedAt: null,
          filename: null,
        };
        recoveryHistoryExportAsyncTasks.set(startedTask.taskId, startedTask);
        await route.fulfill({
          status: 200,
          contentType: 'application/json',
          body: JSON.stringify({
            taskId: startedTask.taskId,
            exportType: startedTask.exportType,
            status: startedTask.status,
            createdAt: startedTask.createdAt,
          }),
        });
        return;
      }

      if (parts.length === 0 && method === 'GET') {
        const rawMaxItems = Number(
          requestUrl.searchParams.get('maxItems')
          || requestUrl.searchParams.get('limit')
          || '20'
        );
        const listLimit = Number.isFinite(rawMaxItems) && rawMaxItems > 0 ? Math.floor(rawMaxItems) : 20;
        const rawSkipCount = Number(requestUrl.searchParams.get('skipCount') || '0');
        const skipCount = Number.isFinite(rawSkipCount) && rawSkipCount > 0 ? Math.floor(rawSkipCount) : 0;
        recoveryHistoryExportAsyncListCalls.push({
          limit: listLimit,
          skipCount,
          exportType: exportTypeFilter || 'ALL',
          status: listStatusFilter || 'ALL',
        });
        const filteredItems = Array.from(recoveryHistoryExportAsyncTasks.values())
          .filter((task) => {
            if (!exportTypeFilter) {
              if (!listStatusFilter) {
                return true;
              }
              return (task.status || '').toUpperCase() === listStatusFilter;
            }
            const typeMatched = (task.exportType || '').toUpperCase() === exportTypeFilter;
            if (!typeMatched) {
              return false;
            }
            if (!listStatusFilter) {
              return true;
            }
            return (task.status || '').toUpperCase() === listStatusFilter;
          })
          .sort((left, right) => (right.createdAt || '').localeCompare(left.createdAt || ''));
        const items = filteredItems.slice(skipCount, skipCount + Math.max(1, listLimit));
        const totalItems = filteredItems.length;
        await route.fulfill({
          status: 200,
          contentType: 'application/json',
          body: JSON.stringify({
            count: items.length,
            paging: {
              skipCount,
              maxItems: listLimit,
              totalItems,
              hasMoreItems: skipCount + items.length < totalItems,
            },
            items,
          }),
        });
        return;
      }

      if (parts.length === 2 && parts[0] === 'retry-terminal' && parts[1] === 'dry-run' && method === 'POST') {
        const rawLimit = Number(requestUrl.searchParams.get('limit') || '20');
        const retryLimit = Number.isFinite(rawLimit) && rawLimit > 0 ? Math.floor(rawLimit) : 20;
        recoveryHistoryExportAsyncRetryTerminalDryRunCalls.push({
          exportType: exportTypeFilter || 'ALL',
          status: statusFilter || 'ALL',
          limit: retryLimit,
        });
        if (statusFilter && statusFilter !== 'COMPLETED' && statusFilter !== 'CANCELLED' && statusFilter !== 'FAILED') {
          await route.fulfill({
            status: 400,
            contentType: 'application/json',
            body: JSON.stringify({
              message: `Unsupported retry-terminal dry-run status filter: ${statusFilter}`,
            }),
          });
          return;
        }
        const sourceTasks = getRetryableTerminalRecoveryHistoryTasks(exportTypeFilter, statusFilter, retryLimit);
        const results = sourceTasks.map((task) => ({
          sourceTaskId: task.taskId,
          exportType: task.exportType,
          sourceStatus: task.status,
          outcome: 'RETRYABLE',
          reasonCode: 'TERMINAL_TASK_RETRYABLE',
          message: 'Terminal ops recovery async export task can be retried',
        }));
        await route.fulfill({
          status: 200,
          contentType: 'application/json',
          body: JSON.stringify({
            requested: sourceTasks.length,
            retryable: results.length,
            skipped: 0,
            limit: retryLimit,
            exportTypeFilter: exportTypeFilter || null,
            statusFilter: statusFilter || null,
            message: results.length > 0
              ? `Dry-run identified ${results.length}/${sourceTasks.length} retryable terminal ops recovery async export tasks (skipped=0)`
              : 'No terminal ops recovery async export tasks matched dry-run filters',
            results,
            reasonBreakdown: results.length > 0
              ? [{ reasonCode: 'TERMINAL_TASK_RETRYABLE', outcome: 'RETRYABLE', count: results.length }]
              : [],
          }),
        });
        return;
      }

      if (parts.length === 3 && parts[0] === 'retry-terminal' && parts[1] === 'dry-run' && parts[2] === 'export' && method === 'GET') {
        const rawLimit = Number(requestUrl.searchParams.get('limit') || '20');
        const retryLimit = Number.isFinite(rawLimit) && rawLimit > 0 ? Math.floor(rawLimit) : 20;
        recoveryHistoryExportAsyncRetryTerminalDryRunExportCalls.push({
          exportType: exportTypeFilter || 'ALL',
          status: statusFilter || 'ALL',
          limit: retryLimit,
        });
        if (statusFilter && statusFilter !== 'COMPLETED' && statusFilter !== 'CANCELLED' && statusFilter !== 'FAILED') {
          await route.fulfill({
            status: 400,
            contentType: 'application/json',
            body: JSON.stringify({
              message: `Unsupported retry-terminal dry-run export status filter: ${statusFilter}`,
            }),
          });
          return;
        }
        const sourceTasks = getRetryableTerminalRecoveryHistoryTasks(exportTypeFilter, statusFilter, retryLimit);
        const retryableCount = sourceTasks.length;
        const rows = sourceTasks.map((task) => (
          `${exportTypeFilter || 'ALL'},${statusFilter || 'FAILED|CANCELLED'},${retryLimit},${sourceTasks.length},${retryableCount},0,${task.taskId},${task.exportType},${task.status},RETRYABLE,TERMINAL_TASK_RETRYABLE,Terminal ops recovery async export task can be retried`
        )).join('\n');
        const payload = [
          'exportTypeFilter,statusFilter,limit,requested,retryable,skipped,sourceTaskId,exportType,sourceStatus,outcome,reasonCode,message',
          rows || `${exportTypeFilter || 'ALL'},${statusFilter || 'FAILED|CANCELLED'},${retryLimit},0,0,0,,,,,,`,
          '',
          'reasonCode,outcome,count',
          retryableCount > 0 ? `TERMINAL_TASK_RETRYABLE,RETRYABLE,${retryableCount}` : 'NONE,UNKNOWN,0',
          '',
        ].join('\n');
        await route.fulfill({
          status: 200,
          contentType: 'text/csv; charset=UTF-8',
          headers: {
            'content-disposition': 'attachment; filename="ops_recovery_history_async_retry_dry_run.csv"',
            'x-ops-recovery-async-retry-dry-run-count': String(sourceTasks.length),
          },
          body: payload,
        });
        return;
      }

      if (parts.length === 2 && parts[0] === 'retry-terminal' && parts[1] === 'by-task-ids' && method === 'POST') {
        const requestBody = route.request().postDataJSON() as { sourceTaskIds?: string[] } | null;
        const sourceTaskIds = Array.from(new Set((requestBody?.sourceTaskIds || [])
          .map((taskId) => String(taskId || '').trim())
          .filter((taskId) => taskId.length > 0)));
        recoveryHistoryExportAsyncRetrySelectedCalls.push({
          exportType: exportTypeFilter || 'ALL',
          sourceTaskIds,
        });
        const results = sourceTaskIds.map((sourceTaskId) => {
          const sourceTask = recoveryHistoryExportAsyncTasks.get(sourceTaskId);
          if (!sourceTask) {
            return {
              sourceTaskId,
              exportType: exportTypeFilter || null,
              sourceStatus: 'NOT_FOUND',
              retriedTaskId: null,
              outcome: 'SKIPPED',
              message: 'Source task not found',
            };
          }
          if (exportTypeFilter && (sourceTask.exportType || '').toUpperCase() !== exportTypeFilter) {
            return {
              sourceTaskId,
              exportType: sourceTask.exportType,
              sourceStatus: sourceTask.status,
              retriedTaskId: null,
              outcome: 'SKIPPED',
              message: 'Source task does not match export type filter',
            };
          }
          const taskStatus = (sourceTask.status || '').toUpperCase();
          if (taskStatus !== 'FAILED' && taskStatus !== 'CANCELLED' && taskStatus !== 'COMPLETED') {
            return {
              sourceTaskId,
              exportType: sourceTask.exportType,
              sourceStatus: sourceTask.status,
              retriedTaskId: null,
              outcome: 'SKIPPED',
              message: 'Source task is not terminal',
            };
          }
          const sequence = recoveryHistoryExportAsyncRetrySelectedSequence;
          recoveryHistoryExportAsyncRetrySelectedSequence += 1;
          const retriedTaskId = sequence === 0
            ? recoveryHistoryExportAsyncRetriedTaskId
            : `ops-recovery-export-task-retried-${String(sequence + 1).padStart(4, '0')}`;
          const retriedTask = {
            ...sourceTask,
            taskId: retriedTaskId,
            status: 'QUEUED',
            error: null,
            createdAt: new Date().toISOString(),
            finishedAt: null,
            filename: `ops_recovery_history_async_${retriedTaskId}.csv`,
            message: `Retried selected from ${sourceTaskId}`,
            retrySourceTaskId: sourceTaskId,
          };
          recoveryHistoryExportAsyncTasks.set(retriedTaskId, retriedTask);
          return {
            sourceTaskId,
            exportType: sourceTask.exportType,
            sourceStatus: sourceTask.status,
            retriedTaskId,
            outcome: 'RETRIED',
            message: 'Retried selected terminal ops recovery async export task',
          };
        });
        const retried = results.filter((item) => item.outcome === 'RETRIED').length;
        const skipped = results.filter((item) => item.outcome === 'SKIPPED').length;
        await route.fulfill({
          status: 202,
          contentType: 'application/json',
          headers: {
            location: '/api/v1/ops/recovery/history/export-async',
          },
          body: JSON.stringify({
            requested: sourceTaskIds.length,
            retried,
            reused: 0,
            skipped,
            failed: 0,
            limit: sourceTaskIds.length,
            exportTypeFilter: exportTypeFilter || null,
            statusFilter: 'BY_TASK_IDS',
            message: sourceTaskIds.length > 0
              ? `Retried ${retried}/${sourceTaskIds.length} selected terminal ops recovery async export tasks (reused=0, skipped=${skipped}, failed=0)`
              : 'No source task ids provided for terminal ops recovery async export retry',
            results,
          }),
        });
        return;
      }

      if (parts.length >= 1) {
        const taskId = decodeURIComponent(parts[0]);
        const task = recoveryHistoryExportAsyncTasks.get(taskId);
        if (!task) {
          await route.fulfill({
            status: 404,
            contentType: 'application/json',
            body: JSON.stringify({ message: `Task not found: ${taskId}` }),
          });
          return;
        }

        if (parts.length === 1 && method === 'GET') {
          recoveryHistoryExportAsyncGetCalls.push(taskId);
          await route.fulfill({
            status: 200,
            contentType: 'application/json',
            body: JSON.stringify(task),
          });
          return;
        }

        if (parts.length === 2 && parts[1] === 'cancel' && method === 'POST') {
          recoveryHistoryExportAsyncCancelCalls.push(taskId);
          const cancelled = {
            ...task,
            status: 'CANCELLED',
            error: 'Cancelled by user',
            finishedAt: new Date().toISOString(),
          };
          recoveryHistoryExportAsyncTasks.set(taskId, cancelled);
          await route.fulfill({
            status: 200,
            contentType: 'application/json',
            body: JSON.stringify(cancelled),
          });
          return;
        }

        if (parts.length === 2 && parts[1] === 'download' && method === 'GET') {
          recoveryHistoryExportAsyncDownloadCalls.push(taskId);
          await route.fulfill({
            status: 200,
            contentType: 'text/csv; charset=UTF-8',
            headers: {
              'content-disposition': `attachment; filename="${task.filename || 'ops_recovery_history_async.csv'}"`,
            },
            body: 'id,eventType,mode\nbbbb2222-bbbb-2222-bbbb-222222222222,OPS_RECOVERY_DRY_RUN,DRY_RUN\n',
          });
          return;
        }
      }
    }

    if (requestUrl.pathname.endsWith('/history/summary/compare/export')) {
      recoveryHistoryCompareExportCalls.push({ days, mode: mode || 'ALL', actor, eventType: eventType || 'ALL' });
      await route.fulfill({
        status: 200,
        contentType: 'text/csv; charset=UTF-8',
        headers: {
          'content-disposition': 'attachment; filename="ops_recovery_history_compare.csv"',
          'x-ops-recovery-compare-count': '1',
        },
        body: 'domain,windowDays,previousWindowDays,modeFilter,actorFilter,eventTypeFilter,currentTotal,previousTotal,delta,deltaPercent,compareAvailable,truncated\\nPREVIEW,7,7,DRY_RUN,admin,,2,1,1,100.0,true,false\\n',
      });
      return;
    }
    if (requestUrl.pathname.endsWith('/history/summary/compare/actors/export')) {
      recoveryHistoryCompareActorExportCalls.push({
        days,
        mode: mode || 'ALL',
        actor,
        eventType: eventType || 'ALL',
        limit: breakdownLimit,
        sort: breakdownSort,
      });
      await route.fulfill({
        status: 200,
        contentType: 'text/csv; charset=UTF-8',
        headers: {
          'content-disposition': 'attachment; filename="ops_recovery_history_compare_actors.csv"',
          'x-ops-recovery-compare-actors-count': '1',
        },
        body: `domain,windowDays,previousWindowDays,modeFilter,actorFilter,eventTypeFilter,compareAvailable,truncated,sortBy,requestedLimit,totalItems,limited,actor,currentCount,previousCount,delta,deltaPercent\\nPREVIEW,7,7,DRY_RUN,admin,,true,false,${breakdownSort},${breakdownLimit},1,false,admin,2,1,1,100.0\\n`,
      });
      return;
    }
    if (requestUrl.pathname.endsWith('/history/summary/compare/breakdown/export')) {
      recoveryHistoryCompareBreakdownExportCalls.push({
        days,
        mode: mode || 'ALL',
        actor,
        eventType: eventType || 'ALL',
        limit: breakdownLimit,
        sort: breakdownSort,
      });
      await route.fulfill({
        status: 200,
        contentType: 'text/csv; charset=UTF-8',
        headers: {
          'content-disposition': 'attachment; filename="ops_recovery_history_compare_breakdown.csv"',
          'x-ops-recovery-compare-breakdown-count': '1',
        },
        body: `domain,windowDays,previousWindowDays,modeFilter,actorFilter,eventTypeFilter,compareAvailable,truncated,sortBy,requestedLimit,totalItems,limited,eventType,mode,currentCount,previousCount,delta,deltaPercent\\nPREVIEW,7,7,DRY_RUN,admin,,true,false,${breakdownSort},${breakdownLimit},1,false,OPS_RECOVERY_DRY_RUN,DRY_RUN,2,1,1,100.0\\n`,
      });
      return;
    }
    if (requestUrl.pathname.endsWith('/history/summary/compare/breakdown')) {
      recoveryHistoryCompareBreakdownCalls.push({
        days,
        mode: mode || 'ALL',
        actor,
        eventType: eventType || 'ALL',
        limit: breakdownLimit,
        sort: breakdownSort,
      });
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          domain: 'PREVIEW',
          windowDays: Number(days),
          previousWindowDays: Number(days),
          modeFilter: mode || null,
          actorFilter: actor || null,
          eventTypeFilter: eventType || null,
          compareAvailable: true,
          truncated: false,
          sortBy: breakdownSort,
          requestedLimit: breakdownLimit,
          totalItems: 1,
          limited: false,
          items: [
            {
              eventType: 'OPS_RECOVERY_DRY_RUN',
              mode: 'DRY_RUN',
              currentCount: 2,
              previousCount: 1,
              delta: 1,
              deltaPercent: 100.0,
            },
          ],
        }),
      });
      return;
    }
    if (requestUrl.pathname.endsWith('/history/summary/compare/actors')) {
      recoveryHistoryCompareActorCalls.push({
        days,
        mode: mode || 'ALL',
        actor,
        eventType: eventType || 'ALL',
        limit: breakdownLimit,
        sort: breakdownSort,
      });
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          domain: 'PREVIEW',
          windowDays: Number(days),
          previousWindowDays: Number(days),
          modeFilter: mode || null,
          actorFilter: actor || null,
          eventTypeFilter: eventType || null,
          compareAvailable: true,
          truncated: false,
          sortBy: breakdownSort,
          requestedLimit: breakdownLimit,
          totalItems: 1,
          limited: false,
          items: [
            {
              actor: 'admin',
              currentCount: 2,
              previousCount: 1,
              delta: 1,
              deltaPercent: 100.0,
            },
          ],
        }),
      });
      return;
    }
    if (requestUrl.pathname.endsWith('/history/summary/compare')) {
      recoveryHistoryCompareCalls.push({ days, mode: mode || 'ALL', actor, eventType: eventType || 'ALL' });
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          domain: 'PREVIEW',
          windowDays: Number(days),
          previousWindowDays: Number(days),
          modeFilter: mode || null,
          actorFilter: actor || null,
          eventTypeFilter: eventType || null,
          currentTotal: 2,
          previousTotal: 1,
          delta: 1,
          deltaPercent: 100.0,
          compareAvailable: true,
          truncated: false,
        }),
      });
      return;
    }
    if (requestUrl.pathname.endsWith('/history/summary/trend/export')) {
      recoveryHistoryTrendExportCalls.push({ days, mode: mode || 'ALL', actor, eventType: eventType || 'ALL' });
      await route.fulfill({
        status: 200,
        contentType: 'text/csv; charset=UTF-8',
        headers: {
          'content-disposition': 'attachment; filename="ops_recovery_history_trend.csv"',
          'x-ops-recovery-trend-count': '1',
        },
        body: 'day,count\\n2026-03-07,2\\n',
      });
      return;
    }
    if (requestUrl.pathname.endsWith('/history/summary/trend')) {
      recoveryHistoryTrendCalls.push({ days, mode: mode || 'ALL', actor, eventType: eventType || 'ALL' });
      const today = new Date().toISOString().slice(0, 10);
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          domain: 'PREVIEW',
          windowDays: Number(days),
          modeFilter: mode || null,
          actorFilter: actor || null,
          eventTypeFilter: eventType || null,
          total: 2,
          truncated: false,
          items: [
            { day: today, count: 2 },
          ],
        }),
      });
      return;
    }
    if (requestUrl.pathname.endsWith('/history/summary/export')) {
      recoveryHistorySummaryExportCalls.push({ days, mode: mode || 'ALL', actor, eventType: eventType || 'ALL' });
      await route.fulfill({
        status: 200,
        contentType: 'text/csv; charset=UTF-8',
        headers: {
          'content-disposition': 'attachment; filename="ops_recovery_history_summary.csv"',
          'x-ops-recovery-summary-count': '2',
        },
        body: 'section,key,mode,count\\nEVENT_TYPE,OPS_RECOVERY_DRY_RUN,DRY_RUN,2\\nACTOR,admin,,2\\n',
      });
      return;
    }
    if (requestUrl.pathname.endsWith('/history/summary')) {
      recoveryHistorySummaryCalls.push({ days, mode: mode || 'ALL', actor, eventType: eventType || 'ALL' });
      const allItems = [
        {
          eventType: 'OPS_RECOVERY_QUEUE_BY_REASON',
          mode: 'QUEUE_BY_REASON',
          actor: 'admin',
          count: 1,
        },
        {
          eventType: 'OPS_RECOVERY_DRY_RUN',
          mode: 'DRY_RUN',
          actor: 'admin',
          count: 2,
        },
      ];
      const filteredByMode = mode ? allItems.filter((item) => item.mode === mode) : allItems;
      const filteredByActor = actor ? filteredByMode.filter((item) => (item.actor || '') === actor) : filteredByMode;
      const filteredItems = eventType ? filteredByActor.filter((item) => item.eventType === eventType) : filteredByActor;
      const total = filteredItems.reduce((sum, item) => sum + item.count, 0);
      const actorCounter = new Map<string, number>();
      filteredItems.forEach((item) => {
        const key = item.actor || 'unknown';
        actorCounter.set(key, (actorCounter.get(key) || 0) + item.count);
      });
      const actorItems = Array.from(actorCounter.entries())
        .map(([name, count]) => ({ actor: name, count }))
        .sort((a, b) => b.count - a.count);
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          domain: 'PREVIEW',
          windowDays: Number(days),
          modeFilter: mode || null,
          actorFilter: actor || null,
          eventTypeFilter: eventType || null,
          total,
          items: filteredItems.map((item) => ({
            eventType: item.eventType,
            mode: item.mode,
            count: item.count,
          })),
          actorItems,
        }),
      });
      return;
    }
    if (requestUrl.pathname.endsWith('/history/export')) {
      recoveryHistoryExportCalls.push({ days, mode: mode || 'ALL', limit, actor, eventType: eventType || 'ALL' });
      await route.fulfill({
        status: 200,
        contentType: 'text/csv; charset=UTF-8',
        headers: {
          'content-disposition': 'attachment; filename="ops_recovery_history.csv"',
          'x-ops-recovery-count': '2',
        },
        body: 'id,eventType,mode\\naaaa1111-aaaa-1111-aaaa-111111111111,OPS_RECOVERY_QUEUE_BY_REASON,QUEUE_BY_REASON\\n',
      });
      return;
    }
    recoveryHistoryCalls.push({ days, mode: mode || 'ALL', page: pageIndex, limit, actor, eventType: eventType || 'ALL' });
    const allItems = [
      {
        id: 'aaaa1111-aaaa-1111-aaaa-111111111111',
        eventType: 'OPS_RECOVERY_QUEUE_BY_REASON',
        mode: 'QUEUE_BY_REASON',
        actor: 'admin',
        eventTime: new Date(Date.now() - 15_000).toISOString(),
        details: 'domain=PREVIEW mode=QUEUE_BY_REASON force=false reason=Timeout contacting preview service category=TEMPORARY retryable=true days=7 maxDocuments=100 requested=1 deduplicated=1 queued=1 skipped=0 failed=0',
      },
      {
        id: 'bbbb2222-bbbb-2222-bbbb-222222222222',
        eventType: 'OPS_RECOVERY_DRY_RUN',
        mode: 'DRY_RUN',
        actor: 'admin',
        eventTime: new Date(Date.now() - 8_000).toISOString(),
        details: 'domain=PREVIEW mode=QUEUE_BY_WINDOW force=false reason=null category=ANY retryable=null days=7 maxDocuments=100 totalCandidates=1 scanned=1 matched=1 truncated=false estimatedQueued=1 estimatedSkipped=0 estimatedFailed=0',
      },
    ];
    const filteredByMode = mode ? allItems.filter((item) => item.mode === mode) : allItems;
    const filteredByActor = actor ? filteredByMode.filter((item) => (item.actor || '') === actor) : filteredByMode;
    const filteredItems = eventType ? filteredByActor.filter((item) => item.eventType === eventType) : filteredByActor;
    const pagedItems = filteredItems.slice(pageIndex * limit, pageIndex * limit + limit);
    const totalPages = filteredItems.length === 0 ? 0 : Math.ceil(filteredItems.length / Math.max(limit, 1));
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        domain: 'PREVIEW',
        windowDays: Number(days),
        limit,
        page: pageIndex,
        totalPages,
        modeFilter: mode || null,
        actorFilter: actor || null,
        eventTypeFilter: eventType || null,
        total: filteredItems.length,
        items: pagedItems,
      }),
    });
  });

  await page.route('**/api/v1/search/faceted', async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        results: { content: [], totalElements: 0, totalPages: 0 },
        facets: { mimeType: [], createdBy: [], tags: [], categories: [], previewStatus: [] },
      }),
    });
  });

  await page.route('**/api/v1/documents/*/preview/queue**', async (route) => {
    const requestUrl = new URL(route.request().url());
    const pathnameParts = requestUrl.pathname.split('/');
    const documentId = pathnameParts[pathnameParts.length - 3];
    const force = requestUrl.searchParams.get('force') === 'true';
    queueCalls.push({ id: documentId, force });
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({ queued: true, previewStatus: 'QUEUED', documentId }),
    });
  });

  await page.route('**/api/v1/ops/recovery/queue-by-reason', async (route) => {
    const payload = route.request().postDataJSON() as {
      domain?: string;
      reason?: string;
      category?: string;
      retryable?: boolean;
      days?: number;
      maxDocuments?: number;
      force?: boolean;
    } | null;
    const reason = payload?.reason || '';
    const category = payload?.category || '';
    const retryable = payload?.retryable === true;
    const days = Number(payload?.days || 7);
    const maxDocuments = Number(payload?.maxDocuments || 100);
    const force = payload?.force === true;

    reasonBatchCalls.push({
      reason,
      category,
      retryable,
      days,
      maxDocuments,
      force,
    });

    const matchedIds = days === 30
      ? [retryableId, retryableTwinId]
      : [retryableId];
    matchedIds.forEach((id) => queueCalls.push({ id, force }));

    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        domain: payload?.domain || 'PREVIEW',
        mode: 'QUEUE_BY_REASON',
        windowDays: days,
        maxDocuments,
        totalCandidates: matchedIds.length,
        scanned: matchedIds.length,
        matched: matchedIds.length,
        truncated: false,
        requested: matchedIds.length,
        deduplicated: matchedIds.length,
        queued: matchedIds.length,
        skipped: 0,
        failed: 0,
        results: matchedIds.map((id) => ({
          documentId: id,
          outcome: 'QUEUED',
          message: 'Preview queued',
          previewStatus: 'FAILED',
          attempts: 0,
          nextAttemptAt: null,
        })),
      }),
    });
  });

  await page.route('**/api/v1/ops/recovery/queue-by-window', async (route) => {
    const payload = route.request().postDataJSON() as {
      domain?: string;
      reason?: string;
      category?: string;
      retryable?: boolean;
      days?: number;
      maxDocuments?: number;
      force?: boolean;
    } | null;
    const reason = payload?.reason || '';
    const category = payload?.category || '';
    const retryable = payload?.retryable === true;
    const days = Number(payload?.days || 7);
    const maxDocuments = Number(payload?.maxDocuments || 100);
    const force = payload?.force === true;

    queueWindowCalls.push({
      reason,
      category,
      retryable,
      days,
      maxDocuments,
      force,
    });

    const matchedIds = days === 30
      ? [retryableId, retryableTwinId]
      : [retryableId];
    matchedIds.forEach((id) => queueCalls.push({ id, force }));

    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        domain: payload?.domain || 'PREVIEW',
        mode: 'QUEUE_BY_WINDOW',
        windowDays: days,
        maxDocuments,
        totalCandidates: matchedIds.length,
        scanned: matchedIds.length,
        matched: matchedIds.length,
        truncated: false,
        requested: matchedIds.length,
        deduplicated: matchedIds.length,
        queued: matchedIds.length,
        skipped: 0,
        failed: 0,
        results: matchedIds.map((id) => ({
          documentId: id,
          outcome: 'QUEUED',
          message: 'Preview queued',
          previewStatus: 'FAILED',
          attempts: 0,
          nextAttemptAt: null,
        })),
      }),
    });
  });

  await page.route('**/api/v1/ops/recovery/dry-run', async (route) => {
    const payload = route.request().postDataJSON() as {
      mode?: string;
      reason?: string;
      category?: string;
      retryable?: boolean;
      days?: number;
      maxDocuments?: number;
      force?: boolean;
    } | null;
    const reason = payload?.reason || '';
    const category = payload?.category || '';
    const mode = payload?.mode || 'QUEUE_BY_WINDOW';
    const retryable = payload?.retryable === true;
    const days = Number(payload?.days || 7);
    const maxDocuments = Number(payload?.maxDocuments || 100);
    const force = payload?.force === true;
    reasonDryRunCalls.push({ mode, reason, category, retryable, days, maxDocuments, force });

    const matched = days === 30 ? 2 : 1;
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        domain: 'PREVIEW',
        mode,
        windowDays: days,
        maxDocuments,
        totalCandidates: matched,
        scanned: matched,
        matched,
        truncated: false,
        estimatedQueued: matched,
        estimatedSkipped: 0,
        estimatedFailed: 0,
        samples: [],
      }),
    });
  });

  await page.route('**/api/v1/preview/diagnostics/failures/queue-batch', async (route) => {
    const payload = route.request().postDataJSON() as { documentIds?: string[]; force?: boolean } | null;
    const documentIds = Array.isArray(payload?.documentIds) ? payload!.documentIds : [];
    const force = payload?.force === true;
    documentIds.forEach((id) => queueCalls.push({ id, force }));
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        requested: documentIds.length,
        deduplicated: documentIds.length,
        queued: documentIds.length,
        skipped: 0,
        failed: 0,
        results: documentIds.map((id) => ({
          documentId: id,
          outcome: 'QUEUED',
          message: 'Preview queued',
          previewStatus: 'FAILED',
          attempts: 0,
          nextAttemptAt: null,
        })),
      }),
    });
  });

  await page.goto('/admin/preview-diagnostics', { waitUntil: 'domcontentloaded' });

  await expect(page.getByRole('heading', { name: 'Preview Diagnostics' })).toBeVisible();
  await expect(page.getByText('Backend Failure Summary')).toBeVisible();
  await expect(page.getByText('Rendition Resource Summary')).toBeVisible();
  await expect(page.getByText('CAD Failover Diagnostics')).toBeVisible();
  await expect(page.getByText('Transform Trace Diagnostics')).toBeVisible();
  await expect(page.getByText('Failure Policy Profiles')).toBeVisible();
  await expect(page.getByText('Ops Recovery Execution History')).toBeVisible();
  await expect(page.getByText('Preview Dead Letter Queue')).toBeVisible();
  await expect(page.getByText('Rendition Prevention Registry')).toBeVisible();
  await expect(page.getByText('Tune max attempts, base delay, backoff slope, and quiet period by mime profile.')).toBeVisible();
  await expect(page.getByText('Loaded 2')).toBeVisible();
  await expect(page.getByText('pv-120')).toBeVisible();
  await expect(page.getByText('CAD preview enabled')).toBeVisible();
  await expect(page.getByText('Circuit breaker on')).toBeVisible();
  await expect(page.getByText('Threshold 3, Open 120s')).toBeVisible();
  await expect(page.getByText('Dead-letter enabled')).toBeVisible();
  await expect(page.getByText('Backend REDIS')).toBeVisible();
  await expect(page.getByText('TTL 5 minutes')).toBeVisible();
  await expect(page.getByText(deadLetterName)).toBeVisible();
  await expect(page.getByText('Blocked 1/5000')).toBeVisible();
  await expect(page.getByText(blockedPreventionName)).toBeVisible();
  await expect(page.getByText('HIGH confidence (sample complete)')).toBeVisible();
  await expect(page.getByText('Sampled 3/3')).toBeVisible();
  await expect(page.getByText('Sampled 80/80')).toBeVisible();
  await expect(page.getByText('Status READY: 53')).toBeVisible();
  await expect(page.getByText('Status FAILED: 21')).toBeVisible();
  await expect(page.getByText('Top transient image rasterization timeout')).toBeVisible();
  await expect(page.getByText('e2e-rendition-resource-7.pdf')).toBeVisible();
  await page.getByRole('button', { name: `Retry rendition resource ${renditionResourceRetryId}` }).click();
  await expect(page.getByText('Rendition resource retry queued')).toBeVisible();
  await expect(page.getByText('Embedded font extraction failed')).toBeVisible();
  await expect(page.getByText('Overflow reason should never be visible')).toHaveCount(0);
  await page.getByRole('button', { name: 'Refresh rendition resource summary' }).click();
  await expect(page.getByText('Sampled 80/80')).toBeVisible();
  await page.getByRole('button', { name: 'Export rendition resources CSV' }).click();
  await expect(page.getByText('Rendition resources CSV exported')).toBeVisible();
  await page.getByRole('button', { name: 'Start rendition resources async export' }).click();
  await expect(page.getByText(`Rendition async export task started: ${renditionResourceExportAsyncStartedTaskId}`)).toBeVisible();
  await page.getByRole('button', { name: 'Refresh rendition export tasks' }).click();
  await expect(page.getByRole('cell', { name: renditionResourceExportAsyncStartedTaskId, exact: true })).toBeVisible();
  await expect(page.getByText('Async total 2').first()).toBeVisible();
  await expect(page.getByText('Completed 1').first()).toBeVisible();
  await expect(page.getByText('Cancelled 0').first()).toBeVisible();
  await page.getByRole('button', { name: `Download rendition export task ${renditionResourceExportAsyncCompletedTaskId}` }).click();
  await expect(page.getByText(/Rendition async export downloaded:/i)).toBeVisible();
  await page.getByRole('button', { name: `Cancel rendition export task ${renditionResourceExportAsyncStartedTaskId}` }).click();
  await expect(page.getByText(`Rendition async export task cancelled: ${renditionResourceExportAsyncStartedTaskId}`)).toBeVisible();
  await page.locator('[aria-label="Rendition async task filter status"]').click();
  await page.getByRole('option', { name: 'Completed' }).click();
  await expect(page.getByText('Async total 1').first()).toBeVisible();
  await expect(page.getByText('Completed 1').first()).toBeVisible();
  await expect(page.getByRole('cell', { name: renditionResourceExportAsyncCompletedTaskId, exact: true })).toBeVisible();
  await page.getByRole('button', { name: 'Cancel active rendition export tasks' }).click();
  await expect(page.getByText(/No active rendition async export tasks matched cancel-active filters/i)).toBeVisible();
  await page.getByRole('button', { name: 'Cleanup rendition export tasks' }).click();
  await expect(page.getByText(/Rendition async export cleanup removed 1 task\(s\)/i)).toBeVisible();
  await expect(page.getByText('Async total 0').first()).toBeVisible();
  await page.locator('[aria-label="Rendition async task filter status"]').click();
  await page.getByRole('option', { name: 'Cancelled' }).click();
  await expect(page.getByText('Async total 1').first()).toBeVisible();
  await expect(page.getByRole('cell', { name: renditionResourceExportAsyncStartedTaskId, exact: true })).toBeVisible();
  await page.getByRole('button', { name: 'Dry-run retry terminal rendition export tasks' }).click();
  await expect(page.getByText(/Rendition async terminal dry-run: retryable=1, skipped=0/i)).toBeVisible();
  const renditionRetryDryRunTable = page.getByRole('table', { name: 'Rendition async retry dry-run candidates' });
  await expect(renditionRetryDryRunTable.getByText(renditionResourceExportAsyncStartedTaskId)).toBeVisible();
  await expect(renditionRetryDryRunTable.getByRole('cell', { name: 'RETRYABLE', exact: true })).toBeVisible();
  const renditionDryRunCheckbox = page.getByRole('checkbox', {
    name: `Select rendition dry-run source task ${renditionResourceExportAsyncStartedTaskId}`,
  });
  await expect(renditionDryRunCheckbox).toBeChecked();
  await page.getByRole('button', { name: 'Export rendition terminal dry-run CSV' }).click();
  await expect(page.getByText('Rendition async terminal dry-run CSV exported')).toBeVisible();
  await page.getByRole('button', { name: 'Retry selected rendition dry-run candidates' }).click();
  await expect(page.getByText(/Rendition async selected retry done: retried=1, reused=0, skipped=0, failed=0/i)).toBeVisible();
  await page.locator('[aria-label="Rendition async task filter status"]').click();
  await page.getByRole('option', { name: 'All' }).click();
  await page.getByRole('button', { name: 'Refresh rendition export tasks' }).click();
  await expect(page.getByRole('cell', { name: renditionResourceExportAsyncRetriedTaskId, exact: true })).toBeVisible();
  await expect(page.getByText('Policy version v3')).toBeVisible();
  await expect(page.getByText('Recent Policy Versions')).toBeVisible();
  await expect(page.getByText('Preview Queue Health')).toBeVisible();
  await expect(page.getByText('Scheduled 2')).toBeVisible();
  await expect(page.getByText('Cancel requested 1')).toBeVisible();
  await page.locator('[aria-label="Queue state filter"]').click();
  await page.getByRole('option', { name: 'Running' }).click();
  await page.getByLabel('Queue query').fill('retryable');
  await page.getByRole('button', { name: 'Apply filters' }).click();
  await expect(page.getByText('Sample 1/2')).toBeVisible();
  await expect(page.getByRole('table', { name: 'Preview queue health' }).getByText(retryableName)).toBeVisible();
  await page.getByRole('button', { name: 'Cancel Filtered' }).click();
  await expect(page.getByText('Queue cancel done: 1/1')).toBeVisible();
  await page.getByRole('button', { name: 'Export Queue CSV' }).click();
  await expect(page.getByText('Queue diagnostics CSV exported')).toBeVisible();
  await expect(page.getByText('Preview Queue Declined')).toBeVisible();
  await page.locator('[aria-label="Queue declined category filter"]').click();
  await page.getByRole('option', { name: 'QUIET_PERIOD' }).click();
  await page.locator('[aria-label="Queue declined force required filter"]').click();
  await page.getByRole('option', { name: 'Force required: no' }).click();
  await page.locator('[aria-label="Queue declined window hours filter"]').click();
  await page.getByRole('option', { name: '1h' }).click();
  await page.getByLabel('Declined query').fill('retryable');
  await page.getByRole('button', { name: 'Apply declined filters' }).click();
  await expect(page.getByText('Filter window=1h')).toBeVisible();
  await expect(page.getByRole('table', { name: 'Preview queue declined' }).getByText(retryableName)).toBeVisible();
  await page.getByLabel('Force requeue').uncheck();
  await page.getByRole('button', { name: 'Dry-run Requeue' }).click();
  await expect(page.getByText('Declined dry-run: queued=0, skipped=1, failed=0')).toBeVisible();
  await expect(page.getByText(/Dry-run result: requested=1, queued=0, skipped=1, failed=0, force=false, forceRequiredFilter=NO, windowHours=1\./i)).toBeVisible();
  await expect(page.getByText('DECLINED_QUIET_PERIOD_BLOCKED: 1')).toBeVisible();
  const queueDeclinedDryRunTable = page.getByRole('table', { name: 'Queue declined requeue dry-run results' });
  await expect(queueDeclinedDryRunTable.getByText('DECLINED_QUIET_PERIOD_BLOCKED')).toBeVisible();
  await expect(queueDeclinedDryRunTable.getByText('status: SKIPPED')).toBeVisible();
  await expect(queueDeclinedDryRunTable.getByText('skip: QUIET_PERIOD_ACTIVE')).toBeVisible();
  await expect(queueDeclinedDryRunTable.getByText('route: QUIET_PERIOD_GATE')).toBeVisible();
  await expect(queueDeclinedDryRunTable.getByText('pipeline: preview-requeue')).toBeVisible();
  await page.getByRole('button', { name: 'Export Requeue Dry-run CSV' }).click();
  await expect(page.getByText('Queue declined requeue dry-run CSV exported')).toBeVisible();
  const queueDeclinedDryRunAsyncStartButton = page.getByRole('button', { name: /Start queue declined requeue dry-run async export/i });
  const runQueueDeclinedDryRunAsyncTaskCenterFlow = async () => {
    await queueDeclinedDryRunAsyncStartButton.click();
    await expect(page.getByText(new RegExp(
      `Queue declined requeue dry-run async export task started: ${queueDeclinedDryRunExportAsyncStartedTaskId}`,
      'i'
    ))).toBeVisible();
    const queueDeclinedDryRunAsyncTable = page.getByRole('table', { name: /Queue declined requeue dry-run async export tasks/i });
    await expect(queueDeclinedDryRunAsyncTable.getByRole('cell', { name: queueDeclinedDryRunExportAsyncStartedTaskId, exact: true })).toBeVisible();
    await page.getByRole('button', { name: /Refresh queue declined requeue dry-run export tasks/i }).click();
    await expect(queueDeclinedDryRunAsyncTable.getByRole('cell', { name: 'COMPLETED', exact: true })).toBeVisible();
    await page.getByRole('button', { name: `Download queue declined requeue dry-run export task ${queueDeclinedDryRunExportAsyncStartedTaskId}` }).click();
    await expect(page.getByText(/Queue declined requeue dry-run async export downloaded:/i)).toBeVisible();
    await queueDeclinedDryRunAsyncStartButton.click();
    await expect(page.getByText(new RegExp(
      `Queue declined requeue dry-run async export task started: ${queueDeclinedDryRunExportAsyncCancelledTaskId}`,
      'i'
    ))).toBeVisible();
    await page.getByRole('button', { name: `Cancel queue declined requeue dry-run export task ${queueDeclinedDryRunExportAsyncCancelledTaskId}` }).click();
    await expect(page.getByText(new RegExp(
      `Queue declined requeue dry-run async export task cancelled: ${queueDeclinedDryRunExportAsyncCancelledTaskId}`,
      'i'
    ))).toBeVisible();
    await page.locator('[aria-label="Queue declined requeue dry-run async task filter status"]').click();
    await page.getByRole('option', { name: 'Completed' }).click();
    await expect(queueDeclinedDryRunAsyncTable.getByRole('cell', { name: queueDeclinedDryRunExportAsyncStartedTaskId, exact: true })).toBeVisible();
    await expect(queueDeclinedDryRunAsyncTable.getByRole('cell', { name: queueDeclinedDryRunExportAsyncCancelledTaskId, exact: true })).toHaveCount(0);
    await page.getByRole('button', { name: /Cancel active queue declined requeue dry-run export tasks/i }).click();
    await expect(page.getByText(/No active queue declined requeue dry-run async export tasks matched cancel-active filters/i)).toBeVisible();
    await page.getByRole('button', { name: /Cleanup queue declined requeue dry-run export tasks/i }).click();
    await expect(page.getByText(/Queue declined requeue dry-run async export cleanup removed 1 task\(s\)/i)).toBeVisible();
    await page.locator('[aria-label="Queue declined requeue dry-run async task filter status"]').click();
    await page.getByRole('option', { name: 'Cancelled' }).click();
    await expect(queueDeclinedDryRunAsyncTable.getByRole('cell', { name: queueDeclinedDryRunExportAsyncCancelledTaskId, exact: true })).toBeVisible();
    await page.getByRole('button', { name: 'Dry-run retry terminal queue declined requeue dry-run export tasks' }).click();
    await expect(page.getByText(/Queue declined requeue dry-run async terminal dry-run: retryable=1, skipped=0/i)).toBeVisible();
    await page.getByRole('button', { name: 'Export queue declined requeue dry-run terminal dry-run CSV' }).click();
    await expect(page.getByText('Queue declined requeue dry-run async terminal dry-run CSV exported')).toBeVisible();
    await page.getByRole('button', { name: 'Retry selected queue declined requeue dry-run export tasks' }).click();
    await expect(page.getByText(/Queue declined requeue dry-run async selected retry done: retried=1, reused=0, skipped=0, failed=0/i)).toBeVisible();
    await page.getByRole('button', { name: 'Retry terminal queue declined requeue dry-run export tasks', exact: true }).click();
    await expect(page.getByText(/Queue declined requeue dry-run async terminal retry done: retried=1, reused=0, skipped=0, failed=0/i)).toBeVisible();
    await page.locator('[aria-label="Queue declined requeue dry-run async task filter status"]').click();
    await page.getByRole('option', { name: 'All' }).click();
    await page.getByRole('button', { name: /Refresh queue declined requeue dry-run export tasks/i }).click();
    await expect(queueDeclinedDryRunAsyncTable.getByRole('cell', { name: queueDeclinedDryRunExportAsyncRetryTerminalTaskId, exact: true })).toBeVisible();
    await page.locator('[aria-label="Queue declined requeue dry-run async task filter status"]').click();
    await page.getByRole('option', { name: 'Cancelled' }).click();
    await page.getByRole('button', { name: `Retry queue declined requeue dry-run export task ${queueDeclinedDryRunExportAsyncCancelledTaskId}` }).click();
    await expect(page.getByText(`Queue declined requeue dry-run async export task retried: ${queueDeclinedDryRunExportAsyncRetriedTaskId}`)).toBeVisible();
    await page.getByRole('button', { name: `Retry queue declined requeue dry-run export task ${queueDeclinedDryRunExportAsyncCancelledTaskId}` }).click();
    await expect(page.getByText(`Queue declined requeue dry-run async export task reused: ${queueDeclinedDryRunExportAsyncRetriedTaskId}`)).toBeVisible();
  };
  if (await queueDeclinedDryRunAsyncStartButton.count()) {
    queueDeclinedDryRunAsyncTaskCenterCovered = true;
    await runQueueDeclinedDryRunAsyncTaskCenterFlow();
  }
  await page.getByLabel('Force requeue').check();
  await page.getByRole('button', { name: 'Requeue Declined' }).click();
  await expect(page.getByText('Declined requeue done: 1/1')).toBeVisible();
  await page.getByRole('button', { name: 'Clear Declined' }).click();
  await expect(page.getByText('Declined clear done: 1/1')).toBeVisible();
  await page.getByRole('button', { name: 'Export Declined CSV' }).click();
  await expect(page.getByText('Queue declined CSV exported')).toBeVisible();
  await page.getByRole('button', { name: 'Start queue declined async export' }).click();
  await expect(page.getByText(`Queue declined async export task started: ${queueDeclinedExportAsyncStartedTaskId}`)).toBeVisible();
  const queueDeclinedAsyncTable = page.getByRole('table', { name: 'Queue declined async export tasks' });
  await expect(queueDeclinedAsyncTable.getByRole('cell', { name: queueDeclinedExportAsyncStartedTaskId, exact: true })).toBeVisible();
  await expect(queueDeclinedAsyncTable.getByRole('cell', { name: 'RUNNING', exact: true })).toBeVisible();
  await page.getByRole('button', { name: 'Start queue declined async export' }).click();
  await expect(page.getByText(`Queue declined async export task reused: ${queueDeclinedExportAsyncStartedTaskId}`)).toBeVisible();
  await page.getByRole('button', { name: 'Refresh queue declined export tasks' }).click();
  await expect(queueDeclinedAsyncTable.getByRole('cell', { name: 'COMPLETED', exact: true })).toBeVisible();
  await page.getByRole('button', { name: `Download queue declined export task ${queueDeclinedExportAsyncStartedTaskId}` }).click();
  await expect(page.getByText(/Queue declined async export downloaded:/i)).toBeVisible();
  await page.getByRole('button', { name: 'Start queue declined async export' }).click();
  await expect(page.getByText(`Queue declined async export task started: ${queueDeclinedExportAsyncCancelledTaskId}`)).toBeVisible();
  await page.getByRole('button', { name: `Cancel queue declined export task ${queueDeclinedExportAsyncCancelledTaskId}` }).click();
  await expect(page.getByText(`Queue declined async export task cancelled: ${queueDeclinedExportAsyncCancelledTaskId}`)).toBeVisible();
  await page.locator('[aria-label="Queue declined async task filter status"]').click();
  await page.getByRole('option', { name: 'Completed' }).click();
  await expect(queueDeclinedAsyncTable.getByRole('cell', { name: queueDeclinedExportAsyncStartedTaskId, exact: true })).toBeVisible();
  await expect(queueDeclinedAsyncTable.getByRole('cell', { name: queueDeclinedExportAsyncCancelledTaskId, exact: true })).toHaveCount(0);
  await page.getByRole('button', { name: 'Cancel active queue declined export tasks' }).click();
  await expect(page.getByText(/No active queue declined async export tasks matched cancel-active filters/i)).toBeVisible();
  await page.getByRole('button', { name: 'Cleanup queue declined export tasks' }).click();
  await expect(page.getByText(/Queue declined async export cleanup removed 1 task\(s\)/i)).toBeVisible();
  await page.locator('[aria-label="Queue declined async task filter status"]').click();
  await page.getByRole('option', { name: 'Cancelled' }).click();
  await expect(queueDeclinedAsyncTable.getByRole('cell', { name: queueDeclinedExportAsyncCancelledTaskId, exact: true })).toBeVisible();
  await page.getByRole('button', { name: 'Dry-run retry terminal queue declined export tasks' }).click();
  await expect(page.getByText(/Queue declined async terminal dry-run: retryable=1, skipped=0/i)).toBeVisible();
  await page.getByRole('button', { name: 'Export queue declined terminal dry-run CSV' }).click();
  await expect(page.getByText('Queue declined async terminal dry-run CSV exported')).toBeVisible();
  await page.getByRole('button', { name: 'Retry selected queue declined export tasks' }).click();
  await expect(page.getByText(/Queue declined async selected retry done: retried=1, reused=0, skipped=0, failed=0/i)).toBeVisible();
  await page.getByRole('button', { name: 'Retry terminal queue declined export tasks', exact: true }).click();
  await expect(page.getByText(/Queue declined async terminal retry done: retried=1, reused=0, skipped=0, failed=0/i)).toBeVisible();
  await page.locator('[aria-label="Queue declined async task filter status"]').click();
  await page.getByRole('option', { name: 'All' }).click();
  await page.getByRole('button', { name: 'Refresh queue declined export tasks' }).click();
  await expect(queueDeclinedAsyncTable.getByRole('cell', { name: queueDeclinedExportAsyncRetryTerminalTaskId, exact: true })).toBeVisible();
  await page.locator('[aria-label="Queue declined async task filter status"]').click();
  await page.getByRole('option', { name: 'Cancelled' }).click();
  await page.getByRole('button', { name: `Retry queue declined export task ${queueDeclinedExportAsyncCancelledTaskId}` }).click();
  await expect(page.getByText(`Queue declined async export task retried: ${queueDeclinedExportAsyncRetriedTaskId}`)).toBeVisible();
  await page.getByRole('button', { name: `Retry queue declined export task ${queueDeclinedExportAsyncCancelledTaskId}` }).click();
  await expect(page.getByText(`Queue declined async export task reused: ${queueDeclinedExportAsyncRetriedTaskId}`)).toBeVisible();
  await expect(queueDeclinedAsyncTable).toBeVisible();
  await page.getByRole('button', { name: 'Cleanup queue declined export tasks' }).click();
  await expect(page.getByText('No queue declined async export tasks yet.')).toBeVisible();
  await expect(page.getByRole('cell', { name: 'v3' })).toBeVisible();
  await expect(page.getByRole('button', { name: 'Rollback to v2' })).toBeVisible();
  await expect(page.getByText('OPS_RECOVERY_QUEUE_BY_REASON')).toBeVisible();
  await expect(page.getByText(/domain=PREVIEW mode=QUEUE_BY_REASON/i)).toBeVisible();

  await page.getByRole('button', { name: 'Run Dry-run' }).click();
  await expect(page.getByText(/Dry-run QUEUE_BY_WINDOW: matched=1, queued=1, skipped=0, failed=0/i)).toBeVisible();
  await page.getByRole('button', { name: 'Execute Recovery' }).click();
  await expect(page.getByText(/Recovery executed: queued=1, skipped=0, failed=0/i).last()).toBeVisible();
  await page.locator('[aria-label="Recovery dry-run mode"]').click();
  await page.getByRole('option', { name: 'Clear by Filter' }).click();
  await page.getByPlaceholder('Failure reason (optional)').fill('Timeout contacting preview service');
  await expect(page.getByRole('button', { name: 'Execute Recovery' })).toBeDisabled();
  await expect(page.getByText('Dry-run plan is stale. Run Dry-run again before Execute Recovery.')).toBeVisible();
  await page.getByRole('button', { name: 'Run Dry-run' }).click();
  await expect(page.getByText(/Dry-run CLEAR_BY_FILTER: matched=1, cleared=1, skipped=0, failed=0/i)).toBeVisible();
  await page.getByRole('button', { name: 'Execute Recovery' }).click();
  await expect(page.getByText(/Recovery executed: cleared=1, skipped=0, failed=0/i).last()).toBeVisible();
  await page.locator('[aria-label="Recovery dry-run mode"]').click();
  await page.getByRole('option', { name: 'Replay by Filter' }).click();
  await page.getByPlaceholder('Failure reason (optional)').fill('Timeout contacting preview service');
  await page.getByRole('button', { name: 'Run Dry-run' }).click();
  await expect(page.getByText(/Dry-run REPLAY_BY_FILTER: matched=1, replayQueued=1, skipped=0, failed=0/i)).toBeVisible();
  await page.getByRole('button', { name: 'Execute Recovery' }).click();
  await expect(page.getByText(/Recovery executed: queued=1, skipped=0, failed=0/i).last()).toBeVisible();

  await page.locator('[aria-label="Ops recovery history mode"]').click();
  await page.getByRole('option', { name: 'Dry-run' }).click();
  await expect(page.getByText('Mode DRY_RUN')).toBeVisible();
  await expect(page.getByText('OPS_RECOVERY_DRY_RUN')).toBeVisible();
  await page.locator('[aria-label="Ops recovery history event type"]').click();
  await page.getByRole('option', { name: 'Dry-run Event' }).click();
  await expect(page.getByText('Event OPS_RECOVERY_DRY_RUN')).toBeVisible();
  await expect(page.getByRole('button', { name: 'Auto Refresh Off' })).toBeVisible();
  await page.getByLabel('Actor filter').fill('admin');
  await expect(page.getByText('Actor admin', { exact: true })).toBeVisible();
  await expect(page.getByText('Summary total 2')).toBeVisible();
  await expect(page.getByText('DRY_RUN 2')).toBeVisible();
  await expect(page.getByText('Top actor admin 2')).toBeVisible();
  await expect(page.getByText('Actor Δ admin +1')).toBeVisible();
  await expect(page.getByText('Actor top 10')).toBeVisible();
  await page.locator('[aria-label="Ops recovery actor compare top"]').click();
  await page.getByRole('option', { name: '20' }).click();
  await page.locator('[aria-label="Ops recovery actor compare sort"]').click();
  await page.getByRole('option', { name: 'Delta Asc' }).click();
  await expect(page.getByText('Actor top 20')).toBeVisible();
  await expect(page.getByText('Compare current 2')).toBeVisible();
  await expect(page.getByText('Previous 1')).toBeVisible();
  await expect(page.getByText('Delta +1')).toBeVisible();
  await expect(page.getByText('Delta% +100.0%')).toBeVisible();
  await expect(page.getByText('Δ DRY_RUN +1')).toBeVisible();
  await expect(page.getByText('Top 10', { exact: true })).toBeVisible();
  await page.locator('[aria-label="Ops recovery compare breakdown top"]').click();
  await page.getByRole('option', { name: '20' }).click();
  await page.locator('[aria-label="Ops recovery compare breakdown sort"]').click();
  await page.getByRole('option', { name: 'Delta Asc' }).click();
  await expect(page.getByText('Top 20', { exact: true })).toBeVisible();
  await expect(page.getByText('Trend total 2')).toBeVisible();
  await page.getByRole('button', { name: 'Export History CSV' }).click();
  await expect(page.getByText('Ops recovery history CSV exported')).toBeVisible();
  await page.getByRole('button', { name: 'Export Summary CSV' }).click();
  await expect(page.getByText('Ops recovery history summary CSV exported')).toBeVisible();
  await page.getByRole('button', { name: 'Export Trend CSV' }).click();
  await expect(page.getByText('Ops recovery history trend CSV exported')).toBeVisible();
  await page.getByRole('button', { name: 'Export Compare CSV' }).click();
  await expect(page.getByText('Ops recovery history compare CSV exported')).toBeVisible();
  await page.getByRole('button', { name: 'Export Actor Compare CSV' }).click();
  await expect(page.getByText('Ops recovery history actor compare CSV exported')).toBeVisible();
  await page.getByRole('button', { name: 'Export Compare Breakdown CSV' }).click();
  await expect(page.getByText('Ops recovery history compare breakdown CSV exported')).toBeVisible();
  await page.locator('[aria-label="Ops recovery async export type"]').click();
  await page.getByRole('option', { name: 'Summary CSV' }).click();
  await page.getByRole('button', { name: 'Start ops recovery async export' }).click();
  await expect(page.getByText(`Ops recovery async export task started: ${recoveryHistoryExportAsyncStartedTaskId}`)).toBeVisible();
  await page.locator('[aria-label="Ops recovery async task filter type"]').click();
  await page.getByRole('option', { name: 'Summary CSV' }).click();
  await page.getByRole('button', { name: 'Refresh ops recovery async export tasks' }).click();
  await page.getByRole('button', { name: `Download ops recovery async export task ${recoveryHistoryExportAsyncCompletedTaskId}` }).click();
  await expect(page.getByText(/Ops recovery async export downloaded:/i)).toBeVisible();
  await page.getByRole('button', { name: `Cancel ops recovery async export task ${recoveryHistoryExportAsyncStartedTaskId}` }).click();
  await expect(page.getByText(`Ops recovery async export task cancelled: ${recoveryHistoryExportAsyncStartedTaskId}`)).toBeVisible();
  await page.locator('[aria-label="Ops recovery async task filter status"]').click();
  await page.getByRole('option', { name: 'Running' }).click();
  await page.getByRole('button', { name: 'Cancel active ops recovery async export tasks' }).click();
  await page.locator('[aria-label="Ops recovery async task filter status"]').click();
  await page.getByRole('option', { name: 'Completed' }).click();
  await page.getByRole('button', { name: 'Refresh ops recovery async export tasks' }).click();
  await page.getByRole('button', { name: 'Cleanup ops recovery async export tasks' }).click();
  await expect(page.getByText(/Ops recovery async export cleanup removed/i)).toBeVisible();
  await page.locator('[aria-label="Ops recovery async task filter status"]').click();
  await page.getByRole('option', { name: 'Cancelled' }).click();
  await page.getByRole('button', { name: 'Dry-run retry terminal ops recovery async export tasks' }).click();
  await expect(page.getByText(/Ops recovery async terminal dry-run: retryable=1, skipped=0/i)).toBeVisible();
  const opsRetryDryRunTable = page.getByRole('table', { name: 'Ops recovery async retry dry-run candidates' });
  await expect(opsRetryDryRunTable.getByText(recoveryHistoryExportAsyncStartedTaskId)).toBeVisible();
  await expect(opsRetryDryRunTable.getByRole('cell', { name: 'HISTORY_SUMMARY', exact: true })).toBeVisible();
  await expect(opsRetryDryRunTable.getByRole('cell', { name: 'RETRYABLE', exact: true })).toBeVisible();
  const opsDryRunCheckbox = page.getByRole('checkbox', {
    name: `Select ops recovery dry-run source task ${recoveryHistoryExportAsyncStartedTaskId}`,
  });
  await expect(opsDryRunCheckbox).toBeChecked();
  await page.getByRole('button', { name: 'Export ops recovery terminal dry-run CSV' }).click();
  await expect(page.getByText('Ops recovery async terminal dry-run CSV exported')).toBeVisible();
  await page.getByRole('button', { name: 'Retry selected ops recovery dry-run candidates' }).click();
  await expect(page.getByText(/Ops recovery async selected retry done: retried=1, reused=0, skipped=0, failed=0/i)).toBeVisible();
  await page.locator('[aria-label="Ops recovery async task filter status"]').click();
  await page.getByRole('option', { name: 'All' }).click();
  await page.getByRole('button', { name: 'Refresh ops recovery async export tasks' }).click();
  await expect(page.getByLabel(recoveryHistoryExportAsyncRetriedTaskId, { exact: true })).toBeVisible();

  const dryRunByReason = page.getByRole('button', { name: /Dry run reason group Timeout contacting preview service/i }).first();
  await dryRunByReason.click();
  await expect(page.getByText(/Dry-run Timeout contacting preview service: matched=1, queued=1, skipped=0, failed=0/i)).toBeVisible();
  const clearByReason = page.getByRole('button', { name: /Clear dead-letter reason group Timeout contacting preview service/i }).first();
  await clearByReason.click();
  await expect(page.getByText(/Dead-letter clear done: 1\/1/i)).toBeVisible();
  const replayDeadLetterByReason = page.getByRole('button', { name: /Replay dead-letter reason group Timeout contacting preview service/i }).first();
  await replayDeadLetterByReason.click();
  await expect(page.getByText(/Dead-letter replay queued: 1\/1/i)).toBeVisible();
  const replayUnsupportedByReason = page.getByRole('button', { name: /Replay dead-letter reason group Preview not supported for mime type application\/octet-stream/i }).first();
  await expect(replayUnsupportedByReason).toBeDisabled();
  const clearUnsupportedByReason = page.getByRole('button', { name: /Clear dead-letter reason group Preview not supported for mime type application\/octet-stream/i }).first();
  await clearUnsupportedByReason.click();
  await expect(page.getByText(/Dead-letter clear done: 1\/1 \(Preview not supported for mime type application\/octet-stream\)/i)).toBeVisible();
  const resetLedgerByReason = page.locator('button:has-text("Reset Ledger")').first();
  await resetLedgerByReason.click();
  await expect(page.getByText(/Failure ledger reset done: 1\/1 \(Timeout contacting preview service\)/i)).toBeVisible();

  await page.getByRole('button', { name: 'Rollback to v2' }).click();
  await expect(page.getByText(/Policy rolled back:/i)).toBeVisible();

  await page.getByRole('button', { name: `Unblock prevention ${blockedPreventionId}` }).click();
  await expect(page.getByText(`Unblocked: ${blockedPreventionId}`)).toBeVisible();
  await page.getByRole('checkbox', { name: `Select blocked entry ${blockedPreventionId}` }).check();
  await page.getByRole('button', { name: 'Unblock Selected' }).click();
  await expect(page.getByText(/Unblock batch done:/i)).toBeVisible();
  await page.getByRole('checkbox', { name: `Select blocked entry ${blockedPreventionId}` }).check();
  await page.getByRole('button', { name: 'Requeue Selected' }).click();
  await expect(page.getByText(/Requeue batch done:/i)).toBeVisible();
  await page.getByRole('checkbox', { name: `Select failure ledger entry ${retryableId}` }).check();
  await page.getByRole('button', { name: 'Reset Selected' }).click();
  await expect(page.getByText(/Failure ledger reset: 1\/1/i)).toBeVisible();
  await page.getByRole('button', { name: `Reset failure ledger ${permanentId}` }).click();
  await expect(page.getByText(`Failure ledger reset: ${permanentId}`)).toBeVisible();
  await page.getByRole('button', { name: 'Export Ledger CSV' }).click();
  await expect(page.getByText('Failure ledger CSV exported')).toBeVisible();
  await page.getByRole('checkbox', { name: `Select dead-letter entry ${deadLetterId}` }).check();
  await page.getByRole('button', { name: 'Replay Selected' }).click();
  await expect(page.getByText(/^Replay queued:/i)).toBeVisible();
  await page.getByRole('checkbox', { name: `Select dead-letter entry ${deadLetterId}` }).check();
  await page.getByRole('button', { name: 'Clear Selected' }).click();
  await expect(page.getByText(/Dead-letter cleared:/i).last()).toBeVisible();
  await page.getByRole('button', { name: `Clear dead-letter ${deadLetterId}` }).click();
  await expect(page.getByText(/Dead-letter cleared:/i).last()).toBeVisible();
  await page.getByRole('button', { name: 'Export CSV' }).click();
  await expect(page.getByText('Dead-letter CSV exported')).toBeVisible();
  await page.getByRole('button', { name: `Unblock and requeue ${blockedPreventionId}` }).click();
  await expect(page.getByText(`Unblocked and queued: ${blockedPreventionId}`)).toBeVisible();

  await expect(page.getByText('Total 3')).toBeVisible();
  await expect(page.getByText('Retryable 1')).toBeVisible();
  await expect(page.getByText('Permanent 1')).toBeVisible();
  await expect(page.getByText('Unsupported 1')).toBeVisible();

  const filter = page.getByPlaceholder('Filter by name, path, mime type...');
  await filter.fill(retryableName);

  const retryableRow = page
    .locator('tr', { hasText: retryableName })
    .filter({ has: page.getByRole('button', { name: 'Copy document id' }) });
  await expect(retryableRow).toBeVisible();

  await retryableRow.getByRole('button', { name: 'Copy document id' }).click();
  await expect(page.getByText('Document id copied')).toBeVisible();
  const copied = await page.evaluate(() => (window as any).__copiedText);
  expect(copied).toBe(retryableId);

  // Tooltip wrapper includes a <span aria-label="Retry preview"> plus the real button; use role=button.
  const retryButton = retryableRow.getByRole('button', { name: 'Retry preview' });
  await expect(retryButton).toBeEnabled();
  await retryButton.click();
  await expect(page.getByText('Preview retry queued')).toBeVisible();

  await filter.fill(unsupportedName);
  const unsupportedRow = page.locator('tr', { hasText: unsupportedName });
  await expect(unsupportedRow).toBeVisible();
  await expect(unsupportedRow.getByRole('button', { name: 'Retry preview' })).toBeDisabled();
  await expect(unsupportedRow.getByRole('button', { name: 'Force rebuild preview' })).toBeDisabled();

  await page.locator('[aria-label="Preview diagnostics days"]').click();
  await page.getByRole('option', { name: 'Last 30 days' }).click();

  await expect(page.getByText('Sampled 4/4')).toBeVisible({ timeout: 60_000 });
  await expect(page.getByText('Total 4')).toBeVisible();
  await expect(page.getByText('Retryable 2')).toBeVisible();
  await expect(page.getByText('Sampled 120/140')).toBeVisible();
  await expect(page.getByText('Status READY: 70')).toBeVisible();
  await expect(page.getByText('e2e-rendition-resource-30.pdf')).toBeVisible();
  await expect(page.getByText('Overflow reason should never be visible')).toHaveCount(0);

  const retryByReason = page.getByRole('button', { name: /Retry reason group Timeout contacting preview service/i }).first();
  await expect(retryByReason).toBeEnabled();
  await retryByReason.click();
  await expect(page.getByText(/Retry queued for 2\/2 document\(s\): Timeout contacting preview service/i)).toBeVisible();

  expect(requestedFailureDays.some((value) => value === '7')).toBeTruthy();
  expect(requestedFailureDays.some((value) => value === '30')).toBeTruthy();
  expect(requestedSummaryDays.some((value) => value === '7')).toBeTruthy();
  expect(requestedSummaryDays.some((value) => value === '30')).toBeTruthy();
  expect(requestedRenditionSummaryDays.some((value) => value === '7')).toBeTruthy();
  expect(requestedRenditionSummaryDays.some((value) => value === '30')).toBeTruthy();
  expect(requestedRenditionSummarySampleLimits.some((value) => value === '500')).toBeTruthy();
  expect(requestedRenditionSummaryDays.length).toBeGreaterThanOrEqual(3);
  expect(requestedRenditionResourceDays.some((value) => value === '7')).toBeTruthy();
  expect(requestedRenditionResourceDays.some((value) => value === '30')).toBeTruthy();
  expect(requestedRenditionResourceLimits.some((value) => value === '500')).toBeTruthy();
  expect(requestedRenditionResourceDays.length).toBeGreaterThanOrEqual(3);
  expect(requestedRenditionResourceExportCalls.length).toBeGreaterThan(0);
  expect(requestedRenditionResourceExportCalls.some((call) => call.days === '7')).toBeTruthy();
  expect(requestedRenditionResourceExportCalls.some((call) => call.limit === '500')).toBeTruthy();
  expect(renditionResourceExportAsyncStartCalls.length).toBeGreaterThan(0);
  expect(renditionResourceExportAsyncStartCalls.some((call) => call.days === 7)).toBeTruthy();
  expect(renditionResourceExportAsyncStartCalls.some((call) => call.limit === 500)).toBeTruthy();
  expect(renditionResourceExportAsyncListCalls.length).toBeGreaterThan(0);
  expect(renditionResourceExportAsyncListCalls.some((call) => call.limit === 10)).toBeTruthy();
  expect(renditionResourceExportAsyncListCalls.some((call) => call.skipCount === 0)).toBeTruthy();
  expect(renditionResourceExportAsyncListCalls.some((call) => call.status === 'ALL')).toBeTruthy();
  expect(renditionResourceExportAsyncListCalls.some((call) => call.status === 'COMPLETED')).toBeTruthy();
  expect(renditionResourceExportAsyncListCalls.some((call) => call.status === 'CANCELLED')).toBeTruthy();
  expect(renditionResourceExportAsyncSummaryCalls.length).toBeGreaterThan(0);
  expect(renditionResourceExportAsyncSummaryCalls.some((call) => call.status === 'ALL')).toBeTruthy();
  expect(renditionResourceExportAsyncSummaryCalls.some((call) => call.status === 'COMPLETED')).toBeTruthy();
  expect(renditionResourceExportAsyncSummaryCalls.some((call) => call.status === 'CANCELLED')).toBeTruthy();
  expect(renditionResourceExportAsyncCancelActiveCalls.length).toBeGreaterThan(0);
  expect(renditionResourceExportAsyncCancelActiveCalls.some((call) => call.status === 'ALL')).toBeTruthy();
  expect(renditionResourceExportAsyncCancelActiveCalls.some((call) => call.status === 'COMPLETED')).toBeFalsy();
  expect(renditionResourceExportAsyncCleanupCalls.length).toBeGreaterThan(0);
  expect(renditionResourceExportAsyncCleanupCalls.some((call) => call.status === 'COMPLETED')).toBeTruthy();
  expect(renditionResourceExportAsyncCancelCalls).toContain(renditionResourceExportAsyncStartedTaskId);
  expect(renditionResourceExportAsyncDownloadCalls).toContain(renditionResourceExportAsyncCompletedTaskId);
  expect(renditionResourceExportAsyncRetryTerminalDryRunCalls.length).toBeGreaterThan(0);
  expect(renditionResourceExportAsyncRetryTerminalDryRunCalls.some((call) => call.status === 'CANCELLED')).toBeTruthy();
  expect(renditionResourceExportAsyncRetryTerminalDryRunCalls.some((call) => call.limit === 10)).toBeTruthy();
  expect(renditionResourceExportAsyncRetryTerminalDryRunExportCalls.length).toBeGreaterThan(0);
  expect(renditionResourceExportAsyncRetryTerminalDryRunExportCalls.some((call) => call.status === 'CANCELLED')).toBeTruthy();
  expect(renditionResourceExportAsyncRetrySelectedCalls.length).toBeGreaterThan(0);
  expect(renditionResourceExportAsyncRetrySelectedCalls.some((call) => call.sourceTaskIds.includes(renditionResourceExportAsyncStartedTaskId))).toBeTruthy();
  expect(reasonBatchCalls.some((call) => call.reason === 'Timeout contacting preview service')).toBeTruthy();
  expect(reasonBatchCalls.some((call) => call.days === 30)).toBeTruthy();
  expect(queueWindowCalls.length).toBeGreaterThan(0);
  expect(queueWindowCalls.some((call) => call.days === 7)).toBeTruthy();
  expect(reasonDryRunCalls.some((call) => call.reason === 'Timeout contacting preview service')).toBeTruthy();
  expect(reasonDryRunCalls.some((call) => call.mode === 'QUEUE_BY_WINDOW')).toBeTruthy();
  expect(reasonDryRunCalls.some((call) => call.mode === 'CLEAR_BY_FILTER' && call.reason === 'Timeout contacting preview service')).toBeTruthy();
  expect(reasonDryRunCalls.some((call) => call.mode === 'REPLAY_BY_FILTER' && call.reason === 'Timeout contacting preview service')).toBeTruthy();
  expect(policyRollbackCalls.length).toBeGreaterThan(0);
  expect(policyHistoryCalls.length).toBeGreaterThan(0);
  expect(recoveryHistoryCalls.length).toBeGreaterThan(0);
  expect(recoveryHistoryCalls.some((value) => value.days === '7')).toBeTruthy();
  expect(recoveryHistoryCalls.some((value) => value.days === '30')).toBeTruthy();
  expect(recoveryHistoryCalls.some((value) => value.page === 0)).toBeTruthy();
  expect(recoveryHistoryCalls.some((value) => value.mode === 'DRY_RUN')).toBeTruthy();
  expect(recoveryHistoryCalls.some((value) => value.actor === 'admin')).toBeTruthy();
  expect(recoveryHistoryCalls.some((value) => value.eventType === 'OPS_RECOVERY_DRY_RUN')).toBeTruthy();
  expect(recoveryHistorySummaryCalls.length).toBeGreaterThan(0);
  expect(recoveryHistorySummaryCalls.some((value) => value.days === '7')).toBeTruthy();
  expect(recoveryHistorySummaryCalls.some((value) => value.days === '30')).toBeTruthy();
  expect(recoveryHistorySummaryCalls.some((value) => value.mode === 'DRY_RUN')).toBeTruthy();
  expect(recoveryHistorySummaryCalls.some((value) => value.actor === 'admin')).toBeTruthy();
  expect(recoveryHistorySummaryCalls.some((value) => value.eventType === 'OPS_RECOVERY_DRY_RUN')).toBeTruthy();
  expect(recoveryHistoryCompareCalls.length).toBeGreaterThan(0);
  expect(recoveryHistoryCompareCalls.some((value) => value.days === '7')).toBeTruthy();
  expect(recoveryHistoryCompareCalls.some((value) => value.mode === 'DRY_RUN')).toBeTruthy();
  expect(recoveryHistoryCompareCalls.some((value) => value.actor === 'admin')).toBeTruthy();
  expect(recoveryHistoryCompareCalls.some((value) => value.eventType === 'OPS_RECOVERY_DRY_RUN')).toBeTruthy();
  expect(recoveryHistoryCompareActorCalls.length).toBeGreaterThan(0);
  expect(recoveryHistoryCompareActorCalls.some((value) => value.days === '7')).toBeTruthy();
  expect(recoveryHistoryCompareActorCalls.some((value) => value.mode === 'DRY_RUN')).toBeTruthy();
  expect(recoveryHistoryCompareActorCalls.some((value) => value.actor === 'admin')).toBeTruthy();
  expect(recoveryHistoryCompareActorCalls.some((value) => value.eventType === 'OPS_RECOVERY_DRY_RUN')).toBeTruthy();
  expect(recoveryHistoryCompareActorCalls.some((value) => value.limit === 10)).toBeTruthy();
  expect(recoveryHistoryCompareActorCalls.some((value) => value.sort === 'DELTA_ABS_DESC')).toBeTruthy();
  expect(recoveryHistoryCompareActorCalls.some((value) => value.limit === 20)).toBeTruthy();
  expect(recoveryHistoryCompareActorCalls.some((value) => value.sort === 'DELTA_ASC')).toBeTruthy();
  expect(recoveryHistoryCompareBreakdownCalls.length).toBeGreaterThan(0);
  expect(recoveryHistoryCompareBreakdownCalls.some((value) => value.days === '7')).toBeTruthy();
  expect(recoveryHistoryCompareBreakdownCalls.some((value) => value.mode === 'DRY_RUN')).toBeTruthy();
  expect(recoveryHistoryCompareBreakdownCalls.some((value) => value.actor === 'admin')).toBeTruthy();
  expect(recoveryHistoryCompareBreakdownCalls.some((value) => value.eventType === 'OPS_RECOVERY_DRY_RUN')).toBeTruthy();
  expect(recoveryHistoryCompareBreakdownCalls.some((value) => value.limit === 10)).toBeTruthy();
  expect(recoveryHistoryCompareBreakdownCalls.some((value) => value.sort === 'DELTA_ABS_DESC')).toBeTruthy();
  expect(recoveryHistoryCompareBreakdownCalls.some((value) => value.limit === 20)).toBeTruthy();
  expect(recoveryHistoryCompareBreakdownCalls.some((value) => value.sort === 'DELTA_ASC')).toBeTruthy();
  expect(recoveryHistoryTrendCalls.length).toBeGreaterThan(0);
  expect(recoveryHistoryTrendCalls.some((value) => value.days === '7')).toBeTruthy();
  expect(recoveryHistoryTrendCalls.some((value) => value.mode === 'DRY_RUN')).toBeTruthy();
  expect(recoveryHistoryTrendCalls.some((value) => value.actor === 'admin')).toBeTruthy();
  expect(recoveryHistoryTrendCalls.some((value) => value.eventType === 'OPS_RECOVERY_DRY_RUN')).toBeTruthy();
  expect(recoveryHistoryExportCalls.length).toBeGreaterThan(0);
  expect(recoveryHistoryExportCalls.some((value) => value.limit === 500)).toBeTruthy();
  expect(recoveryHistoryExportCalls.some((value) => value.mode === 'DRY_RUN')).toBeTruthy();
  expect(recoveryHistoryExportCalls.some((value) => value.actor === 'admin')).toBeTruthy();
  expect(recoveryHistoryExportCalls.some((value) => value.eventType === 'OPS_RECOVERY_DRY_RUN')).toBeTruthy();
  expect(recoveryHistoryExportAsyncStartCalls.length).toBeGreaterThan(0);
  expect(recoveryHistoryExportAsyncStartCalls.some((value) => value.exportType === 'HISTORY_SUMMARY')).toBeTruthy();
  expect(recoveryHistoryExportAsyncStartCalls.some((value) => value.days === 7)).toBeTruthy();
  expect(recoveryHistoryExportAsyncStartCalls.some((value) => value.mode === 'DRY_RUN')).toBeTruthy();
  expect(recoveryHistoryExportAsyncStartCalls.some((value) => value.actor === 'admin')).toBeTruthy();
  expect(recoveryHistoryExportAsyncStartCalls.some((value) => value.eventType === 'OPS_RECOVERY_DRY_RUN')).toBeTruthy();
  expect(recoveryHistoryExportAsyncStartCalls.some((value) => value.limit === 500)).toBeTruthy();
  expect(recoveryHistoryExportAsyncStartCalls.some((value) => value.compareBreakdownLimit === 20)).toBeTruthy();
  expect(recoveryHistoryExportAsyncStartCalls.some((value) => value.compareBreakdownSort === 'DELTA_ASC')).toBeTruthy();
  expect(recoveryHistoryExportAsyncStartCalls.some((value) => value.compareActorLimit === 20)).toBeTruthy();
  expect(recoveryHistoryExportAsyncStartCalls.some((value) => value.compareActorSort === 'DELTA_ASC')).toBeTruthy();
  expect(recoveryHistoryExportAsyncListCalls.length).toBeGreaterThan(0);
  expect(recoveryHistoryExportAsyncListCalls.some((value) => value.limit === 20)).toBeTruthy();
  expect(recoveryHistoryExportAsyncListCalls.some((value) => value.skipCount === 0)).toBeTruthy();
  expect(recoveryHistoryExportAsyncListCalls.some((value) => value.exportType === 'HISTORY_SUMMARY')).toBeTruthy();
  expect(recoveryHistoryExportAsyncListCalls.some((value) => value.status === 'COMPLETED')).toBeTruthy();
  expect(recoveryHistoryExportAsyncSummaryCalls.length).toBeGreaterThan(0);
  expect(recoveryHistoryExportAsyncSummaryCalls.some((value) => value.exportType === 'HISTORY_SUMMARY')).toBeTruthy();
  expect(recoveryHistoryExportAsyncSummaryCalls.some((value) => value.status === 'COMPLETED')).toBeTruthy();
  expect(recoveryHistoryExportAsyncGetCalls).toContain(recoveryHistoryExportAsyncCompletedTaskId);
  expect(recoveryHistoryExportAsyncCancelCalls).toContain(recoveryHistoryExportAsyncStartedTaskId);
  expect(recoveryHistoryExportAsyncDownloadCalls).toContain(recoveryHistoryExportAsyncCompletedTaskId);
  expect(recoveryHistoryExportAsyncCleanupCalls.length).toBeGreaterThan(0);
  expect(recoveryHistoryExportAsyncCleanupCalls.some((value) => value.exportType === 'HISTORY_SUMMARY')).toBeTruthy();
  expect(recoveryHistoryExportAsyncCleanupCalls.some((value) => value.status === 'COMPLETED')).toBeTruthy();
  expect(recoveryHistoryExportAsyncRetryTerminalDryRunCalls.length).toBeGreaterThan(0);
  expect(recoveryHistoryExportAsyncRetryTerminalDryRunCalls.some((value) => value.exportType === 'HISTORY_SUMMARY')).toBeTruthy();
  expect(recoveryHistoryExportAsyncRetryTerminalDryRunCalls.some((value) => value.status === 'CANCELLED')).toBeTruthy();
  expect(recoveryHistoryExportAsyncRetryTerminalDryRunCalls.some((value) => value.limit === 20)).toBeTruthy();
  expect(recoveryHistoryExportAsyncRetryTerminalDryRunExportCalls.length).toBeGreaterThan(0);
  expect(recoveryHistoryExportAsyncRetryTerminalDryRunExportCalls.some((value) => value.exportType === 'HISTORY_SUMMARY')).toBeTruthy();
  expect(recoveryHistoryExportAsyncRetryTerminalDryRunExportCalls.some((value) => value.status === 'CANCELLED')).toBeTruthy();
  expect(recoveryHistoryExportAsyncRetrySelectedCalls.length).toBeGreaterThan(0);
  expect(recoveryHistoryExportAsyncRetrySelectedCalls.some((value) => value.exportType === 'HISTORY_SUMMARY')).toBeTruthy();
  expect(recoveryHistoryExportAsyncRetrySelectedCalls.some((value) => value.sourceTaskIds.includes(recoveryHistoryExportAsyncStartedTaskId))).toBeTruthy();
  expect(recoveryHistorySummaryExportCalls.length).toBeGreaterThan(0);
  expect(recoveryHistorySummaryExportCalls.some((value) => value.days === '7')).toBeTruthy();
  expect(recoveryHistorySummaryExportCalls.some((value) => value.mode === 'DRY_RUN')).toBeTruthy();
  expect(recoveryHistorySummaryExportCalls.some((value) => value.actor === 'admin')).toBeTruthy();
  expect(recoveryHistorySummaryExportCalls.some((value) => value.eventType === 'OPS_RECOVERY_DRY_RUN')).toBeTruthy();
  expect(recoveryHistoryCompareExportCalls.length).toBeGreaterThan(0);
  expect(recoveryHistoryCompareExportCalls.some((value) => value.days === '7')).toBeTruthy();
  expect(recoveryHistoryCompareExportCalls.some((value) => value.mode === 'DRY_RUN')).toBeTruthy();
  expect(recoveryHistoryCompareExportCalls.some((value) => value.actor === 'admin')).toBeTruthy();
  expect(recoveryHistoryCompareExportCalls.some((value) => value.eventType === 'OPS_RECOVERY_DRY_RUN')).toBeTruthy();
  expect(recoveryHistoryCompareActorExportCalls.length).toBeGreaterThan(0);
  expect(recoveryHistoryCompareActorExportCalls.some((value) => value.days === '7')).toBeTruthy();
  expect(recoveryHistoryCompareActorExportCalls.some((value) => value.mode === 'DRY_RUN')).toBeTruthy();
  expect(recoveryHistoryCompareActorExportCalls.some((value) => value.actor === 'admin')).toBeTruthy();
  expect(recoveryHistoryCompareActorExportCalls.some((value) => value.eventType === 'OPS_RECOVERY_DRY_RUN')).toBeTruthy();
  expect(recoveryHistoryCompareActorExportCalls.some((value) => value.limit === 20)).toBeTruthy();
  expect(recoveryHistoryCompareActorExportCalls.some((value) => value.sort === 'DELTA_ASC')).toBeTruthy();
  expect(recoveryHistoryCompareBreakdownExportCalls.length).toBeGreaterThan(0);
  expect(recoveryHistoryCompareBreakdownExportCalls.some((value) => value.days === '7')).toBeTruthy();
  expect(recoveryHistoryCompareBreakdownExportCalls.some((value) => value.mode === 'DRY_RUN')).toBeTruthy();
  expect(recoveryHistoryCompareBreakdownExportCalls.some((value) => value.actor === 'admin')).toBeTruthy();
  expect(recoveryHistoryCompareBreakdownExportCalls.some((value) => value.eventType === 'OPS_RECOVERY_DRY_RUN')).toBeTruthy();
  expect(recoveryHistoryCompareBreakdownExportCalls.some((value) => value.limit === 20)).toBeTruthy();
  expect(recoveryHistoryCompareBreakdownExportCalls.some((value) => value.sort === 'DELTA_ASC')).toBeTruthy();
  expect(recoveryHistoryTrendExportCalls.length).toBeGreaterThan(0);
  expect(recoveryHistoryTrendExportCalls.some((value) => value.days === '7')).toBeTruthy();
  expect(recoveryHistoryTrendExportCalls.some((value) => value.mode === 'DRY_RUN')).toBeTruthy();
  expect(recoveryHistoryTrendExportCalls.some((value) => value.actor === 'admin')).toBeTruthy();
  expect(recoveryHistoryTrendExportCalls.some((value) => value.eventType === 'OPS_RECOVERY_DRY_RUN')).toBeTruthy();
  expect(preventionActionCalls.some((call) => call.id === blockedPreventionId && call.action === 'unblock')).toBeTruthy();
  expect(preventionActionCalls.some((call) => call.id === blockedPreventionId && call.action === 'requeue' && call.force)).toBeTruthy();
  expect(preventionBatchCalls.some((call) => call.action === 'unblock' && call.ids.includes(blockedPreventionId))).toBeTruthy();
  expect(preventionBatchCalls.some((call) => call.action === 'requeue' && call.force && call.ids.includes(blockedPreventionId))).toBeTruthy();
  expect(deadLetterReplayBatchCalls.some((call) => call.force && call.ids.includes(deadLetterId))).toBeTruthy();
  expect(deadLetterClearBatchCalls.length).toBeGreaterThan(0);
  expect(deadLetterClearBatchCalls.some((call) => call.ids.includes(deadLetterId))).toBeTruthy();
  expect(deadLetterClearByFilterCalls.length).toBeGreaterThan(0);
  expect(deadLetterClearByFilterCalls.some((call) => call.reason === 'Timeout contacting preview service')).toBeTruthy();
  expect(deadLetterClearByFilterCalls.some((call) => call.category === 'TEMPORARY' && call.retryable)).toBeTruthy();
  expect(deadLetterClearByFilterCalls.some((call) => call.reason === 'Preview not supported for mime type application/octet-stream')).toBeTruthy();
  expect(deadLetterClearByFilterCalls.some((call) => call.category === 'UNSUPPORTED' && !call.retryable)).toBeTruthy();
  expect(deadLetterReplayByFilterCalls.length).toBeGreaterThan(0);
  expect(deadLetterReplayByFilterCalls.some((call) => call.reason === 'Timeout contacting preview service')).toBeTruthy();
  expect(deadLetterReplayByFilterCalls.some((call) => call.category === 'TEMPORARY' && call.retryable && call.force)).toBeTruthy();
  expect(deadLetterReplayByFilterCalls.some((call) => call.category === 'UNSUPPORTED' && !call.retryable)).toBeFalsy();
  expect(deadLetterExportCalls.length).toBeGreaterThan(0);
  expect(failureLedgerRequestedDays.some((value) => value === '7')).toBeTruthy();
  expect(failureLedgerRequestedDays.some((value) => value === '30')).toBeTruthy();
  expect(failureLedgerResetCalls).toContain(permanentId);
  expect(failureLedgerResetBatchCalls.some((ids) => ids.includes(retryableId))).toBeTruthy();
  expect(failureLedgerResetByFilterCalls.length).toBeGreaterThan(0);
  expect(failureLedgerResetByFilterCalls.some((call) => call.reason === 'Timeout contacting preview service')).toBeTruthy();
  expect(failureLedgerResetByFilterCalls.some((call) => call.category === 'TEMPORARY' && call.retryable)).toBeTruthy();
  expect(failureLedgerExportCalls.length).toBeGreaterThan(0);
  expect(failureLedgerExportCalls.some((call) => call.days === '7')).toBeTruthy();
  expect(failureLedgerExportCalls.some((call) => call.limit === '500')).toBeTruthy();
  expect(queueSummaryCalls.length).toBeGreaterThan(0);
  expect(queueSummaryCalls.some((call) => call.limit === '20')).toBeTruthy();
  expect(queueSummaryCalls.some((call) => call.state === 'ALL')).toBeTruthy();
  expect(queueSummaryCalls.some((call) => call.state === 'RUNNING' && call.query === 'retryable')).toBeTruthy();
  expect(queueSummaryExportCalls.length).toBeGreaterThan(0);
  expect(queueSummaryExportCalls.some((call) => call.limit === '200')).toBeTruthy();
  expect(queueSummaryExportCalls.some((call) => call.state === 'RUNNING' && call.query === 'retryable')).toBeTruthy();
  expect(queueCancelActiveCalls.length).toBeGreaterThan(0);
  expect(queueCancelActiveCalls.some((call) => call.limit === '200')).toBeTruthy();
  expect(queueCancelActiveCalls.some((call) => call.state === 'RUNNING' && call.query === 'retryable')).toBeTruthy();
  expect(queueDeclinedSummaryCalls.length).toBeGreaterThan(0);
  expect(queueDeclinedSummaryCalls.some((call) => call.limit === '20')).toBeTruthy();
  expect(queueDeclinedSummaryCalls.some((call) => call.category === 'ANY' && call.forceRequired === 'ANY' && call.windowHours === '')).toBeTruthy();
  expect(queueDeclinedSummaryCalls.some((call) => (
    call.category === 'QUIET_PERIOD' && call.forceRequired === 'NO' && call.windowHours === '1' && call.query === 'retryable'
  ))).toBeTruthy();
  expect(queueDeclinedExportCalls.length).toBeGreaterThan(0);
  expect(queueDeclinedExportCalls.some((call) => call.limit === '500')).toBeTruthy();
  expect(queueDeclinedExportCalls.some((call) => (
    call.category === 'QUIET_PERIOD' && call.forceRequired === 'NO' && call.windowHours === '1' && call.query === 'retryable'
  ))).toBeTruthy();
  expect(queueDeclinedDryRunCalls.length).toBeGreaterThan(0);
  expect(queueDeclinedDryRunCalls.some((call) => call.limit === '500')).toBeTruthy();
  expect(queueDeclinedDryRunCalls.some((call) => (
    call.category === 'QUIET_PERIOD' && call.forceRequired === 'NO' && call.windowHours === '1' && call.query === 'retryable' && !call.force
  ))).toBeTruthy();
  expect(queueDeclinedDryRunExportCalls.length).toBeGreaterThan(0);
  expect(queueDeclinedDryRunExportCalls.some((call) => call.limit === '500')).toBeTruthy();
  expect(queueDeclinedDryRunExportCalls.some((call) => (
    call.category === 'QUIET_PERIOD' && call.forceRequired === 'NO' && call.windowHours === '1' && call.query === 'retryable' && !call.force
  ))).toBeTruthy();
  expect(queueDeclinedDryRunExportAsyncStartCalls.length > 0).toBe(queueDeclinedDryRunAsyncTaskCenterCovered);
  expect(queueDeclinedDryRunExportAsyncStartCalls.some((call) => call.limit === '500')).toBe(queueDeclinedDryRunAsyncTaskCenterCovered);
  expect(queueDeclinedDryRunExportAsyncStartCalls.some((call) => (
    call.category === 'QUIET_PERIOD'
    && call.forceRequired === 'NO'
    && call.windowHours === '1'
    && call.query === 'retryable'
    && !call.force
  ))).toBe(queueDeclinedDryRunAsyncTaskCenterCovered);
  expect(queueDeclinedDryRunExportAsyncListCalls.length > 0).toBe(queueDeclinedDryRunAsyncTaskCenterCovered);
  expect(queueDeclinedDryRunExportAsyncListCalls.some((call) => call.limit === 20)).toBe(queueDeclinedDryRunAsyncTaskCenterCovered);
  expect(queueDeclinedDryRunExportAsyncListCalls.some((call) => call.skipCount === 0)).toBe(queueDeclinedDryRunAsyncTaskCenterCovered);
  expect(queueDeclinedDryRunExportAsyncListCalls.some((call) => call.status === 'ALL')).toBe(queueDeclinedDryRunAsyncTaskCenterCovered);
  expect(queueDeclinedDryRunExportAsyncListCalls.some((call) => call.status === 'COMPLETED')).toBe(queueDeclinedDryRunAsyncTaskCenterCovered);
  expect(queueDeclinedDryRunExportAsyncListCalls.some((call) => call.status === 'CANCELLED')).toBe(queueDeclinedDryRunAsyncTaskCenterCovered);
  expect(queueDeclinedDryRunExportAsyncSummaryCalls.length > 0).toBe(queueDeclinedDryRunAsyncTaskCenterCovered);
  expect(queueDeclinedDryRunExportAsyncSummaryCalls.some((call) => call.status === 'ALL')).toBe(queueDeclinedDryRunAsyncTaskCenterCovered);
  expect(queueDeclinedDryRunExportAsyncSummaryCalls.some((call) => call.status === 'COMPLETED')).toBe(queueDeclinedDryRunAsyncTaskCenterCovered);
  expect(queueDeclinedDryRunExportAsyncSummaryCalls.some((call) => call.status === 'CANCELLED')).toBe(queueDeclinedDryRunAsyncTaskCenterCovered);
  expect(queueDeclinedDryRunExportAsyncCancelActiveCalls.length > 0).toBe(queueDeclinedDryRunAsyncTaskCenterCovered);
  expect(queueDeclinedDryRunExportAsyncCancelActiveCalls.some((call) => call.status === 'ALL')).toBe(queueDeclinedDryRunAsyncTaskCenterCovered);
  expect(queueDeclinedDryRunExportAsyncCancelActiveCalls.some((call) => call.status === 'COMPLETED')).toBeFalsy();
  expect(queueDeclinedDryRunExportAsyncCleanupCalls.length > 0).toBe(queueDeclinedDryRunAsyncTaskCenterCovered);
  expect(queueDeclinedDryRunExportAsyncCleanupCalls.some((call) => call.status === 'COMPLETED')).toBe(queueDeclinedDryRunAsyncTaskCenterCovered);
  expect(queueDeclinedDryRunExportAsyncGetCalls.includes(queueDeclinedDryRunExportAsyncStartedTaskId)).toBe(queueDeclinedDryRunAsyncTaskCenterCovered);
  expect(queueDeclinedDryRunExportAsyncCancelCalls.includes(queueDeclinedDryRunExportAsyncCancelledTaskId)).toBe(queueDeclinedDryRunAsyncTaskCenterCovered);
  expect(queueDeclinedDryRunExportAsyncRetryCalls.includes(queueDeclinedDryRunExportAsyncCancelledTaskId)).toBe(queueDeclinedDryRunAsyncTaskCenterCovered);
  expect(queueDeclinedDryRunExportAsyncRetryDedupCalls.length > 0).toBe(queueDeclinedDryRunAsyncTaskCenterCovered);
  expect(queueDeclinedDryRunExportAsyncRetryDedupCalls.some((call) => (
    call.sourceTaskId === queueDeclinedDryRunExportAsyncCancelledTaskId
    && call.reusedTaskId === queueDeclinedDryRunExportAsyncRetriedTaskId
  ))).toBe(queueDeclinedDryRunAsyncTaskCenterCovered);
  expect(queueDeclinedDryRunExportAsyncDownloadCalls.includes(queueDeclinedDryRunExportAsyncStartedTaskId)).toBe(queueDeclinedDryRunAsyncTaskCenterCovered);
  expect(queueDeclinedDryRunExportAsyncRetryTerminalDryRunCalls.length > 0).toBe(queueDeclinedDryRunAsyncTaskCenterCovered);
  expect(queueDeclinedDryRunExportAsyncRetryTerminalDryRunCalls.some((call) => call.status === 'CANCELLED')).toBe(queueDeclinedDryRunAsyncTaskCenterCovered);
  expect(queueDeclinedDryRunExportAsyncRetryTerminalDryRunCalls.some((call) => call.limit === 20)).toBe(queueDeclinedDryRunAsyncTaskCenterCovered);
  expect(queueDeclinedDryRunExportAsyncRetryTerminalDryRunExportCalls.length > 0).toBe(queueDeclinedDryRunAsyncTaskCenterCovered);
  expect(queueDeclinedDryRunExportAsyncRetryTerminalDryRunExportCalls.some((call) => call.status === 'CANCELLED')).toBe(queueDeclinedDryRunAsyncTaskCenterCovered);
  expect(queueDeclinedDryRunExportAsyncRetryTerminalDryRunExportCalls.some((call) => call.limit === 20)).toBe(queueDeclinedDryRunAsyncTaskCenterCovered);
  expect(queueDeclinedDryRunExportAsyncRetrySelectedCalls.length > 0).toBe(queueDeclinedDryRunAsyncTaskCenterCovered);
  expect(queueDeclinedDryRunExportAsyncRetrySelectedCalls.some((call) => call.sourceTaskIds.includes(queueDeclinedDryRunExportAsyncCancelledTaskId)))
    .toBe(queueDeclinedDryRunAsyncTaskCenterCovered);
  expect(queueDeclinedDryRunExportAsyncRetryTerminalCalls.length > 0).toBe(queueDeclinedDryRunAsyncTaskCenterCovered);
  expect(queueDeclinedDryRunExportAsyncRetryTerminalCalls.some((call) => call.status === 'CANCELLED')).toBe(queueDeclinedDryRunAsyncTaskCenterCovered);
  expect(queueDeclinedDryRunExportAsyncRetryTerminalCalls.some((call) => call.limit === 20)).toBe(queueDeclinedDryRunAsyncTaskCenterCovered);
  expect(queueDeclinedDryRunExportAsyncTasks.has(queueDeclinedDryRunExportAsyncRetriedTaskId)).toBe(queueDeclinedDryRunAsyncTaskCenterCovered);
  expect(queueDeclinedDryRunExportAsyncTasks.has(queueDeclinedDryRunExportAsyncRetryTerminalTaskId)).toBe(queueDeclinedDryRunAsyncTaskCenterCovered);
  expect(queueDeclinedRequeueCalls.length).toBeGreaterThan(0);
  expect(queueDeclinedRequeueCalls.some((call) => call.limit === '500')).toBeTruthy();
  expect(queueDeclinedRequeueCalls.some((call) => (
    call.category === 'QUIET_PERIOD' && call.forceRequired === 'NO' && call.windowHours === '1' && call.query === 'retryable' && call.force
  ))).toBeTruthy();
  expect(queueDeclinedClearCalls.length).toBeGreaterThan(0);
  expect(queueDeclinedClearCalls.some((call) => call.limit === '500')).toBeTruthy();
  expect(queueDeclinedClearCalls.some((call) => (
    call.category === 'QUIET_PERIOD' && call.forceRequired === 'NO' && call.windowHours === '1' && call.query === 'retryable'
  ))).toBeTruthy();
  expect(queueDeclinedExportAsyncStartCalls.length).toBeGreaterThan(0);
  expect(queueDeclinedExportAsyncStartCalls.some((call) => call.limit === '500')).toBeTruthy();
  expect(queueDeclinedExportAsyncStartCalls.some((call) => (
    call.category === 'QUIET_PERIOD' && call.forceRequired === 'NO' && call.windowHours === '1' && call.query === 'retryable'
  ))).toBeTruthy();
  expect(queueDeclinedExportAsyncStartDedupCalls.length).toBeGreaterThan(0);
  expect(queueDeclinedExportAsyncStartDedupCalls.some((call) => call.reusedTaskId === queueDeclinedExportAsyncStartedTaskId)).toBeTruthy();
  expect(queueDeclinedExportAsyncListCalls.length).toBeGreaterThan(0);
  expect(queueDeclinedExportAsyncListCalls.some((call) => call.limit === 20)).toBeTruthy();
  expect(queueDeclinedExportAsyncListCalls.some((call) => call.skipCount === 0)).toBeTruthy();
  expect(queueDeclinedExportAsyncListCalls.some((call) => call.status === 'ALL')).toBeTruthy();
  expect(queueDeclinedExportAsyncListCalls.some((call) => call.status === 'COMPLETED')).toBeTruthy();
  expect(queueDeclinedExportAsyncListCalls.some((call) => call.status === 'CANCELLED')).toBeTruthy();
  expect(queueDeclinedExportAsyncSummaryCalls.length).toBeGreaterThan(0);
  expect(queueDeclinedExportAsyncSummaryCalls.some((call) => call.status === 'ALL')).toBeTruthy();
  expect(queueDeclinedExportAsyncSummaryCalls.some((call) => call.status === 'COMPLETED')).toBeTruthy();
  expect(queueDeclinedExportAsyncSummaryCalls.some((call) => call.status === 'CANCELLED')).toBeTruthy();
  expect(queueDeclinedExportAsyncCancelActiveCalls.length).toBeGreaterThan(0);
  expect(queueDeclinedExportAsyncCancelActiveCalls.some((call) => call.status === 'ALL')).toBeTruthy();
  expect(queueDeclinedExportAsyncCancelActiveCalls.some((call) => call.status === 'COMPLETED')).toBeFalsy();
  expect(queueDeclinedExportAsyncCancelActiveCalls.some((call) => call.status === 'CANCELLED')).toBeFalsy();
  expect(queueDeclinedExportAsyncRetryTerminalDryRunCalls.length).toBeGreaterThan(0);
  expect(queueDeclinedExportAsyncRetryTerminalDryRunCalls.some((call) => call.status === 'CANCELLED')).toBeTruthy();
  expect(queueDeclinedExportAsyncRetryTerminalDryRunCalls.some((call) => call.limit === 20)).toBeTruthy();
  expect(queueDeclinedExportAsyncRetryTerminalDryRunExportCalls.length).toBeGreaterThan(0);
  expect(queueDeclinedExportAsyncRetryTerminalDryRunExportCalls.some((call) => call.status === 'CANCELLED')).toBeTruthy();
  expect(queueDeclinedExportAsyncRetryTerminalDryRunExportCalls.some((call) => call.limit === 20)).toBeTruthy();
  expect(queueDeclinedExportAsyncRetrySelectedCalls.length).toBeGreaterThan(0);
  expect(queueDeclinedExportAsyncRetrySelectedCalls.some((call) => call.sourceTaskIds.includes(queueDeclinedExportAsyncCancelledTaskId))).toBeTruthy();
  expect(queueDeclinedExportAsyncRetryTerminalCalls.length).toBeGreaterThan(0);
  expect(queueDeclinedExportAsyncRetryTerminalCalls.some((call) => call.status === 'CANCELLED')).toBeTruthy();
  expect(queueDeclinedExportAsyncRetryTerminalCalls.some((call) => call.limit === 20)).toBeTruthy();
  expect(queueDeclinedExportAsyncCleanupCalls.length).toBeGreaterThan(0);
  expect(queueDeclinedExportAsyncCleanupCalls.some((call) => call.status === 'COMPLETED')).toBeTruthy();
  expect(queueDeclinedExportAsyncCleanupCalls.some((call) => call.status === 'CANCELLED')).toBeTruthy();
  expect(queueDeclinedExportAsyncCleanupCalls.some((call) => call.status === 'ALL')).toBeFalsy();
  expect(queueDeclinedExportAsyncGetCalls).toContain(queueDeclinedExportAsyncStartedTaskId);
  expect(queueDeclinedExportAsyncCancelCalls).toContain(queueDeclinedExportAsyncCancelledTaskId);
  expect(queueDeclinedExportAsyncRetryCalls).toContain(queueDeclinedExportAsyncCancelledTaskId);
  expect(queueDeclinedExportAsyncRetryDedupCalls.length).toBeGreaterThan(0);
  expect(queueDeclinedExportAsyncRetryDedupCalls.some((call) => (
    call.sourceTaskId === queueDeclinedExportAsyncCancelledTaskId
    && call.reusedTaskId === queueDeclinedExportAsyncRetriedTaskId
  ))).toBeTruthy();
  expect(queueDeclinedExportAsyncDownloadCalls).toContain(queueDeclinedExportAsyncStartedTaskId);
  expect(queueDeclinedExportAsyncTasks.has(queueDeclinedExportAsyncRetriedTaskId)).toBeTruthy();
  expect(queueDeclinedExportAsyncTasks.has(queueDeclinedExportAsyncRetryTerminalTaskId)).toBeTruthy();
  const nonForceQueueIds = queueCalls.filter((call) => !call.force).map((call) => call.id);
  const forceQueueIds = queueCalls.filter((call) => call.force).map((call) => call.id);
  expect(nonForceQueueIds.filter((id) => id === retryableTwinId).length).toBe(1);
  expect(nonForceQueueIds.filter((id) => id === retryableId).length).toBeGreaterThanOrEqual(2);
  expect(nonForceQueueIds.filter((id) => id === renditionResourceRetryId).length).toBeGreaterThanOrEqual(1);
  expect(forceQueueIds.filter((id) => id === blockedPreventionId).length).toBeGreaterThanOrEqual(2);
});
