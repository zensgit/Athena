import api from './api';
import propertyEncryptionService, {
  EncryptedPropertyDefinitionSummary,
  PROPERTY_ENCRYPTION_UNEXPECTED_RESPONSE_MESSAGE,
  PropertyEncryptionBackfillDryRunResult,
  PropertyEncryptionBackfillJobDto,
  PropertyEncryptionRewrapDryRunResult,
  PropertyEncryptionRewrapJobDto,
  PropertyEncryptionStatus,
} from './propertyEncryptionService';

jest.mock('./api', () => ({
  __esModule: true,
  default: {
    get: jest.fn(),
    post: jest.fn(),
  },
}));

const mockedApi = api as jest.Mocked<typeof api>;

const status: PropertyEncryptionStatus = {
  secretCryptoEnabled: true,
  activeKeyVersion: 'v2',
  activeKeyConfigured: true,
  configuredKeyVersions: ['v1', 'v2'],
  encryptedPropertyDefinitionCount: 2,
  encryptedTypePropertyDefinitionCount: 1,
  encryptedAspectPropertyDefinitionCount: 1,
  nodesWithEncryptedPropertiesCount: 3,
  encryptedPropertyValueCount: 5,
  warnings: [],
};

const definition: EncryptedPropertyDefinitionSummary = {
  id: 'prop-1',
  qualifiedName: 'acme:secretCode',
  name: 'secretCode',
  title: null,
  ownerKind: 'TYPE',
  ownerQName: 'acme:caseFile',
  dataType: 'TEXT',
  mandatory: false,
  multiValued: false,
  indexed: true,
};

const rewrapDryRun: PropertyEncryptionRewrapDryRunResult = {
  targetKeyVersion: 'v2',
  targetKeyConfigured: true,
  secretCryptoEnabled: true,
  candidateNodeCount: 2,
  encryptedPropertyValueCount: 5,
  valuesAlreadyOnTargetKeyCount: 1,
  valuesRequiringRewrapCount: 4,
  unversionedOrMalformedValueCount: 0,
  keyVersionCounts: [{ keyVersion: 'v1', encryptedPropertyValueCount: 4 }],
  missingSourceKeyVersions: [],
  warnings: [],
  executable: true,
};

const backfillDryRun: PropertyEncryptionBackfillDryRunResult = {
  targetKeyVersion: 'v2',
  targetKeyConfigured: true,
  secretCryptoEnabled: true,
  encryptedPropertyDefinitionCount: 2,
  plaintextValueCount: 4,
  alreadyEncryptedValueCount: 1,
  dualStorageConflictValueCount: 0,
  readyValueCount: 4,
  orphanEncryptedValueCount: 0,
  definitionCounts: [
    {
      qualifiedName: 'acme:secretCode',
      ownerKind: 'TYPE',
      ownerQName: 'acme:caseFile',
      plaintextValueCount: 4,
      alreadyEncryptedValueCount: 1,
      dualStorageConflictValueCount: 0,
      readyValueCount: 4,
    },
  ],
  warnings: [],
  executable: true,
};

const rewrapJob: PropertyEncryptionRewrapJobDto = {
  id: 'rewrap-1',
  status: 'PLANNED',
  targetKeyVersion: 'v2',
  requestedBy: 'admin',
  requestedAt: '2026-05-18T01:00:00Z',
  startedAt: null,
  finishedAt: null,
  candidateNodeCount: 2,
  encryptedPropertyValueCount: 5,
  valuesAlreadyOnTargetKeyCount: 1,
  valuesRequiringRewrapCount: 4,
  unversionedOrMalformedValueCount: 0,
  processedValueCount: 0,
  rewrappedValueCount: 0,
  skippedValueCount: 0,
  failedValueCount: 0,
  keyVersionCounts: [{ keyVersion: 'v1', encryptedPropertyValueCount: 4 }],
  missingSourceKeyVersions: [],
  warnings: [],
  lastError: null,
  createdAt: '2026-05-18T01:00:00Z',
  updatedAt: null,
};

const backfillJob: PropertyEncryptionBackfillJobDto = {
  id: 'backfill-1',
  status: 'PLANNED',
  targetKeyVersion: 'v2',
  requestedBy: 'admin',
  requestedAt: '2026-05-18T01:00:00Z',
  startedAt: null,
  finishedAt: null,
  encryptedPropertyDefinitionCount: 2,
  plaintextValueCount: 4,
  alreadyEncryptedValueCount: 1,
  dualStorageConflictValueCount: 0,
  readyValueCount: 4,
  orphanEncryptedValueCount: 0,
  processedValueCount: 0,
  migratedValueCount: 0,
  skippedValueCount: 0,
  failedValueCount: 0,
  warnings: [],
  definitionCounts: [
    {
      qualifiedName: 'acme:secretCode',
      ownerKind: 'TYPE',
      ownerQName: 'acme:caseFile',
      plaintextValueCount: 4,
      alreadyEncryptedValueCount: 1,
      dualStorageConflictValueCount: 0,
      readyValueCount: 4,
    },
  ],
  lastError: null,
  createdAt: '2026-05-18T01:00:00Z',
  updatedAt: null,
};

describe('propertyEncryptionService response guards', () => {
  beforeEach(() => {
    jest.clearAllMocks();
  });

  it('loads status and encrypted definitions from admin property encryption endpoints', async () => {
    mockedApi.get.mockResolvedValueOnce(status).mockResolvedValueOnce([definition]);

    await expect(propertyEncryptionService.getStatus()).resolves.toEqual(status);
    await expect(propertyEncryptionService.listDefinitions()).resolves.toEqual([definition]);

    expect(mockedApi.get).toHaveBeenNthCalledWith(1, '/admin/property-encryption/status');
    expect(mockedApi.get).toHaveBeenNthCalledWith(2, '/admin/property-encryption/definitions');
  });

  it('trims target key version for dry-runs and plan requests', async () => {
    mockedApi.post
      .mockResolvedValueOnce(rewrapDryRun)
      .mockResolvedValueOnce(backfillDryRun)
      .mockResolvedValueOnce(rewrapJob)
      .mockResolvedValueOnce(backfillJob);

    await expect(propertyEncryptionService.dryRunRewrap(' v2 ')).resolves.toEqual(rewrapDryRun);
    await expect(propertyEncryptionService.dryRunBackfill(' v2 ')).resolves.toEqual(backfillDryRun);
    await expect(propertyEncryptionService.planRewrapJob(' v2 ')).resolves.toEqual(rewrapJob);
    await expect(propertyEncryptionService.planBackfillJob(' v2 ')).resolves.toEqual(backfillJob);

    expect(mockedApi.post).toHaveBeenNthCalledWith(
      1,
      '/admin/property-encryption/rewrap-jobs/dry-run',
      { targetKeyVersion: 'v2' }
    );
    expect(mockedApi.post).toHaveBeenNthCalledWith(
      2,
      '/admin/property-encryption/backfill-jobs/dry-run',
      { targetKeyVersion: 'v2' }
    );
    expect(mockedApi.post).toHaveBeenNthCalledWith(
      3,
      '/admin/property-encryption/rewrap-jobs/plan',
      { targetKeyVersion: 'v2' }
    );
    expect(mockedApi.post).toHaveBeenNthCalledWith(
      4,
      '/admin/property-encryption/backfill-jobs/plan',
      { targetKeyVersion: 'v2' }
    );
  });

  it('wraps backfill and rewrap job list, run, and cancel endpoints', async () => {
    mockedApi.get.mockResolvedValueOnce([backfillJob]).mockResolvedValueOnce([rewrapJob]);
    mockedApi.post
      .mockResolvedValueOnce({ ...backfillJob, status: 'RUNNING' })
      .mockResolvedValueOnce({ ...backfillJob, status: 'CANCEL_REQUESTED' })
      .mockResolvedValueOnce({ ...rewrapJob, status: 'RUNNING' })
      .mockResolvedValueOnce({ ...rewrapJob, status: 'CANCEL_REQUESTED' });

    await expect(propertyEncryptionService.listBackfillJobs(25)).resolves.toEqual([backfillJob]);
    await expect(propertyEncryptionService.listRewrapJobs(25)).resolves.toEqual([rewrapJob]);
    await expect(propertyEncryptionService.runBackfillJob('job-1', 50)).resolves.toMatchObject({
      status: 'RUNNING',
    });
    await expect(propertyEncryptionService.cancelBackfillJob('job-1')).resolves.toMatchObject({
      status: 'CANCEL_REQUESTED',
    });
    await expect(propertyEncryptionService.runRewrapJob('rewrap-1', 50)).resolves.toMatchObject({
      status: 'RUNNING',
    });
    await expect(propertyEncryptionService.cancelRewrapJob('rewrap-1')).resolves.toMatchObject({
      status: 'CANCEL_REQUESTED',
    });

    expect(mockedApi.get).toHaveBeenNthCalledWith(1, '/admin/property-encryption/backfill-jobs', {
      params: { limit: 25 },
    });
    expect(mockedApi.get).toHaveBeenNthCalledWith(2, '/admin/property-encryption/rewrap-jobs', {
      params: { limit: 25 },
    });
    expect(mockedApi.post).toHaveBeenNthCalledWith(
      1,
      '/admin/property-encryption/backfill-jobs/job-1/run',
      { batchSize: 50 }
    );
    expect(mockedApi.post).toHaveBeenNthCalledWith(
      2,
      '/admin/property-encryption/backfill-jobs/job-1/cancel'
    );
    expect(mockedApi.post).toHaveBeenNthCalledWith(
      3,
      '/admin/property-encryption/rewrap-jobs/rewrap-1/run',
      { batchSize: 50 }
    );
    expect(mockedApi.post).toHaveBeenNthCalledWith(
      4,
      '/admin/property-encryption/rewrap-jobs/rewrap-1/cancel'
    );
  });

  it('rejects HTML fallback status responses', async () => {
    mockedApi.get.mockResolvedValueOnce('<!doctype html><html></html>');

    await expect(propertyEncryptionService.getStatus()).rejects.toThrow(
      PROPERTY_ENCRYPTION_UNEXPECTED_RESPONSE_MESSAGE
    );
  });

  it('rejects malformed status counts', async () => {
    mockedApi.get.mockResolvedValueOnce({
      ...status,
      encryptedPropertyValueCount: Number.NaN,
    });

    await expect(propertyEncryptionService.getStatus()).rejects.toThrow(
      PROPERTY_ENCRYPTION_UNEXPECTED_RESPONSE_MESSAGE
    );
  });

  it('rejects malformed encrypted definitions', async () => {
    mockedApi.get.mockResolvedValueOnce([{ ...definition, indexed: 'yes' }]);

    await expect(propertyEncryptionService.listDefinitions()).rejects.toThrow(
      PROPERTY_ENCRYPTION_UNEXPECTED_RESPONSE_MESSAGE
    );
  });

  it('rejects malformed rewrap dry-run key-version counts', async () => {
    mockedApi.post.mockResolvedValueOnce({
      ...rewrapDryRun,
      keyVersionCounts: [{ keyVersion: 'v1', encryptedPropertyValueCount: '4' }],
    });

    await expect(propertyEncryptionService.dryRunRewrap('v2')).rejects.toThrow(
      PROPERTY_ENCRYPTION_UNEXPECTED_RESPONSE_MESSAGE
    );
  });

  it('rejects malformed backfill dry-run definition counts', async () => {
    mockedApi.post.mockResolvedValueOnce({
      ...backfillDryRun,
      definitionCounts: [{ ...backfillDryRun.definitionCounts[0], readyValueCount: '4' }],
    });

    await expect(propertyEncryptionService.dryRunBackfill('v2')).rejects.toThrow(
      PROPERTY_ENCRYPTION_UNEXPECTED_RESPONSE_MESSAGE
    );
  });

  it('rejects invalid job statuses', async () => {
    mockedApi.post.mockResolvedValueOnce({ ...rewrapJob, status: 'PAUSED' });

    await expect(propertyEncryptionService.planRewrapJob('v2')).rejects.toThrow(
      PROPERTY_ENCRYPTION_UNEXPECTED_RESPONSE_MESSAGE
    );
  });

  it('rejects malformed backfill job snapshots', async () => {
    mockedApi.get.mockResolvedValueOnce([
      {
        ...backfillJob,
        definitionCounts: [{ ...backfillJob.definitionCounts[0], plaintextValueCount: '4' }],
      },
    ]);

    await expect(propertyEncryptionService.listBackfillJobs()).rejects.toThrow(
      PROPERTY_ENCRYPTION_UNEXPECTED_RESPONSE_MESSAGE
    );
  });
});
