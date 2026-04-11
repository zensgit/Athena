import api from './api';
import {
  buildReplicationDefinitionRequest,
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

describe('transferReplicationService receiver registry wrappers', () => {
  beforeEach(() => {
    jest.clearAllMocks();
  });

  test('lists receiver registry entries', async () => {
    mockedApi.get.mockResolvedValueOnce([]);

    await transferReplicationService.listReceivers();

    expect(mockedApi.get).toHaveBeenCalledWith('/transfer/receivers');
  });

  test('creates receiver registry entries', async () => {
    mockedApi.post.mockResolvedValueOnce({});

    await transferReplicationService.createReceiver({
      name: 'receiver-east',
      rootFolderId: 'folder-id',
      authType: 'BEARER',
      authSecret: 'top-secret',
      enabled: true,
    });

    expect(mockedApi.post).toHaveBeenCalledWith('/transfer/receivers', {
      name: 'receiver-east',
      rootFolderId: 'folder-id',
      authType: 'BEARER',
      authSecret: 'top-secret',
      enabled: true,
    });
  });

  test('verifies receiver registry entries', async () => {
    mockedApi.post.mockResolvedValueOnce({});

    await transferReplicationService.verifyReceiver('receiver-id');

    expect(mockedApi.post).toHaveBeenCalledWith('/transfer/receivers/receiver-id/verify');
  });

  test('deletes receiver registry entries', async () => {
    mockedApi.delete.mockResolvedValueOnce(undefined);

    await transferReplicationService.deleteReceiver('receiver-id');

    expect(mockedApi.delete).toHaveBeenCalledWith('/transfer/receivers/receiver-id');
  });

  test('retries replication jobs', async () => {
    mockedApi.post.mockResolvedValueOnce({});

    await transferReplicationService.retryJob('job-id');

    expect(mockedApi.post).toHaveBeenCalledWith('/replication/jobs/job-id/retry');
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
