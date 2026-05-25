import api from './api';
import savedSearchService, {
  FacetedSearchResponse,
  SavedSearch,
  SavedSearchTemplate,
  SAVED_SEARCH_UNEXPECTED_RESPONSE_MESSAGE,
  SmartFolderResponse,
} from './savedSearchService';

jest.mock('./api', () => ({
  __esModule: true,
  default: {
    get: jest.fn(),
    post: jest.fn(),
    patch: jest.fn(),
    delete: jest.fn(),
    downloadFile: jest.fn(),
  },
}));

const mockedApi = api as jest.Mocked<typeof api>;

const savedSearch: SavedSearch = {
  id: 'saved-1',
  userId: 'admin',
  name: 'Pinned contracts',
  queryParams: {
    query: 'contract',
    filters: { recordOnly: true },
  },
  pinned: true,
  createdAt: '2026-05-14T00:00:00Z',
};

const template: SavedSearchTemplate = {
  id: 'governance-expiring',
  name: 'Expiring governance records',
  description: null,
  queryParams: {
    filters: { recordOnly: true },
  },
  tags: ['governance'],
};

const facetedResponse: FacetedSearchResponse = {
  results: {
    content: [
      {
        id: 'doc-1',
        name: 'Contract.pdf',
        path: '/Sites/Legal/Contract.pdf',
        nodeType: 'DOCUMENT',
        mimeType: 'application/pdf',
        fileSize: 1024,
        highlights: { name: ['Contract.pdf'] },
        matchFields: ['name'],
        tags: ['legal'],
        categories: ['Contracts'],
        record: true,
        recordCategoryPath: '/Records Management/Contracts',
      },
    ],
  },
  facets: {
    nodeType: [{ value: 'DOCUMENT', count: 1 }],
  },
  totalHits: 1,
  queryTime: 12,
};

const smartFolder: SmartFolderResponse = {
  id: 'folder-1',
  name: 'Invoices',
  path: '/Invoices',
  parentId: null,
  smart: true,
  queryCriteria: { query: 'invoice' },
};

describe('savedSearchService response guards', () => {
  beforeEach(() => {
    jest.clearAllMocks();
  });

  test('lists guarded saved searches', async () => {
    mockedApi.get.mockResolvedValueOnce([savedSearch]);

    const result = await savedSearchService.list();

    expect(result).toEqual([savedSearch]);
    expect(mockedApi.get).toHaveBeenCalledWith('/search/saved');
  });

  test('rejects HTML fallback for saved search lists', async () => {
    mockedApi.get.mockResolvedValueOnce('<!doctype html><html></html>');

    await expect(savedSearchService.list()).rejects.toThrow(SAVED_SEARCH_UNEXPECTED_RESPONSE_MESSAGE);
  });

  test('rejects malformed saved search list items', async () => {
    mockedApi.get.mockResolvedValueOnce([{ ...savedSearch, queryParams: null }]);

    await expect(savedSearchService.list()).rejects.toThrow(SAVED_SEARCH_UNEXPECTED_RESPONSE_MESSAGE);
  });

  test.each([
    ['save', () => savedSearchService.save('Pinned contracts', savedSearch.queryParams), 'post'],
    ['get', () => savedSearchService.get('saved-1'), 'get'],
    ['update', () => savedSearchService.update('saved-1', { name: 'Updated contracts' }), 'patch'],
    ['setPinned', () => savedSearchService.setPinned('saved-1', false), 'patch'],
  ] as const)('returns guarded %s responses', async (_name, action, method) => {
    mockedApi[method].mockResolvedValueOnce(savedSearch);

    await expect(action()).resolves.toEqual(savedSearch);
  });

  test('lists guarded saved search templates with tag filtering', async () => {
    mockedApi.get.mockResolvedValueOnce([template]);

    const result = await savedSearchService.listTemplates('governance');

    expect(result).toEqual([template]);
    expect(mockedApi.get).toHaveBeenCalledWith('/search/saved/templates', {
      params: { tag: 'governance' },
    });
  });

  test('rejects malformed template tags', async () => {
    mockedApi.get.mockResolvedValueOnce([{ ...template, tags: 'governance' }]);

    await expect(savedSearchService.listTemplates()).rejects.toThrow(
      SAVED_SEARCH_UNEXPECTED_RESPONSE_MESSAGE
    );
  });

  test('returns guarded execute responses', async () => {
    mockedApi.get.mockResolvedValueOnce(facetedResponse);

    const result = await savedSearchService.execute('saved-1');

    expect(result).toEqual(facetedResponse);
    expect(mockedApi.get).toHaveBeenCalledWith('/search/saved/saved-1/execute');
  });

  test('rejects malformed execute result rows', async () => {
    mockedApi.get.mockResolvedValueOnce({
      ...facetedResponse,
      results: { content: [{ ...facetedResponse.results?.content?.[0], path: 42 }] },
    });

    await expect(savedSearchService.execute('saved-1')).rejects.toThrow(
      SAVED_SEARCH_UNEXPECTED_RESPONSE_MESSAGE
    );
  });

  test('rejects malformed execute facets', async () => {
    mockedApi.get.mockResolvedValueOnce({
      ...facetedResponse,
      facets: { nodeType: [{ value: 'DOCUMENT', count: '1' }] },
    });

    await expect(savedSearchService.execute('saved-1')).rejects.toThrow(
      SAVED_SEARCH_UNEXPECTED_RESPONSE_MESSAGE
    );
  });

  test('creates a guarded smart folder from a saved search', async () => {
    mockedApi.post.mockResolvedValueOnce(smartFolder);

    const result = await savedSearchService.createSmartFolder('saved-1', {
      name: 'Invoices',
      description: 'folder from search',
      parentId: 'root-folder',
    });

    expect(result).toEqual(smartFolder);
    expect(mockedApi.post).toHaveBeenCalledWith('/search/saved/saved-1/smart-folder', {
      name: 'Invoices',
      description: 'folder from search',
      parentId: 'root-folder',
    });
  });

  test('rejects HTML fallback for smart folder creation', async () => {
    mockedApi.post.mockResolvedValueOnce('<!doctype html><html></html>');

    await expect(savedSearchService.createSmartFolder('saved-1', { name: 'Invoices' })).rejects.toThrow(
      SAVED_SEARCH_UNEXPECTED_RESPONSE_MESSAGE
    );
  });

  test('exportResultsCsv downloads the CSV with a sanitized, dated filename', async () => {
    (mockedApi.downloadFile as jest.Mock).mockResolvedValueOnce(undefined);

    await savedSearchService.exportResultsCsv('saved-1', 'My Contracts');

    expect(mockedApi.downloadFile).toHaveBeenCalledWith(
      '/search/saved/saved-1/export',
      expect.stringMatching(/^My_Contracts-search-\d{8}-\d{6}\.csv$/),
    );
  });
});
