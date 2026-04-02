/* eslint-disable testing-library/prefer-screen-queries */
import { expect, test } from '@playwright/test';
import { seedBypassSessionE2E } from './helpers/login';

test('Advanced search: retry all matched failed previews scans across pages (mocked API)', async ({ page }) => {
  test.setTimeout(120_000);

  await seedBypassSessionE2E(page, 'admin', 'e2e-token');

  const now = new Date().toISOString();
  const query = `phase193-preview-batch-${Date.now()}`;
  const searchCalls: Array<{ page: number; size: number; previewStatus: string | null }> = [];
  const searchScopeBatchCalls: any[] = [];
  const searchScopeDryRunCalls: any[] = [];
  const searchScopeDryRunExportAsyncStartCalls: any[] = [];
  const searchScopeDryRunExportAsyncListCalls: Array<{ limit: string | null; status: string | null }> = [];
  const searchScopeDryRunExportAsyncSummaryCalls: Array<{ status: string | null }> = [];
  const searchScopeDryRunExportAsyncCancelActiveCalls: Array<{ status: string | null }> = [];
  const searchScopeDryRunExportAsyncCleanupCalls: Array<{ status: string | null }> = [];
  const searchScopeDryRunExportAsyncStatusCalls: Array<{ taskId: string }> = [];
  const searchScopeDryRunExportAsyncDownloadCalls: string[] = [];
  const capabilitiesCalls: number[] = [];
  const advancedStatsCalls: any[] = [];
  const advancedPivotStatsCalls: any[] = [];
  const queueCalls: string[] = [];
  const cancelQueueCalls: string[] = [];
  const deadLetterReplayByFilterCalls: any[] = [];
  const deadLetterClearByFilterCalls: any[] = [];
  const exportTaskId = 'dry-run-export-task-1';
  let exportTaskStatusPollCount = 0;
  let exportTaskDeleted = false;

  const resolveMockExportTaskStatus = () => (exportTaskStatusPollCount >= 3 ? 'COMPLETED' : 'RUNNING');
  const selectMockExportTasks = (statusFilter: string | null) => {
    if (exportTaskDeleted) {
      return [] as Array<{
        taskId: string;
        status: string;
        createdAt: string;
        finishedAt: string | null;
        filename: string | null;
      }>;
    }
    const taskStatus = resolveMockExportTaskStatus();
    const normalizedStatusFilter = statusFilter ? statusFilter.trim().toUpperCase() : null;
    if (normalizedStatusFilter && normalizedStatusFilter !== taskStatus) {
      return [];
    }
    return [{
      taskId: exportTaskId,
      status: taskStatus,
      createdAt: now,
      finishedAt: taskStatus === 'COMPLETED' ? now : null,
      filename: taskStatus === 'COMPLETED' ? 'search-preview-dry-run-mock.csv' : null,
    }];
  };
  const buildMockExportTaskSummary = (statusFilter: string | null) => {
    const counters = {
      total: 0,
      queued: 0,
      running: 0,
      completed: 0,
      cancelled: 0,
      failed: 0,
      terminal: 0,
      active: 0,
    };
    const items = selectMockExportTasks(statusFilter);
    items.forEach((item) => {
      const normalizedStatus = item.status.toUpperCase();
      counters.total += 1;
      if (normalizedStatus === 'QUEUED') counters.queued += 1;
      if (normalizedStatus === 'RUNNING') counters.running += 1;
      if (normalizedStatus === 'COMPLETED') counters.completed += 1;
      if (normalizedStatus === 'CANCELLED') counters.cancelled += 1;
      if (normalizedStatus === 'FAILED') counters.failed += 1;
    });
    counters.terminal = counters.completed + counters.cancelled + counters.failed;
    counters.active = counters.queued + counters.running;
    return counters;
  };

  const json = (body: unknown) => JSON.stringify(body);
  const fulfillJson = (route: any, body: unknown) => route.fulfill({
    status: 200,
    contentType: 'application/json',
    body: json(body),
  });

  await page.route('**/api/v1/**', async (route) => {
    const requestUrl = new URL(route.request().url());
    const pathname = requestUrl.pathname;
    const method = route.request().method();

    if (pathname.endsWith('/folders/roots')) {
      await fulfillJson(route, [
        {
          id: 'root-folder-id',
          name: 'Root',
          path: '/Root',
          folderType: 'SYSTEM',
          parentId: null,
          inheritPermissions: true,
          description: null,
          createdBy: 'admin',
          createdDate: now,
          lastModifiedBy: 'admin',
          lastModifiedDate: now,
        },
      ]);
      return;
    }
    if (pathname.includes('/folders/') && pathname.endsWith('/contents')) {
      await fulfillJson(route, { content: [], totalElements: 0, totalPages: 1, number: 0, size: 50 });
      return;
    }
    if (pathname.endsWith('/favorites/batch/check')) {
      await fulfillJson(route, { favoritedNodeIds: [] });
      return;
    }
    if (pathname.endsWith('/search/suggestions')) {
      await fulfillJson(route, []);
      return;
    }
    if (pathname.endsWith('/search/saved') && method === 'GET') {
      await fulfillJson(route, []);
      return;
    }
    if (pathname.endsWith('/search/diagnostics')) {
      await fulfillJson(route, {
        username: 'admin',
        admin: true,
        readFilterApplied: false,
        authorityCount: 1,
        authoritySample: ['ROLE_ADMIN'],
        note: null,
        generatedAt: now,
      });
      return;
    }
    if (pathname.endsWith('/search/index/stats')) {
      await fulfillJson(route, { indexName: 'ecm_documents', documentCount: 0, searchEnabled: true });
      return;
    }
    if (pathname.endsWith('/search/index/rebuild/status')) {
      await fulfillJson(route, { inProgress: false, documentsIndexed: 0 });
      return;
    }
    if (pathname.endsWith('/search/preview/queue-failed/capabilities') && method === 'GET') {
      capabilitiesCalls.push(Date.now());
      await fulfillJson(route, {
        defaultMaxDocuments: 100,
        maxMaxDocuments: 500,
        scanPageSize: 100,
        scanLimit: 5000,
        defaultWorkerCount: 4,
        maxWorkerCount: 16,
      });
      return;
    }
    if (pathname.endsWith('/search/advanced/stats') && method === 'POST') {
      advancedStatsCalls.push(route.request().postDataJSON());
      await fulfillJson(route, {
        query,
        normalizedQuery: query,
        hasFilters: true,
        totalHits: 120,
        facetFieldCount: 5,
        previewStatusStats: [
          { value: 'FAILED', count: 120 },
          { value: 'READY', count: 20 },
        ],
        mimeTypeStats: [
          { value: 'application/pdf', count: 118 },
          { value: 'text/plain', count: 2 },
        ],
        createdByStats: [
          { value: 'admin', count: 119 },
          { value: 'scanner', count: 1 },
        ],
        fileSizeRangeStats: [{ value: '0-1MB', count: 120 }],
        createdDateRangeStats: [{ value: 'last7d', count: 120 }],
      });
      return;
    }
    if (pathname.endsWith('/search/advanced/stats/pivot') && method === 'POST') {
      advancedPivotStatsCalls.push(route.request().postDataJSON());
      await fulfillJson(route, {
        query,
        normalizedQuery: query,
        hasFilters: true,
        totalHits: 120,
        rowField: 'previewStatus',
        columnField: 'mimeType',
        cells: [
          { rowValue: 'FAILED', columnValue: 'application/pdf', count: 118 },
          { rowValue: 'FAILED', columnValue: 'text/plain', count: 2 },
        ],
      });
      return;
    }
    if (pathname.endsWith('/search/faceted') && method === 'POST') {
      const payload = route.request().postDataJSON() as any;
      const pageNo = Number(payload?.pageable?.page || '0');
      const pageSize = Number(payload?.pageable?.size || '20');
      const previewStatuses = Array.isArray(payload?.filters?.previewStatuses)
        ? payload.filters.previewStatuses.join(',')
        : null;
      searchCalls.push({ page: pageNo, size: pageSize, previewStatus: previewStatuses });

      if (pageSize === 10 && pageNo === 0) {
        await fulfillJson(route, {
          results: {
            content: [
              {
                id: 'doc-page-1',
                name: `${query}-001.bin`,
                mimeType: 'application/pdf',
                fileSize: 1,
                createdBy: 'admin',
                createdDate: now,
                path: '/Root/Documents',
                nodeType: 'DOCUMENT',
                parentId: 'root-folder-id',
                previewStatus: 'FAILED',
                previewFailureCategory: 'TEMPORARY',
                previewFailureReason: 'preview service timeout',
              },
            ],
            totalElements: 1,
          },
          totalHits: 1,
        });
        return;
      }

      if (pageSize === 50 && pageNo === 0) {
        await fulfillJson(route, {
          results: {
            content: [
              {
                id: 'doc-batch-1',
                name: `${query}-batch-1.bin`,
                mimeType: 'application/pdf',
                fileSize: 1,
                createdBy: 'admin',
                createdDate: now,
                path: '/Root/Documents',
                nodeType: 'DOCUMENT',
                parentId: 'root-folder-id',
                previewStatus: 'FAILED',
                previewFailureCategory: 'TEMPORARY',
                previewFailureReason: 'preview service timeout',
              },
              {
                id: 'doc-batch-2',
                name: `${query}-batch-2.bin`,
                mimeType: 'application/pdf',
                fileSize: 1,
                createdBy: 'admin',
                createdDate: now,
                path: '/Root/Documents',
                nodeType: 'DOCUMENT',
                parentId: 'root-folder-id',
                previewStatus: 'FAILED',
                previewFailureCategory: 'TEMPORARY',
                previewFailureReason: 'preview service timeout',
              },
            ],
            totalElements: 120,
          },
          totalHits: 120,
        });
        return;
      }

      if (pageSize === 50 && pageNo === 1) {
        await fulfillJson(route, {
          results: {
            content: [
              {
                id: 'doc-batch-3',
                name: `${query}-batch-3.bin`,
                mimeType: 'application/pdf',
                fileSize: 1,
                createdBy: 'admin',
                createdDate: now,
                path: '/Root/Documents',
                nodeType: 'DOCUMENT',
                parentId: 'root-folder-id',
                previewStatus: 'FAILED',
                previewFailureCategory: 'TEMPORARY',
                previewFailureReason: 'preview service timeout',
              },
            ],
            totalElements: 120,
          },
          totalHits: 120,
        });
        return;
      }

      await fulfillJson(route, {
        results: { content: [], totalElements: 0 },
        totalHits: 0,
      });
      return;
    }
    if (pathname.endsWith('/search') && method === 'GET') {
      const pageNo = Number(requestUrl.searchParams.get('page') || '0');
      const pageSize = Number(requestUrl.searchParams.get('size') || '20');
      const previewStatus = requestUrl.searchParams.get('previewStatus');
      searchCalls.push({ page: pageNo, size: pageSize, previewStatus });

      if (pageSize === 10 && pageNo === 0) {
        await fulfillJson(route, {
          content: [
            {
              id: 'doc-page-1',
              name: `${query}-001.bin`,
              mimeType: 'application/pdf',
              fileSize: 1,
              createdBy: 'admin',
              createdDate: now,
              path: '/Root/Documents',
              nodeType: 'DOCUMENT',
              parentId: 'root-folder-id',
              previewStatus: 'FAILED',
              previewFailureCategory: 'TEMPORARY',
              previewFailureReason: 'preview service timeout',
            },
            {
              id: 'doc-page-2',
              name: `${query}-002.bin`,
              mimeType: 'application/octet-stream',
              fileSize: 1,
              createdBy: 'admin',
              createdDate: now,
              path: '/Root/Documents',
              nodeType: 'DOCUMENT',
              parentId: 'root-folder-id',
              previewStatus: 'FAILED',
              previewFailureCategory: 'UNSUPPORTED',
              previewFailureReason: 'unsupported mime type',
            },
          ],
          totalElements: 2,
        });
        return;
      }

      if (pageSize === 50 && pageNo === 0) {
        await fulfillJson(route, {
          content: [
            {
              id: 'doc-batch-1',
              name: `${query}-batch-1.bin`,
              mimeType: 'application/pdf',
              fileSize: 1,
              createdBy: 'admin',
              createdDate: now,
              path: '/Root/Documents',
              nodeType: 'DOCUMENT',
              parentId: 'root-folder-id',
              previewStatus: 'FAILED',
              previewFailureCategory: 'TEMPORARY',
              previewFailureReason: 'preview service timeout',
            },
            {
              id: 'doc-batch-2',
              name: `${query}-batch-2.bin`,
              mimeType: 'application/pdf',
              fileSize: 1,
              createdBy: 'admin',
              createdDate: now,
              path: '/Root/Documents',
              nodeType: 'DOCUMENT',
              parentId: 'root-folder-id',
              previewStatus: 'FAILED',
              previewFailureCategory: 'TEMPORARY',
              previewFailureReason: 'preview service timeout',
            },
          ],
          totalElements: 120,
        });
        return;
      }

      if (pageSize === 50 && pageNo === 1) {
        await fulfillJson(route, {
          content: [
            {
              id: 'doc-batch-3',
              name: `${query}-batch-3.bin`,
              mimeType: 'application/pdf',
              fileSize: 1,
              createdBy: 'admin',
              createdDate: now,
              path: '/Root/Documents',
              nodeType: 'DOCUMENT',
              parentId: 'root-folder-id',
              previewStatus: 'FAILED',
              previewFailureCategory: 'TEMPORARY',
              previewFailureReason: 'preview service timeout',
            },
          ],
          totalElements: 120,
        });
        return;
      }

      await fulfillJson(route, {
        content: [],
        totalElements: 0,
      });
      return;
    }

    if (pathname.endsWith('/search/preview/queue-failed') && method === 'POST') {
      const payload = route.request().postDataJSON() as any;
      searchScopeBatchCalls.push(payload);
      await fulfillJson(route, {
        query: payload?.query || null,
        reason: payload?.reason || null,
        maxDocuments: payload?.maxDocuments || 200,
        totalCandidates: 120,
        scanned: 120,
        matched: 3,
        scanSkipped: 3,
        truncated: false,
        reasonBreakdown: [
          { reason: payload?.reason || 'preview service timeout', count: 3 },
        ],
        skipBreakdown: [
          { reason: 'NON_RETRYABLE', count: 2 },
          { reason: 'REASON_MISMATCH', count: 1 },
        ],
        requested: 3,
        deduplicated: 3,
        queued: 3,
        skipped: 0,
        failed: 0,
        workerCount: payload?.workerCount || 4,
        results: [
          { documentId: 'doc-batch-1', outcome: 'QUEUED', message: 'queued', previewStatus: 'PROCESSING', attempts: 1, nextAttemptAt: now },
          { documentId: 'doc-batch-2', outcome: 'QUEUED', message: 'queued', previewStatus: 'PROCESSING', attempts: 1, nextAttemptAt: now },
          { documentId: 'doc-batch-3', outcome: 'QUEUED', message: 'queued', previewStatus: 'PROCESSING', attempts: 1, nextAttemptAt: now },
        ],
      });
      return;
    }

    if (pathname.endsWith('/search/preview/queue-failed/dry-run') && method === 'POST') {
      const payload = route.request().postDataJSON() as any;
      searchScopeDryRunCalls.push(payload);
      await fulfillJson(route, {
        query: payload?.query || null,
        reason: payload?.reason || null,
        maxDocuments: payload?.maxDocuments || 200,
        totalCandidates: 120,
        scanned: 120,
        matched: 3,
        scanSkipped: 3,
        truncated: false,
        reasonBreakdown: [
          { reason: 'preview service timeout', count: 3 },
        ],
        skipBreakdown: [
          { reason: 'NON_RETRYABLE', count: 2 },
          { reason: 'REASON_MISMATCH', count: 1 },
        ],
        workerCount: payload?.workerCount || 4,
        sampleCount: 3,
        samples: [
          { documentId: 'doc-batch-1', name: `${query}-batch-1.bin`, previewStatus: 'FAILED', previewFailureReason: 'preview service timeout', previewFailureCategory: 'TEMPORARY' },
          { documentId: 'doc-batch-2', name: `${query}-batch-2.bin`, previewStatus: 'FAILED', previewFailureReason: 'preview service timeout', previewFailureCategory: 'TEMPORARY' },
          { documentId: 'doc-batch-3', name: `${query}-batch-3.bin`, previewStatus: 'FAILED', previewFailureReason: 'preview service timeout', previewFailureCategory: 'TEMPORARY' },
        ],
      });
      return;
    }

    if (pathname.endsWith('/ops/recovery/replay-by-filter') && method === 'POST') {
      const payload = route.request().postDataJSON() as any;
      deadLetterReplayByFilterCalls.push(payload);
      await fulfillJson(route, {
        domain: payload?.domain || 'PREVIEW',
        mode: 'REPLAY_BY_FILTER',
        windowDays: payload?.days ?? 7,
        maxDocuments: payload?.maxDocuments ?? 200,
        totalCandidates: 1,
        scanned: 1,
        matched: 1,
        truncated: false,
        requested: 1,
        deduplicated: 1,
        queued: 1,
        skipped: 0,
        failed: 0,
        results: [
          {
            documentId: 'doc-batch-1',
            jobState: 'QUEUED',
            outcome: 'QUEUED',
            message: 'Preview queued',
            previewStatus: 'FAILED',
            failureCategory: 'TEMPORARY',
            attempts: 0,
            nextAttemptAt: null,
          },
        ],
      });
      return;
    }

    if (pathname.endsWith('/ops/recovery/clear-by-filter') && method === 'POST') {
      const payload = route.request().postDataJSON() as any;
      deadLetterClearByFilterCalls.push(payload);
      await fulfillJson(route, {
        domain: payload?.domain || 'PREVIEW',
        mode: 'CLEAR_BY_FILTER',
        windowDays: payload?.days ?? 7,
        maxDocuments: payload?.maxDocuments ?? 200,
        totalCandidates: 1,
        scanned: 1,
        matched: 1,
        truncated: false,
        requested: 1,
        deduplicated: 1,
        queued: 1,
        skipped: 0,
        failed: 0,
        results: [
          {
            documentId: 'doc-batch-1',
            jobState: 'CLEARED',
            outcome: 'CLEARED',
            message: 'Dead-letter entry cleared',
            previewStatus: 'FAILED',
            failureCategory: 'TEMPORARY',
            attempts: 0,
            nextAttemptAt: null,
          },
        ],
      });
      return;
    }

    if (pathname.endsWith('/search/preview/queue-failed/dry-run/export-async') && method === 'POST') {
      const payload = route.request().postDataJSON() as any;
      searchScopeDryRunExportAsyncStartCalls.push(payload);
      await fulfillJson(route, {
        taskId: exportTaskId,
        status: 'PREPARING',
      });
      return;
    }

    if (pathname.endsWith('/search/preview/queue-failed/dry-run/export-async') && method === 'GET') {
      const statusFilter = requestUrl.searchParams.get('status');
      searchScopeDryRunExportAsyncListCalls.push({
        limit: requestUrl.searchParams.get('limit'),
        status: statusFilter,
      });
      const normalizedStatusFilter = statusFilter ? statusFilter.trim().toUpperCase() : null;
      if (normalizedStatusFilter && !['QUEUED', 'RUNNING', 'COMPLETED', 'CANCELLED', 'FAILED'].includes(normalizedStatusFilter)) {
        await route.fulfill({
          status: 400,
          contentType: 'application/json',
          body: json({ message: `Unknown async export status: ${statusFilter}` }),
        });
        return;
      }
      const items = selectMockExportTasks(normalizedStatusFilter);
      await fulfillJson(route, {
        count: items.length,
        items,
      });
      return;
    }

    if (pathname.endsWith('/search/preview/queue-failed/dry-run/export-async/summary') && method === 'GET') {
      const statusFilter = requestUrl.searchParams.get('status');
      searchScopeDryRunExportAsyncSummaryCalls.push({ status: statusFilter });
      const normalizedStatusFilter = statusFilter ? statusFilter.trim().toUpperCase() : null;
      if (normalizedStatusFilter && !['QUEUED', 'RUNNING', 'COMPLETED', 'CANCELLED', 'FAILED'].includes(normalizedStatusFilter)) {
        await route.fulfill({
          status: 400,
          contentType: 'application/json',
          body: json({ message: `Unknown async export status: ${statusFilter}` }),
        });
        return;
      }
      await fulfillJson(route, buildMockExportTaskSummary(normalizedStatusFilter));
      return;
    }

    if (pathname.endsWith('/search/preview/queue-failed/dry-run/export-async/cleanup') && method === 'POST') {
      const statusFilter = requestUrl.searchParams.get('status');
      searchScopeDryRunExportAsyncCleanupCalls.push({ status: statusFilter });
      const normalizedStatusFilter = statusFilter ? statusFilter.trim().toUpperCase() : null;
      if (normalizedStatusFilter && !['COMPLETED', 'CANCELLED', 'FAILED'].includes(normalizedStatusFilter)) {
        await route.fulfill({
          status: 400,
          contentType: 'application/json',
          body: json({ message: 'status filter only supports terminal states: COMPLETED, CANCELLED, FAILED' }),
        });
        return;
      }
      const currentStatus = resolveMockExportTaskStatus();
      const isTerminal = ['COMPLETED', 'CANCELLED', 'FAILED'].includes(currentStatus);
      let deletedCount = 0;
      if (!exportTaskDeleted && isTerminal && (!normalizedStatusFilter || normalizedStatusFilter === currentStatus)) {
        exportTaskDeleted = true;
        deletedCount = 1;
      }
      await fulfillJson(route, {
        deletedCount,
        remainingCount: exportTaskDeleted ? 0 : 1,
        status: normalizedStatusFilter,
        message: deletedCount > 0
          ? `Deleted ${deletedCount} async dry-run export tasks with status ${normalizedStatusFilter || currentStatus}`
          : 'No async dry-run export tasks matched cleanup filter',
      });
      return;
    }

    if (pathname.endsWith('/search/preview/queue-failed/dry-run/export-async/cancel-active') && method === 'POST') {
      const statusFilter = requestUrl.searchParams.get('status');
      searchScopeDryRunExportAsyncCancelActiveCalls.push({ status: statusFilter });
      const normalizedStatusFilter = statusFilter ? statusFilter.trim().toUpperCase() : null;
      if (normalizedStatusFilter && !['QUEUED', 'RUNNING'].includes(normalizedStatusFilter)) {
        await route.fulfill({
          status: 400,
          contentType: 'application/json',
          body: json({ message: 'status filter only supports active states: QUEUED, RUNNING' }),
        });
        return;
      }
      await fulfillJson(route, {
        cancelledCount: 0,
        remainingActiveCount: 0,
        status: normalizedStatusFilter,
        message: 'No active async dry-run export tasks matched cancel-active filters',
      });
      return;
    }

    const exportAsyncStatusMatch = pathname.match(/\/search\/preview\/queue-failed\/dry-run\/export-async\/([^/]+)$/);
    if (exportAsyncStatusMatch && method === 'GET') {
      const taskId = decodeURIComponent(exportAsyncStatusMatch[1]);
      searchScopeDryRunExportAsyncStatusCalls.push({ taskId });
      exportTaskStatusPollCount += 1;
      const status = exportTaskStatusPollCount === 1
        ? 'PREPARING'
        : (exportTaskStatusPollCount === 2 ? 'RUNNING' : 'COMPLETED');
      await fulfillJson(route, {
        taskId,
        status,
      });
      return;
    }

    const exportAsyncDownloadMatch = pathname.match(/\/search\/preview\/queue-failed\/dry-run\/export-async\/([^/]+)\/download$/);
    if (exportAsyncDownloadMatch && method === 'GET') {
      const taskId = decodeURIComponent(exportAsyncDownloadMatch[1]);
      searchScopeDryRunExportAsyncDownloadCalls.push(taskId);
      await route.fulfill({
        status: 200,
        contentType: 'text/csv',
        body: 'metric,value\nmatched,3\n',
      });
      return;
    }

    if (pathname.includes('/documents/') && pathname.endsWith('/preview/queue/cancel') && method === 'POST') {
      const match = pathname.match(/\/documents\/([^/]+)\/preview\/queue\/cancel$/);
      const documentId = match?.[1] || 'unknown';
      cancelQueueCalls.push(documentId);
      await fulfillJson(route, {
        documentId,
        queueState: 'CANCELLED',
        cancelled: true,
        hadActiveTask: true,
        running: false,
        message: 'Cancelled queued preview task',
      });
      return;
    }

    if (pathname.includes('/documents/') && pathname.endsWith('/preview/queue') && method === 'POST') {
      const match = pathname.match(/\/documents\/([^/]+)\/preview\/queue/);
      const documentId = match?.[1] || 'unknown';
      queueCalls.push(documentId);
      await fulfillJson(route, {
        queued: true,
        message: 'Preview queued',
        attempts: 1,
        nextAttemptAt: now,
      });
      return;
    }

    await route.fulfill({
      status: 404,
      contentType: 'application/json',
      body: json({ message: `Not mocked: ${pathname}` }),
    });
  });

  await page.goto('/search', { waitUntil: 'domcontentloaded' });
  await expect(page.getByRole('heading', { name: /Advanced Search/i })).toBeVisible();

  const input = page.getByLabel('Search query');
  await input.fill(query);
  await input.press('Enter');

  await expect(page.getByText('Preview issues on current page: 2')).toBeVisible({ timeout: 60_000 });
  await expect(page.getByText('Search Stats')).toBeVisible();
  await expect(page.getByText('Total hits 120')).toBeVisible();
  await expect(page.getByText('Status FAILED (120)')).toBeVisible();
  await expect(page.getByText('MIME application/pdf (118)')).toBeVisible();
  await expect(page.getByText('By admin (119)')).toBeVisible();
  await expect(page.getByText('FAILED × application/pdf (118)')).toBeVisible();
  await expect.poll(() => advancedStatsCalls.length, { timeout: 60_000 }).toBe(1);
  await expect.poll(() => advancedPivotStatsCalls.length, { timeout: 60_000 }).toBe(1);
  expect(advancedPivotStatsCalls[0]?.rowField).toBe('previewStatus');
  expect(advancedPivotStatsCalls[0]?.columnField).toBe('mimeType');
  await expect(page.getByRole('button', { name: 'Retry all matched (max 200)' })).toBeVisible();
  await expect(page.getByRole('button', { name: 'Dry-run all matched (max 200)' })).toBeVisible();
  await expect.poll(() => capabilitiesCalls.length, { timeout: 60_000 }).toBeGreaterThan(0);

  await page.getByLabel('Batch workers').click();
  await page.getByRole('option', { name: '8 workers', exact: true }).click();

  await page.getByRole('button', { name: 'Dry-run all matched (max 200)' }).click();
  await expect.poll(() => searchScopeDryRunCalls.length, { timeout: 60_000 }).toBe(1);
  expect(queueCalls.length).toBe(0);

  const searchScopeDryRunPayload = searchScopeDryRunCalls[0];
  expect(searchScopeDryRunPayload?.query).toBe(query);
  expect(searchScopeDryRunPayload?.maxDocuments).toBe(200);
  expect(searchScopeDryRunPayload?.workerCount).toBe(8);
  expect(searchScopeDryRunPayload?.reason).toBeFalsy();
  expect(searchScopeDryRunPayload?.filters?.previewStatuses || []).toContain('FAILED');
  await expect(page.getByText('preview service timeout (3)')).toBeVisible();
  await expect(page.getByText('Skipped diagnostics')).toBeVisible();
  await expect(page.getByText('NON_RETRYABLE (2)')).toBeVisible();
  await expect(page.getByText(/Dry-run all matched.*scanned 120.*skipped 3.*workers 8/)).toBeVisible();
  await page.getByRole('button', { name: 'Export dry-run CSV' }).click();
  await expect.poll(() => searchScopeDryRunExportAsyncStartCalls.length, { timeout: 60_000 }).toBe(1);
  await expect.poll(() => searchScopeDryRunExportAsyncStatusCalls.length, { timeout: 60_000 }).toBe(3);
  await expect.poll(() => searchScopeDryRunExportAsyncDownloadCalls.length, { timeout: 60_000 }).toBe(1);
  await expect.poll(() => searchScopeDryRunExportAsyncListCalls.length, { timeout: 60_000 }).toBeGreaterThan(0);
  await expect.poll(() => searchScopeDryRunExportAsyncSummaryCalls.length, { timeout: 60_000 }).toBeGreaterThan(0);
  expect(searchScopeDryRunExportAsyncStartCalls[0]?.query).toBe(query);
  expect(searchScopeDryRunExportAsyncStartCalls[0]?.maxDocuments).toBe(200);
  expect(searchScopeDryRunExportAsyncStartCalls[0]?.workerCount).toBe(8);
  expect(searchScopeDryRunExportAsyncStartCalls[0]?.reason).toBeFalsy();
  expect(searchScopeDryRunExportAsyncStartCalls[0]?.filters?.previewStatuses || []).toContain('FAILED');
  expect(searchScopeDryRunExportAsyncStatusCalls.every((item) => item.taskId === exportTaskId)).toBeTruthy();
  expect(searchScopeDryRunExportAsyncDownloadCalls[0]).toBe(exportTaskId);
  await expect(page.getByText('Export task summary')).toBeVisible();
  await expect(page.getByText('Completed 1')).toBeVisible();
  const listCallsAfterExport = searchScopeDryRunExportAsyncListCalls.length;
  const summaryCallsAfterExport = searchScopeDryRunExportAsyncSummaryCalls.length;
  await page.getByRole('button', { name: 'Cancel active export tasks' }).click();
  await expect.poll(() => searchScopeDryRunExportAsyncCancelActiveCalls.length, { timeout: 60_000 }).toBe(1);
  expect(searchScopeDryRunExportAsyncCancelActiveCalls[0]?.status).toBeNull();
  await expect.poll(
    () => searchScopeDryRunExportAsyncListCalls.length,
    { timeout: 60_000 }
  ).toBeGreaterThan(listCallsAfterExport);
  await expect.poll(
    () => searchScopeDryRunExportAsyncSummaryCalls.length,
    { timeout: 60_000 }
  ).toBeGreaterThan(summaryCallsAfterExport);

  await page.getByLabel('Export task status filter').click();
  await page.getByRole('option', { name: 'RUNNING' }).click();
  await expect.poll(
    () => searchScopeDryRunExportAsyncListCalls.some((call) => call.status === 'RUNNING'),
    { timeout: 60_000 }
  ).toBeTruthy();
  await page.getByRole('button', { name: 'Cancel active export tasks' }).click();
  await expect.poll(() => searchScopeDryRunExportAsyncCancelActiveCalls.length, { timeout: 60_000 }).toBe(2);
  expect(searchScopeDryRunExportAsyncCancelActiveCalls[1]?.status).toBe('RUNNING');

  await page.getByLabel('Export task status filter').click();
  await page.getByRole('option', { name: 'COMPLETED' }).click();
  await expect.poll(
    () => searchScopeDryRunExportAsyncListCalls.some((call) => call.status === 'COMPLETED'),
    { timeout: 60_000 }
  ).toBeTruthy();
  await expect.poll(
    () => searchScopeDryRunExportAsyncSummaryCalls.some((call) => call.status === 'COMPLETED'),
    { timeout: 60_000 }
  ).toBeTruthy();
  await expect(page.getByText('Recent export tasks (COMPLETED)')).toBeVisible();
  await page.getByRole('button', { name: 'Cleanup tasks' }).click();
  await expect.poll(() => searchScopeDryRunExportAsyncCleanupCalls.length, { timeout: 60_000 }).toBe(1);
  expect(searchScopeDryRunExportAsyncCleanupCalls[0]?.status).toBe('COMPLETED');
  await expect(page.getByText('No export tasks for current filter.')).toBeVisible();
  expect(searchScopeDryRunExportAsyncCancelActiveCalls.some((call) => call.status === null)).toBeTruthy();
  expect(searchScopeDryRunExportAsyncCancelActiveCalls.some((call) => call.status === 'RUNNING')).toBeTruthy();

  await page.getByRole('button', { name: 'Retry all matched for reason preview service timeout' }).click();
  await expect.poll(() => searchScopeBatchCalls.length, { timeout: 60_000 }).toBe(1);
  expect(searchScopeBatchCalls[0]?.reason).toBe('preview service timeout');
  expect(queueCalls.length).toBe(0);

  await page.getByRole('button', { name: 'Replay dead-letter all matched for reason preview service timeout' }).click();
  await expect.poll(() => deadLetterReplayByFilterCalls.length, { timeout: 60_000 }).toBe(1);
  await expect(page.getByText(/Dead-letter replay queued: 1\/1/i)).toBeVisible();

  await page.getByRole('button', { name: 'Clear dead-letter all matched for reason preview service timeout' }).click();
  await expect.poll(() => deadLetterClearByFilterCalls.length, { timeout: 60_000 }).toBe(1);
  await expect(page.getByText(/Dead-letter clear done: 1\/1/i)).toBeVisible();

  await page.getByRole('button', { name: 'Clear dead-letter all matched for non-retryable reason unsupported mime type' }).click();
  await expect.poll(() => deadLetterClearByFilterCalls.length, { timeout: 60_000 }).toBe(2);
  await expect(page.getByText(/Dead-letter clear done: 1\/1 \(unsupported mime type\)/i)).toBeVisible();

  await page.getByRole('button', { name: 'Retry failed previews' }).click();
  await expect.poll(() => queueCalls.length, { timeout: 60_000 }).toBe(1);
  await expect(page.getByRole('button', { name: 'Cancel preview task' })).toBeVisible();
  await page.getByRole('button', { name: 'Cancel preview task' }).click();
  await expect.poll(() => cancelQueueCalls.length, { timeout: 60_000 }).toBe(1);
  await expect(page.getByText(/Queue state: CANCELLED/)).toBeVisible();
  queueCalls.length = 0;

  await page.getByRole('button', { name: 'Retry all matched (max 200)' }).click();

  await expect.poll(() => searchScopeBatchCalls.length, { timeout: 60_000 }).toBe(2);
  expect(queueCalls.length).toBe(0);

  const searchScopePayload = searchScopeBatchCalls[1];
  expect(searchScopePayload?.query).toBe(query);
  expect(searchScopePayload?.maxDocuments).toBe(200);
  expect(searchScopePayload?.workerCount).toBe(8);
  expect(searchScopePayload?.force).toBeFalsy();
  expect(searchScopePayload?.reason).toBeFalsy();
  expect(searchScopePayload?.filters?.previewStatuses || []).toContain('FAILED');
  expect(deadLetterReplayByFilterCalls[0]?.reason).toBe('preview service timeout');
  expect(deadLetterReplayByFilterCalls[0]?.category).toBe('TEMPORARY');
  expect(deadLetterReplayByFilterCalls[0]?.retryable).toBeTruthy();
  expect(deadLetterReplayByFilterCalls[0]?.force).toBeTruthy();
  expect(deadLetterClearByFilterCalls[0]?.reason).toBe('preview service timeout');
  expect(deadLetterClearByFilterCalls[0]?.category).toBe('TEMPORARY');
  expect(deadLetterClearByFilterCalls[0]?.retryable).toBeTruthy();
  expect(deadLetterClearByFilterCalls[1]?.reason).toBe('unsupported mime type');
  expect(deadLetterClearByFilterCalls[1]?.category).toBe('UNSUPPORTED');
  expect(deadLetterClearByFilterCalls[1]?.retryable).toBeFalsy();

  const fullTextSearchCalls = searchCalls.filter((call) => call.size === 10);
  expect(fullTextSearchCalls.length).toBeGreaterThanOrEqual(1);
});

test('Advanced search: async dry-run CSV export can be cancelled (mocked API)', async ({ page }) => {
  test.setTimeout(120_000);

  await seedBypassSessionE2E(page, 'admin', 'e2e-token');

  const now = new Date().toISOString();
  const query = `phase202-preview-export-cancel-${Date.now()}`;
  const exportTaskId = 'dry-run-export-cancel-task-1';
  const searchScopeDryRunCalls: any[] = [];
  const exportStartCalls: any[] = [];
  const exportListCalls: Array<{ limit: string | null; status: string | null }> = [];
  const exportSummaryCalls: Array<{ status: string | null }> = [];
  const exportStatusCalls: string[] = [];
  const exportCancelCalls: string[] = [];
  const exportDownloadCalls: string[] = [];
  const capabilitiesCalls: number[] = [];
  const advancedStatsCalls: any[] = [];
  const advancedPivotStatsCalls: any[] = [];
  let cancelled = false;

  const json = (body: unknown) => JSON.stringify(body);
  const fulfillJson = (route: any, body: unknown) => route.fulfill({
    status: 200,
    contentType: 'application/json',
    body: json(body),
  });

  await page.route('**/api/v1/**', async (route) => {
    const requestUrl = new URL(route.request().url());
    const pathname = requestUrl.pathname;
    const method = route.request().method();

    if (pathname.endsWith('/folders/roots')) {
      await fulfillJson(route, [{
        id: 'root-folder-id',
        name: 'Root',
        path: '/Root',
        folderType: 'SYSTEM',
        createdBy: 'admin',
        createdDate: now,
        lastModifiedBy: 'admin',
        lastModifiedDate: now,
      }]);
      return;
    }
    if (pathname.includes('/folders/') && pathname.endsWith('/contents')) {
      await fulfillJson(route, { content: [], totalElements: 0, totalPages: 1, number: 0, size: 50 });
      return;
    }
    if (pathname.endsWith('/favorites/batch/check')) {
      await fulfillJson(route, { favoritedNodeIds: [] });
      return;
    }
    if (pathname.endsWith('/search/suggestions')) {
      await fulfillJson(route, []);
      return;
    }
    if (pathname.endsWith('/search/saved') && method === 'GET') {
      await fulfillJson(route, []);
      return;
    }
    if (pathname.endsWith('/search/diagnostics')) {
      await fulfillJson(route, {
        username: 'admin',
        admin: true,
        readFilterApplied: false,
        authorityCount: 1,
        authoritySample: ['ROLE_ADMIN'],
        generatedAt: now,
      });
      return;
    }
    if (pathname.endsWith('/search/index/stats')) {
      await fulfillJson(route, { indexName: 'ecm_documents', documentCount: 0, searchEnabled: true });
      return;
    }
    if (pathname.endsWith('/search/index/rebuild/status')) {
      await fulfillJson(route, { inProgress: false, documentsIndexed: 0 });
      return;
    }
    if (pathname.endsWith('/search/preview/queue-failed/capabilities') && method === 'GET') {
      capabilitiesCalls.push(Date.now());
      await fulfillJson(route, {
        defaultMaxDocuments: 100,
        maxMaxDocuments: 500,
        scanPageSize: 100,
        scanLimit: 5000,
        defaultWorkerCount: 4,
        maxWorkerCount: 16,
      });
      return;
    }
    if (pathname.endsWith('/search/advanced/stats') && method === 'POST') {
      advancedStatsCalls.push(route.request().postDataJSON());
      await fulfillJson(route, {
        query,
        normalizedQuery: query,
        hasFilters: true,
        totalHits: 1,
        facetFieldCount: 3,
        previewStatusStats: [{ value: 'FAILED', count: 1 }],
        mimeTypeStats: [{ value: 'application/pdf', count: 1 }],
        createdByStats: [{ value: 'admin', count: 1 }],
        fileSizeRangeStats: [{ value: '0-1MB', count: 1 }],
        createdDateRangeStats: [{ value: 'last7d', count: 1 }],
      });
      return;
    }
    if (pathname.endsWith('/search/advanced/stats/pivot') && method === 'POST') {
      advancedPivotStatsCalls.push(route.request().postDataJSON());
      await fulfillJson(route, {
        query,
        normalizedQuery: query,
        hasFilters: true,
        totalHits: 1,
        rowField: 'previewStatus',
        columnField: 'mimeType',
        cells: [
          { rowValue: 'FAILED', columnValue: 'application/pdf', count: 1 },
        ],
      });
      return;
    }
    if (pathname.endsWith('/search/faceted') && method === 'POST') {
      const payload = route.request().postDataJSON() as any;
      const pageNo = Number(payload?.pageable?.page || '0');
      const pageSize = Number(payload?.pageable?.size || '20');
      await fulfillJson(route, {
        results: {
          content: [
            {
              id: 'doc-export-cancel-1',
              name: `${query}-001.bin`,
              mimeType: 'application/pdf',
              fileSize: 1,
              createdBy: 'admin',
              createdDate: now,
              path: '/Root/Documents',
              nodeType: 'DOCUMENT',
              parentId: 'root-folder-id',
              previewStatus: 'FAILED',
              previewFailureCategory: 'TEMPORARY',
              previewFailureReason: 'preview service timeout',
            },
          ],
          totalElements: pageSize === 10 && pageNo === 0 ? 1 : 0,
        },
        totalHits: pageSize === 10 && pageNo === 0 ? 1 : 0,
      });
      return;
    }
    if (pathname.endsWith('/search') && method === 'GET') {
      await fulfillJson(route, {
        content: [
          {
            id: 'doc-export-cancel-1',
            name: `${query}-001.bin`,
            mimeType: 'application/pdf',
            fileSize: 1,
            createdBy: 'admin',
            createdDate: now,
            path: '/Root/Documents',
            nodeType: 'DOCUMENT',
            parentId: 'root-folder-id',
            previewStatus: 'FAILED',
            previewFailureCategory: 'TEMPORARY',
            previewFailureReason: 'preview service timeout',
          },
        ],
        totalElements: 1,
      });
      return;
    }

    if (pathname.endsWith('/search/preview/queue-failed/dry-run') && method === 'POST') {
      const payload = route.request().postDataJSON() as any;
      searchScopeDryRunCalls.push(payload);
      await fulfillJson(route, {
        query: payload?.query || null,
        reason: payload?.reason || null,
        maxDocuments: payload?.maxDocuments || 200,
        totalCandidates: 1,
        scanned: 1,
        matched: 1,
        scanSkipped: 1,
        truncated: false,
        reasonBreakdown: [{ reason: 'preview service timeout', count: 1 }],
        skipBreakdown: [{ reason: 'NON_RETRYABLE', count: 1 }],
        workerCount: payload?.workerCount || 4,
        sampleCount: 1,
        samples: [{
          documentId: 'doc-export-cancel-1',
          name: `${query}-001.bin`,
          previewStatus: 'FAILED',
          previewFailureReason: 'preview service timeout',
          previewFailureCategory: 'TEMPORARY',
        }],
      });
      return;
    }

    if (pathname.endsWith('/search/preview/queue-failed/dry-run/export-async') && method === 'POST') {
      const payload = route.request().postDataJSON() as any;
      exportStartCalls.push(payload);
      await fulfillJson(route, {
        taskId: exportTaskId,
        status: 'QUEUED',
      });
      return;
    }

    if (pathname.endsWith('/search/preview/queue-failed/dry-run/export-async') && method === 'GET') {
      const statusFilter = requestUrl.searchParams.get('status');
      exportListCalls.push({
        limit: requestUrl.searchParams.get('limit'),
        status: statusFilter,
      });
      const normalizedStatusFilter = statusFilter ? statusFilter.trim().toUpperCase() : null;
      if (normalizedStatusFilter && !['QUEUED', 'RUNNING', 'COMPLETED', 'CANCELLED', 'FAILED'].includes(normalizedStatusFilter)) {
        await route.fulfill({
          status: 400,
          contentType: 'application/json',
          body: json({ message: `Unknown async export status: ${statusFilter}` }),
        });
        return;
      }
      const taskStatus = cancelled ? 'CANCELLED' : 'RUNNING';
      const taskMatches = !normalizedStatusFilter || normalizedStatusFilter === taskStatus;
      await fulfillJson(route, {
        count: taskMatches ? 1 : 0,
        items: taskMatches
          ? [
              {
                taskId: exportTaskId,
                status: taskStatus,
                error: cancelled ? 'Cancelled by user' : null,
                createdAt: now,
                finishedAt: cancelled ? now : null,
                filename: null,
              },
            ]
          : [],
      });
      return;
    }

    if (pathname.endsWith('/search/preview/queue-failed/dry-run/export-async/summary') && method === 'GET') {
      const statusFilter = requestUrl.searchParams.get('status');
      exportSummaryCalls.push({ status: statusFilter });
      const normalizedStatusFilter = statusFilter ? statusFilter.trim().toUpperCase() : null;
      if (normalizedStatusFilter && !['QUEUED', 'RUNNING', 'COMPLETED', 'CANCELLED', 'FAILED'].includes(normalizedStatusFilter)) {
        await route.fulfill({
          status: 400,
          contentType: 'application/json',
          body: json({ message: `Unknown async export status: ${statusFilter}` }),
        });
        return;
      }
      const counters = {
        total: 0,
        queued: 0,
        running: 0,
        completed: 0,
        cancelled: 0,
        failed: 0,
        terminal: 0,
        active: 0,
      };
      const taskStatus = cancelled ? 'CANCELLED' : 'RUNNING';
      const includeTask = !normalizedStatusFilter || normalizedStatusFilter === taskStatus;
      if (includeTask) {
        counters.total = 1;
        if (taskStatus === 'RUNNING') counters.running = 1;
        if (taskStatus === 'CANCELLED') counters.cancelled = 1;
      }
      counters.terminal = counters.completed + counters.cancelled + counters.failed;
      counters.active = counters.queued + counters.running;
      await fulfillJson(route, counters);
      return;
    }

    const exportStatusMatch = pathname.match(/\/search\/preview\/queue-failed\/dry-run\/export-async\/([^/]+)$/);
    if (exportStatusMatch && method === 'GET') {
      const taskId = decodeURIComponent(exportStatusMatch[1]);
      exportStatusCalls.push(taskId);
      await fulfillJson(route, {
        taskId,
        status: cancelled ? 'CANCELLED' : 'RUNNING',
      });
      return;
    }

    const exportCancelMatch = pathname.match(/\/search\/preview\/queue-failed\/dry-run\/export-async\/([^/]+)\/cancel$/);
    if (exportCancelMatch && method === 'POST') {
      const taskId = decodeURIComponent(exportCancelMatch[1]);
      cancelled = true;
      exportCancelCalls.push(taskId);
      await fulfillJson(route, {
        taskId,
        status: 'CANCELLED',
        error: 'Cancelled by user',
      });
      return;
    }

    const exportDownloadMatch = pathname.match(/\/search\/preview\/queue-failed\/dry-run\/export-async\/([^/]+)\/download$/);
    if (exportDownloadMatch && method === 'GET') {
      const taskId = decodeURIComponent(exportDownloadMatch[1]);
      exportDownloadCalls.push(taskId);
      await route.fulfill({
        status: 200,
        contentType: 'text/csv',
        body: 'metric,value\nmatched,1\n',
      });
      return;
    }

    await route.fulfill({
      status: 404,
      contentType: 'application/json',
      body: json({ message: `Not mocked: ${pathname}` }),
    });
  });

  await page.goto('/search', { waitUntil: 'domcontentloaded' });
  await expect(page.getByRole('heading', { name: /Advanced Search/i })).toBeVisible();

  const input = page.getByLabel('Search query');
  await input.fill(query);
  await input.press('Enter');

  await expect(page.getByText('Preview issues on current page: 1')).toBeVisible({ timeout: 60_000 });
  await expect(page.getByText('Search Stats')).toBeVisible();
  await expect(page.getByText('Total hits 1')).toBeVisible();
  await expect(page.getByText('Status FAILED (1)')).toBeVisible();
  await expect(page.getByText('FAILED × application/pdf (1)')).toBeVisible();
  await expect.poll(() => advancedStatsCalls.length, { timeout: 60_000 }).toBe(1);
  await expect.poll(() => advancedPivotStatsCalls.length, { timeout: 60_000 }).toBe(1);
  await expect.poll(() => capabilitiesCalls.length, { timeout: 60_000 }).toBeGreaterThan(0);
  expect(advancedPivotStatsCalls[0]?.rowField).toBe('previewStatus');
  expect(advancedPivotStatsCalls[0]?.columnField).toBe('mimeType');
  await page.getByLabel('Batch workers').click();
  await page.getByRole('option', { name: '6 workers', exact: true }).click();
  await page.getByRole('button', { name: 'Dry-run all matched (max 200)' }).click();
  await expect.poll(() => searchScopeDryRunCalls.length, { timeout: 60_000 }).toBe(1);

  await page.getByRole('button', { name: 'Export dry-run CSV' }).click();
  await expect.poll(() => exportStartCalls.length, { timeout: 60_000 }).toBe(1);
  await expect(page.getByRole('button', { name: 'Cancel export' })).toBeVisible();
  await page.getByRole('button', { name: 'Cancel export' }).click();

  await expect.poll(() => exportCancelCalls.length, { timeout: 60_000 }).toBe(1);
  await expect.poll(() => exportStatusCalls.length, { timeout: 60_000 }).toBeGreaterThan(0);
  await expect.poll(() => exportListCalls.length, { timeout: 60_000 }).toBeGreaterThan(0);
  await expect.poll(() => exportSummaryCalls.length, { timeout: 60_000 }).toBeGreaterThan(0);
  await expect(page.getByText('Export task summary')).toBeVisible();
  await expect(page.getByText('Cancelled 1')).toBeVisible();
  expect(exportStartCalls[0]?.query).toBe(query);
  expect(searchScopeDryRunCalls[0]?.workerCount).toBe(6);
  expect(exportStartCalls[0]?.workerCount).toBe(6);
  expect(exportStartCalls[0]?.filters?.previewStatuses || []).toContain('FAILED');
  expect(exportCancelCalls[0]).toBe(exportTaskId);
  expect(exportDownloadCalls.length).toBe(0);
});
