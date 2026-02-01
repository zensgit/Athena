import { createSlice, createAsyncThunk, PayloadAction } from '@reduxjs/toolkit';
import nodeService from 'services/nodeService';
import savedSearchService from 'services/savedSearchService';
import { Node, NodeState, SearchCriteria } from 'types';

const initialState: NodeState = {
  currentNode: null,
  nodes: [],
  nodesTotal: 0,
  searchFacets: {},
  searchFacetsCache: {},
  loading: false,
  error: null,
  selectedNodes: [],
};

export const fetchNode = createAsyncThunk(
  'node/fetchNode',
  async (nodeId: string) => {
    const node = await nodeService.getNode(nodeId);
    return node;
  }
);

export const fetchChildren = createAsyncThunk(
  'node/fetchChildren',
  async ({
    nodeId,
    sortBy = 'name',
    ascending = true,
    page = 0,
    size = 50,
  }: {
    nodeId: string;
    sortBy?: string;
    ascending?: boolean;
    page?: number;
    size?: number;
  }) => {
    return nodeService.getChildrenPage(nodeId, sortBy, ascending, page, size);
  }
);

export const createFolder = createAsyncThunk(
  'node/createFolder',
  async ({ parentId, name, properties }: { parentId: string; name: string; properties?: Record<string, any> }) => {
    const folder = await nodeService.createFolder(parentId, { name, properties });
    return folder;
  }
);

export const uploadDocument = createAsyncThunk(
  'node/uploadDocument',
  async ({
    parentId,
    file,
    properties,
    onProgress,
  }: {
    parentId: string;
    file: File;
    properties?: Record<string, any>;
    onProgress?: (progress: number) => void;
  }) => {
    const document = await nodeService.uploadDocument(parentId, file, properties, onProgress);
    return document;
  }
);

export const deleteNodes = createAsyncThunk(
  'node/deleteNodes',
  async (nodeIds: string[]) => {
    await Promise.all(nodeIds.map(id => nodeService.deleteNode(id)));
    return nodeIds;
  }
);

export const moveNode = createAsyncThunk(
  'node/moveNode',
  async ({ nodeId, targetParentId }: { nodeId: string; targetParentId: string }) => {
    const node = await nodeService.moveNode(nodeId, targetParentId);
    return node;
  }
);

export const copyNode = createAsyncThunk(
  'node/copyNode',
  async ({
    nodeId,
    targetParentId,
    deepCopy = true,
    newName,
  }: {
    nodeId: string;
    targetParentId: string;
    deepCopy?: boolean;
    newName?: string;
  }) => {
    const node = await nodeService.copyNode(nodeId, targetParentId, deepCopy, newName);
    return node;
  }
);

export const updateNode = createAsyncThunk(
  'node/updateNode',
  async ({ nodeId, updates }: { nodeId: string; updates: Record<string, any> }) => {
    const node = await nodeService.updateNode(nodeId, updates);
    return node;
  }
);

export const searchNodes = createAsyncThunk(
  'node/searchNodes',
  async (criteria: SearchCriteria) => {
    const response = await nodeService.searchNodes(criteria);
    return { nodes: response.nodes, total: response.total, criteria };
  }
);

export const fetchSearchFacets = createAsyncThunk(
  'node/fetchSearchFacets',
  async (query: string | undefined, { getState }) => {
    const normalizedQuery = (query || '').trim().toLowerCase();
    const state = getState() as { node: NodeState };
    const cached = state.node.searchFacetsCache?.[normalizedQuery];
    if (cached) {
      return { facets: cached, query: normalizedQuery, cached: true };
    }
    const facets = await nodeService.getSearchFacets(query || '');
    return { facets, query: normalizedQuery, cached: false };
  }
);

export const executeSavedSearch = createAsyncThunk(
  'node/executeSavedSearch',
  async (savedSearchId: string) => {
    const response = await savedSearchService.execute(savedSearchId);
    const items = response.results?.content || [];
    const nodes = items.map((item) => {
      const inferredNodeType = item.mimeType || item.fileSize
        ? 'DOCUMENT'
        : (item.nodeType === 'FOLDER' || item.nodeType === 'DOCUMENT' ? item.nodeType : 'FOLDER');

      return ({
        id: item.id,
        name: item.name,
        path: item.path,
        nodeType: inferredNodeType,
        parentId: item.parentId,
        properties: { description: item.description },
        aspects: [],
        created: item.createdDate || new Date().toISOString(),
        modified: item.lastModifiedDate || item.createdDate || new Date().toISOString(),
        creator: item.createdBy || '',
        modifier: item.lastModifiedBy || item.createdBy || '',
        size: item.fileSize,
        contentType: item.mimeType,
        description: item.description,
        highlights: item.highlights,
        tags: item.tags,
        categories: item.categories,
        correspondent: item.correspondent,
        score: item.score,
      } as Node);
    });
    return { nodes, total: response.totalHits ?? nodes.length };
  }
);

const nodeSlice = createSlice({
  name: 'node',
  initialState,
  reducers: {
    setCurrentNode: (state, action: PayloadAction<Node>) => {
      state.currentNode = action.payload;
    },
    setLastSearchCriteria: (state, action: PayloadAction<SearchCriteria | null>) => {
      state.lastSearchCriteria = action.payload || undefined;
    },
    clearCurrentNode: (state) => {
      state.currentNode = null;
    },
    setSelectedNodes: (state, action: PayloadAction<string[]>) => {
      state.selectedNodes = action.payload;
    },
    toggleNodeSelection: (state, action: PayloadAction<string>) => {
      const nodeId = action.payload;
      const index = state.selectedNodes.indexOf(nodeId);
      if (index >= 0) {
        state.selectedNodes.splice(index, 1);
      } else {
        state.selectedNodes.push(nodeId);
      }
    },
    clearSelection: (state) => {
      state.selectedNodes = [];
    },
    clearError: (state) => {
      state.error = null;
    },
  },
  extraReducers: (builder) => {
    builder
      .addCase(fetchNode.pending, (state) => {
        state.loading = true;
        state.error = null;
      })
      .addCase(fetchNode.fulfilled, (state, action) => {
        state.loading = false;
        state.currentNode = action.payload;
      })
      .addCase(fetchNode.rejected, (state, action) => {
        state.loading = false;
        state.error = action.error.message || 'Failed to fetch node';
      })
      .addCase(fetchChildren.pending, (state) => {
        state.loading = true;
        state.error = null;
      })
      .addCase(fetchChildren.fulfilled, (state, action) => {
        state.loading = false;
        state.nodes = action.payload.nodes;
        state.nodesTotal = action.payload.total;
      })
      .addCase(fetchChildren.rejected, (state, action) => {
        state.loading = false;
        state.error = action.error.message || 'Failed to fetch children';
        state.nodesTotal = 0;
      })
      .addCase(createFolder.fulfilled, (state, action) => {
        state.nodes.push(action.payload);
        state.nodesTotal += 1;
      })
      .addCase(uploadDocument.fulfilled, (state, action) => {
        state.nodes.push(action.payload);
        state.nodesTotal += 1;
      })
      .addCase(deleteNodes.fulfilled, (state, action) => {
        state.nodes = state.nodes.filter(node => !action.payload.includes(node.id));
        state.selectedNodes = [];
        state.nodesTotal = Math.max(0, state.nodesTotal - action.payload.length);
      })
      .addCase(searchNodes.pending, (state) => {
        state.loading = true;
        state.error = null;
      })
      .addCase(searchNodes.fulfilled, (state, action) => {
        state.loading = false;
        state.nodes = action.payload.nodes;
        state.nodesTotal = action.payload.total;
        state.lastSearchCriteria = action.payload.criteria;
      })
      .addCase(searchNodes.rejected, (state, action) => {
        state.loading = false;
        state.error = action.error.message || 'Search failed';
        state.nodesTotal = 0;
      })
      .addCase(fetchSearchFacets.fulfilled, (state, action) => {
        state.searchFacets = action.payload.facets || {};
        state.searchFacetsCache = state.searchFacetsCache || {};
        state.searchFacetsCache[action.payload.query] = action.payload.facets || {};
      })
      .addCase(executeSavedSearch.pending, (state) => {
        state.loading = true;
        state.error = null;
      })
      .addCase(executeSavedSearch.fulfilled, (state, action) => {
        state.loading = false;
        state.nodes = action.payload.nodes;
        state.nodesTotal = action.payload.total;
      })
      .addCase(executeSavedSearch.rejected, (state, action) => {
        state.loading = false;
        state.error = action.error.message || 'Saved search failed';
        state.nodesTotal = 0;
      })
      .addCase(updateNode.fulfilled, (state, action) => {
        state.loading = false;
        const updated = action.payload;
        state.currentNode = updated;
        state.nodes = state.nodes.map(node => (node.id === updated.id ? updated : node));
      })
      .addCase(updateNode.rejected, (state, action) => {
        state.error = action.error.message || 'Failed to update node';
      });
  },
});

export const {
  setCurrentNode,
  clearCurrentNode,
  setSelectedNodes,
  toggleNodeSelection,
  clearSelection,
  clearError,
  setLastSearchCriteria,
} = nodeSlice.actions;

export default nodeSlice.reducer;
