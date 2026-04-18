import contentArchiveService from './contentArchiveService';
import api from './api';

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

describe('contentArchiveService', () => {
  beforeEach(() => {
    jest.clearAllMocks();
  });

  it('restores archived nodes through the existing restore endpoint', async () => {
    mockedApi.post.mockResolvedValueOnce({ nodeId: 'node-1', affectedNodeCount: 1 } as any);

    await contentArchiveService.restoreNode('node-1');

    expect(mockedApi.post).toHaveBeenCalledWith('/nodes/node-1/restore');
  });
});
