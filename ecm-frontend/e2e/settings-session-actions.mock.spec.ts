import { expect, test } from '@playwright/test';
import { seedBypassSessionE2E } from './helpers/login';

test('Settings: session actions copy token/header and refresh token (mocked API)', async ({ page }) => {
  test.setTimeout(120_000);

  const now = new Date().toISOString();
  const rootFolderId = 'root-folder-id';
  const token = 'e2e-token-abcdefghijklmnopqrstuvwxyz123456';

  await seedBypassSessionE2E(page, 'admin', token);
  await page.addInitScript(() => {
    (window as any).__clipboardWrites = [];
    const clipboard = {
      writeText: async (value: string) => {
        (window as any).__clipboardWrites.push(String(value));
      },
    };
    try {
      Object.defineProperty(navigator, 'clipboard', {
        value: clipboard,
        configurable: true,
      });
    } catch {
      (navigator as any).clipboard = clipboard;
    }
  });

  const json = (body: unknown) => JSON.stringify(body);
  const fulfillJson = (route: any, body: unknown) =>
    route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: json(body),
    });

  await page.route('**/api/v1/**', async (route) => {
    const requestUrl = new URL(route.request().url());
    const pathname = requestUrl.pathname;

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
      await fulfillJson(route, { content: [], totalElements: 0, totalPages: 1, number: 0, size: 50 });
      return;
    }
    if (pathname.endsWith('/favorites/batch/check')) {
      await fulfillJson(route, { favoritedNodeIds: [] });
      return;
    }
    if (pathname.endsWith('/integration/wopi/health')) {
      await fulfillJson(route, {
        enabled: true,
        wopiHostUrl: 'http://localhost:9980',
        discoveryUrl: 'http://localhost:9980/hosting/discovery',
        capabilitiesUrl: 'http://localhost:9980/hosting/capabilities',
        publicUrl: 'http://localhost:9980',
        discovery: {
          reachable: true,
          lastLoadedAtMs: Date.now(),
          cacheTtlSeconds: 300,
          extensionCount: 2,
          sampleActionsByExtension: {
            docx: ['view', 'edit'],
          },
          lastError: null,
        },
        capabilities: {
          reachable: true,
          productName: 'Collabora',
          productVersion: '24.04',
        },
      });
      return;
    }
    if (pathname.endsWith('/mfa/status')) {
      await fulfillJson(route, {
        username: 'admin',
        configured: false,
        enabled: false,
        lastVerifiedAt: null,
        recoveryCodesGeneratedAt: null,
      });
      return;
    }

    await route.fulfill({
      status: 404,
      contentType: 'application/json',
      body: json({ message: `Not mocked: ${pathname}` }),
    });
  });

  await page.goto('/settings', { waitUntil: 'domcontentloaded' });
  await expect(page.getByRole('heading', { name: 'Settings' })).toBeVisible({ timeout: 60_000 });

  const copyAccessTokenButton = page.getByRole('button', { name: 'Copy Access Token' });
  const copyAuthHeaderButton = page.getByRole('button', { name: 'Copy Authorization Header' });
  const refreshTokenButton = page.getByRole('button', { name: 'Refresh Token' });

  await expect(copyAccessTokenButton).toBeEnabled();
  await expect(copyAuthHeaderButton).toBeEnabled();
  await expect(refreshTokenButton).toBeEnabled();

  await copyAccessTokenButton.click();
  await expect(page.getByText('Access token copied')).toBeVisible({ timeout: 30_000 });
  await expect.poll(async () => page.evaluate(() => (window as any).__clipboardWrites?.[0])).toBe(token);

  await copyAuthHeaderButton.click();
  await expect(page.getByText('Authorization header copied')).toBeVisible({ timeout: 30_000 });
  await expect.poll(async () => page.evaluate(() => (window as any).__clipboardWrites?.[1]))
    .toBe(`Authorization: Bearer ${token}`);

  await refreshTokenButton.click();
  await expect(page.getByText('Token refreshed')).toBeVisible({ timeout: 30_000 });
  await expect(page.getByRole('heading', { name: 'Settings' })).toBeVisible();
});
