import { execFileSync } from 'node:child_process';
import { APIRequestContext, expect, test } from '@playwright/test';
import {
  fetchAccessToken,
  findDocumentId,
  getRootFolderId,
  resolveApiUrl,
  waitForApiReady,
} from './helpers/api';
import { gotoWithAuthE2E } from './helpers/login';

const baseApiUrl = resolveApiUrl();
const defaultUsername = process.env.ECM_E2E_USERNAME || 'admin';
const defaultPassword = process.env.ECM_E2E_PASSWORD || 'admin';
const defaultPostgresContainer = process.env.ECM_POSTGRES_CONTAINER || 'athena-postgres-1';
const defaultPostgresDb = process.env.POSTGRES_DB || 'ecm_db';
const defaultPostgresUser = process.env.POSTGRES_USER || 'ecm_user';
const defaultPostgresPassword = process.env.POSTGRES_PASSWORD || 'ecm_password';

type ReportPresetResponse = {
  id: string;
  owner: string;
  name: string;
  description?: string | null;
  kind: string;
  params: Record<string, unknown>;
};

type ReportPresetScheduleStatus = {
  presetId: string;
  enabled: boolean;
  cronExpression?: string | null;
  timezone?: string | null;
  deliveryFolderId?: string | null;
  nextRunAt?: string | null;
  lastRunAt?: string | null;
};

type ReportPresetExecution = {
  id: string;
  presetId: string;
  status: 'SUCCESS' | 'FAILED';
  filename?: string | null;
  targetFolderId?: string | null;
  documentId?: string | null;
  message?: string | null;
};

const toLocalDateTime = (date: Date) => date.toISOString().slice(0, 19);

async function createFolder(parentId: string, folderName: string, token: string, request: APIRequestContext) {
  const response = await request.post(`${baseApiUrl}/api/v1/folders`, {
    headers: {
      Authorization: `Bearer ${token}`,
      'Content-Type': 'application/json',
    },
    data: {
      name: folderName,
      parentId,
      folderType: 'GENERAL',
      inheritPermissions: true,
    },
  });
  expect(response.ok()).toBeTruthy();
  const payload = (await response.json()) as { id?: string };
  if (!payload.id) {
    throw new Error('Failed to create delivery folder');
  }
  return payload.id;
}

async function createReportPreset(token: string, name: string, from: string, to: string, request: APIRequestContext) {
  return createPreset(token, name, 'ACTIVITY_FAMILY_REPORT', { from, to }, request);
}

async function createPreset(
  token: string,
  name: string,
  kind: string,
  params: Record<string, unknown>,
  request: APIRequestContext
) {
  const response = await request.post(`${baseApiUrl}/api/v1/records/report-presets`, {
    headers: {
      Authorization: `Bearer ${token}`,
      'Content-Type': 'application/json',
    },
    data: {
      name,
      description: 'E2E scheduled delivery smoke preset',
      kind,
      params,
    },
  });
  expect(response.ok()).toBeTruthy();
  return (await response.json()) as ReportPresetResponse;
}

async function fetchScheduleStatus(token: string, presetId: string, request: APIRequestContext) {
  const response = await request.get(`${baseApiUrl}/api/v1/records/report-presets/${presetId}/schedule`, {
    headers: {
      Authorization: `Bearer ${token}`,
    },
  });
  expect(response.ok()).toBeTruthy();
  return (await response.json()) as ReportPresetScheduleStatus;
}

async function listExecutions(token: string, presetId: string, request: APIRequestContext) {
  const response = await request.get(`${baseApiUrl}/api/v1/records/report-presets/${presetId}/executions`, {
    params: {
      limit: 5,
    },
    headers: {
      Authorization: `Bearer ${token}`,
    },
  });
  expect(response.ok()).toBeTruthy();
  return (await response.json()) as ReportPresetExecution[];
}

async function deleteReportPreset(token: string, presetId: string, request: APIRequestContext) {
  await request.delete(`${baseApiUrl}/api/v1/records/report-presets/${presetId}`, {
    headers: {
      Authorization: `Bearer ${token}`,
    },
  }).catch(() => null);
}

async function deleteNode(token: string, nodeId: string, request: APIRequestContext) {
  await request.delete(`${baseApiUrl}/api/v1/nodes/${nodeId}`, {
    headers: {
      Authorization: `Bearer ${token}`,
    },
  }).catch(() => null);
}

function forcePresetNextRunAt(presetId: string, sqlExpression: string) {
  const sql = `
    update rm_report_presets
       set next_run_at = ${sqlExpression}
     where id = '${presetId}'
  `;
  execFileSync(
    'docker',
    [
      'exec',
      '-e',
      `PGPASSWORD=${defaultPostgresPassword}`,
      defaultPostgresContainer,
      'psql',
      '-U',
      defaultPostgresUser,
      '-d',
      defaultPostgresDb,
      '-tAc',
      sql,
    ],
    { stdio: 'pipe' }
  );
}

function forcePresetNextRunAtPast(presetId: string) {
  forcePresetNextRunAt(presetId, "now() - interval '1 minute'");
}

function forcePresetNextRunAtFuture(presetId: string) {
  forcePresetNextRunAt(presetId, "now() + interval '1 day'");
}

function getSavedPresetCard(page: import('@playwright/test').Page) {
  return page
    .getByRole('heading', { name: 'Saved RM Report Presets' })
    .locator('xpath=ancestor::div[contains(@class,"MuiCard-root")][1]');
}

function getPresetDeliveryLedgerCard(page: import('@playwright/test').Page) {
  return page
    .getByRole('heading', { name: 'Preset Delivery Ledger' })
    .locator('xpath=ancestor::div[contains(@class,"MuiCard-root")][1]');
}

function getScheduledDeliveryHealthCard(page: import('@playwright/test').Page) {
  return page
    .getByRole('heading', { name: 'Scheduled Delivery Health' })
    .locator('xpath=ancestor::div[contains(@class,"MuiCard-root")][1]');
}

async function findPresetRow(page: import('@playwright/test').Page, presetName: string) {
  const presetCard = getSavedPresetCard(page);
  const presetRow = presetCard.getByRole('row').filter({ hasText: presetName }).first();
  await expect(presetRow).toBeVisible({ timeout: 60_000 });
  return presetRow;
}

async function openScheduleDialogForPreset(page: import('@playwright/test').Page, presetName: string) {
  const presetRow = await findPresetRow(page, presetName);
  await expect(presetRow.getByRole('button', { name: 'Schedule' })).toBeVisible();
  await presetRow.getByRole('button', { name: 'Schedule' }).click();

  const dialog = page.getByRole('dialog', { name: /Schedule Delivery/i });
  await expect(dialog).toBeVisible({ timeout: 30_000 });
  return { presetRow, dialog };
}

async function pickDeliveryFolder(
  dialog: import('@playwright/test').Locator,
  folderName: string
) {
  const folderTreeItem = dialog.getByRole('treeitem', { name: folderName, exact: true });
  await expect(folderTreeItem).toBeVisible({ timeout: 60_000 });
  await folderTreeItem.scrollIntoViewIfNeeded();
  await folderTreeItem.getByText(folderName, { exact: true }).click();
  await expect(dialog.getByText(`Selected: ${folderName}`, { exact: true })).toBeVisible({ timeout: 30_000 });
}

test('RM report preset schedule can be configured from Records Management (full-stack)', async ({ page, request }) => {
  test.setTimeout(180_000);

  await waitForApiReady(request, { apiUrl: baseApiUrl });
  const token = await fetchAccessToken(request, defaultUsername, defaultPassword);
  const rootFolderId = await getRootFolderId(request, token, { apiUrl: baseApiUrl });

  const now = new Date();
  const from = toLocalDateTime(new Date(now.getTime() - 7 * 24 * 60 * 60 * 1000));
  const to = toLocalDateTime(now);
  const presetName = `e2e-rm-schedule-${Date.now()}`;
  const controlPresetName = `e2e-rm-unscheduled-${Date.now()}`;
  const cronExpression = '0 */10 * * * *';
  const deliveryFolderName = `e2e-rm-delivery-${Date.now()}`;
  const deliveryFolderId = await createFolder(rootFolderId, deliveryFolderName, token, request);

  const preset = await createReportPreset(token, presetName, from, to, request);
  const controlPreset = await createReportPreset(token, controlPresetName, from, to, request);

  try {
    const scheduleProbe = await request.get(`${baseApiUrl}/api/v1/records/report-presets/${preset.id}/schedule`, {
      headers: {
        Authorization: `Bearer ${token}`,
      },
    });
    expect(scheduleProbe.ok()).toBeTruthy();

    await gotoWithAuthE2E(page, '/admin/records-management', defaultUsername, defaultPassword, { token });

    await expect(page).toHaveURL(/\/admin\/records-management$/);
    await expect(page.getByRole('heading', { name: 'Records Management' })).toBeVisible({ timeout: 60_000 });

    const { dialog } = await openScheduleDialogForPreset(page, presetName);

    await dialog.getByRole('checkbox', { name: 'Enable scheduled delivery' }).check();
    await dialog.getByRole('textbox', { name: 'Cron expression' }).fill(cronExpression);
    await pickDeliveryFolder(dialog, deliveryFolderName);

    const saveResponse = page.waitForResponse((response) =>
      response.url().includes(`/api/v1/records/report-presets/${preset.id}/schedule`)
      && response.request().method() === 'PUT'
      && response.ok()
    );
    await dialog.getByRole('button', { name: 'Save schedule' }).click();
    await saveResponse;

    await expect(dialog.getByText('Enabled')).toBeVisible({ timeout: 30_000 });
    await expect(dialog.getByText(/Next:/)).toBeVisible({ timeout: 30_000 });

    await expect.poll(async () => {
      const status = await fetchScheduleStatus(token, preset.id, request);
      return {
        enabled: status.enabled,
        cronExpression: status.cronExpression,
        timezone: status.timezone,
        deliveryFolderId: status.deliveryFolderId,
        hasNextRunAt: Boolean(status.nextRunAt),
      };
    }, { timeout: 30_000 }).toEqual({
      enabled: true,
      cronExpression,
      timezone: 'UTC',
      deliveryFolderId,
      hasNextRunAt: true,
    });

    const deliverResponse = page.waitForResponse((response) =>
      response.url().includes(`/api/v1/records/report-presets/${preset.id}/deliver`)
      && response.request().method() === 'POST'
      && response.ok()
    );
    await dialog.getByRole('button', { name: 'Deliver now' }).click();
    await deliverResponse;

    await expect.poll(async () => {
      const executions = await listExecutions(token, preset.id, request);
      const first = executions[0];
      if (!first) {
        return null;
      }
      return {
        status: first.status,
        filename: first.filename ?? null,
        targetFolderId: first.targetFolderId ?? null,
      };
    }, { timeout: 60_000 }).toEqual({
      status: 'SUCCESS',
      filename: expect.any(String),
      targetFolderId: deliveryFolderId,
    });

    const executions = await listExecutions(token, preset.id, request);
    const deliveredFilename = executions[0]?.filename;
    expect(deliveredFilename).toBeTruthy();
    if (!deliveredFilename) {
      throw new Error('Expected delivered filename from execution ledger');
    }

    await expect(dialog.getByText(deliveredFilename)).toBeVisible({ timeout: 30_000 });
    await findDocumentId(request, deliveryFolderId, deliveredFilename, token, {
      apiUrl: baseApiUrl,
      maxAttempts: 20,
      delayMs: 1000,
    });

    await dialog.getByRole('button', { name: 'Close' }).click();

    const healthCard = getScheduledDeliveryHealthCard(page);
    await expect(healthCard.getByText(/Scheduled presets: [1-9]\d*/)).toBeVisible({ timeout: 30_000 });
    await expect(healthCard.getByText(/Last 24h success: [1-9]\d*/)).toBeVisible({ timeout: 30_000 });

    const ledgerCard = getPresetDeliveryLedgerCard(page);
    await expect(ledgerCard.getByText(deliveredFilename, { exact: true })).toBeVisible({ timeout: 30_000 });
    await healthCard.getByText(/Last 24h success: [1-9]\d*/).click();
    await expect(ledgerCard.getByText('Active ledger filters')).toBeVisible({ timeout: 30_000 });
    await expect(ledgerCard.getByText('Result: Successful')).toBeVisible();
    await expect(ledgerCard.getByText(/^From:/)).toBeVisible();
    await expect(ledgerCard.getByText(/^To:/)).toBeVisible();

    const successLedgerExportResponse = page.waitForResponse((response) => {
      if (!response.url().includes('/api/v1/records/report-presets/executions/export')) {
        return false;
      }
      const url = new URL(response.url());
      return url.searchParams.get('status') === 'SUCCESS'
        && url.searchParams.has('from')
        && url.searchParams.has('to')
        && response.ok();
    });
    await ledgerCard.getByRole('button', { name: 'Export ledger CSV' }).click();
    await successLedgerExportResponse;

    await ledgerCard.getByRole('button', { name: 'Clear applied filters' }).click();
    await expect(ledgerCard.getByText(deliveredFilename, { exact: true })).toBeVisible({ timeout: 30_000 });

    const presetCard = getSavedPresetCard(page);
    await healthCard.getByText(/Scheduled presets: [1-9]\d*/).click();
    await expect(presetCard.getByText(/Scheduled · [1-9]\d*/)).toBeVisible({ timeout: 30_000 });
    await expect(presetCard.getByRole('row').filter({ hasText: presetName }).first()).toBeVisible();
    await expect(presetCard.getByRole('row').filter({ hasText: controlPresetName })).toHaveCount(0);

    const ledgerRow = ledgerCard.getByRole('row').filter({ hasText: presetName }).first();
    await expect(ledgerRow).toBeVisible({ timeout: 30_000 });
    await expect(ledgerRow.getByText('Successful', { exact: true })).toBeVisible();
    await expect(ledgerRow.getByText(deliveredFilename, { exact: true })).toBeVisible();

    await ledgerCard.getByRole('combobox', { name: 'Preset' }).click();
    await page.getByRole('option', { name: presetName, exact: true }).click();
    await ledgerCard.getByRole('button', { name: 'Apply', exact: true }).click();

    await expect(ledgerCard.getByText('Active ledger filters')).toBeVisible({ timeout: 30_000 });
    await expect(ledgerCard.getByText(`Preset: ${presetName}`)).toBeVisible();
    await expect(ledgerCard.getByText('Showing 1 of 1 deliveries')).toBeVisible();

    const exportLedgerResponse = page.waitForResponse((response) => {
      if (!response.url().includes('/api/v1/records/report-presets/executions/export')) {
        return false;
      }
      const url = new URL(response.url());
      return url.searchParams.get('presetId') === preset.id && response.ok();
    });
    await ledgerCard.getByRole('button', { name: 'Export ledger CSV' }).click();
    await exportLedgerResponse;

    await ledgerCard.getByLabel('From').fill('2099-01-01T00:00');
    await ledgerCard.getByRole('button', { name: 'Apply', exact: true }).click();
    await expect(ledgerCard.getByText('No deliveries match the current filters.')).toBeVisible({ timeout: 30_000 });

    await ledgerCard.getByRole('button', { name: 'Show all deliveries' }).click();
    await expect(ledgerRow).toBeVisible({ timeout: 30_000 });
  } finally {
    await deleteReportPreset(token, preset.id, request);
    await deleteReportPreset(token, controlPreset.id, request);
    await deleteNode(token, deliveryFolderId, request);
  }
});

test('RM summary-only preset can be exported and scheduled from Records Management (full-stack)', async ({ page, request }) => {
  test.setTimeout(180_000);

  await waitForApiReady(request, { apiUrl: baseApiUrl });
  const token = await fetchAccessToken(request, defaultUsername, defaultPassword);
  const rootFolderId = await getRootFolderId(request, token, { apiUrl: baseApiUrl });

  const presetName = `e2e-rm-summary-schedule-${Date.now()}`;
  const cronExpression = '0 */15 * * * *';
  const deliveryFolderName = `e2e-rm-summary-delivery-${Date.now()}`;
  const deliveryFolderId = await createFolder(rootFolderId, deliveryFolderName, token, request);
  const preset = await createPreset(
    token,
    presetName,
    'ACTIVITY_FAMILY_HIGHLIGHTS',
    { windowDays: 7 },
    request
  );

  try {
    const scheduleProbe = await request.get(`${baseApiUrl}/api/v1/records/report-presets/${preset.id}/schedule`, {
      headers: {
        Authorization: `Bearer ${token}`,
      },
    });
    expect(scheduleProbe.ok()).toBeTruthy();

    await gotoWithAuthE2E(page, '/admin/records-management', defaultUsername, defaultPassword, { token });

    await expect(page).toHaveURL(/\/admin\/records-management$/);
    await expect(page.getByRole('heading', { name: 'Records Management' })).toBeVisible({ timeout: 60_000 });

    const presetRow = await findPresetRow(page, presetName);
    await expect(presetRow.getByRole('button', { name: 'Export CSV' })).toBeVisible();

    const exportResponse = page.waitForResponse((response) => {
      if (!response.url().includes('/api/v1/records/activity-family-report')) {
        return false;
      }
      const url = new URL(response.url());
      return url.searchParams.get('format') === 'csv' && response.ok();
    });
    await presetRow.getByRole('button', { name: 'Export CSV' }).click();
    await exportResponse;

    const { dialog } = await openScheduleDialogForPreset(page, presetName);

    await dialog.getByRole('checkbox', { name: 'Enable scheduled delivery' }).check();
    await dialog.getByRole('textbox', { name: 'Cron expression' }).fill(cronExpression);
    await pickDeliveryFolder(dialog, deliveryFolderName);

    const saveResponse = page.waitForResponse((response) =>
      response.url().includes(`/api/v1/records/report-presets/${preset.id}/schedule`)
      && response.request().method() === 'PUT'
      && response.ok()
    );
    await dialog.getByRole('button', { name: 'Save schedule' }).click();
    await saveResponse;

    await expect(dialog.getByText('Enabled')).toBeVisible({ timeout: 30_000 });

    await expect.poll(async () => {
      const status = await fetchScheduleStatus(token, preset.id, request);
      return {
        enabled: status.enabled,
        cronExpression: status.cronExpression,
        timezone: status.timezone,
        deliveryFolderId: status.deliveryFolderId,
        hasNextRunAt: Boolean(status.nextRunAt),
      };
    }, { timeout: 30_000 }).toEqual({
      enabled: true,
      cronExpression,
      timezone: 'UTC',
      deliveryFolderId,
      hasNextRunAt: true,
    });

    const deliverResponse = page.waitForResponse((response) =>
      response.url().includes(`/api/v1/records/report-presets/${preset.id}/deliver`)
      && response.request().method() === 'POST'
      && response.ok()
    );
    await dialog.getByRole('button', { name: 'Deliver now' }).click();
    await deliverResponse;

    await expect.poll(async () => {
      const executions = await listExecutions(token, preset.id, request);
      const first = executions[0];
      if (!first) {
        return null;
      }
      return {
        status: first.status,
        filename: first.filename ?? null,
        targetFolderId: first.targetFolderId ?? null,
      };
    }, { timeout: 60_000 }).toEqual({
      status: 'SUCCESS',
      filename: expect.any(String),
      targetFolderId: deliveryFolderId,
    });

    const executions = await listExecutions(token, preset.id, request);
    const deliveredFilename = executions[0]?.filename;
    expect(deliveredFilename).toBeTruthy();
    if (!deliveredFilename) {
      throw new Error('Expected delivered filename from summary-only execution ledger');
    }

    await expect(dialog.getByText(deliveredFilename)).toBeVisible({ timeout: 30_000 });
    await findDocumentId(request, deliveryFolderId, deliveredFilename, token, {
      apiUrl: baseApiUrl,
      maxAttempts: 20,
      delayMs: 1000,
    });
  } finally {
    await deleteReportPreset(token, preset.id, request);
    await deleteNode(token, deliveryFolderId, request);
  }
});

test('RM failed preset delivery is surfaced through scheduled delivery health (full-stack)', async ({ page, request }) => {
  test.setTimeout(180_000);

  await waitForApiReady(request, { apiUrl: baseApiUrl });
  const token = await fetchAccessToken(request, defaultUsername, defaultPassword);
  const rootFolderId = await getRootFolderId(request, token, { apiUrl: baseApiUrl });

  const now = new Date();
  const from = toLocalDateTime(new Date(now.getTime() - 7 * 24 * 60 * 60 * 1000));
  const to = toLocalDateTime(now);
  const presetName = `e2e-rm-failed-schedule-${Date.now()}`;
  const cronExpression = '0 */20 * * * *';
  const deliveryFolderName = `e2e-rm-failed-delivery-${Date.now()}`;
  const deliveryFolderId = await createFolder(rootFolderId, deliveryFolderName, token, request);
  const invalidDeliveryFolderId = '00000000-0000-0000-0000-000000000000';
  const preset = await createReportPreset(token, presetName, from, to, request);

  try {
    await gotoWithAuthE2E(page, '/admin/records-management', defaultUsername, defaultPassword, { token });

    await expect(page).toHaveURL(/\/admin\/records-management$/);
    await expect(page.getByRole('heading', { name: 'Records Management' })).toBeVisible({ timeout: 60_000 });

    const { dialog } = await openScheduleDialogForPreset(page, presetName);

    await dialog.getByRole('checkbox', { name: 'Enable scheduled delivery' }).check();
    await dialog.getByRole('textbox', { name: 'Cron expression' }).fill(cronExpression);
    await pickDeliveryFolder(dialog, deliveryFolderName);

    const saveResponse = page.waitForResponse((response) =>
      response.url().includes(`/api/v1/records/report-presets/${preset.id}/schedule`)
      && response.request().method() === 'PUT'
      && response.ok()
    );
    await dialog.getByRole('button', { name: 'Save schedule' }).click();
    await saveResponse;

    await expect(dialog.getByText('Enabled')).toBeVisible({ timeout: 30_000 });
    await expect.poll(async () => {
      const status = await fetchScheduleStatus(token, preset.id, request);
      return {
        enabled: status.enabled,
        deliveryFolderId: status.deliveryFolderId,
        hasNextRunAt: Boolean(status.nextRunAt),
      };
    }, { timeout: 30_000 }).toEqual({
      enabled: true,
      deliveryFolderId,
      hasNextRunAt: true,
    });

    const forceFailureResponse = await request.put(`${baseApiUrl}/api/v1/records/report-presets/${preset.id}/schedule`, {
      headers: {
        Authorization: `Bearer ${token}`,
        'Content-Type': 'application/json',
      },
      data: {
        enabled: true,
        cronExpression,
        timezone: 'UTC',
        deliveryFolderId: invalidDeliveryFolderId,
      },
    });
    expect(forceFailureResponse.ok()).toBeTruthy();

    await expect.poll(async () => {
      const status = await fetchScheduleStatus(token, preset.id, request);
      return status.deliveryFolderId;
    }, { timeout: 30_000 }).toBe(invalidDeliveryFolderId);

    const deliverResponse = page.waitForResponse((response) =>
      response.url().includes(`/api/v1/records/report-presets/${preset.id}/deliver`)
      && response.request().method() === 'POST'
      && response.ok()
    );
    await dialog.getByRole('button', { name: 'Deliver now' }).click();
    await deliverResponse;

    await expect.poll(async () => {
      const executions = await listExecutions(token, preset.id, request);
      const first = executions[0];
      if (!first) {
        return null;
      }
      return {
        status: first.status,
        targetFolderId: first.targetFolderId ?? null,
        filename: first.filename ?? null,
      };
    }, { timeout: 60_000 }).toEqual({
      status: 'FAILED',
      targetFolderId: invalidDeliveryFolderId,
      filename: expect.any(String),
    });

    await dialog.getByRole('button', { name: 'Close' }).click();

    const healthCard = getScheduledDeliveryHealthCard(page);
    await expect(healthCard.getByText(/Last 24h failed: [1-9]\d*/)).toBeVisible({ timeout: 30_000 });

    const ledgerCard = getPresetDeliveryLedgerCard(page);
    await healthCard.getByText(/Last 24h failed: [1-9]\d*/).click();
    await expect(ledgerCard.getByText('Active ledger filters')).toBeVisible({ timeout: 30_000 });
    await expect(ledgerCard.getByText('Result: Failed')).toBeVisible();
    await expect(ledgerCard.getByText(/^From:/)).toBeVisible();
    await expect(ledgerCard.getByText(/^To:/)).toBeVisible();

    const failedLedgerExportResponse = page.waitForResponse((response) => {
      if (!response.url().includes('/api/v1/records/report-presets/executions/export')) {
        return false;
      }
      const url = new URL(response.url());
      return url.searchParams.get('status') === 'FAILED'
        && url.searchParams.has('from')
        && url.searchParams.has('to')
        && response.ok();
    });
    await ledgerCard.getByRole('button', { name: 'Export ledger CSV' }).click();
    await failedLedgerExportResponse;
  } finally {
    await deleteReportPreset(token, preset.id, request);
    await deleteNode(token, deliveryFolderId, request);
  }
});

test('RM due-now preset is surfaced through scheduled delivery health (full-stack)', async ({ page, request }) => {
  test.setTimeout(180_000);

  await waitForApiReady(request, { apiUrl: baseApiUrl });
  const token = await fetchAccessToken(request, defaultUsername, defaultPassword);
  const rootFolderId = await getRootFolderId(request, token, { apiUrl: baseApiUrl });

  const now = new Date();
  const from = toLocalDateTime(new Date(now.getTime() - 7 * 24 * 60 * 60 * 1000));
  const to = toLocalDateTime(now);
  const presetName = `e2e-rm-due-now-${Date.now()}`;
  const controlPresetName = `e2e-rm-future-schedule-${Date.now()}`;
  const cronExpression = '0 */10 * * * *';
  const deliveryFolderName = `e2e-rm-due-now-delivery-${Date.now()}`;
  const deliveryFolderId = await createFolder(rootFolderId, deliveryFolderName, token, request);
  const preset = await createReportPreset(token, presetName, from, to, request);
  const controlPreset = await createReportPreset(token, controlPresetName, from, to, request);

  try {
    await gotoWithAuthE2E(page, '/admin/records-management', defaultUsername, defaultPassword, { token });

    await expect(page).toHaveURL(/\/admin\/records-management$/);
    await expect(page.getByRole('heading', { name: 'Records Management' })).toBeVisible({ timeout: 60_000 });

    const { dialog } = await openScheduleDialogForPreset(page, presetName);

    await dialog.getByRole('checkbox', { name: 'Enable scheduled delivery' }).check();
    await dialog.getByRole('textbox', { name: 'Cron expression' }).fill(cronExpression);
    await pickDeliveryFolder(dialog, deliveryFolderName);

    const saveResponse = page.waitForResponse((response) =>
      response.url().includes(`/api/v1/records/report-presets/${preset.id}/schedule`)
      && response.request().method() === 'PUT'
      && response.ok()
    );
    await dialog.getByRole('button', { name: 'Save schedule' }).click();
    await saveResponse;

    await expect(dialog.getByText('Enabled')).toBeVisible({ timeout: 30_000 });
    await dialog.getByRole('button', { name: 'Close' }).click();

    const controlScheduleResponse = await request.put(`${baseApiUrl}/api/v1/records/report-presets/${controlPreset.id}/schedule`, {
      headers: {
        Authorization: `Bearer ${token}`,
        'Content-Type': 'application/json',
      },
      data: {
        enabled: true,
        cronExpression,
        timezone: 'UTC',
        deliveryFolderId,
      },
    });
    expect(controlScheduleResponse.ok()).toBeTruthy();

    forcePresetNextRunAtPast(preset.id);
    forcePresetNextRunAtFuture(controlPreset.id);

    await expect.poll(async () => {
      const status = await fetchScheduleStatus(token, preset.id, request);
      return Boolean(status.nextRunAt && new Date(status.nextRunAt).getTime() <= Date.now());
    }, { timeout: 30_000 }).toBe(true);

    await expect.poll(async () => {
      const status = await fetchScheduleStatus(token, controlPreset.id, request);
      return Boolean(status.nextRunAt && new Date(status.nextRunAt).getTime() > Date.now());
    }, { timeout: 30_000 }).toBe(true);

    await page.getByRole('button', { name: 'Refresh', exact: true }).click();
    await expect(page.getByRole('button', { name: 'Refresh', exact: true })).toBeVisible({ timeout: 60_000 });

    const healthCard = getScheduledDeliveryHealthCard(page);
    await expect(healthCard.getByText(/Due now: [1-9]\d*/)).toBeVisible({ timeout: 30_000 });

    const presetCard = getSavedPresetCard(page);
    await healthCard.getByText(/Due now: [1-9]\d*/).click();
    await expect(presetCard.getByText(/Due now · [1-9]\d*/)).toBeVisible({ timeout: 30_000 });

    const dueRow = presetCard.getByRole('row').filter({ hasText: presetName }).first();
    await expect(dueRow).toBeVisible({ timeout: 30_000 });
    await expect(dueRow.getByText('Due now', { exact: true })).toBeVisible();
    await expect(presetCard.getByRole('row').filter({ hasText: controlPresetName })).toHaveCount(0);
  } finally {
    await deleteReportPreset(token, preset.id, request);
    await deleteReportPreset(token, controlPreset.id, request);
    await deleteNode(token, deliveryFolderId, request);
  }
});
