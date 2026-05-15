import api from './api';
import bulkMetadataService, {
  BULK_METADATA_UNEXPECTED_RESPONSE_MESSAGE,
  BulkMetadataRequest,
  BulkMetadataResult,
} from './bulkMetadataService';

jest.mock('./api', () => ({
  __esModule: true,
  default: {
    post: jest.fn(),
  },
}));

const mockedApi = api as jest.Mocked<typeof api>;

const request: BulkMetadataRequest = {
  ids: ['node-1', 'node-2'],
  tagNames: ['finance'],
  categoryIds: ['cat-1'],
  correspondentId: 'corr-1',
  clearCorrespondent: false,
};

const result: BulkMetadataResult = {
  operation: 'BULK_METADATA_UPDATE',
  totalRequested: 2,
  successCount: 1,
  failureCount: 1,
  successfulIds: ['node-1'],
  failures: {
    'node-2': 'Access denied',
  },
};

describe('bulkMetadataService response guards', () => {
  beforeEach(() => {
    jest.clearAllMocks();
  });

  it('returns guarded bulk metadata results and forwards the request', async () => {
    mockedApi.post.mockResolvedValueOnce(result);

    await expect(bulkMetadataService.applyMetadata(request)).resolves.toEqual(result);

    expect(mockedApi.post).toHaveBeenCalledWith('/bulk/metadata', request);
  });

  it('accepts an empty successful update result', async () => {
    const emptyResult: BulkMetadataResult = {
      operation: 'BULK_METADATA_UPDATE',
      totalRequested: 0,
      successCount: 0,
      failureCount: 0,
      successfulIds: [],
      failures: {},
    };
    mockedApi.post.mockResolvedValueOnce(emptyResult);

    await expect(bulkMetadataService.applyMetadata({ ids: [] })).resolves.toEqual(emptyResult);
  });

  it('rejects HTML fallback', async () => {
    mockedApi.post.mockResolvedValueOnce('<!doctype html><html></html>');

    await expect(bulkMetadataService.applyMetadata(request)).rejects.toThrow(
      BULK_METADATA_UNEXPECTED_RESPONSE_MESSAGE,
    );
  });

  it('rejects malformed numeric counters', async () => {
    mockedApi.post.mockResolvedValueOnce({
      ...result,
      successCount: '1',
    });

    await expect(bulkMetadataService.applyMetadata(request)).rejects.toThrow(
      BULK_METADATA_UNEXPECTED_RESPONSE_MESSAGE,
    );
  });

  it('rejects malformed successfulIds arrays', async () => {
    mockedApi.post.mockResolvedValueOnce({
      ...result,
      successfulIds: ['node-1', 42],
    });

    await expect(bulkMetadataService.applyMetadata(request)).rejects.toThrow(
      BULK_METADATA_UNEXPECTED_RESPONSE_MESSAGE,
    );
  });

  it('rejects malformed failures maps', async () => {
    mockedApi.post.mockResolvedValueOnce({
      ...result,
      failures: {
        'node-2': { message: 'Access denied' },
      },
    });

    await expect(bulkMetadataService.applyMetadata(request)).rejects.toThrow(
      BULK_METADATA_UNEXPECTED_RESPONSE_MESSAGE,
    );
  });
});
