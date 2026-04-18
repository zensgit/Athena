import React, { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import {
  Autocomplete,
  Box,
  Paper,
  Typography,
  TextField,
  InputAdornment,
  IconButton,
  Chip,
  Stack,
  Grid,
  Card,
  CardContent,
  CardActions,
  Button,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  Menu,
  MenuItem,
  Pagination,
  CircularProgress,
  Alert,
  FormControl,
  FormControlLabel,
  InputLabel,
  Select,
  Divider,
  Checkbox,
  ListItemIcon,
  ListItemText,
  Switch,
  Tooltip,
} from '@mui/material';
import {
  Search,
  Clear,
  Folder,
  InsertDriveFile,
  Download,
  Visibility,
  Edit,
  AutoAwesome,
  FolderOpen,
  FilterList,
  Refresh,
  Autorenew,
  InfoOutlined,
  Star,
  StarBorder,
  CheckCircleOutline,
  Publish,
  Undo,
  AccountTree,
  Share as ShareIcon,
} from '@mui/icons-material';
import { format } from 'date-fns';
import { useLocation, useNavigate } from 'react-router-dom';
import { useAppSelector, useAppDispatch } from 'store';
import { executeSavedSearch, fetchSearchFacets, searchNodes, setLastSearchCriteria } from 'store/slices/nodeSlice';
import { setSearchOpen, setSearchPrefill, setSidebarOpen, setSelectedNodeId, setShareLinkManagerOpen } from 'store/slices/uiSlice';
import nodeService, { PreviewQueueStatus, SearchDiagnostics, SearchIndexStats, SearchRebuildStatus } from 'services/nodeService';
import savedSearchService, { SavedSearch } from 'services/savedSearchService';
import { Node, SearchCriteria } from 'types';
import { toast } from 'react-toastify';
import Highlight from 'components/search/Highlight';
import RecordStatusChip from 'components/records/RecordStatusChip';
import { buildSearchCriteriaFromSavedSearch } from 'utils/savedSearchUtils';
import {
  isPrecisionFilenameLikeQuery,
  shouldSkipSpellcheckForQuery,
  shouldSuppressStaleFallbackForQuery,
} from 'utils/searchFallbackUtils';
import { formatBreadcrumbPath } from 'utils/pathDisplayUtils';
import {
  formatPreviewFailureReasonLabel,
  getEffectivePreviewStatus,
  getFailedPreviewMeta,
  isRetryablePreviewFailure,
  normalizePreviewFailureReason,
  summarizeFailedPreviews,
} from 'utils/previewStatusUtils';
import { buildPreviewQueueOverride, PreviewQueueOverride } from 'utils/previewQueueOverrideUtils';
import { buildSearchErrorRecovery } from 'utils/searchErrorUtils';
import {
  getAdvancedSearchCancelCheckoutReason,
  getAdvancedSearchCheckInActionReason,
  getAdvancedSearchCheckoutActionReason,
} from 'utils/advancedSearchActionUtils';
import { getSearchResultCheckoutChip } from 'utils/advancedSearchCheckoutUtils';
import { getSearchResultLockChip } from 'utils/advancedSearchLockUtils';
import { getFileLockActionReason } from 'utils/fileLockBadgeUtils';
import { getRecordDeclarationFromNode } from 'utils/recordDeclarationUtils';
import CheckoutGraphDialog from 'components/dialogs/CheckoutGraphDialog';
import RenditionDefinitionDialog from 'components/dialogs/RenditionDefinitionDialog';
const DocumentPreview = React.lazy(() => import('components/preview/DocumentPreview'));

type FacetValue = { value: string; count: number };

const FILE_EXTENSIONS = [
  '.pdf',
  '.txt',
  '.doc',
  '.docx',
  '.xls',
  '.xlsx',
  '.ppt',
  '.pptx',
  '.csv',
  '.rtf',
  '.png',
  '.jpg',
  '.jpeg',
  '.gif',
  '.webp',
];

const MATCH_FIELD_LABELS: Record<string, string> = {
  name: 'Name',
  title: 'Title',
  description: 'Description',
  content: 'Content',
  textContent: 'Text',
  extractedText: 'Extracted text',
  tags: 'Tags',
  categories: 'Categories',
  correspondent: 'Correspondent',
};

const FALLBACK_AUTO_RETRY_MAX = 3;
const FALLBACK_AUTO_RETRY_BASE_DELAY_MS = 1500;
const FALLBACK_AUTO_RETRY_MAX_DELAY_MS = 10000;
type CheckoutFacetState = 'all' | 'checkedOut' | 'available';
type LockFacetState = 'all' | 'locked' | 'unlocked';

const getFallbackAutoRetryDelayMs = (attempt: number) => {
  if (attempt < 0) {
    return FALLBACK_AUTO_RETRY_BASE_DELAY_MS;
  }
  const scaled = FALLBACK_AUTO_RETRY_BASE_DELAY_MS * (2 ** attempt);
  return Math.min(scaled, FALLBACK_AUTO_RETRY_MAX_DELAY_MS);
};

const normalizeCriteriaValues = (values?: string[]) =>
  (values || [])
    .map((value) => value.trim())
    .filter((value) => value.length > 0)
    .sort();

const buildFallbackCriteriaKey = (criteria?: SearchCriteria): string => {
  if (!criteria) {
    return '';
  }

  const mimeTypes = criteria.mimeTypes?.length
    ? criteria.mimeTypes
    : (criteria.contentType ? [criteria.contentType] : []);
  const createdBy = criteria.createdByList?.length
    ? criteria.createdByList
    : (criteria.createdBy ? [criteria.createdBy] : []);

  return JSON.stringify({
    mimeTypes: normalizeCriteriaValues(mimeTypes),
    createdBy: normalizeCriteriaValues(createdBy),
    tags: normalizeCriteriaValues(criteria.tags),
    categories: normalizeCriteriaValues(criteria.categories),
    recordCategoryPaths: normalizeCriteriaValues(criteria.recordCategoryPaths),
    correspondents: normalizeCriteriaValues(criteria.correspondents),
    previewStatuses: normalizeCriteriaValues(criteria.previewStatuses),
    locked: criteria.locked ?? null,
    lockedBy: criteria.lockedBy?.trim() || '',
    checkedOut: criteria.checkedOut ?? null,
    checkoutUser: criteria.checkoutUser?.trim() || '',
    recordOnly: criteria.recordOnly ?? null,
    createdFrom: criteria.createdFrom || '',
    createdTo: criteria.createdTo || '',
    modifiedFrom: criteria.modifiedFrom || '',
    modifiedTo: criteria.modifiedTo || '',
    minSize: criteria.minSize ?? null,
    maxSize: criteria.maxSize ?? null,
    path: criteria.path || '',
    folderId: criteria.folderId || '',
    includeChildren: criteria.includeChildren ?? true,
    sortBy: criteria.sortBy || '',
    sortDirection: criteria.sortDirection || '',
  });
};

const SearchResults: React.FC = () => {
  const navigate = useNavigate();
  const location = useLocation();
  const dispatch = useAppDispatch();
  const { nodes, nodesTotal, loading, error, searchFacets, lastSearchCriteria } = useAppSelector((state) => state.node);
  const { user } = useAppSelector((state) => state.auth);
  const { sidebarAutoCollapse } = useAppSelector((state) => state.ui);
  const [quickSearch, setQuickSearch] = useState('');
  const [quickSearchSuggestions, setQuickSearchSuggestions] = useState<string[]>([]);
  const [quickSearchSuggestionsLoading, setQuickSearchSuggestionsLoading] = useState(false);
  const quickSearchSuggestDebounceRef = useRef<number | null>(null);
  const lastQuickSearchSuggestQueryRef = useRef<string>('');
  const [pinnedSavedSearches, setPinnedSavedSearches] = useState<SavedSearch[]>([]);
  const [pinnedSavedSearchesLoading, setPinnedSavedSearchesLoading] = useState(false);
  const [pinnedSavedSearchesError, setPinnedSavedSearchesError] = useState<string | null>(null);
  const [pinnedSavedSearchMenuAnchorEl, setPinnedSavedSearchMenuAnchorEl] = useState<null | HTMLElement>(null);
  const pinnedSavedSearchMenuOpen = Boolean(pinnedSavedSearchMenuAnchorEl);
  const lastSavedSearchIdFromUrlRef = useRef<string>('');
  const [sortBy, setSortBy] = useState('relevance');
  const [page, setPage] = useState(1);
  const pageSize = 20;

  const [selectedMimeTypes, setSelectedMimeTypes] = useState<string[]>([]);
  const [selectedCreators, setSelectedCreators] = useState<string[]>([]);
  const [selectedCorrespondents, setSelectedCorrespondents] = useState<string[]>([]);
  const [selectedTags, setSelectedTags] = useState<string[]>([]);
  const [selectedCategories, setSelectedCategories] = useState<string[]>([]);
  const [selectedRecordCategoryPaths, setSelectedRecordCategoryPaths] = useState<string[]>([]);
  const [selectedPreviewStatuses, setSelectedPreviewStatuses] = useState<string[]>([]);
  const [selectedLockState, setSelectedLockState] = useState<LockFacetState>('all');
  const [selectedLockOwner, setSelectedLockOwner] = useState('');
  const [selectedCheckoutState, setSelectedCheckoutState] = useState<CheckoutFacetState>('all');
  const [selectedCheckoutUser, setSelectedCheckoutUser] = useState('');
  const [selectedRecordOnly, setSelectedRecordOnly] = useState(false);
  const [previewNode, setPreviewNode] = useState<Node | null>(null);
  const [previewAnnotate, setPreviewAnnotate] = useState(false);
  const [hiddenNodeIds, setHiddenNodeIds] = useState<string[]>([]);
  const [fallbackNodes, setFallbackNodes] = useState<Node[]>([]);
  const [fallbackLabel, setFallbackLabel] = useState('');
  const [fallbackCriteriaKey, setFallbackCriteriaKey] = useState('');
  const [dismissedFallbackCriteriaKey, setDismissedFallbackCriteriaKey] = useState('');
  const [forcedFallbackCriteriaKey, setForcedFallbackCriteriaKey] = useState('');
  const [fallbackAutoRetryCount, setFallbackAutoRetryCount] = useState(0);
  const [fallbackLastRetryAt, setFallbackLastRetryAt] = useState<Date | null>(null);
  const fallbackAutoRetryTimerRef = useRef<number | null>(null);
  const [queueingPreviewId, setQueueingPreviewId] = useState<string | null>(null);
  const [previewQueueStatusById, setPreviewQueueStatusById] = useState<Record<string, PreviewQueueOverride>>({});
  const [batchRetrying, setBatchRetrying] = useState(false);
  const [similarResults, setSimilarResults] = useState<Node[] | null>(null);
  const [similarSource, setSimilarSource] = useState<{ id: string; name?: string } | null>(null);
  const [similarLoadingId, setSimilarLoadingId] = useState<string | null>(null);
  const [similarError, setSimilarError] = useState<string | null>(null);
  const [suggestedFilters, setSuggestedFilters] = useState<
    Array<{ field: string; label: string; value: string; count?: number }>
  >([]);
  const [suggestedFiltersLoading, setSuggestedFiltersLoading] = useState(false);
  const [suggestedFiltersError, setSuggestedFiltersError] = useState<string | null>(null);
  const [spellcheckSuggestions, setSpellcheckSuggestions] = useState<string[]>([]);
  const [spellcheckLoading, setSpellcheckLoading] = useState(false);
  const [spellcheckError, setSpellcheckError] = useState<string | null>(null);
  const [searchDiagnostics, setSearchDiagnostics] = useState<SearchDiagnostics | null>(null);
  const [searchDiagnosticsLoading, setSearchDiagnosticsLoading] = useState(false);
  const [searchDiagnosticsError, setSearchDiagnosticsError] = useState<string | null>(null);
  const [searchIndexStats, setSearchIndexStats] = useState<SearchIndexStats | null>(null);
  const [searchIndexStatsLoading, setSearchIndexStatsLoading] = useState(false);
  const [searchIndexStatsError, setSearchIndexStatsError] = useState<string | null>(null);
  const [searchRebuildStatus, setSearchRebuildStatus] = useState<SearchRebuildStatus | null>(null);
  const [searchRebuildStatusLoading, setSearchRebuildStatusLoading] = useState(false);
  const [searchRebuildStatusError, setSearchRebuildStatusError] = useState<string | null>(null);
  const lastSpellcheckQueryRef = useRef('');
  const previewOpen = Boolean(previewNode);
  const isAdmin = Boolean(user?.roles?.includes('ROLE_ADMIN'));
  const canWrite = Boolean(user?.roles?.includes('ROLE_ADMIN') || user?.roles?.includes('ROLE_EDITOR'));
  const currentUsername = user?.username ?? null;
  const [checkoutActionNodeId, setCheckoutActionNodeId] = useState<string | null>(null);
  const [cancelCheckoutActionNodeId, setCancelCheckoutActionNodeId] = useState<string | null>(null);
  const [checkInDialogNode, setCheckInDialogNode] = useState<Node | null>(null);
  const [checkInFile, setCheckInFile] = useState<File | null>(null);
  const [checkInComment, setCheckInComment] = useState('');
  const [checkInMajorVersion, setCheckInMajorVersion] = useState(false);
  const [checkInKeepCheckedOut, setCheckInKeepCheckedOut] = useState(false);
  const [checkInSubmittingNodeId, setCheckInSubmittingNodeId] = useState<string | null>(null);
  const [checkoutGraphDialogNode, setCheckoutGraphDialogNode] = useState<Node | null>(null);
  const [renditionDialogNode, setRenditionDialogNode] = useState<Node | null>(null);
  const primarySearchErrorRecovery = useMemo(
    () => (error ? buildSearchErrorRecovery(error, 'Search failed') : null),
    [error]
  );
  // During criteria -> UI state sync, facet-change effects can run with stale UI values and
  // accidentally trigger an extra search. Use a state flag so effects re-run once the sync is done.
  const [facetSyncSuppressed, setFacetSyncSuppressed] = useState(false);
  const quickSearchDebounceRef = useRef<number | null>(null);
  const lastSearchCriteriaRef = useRef<SearchCriteria | undefined>(lastSearchCriteria);
  const isSimilarMode = similarResults !== null;
  const previewRetrySummary = useMemo(() => {
    const entries = Object.values(previewQueueStatusById);
    const nextTimes = entries
      .map((entry) => entry.nextAttemptAt)
      .filter((value): value is string => Boolean(value))
      .map((value) => new Date(value).getTime())
      .filter((value) => !Number.isNaN(value));
    if (entries.length === 0 || nextTimes.length === 0) {
      return null;
    }
    const nextAt = new Date(Math.min(...nextTimes));
    return {
      count: entries.length,
      nextAt,
    };
  }, [previewQueueStatusById]);

  const clearSimilarResults = useCallback(() => {
    setSimilarResults(null);
    setSimilarSource(null);
    setSimilarLoadingId(null);
    setSimilarError(null);
  }, []);

  const loadPinnedSavedSearches = useCallback(async () => {
    setPinnedSavedSearchesLoading(true);
    try {
      const searches = await savedSearchService.list();
      const pinned = (searches || []).filter((item) => item.pinned);
      pinned.sort((a, b) => {
        const aTime = a.createdAt ? new Date(a.createdAt).getTime() : 0;
        const bTime = b.createdAt ? new Date(b.createdAt).getTime() : 0;
        return bTime - aTime;
      });
      setPinnedSavedSearches(pinned);
      setPinnedSavedSearchesError(null);
    } catch {
      setPinnedSavedSearches([]);
      setPinnedSavedSearchesError('Failed to load pinned saved searches');
    } finally {
      setPinnedSavedSearchesLoading(false);
    }
  }, []);

  const handleOpenPinnedSavedSearchMenu = (event: React.MouseEvent<HTMLElement>) => {
    setPinnedSavedSearchMenuAnchorEl(event.currentTarget);
    loadPinnedSavedSearches();
  };

  const handleClosePinnedSavedSearchMenu = () => {
    setPinnedSavedSearchMenuAnchorEl(null);
  };

  const handleRunPinnedSavedSearch = async (item: SavedSearch) => {
    handleClosePinnedSavedSearchMenu();
    try {
      await dispatch(executeSavedSearch(item.id)).unwrap();
      dispatch(setLastSearchCriteria(buildSearchCriteriaFromSavedSearch(item)));
    } catch {
      toast.error('Failed to execute saved search');
    }
  };

  const handleUnpinSavedSearch = async (item: SavedSearch) => {
    try {
      await savedSearchService.setPinned(item.id, false);
      setPinnedSavedSearches((prev) => prev.filter((current) => current.id !== item.id));
      toast.success('Unpinned saved search');
    } catch {
      toast.error('Failed to update pin');
    }
  };

  const runSearch = useCallback(
    (criteria: SearchCriteria) => {
      clearSimilarResults();
      return dispatch(searchNodes(criteria));
    },
    [clearSimilarResults, dispatch]
  );

  const loadSearchDiagnostics = useCallback(async (options?: { silent?: boolean }) => {
    const silent = options?.silent === true;
    if (!silent) {
      setSearchDiagnosticsLoading(true);
    }
    try {
      const diagnostics = await nodeService.getSearchDiagnostics();
      setSearchDiagnostics(diagnostics);
      setSearchDiagnosticsError(null);
    } catch {
      setSearchDiagnosticsError('Failed to load search diagnostics');
    } finally {
      setSearchDiagnosticsLoading(false);
    }
  }, []);

  const loadSearchIndexStats = useCallback(async (options?: { silent?: boolean }) => {
    if (!isAdmin) {
      setSearchIndexStats(null);
      setSearchIndexStatsError(null);
      setSearchIndexStatsLoading(false);
      return;
    }
    const silent = options?.silent === true;
    if (!silent) {
      setSearchIndexStatsLoading(true);
    }
    try {
      const stats = await nodeService.getSearchIndexStats();
      setSearchIndexStats(stats);
      setSearchIndexStatsError(stats.error ?? null);
    } catch {
      setSearchIndexStatsError('Failed to load index stats');
    } finally {
      setSearchIndexStatsLoading(false);
    }
  }, [isAdmin]);

  const loadSearchRebuildStatus = useCallback(async (options?: { silent?: boolean }) => {
    if (!isAdmin) {
      setSearchRebuildStatus(null);
      setSearchRebuildStatusError(null);
      setSearchRebuildStatusLoading(false);
      return;
    }
    const silent = options?.silent === true;
    if (!silent) {
      setSearchRebuildStatusLoading(true);
    }
    try {
      const status = await nodeService.getSearchRebuildStatus();
      setSearchRebuildStatus(status);
      setSearchRebuildStatusError(null);
    } catch {
      setSearchRebuildStatusError('Failed to load rebuild status');
    } finally {
      setSearchRebuildStatusLoading(false);
    }
  }, [isAdmin]);

  const handleRefreshDiagnostics = () => {
    loadSearchDiagnostics();
    loadSearchIndexStats();
    loadSearchRebuildStatus();
  };

  const getSortParams = (value: string) => {
    switch (value) {
      case 'name':
        return { sortBy: 'name', sortDirection: 'asc' as const };
      case 'modified':
        return { sortBy: 'modified', sortDirection: 'desc' as const };
      case 'size':
        return { sortBy: 'size', sortDirection: 'desc' as const };
      default:
        return { sortBy: undefined, sortDirection: undefined };
    }
  };

  const handleQuickSearch = async (e: React.FormEvent<HTMLFormElement>) => {
    e.preventDefault();
    if (quickSearchDebounceRef.current) {
      window.clearTimeout(quickSearchDebounceRef.current);
    }
    const formData = new FormData(e.currentTarget);
    const rawQuery = formData.get('quickSearch');
    const query = (typeof rawQuery === 'string' ? rawQuery : quickSearch).trim();
    if (!query) {
      return;
    }
    if (query !== quickSearch) {
      setQuickSearch(query);
    }
    setPage(1);
    const sortParams = getSortParams(sortBy);
    const scopeParams = lastSearchCriteria?.folderId
      ? { folderId: lastSearchCriteria.folderId, includeChildren: lastSearchCriteria.includeChildren ?? true }
      : {};
    await runSearch({ name: query, page: 0, size: pageSize, ...scopeParams, ...sortParams });
  };

  const handleClearSearch = () => {
    setQuickSearch('');
  };

  const handleAdvancedSearch = () => {
    const normalizedQuickSearch = quickSearch.trim();
    const normalizedLastName = (lastSearchCriteria?.name || '').trim();
    const contentType =
      selectedMimeTypes.length === 1
        ? selectedMimeTypes[0]
        : (lastSearchCriteria?.contentType || '');
    const createdBy =
      selectedCreators.length === 1
        ? selectedCreators[0]
        : (lastSearchCriteria?.createdBy || '');

    dispatch(setSearchPrefill({
      name: normalizedQuickSearch || normalizedLastName,
      contentType,
      createdBy,
      createdFrom: lastSearchCriteria?.createdFrom,
      createdTo: lastSearchCriteria?.createdTo,
      modifiedFrom: lastSearchCriteria?.modifiedFrom,
      modifiedTo: lastSearchCriteria?.modifiedTo,
      aspects: lastSearchCriteria?.aspects || [],
      properties: lastSearchCriteria?.properties || {},
      tags: selectedTags.length > 0 ? selectedTags : (lastSearchCriteria?.tags || []),
      categories: selectedCategories.length > 0 ? selectedCategories : (lastSearchCriteria?.categories || []),
      recordCategoryPaths: selectedRecordCategoryPaths.length > 0
        ? selectedRecordCategoryPaths
        : (lastSearchCriteria?.recordCategoryPaths || []),
      correspondents: selectedCorrespondents.length > 0
        ? selectedCorrespondents
        : (lastSearchCriteria?.correspondents || []),
      previewStatuses: selectedPreviewStatuses.length > 0
        ? selectedPreviewStatuses
        : (lastSearchCriteria?.previewStatuses || []),
      locked: selectedLockState === 'locked'
        ? true
        : selectedLockState === 'unlocked'
          ? false
          : lastSearchCriteria?.locked,
      lockedBy: selectedLockOwner.trim() || lastSearchCriteria?.lockedBy || '',
      checkedOut: selectedCheckoutState === 'checkedOut'
        ? true
        : selectedCheckoutState === 'available'
          ? false
          : lastSearchCriteria?.checkedOut,
      checkoutUser: selectedCheckoutUser.trim() || lastSearchCriteria?.checkoutUser || '',
      recordOnly: selectedRecordOnly || lastSearchCriteria?.recordOnly || false,
      minSize: lastSearchCriteria?.minSize,
      maxSize: lastSearchCriteria?.maxSize,
      pathPrefix: lastSearchCriteria?.path || '',
      folderId: lastSearchCriteria?.folderId || '',
      includeChildren: lastSearchCriteria?.includeChildren ?? true,
    }));
    dispatch(setSearchOpen(true));
  };

  const folderScopeLabel = lastSearchCriteria?.includeChildren === false
    ? 'Scope: This folder (no subfolders)'
    : 'Scope: This folder';

  const handleClearFolderScope = () => {
    if (!lastSearchCriteria?.folderId) {
      return;
    }
    const sortParams = getSortParams(sortBy);
    runSearch({
      ...lastSearchCriteria,
      folderId: undefined,
      includeChildren: undefined,
      page: 0,
      size: pageSize,
      ...sortParams,
    });
  };

  const handleRetrySearch = useCallback(() => {
    if (!lastSearchCriteria) {
      return;
    }
    setFallbackLastRetryAt(new Date());
    const sortParams = getSortParams(sortBy);
    runSearch({ ...lastSearchCriteria, page: 0, size: pageSize, ...sortParams });
  }, [lastSearchCriteria, sortBy, pageSize, runSearch]);

  const handleRetryPrimarySearch = useCallback(() => {
    if (lastSearchCriteria) {
      const sortParams = getSortParams(sortBy);
      runSearch({ ...lastSearchCriteria, page: 0, size: pageSize, ...sortParams });
      return;
    }
    const normalizedQuery = quickSearch.trim();
    if (!normalizedQuery) {
      return;
    }
    const sortParams = getSortParams(sortBy);
    runSearch({ name: normalizedQuery, page: 0, size: pageSize, ...sortParams });
  }, [lastSearchCriteria, pageSize, quickSearch, runSearch, sortBy]);

  const handleGoHomeFromError = useCallback(() => {
    navigate('/browse/root');
    if (sidebarAutoCollapse) {
      dispatch(setSidebarOpen(false));
    }
  }, [dispatch, navigate, sidebarAutoCollapse]);

  const handleHideFallbackResults = useCallback(() => {
    const criteriaKey = buildFallbackCriteriaKey(lastSearchCriteria);
    if (!criteriaKey) {
      return;
    }
    setDismissedFallbackCriteriaKey(criteriaKey);
    setForcedFallbackCriteriaKey('');
    setFallbackAutoRetryCount(0);
    setFallbackLastRetryAt(null);
  }, [lastSearchCriteria]);

  const handleShowFallbackResults = useCallback(() => {
    const criteriaKey = buildFallbackCriteriaKey(lastSearchCriteria);
    if (!criteriaKey) {
      return;
    }
    setForcedFallbackCriteriaKey(criteriaKey);
    setDismissedFallbackCriteriaKey('');
  }, [lastSearchCriteria]);

  const handleSpellcheckSuggestion = (suggestion: string) => {
    const nextQuery = suggestion.trim();
    if (!nextQuery) {
      return;
    }
    setQuickSearch(nextQuery);
    setPage(1);
    const sortParams = getSortParams(sortBy);
    const scopeParams = lastSearchCriteria?.folderId
      ? { folderId: lastSearchCriteria.folderId, includeChildren: lastSearchCriteria.includeChildren ?? true }
      : {};
    runSearch({ name: nextQuery, page: 0, size: pageSize, ...scopeParams, ...sortParams });
  };

  const clearFacetFilters = () => {
    setSelectedMimeTypes([]);
    setSelectedCreators([]);
    setSelectedCorrespondents([]);
    setSelectedTags([]);
    setSelectedCategories([]);
    setSelectedRecordCategoryPaths([]);
    setSelectedPreviewStatuses([]);
    setSelectedLockState('all');
    setSelectedLockOwner('');
    setSelectedCheckoutState('all');
    setSelectedCheckoutUser('');
    setSelectedRecordOnly(false);
  };

  const removeFacetValue = (
    setter: React.Dispatch<React.SetStateAction<string[]>>,
    value: string
  ) => {
    setter((prev) => prev.filter((item) => item !== value));
  };

  const togglePreviewStatus = (status: string) => {
    setSelectedPreviewStatuses((prev) =>
      prev.includes(status) ? prev.filter((item) => item !== status) : [...prev, status]
    );
  };
  const nonPreviewFiltersApplied =
    selectedMimeTypes.length > 0 ||
    selectedCreators.length > 0 ||
    selectedCorrespondents.length > 0 ||
    selectedTags.length > 0 ||
    selectedCategories.length > 0 ||
    selectedRecordCategoryPaths.length > 0 ||
    selectedLockState !== 'all' ||
    selectedLockOwner.trim().length > 0 ||
    selectedCheckoutState !== 'all' ||
    selectedCheckoutUser.trim().length > 0 ||
    selectedRecordOnly;
  const previewStatusFilterApplied = selectedPreviewStatuses.length > 0;
  const filtersApplied = nonPreviewFiltersApplied || previewStatusFilterApplied;

  useEffect(() => {
    if (!lastSearchCriteria) {
      return;
    }
    setFacetSyncSuppressed(true);
    const baseMimeTypes = lastSearchCriteria.mimeTypes
      ?? (lastSearchCriteria.contentType ? [lastSearchCriteria.contentType] : []);
    const baseCreators = lastSearchCriteria.createdByList
      ?? (lastSearchCriteria.createdBy ? [lastSearchCriteria.createdBy] : []);

    setSelectedMimeTypes(baseMimeTypes);
    setSelectedCreators(baseCreators);
    setSelectedCorrespondents(lastSearchCriteria.correspondents || []);
    setSelectedTags(lastSearchCriteria.tags || []);
    setSelectedCategories(lastSearchCriteria.categories || []);
    setSelectedRecordCategoryPaths(lastSearchCriteria.recordCategoryPaths || []);
    setSelectedPreviewStatuses(lastSearchCriteria.previewStatuses || []);
    setSelectedLockState(
      lastSearchCriteria.locked === true
        ? 'locked'
        : lastSearchCriteria.locked === false
          ? 'unlocked'
          : 'all'
    );
    setSelectedLockOwner(lastSearchCriteria.lockedBy || '');
    setSelectedCheckoutState(
      lastSearchCriteria.checkedOut === true
        ? 'checkedOut'
        : lastSearchCriteria.checkedOut === false
          ? 'available'
          : 'all'
    );
    setSelectedCheckoutUser(lastSearchCriteria.checkoutUser || '');
    setSelectedRecordOnly(Boolean(lastSearchCriteria.recordOnly));
    setPage(1);

    const timer = window.setTimeout(() => {
      setFacetSyncSuppressed(false);
    }, 0);

    return () => window.clearTimeout(timer);
  }, [lastSearchCriteria]);

  useEffect(() => {
    loadSearchDiagnostics({ silent: true });
    loadSearchIndexStats({ silent: true });
    loadSearchRebuildStatus({ silent: true });
  }, [loadSearchDiagnostics, loadSearchIndexStats, loadSearchRebuildStatus]);

  useEffect(() => {
    const query = (lastSearchCriteria?.name || '').trim();
    if (!query || nonPreviewFiltersApplied || previewStatusFilterApplied) {
      return;
    }
    dispatch(fetchSearchFacets(query));
  }, [lastSearchCriteria, dispatch, nonPreviewFiltersApplied, previewStatusFilterApplied]);

  useEffect(() => {
    const query = (lastSearchCriteria?.name || '').trim();
    if (!query || isSimilarMode) {
      setSuggestedFilters([]);
      setSuggestedFiltersError(null);
      return;
    }

    let active = true;
    setSuggestedFiltersLoading(true);
    nodeService.getSuggestedFilters(query)
      .then((filters) => {
        if (!active) return;
        setSuggestedFilters(Array.isArray(filters) ? filters : []);
        setSuggestedFiltersError(null);
      })
      .catch(() => {
        if (!active) return;
        setSuggestedFilters([]);
        setSuggestedFiltersError('Failed to load suggested filters');
      })
      .finally(() => {
        if (active) {
          setSuggestedFiltersLoading(false);
        }
      });

    return () => {
      active = false;
    };
  }, [lastSearchCriteria, isSimilarMode]);

  useEffect(() => {
    const query = (lastSearchCriteria?.name || '').trim();
    if (!query || isSimilarMode) {
      lastSpellcheckQueryRef.current = '';
      setSpellcheckSuggestions([]);
      setSpellcheckError(null);
      setSpellcheckLoading(false);
      return;
    }
    if (shouldSkipSpellcheckForQuery(query)) {
      lastSpellcheckQueryRef.current = query;
      setSpellcheckSuggestions([]);
      setSpellcheckError(null);
      setSpellcheckLoading(false);
      return;
    }

    if (lastSpellcheckQueryRef.current === query) {
      return;
    }

    lastSpellcheckQueryRef.current = query;
    let active = true;
    setSpellcheckLoading(true);
    nodeService.getSpellcheckSuggestions(query)
      .then((suggestions) => {
        if (!active) return;
        setSpellcheckSuggestions(Array.isArray(suggestions) ? suggestions : []);
        setSpellcheckError(null);
      })
      .catch(() => {
        if (!active) return;
        setSpellcheckSuggestions([]);
        setSpellcheckError('Failed to load spellcheck suggestions');
      })
      .finally(() => {
        if (active) {
          setSpellcheckLoading(false);
        }
      });

    return () => {
      active = false;
    };
  }, [lastSearchCriteria, isSimilarMode]);

  useEffect(() => {
    if (lastSearchCriteria?.name !== undefined) {
      setQuickSearch(lastSearchCriteria.name || '');
    }
  }, [lastSearchCriteria]);

  useEffect(() => {
    const params = new URLSearchParams(location.search);
    const savedSearchId = (params.get('savedSearchId') || '').trim();
    if (!savedSearchId) {
      return;
    }

    if (savedSearchId === lastSavedSearchIdFromUrlRef.current) {
      return;
    }
    lastSavedSearchIdFromUrlRef.current = savedSearchId;

    let active = true;

    (async () => {
      try {
        const savedSearch = await savedSearchService.get(savedSearchId);

        await dispatch(executeSavedSearch(savedSearchId)).unwrap();
        if (!active) {
          return;
        }
        dispatch(setLastSearchCriteria(buildSearchCriteriaFromSavedSearch(savedSearch)));
      } catch {
        if (active) {
          toast.error('Failed to load saved search');
        }
      }
    })();

    return () => {
      active = false;
    };
  }, [dispatch, location.search]);

  useEffect(() => {
    const prefix = quickSearch.trim();
    if (prefix.length < 2) {
      lastQuickSearchSuggestQueryRef.current = '';
      setQuickSearchSuggestions([]);
      setQuickSearchSuggestionsLoading(false);
      if (quickSearchSuggestDebounceRef.current) {
        window.clearTimeout(quickSearchSuggestDebounceRef.current);
        quickSearchSuggestDebounceRef.current = null;
      }
      return;
    }

    if (prefix === lastQuickSearchSuggestQueryRef.current) {
      return;
    }

    if (quickSearchSuggestDebounceRef.current) {
      window.clearTimeout(quickSearchSuggestDebounceRef.current);
    }

    let active = true;
    quickSearchSuggestDebounceRef.current = window.setTimeout(() => {
      lastQuickSearchSuggestQueryRef.current = prefix;
      setQuickSearchSuggestionsLoading(true);
      nodeService.getSearchSuggestions(prefix, 10)
        .then((suggestions) => {
          if (!active) return;
          setQuickSearchSuggestions(Array.isArray(suggestions) ? suggestions : []);
        })
        .catch(() => {
          if (!active) return;
          setQuickSearchSuggestions([]);
        })
        .finally(() => {
          if (active) {
            setQuickSearchSuggestionsLoading(false);
          }
        });
    }, 250);

    return () => {
      active = false;
      if (quickSearchSuggestDebounceRef.current) {
        window.clearTimeout(quickSearchSuggestDebounceRef.current);
        quickSearchSuggestDebounceRef.current = null;
      }
    };
  }, [quickSearch]);

  useEffect(() => {
    const state = location.state as { similarSourceId?: string; similarSourceName?: string } | null;
    const sourceId = state?.similarSourceId;
    if (!sourceId) {
      return;
    }

    let active = true;
    setSimilarLoadingId(sourceId);
    setSimilarError(null);
    nodeService.findSimilar(sourceId, pageSize)
      .then((results) => {
        if (!active) return;
        setSimilarResults(results);
        setSimilarSource({ id: sourceId, name: state.similarSourceName });
        setPage(1);
        if (!results || results.length === 0) {
          toast.info('No similar documents found');
        }
      })
      .catch((error: any) => {
        if (!active) return;
        const message = error?.response?.data?.message || 'Failed to load similar documents';
        setSimilarError(message);
        toast.error(message);
      })
      .finally(() => {
        if (active) {
          setSimilarLoadingId(null);
        }
      });

    navigate(location.pathname, { replace: true, state: null });

    return () => {
      active = false;
    };
  }, [location.pathname, location.state, navigate, pageSize]);

  useEffect(() => {
    const query = quickSearch.trim();
    if (!query) {
      return;
    }
    if (query === (lastSearchCriteria?.name || '')) {
      return;
    }
    if (quickSearchDebounceRef.current) {
      window.clearTimeout(quickSearchDebounceRef.current);
    }
    quickSearchDebounceRef.current = window.setTimeout(() => {
      const sortParams = getSortParams(sortBy);
      runSearch({ name: query, page: 0, size: pageSize, ...sortParams });
    }, 400);

    return () => {
      if (quickSearchDebounceRef.current) {
        window.clearTimeout(quickSearchDebounceRef.current);
      }
    };
  }, [quickSearch, lastSearchCriteria, pageSize, runSearch, sortBy]);

  useEffect(() => {
    if (lastSearchCriteria?.page !== undefined) {
      setPage(lastSearchCriteria.page + 1);
    }
  }, [lastSearchCriteria]);

  useEffect(() => {
    if (lastSearchCriteria) {
      setHiddenNodeIds([]);
    }
  }, [lastSearchCriteria]);

  useEffect(() => {
    lastSearchCriteriaRef.current = lastSearchCriteria;
  }, [lastSearchCriteria]);

  useEffect(() => {
    if (nodes.length > 0) {
      const criteria = lastSearchCriteriaRef.current;
      setFallbackNodes(nodes);
      setFallbackLabel((criteria?.name || '').trim());
      setFallbackCriteriaKey(buildFallbackCriteriaKey(criteria));
    }
  }, [nodes]);

  const currentFallbackCriteriaKey = useMemo(
    () => buildFallbackCriteriaKey(lastSearchCriteria),
    [lastSearchCriteria]
  );

  useEffect(() => {
    setFallbackAutoRetryCount(0);
  }, [currentFallbackCriteriaKey]);

  useEffect(() => {
    if (!dismissedFallbackCriteriaKey) {
      return;
    }
    if (!currentFallbackCriteriaKey || currentFallbackCriteriaKey !== dismissedFallbackCriteriaKey) {
      setDismissedFallbackCriteriaKey('');
    }
  }, [currentFallbackCriteriaKey, dismissedFallbackCriteriaKey]);

  useEffect(() => {
    if (!forcedFallbackCriteriaKey) {
      return;
    }
    if (!currentFallbackCriteriaKey || currentFallbackCriteriaKey !== forcedFallbackCriteriaKey) {
      setForcedFallbackCriteriaKey('');
    }
  }, [currentFallbackCriteriaKey, forcedFallbackCriteriaKey]);

  useEffect(() => {
    if (!lastSearchCriteria || facetSyncSuppressed) {
      return;
    }

    const normalize = (values: string[]) =>
      values.map((value) => value.trim()).filter((value) => value.length > 0).sort();
    const arraysEqual = (left: string[], right: string[]) => {
      if (left.length !== right.length) return false;
      return left.every((value, index) => value === right[index]);
    };

    const baseMimeTypes = normalize(
      lastSearchCriteria.mimeTypes
        ?? (lastSearchCriteria.contentType ? [lastSearchCriteria.contentType] : [])
    );
    const baseCreators = normalize(
      lastSearchCriteria.createdByList
        ?? (lastSearchCriteria.createdBy ? [lastSearchCriteria.createdBy] : [])
    );
    const baseTags = normalize(lastSearchCriteria.tags || []);
    const baseCategories = normalize(lastSearchCriteria.categories || []);
    const baseRecordCategoryPaths = normalize(lastSearchCriteria.recordCategoryPaths || []);
    const baseCorrespondents = normalize(lastSearchCriteria.correspondents || []);
    const basePreviewStatuses = normalize(lastSearchCriteria.previewStatuses || []);
    const baseLockState: LockFacetState =
      lastSearchCriteria.locked === true
        ? 'locked'
        : lastSearchCriteria.locked === false
          ? 'unlocked'
          : 'all';
    const baseLockOwner = (lastSearchCriteria.lockedBy || '').trim();
    const baseCheckoutState: CheckoutFacetState =
      lastSearchCriteria.checkedOut === true
        ? 'checkedOut'
        : lastSearchCriteria.checkedOut === false
          ? 'available'
          : 'all';
    const baseCheckoutUser = (lastSearchCriteria.checkoutUser || '').trim();
    const baseRecordOnly = Boolean(lastSearchCriteria.recordOnly);

    const nextMimeTypes = normalize(selectedMimeTypes);
    const nextCreators = normalize(selectedCreators);
    const nextTags = normalize(selectedTags);
    const nextCategories = normalize(selectedCategories);
    const nextRecordCategoryPaths = normalize(selectedRecordCategoryPaths);
    const nextCorrespondents = normalize(selectedCorrespondents);
    const nextPreviewStatuses = normalize(selectedPreviewStatuses);
    const nextLockOwner = selectedLockOwner.trim();
    const nextCheckoutUser = selectedCheckoutUser.trim();

    if (
      arraysEqual(baseMimeTypes, nextMimeTypes)
      && arraysEqual(baseCreators, nextCreators)
      && arraysEqual(baseTags, nextTags)
      && arraysEqual(baseCategories, nextCategories)
      && arraysEqual(baseRecordCategoryPaths, nextRecordCategoryPaths)
      && arraysEqual(baseCorrespondents, nextCorrespondents)
      && arraysEqual(basePreviewStatuses, nextPreviewStatuses)
      && baseLockState === selectedLockState
      && baseLockOwner === nextLockOwner
      && baseCheckoutState === selectedCheckoutState
      && baseCheckoutUser === nextCheckoutUser
      && baseRecordOnly === selectedRecordOnly
    ) {
      return;
    }

    const sortParams = getSortParams(sortBy);
    runSearch({
      ...lastSearchCriteria,
      mimeTypes: nextMimeTypes.length ? nextMimeTypes : undefined,
      contentType: undefined,
      createdByList: nextCreators.length ? nextCreators : undefined,
      createdBy: undefined,
      tags: nextTags.length ? nextTags : undefined,
      categories: nextCategories.length ? nextCategories : undefined,
      recordCategoryPaths: nextRecordCategoryPaths.length ? nextRecordCategoryPaths : undefined,
      correspondents: nextCorrespondents.length ? nextCorrespondents : undefined,
      previewStatuses: nextPreviewStatuses.length ? nextPreviewStatuses : undefined,
      locked: selectedLockState === 'locked'
        ? true
        : selectedLockState === 'unlocked'
          ? false
          : undefined,
      lockedBy: nextLockOwner || undefined,
      checkedOut: selectedCheckoutState === 'checkedOut'
        ? true
        : selectedCheckoutState === 'available'
          ? false
          : undefined,
      checkoutUser: nextCheckoutUser || undefined,
      recordOnly: selectedRecordOnly || undefined,
      page: 0,
      size: pageSize,
      ...sortParams,
    });
  }, [
    selectedMimeTypes,
    selectedCreators,
    selectedCorrespondents,
    selectedTags,
    selectedCategories,
    selectedRecordCategoryPaths,
    selectedPreviewStatuses,
    selectedLockState,
    selectedLockOwner,
    selectedCheckoutState,
    selectedCheckoutUser,
    selectedRecordOnly,
    lastSearchCriteria,
    facetSyncSuppressed,
    pageSize,
    runSearch,
    sortBy,
  ]);

  useEffect(() => {
    setPage(1);
  }, [
    selectedMimeTypes,
    selectedCreators,
    selectedCorrespondents,
    selectedTags,
    selectedCategories,
    selectedRecordCategoryPaths,
    selectedPreviewStatuses,
    selectedLockState,
    selectedLockOwner,
    selectedCheckoutState,
    selectedCheckoutUser,
    selectedRecordOnly,
  ]);

  useEffect(() => {
    if (!lastSearchCriteria) {
      return;
    }
    const sortParams = getSortParams(sortBy);
    if (
      lastSearchCriteria.sortBy === sortParams.sortBy
      && lastSearchCriteria.sortDirection === sortParams.sortDirection
    ) {
      return;
    }
    setPage(1);
    runSearch({ ...lastSearchCriteria, page: 0, size: pageSize, ...sortParams });
  }, [sortBy, lastSearchCriteria, pageSize, runSearch]);

  const isDocumentNode = (node: Node) => {
    const normalizedName = node.name?.trim().toLowerCase() || '';
    const hasExtension = FILE_EXTENSIONS.some((ext) => normalizedName.endsWith(ext));
    const normalizedPath = node.path?.trim().toLowerCase() || '';
    const hasPathExtension = FILE_EXTENSIONS.some((ext) => normalizedPath.endsWith(ext));
    const contentTypeHint = node.contentType
      || node.properties?.mimeType
      || node.properties?.contentType;
    const sizeHint = node.size
      || node.properties?.fileSize
      || node.properties?.size;
    return node.nodeType === 'DOCUMENT'
      || Boolean(contentTypeHint || sizeHint || node.currentVersionLabel)
      || hasExtension
      || hasPathExtension;
  };

  const isPdfDocument = (node: Node) => {
    const contentTypeHint = node.contentType
      || node.properties?.mimeType
      || node.properties?.contentType;
    const normalizedType = contentTypeHint?.toLowerCase();
    if (normalizedType && normalizedType.includes('pdf')) {
      return true;
    }
    const normalizedName = node.name?.trim().toLowerCase() || '';
    return normalizedName.endsWith('.pdf');
  };

  const isOfficeDocument = (node: Node) => {
    const contentTypeHint = node.contentType
      || node.properties?.mimeType
      || node.properties?.contentType;
    const normalizedType = contentTypeHint?.toLowerCase();
    if (normalizedType) {
      const officeTypes = new Set([
        'application/msword',
        'application/vnd.openxmlformats-officedocument.wordprocessingml.document',
        'application/vnd.ms-excel',
        'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',
        'application/vnd.ms-powerpoint',
        'application/vnd.openxmlformats-officedocument.presentationml.presentation',
        'application/vnd.oasis.opendocument.text',
        'application/vnd.oasis.opendocument.spreadsheet',
        'application/vnd.oasis.opendocument.presentation',
        'application/rtf',
        'text/rtf',
      ]);
      if (officeTypes.has(normalizedType)) {
        return true;
      }
    }
    const normalizedName = node.name?.trim().toLowerCase() || '';
    return [
      '.doc',
      '.docx',
      '.xls',
      '.xlsx',
      '.ppt',
      '.pptx',
      '.odt',
      '.ods',
      '.odp',
      '.rtf',
    ].some((ext) => normalizedName.endsWith(ext));
  };

  const isFolderNode = (node: Node) => node.nodeType === 'FOLDER' && !isDocumentNode(node);

  const hideNodeFromResults = (nodeId: string) => {
    setHiddenNodeIds((prev) => (prev.includes(nodeId) ? prev : [...prev, nodeId]));
  };

  const handlePreviewNode = async (node: Node, options?: { annotate?: boolean }) => {
    try {
      setPreviewAnnotate(Boolean(options?.annotate));
      const freshNode = await nodeService.getNode(node.id);
      if (isFolderNode(freshNode)) {
        navigate(`/browse/${freshNode.id}`);
        if (sidebarAutoCollapse) {
          dispatch(setSidebarOpen(false));
        }
      } else {
        setPreviewNode(freshNode);
      }
    } catch (error: any) {
      const status = error?.response?.status;
      if (status === 404 || status === 410) {
        hideNodeFromResults(node.id);
        toast.info('This item is no longer available and was removed from search results.');
      }
    }
  };

  const handleClosePreview = () => {
    setPreviewNode(null);
    setPreviewAnnotate(false);
  };

  const handleEditOnline = (node: Node, permission: 'read' | 'write') => {
    navigate(`/editor/${node.id}?provider=wopi&permission=${permission}`);
  };

  const handleOpenShareLink = (node: Node) => {
    dispatch(setSelectedNodeId(node.id));
    dispatch(setShareLinkManagerOpen(true));
  };

  const handleDownload = async (node: Node) => {
    if (isDocumentNode(node)) {
      try {
        await nodeService.downloadDocument(node.id);
      } catch (error) {
        toast.error('Failed to download file');
      }
    }
  };

  const handleOpenCheckInTarget = async (node: Node) => {
    try {
      const graph = await nodeService.getNodeRelationCheckoutGraph(node.id);
      const destinationId = graph.destinationNode?.id;
      if (!destinationId) {
        toast.info('No check-in target available for this document.');
        return;
      }
      navigate(`/browse/${destinationId}`);
    } catch {
      toast.error('Failed to open check-in target');
    }
  };

  const handleOpenCheckoutGraph = (node: Node) => {
    setCheckoutGraphDialogNode(node);
  };

  const handleOpenRenditionRegistry = (node: Node) => {
    setRenditionDialogNode(node);
  };

  const handleCheckoutNode = async (node: Node) => {
    setCheckoutActionNodeId(node.id);
    try {
      await nodeService.checkoutDocument(node.id);
      toast.success('Document checked out');
      if (lastSearchCriteria) {
        await runSearch(lastSearchCriteria);
      }
    } catch {
      toast.error('Failed to check out document');
    } finally {
      setCheckoutActionNodeId(null);
    }
  };

  const handleCancelCheckoutNode = async (node: Node) => {
    setCancelCheckoutActionNodeId(node.id);
    try {
      await nodeService.cancelCheckoutDocument(node.id);
      toast.success('Checkout cancelled');
      if (lastSearchCriteria) {
        await runSearch(lastSearchCriteria);
      }
    } catch {
      toast.error('Failed to cancel checkout');
    } finally {
      setCancelCheckoutActionNodeId(null);
    }
  };

  const openCheckInDialog = (node: Node) => {
    setCheckInDialogNode(node);
    setCheckInFile(null);
    setCheckInComment('');
    setCheckInMajorVersion(false);
    setCheckInKeepCheckedOut(false);
  };

  const closeCheckInDialog = () => {
    if (checkInSubmittingNodeId) {
      return;
    }
    setCheckInDialogNode(null);
    setCheckInFile(null);
    setCheckInComment('');
    setCheckInMajorVersion(false);
    setCheckInKeepCheckedOut(false);
  };

  const handleCheckInNode = async () => {
    if (!checkInDialogNode) {
      return;
    }
    if (checkInKeepCheckedOut && !checkInFile) {
      toast.error('Keep checked out requires a new version file');
      return;
    }
    setCheckInSubmittingNodeId(checkInDialogNode.id);
    try {
      await nodeService.checkinDocument(checkInDialogNode.id, {
        file: checkInFile,
        comment: checkInComment,
        majorVersion: checkInMajorVersion,
        keepCheckedOut: checkInKeepCheckedOut,
      });
      toast.success(checkInFile ? 'Document checked in with new version' : 'Document checked in');
      closeCheckInDialog();
      if (lastSearchCriteria) {
        await runSearch(lastSearchCriteria);
      }
    } catch {
      toast.error('Failed to check in document');
    } finally {
      setCheckInSubmittingNodeId(null);
    }
  };

  const handleFindSimilar = async (node: Node) => {
    if (!isDocumentNode(node)) {
      return;
    }
    setSimilarLoadingId(node.id);
    setSimilarError(null);
    try {
      const results = await nodeService.findSimilar(node.id, pageSize);
      setSimilarResults(results);
      setSimilarSource({ id: node.id, name: node.name });
      setPage(1);
      if (!results || results.length === 0) {
        toast.info('No similar documents found');
      }
    } catch (error: any) {
      const message = error?.response?.data?.message || 'Failed to load similar documents';
      setSimilarError(message);
      toast.error(message);
    } finally {
      setSimilarLoadingId(null);
    }
  };

  const formatFileSize = (bytes?: number): string => {
    if (!bytes) return '-';
    const sizes = ['B', 'KB', 'MB', 'GB'];
    const i = Math.floor(Math.log(bytes) / Math.log(1024));
    return `${(bytes / Math.pow(1024, i)).toFixed(2)} ${sizes[i]}`;
  };

  const resolveMatchFields = (node: Node) => {
    if (node.matchFields && node.matchFields.length > 0) {
      return node.matchFields;
    }
    return Object.entries(node.highlights || {})
      .filter(([, values]) => Array.isArray(values) && values.length > 0)
      .map(([key]) => key);
  };

  const formatMatchField = (field: string) => {
    const known = MATCH_FIELD_LABELS[field];
    if (known) {
      return known;
    }
    return field.replace(/([a-z])([A-Z])/g, '$1 $2').replace(/_/g, ' ');
  };

  const resolveHighlightDetails = (node: Node) => {
    const highlightMap = node.highlights || {};
    const matched = resolveMatchFields(node);
    const orderedFields = [
      ...matched,
      ...Object.keys(highlightMap).filter((field) => !matched.includes(field)),
    ];
    return orderedFields
      .map((field) => {
        const values = highlightMap[field];
        if (!Array.isArray(values) || values.length === 0 || !values[0]) {
          return null;
        }
        return {
          field,
          label: formatMatchField(field),
          snippet: values[0],
        };
      })
      .filter((item): item is { field: string; label: string; snippet: string } => Boolean(item))
      .slice(0, 3);
  };

  const applySuggestedFilter = (filter: { field: string; value: string }) => {
    if (!lastSearchCriteria) {
      return;
    }
    if (isSimilarMode) {
      clearSimilarResults();
    }
    const sortParams = getSortParams(sortBy);
    const baseCriteria: SearchCriteria = {
      ...lastSearchCriteria,
      page: 0,
      size: pageSize,
      ...sortParams,
    };

    if (filter.field === 'mimeType') {
      const nextMimeTypes = Array.from(
        new Set([...(baseCriteria.mimeTypes || []), filter.value])
      );
      runSearch({
        ...baseCriteria,
        mimeTypes: nextMimeTypes,
        contentType: undefined,
      });
      return;
    }

    if (filter.field === 'createdBy') {
      const nextCreators = Array.from(
        new Set([...(baseCriteria.createdByList || []), filter.value])
      );
      runSearch({
        ...baseCriteria,
        createdByList: nextCreators,
        createdBy: undefined,
      });
      return;
    }

    if (filter.field === 'dateRange') {
      const now = new Date();
      let fromDate: Date | null = null;
      if (filter.value === '7d') {
        fromDate = new Date(now.getTime() - 7 * 24 * 60 * 60 * 1000);
      } else if (filter.value === '30d') {
        fromDate = new Date(now.getTime() - 30 * 24 * 60 * 60 * 1000);
      } else if (filter.value === '1y') {
        fromDate = new Date(now.getTime() - 365 * 24 * 60 * 60 * 1000);
      }
      runSearch({
        ...baseCriteria,
        createdFrom: fromDate ? fromDate.toISOString() : baseCriteria.createdFrom,
      });
    }
  };

  const getVisualLength = (value?: string) => {
    if (!value) return 0;
    let total = 0;
    for (const char of value) {
      total += char.charCodeAt(0) > 0xff ? 2 : 1;
    }
    return total;
  };

  const getFileIcon = (node: Node) => {
    if (isFolderNode(node)) {
      return <Folder sx={{ fontSize: 48, color: 'primary.main' }} />;
    }
    return <InsertDriveFile sx={{ fontSize: 48, color: 'text.secondary' }} />;
  };

  const getNameTypographySx = (name: string) => {
    const length = getVisualLength(name);
    const isLong = length > 32;
    const isExtraLong = length > 56;
    const isVeryLong = length > 80;
    const lineClamp = isLong ? 3 : 2;
    const fontSize = lineClamp === 3
      ? (isVeryLong ? '0.8rem' : isExtraLong ? '0.86rem' : '0.9rem')
      : undefined;
    const lineHeight = lineClamp === 3 ? (isVeryLong ? 1.05 : isExtraLong ? 1.1 : 1.16) : 1.3;

    return {
      display: '-webkit-box',
      WebkitLineClamp: lineClamp,
      WebkitBoxOrient: 'vertical',
      overflow: 'hidden',
      wordBreak: 'break-word',
      overflowWrap: 'anywhere',
      lineHeight,
      ...(fontSize ? { fontSize } : {}),
    };
  };

  const getFileTypeChip = (contentType?: string) => {
    if (!contentType) return null;

    const typeMap: Record<string, { label: string; color: any }> = {
      'application/pdf': { label: 'PDF', color: 'error' },
      'application/msword': { label: 'Word', color: 'primary' },
      'application/vnd.ms-excel': { label: 'Excel', color: 'success' },
      'image/jpeg': { label: 'Image', color: 'warning' },
      'image/png': { label: 'Image', color: 'warning' },
      'text/plain': { label: 'Text', color: 'default' },
    };

    const type = typeMap[contentType];
    if (type) {
      return <Chip label={type.label} size="small" color={type.color} />;
    }

    return <Chip label="File" size="small" />;
  };

  const getPreviewStatusChip = (node: Node) => {
    if (node.nodeType === 'FOLDER') {
      return null;
    }
    const nodeMimeType = node.contentType || node.properties?.mimeType || node.properties?.contentType;
    const queueStatus = previewQueueStatusById[node.id];
    const previewStatusOverride = queueStatus?.previewStatus ?? node.previewStatus;
    const previewFailureCategoryOverride = queueStatus?.previewFailureCategory ?? node.previewFailureCategory;
    const previewFailureReasonOverride = queueStatus?.previewFailureReason ?? node.previewFailureReason;
    const effectiveStatus = getEffectivePreviewStatus(
      previewStatusOverride,
      previewFailureCategoryOverride,
      nodeMimeType,
      previewFailureReasonOverride
    );
    const failedPreviewMeta = getFailedPreviewMeta(
      nodeMimeType,
      previewFailureCategoryOverride,
      previewFailureReasonOverride
    );
    const failureReason = previewFailureReasonOverride || '';
    const label = effectiveStatus === 'READY'
      ? 'Preview ready'
      : effectiveStatus === 'FAILED' || effectiveStatus === 'UNSUPPORTED'
        ? failedPreviewMeta.label
        : effectiveStatus === 'PROCESSING'
          ? 'Preview processing'
          : effectiveStatus === 'QUEUED'
            ? 'Preview queued'
            : 'Preview pending';
    const color = effectiveStatus === 'READY'
      ? 'success'
      : effectiveStatus === 'FAILED' || effectiveStatus === 'UNSUPPORTED'
        ? failedPreviewMeta.color
        : effectiveStatus === 'PROCESSING'
          ? 'warning'
          : effectiveStatus === 'QUEUED'
            ? 'info'
            : 'default';
    const failedPreviewUnsupported = (effectiveStatus === 'FAILED' || effectiveStatus === 'UNSUPPORTED') && failedPreviewMeta.unsupported;
    const failedPreviewRetryable = effectiveStatus === 'FAILED' && isRetryablePreviewFailure(
      previewFailureCategoryOverride,
      nodeMimeType,
      previewFailureReasonOverride
    );
    const queueDetail = (() => {
      if (!queueStatus) {
        return null;
      }
      const queueStateLabel = queueStatus.queueState
        ? `Queue state: ${queueStatus.queueState}`
        : null;
      const attemptsLabel = queueStatus.attempts !== undefined
        ? `Attempts: ${queueStatus.attempts}`
        : null;
      const nextLabel = queueStatus.nextAttemptAt
        ? `Next retry: ${format(new Date(queueStatus.nextAttemptAt), 'PPp')}`
        : null;
      const updatedLabel = queueStatus.previewLastUpdated
        ? `Preview updated: ${format(new Date(queueStatus.previewLastUpdated), 'PPp')}`
        : null;
      if (!queueStateLabel && !attemptsLabel && !nextLabel && !updatedLabel) {
        return null;
      }
      return [queueStateLabel, attemptsLabel, nextLabel, updatedLabel].filter(Boolean).join(' • ');
    })();
    const tooltipText = [failureReason, queueStatus?.message || '', queueDetail].filter(Boolean).join(' • ');
    return (
      <Box display="flex" flexDirection="column" alignItems="flex-start" gap={0.5}>
        <Box display="flex" alignItems="center" gap={0.5}>
          <Tooltip
            title={tooltipText}
            placement="top-start"
            arrow
            disableHoverListener={!tooltipText}
          >
            <Chip label={label} size="small" variant="outlined" color={color} />
          </Tooltip>
          {effectiveStatus === 'FAILED' && failureReason && !failedPreviewUnsupported && (
            <Tooltip title={failureReason} placement="top-start" arrow>
              <IconButton size="small" aria-label="Preview failure reason">
                <InfoOutlined fontSize="small" />
              </IconButton>
            </Tooltip>
          )}
          {effectiveStatus === 'FAILED' && failedPreviewRetryable && (
            <Tooltip title="Retry preview" placement="top-start" arrow>
              <span>
                <IconButton
                  size="small"
                  aria-label="Retry preview"
                  onClick={(event) => {
                    event.stopPropagation();
                    handleRetryPreview(node);
                  }}
                  disabled={queueingPreviewId === node.id}
                >
                  <Refresh fontSize="small" />
                </IconButton>
              </span>
            </Tooltip>
          )}
          {effectiveStatus === 'FAILED' && failedPreviewRetryable && (
            <Tooltip title="Force rebuild preview" placement="top-start" arrow>
              <span>
                <IconButton
                  size="small"
                  aria-label="Force rebuild preview"
                  onClick={(event) => {
                    event.stopPropagation();
                    handleRetryPreview(node, true);
                  }}
                  disabled={queueingPreviewId === node.id}
                >
                  <Autorenew fontSize="small" />
                </IconButton>
              </span>
            </Tooltip>
          )}
        </Box>
        {queueDetail && (
          <Typography variant="caption" color="text.secondary">
            {queueDetail}
          </Typography>
        )}
      </Box>
    );
  };

  const formatScore = (score?: number) => {
    if (score === undefined || score === null) {
      return null;
    }
    const rounded = Math.round(score * 100) / 100;
    return `Score ${rounded}`;
  };

  const renderTagsCategories = (node: Node) => {
    return (
      <Box display="flex" gap={1} flexWrap="wrap" mt={1}>
        {node.correspondent && (
          <Chip key={`corr-${node.id}`} label={node.correspondent} size="small" color="secondary" variant="outlined" />
        )}
        {node.tags?.map((tag) => (
          <Chip key={tag} label={tag} size="small" variant="outlined" />
        ))}
        {node.categories?.map((cat) => (
          <Chip key={cat} label={cat} size="small" color="info" variant="outlined" />
        ))}
      </Box>
    );
  };

 
  const hasActiveCriteria = isSimilarMode
    || Boolean((lastSearchCriteria?.name || '').trim())
    || filtersApplied;
  const fallbackCriteriaMatches = Boolean(currentFallbackCriteriaKey) && currentFallbackCriteriaKey === fallbackCriteriaKey;
  const fallbackHiddenForCriteria = Boolean(currentFallbackCriteriaKey)
    && currentFallbackCriteriaKey === dismissedFallbackCriteriaKey;
  const fallbackForcedForCriteria = Boolean(currentFallbackCriteriaKey)
    && currentFallbackCriteriaKey === forcedFallbackCriteriaKey;
  const fallbackSuppressionQueryLabel = (lastSearchCriteria?.name || '').trim() || fallbackLabel;
  const shouldEvaluateFallback = !isSimilarMode
    && !loading
    && nodes.length === 0
    && fallbackNodes.length > 0
    && hasActiveCriteria
    && fallbackCriteriaMatches
    && !fallbackHiddenForCriteria;
  const shouldSuppressFallbackForQuery = shouldEvaluateFallback
    && !fallbackForcedForCriteria
    && shouldSuppressStaleFallbackForQuery(fallbackSuppressionQueryLabel);
  const shouldShowFallback = shouldEvaluateFallback && !shouldSuppressFallbackForQuery;
  const shouldShowSuppressedFallbackNotice = shouldEvaluateFallback && shouldSuppressFallbackForQuery;
  const shouldRunFallbackAutoRetry = shouldShowFallback || shouldShowSuppressedFallbackNotice;
  useEffect(() => {
    if (loading) {
      return;
    }
    if (!shouldRunFallbackAutoRetry && fallbackAutoRetryCount !== 0) {
      setFallbackAutoRetryCount(0);
    }
    if (!shouldRunFallbackAutoRetry && fallbackLastRetryAt) {
      setFallbackLastRetryAt(null);
    }
  }, [loading, shouldRunFallbackAutoRetry, fallbackAutoRetryCount, fallbackLastRetryAt]);
  const displayNodes = useMemo(
    () => (isSimilarMode ? (similarResults || []) : (shouldShowFallback ? fallbackNodes : nodes)),
    [isSimilarMode, similarResults, shouldShowFallback, fallbackNodes, nodes]
  );

  useEffect(() => {
    if (fallbackAutoRetryTimerRef.current !== null) {
      window.clearTimeout(fallbackAutoRetryTimerRef.current);
      fallbackAutoRetryTimerRef.current = null;
    }
    if (!shouldRunFallbackAutoRetry || !lastSearchCriteria) {
      return;
    }
    if (fallbackAutoRetryCount >= FALLBACK_AUTO_RETRY_MAX) {
      return;
    }
    const nextDelayMs = getFallbackAutoRetryDelayMs(fallbackAutoRetryCount);

    fallbackAutoRetryTimerRef.current = window.setTimeout(() => {
      setFallbackAutoRetryCount((prev) => prev + 1);
      handleRetrySearch();
    }, nextDelayMs);

    return () => {
      if (fallbackAutoRetryTimerRef.current !== null) {
        window.clearTimeout(fallbackAutoRetryTimerRef.current);
        fallbackAutoRetryTimerRef.current = null;
      }
    };
  }, [shouldRunFallbackAutoRetry, lastSearchCriteria, fallbackAutoRetryCount, handleRetrySearch]);

  const fallbackAutoRetryNextDelayMs = shouldRunFallbackAutoRetry && fallbackAutoRetryCount < FALLBACK_AUTO_RETRY_MAX
    ? getFallbackAutoRetryDelayMs(fallbackAutoRetryCount)
    : null;

  const previewAwareDisplayNodes = useMemo(
    () => displayNodes.map((node) => {
      if (node.nodeType === 'FOLDER') {
        return node;
      }
      const queueStatus = previewQueueStatusById[node.id];
      if (!queueStatus) {
        return node;
      }
      return {
        ...node,
        previewStatus: queueStatus.previewStatus ?? node.previewStatus,
        previewFailureReason: queueStatus.previewFailureReason ?? node.previewFailureReason,
        previewFailureCategory: queueStatus.previewFailureCategory ?? node.previewFailureCategory,
      };
    }),
    [displayNodes, previewQueueStatusById]
  );

  const hasPreviewQueueOverridesInDisplay = useMemo(
    () => previewAwareDisplayNodes.some((node) => node.nodeType !== 'FOLDER' && Boolean(previewQueueStatusById[node.id])),
    [previewAwareDisplayNodes, previewQueueStatusById]
  );

  const failedPreviewSummary = useMemo(
    () => summarizeFailedPreviews(
      previewAwareDisplayNodes
        .filter((node) => node.nodeType === 'DOCUMENT')
        .map((node) => ({
          previewStatus: node.previewStatus,
          previewFailureCategory: node.previewFailureCategory,
          previewFailureReason: node.previewFailureReason,
          mimeType: node.contentType || node.properties?.mimeType || node.properties?.contentType,
        }))
    ),
    [previewAwareDisplayNodes]
  );
  const failedPreviewNodes = previewAwareDisplayNodes.filter((node) => {
    if (node.nodeType !== 'DOCUMENT') {
      return false;
    }
    const nodeMimeType = node.contentType || node.properties?.mimeType || node.properties?.contentType;
    return getEffectivePreviewStatus(
      node.previewStatus,
      node.previewFailureCategory,
      nodeMimeType,
      node.previewFailureReason
    ) === 'FAILED'
      && isRetryablePreviewFailure(
        node.previewFailureCategory,
        nodeMimeType,
        node.previewFailureReason
      );
  });
  const failedPreviewReasonSummary = useMemo(() => {
    return failedPreviewSummary.retryableReasons.slice(0, 6);
  }, [failedPreviewSummary]);
  const previewStatusCounts = useMemo(() => {
    const base = {
      READY: 0,
      PROCESSING: 0,
      QUEUED: 0,
      FAILED: 0,
      UNSUPPORTED: 0,
      PENDING: 0,
      other: 0,
      folders: 0,
    };

    const previewStatusFacets = searchFacets?.previewStatus;
    const canUseSearchFacetsForPreview =
      !hasPreviewQueueOverridesInDisplay
      && !nonPreviewFiltersApplied
      && !previewStatusFilterApplied
      && Array.isArray(previewStatusFacets)
      && previewStatusFacets.length > 0;

    if (canUseSearchFacetsForPreview) {
      for (const facet of previewStatusFacets) {
        const key = (facet.value || '').toUpperCase();
        if (key in base) {
          base[key as keyof typeof base] = facet.count;
        }
      }
      return base;
    }

    return previewAwareDisplayNodes.reduce((acc, node) => {
      if (node.nodeType === 'FOLDER') {
        acc.folders += 1;
        return acc;
      }
      const nodeMimeType = node.contentType || node.properties?.mimeType || node.properties?.contentType;
      const status = getEffectivePreviewStatus(
        node.previewStatus,
        node.previewFailureCategory,
        nodeMimeType,
        node.previewFailureReason
      ) || 'PENDING';
      if (status in acc) {
        acc[status as keyof typeof acc] += 1;
      } else {
        acc.other += 1;
      }
      return acc;
    }, base);
  }, [hasPreviewQueueOverridesInDisplay, nonPreviewFiltersApplied, previewAwareDisplayNodes, previewStatusFilterApplied, searchFacets]);

  const applyPreviewQueueStatusOverride = useCallback((nodeId: string, status?: PreviewQueueStatus | null) => {
    setPreviewQueueStatusById((prev) => ({
      ...prev,
      [nodeId]: buildPreviewQueueOverride(status),
    }));
  }, []);

  const handleRetryPreview = useCallback(async (node: Node, force = false) => {
    if (!node?.id || node.nodeType !== 'DOCUMENT') {
      return;
    }
    setQueueingPreviewId(node.id);
    try {
      const status = await nodeService.queuePreview(node.id, force);
      applyPreviewQueueStatusOverride(node.id, status);
      if (status?.queued) {
        toast.success(status?.message || 'Preview queued');
      } else {
        toast.info(status?.message || 'Preview already up to date');
      }
    } catch {
      toast.error('Failed to queue preview');
    } finally {
      setQueueingPreviewId(null);
    }
  }, [applyPreviewQueueStatusOverride]);

  const handleRetryFailedPreviews = async (force = false) => {
    const targets = failedPreviewNodes;
    if (targets.length === 0) {
      return;
    }
    setBatchRetrying(true);
    let queued = 0;
    let skipped = 0;
    let failed = 0;
    for (const node of targets) {
      try {
        const status = await nodeService.queuePreview(node.id, force);
        applyPreviewQueueStatusOverride(node.id, status);
        if (status?.queued) {
          queued += 1;
        } else {
          skipped += 1;
        }
      } catch {
        failed += 1;
      }
    }
    const parts = [`queued ${queued}`];
    if (skipped > 0) parts.push(`skipped ${skipped}`);
    if (failed > 0) parts.push(`failed ${failed}`);
    toast.success(`Preview ${force ? 'rebuilds' : 'retries'}: ${parts.join(', ')}`);
    setBatchRetrying(false);
  };

  const handleRetryFailedReason = async (reason: string, force = false) => {
    const targets = failedPreviewNodes.filter((node) => {
      const nodeReason = normalizePreviewFailureReason(node.previewFailureReason);
      return nodeReason === reason;
    });
    if (targets.length === 0) {
      return;
    }
    setBatchRetrying(true);
    let queued = 0;
    let skipped = 0;
    let failed = 0;
    for (const node of targets) {
      try {
        const status = await nodeService.queuePreview(node.id, force);
        applyPreviewQueueStatusOverride(node.id, status);
        if (status?.queued) {
          queued += 1;
        } else {
          skipped += 1;
        }
      } catch {
        failed += 1;
      }
    }
    const reasonLabel = formatPreviewFailureReasonLabel(reason);
    const parts = [`queued ${queued}`];
    if (skipped > 0) parts.push(`skipped ${skipped}`);
    if (failed > 0) parts.push(`failed ${failed}`);
    toast.success(`Preview ${force ? 'rebuilds' : 'retries'} (${reasonLabel}): ${parts.join(', ')}`);
    setBatchRetrying(false);
  };
  const paginatedNodes = displayNodes.filter((node) => !hiddenNodeIds.includes(node.id));
  const queuedPreviewDetails = useMemo(() => {
    return paginatedNodes
      .map((node) => {
        const queueStatus = previewQueueStatusById[node.id];
        if (!queueStatus) {
          return null;
        }
        return {
          id: node.id,
          name: node.name,
          status: getEffectivePreviewStatus(
            queueStatus.previewStatus ?? node.previewStatus,
            queueStatus.previewFailureCategory ?? node.previewFailureCategory,
            node.contentType || node.properties?.mimeType || node.properties?.contentType,
            queueStatus.previewFailureReason ?? node.previewFailureReason
          ),
          attempts: queueStatus.attempts,
          nextAttemptAt: queueStatus.nextAttemptAt,
        };
      })
      .filter((item): item is NonNullable<typeof item> => Boolean(item))
      .sort((a, b) => {
        const left = a.nextAttemptAt ? new Date(a.nextAttemptAt).getTime() : 0;
        const right = b.nextAttemptAt ? new Date(b.nextAttemptAt).getTime() : 0;
        return left - right;
      })
      .slice(0, 5);
  }, [paginatedNodes, previewQueueStatusById]);
  const displayTotal = isSimilarMode
    ? paginatedNodes.length
    : nodesTotal;
  const totalPages = Math.max(1, Math.ceil(displayTotal / pageSize));
  const spellcheckQuery = (lastSearchCriteria?.name || '').trim();
  const normalizedSpellcheckQuery = spellcheckQuery.toLowerCase();
  const shouldUseSearchFacets = !nonPreviewFiltersApplied && !previewStatusFilterApplied && searchFacets && Object.keys(searchFacets).length > 0;
  const facets = useMemo(() => {
    if (shouldUseSearchFacets) {
      const toSorted = (values?: FacetValue[]) =>
        (values || []).slice().sort((a, b) => b.count - a.count || a.value.localeCompare(b.value));

      return {
        mimeTypes: toSorted(searchFacets.mimeType),
        creators: toSorted(searchFacets.createdBy),
        correspondents: toSorted(searchFacets.correspondent),
        tags: toSorted(searchFacets.tags),
        categories: toSorted(searchFacets.categories),
        recordCategoryPaths: toSorted(searchFacets.recordCategoryPath),
      };
    }

    const mimeTypeCounts = new Map<string, number>();
    const creatorCounts = new Map<string, number>();
    const correspondentCounts = new Map<string, number>();
    const tagCounts = new Map<string, number>();
    const categoryCounts = new Map<string, number>();
    const recordCategoryPathCounts = new Map<string, number>();

    const inc = (map: Map<string, number>, key: string) => {
      map.set(key, (map.get(key) || 0) + 1);
    };

    for (const node of displayNodes) {
      if (node.contentType) {
        inc(mimeTypeCounts, node.contentType);
      }
      if (node.creator) {
        inc(creatorCounts, node.creator);
      }
      if (node.correspondent) {
        inc(correspondentCounts, node.correspondent);
      }
      for (const tag of node.tags || []) {
        if (tag) {
          inc(tagCounts, tag);
        }
      }
      for (const category of node.categories || []) {
        if (category) {
          inc(categoryCounts, category);
        }
      }
      if (node.recordCategoryPath) {
        inc(recordCategoryPathCounts, node.recordCategoryPath);
      }
    }

    const mapToSortedArray = (map: Map<string, number>): FacetValue[] => {
      return Array.from(map.entries())
        .map(([value, count]) => ({ value, count }))
        .sort((a, b) => b.count - a.count || a.value.localeCompare(b.value));
    };

    return {
      mimeTypes: mapToSortedArray(mimeTypeCounts),
      creators: mapToSortedArray(creatorCounts),
      correspondents: mapToSortedArray(correspondentCounts),
      tags: mapToSortedArray(tagCounts),
      categories: mapToSortedArray(categoryCounts),
      recordCategoryPaths: mapToSortedArray(recordCategoryPathCounts),
    };
  }, [shouldUseSearchFacets, searchFacets, displayNodes]);
  const facetScopeLabel = shouldUseSearchFacets
    ? 'Facet counts reflect the full result set for this query.'
    : 'Facet counts reflect the current page after filters are applied.';
  const filteredSpellcheckSuggestions = useMemo(() => {
    const unique = new Set<string>();
    for (const suggestion of spellcheckSuggestions) {
      const trimmed = suggestion.trim();
      if (!trimmed) continue;
      if (trimmed.toLowerCase() === normalizedSpellcheckQuery) continue;
      if (!unique.has(trimmed)) {
        unique.add(trimmed);
      }
    }
    return Array.from(unique);
  }, [spellcheckSuggestions, normalizedSpellcheckQuery]);
  const primarySpellcheckSuggestion = filteredSpellcheckSuggestions[0];
  const secondarySpellcheckSuggestions = filteredSpellcheckSuggestions.slice(1);
  const showSpellcheckSuggestions = !spellcheckLoading && filteredSpellcheckSuggestions.length > 0;
  const spellcheckActionLabel = displayTotal > 0 ? 'Did you mean' : 'Search instead for';
  const exactMatchModeActive = !isSimilarMode && isPrecisionFilenameLikeQuery(spellcheckQuery);

  return (
    <Box>
      <Paper sx={{ p: 2, mb: 3 }}>
        <form onSubmit={handleQuickSearch}>
          <Box display="flex" alignItems="center" gap={2}>
            <Autocomplete
              freeSolo
              fullWidth
              options={quickSearchSuggestions}
              inputValue={quickSearch}
              onInputChange={(_, value, reason) => {
                if (reason === 'reset') {
                  return;
                }
                setQuickSearch(value);
              }}
              onChange={(_, value) => {
                if (typeof value === 'string') {
                  setQuickSearch(value);
                }
              }}
              filterOptions={(options) => options}
              loading={quickSearchSuggestionsLoading}
              renderInput={(params) => (
                <TextField
                  {...params}
                  placeholder="Quick search by name..."
                  name="quickSearch"
                  InputProps={{
                    ...params.InputProps,
                    startAdornment: (
                      <>
                        <InputAdornment position="start">
                          <Search />
                        </InputAdornment>
                        {params.InputProps.startAdornment}
                      </>
                    ),
                    endAdornment: (
                      <>
                        {quickSearchSuggestionsLoading ? (
                          <CircularProgress color="inherit" size={18} />
                        ) : null}
                        {quickSearch ? (
                          <InputAdornment position="end">
                            <IconButton onClick={handleClearSearch} edge="end" size="small">
                              <Clear />
                            </IconButton>
                          </InputAdornment>
                        ) : null}
                        {params.InputProps.endAdornment}
                      </>
                    ),
                  }}
                />
              )}
            />
            <Button
              variant="outlined"
              startIcon={<Star fontSize="small" color="warning" />}
              onClick={handleOpenPinnedSavedSearchMenu}
              aria-label="Pinned saved searches"
            >
              Saved
            </Button>
            <Button
              variant="outlined"
              startIcon={<FilterList />}
              onClick={handleAdvancedSearch}
            >
              Advanced
            </Button>
          </Box>
        </form>
        <Menu
          anchorEl={pinnedSavedSearchMenuAnchorEl}
          open={pinnedSavedSearchMenuOpen}
          onClose={handleClosePinnedSavedSearchMenu}
          keepMounted
        >
          <MenuItem disabled>
            <ListItemText
              primary="Pinned Saved Searches"
              secondary="Quickly run your pinned searches"
            />
          </MenuItem>
          <Divider />
          {pinnedSavedSearchesLoading ? (
            <MenuItem disabled>
              <ListItemIcon>
                <CircularProgress size={18} />
              </ListItemIcon>
              <ListItemText primary="Loading pinned searches…" />
            </MenuItem>
          ) : pinnedSavedSearchesError ? (
            <MenuItem disabled>
              <ListItemText primary={pinnedSavedSearchesError} />
            </MenuItem>
          ) : pinnedSavedSearches.length === 0 ? (
            <MenuItem disabled>
              <ListItemText primary="No pinned searches yet." secondary="Pin one in Saved Searches or Admin Dashboard." />
            </MenuItem>
          ) : (
            pinnedSavedSearches.map((item) => (
              <MenuItem
                key={item.id}
                onClick={() => handleRunPinnedSavedSearch(item)}
                aria-label={`Run saved search ${item.name}`}
              >
                <ListItemIcon>
                  <Star fontSize="small" color="warning" />
                </ListItemIcon>
                <ListItemText primary={item.name} />
                <Tooltip title="Unpin">
                  <IconButton
                    size="small"
                    aria-label={`Unpin saved search ${item.name}`}
                    onClick={(event) => {
                      event.stopPropagation();
                      handleUnpinSavedSearch(item);
                    }}
                  >
                    <StarBorder fontSize="small" />
                  </IconButton>
                </Tooltip>
              </MenuItem>
            ))
          )}
          <Divider />
          <MenuItem
            onClick={() => {
              handleClosePinnedSavedSearchMenu();
              navigate('/saved-searches');
            }}
          >
            <ListItemText primary="Manage saved searches" />
          </MenuItem>
          <MenuItem
            onClick={() => loadPinnedSavedSearches()}
            aria-label="Refresh pinned saved searches"
          >
            <ListItemIcon>
              <Refresh fontSize="small" />
            </ListItemIcon>
            <ListItemText primary="Refresh" />
          </MenuItem>
        </Menu>
      </Paper>

      {lastSearchCriteria?.folderId && (
        <Box display="flex" flexWrap="wrap" gap={1} sx={{ mb: 2 }}>
          <Chip
            label={folderScopeLabel}
            onDelete={handleClearFolderScope}
            size="small"
            variant="outlined"
          />
        </Box>
      )}

      {exactMatchModeActive && (
        <Alert severity="info" sx={{ mb: 2 }} data-testid="search-exact-match-mode-alert">
          <Typography variant="body2" sx={{ fontWeight: 600 }}>
            Exact filename mode active
          </Typography>
          <Typography variant="body2" color="text.secondary">
            Spelling suggestions are skipped for precise filename-like queries.
          </Typography>
        </Alert>
      )}

      {spellcheckLoading && (
        <Alert severity="info" sx={{ mb: 2 }}>
          Checking spelling suggestions…
        </Alert>
      )}
      {spellcheckError && (
        <Alert severity="warning" sx={{ mb: 2 }}>
          {spellcheckError}
        </Alert>
      )}
      {showSpellcheckSuggestions && (
        <Alert severity="info" sx={{ mb: 2 }}>
          <Box display="flex" alignItems="center" flexWrap="wrap" gap={0.5}>
            {spellcheckQuery && (
              <Typography variant="body2" color="text.secondary">
                {displayTotal > 0
                  ? `Showing results for "${spellcheckQuery}".`
                  : `No results for "${spellcheckQuery}".`}
              </Typography>
            )}
            <Typography variant="body2" sx={{ fontWeight: 600 }}>
              {spellcheckActionLabel}
            </Typography>
            {primarySpellcheckSuggestion && (
              <Button
                size="small"
                onClick={() => handleSpellcheckSuggestion(primarySpellcheckSuggestion)}
                sx={{ textTransform: 'none', ml: 0.5 }}
              >
                {primarySpellcheckSuggestion}
              </Button>
            )}
            {secondarySpellcheckSuggestions.length > 0 && (
              <>
                <Typography variant="body2" color="text.secondary">
                  or
                </Typography>
                {secondarySpellcheckSuggestions.map((suggestion) => (
                  <Button
                    key={suggestion}
                    size="small"
                    onClick={() => handleSpellcheckSuggestion(suggestion)}
                    sx={{ textTransform: 'none', ml: 0.5 }}
                  >
                    {suggestion}
                  </Button>
                ))}
              </>
            )}
          </Box>
        </Alert>
      )}

      <Grid container spacing={2}>
        <Grid item xs={12} md={3}>
          <Paper sx={{ p: 2, height: '100%' }}>
            <Box display="flex" alignItems="center" justifyContent="space-between" mb={1}>
              <Typography variant="h6">Facets</Typography>
              <Button size="small" disabled={!filtersApplied} onClick={clearFacetFilters}>
                Clear
              </Button>
            </Box>
            <Divider sx={{ mb: 2 }} />
            <Typography variant="caption" color="text.secondary" display="block" sx={{ mb: 2 }}>
              {facetScopeLabel}
            </Typography>

            <Typography variant="subtitle2" gutterBottom>
              File Type
            </Typography>
            <FormControl fullWidth size="small" sx={{ mb: 2 }}>
              <InputLabel>Mime Type</InputLabel>
              <Select
                multiple
                value={selectedMimeTypes}
                onChange={(e) => {
                  const value = e.target.value;
                  setSelectedMimeTypes(typeof value === 'string' ? value.split(',') : (value as string[]));
                }}
                label="Mime Type"
                renderValue={(selected) => (
                  <Box sx={{ display: 'flex', flexWrap: 'wrap', gap: 0.5 }}>
                    {(selected as string[]).map((value) => (
                      <Chip key={value} label={value.split('/')[1] || value} size="small" />
                    ))}
                  </Box>
                )}
              >
                {facets.mimeTypes.map((facet) => (
                  <MenuItem key={facet.value} value={facet.value}>
                    <Checkbox checked={selectedMimeTypes.indexOf(facet.value) > -1} />
                    <ListItemText primary={facet.value} secondary={`(${facet.count})`} />
                  </MenuItem>
                ))}
              </Select>
            </FormControl>

            <Typography variant="subtitle2" gutterBottom>
              Preview Status
            </Typography>
            <Typography variant="caption" color="text.secondary" display="block" sx={{ mb: 1 }}>
              Filter results by preview generation state.
            </Typography>
            <Box display="flex" flexWrap="wrap" gap={1} mb={2}>
              {[
                { value: 'READY', label: 'Ready', color: 'success' as const, count: previewStatusCounts.READY },
                { value: 'PROCESSING', label: 'Processing', color: 'warning' as const, count: previewStatusCounts.PROCESSING },
                { value: 'QUEUED', label: 'Queued', color: 'info' as const, count: previewStatusCounts.QUEUED },
                { value: 'FAILED', label: 'Failed', color: 'error' as const, count: previewStatusCounts.FAILED },
                { value: 'UNSUPPORTED', label: 'Unsupported', color: 'default' as const, count: previewStatusCounts.UNSUPPORTED },
                { value: 'PENDING', label: 'Pending', color: 'default' as const, count: previewStatusCounts.PENDING },
              ].map((status) => (
                <Chip
                  key={status.value}
                  label={`${status.label} (${status.count})`}
                  size="small"
                  color={selectedPreviewStatuses.includes(status.value) ? status.color : 'default'}
                  variant={selectedPreviewStatuses.includes(status.value) ? 'filled' : 'outlined'}
                  onClick={() => togglePreviewStatus(status.value)}
                />
              ))}
              <Button size="small" disabled={selectedPreviewStatuses.length === 0} onClick={() => setSelectedPreviewStatuses([])}>
                Clear
              </Button>
            </Box>

            <Typography variant="subtitle2" gutterBottom>
              Lock
            </Typography>
            <Typography variant="caption" color="text.secondary" display="block" sx={{ mb: 1 }}>
              Narrow ordinary search to locked or unlocked documents, and optionally by lock owner.
            </Typography>
            <FormControl fullWidth size="small" sx={{ mb: 1.5 }}>
              <InputLabel id="facet-lock-state-label">Lock state</InputLabel>
              <Select
                id="facet-lock-state-select"
                labelId="facet-lock-state-label"
                value={selectedLockState}
                label="Lock state"
                onChange={(e) => setSelectedLockState(e.target.value as LockFacetState)}
              >
                <MenuItem value="all">All documents</MenuItem>
                <MenuItem value="locked">Locked</MenuItem>
                <MenuItem value="unlocked">Unlocked</MenuItem>
              </Select>
            </FormControl>
            <TextField
              fullWidth
              size="small"
              label="Lock owner"
              value={selectedLockOwner}
              onChange={(event) => setSelectedLockOwner(event.target.value)}
              placeholder="alice"
              sx={{ mb: 2 }}
            />

            <Typography variant="subtitle2" gutterBottom>
              Checkout
            </Typography>
            <Typography variant="caption" color="text.secondary" display="block" sx={{ mb: 1 }}>
              Narrow ordinary search to checked-out or available documents, and optionally by checkout owner.
            </Typography>
            <FormControl fullWidth size="small" sx={{ mb: 1.5 }}>
              <InputLabel id="facet-checkout-state-label">Checkout state</InputLabel>
              <Select
                id="facet-checkout-state-select"
                labelId="facet-checkout-state-label"
                value={selectedCheckoutState}
                label="Checkout state"
                onChange={(e) => setSelectedCheckoutState(e.target.value as CheckoutFacetState)}
              >
                <MenuItem value="all">All documents</MenuItem>
                <MenuItem value="checkedOut">Checked out</MenuItem>
                <MenuItem value="available">Available</MenuItem>
              </Select>
            </FormControl>
            <TextField
              fullWidth
              size="small"
              label="Checkout user"
              value={selectedCheckoutUser}
              onChange={(event) => setSelectedCheckoutUser(event.target.value)}
              placeholder="alice"
              sx={{ mb: 2 }}
            />
            <FormControlLabel
              control={(
                <Checkbox
                  checked={selectedRecordOnly}
                  onChange={(event) => setSelectedRecordOnly(event.target.checked)}
                />
              )}
              label="Record declarations only"
              sx={{ mb: 2 }}
            />

            {facets.recordCategoryPaths.length > 0 && (
              <>
                <Typography variant="subtitle2" gutterBottom>
                  Record Category
                </Typography>
                <FormControl fullWidth size="small" sx={{ mb: 2 }}>
                  <Select
                    multiple
                    value={selectedRecordCategoryPaths}
                    onChange={(e) => {
                      const value = e.target.value;
                      setSelectedRecordCategoryPaths(typeof value === 'string' ? value.split(',') : (value as string[]));
                    }}
                    renderValue={(selected) => (
                      <Box sx={{ display: 'flex', flexWrap: 'wrap', gap: 0.5 }}>
                        {(selected as string[]).map((value) => (
                          <Chip key={value} label={value} size="small" />
                        ))}
                      </Box>
                    )}
                  >
                    {facets.recordCategoryPaths.map((facet) => (
                      <MenuItem key={facet.value} value={facet.value}>
                        <Checkbox checked={selectedRecordCategoryPaths.indexOf(facet.value) > -1} />
                        <ListItemText primary={facet.value} secondary={`(${facet.count})`} />
                      </MenuItem>
                    ))}
                  </Select>
                </FormControl>
              </>
            )}
            {failedPreviewSummary.retryableFailed > 0 && (
              <Box display="flex" alignItems="center" gap={1} mb={2}>
                <Button
                  size="small"
                  variant="outlined"
                  startIcon={<Refresh />}
                  onClick={() => {
                    void handleRetryFailedPreviews(false);
                  }}
                  disabled={batchRetrying}
                >
                  Retry failed previews
                </Button>
                <Button
                  size="small"
                  variant="outlined"
                  startIcon={<Refresh />}
                  onClick={() => handleRetryFailedPreviews(true)}
                  disabled={batchRetrying}
                >
                  Force rebuild failed
                </Button>
                {batchRetrying && (
                  <Typography variant="caption" color="text.secondary">
                    Queueing {failedPreviewSummary.retryableFailed} item(s)…
                  </Typography>
                )}
              </Box>
            )}
            {failedPreviewSummary.totalFailed > 0 && (
              <Typography variant="caption" color="text.secondary" display="block" sx={{ mb: 1 }}>
                Preview issues on current page: {failedPreviewSummary.totalFailed}
                {' • '}Retryable {failedPreviewSummary.retryableFailed}
                {' • '}Unsupported {failedPreviewSummary.unsupportedFailed}
                {failedPreviewSummary.permanentFailed > 0 && (
                  <>
                    {' • '}Permanent {failedPreviewSummary.permanentFailed}
                  </>
                )}
              </Typography>
            )}
            {failedPreviewSummary.totalFailed > 0 && failedPreviewSummary.retryableFailed === 0 && (
              <Typography variant="caption" color="text.secondary" display="block" sx={{ mb: 1 }}>
                {failedPreviewSummary.permanentFailed > 0
                  ? failedPreviewSummary.unsupportedFailed > 0
                    ? 'All preview issues on this page are permanent or unsupported; retry actions are hidden.'
                    : 'All preview issues on this page are permanent; retry actions are hidden.'
                  : 'All preview issues on this page are unsupported; retry actions are hidden.'}
              </Typography>
            )}
            {failedPreviewSummary.retryableFailed > 0 && failedPreviewReasonSummary.length > 0 && (
              <Box sx={{ mb: 2 }}>
                <Typography variant="caption" color="text.secondary" display="block">
                  Failed reasons (current page)
                </Typography>
                <Stack spacing={0.5} sx={{ mt: 0.5 }}>
                  {failedPreviewReasonSummary.map((item) => {
                    const displayReason = formatPreviewFailureReasonLabel(item.reason);
                    const reasonLabel = displayReason.length > 80
                      ? `${displayReason.slice(0, 77)}...`
                      : displayReason;
                    return (
                      <Box key={item.reason} display="flex" alignItems="center" gap={1} flexWrap="wrap">
                        <Chip size="small" color="error" variant="outlined" label={`${reasonLabel} (${item.count})`} />
                        <Button
                          size="small"
                          variant="text"
                          onClick={() => handleRetryFailedReason(item.reason)}
                          disabled={batchRetrying}
                        >
                          Retry this reason
                        </Button>
                        <Button
                          size="small"
                          variant="text"
                          onClick={() => handleRetryFailedReason(item.reason, true)}
                          disabled={batchRetrying}
                        >
                          Force rebuild
                        </Button>
                      </Box>
                    );
                  })}
                </Stack>
              </Box>
            )}
            {previewRetrySummary && (
              <Typography variant="caption" color="text.secondary" display="block" sx={{ mb: 2 }}>
                Preview queue: {previewRetrySummary.count} item(s) • Next retry{' '}
                {format(previewRetrySummary.nextAt, 'PPp')}
              </Typography>
            )}
            {queuedPreviewDetails.length > 0 && (
              <Box sx={{ mb: 2 }}>
                <Typography variant="caption" color="text.secondary" display="block">
                  Queued preview details (current page)
                </Typography>
                <Stack spacing={0.5} sx={{ mt: 0.5 }}>
                  {queuedPreviewDetails.map((item) => (
                    <Typography key={item.id} variant="caption" color="text.secondary">
                      {item.name} • {item.status.toLowerCase()}
                      {item.attempts !== undefined ? ` • attempts ${item.attempts}` : ''}
                      {item.nextAttemptAt ? ` • next ${format(new Date(item.nextAttemptAt), 'PPp')}` : ''}
                    </Typography>
                  ))}
                </Stack>
              </Box>
            )}

            <Typography variant="subtitle2" gutterBottom>
              Created By
            </Typography>
            <FormControl fullWidth size="small" sx={{ mb: 2 }}>
              <InputLabel>Creator</InputLabel>
              <Select
                multiple
                value={selectedCreators}
                onChange={(e) => {
                  const value = e.target.value;
                  setSelectedCreators(typeof value === 'string' ? value.split(',') : (value as string[]));
                }}
                label="Creator"
                renderValue={(selected) => (
                  <Box sx={{ display: 'flex', flexWrap: 'wrap', gap: 0.5 }}>
                    {(selected as string[]).map((value) => (
                      <Chip key={value} label={value} size="small" />
                    ))}
                  </Box>
                )}
              >
                {facets.creators.map((facet) => (
                  <MenuItem key={facet.value} value={facet.value}>
                    <Checkbox checked={selectedCreators.indexOf(facet.value) > -1} />
                    <ListItemText primary={facet.value} secondary={`(${facet.count})`} />
                  </MenuItem>
                ))}
              </Select>
            </FormControl>

            <Typography variant="subtitle2" gutterBottom>
              Correspondent
            </Typography>
            <FormControl fullWidth size="small" sx={{ mb: 2 }}>
              <InputLabel id="facet-correspondent-label">Correspondent</InputLabel>
              <Select
                id="facet-correspondent-select"
                labelId="facet-correspondent-label"
                multiple
                value={selectedCorrespondents}
                onChange={(e) => {
                  const value = e.target.value;
                  setSelectedCorrespondents(typeof value === 'string' ? value.split(',') : (value as string[]));
                }}
                label="Correspondent"
                renderValue={(selected) => (
                  <Box sx={{ display: 'flex', flexWrap: 'wrap', gap: 0.5 }}>
                    {(selected as string[]).map((value) => (
                      <Chip key={value} label={value} size="small" />
                    ))}
                  </Box>
                )}
              >
                {facets.correspondents.map((facet) => (
                  <MenuItem key={facet.value} value={facet.value}>
                    <Checkbox checked={selectedCorrespondents.indexOf(facet.value) > -1} />
                    <ListItemText primary={facet.value} secondary={`(${facet.count})`} />
                  </MenuItem>
                ))}
              </Select>
            </FormControl>

            <Typography variant="subtitle2" gutterBottom>
              Tags
            </Typography>
            <FormControl fullWidth size="small" sx={{ mb: 2 }}>
              <InputLabel>Tags</InputLabel>
              <Select
                multiple
                value={selectedTags}
                onChange={(e) => {
                  const value = e.target.value;
                  setSelectedTags(typeof value === 'string' ? value.split(',') : (value as string[]));
                }}
                label="Tags"
                renderValue={(selected) => (
                  <Box sx={{ display: 'flex', flexWrap: 'wrap', gap: 0.5 }}>
                    {(selected as string[]).map((value) => (
                      <Chip key={value} label={value} size="small" variant="outlined" />
                    ))}
                  </Box>
                )}
              >
                {facets.tags.map((facet) => (
                  <MenuItem key={facet.value} value={facet.value}>
                    <Checkbox checked={selectedTags.indexOf(facet.value) > -1} />
                    <ListItemText primary={facet.value} secondary={`(${facet.count})`} />
                  </MenuItem>
                ))}
              </Select>
            </FormControl>

            <Typography variant="subtitle2" gutterBottom>
              Categories
            </Typography>
            <FormControl fullWidth size="small">
              <InputLabel>Categories</InputLabel>
              <Select
                multiple
                value={selectedCategories}
                onChange={(e) => {
                  const value = e.target.value;
                  setSelectedCategories(typeof value === 'string' ? value.split(',') : (value as string[]));
                }}
                label="Categories"
                renderValue={(selected) => (
                  <Box sx={{ display: 'flex', flexWrap: 'wrap', gap: 0.5 }}>
                    {(selected as string[]).map((value) => (
                      <Chip key={value} label={value} size="small" color="info" variant="outlined" />
                    ))}
                  </Box>
                )}
              >
                {facets.categories.map((facet) => (
                  <MenuItem key={facet.value} value={facet.value}>
                    <Checkbox checked={selectedCategories.indexOf(facet.value) > -1} />
                    <ListItemText primary={facet.value} secondary={`(${facet.count})`} />
                  </MenuItem>
                ))}
              </Select>
            </FormControl>
          </Paper>
        </Grid>

        <Grid item xs={12} md={9}>
          {error && (
            <Alert
              severity="error"
              sx={{ mb: 2 }}
              action={
                <Stack direction="row" spacing={1}>
                  <Button
                    color="inherit"
                    size="small"
                    onClick={handleRetryPrimarySearch}
                    disabled={
                      !primarySearchErrorRecovery?.canRetry
                      || (!lastSearchCriteria && quickSearch.trim().length === 0)
                    }
                  >
                    Retry
                  </Button>
                  <Button color="inherit" size="small" onClick={handleGoHomeFromError}>
                    Back to folder
                  </Button>
                  <Button color="inherit" size="small" onClick={handleAdvancedSearch}>
                    Advanced
                  </Button>
                </Stack>
              }
            >
              <Typography variant="body2">{primarySearchErrorRecovery?.message || error}</Typography>
              {primarySearchErrorRecovery?.hint && (
                <Typography variant="caption" display="block" sx={{ mt: 0.5 }}>
                  {primarySearchErrorRecovery.hint}
                </Typography>
              )}
            </Alert>
          )}
          {similarError && (
            <Alert severity="error" sx={{ mb: 2 }}>
              {similarError}
            </Alert>
          )}
          {isSimilarMode && (
            <Alert
              severity="info"
              sx={{ mb: 2 }}
              action={(
                <Button color="inherit" size="small" onClick={clearSimilarResults}>
                  Back to results
                </Button>
              )}
            >
              {similarSource?.name
                ? `Showing documents similar to "${similarSource.name}".`
                : 'Showing similar documents.'}
            </Alert>
          )}
          {shouldShowFallback && (
            <Alert
              severity="info"
              sx={{ mb: 2 }}
              action={(
                <Stack direction="row" spacing={1}>
                  <Button color="inherit" size="small" onClick={handleRetrySearch}>
                    Retry
                  </Button>
                  <Button color="inherit" size="small" onClick={handleHideFallbackResults}>
                    Hide previous results
                  </Button>
                </Stack>
              )}
            >
              {fallbackLabel
                ? `Search results may still be indexing. Showing previous results for "${fallbackLabel}".`
                : 'Search results may still be indexing. Showing previous results.'}
              {' '}
              {fallbackAutoRetryCount < FALLBACK_AUTO_RETRY_MAX
                ? `Auto-retry ${fallbackAutoRetryCount}/${FALLBACK_AUTO_RETRY_MAX}${fallbackAutoRetryNextDelayMs ? ` (next in ${(fallbackAutoRetryNextDelayMs / 1000).toFixed(1)}s).` : '.'}`
                : `Auto-retry stopped after ${FALLBACK_AUTO_RETRY_MAX} attempts.`}
              {fallbackLastRetryAt ? ` Last retry: ${format(fallbackLastRetryAt, 'PPp')}.` : ''}
            </Alert>
          )}
          {shouldShowSuppressedFallbackNotice && (
            <Alert
              severity="info"
              sx={{ mb: 2 }}
              action={(
                <Stack direction="row" spacing={1}>
                  <Button color="inherit" size="small" onClick={handleRetrySearch}>
                    Retry
                  </Button>
                  <Button color="inherit" size="small" onClick={handleShowFallbackResults}>
                    Show previous results
                  </Button>
                </Stack>
              )}
            >
              {fallbackSuppressionQueryLabel
                ? `Search results may still be indexing for exact query "${fallbackSuppressionQueryLabel}". Previous results are hidden to avoid stale mismatch.`
                : 'Search results may still be indexing for an exact query. Previous results are hidden to avoid stale mismatch.'}
              {' '}
              {fallbackAutoRetryCount < FALLBACK_AUTO_RETRY_MAX
                ? `Auto-retry ${fallbackAutoRetryCount}/${FALLBACK_AUTO_RETRY_MAX}${fallbackAutoRetryNextDelayMs ? ` (next in ${(fallbackAutoRetryNextDelayMs / 1000).toFixed(1)}s).` : '.'}`
                : `Auto-retry stopped after ${FALLBACK_AUTO_RETRY_MAX} attempts.`}
              {fallbackLastRetryAt ? ` Last retry: ${format(fallbackLastRetryAt, 'PPp')}.` : ''}
            </Alert>
          )}
          {suggestedFiltersError && (
            <Alert severity="warning" sx={{ mb: 2 }}>
              {suggestedFiltersError}
            </Alert>
          )}
          {suggestedFiltersLoading && (
            <Alert severity="info" sx={{ mb: 2 }}>
              Loading suggested filters…
            </Alert>
          )}
          {!suggestedFiltersLoading && suggestedFilters.length > 0 && (
            <Paper sx={{ p: 1.5, mb: 2 }}>
              <Box display="flex" alignItems="center" gap={1} flexWrap="wrap">
                <Typography variant="subtitle2" color="text.secondary">
                  Suggested:
                </Typography>
                {suggestedFilters.map((filter, index) => (
                  <Chip
                    key={`${filter.field}-${filter.value}-${index}`}
                    label={filter.count !== undefined ? `${filter.label} (${filter.count})` : filter.label}
                    size="small"
                    onClick={() => applySuggestedFilter(filter)}
                    variant="outlined"
                  />
                ))}
              </Box>
            </Paper>
          )}
          {searchDiagnosticsError && (
            <Alert severity="warning" sx={{ mb: 2 }}>
              {searchDiagnosticsError}
            </Alert>
          )}
          {searchDiagnosticsLoading && !searchDiagnostics && (
            <Alert severity="info" sx={{ mb: 2 }}>
              Loading access scope…
            </Alert>
          )}
          {searchDiagnostics && (
            <Paper sx={{ p: 1.5, mb: 2 }}>
              <Box display="flex" alignItems="center" justifyContent="space-between" mb={1}>
                <Typography variant="subtitle2">Access scope</Typography>
                <Button
                  size="small"
                  startIcon={<Refresh fontSize="small" />}
                  onClick={handleRefreshDiagnostics}
                  disabled={searchDiagnosticsLoading || searchIndexStatsLoading}
                >
                  Refresh
                </Button>
              </Box>
              <Stack direction="row" spacing={1} flexWrap="wrap" sx={{ mb: 1 }}>
                <Chip
                  size="small"
                  color={searchDiagnostics.admin ? 'success' : 'warning'}
                  label={searchDiagnostics.admin ? 'Admin (ACL bypass)' : 'Restricted'}
                />
                <Chip
                  size="small"
                  variant="outlined"
                  label={searchDiagnostics.readFilterApplied ? 'ACL filter active' : 'ACL filter off'}
                />
                <Chip size="small" variant="outlined" label={`Authorities ${searchDiagnostics.authorityCount}`} />
                {searchDiagnostics.username && (
                  <Chip size="small" variant="outlined" label={`User ${searchDiagnostics.username}`} />
                )}
              </Stack>
              {searchDiagnostics.authoritySample?.length > 0 && (
                <Box display="flex" flexWrap="wrap" gap={1} sx={{ mb: 1 }}>
                  {searchDiagnostics.authoritySample.map((authority) => (
                    <Chip key={authority} size="small" variant="outlined" label={authority} />
                  ))}
                </Box>
              )}
              {searchDiagnostics.note && (
                <Typography variant="caption" color="text.secondary" display="block">
                  {searchDiagnostics.note}
                </Typography>
              )}
              {searchDiagnostics.generatedAt && (
                <Typography variant="caption" color="text.secondary" display="block">
                  Updated {format(new Date(searchDiagnostics.generatedAt), 'PPp')}
                </Typography>
              )}
              {isAdmin && (
                <>
                  <Divider sx={{ my: 1 }} />
                  <Box display="flex" alignItems="center" justifyContent="space-between" mb={1}>
                    <Typography variant="subtitle2">Index stats</Typography>
                    {searchIndexStatsLoading && <CircularProgress size={16} />}
                  </Box>
                  {searchIndexStatsError && (
                    <Typography variant="caption" color="error" display="block" sx={{ mb: 1 }}>
                      {searchIndexStatsError}
                    </Typography>
                  )}
                  {searchIndexStats && !searchIndexStatsError ? (
                    <Stack direction="row" spacing={1} flexWrap="wrap">
                      <Chip size="small" variant="outlined" label={`Index ${searchIndexStats.indexName}`} />
                      {typeof searchIndexStats.documentCount === 'number' && (
                        <Chip size="small" variant="outlined" label={`Documents ${searchIndexStats.documentCount}`} />
                      )}
                      <Chip
                        size="small"
                        color={searchIndexStats.searchEnabled ? 'success' : 'warning'}
                        label={searchIndexStats.searchEnabled ? 'Search enabled' : 'Search disabled'}
                      />
                    </Stack>
                  ) : (
                    !searchIndexStatsError && (
                      <Typography variant="caption" color="text.secondary" display="block">
                        Index stats unavailable
                      </Typography>
                    )
                  )}
                  <Divider sx={{ my: 1 }} />
                  <Box display="flex" alignItems="center" justifyContent="space-between" mb={1}>
                    <Typography variant="subtitle2">Rebuild status</Typography>
                    {searchRebuildStatusLoading && <CircularProgress size={16} />}
                  </Box>
                  {searchRebuildStatusError && (
                    <Typography variant="caption" color="error" display="block" sx={{ mb: 1 }}>
                      {searchRebuildStatusError}
                    </Typography>
                  )}
                  {searchRebuildStatus && !searchRebuildStatusError ? (
                    <Stack direction="row" spacing={1} flexWrap="wrap">
                      <Chip
                        size="small"
                        color={searchRebuildStatus.inProgress ? 'warning' : 'success'}
                        label={searchRebuildStatus.inProgress ? 'Rebuild in progress' : 'Rebuild idle'}
                      />
                      <Chip
                        size="small"
                        variant="outlined"
                        label={`Indexed ${searchRebuildStatus.documentsIndexed}`}
                      />
                    </Stack>
                  ) : (
                    !searchRebuildStatusError && (
                      <Typography variant="caption" color="text.secondary" display="block">
                        Rebuild status unavailable
                      </Typography>
                    )
                  )}
                </>
              )}
            </Paper>
          )}
          <Box display="flex" justifyContent="space-between" alignItems="center" mb={2}>
              <Typography variant="h6">
              {loading
                ? 'Searching...'
                : isSimilarMode
                  ? (paginatedNodes.length > 0
                    ? `Showing ${paginatedNodes.length} similar result${paginatedNodes.length === 1 ? '' : 's'}`
                    : '0 similar results found')
                  : shouldShowFallback
                    ? `Showing previous results (${paginatedNodes.length}) while the index refreshes`
                    : displayTotal > 0
                      ? `Showing ${paginatedNodes.length} of ${displayTotal} results`
                      : '0 results found'}
            </Typography>
            <FormControl size="small" sx={{ minWidth: 150 }}>
              <InputLabel>Sort by</InputLabel>
              <Select value={sortBy} onChange={(e) => setSortBy(e.target.value)} label="Sort by">
                <MenuItem value="relevance">Relevance</MenuItem>
                <MenuItem value="name">Name</MenuItem>
                <MenuItem value="modified">Modified Date</MenuItem>
                <MenuItem value="size">Size</MenuItem>
              </Select>
            </FormControl>
          </Box>
          {filtersApplied && (
            <Box display="flex" flexWrap="wrap" gap={1} mb={2}>
              {selectedMimeTypes.map((value) => (
                <Chip
                  key={`mime-${value}`}
                  label={`Type: ${value}`}
                  size="small"
                  onDelete={() => removeFacetValue(setSelectedMimeTypes, value)}
                />
              ))}
              {selectedCreators.map((value) => (
                <Chip
                  key={`creator-${value}`}
                  label={`Creator: ${value}`}
                  size="small"
                  onDelete={() => removeFacetValue(setSelectedCreators, value)}
                />
              ))}
              {selectedCorrespondents.map((value) => (
                <Chip
                  key={`corr-${value}`}
                  label={`Correspondent: ${value}`}
                  size="small"
                  onDelete={() => removeFacetValue(setSelectedCorrespondents, value)}
                />
              ))}
              {selectedTags.map((value) => (
                <Chip
                  key={`tag-${value}`}
                  label={`Tag: ${value}`}
                  size="small"
                  onDelete={() => removeFacetValue(setSelectedTags, value)}
                />
              ))}
              {selectedCategories.map((value) => (
                <Chip
                  key={`cat-${value}`}
                  label={`Category: ${value}`}
                  size="small"
                  onDelete={() => removeFacetValue(setSelectedCategories, value)}
                />
              ))}
              {selectedRecordCategoryPaths.map((value) => (
                <Chip
                  key={`record-category-${value}`}
                  label={`Record category: ${value}`}
                  size="small"
                  onDelete={() => removeFacetValue(setSelectedRecordCategoryPaths, value)}
                />
              ))}
              {selectedPreviewStatuses.map((value) => (
                <Chip
                  key={`preview-${value}`}
                  label={`Preview: ${value.toLowerCase()}`}
                  size="small"
                  onDelete={() => removeFacetValue(setSelectedPreviewStatuses, value)}
                />
              ))}
              {selectedLockState !== 'all' && (
                <Chip
                  key={`lock-state-${selectedLockState}`}
                  label={`Lock: ${selectedLockState === 'locked' ? 'locked' : 'unlocked'}`}
                  size="small"
                  onDelete={() => setSelectedLockState('all')}
                />
              )}
              {selectedLockOwner.trim() && (
                <Chip
                  key={`lock-owner-${selectedLockOwner.trim()}`}
                  label={`Lock owner: ${selectedLockOwner.trim()}`}
                  size="small"
                  onDelete={() => setSelectedLockOwner('')}
                />
              )}
              {selectedCheckoutState !== 'all' && (
                <Chip
                  key={`checkout-state-${selectedCheckoutState}`}
                  label={`Checkout: ${selectedCheckoutState === 'checkedOut' ? 'checked out' : 'available'}`}
                  size="small"
                  onDelete={() => setSelectedCheckoutState('all')}
                />
              )}
              {selectedCheckoutUser.trim() && (
                <Chip
                  key={`checkout-user-${selectedCheckoutUser.trim()}`}
                  label={`Checkout user: ${selectedCheckoutUser.trim()}`}
                  size="small"
                  onDelete={() => setSelectedCheckoutUser('')}
                />
              )}
              {selectedRecordOnly && (
                <Chip
                  key="record-only"
                  label="Record: declared only"
                  size="small"
                  onDelete={() => setSelectedRecordOnly(false)}
                />
              )}
              <Button size="small" onClick={clearFacetFilters}>
                Clear all
              </Button>
            </Box>
          )}

          {loading ? (
            <Box display="flex" justifyContent="center" p={4}>
              <CircularProgress />
            </Box>
          ) : paginatedNodes.length === 0 ? (
            <Paper sx={{ p: 4, textAlign: 'center' }}>
              <Typography variant="h6" color="text.secondary" gutterBottom>
                No results found
              </Typography>
              <Typography variant="body2" color="text.secondary">
                Try adjusting your search criteria or use the advanced search for more options
              </Typography>
              <Button
                variant="contained"
                startIcon={<FilterList />}
                onClick={handleAdvancedSearch}
                sx={{ mt: 2 }}
              >
                Advanced Search
              </Button>
            </Paper>
          ) : (
            <>
              <Grid container spacing={2}>
                {paginatedNodes.map((node) => {
                  const recordDeclaration = getRecordDeclarationFromNode(node);
                  return (
                  <Grid item xs={12} sm={6} md={4} lg={3} xl={2} key={node.id}>
                    <Card sx={{ height: '100%', display: 'flex', flexDirection: 'column' }}>
                      <CardContent sx={{ flex: 1 }}>
                        <Box display="flex" alignItems="center" mb={2}>
                          {getFileIcon(node)}
                            <Box ml={2} flex={1} sx={{ minWidth: 0 }}>
                              <Tooltip title={node.name} placement="top-start" arrow>
                                <Typography
                                variant="h6"
                                sx={getNameTypographySx(node.name)}
                              >
                                {node.name}
                              </Typography>
                              </Tooltip>
                              <Box display="flex" gap={1} mt={0.5}>
                                {(() => {
                                  const checkoutChip = getSearchResultCheckoutChip(node.checkedOut, node.checkoutUser);
                                  if (!checkoutChip) {
                                    return null;
                                  }
                                  return (
                                    <Tooltip title={checkoutChip.tooltip} placement="top-start" arrow>
                                      <Chip label={checkoutChip.label} size="small" variant="outlined" color={checkoutChip.color} />
                                    </Tooltip>
                                  );
                                })()}
                                {(() => {
                                  const lockChip = getSearchResultLockChip(node.locked, node.lockedBy);
                                  if (!lockChip) {
                                    return null;
                                  }
                                  return (
                                    <Tooltip title={lockChip.tooltip} placement="top-start" arrow>
                                      <Chip label={lockChip.label} size="small" variant="outlined" color={lockChip.color} />
                                    </Tooltip>
                                  );
                                })()}
                                {getFileTypeChip(node.contentType)}
                                {node.currentVersionLabel && (
                                  <Chip label={`v${node.currentVersionLabel}`} size="small" variant="outlined" />
                                )}
                                {recordDeclaration && (
                                  <RecordStatusChip declaration={recordDeclaration} />
                                )}
                                {getPreviewStatusChip(node)}
                            </Box>
                          </Box>
                        </Box>
                        {(() => {
                          const breadcrumb = formatBreadcrumbPath(node.path, { nodeName: node.name, maxSegments: 4 });
                          const creator = (node.creator || '').trim();
                          const parts = [breadcrumb, creator ? `By ${creator}` : null].filter(Boolean) as string[];
                          if (parts.length === 0) {
                            return null;
                          }
                          return (
                            <Tooltip title={node.path} placement="top-start" arrow disableHoverListener={!node.path}>
                              <Typography variant="caption" color="text.secondary" gutterBottom noWrap>
                                {parts.join(' | ')}
                              </Typography>
                            </Tooltip>
                          );
                        })()}
                        <Highlight
                          text={node.description}
                          highlights={
                            node.highlightSummary
                              ? [node.highlightSummary]
                              : node.highlights?.description
                                || node.highlights?.content
                                || node.highlights?.textContent
                                || node.highlights?.extractedText
                                || node.highlights?.title
                                || node.highlights?.name
                          }
                        />
                        {(() => {
                          const matchFields = resolveMatchFields(node);
                          if (matchFields.length === 0) {
                            return null;
                          }
                          const displayFields = matchFields.slice(0, 4);
                          const remaining = matchFields.length - displayFields.length;
                          return (
                            <Stack direction="row" spacing={0.5} sx={{ mt: 1, flexWrap: 'wrap', gap: 0.5 }}>
                              <Chip size="small" label="Matched in" variant="outlined" />
                              {displayFields.map((field) => (
                                <Chip key={`${node.id}-match-${field}`} size="small" label={formatMatchField(field)} />
                              ))}
                              {remaining > 0 && (
                                <Chip size="small" label={`+${remaining}`} variant="outlined" />
                              )}
                              {formatScore(node.score) && (
                                <Chip size="small" variant="outlined" label={formatScore(node.score)} />
                              )}
                            </Stack>
                          );
                        })()}
                        {(() => {
                          const details = resolveHighlightDetails(node);
                          if (details.length === 0) {
                            return null;
                          }
                          return (
                            <Box sx={{ mt: 1 }}>
                              <Typography variant="caption" color="text.secondary" display="block">
                                Hit details
                              </Typography>
                              <Stack spacing={0.5} sx={{ mt: 0.5 }}>
                                {details.map((detail) => (
                                  <Box key={`${node.id}-detail-${detail.field}`} display="flex" alignItems="flex-start" gap={0.5}>
                                    <Chip size="small" variant="outlined" label={detail.label} />
                                    <Typography
                                      variant="caption"
                                      color="text.secondary"
                                      sx={{
                                        display: '-webkit-box',
                                        WebkitLineClamp: 2,
                                        WebkitBoxOrient: 'vertical',
                                        overflow: 'hidden',
                                        '& em': {
                                          fontStyle: 'normal',
                                          fontWeight: 700,
                                        },
                                      }}
                                      dangerouslySetInnerHTML={{ __html: detail.snippet }}
                                    />
                                  </Box>
                                ))}
                              </Stack>
                            </Box>
                          );
                        })()}
                        {renderTagsCategories(node)}
                        <Box mt={2}>
                          <Typography variant="caption" color="text.secondary">
                            Modified: {format(new Date(node.modified), 'PPp')}
                          </Typography>
                          <br />
                          <Typography variant="caption" color="text.secondary">
                            Size: {formatFileSize(node.size)}
                          </Typography>
                        </Box>
                      </CardContent>
                      <CardActions>
                        <Button size="small" startIcon={<Visibility />} onClick={() => void handlePreviewNode(node)}>
                          View
                        </Button>
                        {isDocumentNode(node) && isPdfDocument(node) && canWrite && (
                          <Tooltip
                            title={getFileLockActionReason(node, 'annotate this item', currentUsername) || ''}
                            placement="top-start"
                            arrow
                            disableHoverListener={!getFileLockActionReason(node, 'annotate this item', currentUsername)}
                          >
                            <span>
                              <Button
                                size="small"
                                startIcon={<Edit />}
                                disabled={Boolean(getFileLockActionReason(node, 'annotate this item', currentUsername))}
                                onClick={() => void handlePreviewNode(node, { annotate: true })}
                              >
                                Annotate (PDF)
                              </Button>
                            </span>
                          </Tooltip>
                        )}
                        {isDocumentNode(node) && isOfficeDocument(node) && !isPdfDocument(node) && (
                          <Tooltip
                            title={canWrite ? (getFileLockActionReason(node, 'edit this item online', currentUsername) || '') : ''}
                            placement="top-start"
                            arrow
                            disableHoverListener={!canWrite || !getFileLockActionReason(node, 'edit this item online', currentUsername)}
                          >
                            <span>
                              <Button
                                size="small"
                                startIcon={canWrite ? <Edit /> : <Visibility />}
                                disabled={canWrite && Boolean(getFileLockActionReason(node, 'edit this item online', currentUsername))}
                                onClick={() => handleEditOnline(node, canWrite ? 'write' : 'read')}
                              >
                                {canWrite ? 'Edit Online' : 'View Online'}
                              </Button>
                            </span>
                          </Tooltip>
                        )}
                        {isDocumentNode(node) && (
                          <Button size="small" startIcon={<Download />} onClick={() => handleDownload(node)}>
                            Download
                          </Button>
                        )}
                        {canWrite && isDocumentNode(node) && (
                          <Button size="small" startIcon={<ShareIcon />} onClick={() => handleOpenShareLink(node)}>
                            Share
                          </Button>
                        )}
                        {isDocumentNode(node) && node.checkedOut && (
                          <Button size="small" startIcon={<AccountTree />} onClick={() => handleOpenCheckoutGraph(node)}>
                            Checkout Graph
                          </Button>
                        )}
                        {isDocumentNode(node) && node.checkedOut && (
                          <Button size="small" startIcon={<FolderOpen />} onClick={() => void handleOpenCheckInTarget(node)}>
                            Open Check-In Target
                          </Button>
                        )}
                        {isDocumentNode(node) && (
                          <Button size="small" startIcon={<InfoOutlined />} onClick={() => handleOpenRenditionRegistry(node)}>
                            Rendition Registry
                          </Button>
                        )}
                        {canWrite && isDocumentNode(node) && !node.checkedOut && (
                          <Tooltip
                            title={getAdvancedSearchCheckoutActionReason(node, currentUsername) || ''}
                            placement="top-start"
                            arrow
                            disableHoverListener={!getAdvancedSearchCheckoutActionReason(node, currentUsername)}
                          >
                            <span>
                              <Button
                                size="small"
                                variant="outlined"
                                startIcon={<CheckCircleOutline />}
                                disabled={checkoutActionNodeId === node.id || Boolean(getAdvancedSearchCheckoutActionReason(node, currentUsername))}
                                onClick={() => void handleCheckoutNode(node)}
                              >
                                {checkoutActionNodeId === node.id ? 'Checking out...' : 'Check Out'}
                              </Button>
                            </span>
                          </Tooltip>
                        )}
                        {canWrite && isDocumentNode(node) && node.checkedOut && (
                          <Tooltip
                            title={getAdvancedSearchCheckInActionReason(node, currentUsername, isAdmin) || ''}
                            placement="top-start"
                            arrow
                            disableHoverListener={!getAdvancedSearchCheckInActionReason(node, currentUsername, isAdmin)}
                          >
                            <span>
                              <Button
                                size="small"
                                variant="outlined"
                                startIcon={<Publish />}
                                disabled={checkInSubmittingNodeId === node.id || Boolean(getAdvancedSearchCheckInActionReason(node, currentUsername, isAdmin))}
                                onClick={() => openCheckInDialog(node)}
                              >
                                {checkInSubmittingNodeId === node.id ? 'Checking in...' : 'Check In'}
                              </Button>
                            </span>
                          </Tooltip>
                        )}
                        {canWrite && isDocumentNode(node) && node.checkedOut && (
                          <Tooltip
                            title={getAdvancedSearchCancelCheckoutReason(node, currentUsername, isAdmin) || ''}
                            placement="top-start"
                            arrow
                            disableHoverListener={!getAdvancedSearchCancelCheckoutReason(node, currentUsername, isAdmin)}
                          >
                            <span>
                              <Button
                                size="small"
                                variant="outlined"
                                color="inherit"
                                startIcon={<Undo />}
                                disabled={cancelCheckoutActionNodeId === node.id || Boolean(getAdvancedSearchCancelCheckoutReason(node, currentUsername, isAdmin))}
                                onClick={() => void handleCancelCheckoutNode(node)}
                              >
                                {cancelCheckoutActionNodeId === node.id ? 'Cancelling...' : 'Cancel Checkout'}
                              </Button>
                            </span>
                          </Tooltip>
                        )}
                        {isDocumentNode(node) && (
                          <Button
                            size="small"
                            startIcon={<AutoAwesome />}
                            onClick={() => handleFindSimilar(node)}
                            disabled={similarLoadingId === node.id}
                          >
                            {similarLoadingId === node.id ? 'Finding...' : 'More like this'}
                          </Button>
                        )}
                      </CardActions>
                    </Card>
                  </Grid>
                  );
                })}
              </Grid>

              {totalPages > 1 && (
                <Box display="flex" justifyContent="center" mt={4}>
                  <Pagination
                    count={totalPages}
                    page={page}
                    onChange={(_, value) => {
                      setPage(value);
                      if (lastSearchCriteria) {
                        const sortParams = getSortParams(sortBy);
                        runSearch({ ...lastSearchCriteria, page: value - 1, size: pageSize, ...sortParams });
                      }
                      window.scrollTo({ top: 0, behavior: 'smooth' });
                    }}
                    color="primary"
                  />
                </Box>
              )}
            </>
          )}
        </Grid>
      </Grid>

      {previewOpen && previewNode && (
        <React.Suspense
          fallback={(
            <Box
              sx={{
                position: 'fixed',
                inset: 0,
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
                bgcolor: 'rgba(0,0,0,0.35)',
                zIndex: (theme) => theme.zIndex.modal + 1,
              }}
            >
              <CircularProgress />
            </Box>
          )}
        >
          <DocumentPreview
            open={previewOpen}
            onClose={handleClosePreview}
            node={previewNode}
            initialAnnotateMode={previewAnnotate}
          />
        </React.Suspense>
      )}

      <Dialog
        open={Boolean(checkInDialogNode)}
        onClose={closeCheckInDialog}
        maxWidth="sm"
        fullWidth
      >
        <DialogTitle>Check In</DialogTitle>
        <DialogContent dividers sx={{ display: 'flex', flexDirection: 'column', gap: 2 }}>
          <Typography variant="body2" color="text.secondary">
            {checkInDialogNode ? `Check in "${checkInDialogNode.name}" with an optional new version file.` : ''}
          </Typography>
          <Alert severity="info">
            Leave the file empty to release checkout without uploading a new version. Enable keep checked out only when uploading a new file.
          </Alert>
          <Button component="label" variant="outlined" sx={{ alignSelf: 'flex-start' }}>
            {checkInFile ? `Selected: ${checkInFile.name}` : 'Choose New Version File'}
            <input
              hidden
              type="file"
              onChange={(event) => {
                const file = event.target.files?.[0] ?? null;
                setCheckInFile(file);
                if (!file) {
                  setCheckInMajorVersion(false);
                  setCheckInKeepCheckedOut(false);
                }
                event.currentTarget.value = '';
              }}
            />
          </Button>
          {checkInFile && (
            <Button
              color="inherit"
              onClick={() => {
                setCheckInFile(null);
                setCheckInMajorVersion(false);
                setCheckInKeepCheckedOut(false);
              }}
              sx={{ alignSelf: 'flex-start' }}
            >
              Clear File
            </Button>
          )}
          <TextField
            label="Version Comment"
            value={checkInComment}
            onChange={(event) => setCheckInComment(event.target.value)}
            fullWidth
            multiline
            minRows={2}
          />
          <FormControlLabel
            control={(
              <Switch
                checked={checkInMajorVersion}
                onChange={(event) => setCheckInMajorVersion(event.target.checked)}
                disabled={!checkInFile}
              />
            )}
            label="Create major version"
          />
          <FormControlLabel
            control={(
              <Switch
                checked={checkInKeepCheckedOut}
                onChange={(event) => setCheckInKeepCheckedOut(event.target.checked)}
                disabled={!checkInFile || (Boolean(checkInDialogNode?.checkoutUser) && checkInDialogNode?.checkoutUser !== currentUsername)}
              />
            )}
            label="Keep checked out after check-in"
          />
        </DialogContent>
        <DialogActions>
          <Button onClick={closeCheckInDialog} disabled={Boolean(checkInSubmittingNodeId)}>
            Cancel
          </Button>
          <Button variant="contained" onClick={() => void handleCheckInNode()} disabled={Boolean(checkInSubmittingNodeId)}>
            {checkInSubmittingNodeId ? 'Checking in...' : 'Check In'}
          </Button>
        </DialogActions>
      </Dialog>
      <CheckoutGraphDialog
        open={Boolean(checkoutGraphDialogNode)}
        nodeId={checkoutGraphDialogNode?.id || null}
        nodeName={checkoutGraphDialogNode?.name}
        onClose={() => setCheckoutGraphDialogNode(null)}
      />
      <RenditionDefinitionDialog
        open={Boolean(renditionDialogNode)}
        nodeId={renditionDialogNode?.id || null}
        nodeName={renditionDialogNode?.name}
        onClose={() => setRenditionDialogNode(null)}
      />
    </Box>
  );
};

export default SearchResults;
