import { expect, test } from '@playwright/test';
import { seedBypassSessionE2E } from './helpers/login';

test('Permissions dialog shows effective permission presets (mocked API)', async ({ page }) => {
  test.setTimeout(120_000);

  const nodeId = 'e2e-permissions-node-id';
  const nodeName = 'e2e-permissions-preset-target';

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
      body: JSON.stringify({
        content: [
          {
            id: nodeId,
            name: nodeName,
            path: `/Root/Documents/${nodeName}`,
            nodeType: 'FOLDER',
            parentId: 'root-folder-id',
            size: 0,
            createdBy: 'admin',
            createdDate: new Date().toISOString(),
            lastModifiedBy: 'admin',
            lastModifiedDate: new Date().toISOString(),
            contentType: null,
          },
        ],
        totalElements: 1,
        number: 0,
        size: 50,
      }),
    });
  });

  await page.route(`**/api/v1/nodes/${nodeId}`, async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        id: nodeId,
        name: nodeName,
        path: `/Root/Documents/${nodeName}`,
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

  await page.route('**/api/v1/security/nodes/*/permissions', async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify([
        // Exact EDITOR
        { authority: 'preset-editor', authorityType: 'USER', permission: 'READ', allowed: true, inherited: false },
        { authority: 'preset-editor', authorityType: 'USER', permission: 'WRITE', allowed: true, inherited: false },
        { authority: 'preset-editor', authorityType: 'USER', permission: 'CHECKOUT', allowed: true, inherited: false },
        { authority: 'preset-editor', authorityType: 'USER', permission: 'CHECKIN', allowed: true, inherited: false },
        { authority: 'preset-editor', authorityType: 'USER', permission: 'CANCEL_CHECKOUT', allowed: true, inherited: false },

        // Custom user (closest to EDITOR, missing version ops)
        { authority: 'custom-user', authorityType: 'USER', permission: 'READ', allowed: true, inherited: false },
        { authority: 'custom-user', authorityType: 'USER', permission: 'WRITE', allowed: true, inherited: false },

        // Exact CONSUMER
        { authority: 'preset-consumer', authorityType: 'USER', permission: 'READ', allowed: true, inherited: false },

        // Exact CONTRIBUTOR group
        { authority: 'contributors', authorityType: 'GROUP', permission: 'READ', allowed: true, inherited: true },
        { authority: 'contributors', authorityType: 'GROUP', permission: 'CREATE_CHILDREN', allowed: true, inherited: true },
        { authority: 'contributors', authorityType: 'GROUP', permission: 'CHECKOUT', allowed: true, inherited: true },
        { authority: 'contributors', authorityType: 'GROUP', permission: 'CHECKIN', allowed: true, inherited: true },
        { authority: 'contributors', authorityType: 'GROUP', permission: 'CANCEL_CHECKOUT', allowed: true, inherited: true },
      ]),
    });
  });

  await page.route('**/api/v1/security/permission-sets/metadata', async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify([
        {
          name: 'COORDINATOR',
          label: 'Coordinator',
          description: 'Full control including permission changes',
          order: 1,
          permissions: [
            'READ',
            'WRITE',
            'DELETE',
            'CREATE_CHILDREN',
            'DELETE_CHILDREN',
            'EXECUTE',
            'CHANGE_PERMISSIONS',
            'TAKE_OWNERSHIP',
            'CHECKOUT',
            'CHECKIN',
            'CANCEL_CHECKOUT',
            'APPROVE',
            'REJECT',
          ],
        },
        {
          name: 'EDITOR',
          label: 'Editor',
          description: 'Edit content and manage versions',
          order: 2,
          permissions: ['READ', 'WRITE', 'CHECKOUT', 'CHECKIN', 'CANCEL_CHECKOUT'],
        },
        {
          name: 'CONTRIBUTOR',
          label: 'Contributor',
          description: 'Create content and add versions',
          order: 3,
          permissions: ['READ', 'CREATE_CHILDREN', 'CHECKOUT', 'CHECKIN', 'CANCEL_CHECKOUT'],
        },
        {
          name: 'CONSUMER',
          label: 'Consumer',
          description: 'Read-only access',
          order: 4,
          permissions: ['READ'],
        },
      ]),
    });
  });

  await page.route('**/api/v1/security/permission-sets', async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        COORDINATOR: [
          'READ',
          'WRITE',
          'DELETE',
          'CREATE_CHILDREN',
          'DELETE_CHILDREN',
          'EXECUTE',
          'CHANGE_PERMISSIONS',
          'TAKE_OWNERSHIP',
          'CHECKOUT',
          'CHECKIN',
          'CANCEL_CHECKOUT',
          'APPROVE',
          'REJECT',
        ],
        EDITOR: ['READ', 'WRITE', 'CHECKOUT', 'CHECKIN', 'CANCEL_CHECKOUT'],
        CONTRIBUTOR: ['READ', 'CREATE_CHILDREN', 'CHECKOUT', 'CHECKIN', 'CANCEL_CHECKOUT'],
        CONSUMER: ['READ'],
      }),
    });
  });

  await page.route('**/api/v1/security/permission-templates', async (route) => {
    await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify([]) });
  });

  await page.route('**/api/v1/security/nodes/*/permission-diagnostics**', async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        nodeId,
        username: 'admin',
        permission: 'READ',
        allowed: true,
        reason: 'ADMIN',
        dynamicAuthority: null,
        allowedAuthorities: [],
        deniedAuthorities: [],
      }),
    });
  });

  await page.route('**/api/v1/users**', async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({ content: [], totalElements: 0, totalPages: 0, number: 0, size: 100 }),
    });
  });

  await page.route('**/api/v1/groups**', async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({ content: [], totalElements: 0, totalPages: 0, number: 0, size: 100 }),
    });
  });

  await page.route('**/api/v1/favorites/batch/check', async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({ favoritedNodeIds: [] }),
    });
  });

  // When running against a static build server (no SPA rewrite), avoid deep links.
  await page.goto('/', { waitUntil: 'domcontentloaded' });
  await expect(page.getByRole('button', { name: 'Account menu' })).toBeVisible();

  const actionButton = page.getByLabel(`Actions for ${nodeName}`).first();
  await expect(actionButton).toBeVisible();
  await actionButton.click();
  await page.getByRole('menuitem', { name: 'Permissions' }).click();

  const dialog = page.getByRole('dialog').filter({ hasText: 'Manage Permissions' });
  await expect(dialog).toBeVisible({ timeout: 60_000 });
  await expect(dialog.getByText('Permission presets (Alfresco-style)')).toBeVisible();
  await expect(dialog.getByText(/Presets map common roles/i)).toBeVisible();

  const editorRow = dialog.locator('tr', { hasText: 'preset-editor' });
  await expect(editorRow).toBeVisible();
  await expect(editorRow.getByRole('cell').nth(1)).toContainText('Editor');

  const customRow = dialog.locator('tr', { hasText: 'custom-user' });
  await expect(customRow).toBeVisible();
  await expect(customRow.getByRole('cell').nth(1)).toContainText('Custom');
  await expect(customRow.getByRole('cell').nth(1)).toContainText('Closest: Editor');

  const consumerRow = dialog.locator('tr', { hasText: 'preset-consumer' });
  await expect(consumerRow).toBeVisible();
  await expect(consumerRow.getByRole('cell').nth(1)).toContainText('Consumer');

  await dialog.getByRole('tab', { name: 'Groups' }).click();
  const groupRow = dialog.locator('tr', { hasText: 'contributors' });
  await expect(groupRow).toBeVisible();
  await expect(groupRow.getByRole('cell').nth(1)).toContainText('Contributor');
});
