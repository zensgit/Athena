import React from 'react';
import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { toast } from 'react-toastify';
import PropertyEncryptionOperationsPage from './PropertyEncryptionOperationsPage';
import propertyEncryptionService from 'services/propertyEncryptionService';

jest.mock('react-toastify', () => ({
  toast: {
    error: jest.fn(),
    success: jest.fn(),
  },
}));

jest.mock('services/propertyEncryptionService', () => ({
  __esModule: true,
  default: {
    getStatus: jest.fn(),
    listDefinitions: jest.fn(),
    listBackfillJobs: jest.fn(),
    dryRunBackfill: jest.fn(),
    dryRunRewrap: jest.fn(),
    planBackfillJob: jest.fn(),
    runBackfillJob: jest.fn(),
    cancelBackfillJob: jest.fn(),
  },
}));

const mockedPropertyEncryptionService = propertyEncryptionService as jest.Mocked<typeof propertyEncryptionService>;

const plannedJob = {
  id: 'job-1',
  status: 'PLANNED' as const,
  targetKeyVersion: 'v1',
  requestedBy: 'admin',
  requestedAt: '2026-04-30T00:00:00Z',
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
  createdAt: '2026-04-30T00:00:00Z',
  updatedAt: null,
};

beforeEach(() => {
  jest.clearAllMocks();
  mockedPropertyEncryptionService.getStatus.mockResolvedValue({
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
  mockedPropertyEncryptionService.listDefinitions.mockResolvedValue([
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
  mockedPropertyEncryptionService.listBackfillJobs.mockResolvedValue([plannedJob]);
});

test('loads property encryption status, definitions, and jobs', async () => {
  render(<PropertyEncryptionOperationsPage />);

  expect(await screen.findByText('Property Encryption')).toBeTruthy();
  expect(await screen.findByText('Secret crypto enabled')).toBeTruthy();
  expect(screen.getByText('cm:secretCode')).toBeTruthy();
  expect(screen.getByText('PLANNED')).toBeTruthy();

  expect(mockedPropertyEncryptionService.getStatus).toHaveBeenCalledTimes(1);
  expect(mockedPropertyEncryptionService.listDefinitions).toHaveBeenCalledTimes(1);
  expect(mockedPropertyEncryptionService.listBackfillJobs).toHaveBeenCalledWith(10);
});

test('runs dry-run, plan, run, and cancel actions', async () => {
  mockedPropertyEncryptionService.dryRunBackfill.mockResolvedValue({
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
  mockedPropertyEncryptionService.dryRunRewrap.mockResolvedValue({
    targetKeyVersion: 'v1',
    targetKeyConfigured: true,
    secretCryptoEnabled: true,
    candidateNodeCount: 3,
    encryptedPropertyValueCount: 5,
    valuesAlreadyOnTargetKeyCount: 2,
    valuesRequiringRewrapCount: 3,
    unversionedOrMalformedValueCount: 0,
    keyVersionCounts: [],
    missingSourceKeyVersions: [],
    warnings: [],
    executable: true,
  });
  mockedPropertyEncryptionService.planBackfillJob.mockResolvedValue(plannedJob);
  mockedPropertyEncryptionService.runBackfillJob.mockResolvedValue({
    ...plannedJob,
    status: 'RUNNING',
    startedAt: '2026-04-30T00:01:00Z',
  });
  mockedPropertyEncryptionService.cancelBackfillJob.mockResolvedValue({
    ...plannedJob,
    status: 'CANCEL_REQUESTED',
  });

  render(<PropertyEncryptionOperationsPage />);
  await screen.findByText('PLANNED');

  fireEvent.click(screen.getByRole('button', { name: 'Backfill Dry Run' }));
  expect(await screen.findByText(/Backfill dry-run: executable/)).toBeTruthy();
  expect(mockedPropertyEncryptionService.dryRunBackfill).toHaveBeenCalledWith('v1');

  fireEvent.click(screen.getByRole('button', { name: 'Plan Backfill Job' }));
  await waitFor(() => expect(mockedPropertyEncryptionService.planBackfillJob).toHaveBeenCalledWith('v1'));
  await waitFor(() => {
    const rewrapButton = screen.getByRole('button', { name: 'Rewrap Dry Run' }) as HTMLButtonElement;
    expect(rewrapButton.disabled).toBe(false);
  });

  fireEvent.click(screen.getByRole('button', { name: 'Rewrap Dry Run' }));
  expect(await screen.findByText(/Rewrap dry-run only/)).toBeTruthy();
  expect(mockedPropertyEncryptionService.dryRunRewrap).toHaveBeenCalledWith('v1');

  fireEvent.click(screen.getByRole('button', { name: 'Run' }));
  await waitFor(() => expect(mockedPropertyEncryptionService.runBackfillJob).toHaveBeenCalledWith('job-1'));
  await waitFor(() => {
    const cancelButton = screen.getByRole('button', { name: 'Cancel' }) as HTMLButtonElement;
    expect(cancelButton.disabled).toBe(false);
  });

  fireEvent.click(screen.getByRole('button', { name: 'Cancel' }));
  await waitFor(() => expect(mockedPropertyEncryptionService.cancelBackfillJob).toHaveBeenCalledWith('job-1'));

  expect(toast.success).toHaveBeenCalledWith('Backfill dry-run completed.');
});
