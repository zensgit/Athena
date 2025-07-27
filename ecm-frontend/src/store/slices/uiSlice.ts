import { createSlice, PayloadAction } from '@reduxjs/toolkit';

interface UIState {
  sidebarOpen: boolean;
  viewMode: 'grid' | 'list';
  sortBy: string;
  sortAscending: boolean;
  searchOpen: boolean;
  uploadDialogOpen: boolean;
  createFolderDialogOpen: boolean;
  propertiesDialogOpen: boolean;
  permissionsDialogOpen: boolean;
  versionHistoryDialogOpen: boolean;
  selectedNodeId: string | null;
}

const initialState: UIState = {
  sidebarOpen: true,
  viewMode: 'list',
  sortBy: 'name',
  sortAscending: true,
  searchOpen: false,
  uploadDialogOpen: false,
  createFolderDialogOpen: false,
  propertiesDialogOpen: false,
  permissionsDialogOpen: false,
  versionHistoryDialogOpen: false,
  selectedNodeId: null,
};

const uiSlice = createSlice({
  name: 'ui',
  initialState,
  reducers: {
    toggleSidebar: (state) => {
      state.sidebarOpen = !state.sidebarOpen;
    },
    setSidebarOpen: (state, action: PayloadAction<boolean>) => {
      state.sidebarOpen = action.payload;
    },
    setViewMode: (state, action: PayloadAction<'grid' | 'list'>) => {
      state.viewMode = action.payload;
    },
    setSortBy: (state, action: PayloadAction<string>) => {
      state.sortBy = action.payload;
    },
    toggleSortOrder: (state) => {
      state.sortAscending = !state.sortAscending;
    },
    setSearchOpen: (state, action: PayloadAction<boolean>) => {
      state.searchOpen = action.payload;
    },
    setUploadDialogOpen: (state, action: PayloadAction<boolean>) => {
      state.uploadDialogOpen = action.payload;
    },
    setCreateFolderDialogOpen: (state, action: PayloadAction<boolean>) => {
      state.createFolderDialogOpen = action.payload;
    },
    setPropertiesDialogOpen: (state, action: PayloadAction<boolean>) => {
      state.propertiesDialogOpen = action.payload;
    },
    setPermissionsDialogOpen: (state, action: PayloadAction<boolean>) => {
      state.permissionsDialogOpen = action.payload;
    },
    setVersionHistoryDialogOpen: (state, action: PayloadAction<boolean>) => {
      state.versionHistoryDialogOpen = action.payload;
    },
    setSelectedNodeId: (state, action: PayloadAction<string | null>) => {
      state.selectedNodeId = action.payload;
    },
    closeAllDialogs: (state) => {
      state.uploadDialogOpen = false;
      state.createFolderDialogOpen = false;
      state.propertiesDialogOpen = false;
      state.permissionsDialogOpen = false;
      state.versionHistoryDialogOpen = false;
    },
  },
});

export const {
  toggleSidebar,
  setSidebarOpen,
  setViewMode,
  setSortBy,
  toggleSortOrder,
  setSearchOpen,
  setUploadDialogOpen,
  setCreateFolderDialogOpen,
  setPropertiesDialogOpen,
  setPermissionsDialogOpen,
  setVersionHistoryDialogOpen,
  setSelectedNodeId,
  closeAllDialogs,
} = uiSlice.actions;

export default uiSlice.reducer;