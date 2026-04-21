import { expect, test } from '@playwright/test';
import { seedBypassSessionE2E } from './helpers/login';

const now = '2026-04-21T09:00:00Z';
const rootFolderId = '11111111-1111-1111-1111-111111111111';
const deliverablePresetId = '22222222-2222-2222-2222-222222222222';
const summaryOnlyPresetId = '33333333-3333-3333-3333-333333333333';
const deliveryFolderId = '44444444-4444-4444-4444-444444444444';

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
        from: '2026-04-14T00:00:00',
        to: '2026-04-20T23:59:59',
      },
      createdDate: now,
      lastModifiedDate: now,
    },
  ];

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
  ]);

  const executionState = new Map<string, any[]>([
    [deliverablePresetId, []],
  ]);

  let scheduleGetCount = 0;
  let executionsGetCount = 0;
  let lastScheduleUpdate: any = null;
  let lastDeliveredPresetId: string | null = null;

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
      await fulfillJson(route, {
        content: [],
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
      await fulfillJson(route, reportPresets);
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
        nextRunAt: body.enabled ? '2026-04-22T09:00:00' : null,
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

  await expect(deliverableRow.getByRole('button', { name: 'Schedule' })).toBeVisible();
  await expect(deliverableRow.getByRole('button', { name: 'Export CSV' })).toBeVisible();
  await expect(summaryOnlyRow.getByRole('button', { name: 'Apply to audit' })).toBeVisible();
  await expect(summaryOnlyRow.getByRole('button', { name: 'Schedule' })).toHaveCount(0);
  await expect(summaryOnlyRow.getByRole('button', { name: 'Export CSV' })).toHaveCount(0);

  await deliverableRow.getByRole('button', { name: 'Schedule' }).click();

  const dialog = page.getByRole('dialog', { name: /Schedule Delivery/i });
  await expect(dialog).toBeVisible();
  await expect.poll(() => scheduleGetCount).toBe(1);
  await expect.poll(() => executionsGetCount).toBe(1);

  await dialog.getByRole('checkbox', { name: 'Enable scheduled delivery' }).check();
  await dialog.getByRole('textbox', { name: 'Cron expression' }).fill('0 9 * * MON-FRI');
  await dialog.getByRole('textbox', { name: 'Delivery folder ID' }).fill(deliveryFolderId);
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
});
