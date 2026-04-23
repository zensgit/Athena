import { expect, test } from '@playwright/test';
import { seedBypassSessionE2E } from './helpers/login';

const now = '2026-04-21T09:00:00Z';
const rootFolderId = '11111111-1111-1111-1111-111111111111';
const deliverablePresetId = '22222222-2222-2222-2222-222222222222';
const summaryOnlyPresetId = '33333333-3333-3333-3333-333333333333';
const deliveryFolderId = '44444444-4444-4444-4444-444444444444';
const summaryMixPresetId = '77777777-7777-7777-7777-777777777777';
const exportedCsvBody = 'family,currentCount,previousCount,delta\nDECLARED,5,2,3\n';
const nowTimestamp = Date.parse(now);

test('RM report preset scheduled delivery flow works end-to-end (mocked API)', async ({ page }) => {
  test.setTimeout(120_000);

  await seedBypassSessionE2E(page, 'admin', 'e2e-token');

  const json = (body: unknown) => JSON.stringify(body);
  const fulfillJson = (route: any, body: unknown, status = 200) =>
    route.fulfill({
      status,
      contentType: 'application/json',
      body: json(body),
    });

  const reportPresets = [
    {
      id: deliverablePresetId,
      owner: 'admin',
      name: 'Weekly Family Report',
      description: 'CSV capable preset',
      kind: 'ACTIVITY_FAMILY_REPORT',
      params: {
        from: '2026-04-14T00:00:00',
        to: '2026-04-20T23:59:59',
      },
      createdDate: now,
      lastModifiedDate: now,
    },
    {
      id: summaryOnlyPresetId,
      owner: 'admin',
      name: 'Weekly Family Highlights',
      description: 'Summary-only preset',
      kind: 'ACTIVITY_FAMILY_HIGHLIGHTS',
      params: {
        windowDays: 7,
      },
      createdDate: now,
      lastModifiedDate: now,
    },
    {
      id: summaryMixPresetId,
      owner: 'admin',
      name: 'Weekly Family Mix',
      description: 'Summary-only preset',
      kind: 'ACTIVITY_FAMILY_MIX',
      params: {
        days: 28,
      },
      createdDate: now,
      lastModifiedDate: now,
    },
  ];

  const presetById = new Map(reportPresets.map((preset) => [preset.id, preset]));
  const seededFailedExecution = {
    id: '99999999-9999-9999-9999-999999999999',
    presetId: summaryMixPresetId,
    presetName: 'Weekly Family Mix',
    presetKind: 'ACTIVITY_FAMILY_MIX',
    triggerType: 'SCHEDULED',
    status: 'FAILED',
    filename: null,
    targetFolderId: deliveryFolderId,
    documentId: null,
    message: 'Delivery failed',
    startedAt: '2026-04-20T12:00:00Z',
    finishedAt: '2026-04-20T12:00:05Z',
    durationMs: 5000,
  };

  const scheduleState = new Map<string, any>([
    [deliverablePresetId, {
      presetId: deliverablePresetId,
      enabled: false,
      cronExpression: null,
      timezone: 'UTC',
      deliveryFolderId: null,
      nextRunAt: null,
      lastRunAt: null,
      lastExecution: null,
    }],
    [summaryOnlyPresetId, {
      presetId: summaryOnlyPresetId,
      enabled: false,
      cronExpression: null,
      timezone: 'UTC',
      deliveryFolderId: null,
      nextRunAt: null,
      lastRunAt: null,
      lastExecution: null,
    }],
  ]);

  const executionState = new Map<string, any[]>([
    [deliverablePresetId, []],
    [summaryOnlyPresetId, []],
  ]);

  const withScheduleMetadata = (preset: (typeof reportPresets)[number]) => {
    const schedule = scheduleState.get(preset.id);
    return {
      ...preset,
      scheduleEnabled: Boolean(schedule?.enabled),
      deliveryFolderId: schedule?.deliveryFolderId ?? null,
      nextRunAt: schedule?.nextRunAt ?? null,
      lastRunAt: schedule?.lastRunAt ?? null,
    };
  };

  const buildLedgerEntries = () => {
    const dynamicEntries = Array.from(executionState.entries()).flatMap(([presetId, executions]) => {
      const preset = presetById.get(presetId);
      return executions.map((execution) => ({
        ...execution,
        presetName: execution.presetName ?? preset?.name ?? presetId,
        presetKind: execution.presetKind ?? preset?.kind ?? null,
      }));
    });

    return [seededFailedExecution, ...dynamicEntries].sort((left, right) =>
      Date.parse(right.startedAt) - Date.parse(left.startedAt)
    );
  };

  const filterLedgerEntries = (entries: any[], url: URL) => {
    const presetId = url.searchParams.get('presetId') || '';
    const status = url.searchParams.get('status') || '';
    const triggerType = url.searchParams.get('triggerType') || '';
    const from = url.searchParams.get('from');
    const to = url.searchParams.get('to');

    return entries.filter((entry) => {
      if (presetId && entry.presetId !== presetId) return false;
      if (status && entry.status !== status) return false;
      if (triggerType && entry.triggerType !== triggerType) return false;
      if (from && Date.parse(entry.startedAt) < Date.parse(from)) return false;
      if (to && Date.parse(entry.startedAt) > Date.parse(to)) return false;
      return true;
    });
  };

  const buildTelemetry = () => {
    const entries = buildLedgerEntries();
    const last24hFloor = nowTimestamp - 24 * 60 * 60 * 1000;
    const last24hEntries = entries.filter((entry) => Date.parse(entry.finishedAt) >= last24hFloor);
    const lastExecutionAt = entries.length > 0
      ? entries.reduce((latest, entry) => (!latest || Date.parse(entry.finishedAt) > Date.parse(latest) ? entry.finishedAt : latest), null as string | null)
      : null;

    return {
      scheduleEnabledCount: Array.from(scheduleState.values()).filter((status) => status.enabled).length,
      duePresetCount: Array.from(scheduleState.values()).filter((status) =>
        status.enabled && status.nextRunAt && Date.parse(status.nextRunAt) <= nowTimestamp
      ).length,
      last24hSuccessCount: last24hEntries.filter((entry) => entry.status === 'SUCCESS').length,
      last24hFailedCount: last24hEntries.filter((entry) => entry.status === 'FAILED').length,
      lastExecutionAt,
      generatedAt: now,
    };
  };

  let scheduleGetCount = 0;
  let executionsGetCount = 0;
  let lastScheduleUpdate: any = null;
  let lastDeliveredPresetId: string | null = null;
  let activityFamilyReportCsvRequestCount = 0;
  let lastActivityFamilyReportCsvQuery: Record<string, string> | null = null;
  let ledgerExportRequestCount = 0;
  let lastLedgerExportQuery: Record<string, string> | null = null;

  await page.route('**/api/v1/**', async (route) => {
    const request = route.request();
    const url = new URL(request.url());
    const pathname = url.pathname;
    const method = request.method().toUpperCase();

    if (pathname.endsWith('/folders/roots')) {
      await fulfillJson(route, [
        {
          id: rootFolderId,
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
      const folderId = pathname.split('/').slice(-2)[0];
      await fulfillJson(route, {
        content: folderId === rootFolderId ? [
          {
            id: deliveryFolderId,
            name: 'Delivery Target',
            path: '/Root/Delivery Target',
            nodeType: 'FOLDER',
            createdBy: 'admin',
            createdDate: now,
            lastModifiedBy: 'admin',
            lastModifiedDate: now,
          },
        ] : [],
        totalElements: 0,
        totalPages: 1,
        number: 0,
        size: 50,
      });
      return;
    }

    if (pathname.endsWith('/favorites/batch/check')) {
      await fulfillJson(route, { favoritedNodeIds: [] });
      return;
    }

    if (pathname.endsWith('/tags')) {
      await fulfillJson(route, []);
      return;
    }

    if (pathname.endsWith('/records/summary')) {
      await fulfillJson(route, {
        declaredRecordCount: 0,
        filePlanCount: 0,
        recordCategoryCount: 0,
        uncategorizedRecordCount: 0,
        outsideFilePlanRecordCount: 0,
        categoryBreakdown: [],
        filePlanBreakdown: [],
      });
      return;
    }

    if (pathname.endsWith('/records') && method === 'GET') {
      await fulfillJson(route, []);
      return;
    }

    if (pathname.endsWith('/records/file-plans')) {
      await fulfillJson(route, []);
      return;
    }

    if (pathname.endsWith('/records/categories')) {
      await fulfillJson(route, []);
      return;
    }

    if (pathname.endsWith('/records/audit')) {
      await fulfillJson(route, {
        content: [],
        totalElements: 0,
        totalPages: 0,
        number: 0,
        size: Number(url.searchParams.get('size') || '10'),
      });
      return;
    }

    if (pathname.endsWith('/records/operations')) {
      await fulfillJson(route, {
        governedImportJobCount: 0,
        activeGovernedImportJobCount: 0,
        failedGovernedImportJobCount: 0,
        governedTransferJobCount: 0,
        activeGovernedTransferJobCount: 0,
        failedGovernedTransferJobCount: 0,
        importStatusBreakdown: [],
        transferStatusBreakdown: [],
        importGovernanceReasonBreakdown: [],
        transferGovernanceReasonBreakdown: [],
        recentImportJobs: [],
        recentTransferJobs: [],
      });
      return;
    }

    if (pathname.endsWith('/records/activity-timeline')) {
      await fulfillJson(route, { days: 14, points: [] });
      return;
    }

    if (pathname.endsWith('/records/activity-highlights')) {
      await fulfillJson(route, {
        windowDays: 7,
        currentWindow: {
          fromDay: '2026-04-14',
          toDay: '2026-04-20',
          activeDayCount: 0,
          declaredCount: 0,
          undeclaredCount: 0,
          categoryAssignedCount: 0,
          governanceChangeCount: 0,
          totalCount: 0,
        },
        previousWindow: {
          fromDay: '2026-04-07',
          toDay: '2026-04-13',
          activeDayCount: 0,
          declaredCount: 0,
          undeclaredCount: 0,
          categoryAssignedCount: 0,
          governanceChangeCount: 0,
          totalCount: 0,
        },
        busiestDay: null,
      });
      return;
    }

    if (pathname.endsWith('/records/activity-breakdown')) {
      await fulfillJson(route, { days: 28, bucketDays: 7, buckets: [] });
      return;
    }

    if (pathname.endsWith('/records/activity-contributors')) {
      await fulfillJson(route, { days: 28, limit: 5, contributors: [] });
      return;
    }

    if (pathname.endsWith('/records/activity-contributor-family-trend')) {
      await fulfillJson(route, { days: 28, bucketDays: 7, limit: 5, trackedContributors: [], buckets: [] });
      return;
    }

    if (pathname.endsWith('/records/activity-contributor-family-highlights')) {
      await fulfillJson(route, {
        windowDays: 7,
        limit: 5,
        currentWindow: { fromDay: '2026-04-14', toDay: '2026-04-20' },
        previousWindow: { fromDay: '2026-04-07', toDay: '2026-04-13' },
        contributors: [],
      });
      return;
    }

    if (pathname.endsWith('/records/activity-contributor-event-type-highlights')) {
      await fulfillJson(route, {
        windowDays: 7,
        limit: 5,
        eventTypeLimit: 3,
        currentWindow: { fromDay: '2026-04-14', toDay: '2026-04-20' },
        previousWindow: { fromDay: '2026-04-07', toDay: '2026-04-13' },
        contributors: [],
      });
      return;
    }

    if (pathname.endsWith('/records/activity-contributor-event-type-trend')) {
      await fulfillJson(route, {
        days: 28,
        bucketDays: 7,
        limit: 5,
        eventTypeLimit: 3,
        trackedContributors: [],
        buckets: [],
      });
      return;
    }

    if (pathname.endsWith('/records/activity-event-types')) {
      await fulfillJson(route, { days: 28, limit: 8, eventTypes: [] });
      return;
    }

    if (pathname.endsWith('/records/activity-families')) {
      await fulfillJson(route, { days: 28, totalCount: 0, families: [] });
      return;
    }

    if (pathname.endsWith('/records/activity-family-report')) {
      if (url.searchParams.get('format') === 'csv') {
        activityFamilyReportCsvRequestCount += 1;
        lastActivityFamilyReportCsvQuery = Object.fromEntries(url.searchParams.entries());
        await route.fulfill({
          status: 200,
          contentType: 'text/csv; charset=UTF-8',
          body: exportedCsvBody,
        });
        return;
      }
      await fulfillJson(route, {
        currentWindow: {
          from: '2026-04-14T00:00:00',
          to: '2026-04-20T23:59:59',
        },
        previousWindow: {
          from: '2026-04-07T00:00:00',
          to: '2026-04-13T23:59:59',
        },
        eventTypeLimit: 5,
        contributorLimit: 5,
        currentTotalCount: 0,
        previousTotalCount: 0,
        families: [],
      });
      return;
    }

    if (pathname.endsWith('/records/activity-family-highlights')) {
      await fulfillJson(route, {
        windowDays: 7,
        currentWindow: { fromDay: '2026-04-14', toDay: '2026-04-20' },
        previousWindow: { fromDay: '2026-04-07', toDay: '2026-04-13' },
        families: [],
      });
      return;
    }

    if (pathname.endsWith('/records/report-presets') && method === 'GET') {
      await fulfillJson(route, reportPresets.map(withScheduleMetadata));
      return;
    }

    if (pathname.endsWith('/records/report-presets/telemetry') && method === 'GET') {
      await fulfillJson(route, buildTelemetry());
      return;
    }

    if (pathname.endsWith('/records/report-presets/executions') && method === 'GET') {
      const filtered = filterLedgerEntries(buildLedgerEntries(), url);
      const size = Number(url.searchParams.get('size') || '10');
      const pageIndex = Number(url.searchParams.get('page') || '0');
      const start = pageIndex * size;
      const content = filtered.slice(start, start + size);
      await fulfillJson(route, {
        content,
        page: pageIndex,
        size,
        totalElements: filtered.length,
        totalPages: filtered.length === 0 ? 0 : Math.ceil(filtered.length / size),
        first: pageIndex === 0,
        last: start + size >= filtered.length,
      });
      return;
    }

    if (pathname.endsWith('/records/report-presets/executions/export') && method === 'GET') {
      ledgerExportRequestCount += 1;
      lastLedgerExportQuery = Object.fromEntries(url.searchParams.entries());
      await route.fulfill({
        status: 200,
        contentType: 'text/csv; charset=UTF-8',
        body: [
          'presetName,presetKind,triggerType,status,filename,message',
          'Weekly Family Mix,ACTIVITY_FAMILY_MIX,SCHEDULED,FAILED,,Delivery failed',
        ].join('\n'),
      });
      return;
    }

    const scheduleMatch = pathname.match(/\/api\/v1\/records\/report-presets\/([^/]+)\/schedule$/);
    if (scheduleMatch && method === 'GET') {
      scheduleGetCount += 1;
      await fulfillJson(route, scheduleState.get(scheduleMatch[1]));
      return;
    }

    if (scheduleMatch && method === 'PUT') {
      const presetId = scheduleMatch[1];
      const body = JSON.parse(request.postData() || '{}');
      const nextStatus = {
        ...scheduleState.get(presetId),
        enabled: body.enabled,
        cronExpression: body.enabled ? body.cronExpression : null,
        timezone: body.timezone ?? 'UTC',
        deliveryFolderId: body.deliveryFolderId ?? null,
        nextRunAt: body.enabled
          ? (presetId === deliverablePresetId ? '2026-04-21T08:00:00Z' : '2099-04-22T09:00:00Z')
          : null,
      };
      lastScheduleUpdate = body;
      scheduleState.set(presetId, nextStatus);
      await fulfillJson(route, nextStatus);
      return;
    }

    const executionsMatch = pathname.match(/\/api\/v1\/records\/report-presets\/([^/]+)\/executions$/);
    if (executionsMatch && method === 'GET') {
      executionsGetCount += 1;
      const presetId = executionsMatch[1];
      const limit = Number(url.searchParams.get('limit') || '20');
      await fulfillJson(route, (executionState.get(presetId) || []).slice(0, limit));
      return;
    }

    const deliverMatch = pathname.match(/\/api\/v1\/records\/report-presets\/([^/]+)\/deliver$/);
    if (deliverMatch && method === 'POST') {
      const presetId = deliverMatch[1];
      lastDeliveredPresetId = presetId;
      const execution = {
        id: '55555555-5555-5555-5555-555555555555',
        presetId,
        triggerType: 'MANUAL',
        status: 'SUCCESS',
        filename: 'weekly-family-report-20260421.csv',
        targetFolderId: deliveryFolderId,
        documentId: '66666666-6666-6666-6666-666666666666',
        message: 'Delivered successfully',
        startedAt: '2026-04-21T09:00:00',
        finishedAt: '2026-04-21T09:00:01',
        durationMs: 1000,
      };
      executionState.set(presetId, [execution, ...(executionState.get(presetId) || [])]);
      scheduleState.set(presetId, {
        ...scheduleState.get(presetId),
        lastRunAt: execution.finishedAt,
        lastExecution: execution,
      });
      await fulfillJson(route, execution);
      return;
    }

    await route.fulfill({
      status: 404,
      contentType: 'application/json',
      body: json({ message: `Not mocked: ${method} ${pathname}` }),
    });
  });

  await page.goto('/admin/records-management', { waitUntil: 'domcontentloaded' });

  await expect(page.getByRole('heading', { name: 'Records Management' })).toBeVisible();

  const deliverableRow = page.locator('tr', { has: page.getByText('Weekly Family Report') }).first();
  const summaryOnlyRow = page.locator('tr', { has: page.getByText('Weekly Family Highlights') }).first();
  const summaryMixRow = page.locator('tr', { has: page.getByText('Weekly Family Mix') }).first();

  await expect(deliverableRow.getByRole('button', { name: 'Schedule' })).toBeVisible();
  await expect(deliverableRow.getByRole('button', { name: 'Export CSV' })).toBeVisible();
  await expect(summaryOnlyRow.getByRole('button', { name: 'Apply to audit' })).toBeVisible();
  await expect(summaryOnlyRow.getByRole('button', { name: 'Schedule' })).toBeVisible();
  await expect(summaryOnlyRow.getByRole('button', { name: 'Export CSV' })).toBeVisible();
  await expect(summaryMixRow.getByRole('button', { name: 'Schedule' })).toBeVisible();
  await expect(summaryMixRow.getByRole('button', { name: 'Export CSV' })).toBeVisible();

  await summaryOnlyRow.getByRole('button', { name: 'Export CSV' }).click();

  await expect.poll(() => activityFamilyReportCsvRequestCount).toBe(1);
  await expect.poll(() => lastActivityFamilyReportCsvQuery).toMatchObject({
    format: 'csv',
  });

  await deliverableRow.getByRole('button', { name: 'Schedule' }).click();

  const dialog = page.getByRole('dialog', { name: /Schedule Delivery/i });
  await expect(dialog).toBeVisible();
  await expect.poll(() => scheduleGetCount).toBe(1);
  await expect.poll(() => executionsGetCount).toBe(1);

  await dialog.getByRole('checkbox', { name: 'Enable scheduled delivery' }).check();
  await dialog.getByRole('textbox', { name: 'Cron expression' }).fill('0 9 * * MON-FRI');
  await dialog.getByRole('treeitem', { name: /Delivery Target/i }).click();
  await dialog.getByRole('button', { name: 'Save schedule' }).click();

  await expect.poll(() => lastScheduleUpdate).toMatchObject({
    enabled: true,
    cronExpression: '0 9 * * MON-FRI',
    timezone: 'UTC',
    deliveryFolderId,
  });
  await expect.poll(() => scheduleGetCount).toBe(2);
  await expect(dialog.getByText('Enabled')).toBeVisible();
  await expect(dialog.getByText(/Next:/)).toBeVisible();

  await dialog.getByRole('button', { name: 'Deliver now' }).click();

  await expect.poll(() => lastDeliveredPresetId).toBe(deliverablePresetId);
  await expect.poll(() => scheduleGetCount).toBe(3);
  await expect.poll(() => executionsGetCount).toBe(3);
  await expect(dialog.getByText('weekly-family-report-20260421.csv')).toBeVisible();
  await expect(dialog.getByText(/Last:/)).toBeVisible();

  await dialog.getByRole('button', { name: 'Close' }).click();

  await summaryOnlyRow.getByRole('button', { name: 'Schedule' }).click();

  const summaryDialog = page.getByRole('dialog', { name: /Schedule Delivery/i });
  await expect(summaryDialog).toBeVisible();
  await expect.poll(() => scheduleGetCount).toBe(4);
  await expect.poll(() => executionsGetCount).toBe(4);

  await summaryDialog.getByRole('checkbox', { name: 'Enable scheduled delivery' }).check();
  await summaryDialog.getByRole('textbox', { name: 'Cron expression' }).fill('0 12 * * MON-FRI');
  await summaryDialog.getByRole('treeitem', { name: /Delivery Target/i }).click();
  await summaryDialog.getByRole('button', { name: 'Save schedule' }).click();

  await expect.poll(() => lastScheduleUpdate).toMatchObject({
    enabled: true,
    cronExpression: '0 12 * * MON-FRI',
    timezone: 'UTC',
    deliveryFolderId,
  });
  await expect.poll(() => scheduleGetCount).toBe(5);

  await summaryDialog.getByRole('button', { name: 'Deliver now' }).click();

  await expect.poll(() => lastDeliveredPresetId).toBe(summaryOnlyPresetId);
  await expect.poll(() => scheduleGetCount).toBe(6);
  await expect.poll(() => executionsGetCount).toBe(6);
  await expect(summaryDialog.getByText('weekly-family-report-20260421.csv')).toBeVisible();
  await expect(summaryDialog.getByText(/Last:/)).toBeVisible();

  await summaryDialog.getByRole('button', { name: 'Close' }).click();

  const healthCard = page.getByRole('heading', { name: 'Scheduled Delivery Health' })
    .locator('xpath=ancestor::div[contains(@class,"MuiCard-root")][1]');
  await expect(healthCard.getByText('Scheduled presets: 2')).toBeVisible();
  await expect(healthCard.getByText('Due now: 1')).toBeVisible();
  await expect(healthCard.getByText('Last 24h success: 2')).toBeVisible();
  await expect(healthCard.getByText('Last 24h failed: 1')).toBeVisible();

  const presetCard = page.getByRole('heading', { name: 'Saved RM Report Presets' })
    .locator('xpath=ancestor::div[contains(@class,"MuiCard-root")][1]');
  await healthCard.getByText('Due now: 1').click();
  await expect(presetCard.getByText('Due now · 1')).toBeVisible();
  await expect(presetCard.getByRole('row').filter({ hasText: 'Weekly Family Report' }).first()).toBeVisible();
  await expect(presetCard.getByRole('row').filter({ hasText: 'Weekly Family Highlights' })).toHaveCount(0);

  const ledgerCard = page.getByRole('heading', { name: 'Preset Delivery Ledger' })
    .locator('xpath=ancestor::div[contains(@class,"MuiCard-root")][1]');
  await expect(ledgerCard.getByText('Showing 3 of 3 deliveries')).toBeVisible();
  await expect(ledgerCard.getByText('Weekly Family Highlights')).toBeVisible();
  await expect(ledgerCard.getByText('weekly-family-report-20260421.csv')).toHaveCount(2);

  await healthCard.getByText('Last 24h failed: 1').click();
  await expect(ledgerCard.getByText('Active ledger filters')).toBeVisible();
  await expect(ledgerCard.getByText('Result: Failed')).toBeVisible();
  await expect(ledgerCard.getByText(/^From:/)).toBeVisible();
  await expect(ledgerCard.getByText(/^To:/)).toBeVisible();
  await expect(ledgerCard.getByText('Showing 1 of 1 deliveries')).toBeVisible();
  await expect(ledgerCard.getByText('Weekly Family Mix')).toBeVisible();

  await ledgerCard.getByRole('button', { name: 'Export ledger CSV' }).click();
  await expect.poll(() => ledgerExportRequestCount).toBe(1);
  await expect.poll(() => lastLedgerExportQuery).toMatchObject({
    status: 'FAILED',
    limit: '10',
  });

  await ledgerCard.getByRole('combobox', { name: 'Preset' }).click();
  await page.getByRole('option', { name: 'Weekly Family Highlights' }).click();
  await ledgerCard.getByRole('button', { name: 'Apply', exact: true }).click();

  await expect(ledgerCard.getByText('No deliveries match the current filters.')).toBeVisible();
  await ledgerCard.getByRole('button', { name: 'Show all deliveries' }).click();
  await expect(ledgerCard.getByText('Showing 3 of 3 deliveries')).toBeVisible();
});
