import nodeReducer, { executeSavedSearch, fetchChildren } from 'store/slices/nodeSlice';
import savedSearchService from 'services/savedSearchService';

jest.mock('services/savedSearchService', () => ({
  __esModule: true,
  default: {
    execute: jest.fn(),
  },
}));

const mockedSavedSearchService = savedSearchService as jest.Mocked<typeof savedSearchService>;

describe('nodeSlice fetchChildren state handling', () => {
  beforeEach(() => {
    jest.clearAllMocks();
  });

  it('clears stale nodes when a new children request starts', () => {
    const initialState = nodeReducer(undefined, { type: 'node/init' });
    const seededState = {
      ...initialState,
      nodes: [
        {
          id: 'old-node',
          name: 'old-node.txt',
          path: '/Root/Documents/old-node.txt',
          nodeType: 'DOCUMENT' as const,
          aspects: [],
          created: '2026-02-01T00:00:00Z',
          modified: '2026-02-01T00:00:00Z',
          creator: 'admin',
          modifier: 'admin',
        },
      ],
      nodesTotal: 1,
      loading: false,
      error: 'old error',
    };

    const nextState = nodeReducer(
      seededState,
      fetchChildren.pending('req-1', {
        nodeId: 'root',
        sortBy: 'name',
        ascending: true,
        page: 0,
        size: 50,
      })
    );

    expect(nextState.loading).toBe(true);
    expect(nextState.error).toBeNull();
    expect(nextState.nodes).toEqual([]);
    expect(nextState.nodesTotal).toBe(0);
  });

  it('clears nodes when fetching children fails', () => {
    const initialState = nodeReducer(undefined, { type: 'node/init' });
    const seededState = {
      ...initialState,
      nodes: [
        {
          id: 'old-node',
          name: 'old-node.txt',
          path: '/Root/Documents/old-node.txt',
          nodeType: 'DOCUMENT' as const,
          aspects: [],
          created: '2026-02-01T00:00:00Z',
          modified: '2026-02-01T00:00:00Z',
          creator: 'admin',
          modifier: 'admin',
        },
      ],
      nodesTotal: 1,
      loading: true,
      error: null,
    };

    const nextState = nodeReducer(
      seededState,
      fetchChildren.rejected(new Error('boom'), 'req-2', {
        nodeId: 'root',
        sortBy: 'name',
        ascending: true,
        page: 0,
        size: 50,
      })
    );

    expect(nextState.loading).toBe(false);
    expect(nextState.error).toBe('boom');
    expect(nextState.nodes).toEqual([]);
    expect(nextState.nodesTotal).toBe(0);
  });

  it('preserves RM projection when executing a saved search', async () => {
    mockedSavedSearchService.execute.mockResolvedValueOnce({
      results: {
        content: [
          {
            id: 'doc-1',
            name: 'Contract.pdf',
            path: '/Sites/Legal/Contract.pdf',
            nodeType: 'DOCUMENT',
            parentId: 'folder-1',
            mimeType: 'application/pdf',
            fileSize: 1024,
            createdBy: 'alice',
            createdDate: '2026-04-19T10:00:00Z',
            lastModifiedBy: 'alice',
            lastModifiedDate: '2026-04-19T10:00:00Z',
            currentVersionLabel: '1.1',
            record: true,
            declaredBy: 'records-admin',
            declaredAt: '2026-04-18T09:00:00Z',
            declaredVersionLabel: '1.0',
            declarationComment: 'Declare for retention',
            recordCategoryId: 'cat-1',
            recordCategoryName: 'Contracts',
            recordCategoryPath: '/Records Management/Contracts',
          },
        ],
      },
      totalHits: 1,
    } as any);

    const dispatch = jest.fn();
    const getState = jest.fn();
    const result = await executeSavedSearch('saved-1')(dispatch, getState, undefined);

    expect(mockedSavedSearchService.execute).toHaveBeenCalledWith('saved-1');
    expect(result.type).toBe('node/executeSavedSearch/fulfilled');
    if (result.type !== 'node/executeSavedSearch/fulfilled') {
      throw new Error('Expected saved search execution to be fulfilled');
    }
    expect(result.payload.total).toBe(1);
    expect(result.payload.nodes[0]).toMatchObject({
      id: 'doc-1',
      record: true,
      currentVersionLabel: '1.1',
      declaredBy: 'records-admin',
      declaredVersionLabel: '1.0',
      recordCategoryPath: '/Records Management/Contracts',
    });
    expect(result.payload.nodes[0].aspects).toContain('rm:record');
    expect(result.payload.nodes[0].properties['rm:declaredBy']).toBe('records-admin');
    expect(result.payload.nodes[0].properties['rm:recordCategoryPath']).toBe('/Records Management/Contracts');
  });
});
