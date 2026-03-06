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

  const queueCalls: Array<{ id: string; force: boolean }> = [];
  const requestedFailureDays: string[] = [];
  const requestedSummaryDays: string[] = [];

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

  // When running against a static build server (no SPA rewrite), avoid deep links.
  // Navigate from the app root instead.
  await page.goto('/', { waitUntil: 'domcontentloaded' });
  await expect(page.getByRole('button', { name: 'Account menu' })).toBeVisible();
  await page.getByRole('button', { name: 'Account menu' }).click();
  await page.getByRole('menuitem', { name: 'Preview Diagnostics' }).click();

  await expect(page.getByRole('heading', { name: 'Preview Diagnostics' })).toBeVisible();
  await expect(page.getByText('Backend Failure Summary')).toBeVisible();
  await expect(page.getByText('HIGH confidence (sample complete)')).toBeVisible();
  await expect(page.getByText('Sampled 3/3')).toBeVisible();

  await expect(page.getByText('Total 3')).toBeVisible();
  await expect(page.getByText('Retryable 1')).toBeVisible();
  await expect(page.getByText('Permanent 1')).toBeVisible();
  await expect(page.getByText('Unsupported 1')).toBeVisible();

  const filter = page.getByPlaceholder('Filter by name, path, mime type...');
  await filter.fill(retryableName);

  const retryableRow = page.locator('tr', { hasText: retryableName });
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

  const retryByReason = page.getByRole('button', { name: /Retry reason group Timeout contacting preview service/i }).first();
  await expect(retryByReason).toBeEnabled();
  await retryByReason.click();
  await expect(page.getByText(/Retry queued for 2 document\(s\): Timeout contacting preview service/i)).toBeVisible();

  expect(requestedFailureDays.some((value) => value === '7')).toBeTruthy();
  expect(requestedFailureDays.some((value) => value === '30')).toBeTruthy();
  expect(requestedSummaryDays.some((value) => value === '7')).toBeTruthy();
  expect(requestedSummaryDays.some((value) => value === '30')).toBeTruthy();
  const nonForceQueueIds = queueCalls.filter((call) => !call.force).map((call) => call.id);
  expect(nonForceQueueIds.filter((id) => id === retryableTwinId).length).toBe(1);
  expect(nonForceQueueIds.filter((id) => id === retryableId).length).toBeGreaterThanOrEqual(2);
});
