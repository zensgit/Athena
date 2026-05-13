import api from './api';
import {
  buildReplicationDefinitionRequest,
  TRANSFER_REPLICATION_UNEXPECTED_RESPONSE_MESSAGE,
  ReplicationDefinitionDto,
  ReplicationJobDto,
  TransferReceiverDto,
  TransferTargetDto,
  transferReplicationService,
} from './transferReplicationService';

jest.mock('./api', () => ({
  __esModule: true,
  default: {
    get: jest.fn(),
    post: jest.fn(),
    put: jest.fn(),
    delete: jest.fn(),
  },
}));

const mockedApi = api as jest.Mocked<typeof api>;

const target: TransferTargetDto = {
  id: 'target-id',
  name: 'Loopback target',
  description: null,
  transportType: 'LOOPBACK',
  targetFolderId: 'target-folder-id',
  targetFolderName: 'Target Folder',
  endpointUrl: null,
  endpointPath: null,
  authType: 'NONE',
  authUsername: null,
  authSecretConfigured: false,
  enabled: true,
  verificationStatus: 'VERIFIED',
  verificationMessage: null,
  remoteRepositoryId: null,
  lastVerifiedAt: null,
  createdAt: '2026-05-13T00:00:00Z',
  updatedAt: null,
};

const receiver: TransferReceiverDto = {
  id: 'receiver-id',
  name: 'receiver-east',
  description: null,
  rootFolderId: 'folder-id',
  rootFolderName: 'Inbound Root',
  authType: 'BEARER',
  authUsername: null,
  authSecretConfigured: true,
  enabled: true,
  verificationStatus: 'VERIFIED',
  verificationMessage: null,
  lastVerifiedAt: null,
  lastAccessStatus: 'SUCCESS',
  lastAccessMessage: null,
  lastAccessedAt: null,
  createdAt: '2026-05-13T00:00:00Z',
  updatedAt: null,
};

const definition: ReplicationDefinitionDto = {
  id: 'definition-id',
  name: 'Nightly definition',
  description: null,
  sourceNodeId: 'source-node-id',
  sourceNodeName: 'Source Folder',
  transferTargetId: target.id,
  transferTargetName: target.name,
  includeChildren: true,
  enabled: true,
  conflictPolicy: 'RENAME',
  cronExpression: null,
  scheduleTimezone: 'UTC',
  nextRunAt: null,
  autoRetryEnabled: false,
  maxRetryAttempts: 3,
  retryBackoffMinutes: 15,
  jobRetentionDays: 30,
  lastRunAt: null,
  createdAt: '2026-05-13T00:00:00Z',
  updatedAt: null,
};

const job: ReplicationJobDto = {
  id: 'job-id',
  definitionId: definition.id,
  transferTargetId: target.id,
  sourceNodeId: definition.sourceNodeId,
  retryOfJobId: null,
  attemptNumber: 1,
  scheduledFor: null,
  copiedNodeId: null,
  userId: 'admin',
  status: 'COMPLETED',
  lastMessage: 'Completed',
  transportStatus: 'SUCCESS',
  transportMessage: null,
  errorLog: null,
  entryReport: null,
  reportTruncated: false,
  lastAttemptedAt: null,
  startedAt: null,
  completedAt: null,
  createdAt: '2026-05-13T00:00:00Z',
  updatedAt: null,
};

describe('transferReplicationService receiver registry wrappers', () => {
  beforeEach(() => {
    jest.clearAllMocks();
  });

  test('lists receiver registry entries', async () => {
    mockedApi.get.mockResolvedValueOnce([receiver]);

    const result = await transferReplicationService.listReceivers();

    expect(result).toEqual([receiver]);
    expect(mockedApi.get).toHaveBeenCalledWith('/transfer/receivers');
  });

  test('creates receiver registry entries', async () => {
    mockedApi.post.mockResolvedValueOnce(receiver);

    const result = await transferReplicationService.createReceiver({
      name: 'receiver-east',
      rootFolderId: 'folder-id',
      authType: 'BEARER',
      authSecret: 'top-secret',
      enabled: true,
    });

    expect(result).toEqual(receiver);
    expect(mockedApi.post).toHaveBeenCalledWith('/transfer/receivers', {
      name: 'receiver-east',
      rootFolderId: 'folder-id',
      authType: 'BEARER',
      authSecret: 'top-secret',
      enabled: true,
    });
  });

  test('verifies receiver registry entries', async () => {
    mockedApi.post.mockResolvedValueOnce(receiver);

    const result = await transferReplicationService.verifyReceiver('receiver-id');

    expect(result).toEqual(receiver);
    expect(mockedApi.post).toHaveBeenCalledWith('/transfer/receivers/receiver-id/verify');
  });

  test('deletes receiver registry entries', async () => {
    mockedApi.delete.mockResolvedValueOnce(undefined);

    await transferReplicationService.deleteReceiver('receiver-id');

    expect(mockedApi.delete).toHaveBeenCalledWith('/transfer/receivers/receiver-id');
  });

  test('retries replication jobs', async () => {
    mockedApi.post.mockResolvedValueOnce(job);

    const result = await transferReplicationService.retryJob('job-id');

    expect(result).toEqual(job);
    expect(mockedApi.post).toHaveBeenCalledWith('/replication/jobs/job-id/retry');
  });
});

describe('transferReplicationService response guards', () => {
  beforeEach(() => {
    jest.clearAllMocks();
  });

  test('rejects HTML fallback for target lists', async () => {
    mockedApi.get.mockResolvedValueOnce('<!doctype html><html></html>');

    await expect(transferReplicationService.listTargets()).rejects.toThrow(
      TRANSFER_REPLICATION_UNEXPECTED_RESPONSE_MESSAGE
    );
  });

  test('rejects malformed definition lists', async () => {
    mockedApi.get.mockResolvedValueOnce([{ ...definition, includeChildren: 'yes' }]);

    await expect(transferReplicationService.listDefinitions()).rejects.toThrow(
      TRANSFER_REPLICATION_UNEXPECTED_RESPONSE_MESSAGE
    );
  });

  test('rejects HTML fallback for job pages', async () => {
    mockedApi.get.mockResolvedValueOnce('<!doctype html><html></html>');

    await expect(transferReplicationService.listJobs()).rejects.toThrow(
      TRANSFER_REPLICATION_UNEXPECTED_RESPONSE_MESSAGE
    );
  });

  test('returns guarded job pages', async () => {
    mockedApi.get.mockResolvedValueOnce({
      content: [job],
      totalElements: 1,
      totalPages: 1,
      number: 0,
      size: 10,
    });

    const result = await transferReplicationService.listJobs();

    expect(result.content).toEqual([job]);
    expect(mockedApi.get).toHaveBeenCalledWith('/replication/jobs', {
      params: { page: 0, size: 10 },
    });
  });

  test('rejects HTML fallback for mutation responses', async () => {
    mockedApi.post.mockResolvedValueOnce('<!doctype html><html></html>');

    await expect(
      transferReplicationService.createTarget({
        name: 'target',
        transportType: 'LOOPBACK',
        targetFolderId: 'folder-id',
      })
    ).rejects.toThrow(TRANSFER_REPLICATION_UNEXPECTED_RESPONSE_MESSAGE);
  });

  test('returns guarded target and definition lists', async () => {
    mockedApi.get.mockResolvedValueOnce([target]);
    mockedApi.get.mockResolvedValueOnce([definition]);

    await expect(transferReplicationService.listTargets()).resolves.toEqual([target]);
    await expect(transferReplicationService.listDefinitions()).resolves.toEqual([definition]);
  });
});

describe('transferReplicationService replication definition builder', () => {
  test('trims and preserves schedule, conflict, and failure policy fields', () => {
    expect(
      buildReplicationDefinitionRequest({
        name: '  nightly export  ',
        description: '  exports all documents  ',
        sourceNodeId: '  node-id  ',
        transferTargetId: 'target-id',
        includeChildren: true,
        enabled: false,
        conflictPolicy: 'RENAME',
        cronExpression: ' 0 0 * * * ',
        scheduleTimezone: '  UTC ',
        autoRetryEnabled: true,
        maxRetryAttempts: ' 5 ',
        retryBackoffMinutes: ' 15 ',
        jobRetentionDays: ' 45 ',
      })
    ).toEqual({
      name: 'nightly export',
      description: 'exports all documents',
      sourceNodeId: 'node-id',
      transferTargetId: 'target-id',
      includeChildren: true,
      enabled: false,
      conflictPolicy: 'RENAME',
      cronExpression: '0 0 * * *',
      scheduleTimezone: 'UTC',
      autoRetryEnabled: true,
      maxRetryAttempts: 5,
      retryBackoffMinutes: 15,
      jobRetentionDays: 45,
    });
  });

  test('defaults conflict policy to rename when omitted', () => {
    expect(
      buildReplicationDefinitionRequest({
        name: 'nightly export',
        sourceNodeId: 'node-id',
        transferTargetId: 'target-id',
      })
    ).toMatchObject({
      conflictPolicy: 'RENAME',
    });
  });
});
