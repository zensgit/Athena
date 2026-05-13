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
  const asyncGovernanceOverviewCalls: string[] = [];
  const recentAsyncTaskDomainCalls: string[] = [];
  const recentAsyncTaskStatusCalls: string[] = [];
  const recentAsyncTaskIncludeAcknowledgedCalls: string[] = [];
  const recentAsyncTaskCancelCalls: string[] = [];
  const recoveryHistoryExportAsyncSummaryCalls: string[] = [];
  const searchDryRunExportAsyncSummaryCalls: string[] = [];
  const renditionResourcesExportAsyncSummaryCalls: string[] = [];
  const propertyEncryptionBackfillJobId = '00000000-0000-0000-0000-000000000010';
  const propertyEncryptionBackfillTaskId = `backfill:${propertyEncryptionBackfillJobId}`;
  let propertyEncryptionBackfillTaskCancellable = true;
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
  const propertyEncryptionAsyncSummary = {
    totalCount: 7,
    queuedCount: 1,
    runningCount: 1,
    completedCount: 4,
    cancelledCount: 0,
    failedCount: 1,
    activeCount: 2,
    terminalCount: 5,
  };
  const asyncExportHealthOverviewExpected = {
    total: 1
      + recoveryHistoryExportAsyncSummary.totalCount
      + searchDryRunExportAsyncSummary.total
      + renditionResourcesExportAsyncSummary.totalCount
      + propertyEncryptionAsyncSummary.totalCount,
    active: 0
      + recoveryHistoryExportAsyncSummary.activeCount
      + searchDryRunExportAsyncSummary.active
      + renditionResourcesExportAsyncSummary.activeCount
      + propertyEncryptionAsyncSummary.activeCount,
    terminal: 1
      + recoveryHistoryExportAsyncSummary.terminalCount
      + searchDryRunExportAsyncSummary.terminal
      + renditionResourcesExportAsyncSummary.terminalCount
      + propertyEncryptionAsyncSummary.terminalCount,
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
    if (pathname.endsWith('/nodes/download/batch-async/summary') && route.request().method().toUpperCase() === 'GET') {
      await fulfillJson(route, {
        totalCount: 0,
        activeCount: 0,
        terminalCount: 0,
        queuedCount: 0,
        runningCount: 0,
        cancelRequestedCount: 0,
        cancelledCount: 0,
        completedCount: 0,
        failedCount: 0,
      });
      return;
    }
    if (pathname.endsWith('/nodes/download/batch-async') && route.request().method().toUpperCase() === 'GET') {
      const maxItems = Number(requestUrl.searchParams.get('maxItems') || requestUrl.searchParams.get('limit') || '10');
      const skipCount = Number(requestUrl.searchParams.get('skipCount') || '0');
      await fulfillJson(route, {
        items: [],
        paging: {
          maxItems,
          skipCount,
          totalItems: 0,
          hasMoreItems: false,
        },
      });
      return;
    }
    if (pathname.endsWith('/analytics/async-governance/tasks') && route.request().method().toUpperCase() === 'GET') {
      const domainFilter = requestUrl.searchParams.get('domain') || 'ALL';
      const statusFilter = requestUrl.searchParams.get('status') || 'ALL';
      recentAsyncTaskDomainCalls.push(domainFilter);
      recentAsyncTaskStatusCalls.push(statusFilter);
      recentAsyncTaskIncludeAcknowledgedCalls.push(requestUrl.searchParams.get('includeAcknowledged') || 'false');
      const propertyEncryptionTask = {
        domainKey: 'propertyencryption',
        domainLabel: 'Property Encryption',
        taskId: propertyEncryptionBackfillTaskId,
        status: 'RUNNING',
        error: null,
        createdAt: new Date(Date.now() - 60_000).toISOString(),
        startedAt: new Date(Date.now() - 45_000).toISOString(),
        updatedAt: new Date(Date.now() - 10_000).toISOString(),
        timeoutAt: null,
        expiresAt: null,
        finishedAt: null,
        filename: null,
        createdBy: 'admin',
        updatedBy: null,
        fingerprint: `propertyencryption:${propertyEncryptionBackfillTaskId}:running`,
        acknowledged: false,
        acknowledgedAt: null,
        cancelUrl: propertyEncryptionBackfillTaskCancellable
          ? `/api/v1/admin/property-encryption/backfill-jobs/${propertyEncryptionBackfillJobId}/cancel`
          : null,
        downloadUrl: null,
        cleanupUrl: null,
        cancellable: propertyEncryptionBackfillTaskCancellable,
        cleanupEligible: false,
        downloadReady: false,
      };
      const items = (
        (domainFilter === 'ALL' || domainFilter === 'propertyencryption')
        && (statusFilter === 'ALL' || statusFilter === 'RUNNING')
      ) ? [propertyEncryptionTask] : [];
      await fulfillJson(route, {
        items,
        totalCount: items.length,
        count: items.length,
        paging: {
          skipCount: 0,
          maxItems: Number(requestUrl.searchParams.get('maxItems') || '10'),
          totalItems: items.length,
          hasMoreItems: false,
        },
        generatedAt: new Date().toISOString(),
      });
      return;
    }
    if (pathname.endsWith(`/admin/property-encryption/backfill-jobs/${propertyEncryptionBackfillJobId}/cancel`) && route.request().method().toUpperCase() === 'POST') {
      recentAsyncTaskCancelCalls.push(propertyEncryptionBackfillJobId);
      propertyEncryptionBackfillTaskCancellable = false;
      await fulfillJson(route, {
        id: propertyEncryptionBackfillJobId,
        status: 'CANCEL_REQUESTED',
      });
      return;
    }
    if (pathname.endsWith('/analytics/async-governance/overview') && route.request().method().toUpperCase() === 'GET') {
      asyncGovernanceOverviewCalls.push('ALL');
      await fulfillJson(route, {
        generatedAt: new Date().toISOString(),
        overallStatus: 'HEALTHY',
        overallRiskLevel: 'MEDIUM',
        totalDomains: 6,
        degradedDomainCount: 0,
        totalCount: asyncExportHealthOverviewExpected.total,
        activeCount: asyncExportHealthOverviewExpected.active,
        terminalCount: asyncExportHealthOverviewExpected.terminal,
        queuedCount: 7,
        runningCount: 10,
        completedCount: 33,
        cancelledCount: 3,
        failedCount: 5,
        timedOutCount: 0,
        expiredCount: 0,
        failureRate: 5 / asyncExportHealthOverviewExpected.total,
        domains: [
          {
            key: 'audit',
            label: 'Audit',
            status: 'HEALTHY',
            riskLevel: 'LOW',
            totalCount: 1,
            activeCount: 0,
            terminalCount: 1,
            queuedCount: 0,
            runningCount: 0,
            completedCount: 1,
            cancelledCount: 0,
            failedCount: 0,
            timedOutCount: 0,
            expiredCount: 0,
            failureRate: 0,
          },
          {
            key: 'ops',
            label: 'Ops Recovery',
            status: 'HEALTHY',
            riskLevel: 'MEDIUM',
            totalCount: recoveryHistoryExportAsyncSummary.totalCount,
            activeCount: recoveryHistoryExportAsyncSummary.activeCount,
            terminalCount: recoveryHistoryExportAsyncSummary.terminalCount,
            queuedCount: recoveryHistoryExportAsyncSummary.queuedCount,
            runningCount: recoveryHistoryExportAsyncSummary.runningCount,
            completedCount: recoveryHistoryExportAsyncSummary.completedCount,
            cancelledCount: recoveryHistoryExportAsyncSummary.cancelledCount,
            failedCount: recoveryHistoryExportAsyncSummary.failedCount,
            timedOutCount: 0,
            expiredCount: 0,
            failureRate: 1 / recoveryHistoryExportAsyncSummary.totalCount,
          },
          {
            key: 'search',
            label: 'Search',
            status: 'HEALTHY',
            riskLevel: 'MEDIUM',
            totalCount: searchDryRunExportAsyncSummary.total,
            activeCount: searchDryRunExportAsyncSummary.active,
            terminalCount: searchDryRunExportAsyncSummary.terminal,
            queuedCount: searchDryRunExportAsyncSummary.queued,
            runningCount: searchDryRunExportAsyncSummary.running,
            completedCount: searchDryRunExportAsyncSummary.completed,
            cancelledCount: searchDryRunExportAsyncSummary.cancelled,
            failedCount: searchDryRunExportAsyncSummary.failed,
            timedOutCount: 0,
            expiredCount: 0,
            failureRate: 1 / searchDryRunExportAsyncSummary.total,
          },
          {
            key: 'preview',
            label: 'Preview',
            status: 'HEALTHY',
            riskLevel: 'MEDIUM',
            totalCount: renditionResourcesExportAsyncSummary.totalCount,
            activeCount: renditionResourcesExportAsyncSummary.activeCount,
            terminalCount: renditionResourcesExportAsyncSummary.terminalCount,
            queuedCount: renditionResourcesExportAsyncSummary.queuedCount,
            runningCount: renditionResourcesExportAsyncSummary.runningCount,
            completedCount: renditionResourcesExportAsyncSummary.completedCount,
            cancelledCount: renditionResourcesExportAsyncSummary.cancelledCount,
            failedCount: renditionResourcesExportAsyncSummary.failedCount,
            timedOutCount: 0,
            expiredCount: 0,
            failureRate: 1 / renditionResourcesExportAsyncSummary.totalCount,
          },
          {
            key: 'batchdownload',
            label: 'Batch Download',
            status: 'HEALTHY',
            riskLevel: 'LOW',
            totalCount: 0,
            activeCount: 0,
            terminalCount: 0,
            queuedCount: 0,
            runningCount: 0,
            completedCount: 0,
            cancelledCount: 0,
            failedCount: 0,
            timedOutCount: 0,
            expiredCount: 0,
            failureRate: 0,
          },
          {
            key: 'propertyencryption',
            label: 'Property Encryption',
            status: 'HEALTHY',
            riskLevel: 'MEDIUM',
            totalCount: propertyEncryptionAsyncSummary.totalCount,
            activeCount: propertyEncryptionAsyncSummary.activeCount,
            terminalCount: propertyEncryptionAsyncSummary.terminalCount,
            queuedCount: propertyEncryptionAsyncSummary.queuedCount,
            runningCount: propertyEncryptionAsyncSummary.runningCount,
            completedCount: propertyEncryptionAsyncSummary.completedCount,
            cancelledCount: propertyEncryptionAsyncSummary.cancelledCount,
            failedCount: propertyEncryptionAsyncSummary.failedCount,
            timedOutCount: 0,
            expiredCount: 0,
            failureRate: 1 / propertyEncryptionAsyncSummary.totalCount,
          },
        ],
      });
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

  await page.goto('/admin?asyncTaskDomain=propertyencryption', { waitUntil: 'domcontentloaded' });

  await expect(page.getByRole('heading', { name: 'System Dashboard' })).toBeVisible({ timeout: 60_000 });
  await expect(page).toHaveURL(/asyncTaskDomain=propertyencryption/);
  await expect(page.getByText('Domain Property Encryption')).toBeVisible();
  await expect.poll(() => recentAsyncTaskDomainCalls.includes('propertyencryption')).toBeTruthy();
  const recentAsyncTaskStatusFilter = page.locator(
    '[role="combobox"][aria-label="Recent async task status filter"]'
  );
  await recentAsyncTaskStatusFilter.click();
  await page.getByRole('option', { name: 'Running' }).click();
  await expect(page).toHaveURL(/asyncTaskStatus=RUNNING/);
  await expect(page.getByText('Status RUNNING')).toBeVisible();
  await expect.poll(() => recentAsyncTaskStatusCalls.includes('RUNNING')).toBeTruthy();
  const recentAsyncTaskTable = page.getByRole('table', { name: 'Recent async task list' });
  const propertyEncryptionRecentTaskRow = recentAsyncTaskTable.getByRole('row', {
    name: new RegExp(`Property Encryption\\s+propertyencryption\\s+RUNNING\\s+${propertyEncryptionBackfillTaskId}`),
  });
  await expect(propertyEncryptionRecentTaskRow).toBeVisible();
  await propertyEncryptionRecentTaskRow.getByRole('button', { name: 'Cancel' }).click();
  await expect(page.getByText(`Async task updated: ${propertyEncryptionBackfillTaskId}`)).toBeVisible();
  await expect.poll(() => recentAsyncTaskCancelCalls.includes(propertyEncryptionBackfillJobId)).toBeTruthy();
  await expect(propertyEncryptionRecentTaskRow.getByRole('button', { name: 'Cancel' })).toHaveCount(0);
  await page.getByLabel('Show acknowledged async tasks').check();
  await expect(page).toHaveURL(/asyncTaskIncludeAcknowledged=true/);
  await expect(page.getByText('Including acknowledged')).toBeVisible();
  await expect.poll(() => recentAsyncTaskIncludeAcknowledgedCalls.includes('true')).toBeTruthy();

  const asyncExportHealthOverview = page.getByRole('heading', { name: 'Async Task Health Overview' });
  await expect(asyncExportHealthOverview).toBeVisible();
  await expect(page.getByText(new RegExp(`Total\\s*${asyncExportHealthOverviewExpected.total}`))).toBeVisible();
  await expect(page.getByText(new RegExp(`Active\\s*${asyncExportHealthOverviewExpected.active}`))).toBeVisible();
  await expect(page.getByText(new RegExp(`Terminal\\s*${asyncExportHealthOverviewExpected.terminal}`))).toBeVisible();
  const asyncTaskHealthTable = page.getByRole('table', { name: 'Async task health overview' });
  const propertyEncryptionHealthRow = asyncTaskHealthTable.getByRole('row', { name: /Property Encryption/ });
  await expect(propertyEncryptionHealthRow).toBeVisible();
  await expect(propertyEncryptionHealthRow).toHaveText(/Property EncryptionhealthyMEDIUM72541000/);

  const healthOverviewRefreshButton = page.getByRole('button', { name: 'Refresh async task health overview' });
  await expect(healthOverviewRefreshButton).toBeVisible();
  const beforeAsyncGovernanceOverviewCalls = asyncGovernanceOverviewCalls.length;
  await healthOverviewRefreshButton.click();
  await expect.poll(() => asyncGovernanceOverviewCalls.length).toBeGreaterThan(beforeAsyncGovernanceOverviewCalls);

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
  const auditAsyncTaskStatusFilter = page.locator(
    '[role="combobox"][aria-label="Audit async task status filter"]'
  );
  const dismissVisibleToasts = async () => {
    const closeButtons = page.locator('.Toastify__close-button');
    const closeButtonCount = await closeButtons.count();
    for (let index = 0; index < closeButtonCount; index += 1) {
      await closeButtons.first().click({ force: true }).catch(() => undefined);
    }
  };
  const selectAuditAsyncTaskStatus = async (name: string, expectedStatus: string, optionValue = expectedStatus) => {
    const callsBeforeSelection = auditAsyncListStatusCalls.length;
    await dismissVisibleToasts();
    await auditAsyncTaskStatusFilter.click();
    const statusListbox = page.locator('[role="listbox"]').last();
    await expect(statusListbox).toBeVisible();
    const option = statusListbox.locator(`[role="option"][data-value="${optionValue || 'ALL'}"]`);
    await expect(option).toContainText(name);
    await option.focus();
    await page.keyboard.press('Enter');
    await expect(auditAsyncTaskStatusFilter).toContainText(name);
    await expect.poll(
      () => auditAsyncListStatusCalls.slice(callsBeforeSelection),
      { timeout: 5_000 }
    ).toContain(expectedStatus);
  };
  await selectAuditAsyncTaskStatus('Completed', 'COMPLETED');
  await page.getByRole('button', { name: `Download audit async export task ${auditAsyncCompletedTaskId}` }).click();
  await expect(page.getByText(/Audit async export downloaded:/i)).toBeVisible();
  await page.getByRole('button', { name: 'Cleanup audit async export tasks' }).click();
  await expect(page.getByText(/Deleted 1 audit async export tasks/i)).toBeVisible();
  await selectAuditAsyncTaskStatus('All statuses', '', 'ALL');
  await page.getByRole('button', { name: `Cancel audit async export task ${auditAsyncStartedTaskId}` }).click();
  await expect(page.getByText(`Audit async export task cancelled: ${auditAsyncStartedTaskId}`)).toBeVisible();
  await selectAuditAsyncTaskStatus('Running', 'RUNNING');
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
  expect(asyncGovernanceOverviewCalls.length).toBeGreaterThan(0);
  expect(asyncGovernanceOverviewCalls).toContain('ALL');
  expect(recentAsyncTaskDomainCalls).toContain('propertyencryption');
  expect(recentAsyncTaskStatusCalls).toContain('RUNNING');
  expect(recentAsyncTaskIncludeAcknowledgedCalls).toContain('true');
  expect(recentAsyncTaskCancelCalls).toContain(propertyEncryptionBackfillJobId);
  expect(recoveryHistoryExportAsyncSummaryCalls).toHaveLength(0);
  expect(searchDryRunExportAsyncSummaryCalls).toHaveLength(0);
  expect(renditionResourcesExportAsyncSummaryCalls).toHaveLength(0);
});
