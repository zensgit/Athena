import api from './api';
import nodeService, { NODE_UNEXPECTED_RESPONSE_MESSAGE } from './nodeService';

jest.mock('./api', () => ({
  __esModule: true,
  default: {
    get: jest.fn(),
    post: jest.fn(),
  },
}));

const mockedApi = api as jest.Mocked<typeof api>;

const HTML_FALLBACK = '<!doctype html><html><body>app shell</body></html>';

// Minimal raw search-result item per gate H3: only id/name/path required;
// createdDate/createdBy and the rest may be missing in real responses.
const minimalRawItem = { id: 'd1', name: 'doc.txt', path: '/a/doc.txt' };

// Fuller raw item exercising additional mapper-read fields.
const fullRawItem = {
  id: 'd2',
  name: 'report.pdf',
  path: '/r/report.pdf',
  createdDate: '2026-05-19T00:00:00Z',
  createdBy: 'admin',
  lastModifiedDate: '2026-05-19T01:00:00Z',
  lastModifiedBy: 'editor',
  mimeType: 'application/pdf',
  fileSize: 12345,
  score: 0.95,
  tags: ['rpt', 'q1'],
  highlights: { name: ['report'] },
  matchFields: ['name'],
  previewStatus: 'READY',
};

const runtimeSparseRawItem = {
  id: 'd3',
  name: 'runtime.pdf',
  path: null,
  createdDate: [2026, 5, 20, 3, 30, 0],
  createdBy: null,
  lastModifiedDate: null,
  lastModifiedBy: null,
  nodeType: 'DOCUMENT',
  mimeType: 'application/pdf',
  fileSize: null,
  score: null,
  highlights: { name: ['runtime'] },
};

const validFacetValueCount = { value: 'application/pdf', count: 3 };
const validFacetMap = { mimeType: [validFacetValueCount] };

const validFacetStat = { value: 'application/pdf', count: 3 };
const validAdvancedSearchStats = {
  query: 'q',
  normalizedQuery: 'q',
  hasFilters: false,
  totalHits: 1,
  facetFieldCount: 5,
  previewStatusStats: [validFacetStat],
  mimeTypeStats: [validFacetStat],
  createdByStats: [validFacetStat],
  fileSizeRangeStats: [validFacetStat],
  createdDateRangeStats: [validFacetStat],
};

const validPivotApiResponse = {
  query: 'q',
  hasFilters: false,
  totalHits: 1,
  rowField: 'previewStatus',
  columnField: 'mimeType',
  matrix: [
    {
      previewStatus: 'READY',
      mimeTypeCounts: [{ mimeType: 'application/pdf', count: 3 }],
    },
  ],
};

const validSearchDiagnostics = {
  username: 'admin',
  admin: true,
  readFilterApplied: false,
  authorityCount: 2,
  authoritySample: ['GROUP_ADMINS', 'admin'],
};

const validSearchIndexStats = {
  indexName: 'documents',
  documentCount: 1234,
  searchEnabled: true,
};

const validSearchRebuildStatus = { inProgress: false, documentsIndexed: 1234 };

const validSuggestedFilter = { field: 'mimeType', label: 'PDF', value: 'application/pdf' };

beforeEach(() => {
  jest.clearAllMocks();
});

describe('nodeService search proper response shape guards', () => {
  it('searchNodes fast-path: name-only criteria → GET /search; valid SearchPagePayload mapped', async () => {
    mockedApi.get.mockResolvedValueOnce({ content: [fullRawItem], totalElements: 1 });
    const result = await nodeService.searchNodes({ name: 'report' });
    expect(result.total).toBe(1);
    expect(result.nodes).toHaveLength(1);
    expect(result.nodes[0].id).toBe('d2');
    expect(result.nodes[0].name).toBe('report.pdf');
    expect(result.nodes[0].path).toBe('/r/report.pdf');
    expect(result.nodes[0].contentType).toBe('application/pdf');
    expect(mockedApi.get).toHaveBeenLastCalledWith('/search', {
      params: {
        q: 'report',
        page: 0,
        size: 50,
        sortBy: undefined,
        sortDirection: undefined,
        folderId: undefined,
        includeChildren: true,
        previewStatus: undefined,
      },
    });
  });

  it('searchNodes POST path: criteria with non-scope filters → POST /search/query envelope mapped', async () => {
    mockedApi.post.mockResolvedValueOnce({
      results: { content: [fullRawItem], totalElements: 1 },
      facets: validFacetMap,
      suggestions: ['repot'],
    });
    const result = await nodeService.searchNodes({ name: 'report', recordOnly: true });
    expect(result.nodes).toHaveLength(1);
    expect(result.total).toBe(1);
    expect(result.facets).toEqual(validFacetMap);
    expect(result.suggestions).toEqual(['repot']);
    expect(mockedApi.post).toHaveBeenLastCalledWith(
      '/search/query',
      expect.objectContaining({
        query: 'report',
        include: ['results', 'facets', 'suggestions'],
      })
    );
  });

  it('searchNodes POST path: envelope with missing optional sub-fields keeps mapping fallback (no throw)', async () => {
    // results absent → resultPage.content fallback to []; facets/suggestions absent → undefined
    mockedApi.post.mockResolvedValueOnce({});
    const result = await nodeService.searchNodes({ name: 'x', recordOnly: true });
    expect(result.nodes).toEqual([]);
    expect(result.total).toBe(0);
    expect(result.facets).toBeUndefined();
    expect(result.suggestions).toBeUndefined();
  });

  it('searchNodesEnvelope: pivot matrix transform preserved byte-for-byte', async () => {
    mockedApi.post.mockResolvedValueOnce({
      results: { content: [], totalElements: 0 },
      stats: validAdvancedSearchStats,
      pivot: validPivotApiResponse,
    });
    const result = await nodeService.searchNodesEnvelope(
      { name: 'q' },
      { includeFacets: true, includeStats: true, includePivot: true }
    );
    expect(result.stats).toEqual(validAdvancedSearchStats);
    expect(result.pivot).toEqual({
      query: 'q',
      normalizedQuery: 'q', // pivotResponse.normalizedQuery missing → falls back to query
      hasFilters: false,
      totalHits: 1,
      rowField: 'previewStatus',
      columnField: 'mimeType',
      cells: [{ rowValue: 'READY', columnValue: 'application/pdf', count: 3 }],
      generatedAt: null,
    });
  });

  it('searchNodesEnvelope: pivot fallback cells path when matrix absent', async () => {
    mockedApi.post.mockResolvedValueOnce({
      results: { content: [], totalElements: 0 },
      pivot: {
        rowField: 'previewStatus',
        columnField: 'mimeType',
        cells: [{ rowValue: 'READY', columnValue: 'application/pdf', count: 2 }],
      },
    });
    const result = await nodeService.searchNodesEnvelope({ name: '' }, { includePivot: true });
    expect(result.pivot?.cells).toEqual([
      { rowValue: 'READY', columnValue: 'application/pdf', count: 2 },
    ]);
  });

  it('searchNodesEnvelope: pivot null when missing; stats null when missing', async () => {
    mockedApi.post.mockResolvedValueOnce({ results: { content: [], totalElements: 0 } });
    const result = await nodeService.searchNodesEnvelope({ name: '' });
    expect(result.pivot).toBeNull();
    expect(result.stats).toBeNull();
  });

  it('findSimilar: valid array of items → mapped; non-array / bad element → throw (gate H5)', async () => {
    mockedApi.get.mockResolvedValueOnce([fullRawItem]);
    const result = await nodeService.findSimilar('parent-id', 5);
    expect(result).toHaveLength(1);
    expect(result[0].id).toBe('d2');
    expect(mockedApi.get).toHaveBeenLastCalledWith('/search/similar/parent-id', {
      params: { maxResults: 5 },
    });

    mockedApi.get.mockResolvedValueOnce({ not: 'an array' });
    await expect(nodeService.findSimilar('p')).rejects.toThrow(NODE_UNEXPECTED_RESPONSE_MESSAGE);

    mockedApi.get.mockResolvedValueOnce([{ id: 'x' /* missing required name */ }]);
    await expect(nodeService.findSimilar('p')).rejects.toThrow(NODE_UNEXPECTED_RESPONSE_MESSAGE);
  });

  it('H3: minimal raw item with only id/name/path passes (partial responses tolerated)', async () => {
    // Fast path
    mockedApi.get.mockResolvedValueOnce({ content: [minimalRawItem], totalElements: 1 });
    const fast = await nodeService.searchNodes({ name: 'doc' });
    expect(fast.nodes).toHaveLength(1);
    expect(fast.nodes[0].id).toBe('d1');

    // POST path
    mockedApi.post.mockResolvedValueOnce({ results: { content: [minimalRawItem], totalElements: 1 } });
    const adv = await nodeService.searchNodes({ name: 'doc', recordOnly: true });
    expect(adv.nodes[0].id).toBe('d1');

    // findSimilar
    mockedApi.get.mockResolvedValueOnce([minimalRawItem]);
    const sim = await nodeService.findSimilar('p');
    expect(sim[0].id).toBe('d1');
  });

  it('runtime sparse search hits with nullable mapper-read fields do not blank the result page', async () => {
    mockedApi.get.mockResolvedValueOnce({ content: [runtimeSparseRawItem], totalElements: 1 });

    const result = await nodeService.searchNodes({ name: 'runtime' });

    expect(result.nodes).toHaveLength(1);
    expect(result.nodes[0].id).toBe('d3');
    expect(result.nodes[0].name).toBe('runtime.pdf');
    expect(result.nodes[0].path).toBeNull();
    expect(result.nodes[0].contentType).toBe('application/pdf');
    expect(result.nodes[0].size).toBeNull();
  });

  it('search result required identity fields still reject wrong types', async () => {
    mockedApi.get.mockResolvedValueOnce({ content: [{ ...minimalRawItem, path: 123 }], totalElements: 1 });

    await expect(nodeService.searchNodes({ name: 'bad-path' })).rejects.toThrow(
      NODE_UNEXPECTED_RESPONSE_MESSAGE
    );
  });

  it('getSearchFacets: valid Record passthrough; malformed → throw; endpoint locked', async () => {
    mockedApi.get.mockResolvedValueOnce(validFacetMap);
    await expect(nodeService.getSearchFacets('term')).resolves.toEqual(validFacetMap);
    expect(mockedApi.get).toHaveBeenLastCalledWith('/search/facets', { params: { q: 'term' } });

    mockedApi.get.mockResolvedValueOnce({ mimeType: [{ value: 1, count: 2 }] });
    await expect(nodeService.getSearchFacets()).rejects.toThrow(NODE_UNEXPECTED_RESPONSE_MESSAGE);

    mockedApi.get.mockResolvedValueOnce(HTML_FALLBACK);
    await expect(nodeService.getSearchFacets()).rejects.toThrow(NODE_UNEXPECTED_RESPONSE_MESSAGE);
  });

  it('getSuggestedFilters: endpoint is /search/filters/suggested; valid + malformed', async () => {
    mockedApi.get.mockResolvedValueOnce([validSuggestedFilter]);
    await expect(nodeService.getSuggestedFilters('term')).resolves.toEqual([validSuggestedFilter]);
    expect(mockedApi.get).toHaveBeenLastCalledWith('/search/filters/suggested', {
      params: { q: 'term' },
    });

    mockedApi.get.mockResolvedValueOnce([{ field: 'x' /* missing label */, value: 'y' }]);
    await expect(nodeService.getSuggestedFilters()).rejects.toThrow(
      NODE_UNEXPECTED_RESPONSE_MESSAGE
    );
  });

  it('getSpellcheckSuggestions and getSearchSuggestions: string[] valid + malformed', async () => {
    mockedApi.get.mockResolvedValueOnce(['repot']);
    await expect(nodeService.getSpellcheckSuggestions('repot', 5)).resolves.toEqual(['repot']);
    expect(mockedApi.get).toHaveBeenLastCalledWith('/search/spellcheck', {
      params: { q: 'repot', limit: 5 },
    });

    mockedApi.get.mockResolvedValueOnce([1, 2]);
    await expect(nodeService.getSpellcheckSuggestions()).rejects.toThrow(
      NODE_UNEXPECTED_RESPONSE_MESSAGE
    );

    mockedApi.get.mockResolvedValueOnce(['report', 'reporter']);
    await expect(nodeService.getSearchSuggestions('rep')).resolves.toEqual(['report', 'reporter']);
    expect(mockedApi.get).toHaveBeenLastCalledWith('/search/suggestions', {
      params: { prefix: 'rep', limit: 10 },
    });
  });

  it('getSearchDiagnostics / getSearchIndexStats / getSearchRebuildStatus: valid + malformed', async () => {
    mockedApi.get.mockResolvedValueOnce(validSearchDiagnostics);
    await expect(nodeService.getSearchDiagnostics()).resolves.toEqual(validSearchDiagnostics);
    expect(mockedApi.get).toHaveBeenLastCalledWith('/search/diagnostics');

    mockedApi.get.mockResolvedValueOnce(validSearchIndexStats);
    await expect(nodeService.getSearchIndexStats()).resolves.toEqual(validSearchIndexStats);
    expect(mockedApi.get).toHaveBeenLastCalledWith('/search/index/stats');

    mockedApi.get.mockResolvedValueOnce(validSearchRebuildStatus);
    await expect(nodeService.getSearchRebuildStatus()).resolves.toEqual(validSearchRebuildStatus);
    expect(mockedApi.get).toHaveBeenLastCalledWith('/search/index/rebuild/status');

    mockedApi.get.mockResolvedValueOnce({ ...validSearchDiagnostics, admin: 'no' });
    await expect(nodeService.getSearchDiagnostics()).rejects.toThrow(
      NODE_UNEXPECTED_RESPONSE_MESSAGE
    );

    mockedApi.get.mockResolvedValueOnce({ ...validSearchIndexStats, searchEnabled: 'true' });
    await expect(nodeService.getSearchIndexStats()).rejects.toThrow(
      NODE_UNEXPECTED_RESPONSE_MESSAGE
    );
  });

  it('throws the sentinel on HTML fallback / null for search-proper methods', async () => {
    const expectThrow = async (fn: () => Promise<unknown>) =>
      expect(fn()).rejects.toThrow(NODE_UNEXPECTED_RESPONSE_MESSAGE);

    for (const bad of [HTML_FALLBACK, null]) {
      mockedApi.get.mockResolvedValueOnce(bad);
      await expectThrow(() => nodeService.searchNodes({ name: 'q' })); // fast path bad raw
      mockedApi.post.mockResolvedValueOnce(bad);
      await expectThrow(() => nodeService.searchNodes({ name: 'q', recordOnly: true })); // POST path bad raw
      mockedApi.post.mockResolvedValueOnce(bad);
      await expectThrow(() => nodeService.searchNodesEnvelope({ name: '' }));
      mockedApi.get.mockResolvedValueOnce(bad);
      await expectThrow(() => nodeService.findSimilar('p'));
      mockedApi.get.mockResolvedValueOnce(bad);
      await expectThrow(() => nodeService.getSearchFacets());
      mockedApi.get.mockResolvedValueOnce(bad);
      await expectThrow(() => nodeService.getSuggestedFilters());
      mockedApi.get.mockResolvedValueOnce(bad);
      await expectThrow(() => nodeService.getSearchSuggestions('x'));
    }
  });

  it('envelope present-but-bad sub-fields → throw (results non-page-shape, facets non-record, stats malformed, pivot.matrix bad item)', async () => {
    // results not a SearchPagePayload (content present but not array)
    mockedApi.post.mockResolvedValueOnce({ results: { content: 'oops' } });
    await expect(
      nodeService.searchNodes({ name: 'q', recordOnly: true })
    ).rejects.toThrow(NODE_UNEXPECTED_RESPONSE_MESSAGE);

    // facets present but value entry not array of FacetValueCount
    mockedApi.post.mockResolvedValueOnce({ facets: { mime: 'oops' } });
    await expect(
      nodeService.searchNodes({ name: 'q', recordOnly: true })
    ).rejects.toThrow(NODE_UNEXPECTED_RESPONSE_MESSAGE);

    // stats present but missing required-array field
    mockedApi.post.mockResolvedValueOnce({ stats: { ...validAdvancedSearchStats, mimeTypeStats: 'oops' } });
    await expect(nodeService.searchNodesEnvelope({ name: '' })).rejects.toThrow(
      NODE_UNEXPECTED_RESPONSE_MESSAGE
    );

    // pivot present but matrix row malformed (mimeTypeCounts non-array when present)
    mockedApi.post.mockResolvedValueOnce({
      pivot: { matrix: [{ previewStatus: 'X', mimeTypeCounts: 'oops' }] },
    });
    await expect(nodeService.searchNodesEnvelope({ name: '' })).rejects.toThrow(
      NODE_UNEXPECTED_RESPONSE_MESSAGE
    );
  });
});
