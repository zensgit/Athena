import { expect, test } from '@playwright/test';
import { seedBypassSessionE2E } from './helpers/login';
import { mockKeycloakUnreachable } from './helpers/keycloakMock';

const hold1Summary = {
  id: 'hold-1',
  name: 'Finance Q1 2026',
  description: 'Finance records hold',
  status: 'ACTIVE',
  itemCount: 3,
  createdBy: 'admin',
  createdDate: '2026-04-01T10:00:00',
  releasedAt: null,
  releasedBy: null,
};

const hold2Summary = {
  id: 'hold-2',
  name: 'Compliance Audit',
  description: null,
  status: 'RELEASED',
  itemCount: 1,
  createdBy: 'admin',
  createdDate: '2026-03-01T10:00:00',
  releasedAt: '2026-04-10T12:00:00',
  releasedBy: 'admin',
};

const hold1Detail = {
  ...hold1Summary,
  releaseComment: null,
  items: [
    {
      nodeId: 'node-1',
      nodeType: 'DOCUMENT',
      nodeName: 'contract.pdf',
      nodePath: '/sites/finance/contract.pdf',
      addedBy: 'admin',
      addedAt: '2026-04-01T10:05:00',
    },
  ],
};

const hold3Detail = {
  id: 'hold-3',
  name: 'New Hold',
  description: 'Test',
  status: 'ACTIVE',
  itemCount: 0,
  createdBy: 'admin',
  createdDate: '2026-04-26T00:00:00',
  releasedAt: null,
  releasedBy: null,
  releaseComment: null,
  items: [],
};

async function setupRoutes(page: Parameters<typeof seedBypassSessionE2E>[0]) {
  await mockKeycloakUnreachable(page);
  await seedBypassSessionE2E(page, 'admin', 'e2e-token');

  await page.route('**/api/v1/legal-holds/hold-1', async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify(hold1Detail),
    });
  });

  await page.route('**/api/v1/legal-holds', async (route) => {
    if (route.request().method() === 'POST') {
      await route.fulfill({
        status: 201,
        contentType: 'application/json',
        body: JSON.stringify(hold3Detail),
      });
    } else {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify([hold1Summary, hold2Summary]),
      });
    }
  });
}

test('shows legal holds list', async ({ page }) => {
  test.setTimeout(60_000);

  await setupRoutes(page);
  await page.goto('/admin/legal-holds');

  await expect(page.getByText('Finance Q1 2026')).toBeVisible();
  await expect(page.getByText('Compliance Audit')).toBeVisible();
});

test('shows ACTIVE and RELEASED status chips', async ({ page }) => {
  test.setTimeout(60_000);

  await setupRoutes(page);
  await page.goto('/admin/legal-holds');

  await expect(page.getByText('Finance Q1 2026')).toBeVisible();
  await expect(page.getByText('ACTIVE')).toBeVisible();
  await expect(page.getByText('RELEASED')).toBeVisible();
});

test('clicking a hold shows its detail', async ({ page }) => {
  test.setTimeout(60_000);

  await setupRoutes(page);
  await page.goto('/admin/legal-holds');

  await expect(page.getByText('Finance Q1 2026')).toBeVisible();
  await page.getByText('Finance Q1 2026').click();

  // exact: true prevents matching the nodePath "/sites/finance/contract.pdf" which also contains "contract.pdf"
  await expect(page.getByText('contract.pdf', { exact: true })).toBeVisible();
});

test('create hold dialog opens and submits', async ({ page }) => {
  test.setTimeout(60_000);

  await setupRoutes(page);

  let postCalled = false;
  await page.route('**/api/v1/legal-holds', async (route) => {
    if (route.request().method() === 'POST') {
      postCalled = true;
      await route.fulfill({
        status: 201,
        contentType: 'application/json',
        body: JSON.stringify(hold3Detail),
      });
    } else {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify([hold1Summary, hold2Summary]),
      });
    }
  });

  await page.goto('/admin/legal-holds');

  await page.getByRole('button', { name: 'Create Hold' }).click();

  await expect(page.getByRole('dialog', { name: 'Create Legal Hold' })).toBeVisible();

  await page.getByLabel('Name').fill('New Hold');
  await page.getByLabel('Description').fill('Test');

  await page.getByRole('button', { name: 'Create' }).click();

  // Use first() since "New Hold" may also appear in a toast notification simultaneously
  await expect(page.getByText('New Hold').first()).toBeVisible();
  expect(postCalled).toBe(true);
});
