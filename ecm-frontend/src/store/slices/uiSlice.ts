import { createSlice, PayloadAction } from '@reduxjs/toolkit';

const SIDEBAR_AUTO_COLLAPSE_STORAGE_KEY = 'athena.ecm.sidebarAutoCollapse';
const SIDEBAR_OPEN_STORAGE_KEY = 'athena.ecm.sidebarOpen';
const COMPACT_MODE_STORAGE_KEY = 'athena.ecm.compactMode';

const readBooleanSetting = (key: string, defaultValue: boolean) => {
  if (typeof window === 'undefined') {
    return defaultValue;
  }
  try {
    const raw = window.localStorage.getItem(key);
    if (raw === null) {
      return defaultValue;
    }
    return raw === 'true';
  } catch {
    return defaultValue;
  }
};

interface UIState {
  sidebarOpen: boolean;
  sidebarAutoCollapse: boolean;
  compactMode: boolean;
  viewMode: 'grid' | 'list';
  sortBy: string;
  sortAscending: boolean;
  searchOpen: boolean;
  searchPrefill: SearchPrefill | null;
  uploadDialogOpen: boolean;
  createFolderDialogOpen: boolean;
  propertiesDialogOpen: boolean;
  permissionsDialogOpen: boolean;
  versionHistoryDialogOpen: boolean;
  tagManagerOpen: boolean;
  categoryManagerOpen: boolean;
  shareLinkManagerOpen: boolean;
  mlSuggestionsOpen: boolean;
  selectedNodeId: string | null;
}

export interface SearchPrefill {
  name?: string;
  contentType?: string;
  previewStatuses?: string[];
  createdBy?: string;
  createdFrom?: string;
  createdTo?: string;
  modifiedFrom?: string;
  modifiedTo?: string;
  tags?: string[];
  categories?: string[];
  correspondents?: string[];
  minSize?: number;
  maxSize?: number;
  pathPrefix?: string;
  folderId?: string;
  includeChildren?: boolean;
}

const initialState: UIState = {
  sidebarOpen: readBooleanSetting(SIDEBAR_OPEN_STORAGE_KEY, true),
  sidebarAutoCollapse: readBooleanSetting(SIDEBAR_AUTO_COLLAPSE_STORAGE_KEY, true),
  compactMode: readBooleanSetting(COMPACT_MODE_STORAGE_KEY, false),
  viewMode: 'list',
  sortBy: 'name',
  sortAscending: true,
  searchOpen: false,
  searchPrefill: null,
  uploadDialogOpen: false,
  createFolderDialogOpen: false,
  propertiesDialogOpen: false,
  permissionsDialogOpen: false,
  versionHistoryDialogOpen: false,
  tagManagerOpen: false,
  categoryManagerOpen: false,
  shareLinkManagerOpen: false,
  mlSuggestionsOpen: false,
  selectedNodeId: null,
};

const uiSlice = createSlice({
  name: 'ui',
  initialState,
  reducers: {
    toggleSidebar: (state) => {
      state.sidebarOpen = !state.sidebarOpen;
      try {
        window.localStorage.setItem(SIDEBAR_OPEN_STORAGE_KEY, String(state.sidebarOpen));
      } catch {
        // ignore
      }
    },
    setSidebarOpen: (state, action: PayloadAction<boolean>) => {
      state.sidebarOpen = action.payload;
      try {
        window.localStorage.setItem(SIDEBAR_OPEN_STORAGE_KEY, String(action.payload));
      } catch {
        // ignore
      }
    },
    setSidebarAutoCollapse: (state, action: PayloadAction<boolean>) => {
      state.sidebarAutoCollapse = action.payload;
    },
    setCompactMode: (state, action: PayloadAction<boolean>) => {
      state.compactMode = action.payload;
      try {
        window.localStorage.setItem(COMPACT_MODE_STORAGE_KEY, String(action.payload));
      } catch {
        // ignore
      }
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
    setSearchPrefill: (state, action: PayloadAction<SearchPrefill | null>) => {
      state.searchPrefill = action.payload;
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
    setTagManagerOpen: (state, action: PayloadAction<boolean>) => {
      state.tagManagerOpen = action.payload;
    },
    setCategoryManagerOpen: (state, action: PayloadAction<boolean>) => {
      state.categoryManagerOpen = action.payload;
    },
    setShareLinkManagerOpen: (state, action: PayloadAction<boolean>) => {
      state.shareLinkManagerOpen = action.payload;
    },
    setMlSuggestionsOpen: (state, action: PayloadAction<boolean>) => {
      state.mlSuggestionsOpen = action.payload;
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
      state.tagManagerOpen = false;
      state.categoryManagerOpen = false;
      state.shareLinkManagerOpen = false;
      state.mlSuggestionsOpen = false;
    },
  },
});

export const {
  toggleSidebar,
  setSidebarOpen,
  setSidebarAutoCollapse,
  setCompactMode,
  setViewMode,
  setSortBy,
  toggleSortOrder,
  setSearchOpen,
  setSearchPrefill,
  setUploadDialogOpen,
  setCreateFolderDialogOpen,
  setPropertiesDialogOpen,
  setPermissionsDialogOpen,
  setVersionHistoryDialogOpen,
  setTagManagerOpen,
  setCategoryManagerOpen,
  setShareLinkManagerOpen,
  setMlSuggestionsOpen,
  setSelectedNodeId,
  closeAllDialogs,
} = uiSlice.actions;

export default uiSlice.reducer;
