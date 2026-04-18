import savedSearchService from './savedSearchService';
import api from './api';

jest.mock('./api', () => ({
  __esModule: true,
  default: {
    get: jest.fn(),
    post: jest.fn(),
    patch: jest.fn(),
    delete: jest.fn(),
  },
}));

const mockedApi = api as jest.Mocked<typeof api>;

describe('savedSearchService smart folder bridge', () => {
  beforeEach(() => {
    jest.clearAllMocks();
  });

  it('creates a smart folder from a saved search', async () => {
    mockedApi.post.mockResolvedValueOnce({
      id: 'folder-1',
      name: 'Invoices',
      path: '/Invoices',
      smart: true,
    });

    await savedSearchService.createSmartFolder('saved-1', {
      name: 'Invoices',
      description: 'folder from search',
      parentId: 'root-folder',
    });

    expect(mockedApi.post).toHaveBeenCalledWith('/search/saved/saved-1/smart-folder', {
      name: 'Invoices',
      description: 'folder from search',
      parentId: 'root-folder',
    });
  });
});
