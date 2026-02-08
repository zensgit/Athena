import nodeReducer, { fetchChildren } from 'store/slices/nodeSlice';

describe('nodeSlice fetchChildren state handling', () => {
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
});
