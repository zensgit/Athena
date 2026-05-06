import React from 'react';
import { fireEvent, render, screen, waitFor, within } from '@testing-library/react';
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
    listRewrapJobs: jest.fn(),
    dryRunBackfill: jest.fn(),
    dryRunRewrap: jest.fn(),
    planBackfillJob: jest.fn(),
    planRewrapJob: jest.fn(),
    runBackfillJob: jest.fn(),
    cancelBackfillJob: jest.fn(),
    runRewrapJob: jest.fn(),
    cancelRewrapJob: jest.fn(),
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

const plannedRewrapJob = {
  id: 'rewrap-1',
  status: 'PLANNED' as const,
  targetKeyVersion: 'v1',
  requestedBy: 'admin',
  requestedAt: '2026-04-30T00:00:00Z',
  startedAt: null,
  finishedAt: null,
  candidateNodeCount: 3,
  encryptedPropertyValueCount: 5,
  valuesAlreadyOnTargetKeyCount: 2,
  valuesRequiringRewrapCount: 3,
  unversionedOrMalformedValueCount: 0,
  processedValueCount: 0,
  rewrappedValueCount: 0,
  skippedValueCount: 0,
  failedValueCount: 0,
  keyVersionCounts: [{ keyVersion: 'v0', encryptedPropertyValueCount: 3 }],
  missingSourceKeyVersions: [],
  warnings: [],
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
  mockedPropertyEncryptionService.listRewrapJobs.mockResolvedValue([plannedRewrapJob]);
});

test('loads property encryption status, definitions, and jobs', async () => {
  render(<PropertyEncryptionOperationsPage />);

  expect(await screen.findByText('Property Encryption')).toBeTruthy();
  expect(await screen.findByText('Secret crypto enabled')).toBeTruthy();
  expect(screen.getByText('cm:secretCode')).toBeTruthy();
  const backfillTable = screen.getByRole('table', { name: 'Property encryption backfill jobs' });
  const rewrapTable = screen.getByRole('table', { name: 'Property encryption rewrap jobs' });
  expect(within(backfillTable).getByText('PLANNED')).toBeTruthy();
  expect(within(rewrapTable).getByText('PLANNED')).toBeTruthy();

  expect(mockedPropertyEncryptionService.getStatus).toHaveBeenCalledTimes(1);
  expect(mockedPropertyEncryptionService.listDefinitions).toHaveBeenCalledTimes(1);
  expect(mockedPropertyEncryptionService.listBackfillJobs).toHaveBeenCalledWith(10);
  expect(mockedPropertyEncryptionService.listRewrapJobs).toHaveBeenCalledWith(10);
});

test('runs dry-run, plan, run, and cancel actions for backfill and rewrap jobs', async () => {
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
  mockedPropertyEncryptionService.planRewrapJob.mockResolvedValue(plannedRewrapJob);
  mockedPropertyEncryptionService.runBackfillJob.mockResolvedValue({
    ...plannedJob,
    status: 'RUNNING',
    startedAt: '2026-04-30T00:01:00Z',
  });
  mockedPropertyEncryptionService.cancelBackfillJob.mockResolvedValue({
    ...plannedJob,
    status: 'CANCEL_REQUESTED',
  });
  mockedPropertyEncryptionService.runRewrapJob.mockResolvedValue({
    ...plannedRewrapJob,
    status: 'RUNNING',
    startedAt: '2026-04-30T00:01:00Z',
  });
  mockedPropertyEncryptionService.cancelRewrapJob.mockResolvedValue({
    ...plannedRewrapJob,
    status: 'CANCEL_REQUESTED',
  });

  render(<PropertyEncryptionOperationsPage />);
  await screen.findAllByText('PLANNED');
  const backfillTable = screen.getByRole('table', { name: 'Property encryption backfill jobs' });
  const rewrapTable = screen.getByRole('table', { name: 'Property encryption rewrap jobs' });

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
  expect(await screen.findByText(/Rewrap dry-run: executable/)).toBeTruthy();
  expect(mockedPropertyEncryptionService.dryRunRewrap).toHaveBeenCalledWith('v1');

  fireEvent.click(screen.getByRole('button', { name: 'Plan Rewrap Job' }));
  await waitFor(() => expect(mockedPropertyEncryptionService.planRewrapJob).toHaveBeenCalledWith('v1'));

  await waitFor(() => {
    const runBackfillButton = within(backfillTable).getByRole('button', { name: 'Run Backfill' }) as HTMLButtonElement;
    expect(runBackfillButton.disabled).toBe(false);
  });
  fireEvent.click(within(backfillTable).getByRole('button', { name: 'Run Backfill' }));
  await waitFor(() => expect(mockedPropertyEncryptionService.runBackfillJob).toHaveBeenCalledWith('job-1'));
  await waitFor(() => {
    const cancelButton = within(backfillTable).getByRole('button', { name: 'Cancel Backfill' }) as HTMLButtonElement;
    expect(cancelButton.disabled).toBe(false);
  });

  fireEvent.click(within(backfillTable).getByRole('button', { name: 'Cancel Backfill' }));
  await waitFor(() => expect(mockedPropertyEncryptionService.cancelBackfillJob).toHaveBeenCalledWith('job-1'));
  await waitFor(() => {
    const cancelButton = within(backfillTable).getByRole('button', { name: 'Cancel Backfill' }) as HTMLButtonElement;
    expect(cancelButton.disabled).toBe(true);
  });

  await waitFor(() => {
    const runRewrapButton = within(rewrapTable).getByRole('button', { name: 'Run Rewrap' }) as HTMLButtonElement;
    expect(runRewrapButton.disabled).toBe(false);
  });
  fireEvent.click(within(rewrapTable).getByRole('button', { name: 'Run Rewrap' }));
  await waitFor(() => expect(mockedPropertyEncryptionService.runRewrapJob).toHaveBeenCalledWith('rewrap-1'));

  await waitFor(() => {
    const cancelRewrapButton = within(rewrapTable).getByRole('button', { name: 'Cancel Rewrap' }) as HTMLButtonElement;
    expect(cancelRewrapButton.disabled).toBe(false);
  });
  fireEvent.click(within(rewrapTable).getByRole('button', { name: 'Cancel Rewrap' }));
  await waitFor(() => expect(mockedPropertyEncryptionService.cancelRewrapJob).toHaveBeenCalledWith('rewrap-1'));
  await waitFor(() => {
    const cancelRewrapButton = within(rewrapTable).getByRole('button', { name: 'Cancel Rewrap' }) as HTMLButtonElement;
    expect(cancelRewrapButton.disabled).toBe(true);
  });

  expect(toast.success).toHaveBeenCalledWith('Backfill dry-run completed.');
}, 15000);
