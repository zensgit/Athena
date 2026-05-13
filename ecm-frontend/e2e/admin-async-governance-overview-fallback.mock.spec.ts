import { expect, test } from '@playwright/test';
import { seedBypassSessionE2E } from './helpers/login';

test('async governance overview fallback marks property encryption as overview-required', async ({ page }) => {
  test.setTimeout(90_000);

  await seedBypassSessionE2E(page, 'admin', 'e2e-token');

  const summary = {
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
  };
  const overviewCalls: string[] = [];
  const legacySummaryCalls: string[] = [];
  const unexpectedPropertyEncryptionSummaryCalls: string[] = [];

  const fulfillJson = (route: any, body: unknown, status = 200) => route.fulfill({
    status,
    contentType: 'application/json',
    body: JSON.stringify(body),
  });

  await page.route('**/api/v1/**', async (route) => {
    const requestUrl = new URL(route.request().url());
    const pathname = requestUrl.pathname;

    if (pathname.endsWith('/folders/roots')) {
      await fulfillJson(route, []);
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
      await fulfillJson(route, { windowDays: 30, totalEvents: 0, countsByCategory: {} });
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
      await fulfillJson(route, []);
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
    if (pathname.endsWith('/analytics/async-governance/tasks')) {
      await fulfillJson(route, {
        items: [],
        totalCount: 0,
        count: 0,
        paging: {
          skipCount: 0,
          maxItems: Number(requestUrl.searchParams.get('maxItems') || '10'),
          totalItems: 0,
          hasMoreItems: false,
        },
        generatedAt: new Date().toISOString(),
      });
      return;
    }
    if (pathname.endsWith('/analytics/async-governance/overview')) {
      overviewCalls.push(pathname);
      await fulfillJson(route, { message: 'overview temporarily unavailable' }, 503);
      return;
    }
    if (pathname.endsWith('/admin/property-encryption/status')) {
      await fulfillJson(route, {
        secretCryptoEnabled: true,
        activeKeyVersionName: 'v1',
        configuredKeyVersions: ['v1'],
        encryptedDefinitionCount: 0,
        encryptedPayloadCount: 0,
        warnings: [],
      });
      return;
    }
    if (pathname.includes('/admin/property-encryption') && pathname.endsWith('/summary')) {
      unexpectedPropertyEncryptionSummaryCalls.push(pathname);
      await fulfillJson(route, summary);
      return;
    }
    if (
      pathname.endsWith('/analytics/audit/export-async/summary')
      || pathname.endsWith('/ops/recovery/history/export-async/summary')
      || pathname.endsWith('/search/preview/queue-failed/dry-run/export-async/summary')
      || pathname.endsWith('/preview/diagnostics/renditions/resources/export-async/summary')
      || pathname.endsWith('/nodes/download/batch-async/summary')
    ) {
      legacySummaryCalls.push(pathname);
      await fulfillJson(route, summary);
      return;
    }
    if (pathname.endsWith('/analytics/audit/export-async')) {
      await fulfillJson(route, { count: 0, items: [] });
      return;
    }
    if (pathname.endsWith('/nodes/download/batch-async')) {
      await fulfillJson(route, {
        items: [],
        paging: {
          maxItems: Number(requestUrl.searchParams.get('maxItems') || '10'),
          skipCount: Number(requestUrl.searchParams.get('skipCount') || '0'),
          totalItems: 0,
          hasMoreItems: false,
        },
      });
      return;
    }

    await fulfillJson(route, { message: `Not mocked: ${pathname}` }, 404);
  });

  await page.goto('/admin', { waitUntil: 'domcontentloaded' });

  await expect(page.getByRole('heading', { name: 'System Dashboard' })).toBeVisible({ timeout: 60_000 });
  await expect.poll(() => overviewCalls.length).toBeGreaterThan(0);
  await expect.poll(() => new Set(legacySummaryCalls).size).toBe(5);
  expect(unexpectedPropertyEncryptionSummaryCalls).toEqual([]);

  const asyncTaskHealthTable = page.getByRole('table', { name: 'Async task health overview' });
  const propertyEncryptionHealthRow = asyncTaskHealthTable.getByRole('row', { name: /Property Encryption/ });
  await expect(propertyEncryptionHealthRow).toBeVisible();
  await expect(propertyEncryptionHealthRow.getByText('degraded')).toBeVisible();
  await expect(propertyEncryptionHealthRow.getByText('overview-required')).toBeVisible();
  await expect(propertyEncryptionHealthRow.getByText('CRITICAL')).toBeVisible();
  await expect(propertyEncryptionHealthRow.getByRole('cell').nth(3)).toHaveText('0');
  await expect(page.getByText('Status DEGRADED')).toBeVisible();
  await expect(page.getByText('Risk CRITICAL')).toBeVisible();
});
