import api from './api';
import propertyEncryptionService from './propertyEncryptionService';

jest.mock('./api', () => ({
  __esModule: true,
  default: {
    get: jest.fn(),
    post: jest.fn(),
  },
}));

const mockedApi = api as jest.Mocked<typeof api>;

describe('propertyEncryptionService', () => {
  beforeEach(() => {
    jest.clearAllMocks();
  });

  test('loads status and encrypted definitions from admin property encryption endpoints', async () => {
    mockedApi.get.mockResolvedValueOnce({}).mockResolvedValueOnce([]);

    await propertyEncryptionService.getStatus();
    await propertyEncryptionService.listDefinitions();

    expect(mockedApi.get).toHaveBeenNthCalledWith(1, '/admin/property-encryption/status');
    expect(mockedApi.get).toHaveBeenNthCalledWith(2, '/admin/property-encryption/definitions');
  });

  test('trims target key version for dry-runs and plan requests', async () => {
    mockedApi.post.mockResolvedValue({});

    await propertyEncryptionService.dryRunRewrap(' v2 ');
    await propertyEncryptionService.dryRunBackfill(' v2 ');
    await propertyEncryptionService.planRewrapJob(' v2 ');
    await propertyEncryptionService.planBackfillJob(' v2 ');

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

  test('wraps backfill and rewrap job list, run, and cancel endpoints', async () => {
    mockedApi.get.mockResolvedValue([]);
    mockedApi.post.mockResolvedValue({});

    await propertyEncryptionService.listBackfillJobs(25);
    await propertyEncryptionService.listRewrapJobs(25);
    await propertyEncryptionService.runBackfillJob('job-1', 50);
    await propertyEncryptionService.cancelBackfillJob('job-1');
    await propertyEncryptionService.runRewrapJob('rewrap-1', 50);
    await propertyEncryptionService.cancelRewrapJob('rewrap-1');

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
});
