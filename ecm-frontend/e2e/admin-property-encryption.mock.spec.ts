import { expect, test } from '@playwright/test';
import { seedBypassSessionE2E } from './helpers/login';
import { mockKeycloakUnreachable } from './helpers/keycloakMock';

const json = (body: unknown) => JSON.stringify(body);

test('property encryption admin page supports mocked dry-run and backfill job operations', async ({ page }) => {
  test.setTimeout(60_000);
  await mockKeycloakUnreachable(page);
  await seedBypassSessionE2E(page, 'admin', 'e2e-token');

  const plannedJob = {
    id: 'job-1',
    status: 'PLANNED',
    targetKeyVersion: 'v1',
    requestedBy: 'admin',
    requestedAt: '2026-05-05T00:00:00Z',
    startedAt: null,
    finishedAt: null,
    encryptedPropertyDefinitionCount: 1,
    plaintextValueCount: 1,
    alreadyEncryptedValueCount: 0,
    dualStorageConflictValueCount: 0,
    readyValueCount: 1,
    orphanEncryptedValueCount: 0,
    processedValueCount: 0,
    migratedValueCount: 0,
    skippedValueCount: 0,
    failedValueCount: 0,
    warnings: [],
    definitionCounts: [],
    lastError: null,
    createdAt: '2026-05-05T00:00:00Z',
    updatedAt: null,
  };
  let currentJob = plannedJob;

  await page.route('**/api/v1/**', async (route) => {
    const request = route.request();
    const method = request.method().toUpperCase();
    const pathname = new URL(request.url()).pathname;

    const fulfillJson = async (body: unknown, status = 200) => route.fulfill({
      status,
      contentType: 'application/json',
      body: json(body),
    });

    if (pathname.endsWith('/notifications/unread-count')) {
      await fulfillJson({ count: 0 });
      return;
    }

    if (pathname.endsWith('/admin/property-encryption/status') && method === 'GET') {
      await fulfillJson({
        secretCryptoEnabled: true,
        activeKeyVersion: 'v1',
        activeKeyConfigured: true,
        configuredKeyVersions: ['v1'],
        encryptedPropertyDefinitionCount: 1,
        encryptedTypePropertyDefinitionCount: 1,
        encryptedAspectPropertyDefinitionCount: 0,
        nodesWithEncryptedPropertiesCount: 3,
        encryptedPropertyValueCount: 5,
        warnings: [],
      });
      return;
    }

    if (pathname.endsWith('/admin/property-encryption/definitions') && method === 'GET') {
      await fulfillJson([
        {
          id: 'definition-1',
          qualifiedName: 'cm:secretCode',
          name: 'secretCode',
          title: 'Secret Code',
          ownerKind: 'TYPE',
          ownerQName: 'cm:folder',
          dataType: 'TEXT',
          mandatory: false,
          multiValued: false,
          indexed: true,
        },
      ]);
      return;
    }

    if (pathname.endsWith('/admin/property-encryption/backfill-jobs') && method === 'GET') {
      await fulfillJson([currentJob]);
      return;
    }

    if (pathname.endsWith('/admin/property-encryption/backfill-jobs/dry-run') && method === 'POST') {
      await fulfillJson({
        targetKeyVersion: 'v1',
        targetKeyConfigured: true,
        secretCryptoEnabled: true,
        encryptedPropertyDefinitionCount: 1,
        plaintextValueCount: 1,
        alreadyEncryptedValueCount: 0,
        dualStorageConflictValueCount: 0,
        readyValueCount: 1,
        orphanEncryptedValueCount: 0,
        definitionCounts: [],
        warnings: [],
        executable: true,
      });
      return;
    }

    if (pathname.endsWith('/admin/property-encryption/rewrap-jobs/dry-run') && method === 'POST') {
      await fulfillJson({
        targetKeyVersion: 'v1',
        targetKeyConfigured: true,
        secretCryptoEnabled: true,
        candidateNodeCount: 3,
        encryptedPropertyValueCount: 5,
        valuesAlreadyOnTargetKeyCount: 2,
        valuesRequiringRewrapCount: 3,
        unversionedOrMalformedValueCount: 0,
        keyVersionCounts: [{ keyVersion: 'v0', encryptedPropertyValueCount: 3 }],
        missingSourceKeyVersions: [],
        warnings: [],
        executable: true,
      });
      return;
    }

    if (pathname.endsWith('/admin/property-encryption/backfill-jobs/plan') && method === 'POST') {
      currentJob = plannedJob;
      await fulfillJson(currentJob, 201);
      return;
    }

    if (pathname.endsWith('/admin/property-encryption/backfill-jobs/job-1/run') && method === 'POST') {
      currentJob = {
        ...currentJob,
        status: 'RUNNING',
        startedAt: '2026-05-05T00:01:00Z',
      };
      await fulfillJson(currentJob, 202);
      return;
    }

    if (pathname.endsWith('/admin/property-encryption/backfill-jobs/job-1/cancel') && method === 'POST') {
      currentJob = {
        ...currentJob,
        status: 'CANCEL_REQUESTED',
      };
      await fulfillJson(currentJob);
      return;
    }

    await fulfillJson({ message: `Not mocked: ${method} ${pathname}` }, 404);
  });

  await page.goto('/admin/property-encryption', { waitUntil: 'domcontentloaded' });

  await expect(page.getByRole('heading', { name: 'Property Encryption' })).toBeVisible();
  await expect(page.getByText('Secret crypto enabled')).toBeVisible();
  await expect(page.getByText('cm:secretCode')).toBeVisible();
  const jobsTable = page.getByRole('table', { name: 'Property encryption backfill jobs' });
  await expect(jobsTable.getByText('PLANNED', { exact: true })).toBeVisible();

  await page.getByRole('button', { name: 'Backfill Dry Run' }).click();
  await expect(page.getByText(/Backfill dry-run: executable/)).toBeVisible();

  await page.getByRole('button', { name: 'Rewrap Dry Run' }).click();
  await expect(page.getByText(/Rewrap dry-run only/)).toBeVisible();

  await page.getByRole('button', { name: 'Plan Backfill Job' }).click();
  await expect(jobsTable.getByText('PLANNED', { exact: true })).toBeVisible();

  await jobsTable.getByRole('button', { name: 'Run', exact: true }).click();
  await expect(jobsTable.getByText('RUNNING', { exact: true })).toBeVisible();

  await jobsTable.getByRole('button', { name: 'Cancel', exact: true }).click();
  await expect(jobsTable.getByText('CANCEL_REQUESTED', { exact: true })).toBeVisible();
});
