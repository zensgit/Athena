import { expect, test } from '@playwright/test';
import { seedBypassSessionE2E } from './helpers/login';

test.use({ acceptDownloads: true });

test('Admin audit filters persist in URL and export filename is stable (mocked API)', async ({ page }) => {
  test.setTimeout(120_000);

  await seedBypassSessionE2E(page, 'admin', 'e2e-token');

  const nodeId = '11111111-1111-1111-1111-111111111111';
  const fromValue = '2026-02-01T00:00';
  const toValue = '2026-02-02T00:00';
  const auditAsyncStartedTaskId = 'audit-export-task-started-0001';
  const auditAsyncCompletedTaskId = 'audit-export-task-completed-0001';
  const auditAsyncStartCalls: Array<{
    from: string;
    to: string;
    username: string;
    eventType: string;
    category: string;
    nodeId: string;
    preset: string;
    days: number | null;
  }> = [];
  const auditAsyncListCalls: number[] = [];
  const auditAsyncListStatusCalls: string[] = [];
  const auditAsyncSummaryCalls: string[] = [];
  const auditAsyncCleanupCalls: string[] = [];
  const auditAsyncCancelActiveCalls: string[] = [];
  const auditAsyncStatusCalls: string[] = [];
  const auditAsyncCancelCalls: string[] = [];
  const auditAsyncDownloadCalls: string[] = [];
  const recoveryHistoryExportAsyncSummaryCalls: string[] = [];
  const searchDryRunExportAsyncSummaryCalls: string[] = [];
  const renditionResourcesExportAsyncSummaryCalls: string[] = [];
  const auditExportAsyncTasks = new Map<string, {
    taskId: string;
    status: string;
    error: string | null;
    createdAt: string;
    finishedAt: string | null;
    filename: string | null;
    rowCount: number | null;
  }>();
  auditExportAsyncTasks.set(auditAsyncCompletedTaskId, {
    taskId: auditAsyncCompletedTaskId,
    status: 'COMPLETED',
    error: null,
    createdAt: new Date(Date.now() - 120_000).toISOString(),
    finishedAt: new Date(Date.now() - 90_000).toISOString(),
    filename: 'audit_logs_async_completed.csv',
    rowCount: 1,
  });
  const recoveryHistoryExportAsyncSummary = {
    totalCount: 17,
    queuedCount: 2,
    runningCount: 3,
    completedCount: 10,
    cancelledCount: 1,
    failedCount: 1,
    activeCount: 5,
    terminalCount: 12,
  };
  const searchDryRunExportAsyncSummary = {
    total: 13,
    queued: 1,
    running: 3,
    completed: 7,
    cancelled: 1,
    failed: 1,
    active: 4,
    terminal: 9,
  };
  const renditionResourcesExportAsyncSummary = {
    totalCount: 19,
    queuedCount: 3,
    runningCount: 3,
    completedCount: 11,
    cancelledCount: 1,
    failedCount: 1,
    activeCount: 6,
    terminalCount: 13,
  };
  const asyncExportHealthOverviewExpected = {
    total: 1 + recoveryHistoryExportAsyncSummary.totalCount + searchDryRunExportAsyncSummary.total + renditionResourcesExportAsyncSummary.totalCount,
    active: 0 + recoveryHistoryExportAsyncSummary.activeCount + searchDryRunExportAsyncSummary.active + renditionResourcesExportAsyncSummary.activeCount,
    terminal: 1 + recoveryHistoryExportAsyncSummary.terminalCount + searchDryRunExportAsyncSummary.terminal + renditionResourcesExportAsyncSummary.terminalCount,
  };

  const json = (body: unknown) => JSON.stringify(body);
  const fulfillJson = (route: any, body: unknown) => route.fulfill({
    status: 200,
    contentType: 'application/json',
    body: json(body),
  });
  const isTerminalStatus = (status: string) => ['COMPLETED', 'FAILED', 'CANCELLED'].includes((status || '').toUpperCase());
  await page.route('**/api/v1/**', async (route) => {
    const requestUrl = new URL(route.request().url());
    const pathname = requestUrl.pathname;

    // File browser boot (app root)
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
          createdDate: new Date().toISOString(),
          lastModifiedBy: 'admin',
          lastModifiedDate: new Date().toISOString(),
        },
      ]);
      return;
    }
    if (pathname.includes('/folders/') && pathname.endsWith('/contents')) {
      await fulfillJson(route, { content: [], totalElements: 0, number: 0, size: 50 });
      return;
    }
    if (pathname.endsWith('/favorites/batch/check')) {
      await fulfillJson(route, { favoritedNodeIds: [] });
      return;
    }

    // Admin dashboard bootstrap
    if (pathname.endsWith('/analytics/dashboard')) {
      await fulfillJson(route, {
        summary: {
          totalDocuments: 0,
          totalFolders: 0,
          totalSizeBytes: 0,
          formattedTotalSize: '0 B',
        },
        storage: [],
        activity: [],
        topUsers: [{ username: 'admin', activityCount: 1 }],
      });
      return;
    }
    if (pathname.endsWith('/analytics/audit/recent')) {
      await fulfillJson(route, []);
      return;
    }
    if (pathname.endsWith('/system/license')) {
      await fulfillJson(route, {
        edition: 'Community',
        maxUsers: 0,
        maxStorageGb: 0,
        expirationDate: null,
        features: [],
        valid: true,
      });
      return;
    }
    if (pathname.endsWith('/analytics/audit/retention')) {
      await fulfillJson(route, { retentionDays: 90, expiredLogCount: 0, exportMaxRangeDays: 90 });
      return;
    }
    if (pathname.endsWith('/analytics/audit/report')) {
      await fulfillJson(route, { windowDays: 30, totalEvents: 1, countsByCategory: { NODE: 1 } });
      return;
    }
    if (pathname.endsWith('/analytics/rules/summary')) {
      await fulfillJson(route, null);
      return;
    }
    if (pathname.endsWith('/analytics/rules/recent')) {
      await fulfillJson(route, []);
      return;
    }
    if (pathname.endsWith('/analytics/audit/presets')) {
      await fulfillJson(route, []);
      return;
    }
    if (pathname.endsWith('/analytics/audit/categories')) {
      await fulfillJson(route, [{ category: 'NODE', enabled: true }]);
      return;
    }
    if (pathname.endsWith('/analytics/audit/event-types')) {
      await fulfillJson(route, [{ eventType: 'NODE_CREATED', count: 10 }]);
      return;
    }
    if (pathname.endsWith('/search/saved')) {
      await fulfillJson(route, []);
      return;
    }
    if (pathname.endsWith('/integration/mail/fetch/summary')) {
      await fulfillJson(route, { summary: null, fetchedAt: null });
      return;
    }
    if (pathname.endsWith('/ops/recovery/history/export-async/summary') && route.request().method().toUpperCase() === 'GET') {
      const statusFilter = (requestUrl.searchParams.get('status') || '').trim().toUpperCase();
      recoveryHistoryExportAsyncSummaryCalls.push(statusFilter || 'ALL');
      await fulfillJson(route, recoveryHistoryExportAsyncSummary);
      return;
    }
    if (pathname.endsWith('/search/preview/queue-failed/dry-run/export-async/summary') && route.request().method().toUpperCase() === 'GET') {
      const statusFilter = (requestUrl.searchParams.get('status') || '').trim().toUpperCase();
      searchDryRunExportAsyncSummaryCalls.push(statusFilter || 'ALL');
      await fulfillJson(route, searchDryRunExportAsyncSummary);
      return;
    }
    if (pathname.endsWith('/preview/diagnostics/renditions/resources/export-async/summary') && route.request().method().toUpperCase() === 'GET') {
      const statusFilter = (requestUrl.searchParams.get('status') || '').trim().toUpperCase();
      renditionResourcesExportAsyncSummaryCalls.push(statusFilter || 'ALL');
      await fulfillJson(route, renditionResourcesExportAsyncSummary);
      return;
    }

    // Filtered audit search
    if (pathname.endsWith('/analytics/audit/search')) {
      expect(requestUrl.searchParams.get('username')).toBe('alice');
      expect(requestUrl.searchParams.get('eventType')).toBe('NODE_CREATED');
      expect(requestUrl.searchParams.get('category')).toBe('NODE');
      expect(requestUrl.searchParams.get('nodeId')).toBe(nodeId);
      expect(requestUrl.searchParams.get('from')).toBeTruthy();
      expect(requestUrl.searchParams.get('to')).toBeTruthy();

      await fulfillJson(route, {
        content: [
          {
            id: 'audit-log-1',
            eventType: 'NODE_CREATED',
            nodeName: 'demo.txt',
            username: 'alice',
            eventTime: new Date().toISOString(),
            details: 'Created demo.txt',
          },
        ],
        totalElements: 1,
        totalPages: 1,
        number: 0,
        size: 50,
      });
      return;
    }

    // Export endpoint returns a CSV payload.
    if (pathname.endsWith('/analytics/audit/export-async')) {
      const method = route.request().method().toUpperCase();
      if (method === 'POST') {
        const payload = route.request().postDataJSON() as {
          from?: string;
          to?: string;
          username?: string;
          eventType?: string;
          category?: string;
          nodeId?: string;
          preset?: string;
          days?: number;
        } | null;
        auditAsyncStartCalls.push({
          from: payload?.from || '',
          to: payload?.to || '',
          username: payload?.username || '',
          eventType: payload?.eventType || '',
          category: payload?.category || '',
          nodeId: payload?.nodeId || '',
          preset: payload?.preset || 'custom',
          days: payload?.days ?? null,
        });
        const started = {
          taskId: auditAsyncStartedTaskId,
          status: 'QUEUED',
          error: null,
          createdAt: new Date().toISOString(),
          finishedAt: null,
          filename: null,
          rowCount: null,
        };
        auditExportAsyncTasks.set(started.taskId, started);
        await fulfillJson(route, {
          taskId: started.taskId,
          status: started.status,
          createdAt: started.createdAt,
        });
        return;
      }

      const limit = Number(requestUrl.searchParams.get('limit') || '20');
      const statusFilter = (requestUrl.searchParams.get('status') || '').trim().toUpperCase();
      auditAsyncListCalls.push(limit);
      auditAsyncListStatusCalls.push(statusFilter);
      const items = Array.from(auditExportAsyncTasks.values())
        .filter((task) => !statusFilter || (task.status || '').toUpperCase() === statusFilter)
        .sort((left, right) => (right.createdAt || '').localeCompare(left.createdAt || ''))
        .slice(0, Math.max(1, limit));
      await fulfillJson(route, {
        count: items.length,
        items,
      });
      return;
    }
    if (pathname.endsWith('/analytics/audit/export-async/summary') && route.request().method().toUpperCase() === 'GET') {
      const statusFilter = (requestUrl.searchParams.get('status') || '').trim().toUpperCase();
      auditAsyncSummaryCalls.push(statusFilter || 'ALL');
      const summarySource = statusFilter
        ? new Map(
          Array.from(auditExportAsyncTasks.entries()).filter(([, task]) => (
            (task.status || '').toUpperCase() === statusFilter
          ))
        )
        : auditExportAsyncTasks;
      const items = Array.from(summarySource.values());
      const queued = items.filter((task) => (task.status || '').toUpperCase() === 'QUEUED').length;
      const running = items.filter((task) => (task.status || '').toUpperCase() === 'RUNNING').length;
      const completed = items.filter((task) => (task.status || '').toUpperCase() === 'COMPLETED').length;
      const cancelled = items.filter((task) => (task.status || '').toUpperCase() === 'CANCELLED').length;
      const failed = items.filter((task) => (task.status || '').toUpperCase() === 'FAILED').length;
      await fulfillJson(route, {
        totalCount: items.length,
        queuedCount: queued,
        runningCount: running,
        completedCount: completed,
        cancelledCount: cancelled,
        failedCount: failed,
        activeCount: queued + running,
        terminalCount: completed + cancelled + failed,
      });
      return;
    }
    if (pathname.endsWith('/analytics/audit/export-async/cleanup') && route.request().method().toUpperCase() === 'POST') {
      const statusFilter = (requestUrl.searchParams.get('status') || '').trim().toUpperCase();
      if (statusFilter && !['COMPLETED', 'FAILED', 'CANCELLED'].includes(statusFilter)) {
        await route.fulfill({
          status: 400,
          contentType: 'application/json',
          body: json({ message: `Cleanup only supports terminal statuses, got: ${statusFilter}` }),
        });
        return;
      }
      auditAsyncCleanupCalls.push(statusFilter);
      let deletedCount = 0;
      Array.from(auditExportAsyncTasks.entries()).forEach(([taskId, task]) => {
        const normalized = (task.status || '').toUpperCase();
        if (!isTerminalStatus(normalized)) {
          return;
        }
        if (statusFilter && normalized !== statusFilter) {
          return;
        }
        auditExportAsyncTasks.delete(taskId);
        deletedCount += 1;
      });
      await fulfillJson(route, {
        deletedCount,
        remainingCount: auditExportAsyncTasks.size,
        statusFilter: statusFilter || 'TERMINAL',
        message: deletedCount > 0
          ? `Deleted ${deletedCount} audit async export tasks`
          : 'No audit async export tasks matched cleanup filter',
      });
      return;
    }
    if (pathname.endsWith('/analytics/audit/export-async/cancel-active') && route.request().method().toUpperCase() === 'POST') {
      const statusFilter = (requestUrl.searchParams.get('status') || '').trim().toUpperCase();
      if (statusFilter && !['QUEUED', 'RUNNING'].includes(statusFilter)) {
        await route.fulfill({
          status: 400,
          contentType: 'application/json',
          body: json({ message: `Cancel-active only supports QUEUED/RUNNING, got: ${statusFilter}` }),
        });
        return;
      }
      auditAsyncCancelActiveCalls.push(statusFilter || 'ALL');
      const activeStatuses = new Set(['QUEUED', 'RUNNING']);
      let cancelledCount = 0;
      Array.from(auditExportAsyncTasks.entries()).forEach(([taskId, task]) => {
        const normalized = (task.status || '').toUpperCase();
        if (!activeStatuses.has(normalized)) {
          return;
        }
        if (statusFilter && normalized !== statusFilter) {
          return;
        }
        auditExportAsyncTasks.set(taskId, {
          ...task,
          status: 'CANCELLED',
          error: 'Cancelled by cancel-active',
          finishedAt: new Date().toISOString(),
        });
        cancelledCount += 1;
      });
      const remainingActiveCount = Array.from(auditExportAsyncTasks.values()).filter((task) => {
        const normalized = (task.status || '').toUpperCase();
        return normalized === 'QUEUED' || normalized === 'RUNNING';
      }).length;
      await fulfillJson(route, {
        cancelledCount,
        remainingActiveCount,
        statusFilter: statusFilter || null,
        message: cancelledCount > 0
          ? `Cancelled ${cancelledCount} active async audit export tasks`
          : 'No active async audit export tasks matched cancel-active filter',
      });
      return;
    }
    if (pathname.includes('/analytics/audit/export-async/')) {
      const method = route.request().method().toUpperCase();
      const taskId = decodeURIComponent(pathname.split('/analytics/audit/export-async/')[1].split('/')[0]);
      const task = auditExportAsyncTasks.get(taskId);
      if (!task) {
        await route.fulfill({
          status: 404,
          contentType: 'application/json',
          body: json({ message: `Task not found: ${taskId}` }),
        });
        return;
      }
      if (pathname.endsWith('/cancel') && method === 'POST') {
        auditAsyncCancelCalls.push(taskId);
        const cancelled = {
          ...task,
          status: 'CANCELLED',
          error: 'Cancelled by user',
          finishedAt: new Date().toISOString(),
        };
        auditExportAsyncTasks.set(taskId, cancelled);
        await fulfillJson(route, cancelled);
        return;
      }
      if (pathname.endsWith('/download') && method === 'GET') {
        auditAsyncDownloadCalls.push(taskId);
        await route.fulfill({
          status: 200,
          headers: {
            'Content-Type': 'text/csv',
            'Content-Disposition': `attachment; filename="${task.filename || 'audit_logs_async.csv'}"`,
          },
          body: 'id,eventType,nodeName,username,eventTime,details\n2,NODE_CREATED,demo2.txt,alice,2026-02-01T00:00:00Z,Created demo2.txt\n',
        });
        return;
      }
      if (method === 'GET') {
        auditAsyncStatusCalls.push(taskId);
        await fulfillJson(route, task);
        return;
      }
    }

    if (pathname.endsWith('/analytics/audit/export')) {
      await route.fulfill({
        status: 200,
        headers: {
          'Content-Type': 'text/csv',
          'X-Audit-Export-Count': '1',
        },
        body: 'id,eventType,nodeName,username,eventTime,details\n1,NODE_CREATED,demo.txt,alice,2026-02-01T00:00:00Z,Created demo.txt\n',
      });
      return;
    }

    await route.fulfill({
      status: 404,
      contentType: 'application/json',
      body: json({ message: `Not mocked: ${pathname}` }),
    });
  });

  // When running against a static build server (no SPA rewrite), avoid deep links.
  await page.goto('/', { waitUntil: 'domcontentloaded' });
  await expect(page.getByRole('button', { name: 'Account menu' })).toBeVisible();
  await page.getByRole('button', { name: 'Account menu' }).click();
  await page.getByRole('menuitem', { name: 'Admin Dashboard' }).click();

  await expect(page.getByRole('heading', { name: 'System Dashboard' })).toBeVisible({ timeout: 60_000 });
  const asyncExportHealthOverview = page.getByRole('heading', { name: 'Async Task Health Overview' });
  await expect(asyncExportHealthOverview).toBeVisible();
  await expect(page.getByText(new RegExp(`Total\\s*${asyncExportHealthOverviewExpected.total}`))).toBeVisible();
  await expect(page.getByText(new RegExp(`Active\\s*${asyncExportHealthOverviewExpected.active}`))).toBeVisible();
  await expect(page.getByText(new RegExp(`Terminal\\s*${asyncExportHealthOverviewExpected.terminal}`))).toBeVisible();

  const healthOverviewRefreshButton = page.getByRole('button', { name: 'Refresh async task health overview' });
  await expect(healthOverviewRefreshButton).toBeVisible();
  const beforeRecoverySummaryCalls = recoveryHistoryExportAsyncSummaryCalls.length;
  const beforeSearchSummaryCalls = searchDryRunExportAsyncSummaryCalls.length;
  const beforeRenditionSummaryCalls = renditionResourcesExportAsyncSummaryCalls.length;
  await healthOverviewRefreshButton.click();
  await expect.poll(() => recoveryHistoryExportAsyncSummaryCalls.length).toBeGreaterThan(beforeRecoverySummaryCalls);
  await expect.poll(() => searchDryRunExportAsyncSummaryCalls.length).toBeGreaterThan(beforeSearchSummaryCalls);
  await expect.poll(() => renditionResourcesExportAsyncSummaryCalls.length).toBeGreaterThan(beforeRenditionSummaryCalls);

  await page.getByLabel('User').fill('alice');
  await page.getByLabel('Event Type').fill('NODE_CREATED');

  await page.getByLabel('Category').click();
  await page.getByRole('option', { name: 'Nodes' }).click();

  await page.getByLabel('Node ID').fill(nodeId);
  await page.getByRole('textbox', { name: 'From', exact: true }).fill(fromValue);
  await page.getByRole('textbox', { name: 'To', exact: true }).fill(toValue);

  await page.getByRole('button', { name: 'Filter Logs' }).click();

  await expect(page).toHaveURL(/auditUser=alice/);
  await expect(page).toHaveURL(new RegExp(`auditNodeId=${nodeId}`));
  await expect(page).toHaveURL(/auditFrom=/);
  await expect(page).toHaveURL(/auditTo=/);

  await expect(page.getByText('alice - Node Created').first()).toBeVisible({ timeout: 60_000 });

  const [download] = await Promise.all([
    page.waitForEvent('download'),
    page.getByRole('button', { name: 'Export CSV' }).click(),
  ]);

  const filename = download.suggestedFilename();
  expect(filename).toContain('audit_logs_20260201_to_20260202');
  expect(filename).toContain('user-alice');
  expect(filename).toContain('event-NODE_CREATED');
  expect(filename).toContain('cat-NODE');
  expect(filename).toContain('node-11111111');

  await page.getByRole('button', { name: 'Start audit async export' }).click();
  await expect(page.getByText(`Audit async export task started: ${auditAsyncStartedTaskId}`)).toBeVisible();
  await page.getByRole('button', { name: 'Refresh audit async export tasks' }).click();
  const auditAsyncTaskStatusFilter = page.locator('[aria-labelledby="audit-async-status-filter-label"]');
  await auditAsyncTaskStatusFilter.click();
  await page.getByRole('option', { name: 'Completed' }).click();
  await page.getByRole('button', { name: `Download audit async export task ${auditAsyncCompletedTaskId}` }).click();
  await expect(page.getByText(/Audit async export downloaded:/i)).toBeVisible();
  await page.getByRole('button', { name: 'Cleanup audit async export tasks' }).click();
  await expect(page.getByText(/Deleted 1 audit async export tasks/i)).toBeVisible();
  await auditAsyncTaskStatusFilter.click();
  await page.getByRole('option', { name: 'All statuses' }).click();
  await page.getByRole('button', { name: `Cancel audit async export task ${auditAsyncStartedTaskId}` }).click();
  await expect(page.getByText(`Audit async export task cancelled: ${auditAsyncStartedTaskId}`)).toBeVisible();
  await auditAsyncTaskStatusFilter.click();
  await page.getByRole('option', { name: 'Running' }).click();
  await page.getByRole('button', { name: 'Cancel active audit async export tasks' }).click();
  await expect(page.getByText(/No active async audit export tasks matched cancel-active filter/i)).toBeVisible();

  expect(auditAsyncStartCalls.length).toBeGreaterThan(0);
  expect(auditAsyncStartCalls.some((call) => call.username === 'alice')).toBeTruthy();
  expect(auditAsyncStartCalls.some((call) => call.eventType === 'NODE_CREATED')).toBeTruthy();
  expect(auditAsyncStartCalls.some((call) => call.category === 'NODE')).toBeTruthy();
  expect(auditAsyncStartCalls.some((call) => call.nodeId === nodeId)).toBeTruthy();
  expect(auditAsyncStartCalls.some((call) => Boolean(call.from) && Boolean(call.to))).toBeTruthy();
  expect(auditAsyncListCalls.some((limit) => limit === 10)).toBeTruthy();
  expect(auditAsyncListStatusCalls).toContain('COMPLETED');
  expect(auditAsyncSummaryCalls.length).toBeGreaterThan(0);
  expect(auditAsyncSummaryCalls).toContain('COMPLETED');
  expect(auditAsyncCleanupCalls).toContain('COMPLETED');
  expect(auditAsyncCancelActiveCalls).toContain('RUNNING');
  expect(auditAsyncStatusCalls).toContain(auditAsyncCompletedTaskId);
  expect(auditAsyncCancelCalls).toContain(auditAsyncStartedTaskId);
  expect(auditAsyncDownloadCalls).toContain(auditAsyncCompletedTaskId);
  expect(recoveryHistoryExportAsyncSummaryCalls.length).toBeGreaterThan(0);
  expect(searchDryRunExportAsyncSummaryCalls.length).toBeGreaterThan(0);
  expect(renditionResourcesExportAsyncSummaryCalls.length).toBeGreaterThan(0);
  expect(recoveryHistoryExportAsyncSummaryCalls).toContain('ALL');
  expect(searchDryRunExportAsyncSummaryCalls).toContain('ALL');
  expect(renditionResourcesExportAsyncSummaryCalls).toContain('ALL');
});
