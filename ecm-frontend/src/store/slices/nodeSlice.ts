import { createSlice, createAsyncThunk, PayloadAction } from '@reduxjs/toolkit';
import nodeService from 'services/nodeService';
import { Node, NodeState, SearchCriteria } from 'types';

const initialState: NodeState = {
  currentNode: null,
  nodes: [],
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
  async ({ nodeId, sortBy = 'name', ascending = true }: { nodeId: string; sortBy?: string; ascending?: boolean }) => {
    const children = await nodeService.getChildren(nodeId, sortBy, ascending);
    return children;
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
  async ({ parentId, file, properties }: { parentId: string; file: File; properties?: Record<string, any> }) => {
    const document = await nodeService.uploadDocument(parentId, file, properties);
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
  async ({ nodeId, targetParentId, deepCopy = true }: { nodeId: string; targetParentId: string; deepCopy?: boolean }) => {
    const node = await nodeService.copyNode(nodeId, targetParentId, deepCopy);
    return node;
  }
);

export const updateNode = createAsyncThunk(
  'node/updateNode',
  async ({ nodeId, properties }: { nodeId: string; properties: Record<string, any> }) => {
    const node = await nodeService.updateNode(nodeId, properties);
    return node;
  }
);

export const searchNodes = createAsyncThunk(
  'node/searchNodes',
  async (criteria: SearchCriteria) => {
    return nodeService.searchNodes(criteria);
  }
);

const nodeSlice = createSlice({
  name: 'node',
  initialState,
  reducers: {
    setCurrentNode: (state, action: PayloadAction<Node>) => {
      state.currentNode = action.payload;
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
        state.nodes = action.payload;
      })
      .addCase(fetchChildren.rejected, (state, action) => {
        state.loading = false;
        state.error = action.error.message || 'Failed to fetch children';
      })
      .addCase(createFolder.fulfilled, (state, action) => {
        state.nodes.push(action.payload);
      })
      .addCase(uploadDocument.fulfilled, (state, action) => {
        state.nodes.push(action.payload);
      })
      .addCase(deleteNodes.fulfilled, (state, action) => {
        state.nodes = state.nodes.filter(node => !action.payload.includes(node.id));
        state.selectedNodes = [];
      })
      .addCase(searchNodes.pending, (state) => {
        state.loading = true;
        state.error = null;
      })
      .addCase(searchNodes.fulfilled, (state, action) => {
        state.loading = false;
        state.nodes = action.payload;
      })
      .addCase(searchNodes.rejected, (state, action) => {
        state.loading = false;
        state.error = action.error.message || 'Search failed';
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
} = nodeSlice.actions;

export default nodeSlice.reducer;
