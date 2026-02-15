import { expect, test } from '@playwright/test';
import { seedBypassSessionE2E } from './helpers/login';

test.use({ acceptDownloads: true });

test('Admin audit filters persist in URL and export filename is stable (mocked API)', async ({ page }) => {
  test.setTimeout(120_000);

  await seedBypassSessionE2E(page, 'admin', 'e2e-token');

  const nodeId = '11111111-1111-1111-1111-111111111111';
  const fromValue = '2026-02-01T00:00';
  const toValue = '2026-02-02T00:00';

  const json = (body: unknown) => JSON.stringify(body);
  const fulfillJson = (route: any, body: unknown) => route.fulfill({
    status: 200,
    contentType: 'application/json',
    body: json(body),
  });

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
});
