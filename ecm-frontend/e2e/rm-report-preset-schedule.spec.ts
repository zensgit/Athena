import { APIRequestContext, expect, test } from '@playwright/test';
import {
  fetchAccessToken,
  findChildFolderId,
  findDocumentId,
  getRootFolderId,
  resolveApiUrl,
  waitForApiReady,
} from './helpers/api';
import { gotoWithAuthE2E } from './helpers/login';

const baseApiUrl = resolveApiUrl();
const defaultUsername = process.env.ECM_E2E_USERNAME || 'admin';
const defaultPassword = process.env.ECM_E2E_PASSWORD || 'admin';

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
  const response = await request.post(`${baseApiUrl}/api/v1/records/report-presets`, {
    headers: {
      Authorization: `Bearer ${token}`,
      'Content-Type': 'application/json',
    },
    data: {
      name,
      description: 'E2E scheduled delivery smoke preset',
      kind: 'ACTIVITY_FAMILY_REPORT',
      params: {
        from,
        to,
      },
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

test('RM report preset schedule can be configured from Records Management (full-stack)', async ({ page, request }) => {
  test.setTimeout(180_000);

  await waitForApiReady(request, { apiUrl: baseApiUrl });
  const token = await fetchAccessToken(request, defaultUsername, defaultPassword);
  const rootFolderId = await getRootFolderId(request, token, { apiUrl: baseApiUrl });
  const documentsFolderId = await findChildFolderId(request, rootFolderId, 'Documents', token, { apiUrl: baseApiUrl });

  const now = new Date();
  const from = toLocalDateTime(new Date(now.getTime() - 7 * 24 * 60 * 60 * 1000));
  const to = toLocalDateTime(now);
  const presetName = `e2e-rm-schedule-${Date.now()}`;
  const cronExpression = '0 */10 * * * *';
  const deliveryFolderName = `e2e-rm-delivery-${Date.now()}`;
  const deliveryFolderId = await createFolder(documentsFolderId, deliveryFolderName, token, request);

  const preset = await createReportPreset(token, presetName, from, to, request);

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

    const presetRow = page.locator('tr', { has: page.getByText(presetName) }).first();
    await expect(presetRow).toBeVisible({ timeout: 60_000 });
    await expect(presetRow.getByRole('button', { name: 'Schedule' })).toBeVisible();

    await presetRow.getByRole('button', { name: 'Schedule' }).click();

    const dialog = page.getByRole('dialog', { name: /Schedule Delivery/i });
    await expect(dialog).toBeVisible({ timeout: 30_000 });

    await dialog.getByRole('checkbox', { name: 'Enable scheduled delivery' }).check();
    await dialog.getByRole('textbox', { name: 'Cron expression' }).fill(cronExpression);
    await dialog.getByRole('textbox', { name: 'Delivery folder ID' }).fill(deliveryFolderId);

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
  } finally {
    await deleteReportPreset(token, preset.id, request);
    await deleteNode(token, deliveryFolderId, request);
  }
});
