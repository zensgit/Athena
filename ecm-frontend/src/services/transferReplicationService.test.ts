import api from './api';
import transferReplicationService from './transferReplicationService';

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
