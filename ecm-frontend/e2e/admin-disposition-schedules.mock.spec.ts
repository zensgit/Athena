import { expect, test } from '@playwright/test';
import { seedBypassSessionE2E } from './helpers/login';
import { mockKeycloakUnreachable } from './helpers/keycloakMock';

const SCHED_1 = {
  id: 'sched-1',
  folderId: 'folder-1',
  folderName: 'Finance Records',
  folderPath: '/company/finance',
  enabled: true,
  includeSubfolders: true,
  cutoffAfterDays: 365,
  archiveAfterCutoffDays: 180,
  destroyAfterArchiveDays: 1825,
  archiveStorageTier: 'COLD',
  maxCandidatesPerAction: 500,
  lastDryRunAt: null,
  lastExecutedAt: null,
  lastError: null,
};

const SCHED_2 = {
  id: 'sched-2',
  folderId: 'folder-2',
  folderName: 'HR Records',
  folderPath: '/company/hr',
  enabled: false,
  includeSubfolders: false,
  cutoffAfterDays: 730,
  archiveAfterCutoffDays: null,
  destroyAfterArchiveDays: null,
  archiveStorageTier: null,
  maxCandidatesPerAction: 100,
  lastDryRunAt: null,
  lastExecutedAt: null,
  lastError: null,
};

const EMPTY_EXECUTIONS = {
  content: [],
  totalElements: 0,
  totalPages: 0,
  number: 0,
  size: 10,
};

const BATCH_RUN_RESULT = {
  executedSchedules: 1,
  cutoffCount: 0,
  archivedNodeCount: 0,
  destroyedNodeCount: 0,
  blockedCount: 0,
  failureCount: 0,
  results: [],
};

test('shows schedule list with enabled/disabled status', async ({ page }) => {
  test.setTimeout(60_000);
  await mockKeycloakUnreachable(page);
  await seedBypassSessionE2E(page, 'admin', 'e2e-token');

  await page.route('**/api/v1/disposition-schedules', async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify([SCHED_1, SCHED_2]),
    });
  });
  await page.route('**/api/v1/folders/folder-1/disposition-schedule', async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify(SCHED_1),
    });
  });
  await page.route('**/api/v1/folders/folder-1/disposition-schedule/executions**', async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify(EMPTY_EXECUTIONS),
    });
  });
  await page.route('**/api/v1/disposition-schedules/run', async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify(BATCH_RUN_RESULT),
    });
  });

  await page.goto('/admin/disposition-schedules');

  await expect(page.getByText('Finance Records')).toBeVisible();
  await expect(page.getByText('HR Records')).toBeVisible();

  // Finance Records is enabled → chip shows "Active"
  // HR Records is disabled → chip shows "Disabled"
  // The page renders multiple chips; use first() for the enabled one in the list
  const activeChips = page.getByText('Active');
  await expect(activeChips.first()).toBeVisible();
  await expect(page.getByText('Disabled').first()).toBeVisible();
});

test('clicking a schedule shows detail panel', async ({ page }) => {
  test.setTimeout(60_000);
  await mockKeycloakUnreachable(page);
  await seedBypassSessionE2E(page, 'admin', 'e2e-token');

  await page.route('**/api/v1/disposition-schedules', async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify([SCHED_1, SCHED_2]),
    });
  });
  await page.route('**/api/v1/folders/folder-1/disposition-schedule', async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify(SCHED_1),
    });
  });
  await page.route('**/api/v1/folders/folder-1/disposition-schedule/executions**', async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify(EMPTY_EXECUTIONS),
    });
  });
  await page.route('**/api/v1/disposition-schedules/run', async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify(BATCH_RUN_RESULT),
    });
  });

  await page.goto('/admin/disposition-schedules');

  await expect(page.getByText('Finance Records')).toBeVisible();

  // Click on Finance Records in the list
  await page.getByText('Finance Records').first().click();

  // Detail panel should show the cutoff days value: "365 days"
  await expect(page.getByText('365 days')).toBeVisible();

  // Should also show "Cutoff after" label in the settings grid
  await expect(page.getByText('Cutoff after')).toBeVisible();
});

test('run all schedules button triggers batch run', async ({ page }) => {
  test.setTimeout(60_000);
  await mockKeycloakUnreachable(page);
  await seedBypassSessionE2E(page, 'admin', 'e2e-token');

  await page.route('**/api/v1/disposition-schedules', async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify([SCHED_1, SCHED_2]),
    });
  });
  await page.route('**/api/v1/folders/folder-1/disposition-schedule', async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify(SCHED_1),
    });
  });
  await page.route('**/api/v1/folders/folder-1/disposition-schedule/executions**', async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify(EMPTY_EXECUTIONS),
    });
  });

  let runAllCalled = false;
  await page.route('**/api/v1/disposition-schedules/run', async (route) => {
    runAllCalled = true;
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify(BATCH_RUN_RESULT),
    });
  });

  await page.goto('/admin/disposition-schedules');

  await expect(page.getByRole('button', { name: 'Run All Schedules' })).toBeVisible();

  // No confirm dialog — handleRunAll calls the service directly
  await page.getByRole('button', { name: 'Run All Schedules' }).click();

  // Wait for the result dialog to appear (shows "Run All Schedules — Summary" title)
  await expect(page.getByText('Run All Schedules — Summary')).toBeVisible();

  expect(runAllCalled).toBe(true);
});
