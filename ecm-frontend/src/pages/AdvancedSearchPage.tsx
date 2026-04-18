import React, { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import {
  Box,
  TextField,
  Button,
  Grid,
  Paper,
  Typography,
  Chip,
  Alert,
  FormControl,
  Select,
  MenuItem,
  Checkbox,
  ListItemText,
  Pagination,
  Stack,
  Divider,
  Tooltip,
  IconButton,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  FormControlLabel,
  Switch,
} from '@mui/material';
import {
  Search as SearchIcon,
  FilterList as FilterIcon,
  Refresh as RefreshIcon,
  Autorenew as RebuildIcon,
  CancelOutlined as CancelQueueIcon,
  CheckCircleOutline,
  Publish,
  Undo,
} from '@mui/icons-material';
import { format } from 'date-fns';
import { toast } from 'react-toastify';
import { useLocation, useNavigate } from 'react-router-dom';
import { useAppDispatch, useAppSelector } from 'store';
import { setSidebarOpen } from 'store/slices/uiSlice';
import nodeService, {
  AdvancedSearchPivotStats,
  AdvancedSearchStats,
  NodeCheckoutGraph,
  NodeRelationEdge,
  NodeRelationNodeRef,
  NodeCheckoutRelation,
  NodeRenditionDefinitionStatus,
  NodeRenditionRelation,
  NodeRelationsSummary,
  PreviewQueueSearchBatchItem,
  PreviewQueueSearchCapabilities,
  PreviewQueueSearchDryRunExportAsyncTaskActiveStatusFilter,
  PreviewQueueSearchDryRunExportAsyncTaskSummary,
} from 'services/nodeService';
import { Version } from 'types';
import opsRecoveryService from 'services/opsRecoveryService';
import savedSearchService, { SavedSearchTemplate } from 'services/savedSearchService';
import {
  buildNonRetryablePreviewSummaryMessage,
  formatPreviewBatchOperationProgress,
  formatPreviewFailureReasonLabel,
  getEffectivePreviewStatus,
  getFailedPreviewMeta,
  isRetryablePreviewFailure,
  normalizePreviewFailureReason,
  PreviewBatchOperationProgress,
  summarizeFailedPreviews,
} from 'utils/previewStatusUtils';
import {
  getAdvancedSearchCancelCheckoutReason,
  getAdvancedSearchCheckInActionReason,
  getAdvancedSearchCheckoutActionReason,
} from 'utils/advancedSearchActionUtils';
import { getSearchResultLockChip } from 'utils/advancedSearchLockUtils';
import {
  AdvancedSearchDateRange,
  AdvancedSearchFilterState,
  AdvancedSearchUrlState,
  buildAdvancedSearchCriteriaKey,
  buildAdvancedSearchUrlSearch,
  buildSearchCriteriaFromAdvancedState,
  hasActiveAdvancedSearchCriteria,
  hasRestorableAdvancedSearchState,
  parseAdvancedSearchUrlState,
  resolveModifiedFromDate,
  resolveTemplateQueryState,
} from 'utils/advancedSearchStateUtils';
import { getSearchResultCheckoutChip } from 'utils/advancedSearchCheckoutUtils';
import { shouldSuppressStaleFallbackForQuery } from 'utils/searchFallbackUtils';
import { buildPreviewQueueOverride } from 'utils/previewQueueOverrideUtils';
import CheckoutGraphDialog from 'components/dialogs/CheckoutGraphDialog';
import RenditionDefinitionDialog from 'components/dialogs/RenditionDefinitionDialog';
import RecordStatusChip from 'components/records/RecordStatusChip';
import { formatCheckoutGraphSummary } from 'utils/checkoutGraphUtils';
import { summarizeNodeAssociationEdges } from 'utils/nodeAssociationUtils';
import { applyPreviewQueueSearchBatchResultToOverrides } from 'utils/previewQueueSearchBatchUtils';
import { summarizeRenditionDefinitions } from 'utils/renditionDefinitionUtils';
import { formatBreadcrumbPath } from 'utils/pathDisplayUtils';
import { getRecordDeclarationFromNode } from 'utils/recordDeclarationUtils';
import { buildSearchErrorRecovery, SearchErrorRecovery } from 'utils/searchErrorUtils';

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
const DEFAULT_PREVIEW_BATCH_MATCHED_MAX = 200;
const PREVIEW_BATCH_MATCHED_PAGE_SIZE = 50;
const DEFAULT_PREVIEW_BATCH_WORKER_COUNT = 4;
const MAX_PREVIEW_BATCH_WORKER_COUNT = 16;
const EXPORT_DRY_RUN_CSV_POLL_MAX_ATTEMPTS = 30;
const EXPORT_DRY_RUN_CSV_POLL_INTERVAL_MS = 1000;
const NODE_RELATIONS_DETAILS_PAGE_SIZE = 5;

type ExportDryRunCsvPhase = 'idle' | 'preparing' | 'running' | 'downloading';
type ExportDryRunCsvTaskPhase = 'preparing' | 'running' | 'completed' | 'failed';
type ExportDryRunTaskStatusFilter = 'ALL' | 'QUEUED' | 'RUNNING' | 'COMPLETED' | 'CANCELLED' | 'FAILED';
type LockSearchState = 'all' | 'locked' | 'unlocked';
type CheckoutSearchState = 'all' | 'checkedOut' | 'available';
type PreviewQueueUiStatus = {
  previewStatus?: string | null;
  previewFailureReason?: string | null;
  previewFailureCategory?: string | null;
  previewLastUpdated?: string | null;
  attempts?: number;
  nextAttemptAt?: string;
  queueState?: string;
  message?: string | null;
};
const ACTIVE_PREVIEW_QUEUE_STATES = new Set(['QUEUED', 'RUNNING', 'PROCESSING', 'CANCEL_REQUESTED']);

const EXPORT_DRY_RUN_CSV_RUNNING_STATUSES = new Set(['RUNNING', 'PROCESSING', 'IN_PROGRESS', 'STARTED']);
const EXPORT_DRY_RUN_CSV_COMPLETED_STATUSES = new Set(['COMPLETED', 'DONE', 'SUCCESS', 'SUCCEEDED', 'FINISHED']);
const EXPORT_DRY_RUN_CSV_FAILED_STATUSES = new Set(['FAILED', 'ERROR', 'CANCELED', 'CANCELLED']);
const EXPORT_DRY_RUN_TASK_STATUS_FILTERS: ExportDryRunTaskStatusFilter[] = [
  'ALL',
  'QUEUED',
  'RUNNING',
  'COMPLETED',
  'CANCELLED',
  'FAILED',
];

const waitForDelay = (delayMs: number) =>
  new Promise<void>((resolve) => {
    window.setTimeout(resolve, delayMs);
  });

const resolveExportDryRunCsvTaskPhase = (status?: string | null): ExportDryRunCsvTaskPhase => {
  const normalized = (status || '').trim().toUpperCase();
  if (EXPORT_DRY_RUN_CSV_COMPLETED_STATUSES.has(normalized)) {
    return 'completed';
  }
  if (EXPORT_DRY_RUN_CSV_FAILED_STATUSES.has(normalized)) {
    return 'failed';
  }
  if (EXPORT_DRY_RUN_CSV_RUNNING_STATUSES.has(normalized)) {
    return 'running';
  }
  return 'preparing';
};

const formatExportDryRunCsvProgressText = (phase: ExportDryRunCsvPhase): string => {
  if (phase === 'preparing') {
    return 'Preparing CSV export...';
  }
  if (phase === 'running') {
    return 'Running CSV export...';
  }
  if (phase === 'downloading') {
    return 'Downloading CSV...';
  }
  return 'Export dry-run CSV';
};

const extractErrorMessage = (error: unknown): string | null => {
  const responseDataMessage = (error as { response?: { data?: { message?: unknown } } })?.response?.data?.message;
  if (typeof responseDataMessage === 'string' && responseDataMessage.trim().length > 0) {
    return responseDataMessage;
  }

  const errorMessage = (error as { message?: unknown })?.message;
  if (typeof errorMessage === 'string' && errorMessage.trim().length > 0) {
    return errorMessage;
  }
  return null;
};

const normalizePivotLabel = (value: string | null | undefined, fallback: string): string => {
  const normalized = (value || '').trim();
  return normalized.length > 0 ? normalized : fallback;
};

const normalizeExportTaskStatus = (status?: string | null): string => {
  const normalized = (status || '').trim().toUpperCase();
  return normalized || 'UNKNOWN';
};

const isActivePreviewQueueState = (queueState?: string | null): boolean =>
  ACTIVE_PREVIEW_QUEUE_STATES.has((queueState || '').trim().toUpperCase());

const clampNumber = (value: number, min: number, max: number): number =>
  Math.min(Math.max(value, min), max);

const getFallbackAutoRetryDelayMs = (attempt: number) => {
  if (attempt < 0) {
    return FALLBACK_AUTO_RETRY_BASE_DELAY_MS;
  }
  const scaled = FALLBACK_AUTO_RETRY_BASE_DELAY_MS * (2 ** attempt);
  return Math.min(scaled, FALLBACK_AUTO_RETRY_MAX_DELAY_MS);
};

const resolveWindowDaysFromDateRange = (range: AdvancedSearchDateRange): number => {
  switch (range) {
    case 'today':
      return 1;
    case 'week':
      return 7;
    case 'month':
      return 30;
    default:
      return 0;
  }
};

interface SearchResult {
  id: string;
  name: string;
  mimeType: string;
  fileSize: number;
  checkedOut?: boolean;
  checkoutUser?: string;
  locked?: boolean;
  lockedBy?: string;
  createdBy?: string;
  highlights?: Record<string, string[]>;
  matchFields?: string[];
  highlightSummary?: string;
  score: number;
  createdDate: string;
  path: string;
  nodeType?: 'FOLDER' | 'DOCUMENT';
  parentId?: string;
  previewStatus?: string;
  previewFailureReason?: string;
  previewFailureCategory?: string;
  record?: boolean;
  declaredBy?: string;
  declaredAt?: string;
  declaredVersionLabel?: string;
  declarationComment?: string;
  recordCategoryId?: string;
  recordCategoryName?: string;
  recordCategoryPath?: string;
}


interface FacetValue {
  value: string;
  count: number;
}

interface Facets {
  mimeType: FacetValue[];
  createdBy: FacetValue[];
  tags: FacetValue[];
  categories: FacetValue[];
  recordCategoryPath: FacetValue[];
  previewStatus?: FacetValue[];
}

interface PreviewBatchDryRunSummary {
  query: string | null;
  reason: string | null;
  maxDocuments: number;
  totalCandidates: number;
  scanned: number;
  matched: number;
  scanSkipped: number;
  truncated: boolean;
  workerCount: number;
  reasonBreakdown: Array<{
    reason: string;
    count: number;
  }>;
  sampleCount: number;
  samples: Array<{
    documentId: string | null;
    name: string | null;
    previewStatus: string | null;
    previewFailureReason: string | null;
    previewFailureCategory: string | null;
    preflightStatus?: string | null;
    preflightSkipReason?: string | null;
    preflightRoute?: string | null;
    preflightPolicyProfile?: string | null;
    preflightPipeline?: string | null;
  }>;
  finishedAt: Date;
}

interface PreviewBatchExportTask {
  taskId: string;
  status: string;
  error: string | null;
  createdAt: string | null;
  finishedAt: string | null;
  filename: string | null;
}

interface FailedReasonActionItem {
  reason: string;
  count: number;
  category: string;
  retryable: boolean;
}

const normalizeSearchFacets = (
  rawFacets?: Record<string, FacetValue[]>
): Facets | null => {
  if (!rawFacets) {
    return null;
  }
  return {
    mimeType: rawFacets.mimeType || [],
    createdBy: rawFacets.createdBy || [],
    tags: rawFacets.tags || [],
    categories: rawFacets.categories || [],
    recordCategoryPath: rawFacets.recordCategoryPath || [],
    previewStatus: rawFacets.previewStatus || [],
  };
};

const AdvancedSearchPage: React.FC = () => {
  const navigate = useNavigate();
  const location = useLocation();
  const dispatch = useAppDispatch();
  const { sidebarAutoCollapse } = useAppSelector((state) => state.ui);
  const { user } = useAppSelector((state) => state.auth);
  const canWrite = Boolean(user?.roles?.includes('ROLE_ADMIN') || user?.roles?.includes('ROLE_EDITOR'));
  const isAdmin = Boolean(user?.roles?.includes('ROLE_ADMIN'));
  const currentUsername = user?.username ?? null;
  const initializedFromUrlRef = useRef(false);
  const [query, setQuery] = useState('');
  const [results, setResults] = useState<SearchResult[]>([]);
  const [facets, setFacets] = useState<Facets | null>(null);
  const [searchStats, setSearchStats] = useState<AdvancedSearchStats | null>(null);
  const [searchPivotStats, setSearchPivotStats] = useState<AdvancedSearchPivotStats | null>(null);
  const [nodeRelationsSummary, setNodeRelationsSummary] = useState<NodeRelationsSummary | null>(null);
  const [nodeRelationsSummaryUnavailable, setNodeRelationsSummaryUnavailable] = useState(false);
  const [nodeRelationsSummaryLoading, setNodeRelationsSummaryLoading] = useState(false);
  const [nodeRelationParents, setNodeRelationParents] = useState<NodeRelationNodeRef[]>([]);
  const [nodeRelationSources, setNodeRelationSources] = useState<NodeRelationEdge[]>([]);
  const [nodeRelationTargets, setNodeRelationTargets] = useState<NodeRelationEdge[]>([]);
  const [nodeRelationSecondaryChildren, setNodeRelationSecondaryChildren] = useState<NodeRelationEdge[]>([]);
  const [nodeRelationSecondaryParents, setNodeRelationSecondaryParents] = useState<NodeRelationEdge[]>([]);
  const [nodeRelationVersions, setNodeRelationVersions] = useState<Version[]>([]);
  const [nodeRelationCheckout, setNodeRelationCheckout] = useState<NodeCheckoutRelation | null>(null);
  const [nodeRelationCheckoutGraph, setNodeRelationCheckoutGraph] = useState<NodeCheckoutGraph | null>(null);
  const [nodeRelationRenditions, setNodeRelationRenditions] = useState<NodeRenditionRelation[]>([]);
  const [nodeRelationRenditionDefinitions, setNodeRelationRenditionDefinitions] = useState<NodeRenditionDefinitionStatus[]>([]);
  const [nodeRelationDetailsUnavailable, setNodeRelationDetailsUnavailable] = useState(false);
  const [nodeRelationDetailsLoading, setNodeRelationDetailsLoading] = useState(false);
  const [totalResults, setTotalResults] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [page, setPage] = useState(1);
  const [loading, setLoading] = useState(false);
  const [searchError, setSearchError] = useState<SearchErrorRecovery | null>(null);
  const [templates, setTemplates] = useState<SavedSearchTemplate[]>([]);
  const [templatesLoading, setTemplatesLoading] = useState(false);
  const [templatesError, setTemplatesError] = useState<string | null>(null);

  // Filters
  const [selectedMimeTypes, setSelectedMimeTypes] = useState<string[]>([]);
  const [selectedCreators, setSelectedCreators] = useState<string[]>([]);
  const [selectedTags, setSelectedTags] = useState<string[]>([]);
  const [selectedCategories, setSelectedCategories] = useState<string[]>([]);
  const [selectedRecordCategoryPaths, setSelectedRecordCategoryPaths] = useState<string[]>([]);
  const [lockState, setLockState] = useState<LockSearchState>('all');
  const [lockOwner, setLockOwner] = useState('');
  const [checkoutState, setCheckoutState] = useState<CheckoutSearchState>('all');
  const [checkoutUser, setCheckoutUser] = useState('');
  const [recordOnly, setRecordOnly] = useState(false);
  const [dateRange, setDateRange] = useState<'all' | 'today' | 'week' | 'month'>('all');
  const [minSize, setMinSize] = useState<number | undefined>();
  const [maxSize, setMaxSize] = useState<number | undefined>();
  const [selectedPreviewStatuses, setSelectedPreviewStatuses] = useState<string[]>([]);
  const [checkoutActionNodeId, setCheckoutActionNodeId] = useState<string | null>(null);
  const [checkInDialogResult, setCheckInDialogResult] = useState<SearchResult | null>(null);
  const [checkInFile, setCheckInFile] = useState<File | null>(null);
  const [checkInComment, setCheckInComment] = useState('');
  const [checkInMajorVersion, setCheckInMajorVersion] = useState(false);
  const [checkInKeepCheckedOut, setCheckInKeepCheckedOut] = useState(false);
  const [checkInSubmittingNodeId, setCheckInSubmittingNodeId] = useState<string | null>(null);
  const [cancelCheckoutActionNodeId, setCancelCheckoutActionNodeId] = useState<string | null>(null);
  const [checkoutGraphDialogOpen, setCheckoutGraphDialogOpen] = useState(false);
  const [renditionDialogNode, setRenditionDialogNode] = useState<{ id: string; name?: string } | null>(null);
  const [queueingPreviewId, setQueueingPreviewId] = useState<string | null>(null);
  const [batchRetrying, setBatchRetrying] = useState(false);
  const [batchDryRunning, setBatchDryRunning] = useState(false);
  const [batchTargetResolving, setBatchTargetResolving] = useState(false);
  const [previewBatchCapabilities, setPreviewBatchCapabilities] = useState<PreviewQueueSearchCapabilities | null>(null);
  const [previewBatchMaxDocuments, setPreviewBatchMaxDocuments] = useState(DEFAULT_PREVIEW_BATCH_MATCHED_MAX);
  const [previewBatchWorkerMax, setPreviewBatchWorkerMax] = useState(MAX_PREVIEW_BATCH_WORKER_COUNT);
  const [previewBatchWorkerCount, setPreviewBatchWorkerCount] = useState(DEFAULT_PREVIEW_BATCH_WORKER_COUNT);
  const [previewBatchLabel, setPreviewBatchLabel] = useState<string | null>(null);
  const [previewBatchProgress, setPreviewBatchProgress] = useState<PreviewBatchOperationProgress | null>(null);
  const [previewBatchReasonBreakdown, setPreviewBatchReasonBreakdown] = useState<Array<{ reason: string; count: number }> | null>(null);
  const [previewBatchSkipBreakdown, setPreviewBatchSkipBreakdown] = useState<Array<{ reason: string; count: number }> | null>(null);
  const [previewBatchDryRunSummary, setPreviewBatchDryRunSummary] = useState<PreviewBatchDryRunSummary | null>(null);
  const [previewBatchFinishedAt, setPreviewBatchFinishedAt] = useState<Date | null>(null);
  const [exportingDryRunCsv, setExportingDryRunCsv] = useState(false);
  const [exportDryRunCsvPhase, setExportDryRunCsvPhase] = useState<ExportDryRunCsvPhase>('idle');
  const [exportDryRunCsvTaskId, setExportDryRunCsvTaskId] = useState<string | null>(null);
  const [cancelingDryRunCsv, setCancelingDryRunCsv] = useState(false);
  const exportDryRunCsvCancelledByUserRef = useRef(false);
  const [previewBatchExportTasks, setPreviewBatchExportTasks] = useState<PreviewBatchExportTask[]>([]);
  const [previewBatchExportTasksLoading, setPreviewBatchExportTasksLoading] = useState(false);
  const [previewBatchExportTaskStatusFilter, setPreviewBatchExportTaskStatusFilter] = useState<ExportDryRunTaskStatusFilter>('ALL');
  const [previewBatchExportTaskSummary, setPreviewBatchExportTaskSummary] = useState<PreviewQueueSearchDryRunExportAsyncTaskSummary | null>(null);
  const [previewBatchExportTasksCleanupLoading, setPreviewBatchExportTasksCleanupLoading] = useState(false);
  const [previewBatchExportTasksCancelActiveLoading, setPreviewBatchExportTasksCancelActiveLoading] = useState(false);
  const [showAllRetryReasons, setShowAllRetryReasons] = useState(false);
  const [showAllNonRetryReasons, setShowAllNonRetryReasons] = useState(false);
  const [reasonDeadLetterActionKey, setReasonDeadLetterActionKey] = useState<string | null>(null);
  const [previewQueueStatusById, setPreviewQueueStatusById] = useState<Record<string, PreviewQueueUiStatus>>({});
  const [cancelingPreviewId, setCancelingPreviewId] = useState<string | null>(null);
  const [fallbackResults, setFallbackResults] = useState<SearchResult[]>([]);
  const [fallbackLabel, setFallbackLabel] = useState('');
  const [fallbackCriteriaKey, setFallbackCriteriaKey] = useState('');
  const [currentCriteriaKey, setCurrentCriteriaKey] = useState('');
  const [currentCriteriaHasFilters, setCurrentCriteriaHasFilters] = useState(false);
  const [dismissedFallbackCriteriaKey, setDismissedFallbackCriteriaKey] = useState('');
  const [forcedFallbackCriteriaKey, setForcedFallbackCriteriaKey] = useState('');
  const [fallbackAutoRetryCount, setFallbackAutoRetryCount] = useState(0);
  const [fallbackLastRetryAt, setFallbackLastRetryAt] = useState<Date | null>(null);
  const fallbackAutoRetryTimerRef = useRef<number | null>(null);
  const nodeRelationsSummaryRequestSeqRef = useRef(0);
  const nodeRelationsDetailsRequestSeqRef = useRef(0);

  const syncSearchStateToUrl = useCallback((state: AdvancedSearchUrlState) => {
    const nextSearch = buildAdvancedSearchUrlSearch(state);
    navigate(
      {
        pathname: location.pathname,
        search: nextSearch ? `?${nextSearch}` : '',
      },
      { replace: true }
    );
  }, [location.pathname, navigate]);

  const getPreviewStatusMeta = (
    status?: string,
    mimeType?: string,
    failureCategory?: string,
    failureReason?: string
  ) => {
    const normalized = status?.toUpperCase();
    if (!normalized || normalized === 'READY') {
      return null;
    }
    if (normalized === 'FAILED') {
      return getFailedPreviewMeta(mimeType, failureCategory, failureReason);
    }
    if (normalized === 'UNSUPPORTED') {
      return { label: 'Preview unsupported', color: 'default' as const, unsupported: true };
    }
    if (normalized === 'PROCESSING') {
      return { label: 'Preview processing', color: 'warning' as const, unsupported: false };
    }
    if (normalized === 'QUEUED') {
      return { label: 'Preview queued', color: 'info' as const, unsupported: false };
    }
    return { label: `Preview ${normalized.toLowerCase()}`, color: 'default' as const, unsupported: false };
  };

  const formatScore = (score?: number) => {
    if (score === undefined || score === null) {
      return null;
    }
    const rounded = Math.round(score * 100) / 100;
    return `Score ${rounded}`;
  };

  useEffect(() => {
    let cancelled = false;
    const loadPreviewBatchCapabilities = async () => {
      try {
        const response = await nodeService.getPreviewQueueBySearchCapabilities();
        if (cancelled || !response) {
          return;
        }
        const maxDocuments = clampNumber(
          Math.max(1, Number(response.maxMaxDocuments) || DEFAULT_PREVIEW_BATCH_MATCHED_MAX),
          1,
          5000
        );
        const preferredDocuments = clampNumber(
          DEFAULT_PREVIEW_BATCH_MATCHED_MAX,
          1,
          maxDocuments
        );
        const workerMax = clampNumber(
          Math.max(1, Number(response.maxWorkerCount) || MAX_PREVIEW_BATCH_WORKER_COUNT),
          1,
          MAX_PREVIEW_BATCH_WORKER_COUNT
        );
        const workerDefault = clampNumber(
          Math.max(1, Number(response.defaultWorkerCount) || DEFAULT_PREVIEW_BATCH_WORKER_COUNT),
          1,
          workerMax
        );
        setPreviewBatchCapabilities(response);
        setPreviewBatchMaxDocuments(preferredDocuments);
        setPreviewBatchWorkerMax(workerMax);
        setPreviewBatchWorkerCount(workerDefault);
      } catch {
        // Keep local defaults when capability endpoint is unavailable.
      }
    };
    void loadPreviewBatchCapabilities();
    return () => {
      cancelled = true;
    };
  }, []);

  useEffect(() => {
    let cancelled = false;

    const loadTemplates = async () => {
      setTemplatesLoading(true);
      try {
        const response = await savedSearchService.listTemplates('governance');
        if (cancelled) {
          return;
        }
        setTemplates(response || []);
        setTemplatesError(null);
      } catch (error) {
        if (cancelled) {
          return;
        }
        setTemplates([]);
        setTemplatesError(extractErrorMessage(error) || 'Failed to load governance templates');
      } finally {
        if (!cancelled) {
          setTemplatesLoading(false);
        }
      }
    };

    void loadTemplates();
    return () => {
      cancelled = true;
    };
  }, []);

  const handleSearch = useCallback(async (
    newPage = 1,
    options?: {
      queryOverride?: string;
      previewStatuses?: string[];
      lockStateOverride?: LockSearchState;
      lockOwnerOverride?: string;
      checkoutStateOverride?: CheckoutSearchState;
      checkoutUserOverride?: string;
      recordOnlyOverride?: boolean;
      recordCategoryPathsOverride?: string[];
      dateRangeOverride?: 'all' | 'today' | 'week' | 'month';
      mimeTypesOverride?: string[];
      creatorsOverride?: string[];
      tagsOverride?: string[];
      categoriesOverride?: string[];
      minSizeOverride?: number;
      maxSizeOverride?: number;
    }
  ) => {
    try {
      setLoading(true);
      setSearchError(null);
      const effectiveQuery = options?.queryOverride ?? query;
      const effectivePreviewStatuses = options?.previewStatuses ?? selectedPreviewStatuses;
      const effectiveLockState = options?.lockStateOverride ?? lockState;
      const effectiveLockOwner = options?.lockOwnerOverride ?? lockOwner;
      const effectiveCheckoutState = options?.checkoutStateOverride ?? checkoutState;
      const effectiveCheckoutUser = options?.checkoutUserOverride ?? checkoutUser;
      const effectiveRecordOnly = options?.recordOnlyOverride ?? recordOnly;
      const effectiveRecordCategoryPaths = options?.recordCategoryPathsOverride ?? selectedRecordCategoryPaths;
      const effectiveDateRange = options?.dateRangeOverride ?? dateRange;
      const effectiveMimeTypes = options?.mimeTypesOverride ?? selectedMimeTypes;
      const effectiveCreators = options?.creatorsOverride ?? selectedCreators;
      const effectiveTags = options?.tagsOverride ?? selectedTags;
      const effectiveCategories = options?.categoriesOverride ?? selectedCategories;
      const effectiveMinSize = options?.minSizeOverride ?? minSize;
      const effectiveMaxSize = options?.maxSizeOverride ?? maxSize;
      const effectiveCriteria: AdvancedSearchFilterState = {
        query: effectiveQuery,
        previewStatuses: effectivePreviewStatuses,
        lockState: effectiveLockState,
        lockOwner: effectiveLockOwner,
        checkoutState: effectiveCheckoutState,
        checkoutUser: effectiveCheckoutUser,
        recordOnly: effectiveRecordOnly,
        recordCategoryPaths: effectiveRecordCategoryPaths,
        dateRange: effectiveDateRange,
        mimeTypes: effectiveMimeTypes,
        creators: effectiveCreators,
        tags: effectiveTags,
        categories: effectiveCategories,
        minSize: effectiveMinSize,
        maxSize: effectiveMaxSize,
      };
      const effectiveCriteriaKey = buildAdvancedSearchCriteriaKey(effectiveCriteria);
      const effectiveHasCriteria = hasActiveAdvancedSearchCriteria(effectiveCriteria);
      setCurrentCriteriaKey(effectiveCriteriaKey);
      setCurrentCriteriaHasFilters(effectiveHasCriteria);
      const searchCriteria = buildSearchCriteriaFromAdvancedState(effectiveCriteria, newPage, 10);

      const response = await nodeService.searchNodesEnvelope(searchCriteria, {
        includeFacets: true,
        includeStats: true,
        includePivot: true,
      });

      const mappedResults: SearchResult[] = response.nodes.map((node) => ({
        id: node.id,
        name: node.name,
        mimeType: node.contentType || '',
        fileSize: node.size || 0,
        checkedOut: node.checkedOut,
        checkoutUser: node.checkoutUser,
        locked: node.locked,
        lockedBy: node.lockedBy,
        createdBy: node.creator,
        highlights: node.highlights,
        matchFields: node.matchFields,
        highlightSummary: node.highlightSummary,
        score: node.score ?? 0,
        createdDate: node.created || new Date().toISOString(),
        path: node.path,
        nodeType: node.nodeType,
        parentId: node.parentId,
        previewStatus: node.previewStatus,
        previewFailureReason: node.previewFailureReason,
        previewFailureCategory: node.previewFailureCategory,
      }));

      setResults(mappedResults);
      if (mappedResults.length > 0) {
        setFallbackResults(mappedResults);
        setFallbackLabel(effectiveQuery.trim());
        setFallbackCriteriaKey(effectiveCriteriaKey);
      }
      setTotalResults(response.total);
      setTotalPages(Math.ceil(response.total / 10));
      setFacets(normalizeSearchFacets(response.facets));
      setSearchStats(response.stats || null);
      setSearchPivotStats(response.pivot || null);
      setPage(newPage);
      syncSearchStateToUrl({
        query: effectiveQuery,
        page: newPage,
        previewStatuses: effectivePreviewStatuses,
        lockState: effectiveLockState,
        lockOwner: effectiveLockOwner,
        checkoutState: effectiveCheckoutState,
        checkoutUser: effectiveCheckoutUser,
        recordOnly: effectiveRecordOnly,
        recordCategoryPaths: effectiveRecordCategoryPaths,
        dateRange: effectiveDateRange,
        mimeTypes: effectiveMimeTypes,
        creators: effectiveCreators,
        tags: effectiveTags,
        categories: effectiveCategories,
        minSize: effectiveMinSize,
        maxSize: effectiveMaxSize,
      });
      setPreviewQueueStatusById((prev) => {
        const next: Record<string, PreviewQueueUiStatus> = {};
        mappedResults.forEach((result) => {
          if (prev[result.id]) {
            next[result.id] = prev[result.id];
          }
        });
        return next;
      });

    } catch (error) {
      const recovery = buildSearchErrorRecovery(error, 'Search failed');
      setSearchError(recovery);
      setSearchStats(null);
      setSearchPivotStats(null);
      toast.error(recovery.message);
    } finally {
      setLoading(false);
    }
  }, [
    dateRange,
    lockOwner,
    lockState,
    checkoutState,
    checkoutUser,
    maxSize,
    minSize,
    query,
    recordOnly,
    selectedRecordCategoryPaths,
    selectedCategories,
    selectedCreators,
    selectedMimeTypes,
    selectedPreviewStatuses,
    selectedTags,
    syncSearchStateToUrl,
  ]);

  const handleApplyTemplate = useCallback((template: SavedSearchTemplate) => {
    const criteria = resolveTemplateQueryState(template.queryParams);
    setQuery(criteria.query);
    setSelectedPreviewStatuses(criteria.previewStatuses);
    setLockState(criteria.lockState);
    setLockOwner(criteria.lockOwner);
    setCheckoutState(criteria.checkoutState);
    setCheckoutUser(criteria.checkoutUser);
    setRecordOnly(criteria.recordOnly);
    setSelectedRecordCategoryPaths(criteria.recordCategoryPaths);
    setDateRange(criteria.dateRange);
    setSelectedMimeTypes(criteria.mimeTypes);
    setSelectedCreators(criteria.creators);
    setSelectedTags(criteria.tags);
    setSelectedCategories(criteria.categories);
    setMinSize(criteria.minSize);
    setMaxSize(criteria.maxSize);

    void handleSearch(1, {
      queryOverride: criteria.query,
      previewStatuses: criteria.previewStatuses,
      lockStateOverride: criteria.lockState,
      lockOwnerOverride: criteria.lockOwner,
      checkoutStateOverride: criteria.checkoutState,
      checkoutUserOverride: criteria.checkoutUser,
      recordOnlyOverride: criteria.recordOnly,
      recordCategoryPathsOverride: criteria.recordCategoryPaths,
      dateRangeOverride: criteria.dateRange,
      mimeTypesOverride: criteria.mimeTypes,
      creatorsOverride: criteria.creators,
      tagsOverride: criteria.tags,
      categoriesOverride: criteria.categories,
      minSizeOverride: criteria.minSize,
      maxSizeOverride: criteria.maxSize,
    });

    toast.success(`Template applied: ${template.name}`);
  }, [handleSearch]);

  const fallbackCriteriaMatches = Boolean(currentCriteriaKey) && currentCriteriaKey === fallbackCriteriaKey;
  const fallbackHiddenForCriteria = Boolean(currentCriteriaKey) && currentCriteriaKey === dismissedFallbackCriteriaKey;
  const fallbackForcedForCriteria = Boolean(currentCriteriaKey) && currentCriteriaKey === forcedFallbackCriteriaKey;
  const fallbackSuppressionQueryLabel = query.trim() || fallbackLabel;
  const shouldEvaluateFallback = !loading
    && results.length === 0
    && fallbackResults.length > 0
    && currentCriteriaHasFilters
    && fallbackCriteriaMatches
    && !fallbackHiddenForCriteria;
  const shouldSuppressFallbackForQuery = shouldEvaluateFallback
    && !fallbackForcedForCriteria
    && shouldSuppressStaleFallbackForQuery(fallbackSuppressionQueryLabel);
  const shouldShowFallback = shouldEvaluateFallback && !shouldSuppressFallbackForQuery;
  const shouldShowSuppressedFallbackNotice = shouldEvaluateFallback && shouldSuppressFallbackForQuery;
  const shouldRunFallbackAutoRetry = shouldShowFallback || shouldShowSuppressedFallbackNotice;
  const displayResults = shouldShowFallback ? fallbackResults : results;

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

  useEffect(() => {
    if (!dismissedFallbackCriteriaKey) {
      return;
    }
    if (!currentCriteriaKey || currentCriteriaKey !== dismissedFallbackCriteriaKey) {
      setDismissedFallbackCriteriaKey('');
    }
  }, [currentCriteriaKey, dismissedFallbackCriteriaKey]);

  useEffect(() => {
    if (!forcedFallbackCriteriaKey) {
      return;
    }
    if (!currentCriteriaKey || currentCriteriaKey !== forcedFallbackCriteriaKey) {
      setForcedFallbackCriteriaKey('');
    }
  }, [currentCriteriaKey, forcedFallbackCriteriaKey]);

  useEffect(() => {
    if (fallbackAutoRetryTimerRef.current !== null) {
      window.clearTimeout(fallbackAutoRetryTimerRef.current);
      fallbackAutoRetryTimerRef.current = null;
    }
    if (!shouldRunFallbackAutoRetry) {
      return;
    }
    if (fallbackAutoRetryCount >= FALLBACK_AUTO_RETRY_MAX) {
      return;
    }

    const nextDelayMs = getFallbackAutoRetryDelayMs(fallbackAutoRetryCount);
    fallbackAutoRetryTimerRef.current = window.setTimeout(() => {
      setFallbackAutoRetryCount((prev) => prev + 1);
      setFallbackLastRetryAt(new Date());
      void handleSearch(page);
    }, nextDelayMs);

    return () => {
      if (fallbackAutoRetryTimerRef.current !== null) {
        window.clearTimeout(fallbackAutoRetryTimerRef.current);
        fallbackAutoRetryTimerRef.current = null;
      }
    };
  }, [shouldRunFallbackAutoRetry, fallbackAutoRetryCount, handleSearch, page]);

  const fallbackAutoRetryNextDelayMs = shouldRunFallbackAutoRetry && fallbackAutoRetryCount < FALLBACK_AUTO_RETRY_MAX
    ? getFallbackAutoRetryDelayMs(fallbackAutoRetryCount)
    : null;

  const handleRetrySearch = useCallback(() => {
    setFallbackLastRetryAt(new Date());
    void handleSearch(page);
  }, [handleSearch, page]);

  const handleGoHome = useCallback(() => {
    navigate('/browse/root');
    if (sidebarAutoCollapse) {
      dispatch(setSidebarOpen(false));
    }
  }, [dispatch, navigate, sidebarAutoCollapse]);

  const handleHideFallbackResults = useCallback(() => {
    if (!currentCriteriaKey) {
      return;
    }
    setDismissedFallbackCriteriaKey(currentCriteriaKey);
    setForcedFallbackCriteriaKey('');
    setFallbackAutoRetryCount(0);
    setFallbackLastRetryAt(null);
  }, [currentCriteriaKey]);

  const handleShowFallbackResults = useCallback(() => {
    if (!currentCriteriaKey) {
      return;
    }
    setForcedFallbackCriteriaKey(currentCriteriaKey);
    setDismissedFallbackCriteriaKey('');
  }, [currentCriteriaKey]);

  useEffect(() => {
    if (initializedFromUrlRef.current) {
      return;
    }
    initializedFromUrlRef.current = true;
    const restoredState = parseAdvancedSearchUrlState(location.search);

    setQuery(restoredState.query);
    setSelectedPreviewStatuses(restoredState.previewStatuses);
    setLockState(restoredState.lockState);
    setLockOwner(restoredState.lockOwner);
    setCheckoutState(restoredState.checkoutState);
   setCheckoutUser(restoredState.checkoutUser);
    setRecordOnly(restoredState.recordOnly);
    setSelectedRecordCategoryPaths(restoredState.recordCategoryPaths);
    setDateRange(restoredState.dateRange);
    setSelectedMimeTypes(restoredState.mimeTypes);
    setSelectedCreators(restoredState.creators);
    setSelectedTags(restoredState.tags);
    setSelectedCategories(restoredState.categories);
    setMinSize(restoredState.minSize);
    setMaxSize(restoredState.maxSize);

    if (hasRestorableAdvancedSearchState(restoredState)) {
      void handleSearch(restoredState.page, {
        queryOverride: restoredState.query,
        previewStatuses: restoredState.previewStatuses,
        lockStateOverride: restoredState.lockState,
        lockOwnerOverride: restoredState.lockOwner,
        checkoutStateOverride: restoredState.checkoutState,
        checkoutUserOverride: restoredState.checkoutUser,
        recordOnlyOverride: restoredState.recordOnly,
        recordCategoryPathsOverride: restoredState.recordCategoryPaths,
        dateRangeOverride: restoredState.dateRange,
        mimeTypesOverride: restoredState.mimeTypes,
        creatorsOverride: restoredState.creators,
        tagsOverride: restoredState.tags,
        categoriesOverride: restoredState.categories,
        minSizeOverride: restoredState.minSize,
        maxSizeOverride: restoredState.maxSize,
      });
    }
  }, [handleSearch, location.search]);

  const handlePageChange = (event: React.ChangeEvent<unknown>, value: number) => {
    handleSearch(value);
  };

  const previewStatusCounts = useMemo(() => {
    const base = {
      READY: 0,
      PROCESSING: 0,
      QUEUED: 0,
      FAILED: 0,
      UNSUPPORTED: 0,
      PENDING: 0,
    };

    const hasPreviewQueueOverridesInDisplay = displayResults.some((result) => Boolean(previewQueueStatusById[result.id]));
    if (!hasPreviewQueueOverridesInDisplay && facets?.previewStatus && facets.previewStatus.length > 0) {
      for (const facet of facets.previewStatus) {
        const key = (facet.value || '').toUpperCase();
        if (key in base) {
          base[key as keyof typeof base] = facet.count;
        }
      }
      return base;
    }

    return displayResults.reduce((acc, result) => {
      if (result.nodeType === 'FOLDER') {
        return acc;
      }
      const queueStatus = previewQueueStatusById[result.id];
      const status = getEffectivePreviewStatus(
        queueStatus?.previewStatus ?? result.previewStatus,
        queueStatus?.previewFailureCategory ?? result.previewFailureCategory,
        result.mimeType,
        queueStatus?.previewFailureReason ?? result.previewFailureReason
      );
      if (status in acc) {
        acc[status as keyof typeof acc] += 1;
      } else {
        acc.PENDING += 1;
      }
      return acc;
    }, base);
  }, [displayResults, facets, previewQueueStatusById]);

  const previewAwareDisplayResults = useMemo(
    () => displayResults.map((result) => {
      if (result.nodeType === 'FOLDER') {
        return result;
      }
      const queueStatus = previewQueueStatusById[result.id];
      if (!queueStatus) {
        return result;
      }
      return {
        ...result,
        previewStatus: queueStatus.previewStatus ?? result.previewStatus,
        previewFailureReason: queueStatus.previewFailureReason ?? result.previewFailureReason,
        previewFailureCategory: queueStatus.previewFailureCategory ?? result.previewFailureCategory,
      };
    }),
    [displayResults, previewQueueStatusById]
  );

  const statsPreviewStatusTop = useMemo(
    () => (searchStats?.previewStatusStats || []).slice(0, 6),
    [searchStats]
  );
  const statsMimeTypeTop = useMemo(
    () => (searchStats?.mimeTypeStats || []).slice(0, 4),
    [searchStats]
  );
  const statsCreatedByTop = useMemo(
    () => (searchStats?.createdByStats || []).slice(0, 4),
    [searchStats]
  );
  const statsPivotCellsTop = useMemo(
    () =>
      (searchPivotStats?.cells || [])
        .map((cell) => ({
          rowValue: normalizePivotLabel(cell.rowValue, 'UNKNOWN'),
          columnValue: normalizePivotLabel(cell.columnValue, 'unknown'),
          count: Number(cell.count) || 0,
        }))
        .filter((cell) => cell.count > 0)
        .sort(
          (a, b) => b.count - a.count
            || a.rowValue.localeCompare(b.rowValue)
            || a.columnValue.localeCompare(b.columnValue)
        )
        .slice(0, 8),
    [searchPivotStats]
  );

  const representativeDocument = useMemo(
    () => results.find((result) => result.nodeType === 'DOCUMENT') || null,
    [results]
  );

  useEffect(() => {
    nodeRelationsSummaryRequestSeqRef.current += 1;
    const requestSeq = nodeRelationsSummaryRequestSeqRef.current;

    if (!representativeDocument?.id) {
      setNodeRelationsSummary(null);
      setNodeRelationsSummaryUnavailable(false);
      setNodeRelationsSummaryLoading(false);
      return;
    }

    let cancelled = false;
    setNodeRelationsSummaryLoading(true);
    setNodeRelationsSummaryUnavailable(false);

    const loadNodeRelationsSummary = async () => {
      try {
        const summary = await nodeService.getNodeRelationsSummary(representativeDocument.id);
        if (cancelled || requestSeq !== nodeRelationsSummaryRequestSeqRef.current) {
          return;
        }
        setNodeRelationsSummary(summary);
      } catch {
        if (cancelled || requestSeq !== nodeRelationsSummaryRequestSeqRef.current) {
          return;
        }
        setNodeRelationsSummary(null);
        setNodeRelationsSummaryUnavailable(true);
      } finally {
        if (cancelled || requestSeq !== nodeRelationsSummaryRequestSeqRef.current) {
          return;
        }
        setNodeRelationsSummaryLoading(false);
      }
    };

    void loadNodeRelationsSummary();

    return () => {
      cancelled = true;
    };
  }, [query, representativeDocument]);

  const hasNodeRelationDetails = useMemo(
    () => nodeRelationParents.length > 0
      || nodeRelationSources.length > 0
      || nodeRelationTargets.length > 0
      || nodeRelationSecondaryChildren.length > 0
      || nodeRelationSecondaryParents.length > 0
      || nodeRelationVersions.length > 0
      || Boolean(nodeRelationCheckout?.document)
      || Boolean(nodeRelationCheckoutGraph?.workingCopyNode)
      || nodeRelationRenditionDefinitions.length > 0
      || nodeRelationRenditions.length > 0,
    [
      nodeRelationParents,
      nodeRelationSources,
      nodeRelationTargets,
      nodeRelationSecondaryChildren,
      nodeRelationSecondaryParents,
      nodeRelationVersions,
      nodeRelationCheckout,
      nodeRelationCheckoutGraph,
      nodeRelationRenditionDefinitions,
      nodeRelationRenditions,
    ]
  );

  useEffect(() => {
    nodeRelationsDetailsRequestSeqRef.current += 1;
    const requestSeq = nodeRelationsDetailsRequestSeqRef.current;

    if (!representativeDocument?.id) {
      setNodeRelationParents([]);
      setNodeRelationSources([]);
      setNodeRelationTargets([]);
      setNodeRelationSecondaryChildren([]);
      setNodeRelationSecondaryParents([]);
      setNodeRelationVersions([]);
      setNodeRelationCheckout(null);
      setNodeRelationCheckoutGraph(null);
      setNodeRelationRenditionDefinitions([]);
      setNodeRelationRenditions([]);
      setNodeRelationDetailsUnavailable(false);
      setNodeRelationDetailsLoading(false);
      return;
    }

    let cancelled = false;
    setNodeRelationDetailsLoading(true);
    setNodeRelationDetailsUnavailable(false);

    const loadNodeRelationDetails = async () => {
      try {
        const [parents, sourcesPage, targetsPage, secondaryChildren, secondaryParents, versions, checkout, checkoutGraph, renditionDefinitions, renditions] = await Promise.all([
          nodeService.getNodeRelationParents(representativeDocument.id, NODE_RELATIONS_DETAILS_PAGE_SIZE),
          nodeService.getNodeRelationSources(representativeDocument.id, 0, NODE_RELATIONS_DETAILS_PAGE_SIZE),
          nodeService.getNodeRelationTargets(representativeDocument.id, 0, NODE_RELATIONS_DETAILS_PAGE_SIZE),
          nodeService.getSecondaryChildren(representativeDocument.id).catch(() => []),
          nodeService.getSecondaryParents(representativeDocument.id).catch(() => []),
          nodeService.getNodeRelationVersions(representativeDocument.id, 0, NODE_RELATIONS_DETAILS_PAGE_SIZE),
          nodeService.getNodeRelationCheckout(representativeDocument.id),
          nodeService.getNodeRelationCheckoutGraph(representativeDocument.id).catch(() => null),
          nodeService.getNodeRenditionDefinitions(representativeDocument.id).catch(() => []),
          nodeService.getNodeRelationRenditions(representativeDocument.id, 0, NODE_RELATIONS_DETAILS_PAGE_SIZE),
        ]);
        if (cancelled || requestSeq !== nodeRelationsDetailsRequestSeqRef.current) {
          return;
        }
        setNodeRelationParents(parents || []);
        setNodeRelationSources(sourcesPage.content || []);
        setNodeRelationTargets(targetsPage.content || []);
        setNodeRelationSecondaryChildren((secondaryChildren || []).slice(0, NODE_RELATIONS_DETAILS_PAGE_SIZE));
        setNodeRelationSecondaryParents((secondaryParents || []).slice(0, NODE_RELATIONS_DETAILS_PAGE_SIZE));
        setNodeRelationVersions(versions || []);
        setNodeRelationCheckout(checkout || null);
        setNodeRelationCheckoutGraph(checkoutGraph || null);
        setNodeRelationRenditionDefinitions(renditionDefinitions || []);
        setNodeRelationRenditions(renditions || []);
      } catch {
        if (cancelled || requestSeq !== nodeRelationsDetailsRequestSeqRef.current) {
          return;
        }
        setNodeRelationParents([]);
        setNodeRelationSources([]);
        setNodeRelationTargets([]);
        setNodeRelationSecondaryChildren([]);
        setNodeRelationSecondaryParents([]);
        setNodeRelationVersions([]);
        setNodeRelationCheckout(null);
        setNodeRelationCheckoutGraph(null);
        setNodeRelationRenditionDefinitions([]);
        setNodeRelationRenditions([]);
        setNodeRelationDetailsUnavailable(true);
      } finally {
        if (cancelled || requestSeq !== nodeRelationsDetailsRequestSeqRef.current) {
          return;
        }
        setNodeRelationDetailsLoading(false);
      }
    };

    void loadNodeRelationDetails();
    return () => {
      cancelled = true;
    };
  }, [query, representativeDocument]);

  const activePreviewStatusFilters = useMemo(() => {
    const fromUrl = parseAdvancedSearchUrlState(location.search).previewStatuses;
    if (fromUrl.length > 0) {
      return fromUrl;
    }
    return selectedPreviewStatuses;
  }, [location.search, selectedPreviewStatuses]);

  const previewIssueScopeResults = useMemo(
    () => previewAwareDisplayResults.filter((result) => {
      if (result.nodeType === 'FOLDER') {
        return false;
      }
      if (activePreviewStatusFilters.length === 0) {
        return true;
      }
      const effectiveStatus = getEffectivePreviewStatus(
        result.previewStatus,
        result.previewFailureCategory,
        result.mimeType,
        result.previewFailureReason
      );
      return activePreviewStatusFilters.includes(effectiveStatus);
    }),
    [activePreviewStatusFilters, previewAwareDisplayResults]
  );

  const failedPreviewResults = useMemo(
    () => previewIssueScopeResults.filter((result) =>
      (result.previewStatus || '').toUpperCase() === 'FAILED'
      && isRetryablePreviewFailure(result.previewFailureCategory, result.mimeType, result.previewFailureReason)),
    [previewIssueScopeResults]
  );

  const failedPreviewSummary = useMemo(
    () => summarizeFailedPreviews(
      previewIssueScopeResults.map((result) => ({
        previewStatus: result.previewStatus,
        previewFailureCategory: result.previewFailureCategory,
        previewFailureReason: result.previewFailureReason,
        mimeType: result.mimeType,
      }))
    ),
    [previewIssueScopeResults]
  );

  const failedPreviewReasonSummary = useMemo(() => {
    if (showAllRetryReasons) {
      return failedPreviewSummary.retryableReasons;
    }
    return failedPreviewSummary.retryableReasons.slice(0, 4);
  }, [failedPreviewSummary, showAllRetryReasons]);

  const nonRetryableFailedReasonSummary = useMemo<FailedReasonActionItem[]>(() => {
    const buckets = new Map<string, FailedReasonActionItem>();
    previewIssueScopeResults.forEach((result) => {
      if (result.nodeType === 'FOLDER') {
        return;
      }
      const effectiveStatus = getEffectivePreviewStatus(
        result.previewStatus,
        result.previewFailureCategory,
        result.mimeType,
        result.previewFailureReason
      );
      if (effectiveStatus !== 'FAILED' && effectiveStatus !== 'UNSUPPORTED') {
        return;
      }

      const retryable = isRetryablePreviewFailure(
        result.previewFailureCategory,
        result.mimeType,
        result.previewFailureReason
      );
      if (retryable) {
        return;
      }

      const normalizedCategory = effectiveStatus === 'UNSUPPORTED'
        ? 'UNSUPPORTED'
        : (result.previewFailureCategory || 'PERMANENT').trim().toUpperCase() || 'PERMANENT';
      const normalizedReason = normalizePreviewFailureReason(result.previewFailureReason);
      const bucketKey = `${normalizedReason}::${normalizedCategory}`;
      const current = buckets.get(bucketKey);
      if (current) {
        current.count += 1;
      } else {
        buckets.set(bucketKey, {
          reason: normalizedReason,
          count: 1,
          category: normalizedCategory,
          retryable: false,
        });
      }
    });
    return Array.from(buckets.values())
      .sort((a, b) => b.count - a.count || a.reason.localeCompare(b.reason) || a.category.localeCompare(b.category));
  }, [previewIssueScopeResults]);

  const nonRetryablePreviewReasonSummary = useMemo(() => {
    if (showAllNonRetryReasons) {
      return nonRetryableFailedReasonSummary;
    }
    return nonRetryableFailedReasonSummary.slice(0, 4);
  }, [nonRetryableFailedReasonSummary, showAllNonRetryReasons]);

  const nonRetryablePreviewSummaryMessage = useMemo(
    () => buildNonRetryablePreviewSummaryMessage(failedPreviewSummary),
    [failedPreviewSummary]
  );

  const previewRetrySummary = useMemo(() => {
    const entries = Object.values(previewQueueStatusById);
    if (entries.length === 0) {
      return null;
    }
    const times = entries
      .map((entry) => entry.nextAttemptAt)
      .filter((value): value is string => Boolean(value))
      .map((value) => new Date(value))
      .filter((value) => !Number.isNaN(value.getTime()));
    if (times.length === 0) {
      return null;
    }
    const nextAt = times.sort((a, b) => a.getTime() - b.getTime())[0];
    return {
      count: entries.length,
      nextAt,
    };
  }, [previewQueueStatusById]);

  useEffect(() => {
    if (failedPreviewSummary.retryableReasons.length <= 4 && showAllRetryReasons) {
      setShowAllRetryReasons(false);
    }
  }, [failedPreviewSummary.retryableReasons.length, showAllRetryReasons]);

  useEffect(() => {
    if (nonRetryableFailedReasonSummary.length <= 4 && showAllNonRetryReasons) {
      setShowAllNonRetryReasons(false);
    }
  }, [nonRetryableFailedReasonSummary.length, showAllNonRetryReasons]);

  useEffect(() => {
    if (failedPreviewSummary.totalFailed === 0 && (previewBatchProgress || previewBatchDryRunSummary || previewBatchReasonBreakdown || previewBatchSkipBreakdown)) {
      setPreviewBatchLabel(null);
      setPreviewBatchProgress(null);
      setPreviewBatchReasonBreakdown(null);
      setPreviewBatchSkipBreakdown(null);
      setPreviewBatchDryRunSummary(null);
      setPreviewBatchFinishedAt(null);
      setPreviewBatchExportTasks([]);
      setPreviewBatchExportTaskSummary(null);
      setPreviewBatchExportTaskStatusFilter('ALL');
    }
  }, [failedPreviewSummary.totalFailed, previewBatchDryRunSummary, previewBatchProgress, previewBatchReasonBreakdown, previewBatchSkipBreakdown]);

  const getQueueDetail = useCallback((resultId: string) => {
    const queueStatus = previewQueueStatusById[resultId];
    if (!queueStatus) {
      return null;
    }
    const previewStatusLabel = queueStatus.previewStatus
      ? `Preview status: ${queueStatus.previewStatus}`
      : null;
    const attemptsLabel = queueStatus.attempts !== undefined
      ? `Attempts: ${queueStatus.attempts}`
      : null;
    const nextLabel = queueStatus.nextAttemptAt
      ? `Next retry: ${format(new Date(queueStatus.nextAttemptAt), 'PPp')}`
      : null;
    const queueStateLabel = queueStatus.queueState
      ? `Queue state: ${queueStatus.queueState}`
      : null;
    const messageLabel = queueStatus.message
      ? `Queue message: ${queueStatus.message}`
      : null;
    const labels = [previewStatusLabel, queueStateLabel, attemptsLabel, nextLabel, messageLabel].filter(Boolean);
    return labels.length > 0 ? labels.join(' • ') : null;
  }, [previewQueueStatusById]);

  const togglePreviewStatus = useCallback((status: string) => {
    const next = selectedPreviewStatuses.includes(status)
      ? selectedPreviewStatuses.filter((value) => value !== status)
      : [...selectedPreviewStatuses, status];
    setSelectedPreviewStatuses(next);
    syncSearchStateToUrl({
      query,
      page: 1,
      previewStatuses: next,
      lockState,
      lockOwner,
      checkoutState,
      checkoutUser,
      recordOnly,
      recordCategoryPaths: selectedRecordCategoryPaths,
      dateRange,
      mimeTypes: selectedMimeTypes,
      creators: selectedCreators,
      tags: selectedTags,
      categories: selectedCategories,
      minSize,
      maxSize,
    });
    void handleSearch(1, { previewStatuses: next });
  }, [checkoutState, checkoutUser, dateRange, handleSearch, lockOwner, lockState, maxSize, minSize, query, recordOnly, selectedCategories, selectedCreators, selectedMimeTypes, selectedPreviewStatuses, selectedRecordCategoryPaths, selectedTags, syncSearchStateToUrl]);

  const handleRetryPreview = useCallback(async (result: SearchResult, force = false) => {
    if (!result?.id || result.nodeType === 'FOLDER') {
      return;
    }
    setQueueingPreviewId(result.id);
    try {
      const status = await nodeService.queuePreview(result.id, force);
      setPreviewQueueStatusById((prev) => ({
        ...prev,
        [result.id]: buildPreviewQueueOverride(status),
      }));
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
  }, []);

  const handleCancelPreviewQueue = useCallback(async (result: SearchResult) => {
    if (!result?.id || result.nodeType === 'FOLDER') {
      return;
    }
    setCancelingPreviewId(result.id);
    try {
      const status = await nodeService.cancelQueuedPreview(result.id);
      setPreviewQueueStatusById((prev) => ({
        ...prev,
        [result.id]: {
          ...prev[result.id],
          queueState: status.queueState || (status.cancelled ? 'CANCELLED' : 'IDLE'),
          message: status.message || null,
        },
      }));
      if (status.cancelled) {
        toast.success(status.message || 'Preview queue task cancelled');
      } else {
        toast.info(status.message || 'No active preview queue task');
      }
    } catch {
      toast.error('Failed to cancel preview queue task');
    } finally {
      setCancelingPreviewId(null);
    }
  }, []);

  const handleCheckoutResult = useCallback(async (result: SearchResult) => {
    setCheckoutActionNodeId(result.id);
    try {
      await nodeService.checkoutDocument(result.id);
      toast.success('Document checked out');
      await handleSearch(page);
    } catch {
      toast.error('Failed to check out document');
    } finally {
      setCheckoutActionNodeId(null);
    }
  }, [handleSearch, page]);

  const closeCheckInDialog = useCallback(() => {
    if (checkInSubmittingNodeId) {
      return;
    }
    setCheckInDialogResult(null);
    setCheckInFile(null);
    setCheckInComment('');
    setCheckInMajorVersion(false);
    setCheckInKeepCheckedOut(false);
  }, [checkInSubmittingNodeId]);

  const openCheckInDialog = useCallback((result: SearchResult) => {
    setCheckInDialogResult(result);
    setCheckInFile(null);
    setCheckInComment('');
    setCheckInMajorVersion(false);
    setCheckInKeepCheckedOut(false);
  }, []);

  const handleCheckInResult = useCallback(async () => {
    if (!checkInDialogResult) {
      return;
    }
    if (checkInKeepCheckedOut && !checkInFile) {
      toast.error('Keep checked out requires a new version file');
      return;
    }

    setCheckInSubmittingNodeId(checkInDialogResult.id);
    try {
      await nodeService.checkinDocument(checkInDialogResult.id, {
        file: checkInFile,
        comment: checkInComment,
        majorVersion: checkInMajorVersion,
        keepCheckedOut: checkInKeepCheckedOut,
      });
      setCheckInSubmittingNodeId(null);
      toast.success(checkInFile ? 'Document checked in with new version' : 'Document checked in');
      closeCheckInDialog();
      try {
        await handleSearch(page);
      } catch {
        // Preserve the successful mutation outcome even if result refresh fails.
      }
    } catch {
      setCheckInSubmittingNodeId(null);
      toast.error('Failed to check in document');
    }
  }, [
    checkInComment,
    checkInDialogResult,
    checkInFile,
    checkInKeepCheckedOut,
    checkInMajorVersion,
    closeCheckInDialog,
    handleSearch,
    page,
  ]);

  const handleCancelCheckoutResult = useCallback(async (result: SearchResult) => {
    setCancelCheckoutActionNodeId(result.id);
    try {
      await nodeService.cancelCheckoutDocument(result.id);
      toast.success('Checkout cancelled');
      await handleSearch(page);
    } catch {
      toast.error('Failed to cancel checkout');
    } finally {
      setCancelCheckoutActionNodeId(null);
    }
  }, [handleSearch, page]);

  const mapNodeToSearchResult = useCallback((node: any): SearchResult => ({
    id: node.id,
    name: node.name || node.id,
    mimeType: node.contentType || '',
    fileSize: node.size || 0,
    checkedOut: node.checkedOut,
    checkoutUser: node.checkoutUser,
    locked: node.locked,
    lockedBy: node.lockedBy,
    createdBy: node.creator,
    highlights: node.highlights,
    matchFields: node.matchFields,
    highlightSummary: node.highlightSummary,
    score: node.score ?? 0,
    createdDate: node.created || new Date().toISOString(),
    path: node.path || '',
    nodeType: node.nodeType,
    parentId: node.parentId,
    previewStatus: node.previewStatus,
    previewFailureReason: node.previewFailureReason,
    previewFailureCategory: node.previewFailureCategory,
  }), []);

  const collectMatchedRetryableFailedTargets = useCallback(async (reason?: string) => {
    const normalizedReason = reason ? normalizePreviewFailureReason(reason) : null;
    const modifiedFrom = resolveModifiedFromDate(dateRange);
    const candidates: SearchResult[] = [];
    const seen = new Set<string>();
    let currentPage = 0;
    let total = 0;
    let scannedResults = 0;

    do {
      const response = await nodeService.searchNodes({
        name: query,
        locked: lockState === 'locked' ? true : lockState === 'unlocked' ? false : undefined,
        lockedBy: lockOwner.trim() || undefined,
        checkedOut: checkoutState === 'checkedOut' ? true : checkoutState === 'available' ? false : undefined,
        checkoutUser: checkoutUser.trim() || undefined,
        recordOnly: recordOnly || undefined,
        recordCategoryPaths: selectedRecordCategoryPaths.length > 0 ? selectedRecordCategoryPaths : undefined,
        mimeTypes: selectedMimeTypes.length > 0 ? selectedMimeTypes : undefined,
        createdByList: selectedCreators.length > 0 ? selectedCreators : undefined,
        tags: selectedTags.length > 0 ? selectedTags : undefined,
        categories: selectedCategories.length > 0 ? selectedCategories : undefined,
        previewStatuses: ['FAILED'],
        modifiedFrom,
        minSize,
        maxSize,
        page: currentPage,
        size: PREVIEW_BATCH_MATCHED_PAGE_SIZE,
      });

      total = response.total || 0;
      if (!response.nodes || response.nodes.length === 0) {
        break;
      }

      for (const node of response.nodes) {
        scannedResults += 1;
        if (!node?.id || node.nodeType === 'FOLDER' || seen.has(node.id)) {
          continue;
        }
        if (!isRetryablePreviewFailure(node.previewFailureCategory, node.contentType, node.previewFailureReason)) {
          continue;
        }
        if (normalizedReason !== null && normalizePreviewFailureReason(node.previewFailureReason) !== normalizedReason) {
          continue;
        }
        seen.add(node.id);
        candidates.push(mapNodeToSearchResult(node));
        if (candidates.length >= previewBatchMaxDocuments) {
          break;
        }
      }

      currentPage += 1;
    } while (candidates.length < previewBatchMaxDocuments && currentPage * PREVIEW_BATCH_MATCHED_PAGE_SIZE < total);

    return {
      targets: candidates,
      totalMatched: total,
      truncated: candidates.length >= previewBatchMaxDocuments,
      scannedPages: currentPage,
      scannedResults,
    };
  }, [
    checkoutState,
    checkoutUser,
    recordOnly,
    selectedRecordCategoryPaths,
    dateRange,
    lockOwner,
    lockState,
    mapNodeToSearchResult,
    maxSize,
    minSize,
    query,
    selectedCategories,
    selectedCreators,
    selectedMimeTypes,
    selectedTags,
    previewBatchMaxDocuments,
  ]);

  const applySearchScopeBatchResult = useCallback((
    response: {
      queued: number;
      skipped: number;
      failed: number;
      requested: number;
      deduplicated: number;
      truncated: boolean;
      scanned: number;
      matched: number;
      scanSkipped?: number;
      workerCount?: number;
      reasonBreakdown: Array<{
        reason: string;
        count: number;
      }>;
      skipBreakdown?: Array<{
        reason: string;
        count: number;
      }>;
      results: PreviewQueueSearchBatchItem[];
    },
    options?: {
      force?: boolean;
      reason?: string;
    }
  ) => {
    const force = options?.force ?? false;
    const reason = options?.reason;
    const reasonLabel = reason ? formatPreviewFailureReasonLabel(reason) : null;
    setPreviewBatchDryRunSummary(null);

    if (response.matched === 0) {
      setPreviewBatchReasonBreakdown(null);
      setPreviewBatchSkipBreakdown(response.skipBreakdown || []);
      toast.info(reasonLabel
        ? `No retryable failed previews found for reason: ${reasonLabel}`
        : 'No retryable failed previews found in all matched results');
      return;
    }

    if (response.truncated) {
      toast.info(`All-matched scan reached cap ${previewBatchMaxDocuments}. Running batch on ${response.deduplicated}.`);
    }

    setPreviewBatchLabel(
      `${force ? 'Force rebuild' : 'Retry'} failed previews${reasonLabel ? ` (${reasonLabel})` : ''} [all matched, scanned ${response.scanned}, skipped ${Math.max(0, Number(response.scanSkipped) || 0)}, workers ${Math.max(1, Number(response.workerCount) || previewBatchWorkerCount)}]`
    );
    setPreviewBatchReasonBreakdown(response.reasonBreakdown || []);
    setPreviewBatchSkipBreakdown(response.skipBreakdown || []);
    setPreviewBatchProgress({
      processed: response.deduplicated,
      total: response.requested,
      queued: response.queued,
      skipped: response.skipped,
      failed: response.failed,
    });
    setPreviewBatchFinishedAt(new Date());

    setPreviewQueueStatusById((prev) => {
      return applyPreviewQueueSearchBatchResultToOverrides(prev, response.results);
    });

    const parts = [`queued ${response.queued}`];
    if (response.skipped > 0) parts.push(`skipped ${response.skipped}`);
    if (response.failed > 0) parts.push(`failed ${response.failed}`);
    const toastMessage = `Preview ${force ? 'rebuilds' : 'retries'}${reasonLabel ? ` (${reasonLabel})` : ''} [all matched]: ${parts.join(', ')}`;
    if (response.failed > 0) {
      toast.warning(toastMessage);
      return;
    }
    toast.success(toastMessage);
  }, [previewBatchMaxDocuments, previewBatchWorkerCount]);

  const applySearchScopeDryRunResult = useCallback((
    response: {
      query: string | null;
      reason: string | null;
      maxDocuments: number;
      totalCandidates: number;
      scanned: number;
      matched: number;
      scanSkipped?: number;
      truncated: boolean;
      reasonBreakdown: Array<{
        reason: string;
        count: number;
      }>;
      skipBreakdown?: Array<{
        reason: string;
        count: number;
      }>;
      workerCount?: number;
      sampleCount: number;
      samples: Array<{
        documentId: string | null;
        name: string | null;
        previewStatus: string | null;
        previewFailureReason: string | null;
        previewFailureCategory: string | null;
      }>;
    },
    options?: {
      reason?: string;
    }
  ) => {
    const reason = options?.reason;
    const reasonLabel = reason ? formatPreviewFailureReasonLabel(reason) : null;

    setPreviewBatchLabel(null);
    setPreviewBatchProgress(null);
    setPreviewBatchReasonBreakdown(null);
    setPreviewBatchSkipBreakdown(response.skipBreakdown || []);
    setPreviewBatchFinishedAt(null);
    setPreviewBatchDryRunSummary({
      query: response.query ?? (query.trim() || null),
      reason: response.reason ?? reason ?? null,
      maxDocuments: response.maxDocuments,
      totalCandidates: response.totalCandidates,
      scanned: response.scanned,
      matched: response.matched,
      scanSkipped: Math.max(0, Number(response.scanSkipped) || 0),
      truncated: response.truncated,
      workerCount: Math.max(1, Number(response.workerCount) || previewBatchWorkerCount),
      reasonBreakdown: response.reasonBreakdown || [],
      sampleCount: response.sampleCount,
      samples: response.samples || [],
      finishedAt: new Date(),
    });

    if (response.matched === 0) {
      toast.info(reasonLabel
        ? `No retryable failed previews found for reason: ${reasonLabel}`
        : 'No retryable failed previews found in all matched results');
      return;
    }

    const summaryMessage = [
      `matched ${response.matched}`,
      `sampled ${response.sampleCount}`,
      `scanned ${response.scanned}`,
      `skipped ${Math.max(0, Number(response.scanSkipped) || 0)}`,
    ].join(', ');
    if (response.truncated) {
      toast.info(`Dry-run reached cap ${previewBatchMaxDocuments}: ${summaryMessage}`);
      return;
    }
    toast.info(`Dry-run summary (workers ${Math.max(1, Number(response.workerCount) || previewBatchWorkerCount)}): ${summaryMessage}`);
  }, [previewBatchMaxDocuments, previewBatchWorkerCount, query]);

  const runPreviewBatchAction = useCallback(async (
    targets: SearchResult[],
    options?: {
      force?: boolean;
      reason?: string;
      scopeLabel?: string;
    }
  ) => {
    if (targets.length === 0) {
      setPreviewBatchReasonBreakdown(null);
      setPreviewBatchSkipBreakdown(null);
      return;
    }

    const force = options?.force ?? false;
    const reason = options?.reason;
    const scopeLabel = options?.scopeLabel;
    const reasonLabel = reason ? formatPreviewFailureReasonLabel(reason) : null;
    const actionLabel = force ? 'Force rebuild' : 'Retry';
    const baseBatchLabel = reasonLabel
      ? `${actionLabel} failed previews (${reasonLabel})`
      : `${actionLabel} failed previews`;
    const batchLabel = scopeLabel ? `${baseBatchLabel} [${scopeLabel}]` : baseBatchLabel;
    const reasonBuckets = new Map<string, number>();
    targets.forEach((target) => {
      const normalizedReason = normalizePreviewFailureReason(target.previewFailureReason);
      reasonBuckets.set(normalizedReason, (reasonBuckets.get(normalizedReason) || 0) + 1);
    });
    const reasonBreakdown = Array.from(reasonBuckets.entries())
      .map(([reasonBucket, count]) => ({ reason: reasonBucket, count }))
      .sort((a, b) => b.count - a.count || a.reason.localeCompare(b.reason));

    const total = targets.length;
    let queued = 0;
    let skipped = 0;
    let failed = 0;

    setBatchRetrying(true);
    setPreviewBatchDryRunSummary(null);
    setPreviewBatchReasonBreakdown(reasonBreakdown);
    setPreviewBatchSkipBreakdown(null);
    setPreviewBatchLabel(batchLabel);
    setPreviewBatchFinishedAt(null);
    setPreviewBatchProgress({
      processed: 0,
      total,
      queued: 0,
      skipped: 0,
      failed: 0,
    });

    try {
      for (const [index, result] of targets.entries()) {
        try {
      const status = await nodeService.queuePreview(result.id, force);
      setPreviewQueueStatusById((prev) => ({
        ...prev,
        [result.id]: buildPreviewQueueOverride(status),
      }));
          if (status?.queued) {
            queued += 1;
          } else {
            skipped += 1;
          }
        } catch {
          failed += 1;
        }

        setPreviewBatchProgress({
          processed: index + 1,
          total,
          queued,
          skipped,
          failed,
        });
      }
    } finally {
      setBatchRetrying(false);
      setPreviewBatchFinishedAt(new Date());
    }

    const parts = [`queued ${queued}`];
    if (skipped > 0) parts.push(`skipped ${skipped}`);
    if (failed > 0) parts.push(`failed ${failed}`);
    const toastMessage = `Preview ${force ? 'rebuilds' : 'retries'}${reasonLabel ? ` (${reasonLabel})` : ''}${scopeLabel ? ` [${scopeLabel}]` : ''}: ${parts.join(', ')}`;
    if (failed > 0) {
      toast.warning(toastMessage);
      return;
    }
    toast.success(toastMessage);
  }, []);

  const handleDryRunFailedPreviewsAllMatched = useCallback(async (reason?: string) => {
    setBatchTargetResolving(true);
    setBatchDryRunning(true);
    try {
      const response = await nodeService.dryRunFailedPreviewsBySearch({
        query,
        filters: {
          locked: lockState === 'locked' ? true : lockState === 'unlocked' ? false : undefined,
          lockedBy: lockOwner.trim() || undefined,
          checkedOut: checkoutState === 'checkedOut' ? true : checkoutState === 'available' ? false : undefined,
          checkoutUser: checkoutUser.trim() || undefined,
          recordOnly: recordOnly || undefined,
          recordCategoryPaths: selectedRecordCategoryPaths.length > 0 ? selectedRecordCategoryPaths : undefined,
          mimeTypes: selectedMimeTypes.length > 0 ? selectedMimeTypes : undefined,
          createdByList: selectedCreators.length > 0 ? selectedCreators : undefined,
          tags: selectedTags.length > 0 ? selectedTags : undefined,
          categories: selectedCategories.length > 0 ? selectedCategories : undefined,
          modifiedFrom: resolveModifiedFromDate(dateRange),
          minSize,
          maxSize,
          previewStatuses: ['FAILED'],
        },
        reason,
        maxDocuments: previewBatchMaxDocuments,
        workerCount: previewBatchWorkerCount,
      });
      applySearchScopeDryRunResult(response, { reason });
    } catch {
      try {
        const scope = await collectMatchedRetryableFailedTargets(reason);
        const samples = scope.targets.slice(0, 20).map((target) => ({
          documentId: target.id,
          name: target.name || null,
          previewStatus: target.previewStatus || null,
          previewFailureReason: target.previewFailureReason || null,
          previewFailureCategory: target.previewFailureCategory || null,
        }));
        const reasonBuckets = new Map<string, number>();
        scope.targets.forEach((target) => {
          const normalizedReason = normalizePreviewFailureReason(target.previewFailureReason);
          reasonBuckets.set(normalizedReason, (reasonBuckets.get(normalizedReason) || 0) + 1);
        });
        const reasonBreakdown = Array.from(reasonBuckets.entries())
          .map(([reasonBucket, count]) => ({ reason: reasonBucket, count }))
          .sort((a, b) => b.count - a.count || a.reason.localeCompare(b.reason));
        applySearchScopeDryRunResult({
          query: query.trim() || null,
          reason: reason || null,
          maxDocuments: previewBatchMaxDocuments,
          totalCandidates: scope.totalMatched,
          scanned: scope.scannedResults,
          matched: scope.targets.length,
          scanSkipped: 0,
          truncated: scope.truncated,
          reasonBreakdown,
          skipBreakdown: [],
          workerCount: previewBatchWorkerCount,
          sampleCount: samples.length,
          samples,
        }, { reason });
      } catch {
        toast.error(reason
          ? 'Failed to dry-run matched failed previews for selected reason'
          : 'Failed to dry-run matched failed previews');
      }
    } finally {
      setBatchDryRunning(false);
      setBatchTargetResolving(false);
    }
  }, [
    applySearchScopeDryRunResult,
    collectMatchedRetryableFailedTargets,
    checkoutState,
    checkoutUser,
    dateRange,
    lockOwner,
    lockState,
    maxSize,
    minSize,
    query,
    recordOnly,
    selectedRecordCategoryPaths,
    selectedCategories,
    selectedCreators,
    selectedMimeTypes,
    selectedTags,
    previewBatchMaxDocuments,
    previewBatchWorkerCount,
  ]);

  const loadPreviewBatchExportTasks = useCallback(async (silent = false) => {
    const statusFilter = previewBatchExportTaskStatusFilter === 'ALL'
      ? undefined
      : previewBatchExportTaskStatusFilter;
    setPreviewBatchExportTasksLoading(true);
    try {
      const [response, summary] = await Promise.all([
        nodeService.listDryRunFailedPreviewsCsvExportAsyncBySearchTasks(20, statusFilter),
        nodeService.getDryRunFailedPreviewsCsvExportAsyncBySearchTasksSummary(statusFilter),
      ]);
      const items = (response.items || []).map((item) => ({
        taskId: item.taskId,
        status: normalizeExportTaskStatus(item.status),
        error: item.error || item.message || null,
        createdAt: item.createdAt || null,
        finishedAt: item.finishedAt || null,
        filename: item.filename || null,
      }));
      setPreviewBatchExportTasks(items);
      setPreviewBatchExportTaskSummary({
        total: Number(summary.total || 0),
        queued: Number(summary.queued || 0),
        running: Number(summary.running || 0),
        completed: Number(summary.completed || 0),
        cancelled: Number(summary.cancelled || 0),
        failed: Number(summary.failed || 0),
        terminal: Number(summary.terminal || 0),
        active: Number(summary.active || 0),
      });
    } catch (error) {
      setPreviewBatchExportTaskSummary(null);
      if (!silent) {
        toast.error(extractErrorMessage(error) || 'Failed to load export tasks');
      }
    } finally {
      setPreviewBatchExportTasksLoading(false);
    }
  }, [previewBatchExportTaskStatusFilter]);

  const handleCleanupExportTasks = useCallback(async () => {
    const statusFilter = previewBatchExportTaskStatusFilter === 'ALL'
      ? undefined
      : previewBatchExportTaskStatusFilter;
    if (statusFilter === 'QUEUED' || statusFilter === 'RUNNING') {
      toast.info('Cleanup supports terminal statuses only: COMPLETED, CANCELLED, FAILED');
      return;
    }

    setPreviewBatchExportTasksCleanupLoading(true);
    try {
      const response = await nodeService.cleanupDryRunFailedPreviewsCsvExportAsyncBySearchTasks(statusFilter);
      if ((response.deletedCount || 0) > 0) {
        toast.success(response.message || `Deleted ${response.deletedCount} export tasks`);
      } else {
        toast.info(response.message || 'No export tasks matched cleanup filter');
      }
      await loadPreviewBatchExportTasks(true);
    } catch (error) {
      toast.error(extractErrorMessage(error) || 'Failed to cleanup export tasks');
    } finally {
      setPreviewBatchExportTasksCleanupLoading(false);
    }
  }, [loadPreviewBatchExportTasks, previewBatchExportTaskStatusFilter]);

  const handleCancelActiveExportTasks = useCallback(async () => {
    setPreviewBatchExportTasksCancelActiveLoading(true);
    try {
      const statusFilter: PreviewQueueSearchDryRunExportAsyncTaskActiveStatusFilter | undefined = (
        previewBatchExportTaskStatusFilter === 'QUEUED'
        || previewBatchExportTaskStatusFilter === 'RUNNING'
      ) ? previewBatchExportTaskStatusFilter : undefined;
      const response = await nodeService.cancelActiveDryRunFailedPreviewsCsvExportAsyncBySearchTasks(statusFilter);
      const cancelledCount = Number.isFinite(response?.cancelledCount) ? response.cancelledCount : 0;
      if (cancelledCount > 0) {
        toast.success(response?.message || `Cancelled ${cancelledCount} active export task(s)`);
      } else {
        toast.info(response?.message || 'No active export tasks matched cancel-active filters');
      }
      await loadPreviewBatchExportTasks(true);
    } catch (error) {
      toast.error(extractErrorMessage(error) || 'Failed to cancel active export tasks');
    } finally {
      setPreviewBatchExportTasksCancelActiveLoading(false);
    }
  }, [loadPreviewBatchExportTasks, previewBatchExportTaskStatusFilter]);

  useEffect(() => {
    if (!previewBatchDryRunSummary) {
      setPreviewBatchExportTasks([]);
      setPreviewBatchExportTaskSummary(null);
      return;
    }
    void loadPreviewBatchExportTasks(true);
  }, [loadPreviewBatchExportTasks, previewBatchDryRunSummary]);

  const handleExportDryRunCsv = useCallback(async () => {
    setExportingDryRunCsv(true);
    setExportDryRunCsvPhase('preparing');
    setCancelingDryRunCsv(false);
    setExportDryRunCsvTaskId(null);
    exportDryRunCsvCancelledByUserRef.current = false;
    try {
      const payload = {
        query,
        filters: {
          locked: lockState === 'locked' ? true : lockState === 'unlocked' ? false : undefined,
          lockedBy: lockOwner.trim() || undefined,
          checkedOut: checkoutState === 'checkedOut' ? true : checkoutState === 'available' ? false : undefined,
          checkoutUser: checkoutUser.trim() || undefined,
          recordOnly: recordOnly || undefined,
          recordCategoryPaths: selectedRecordCategoryPaths.length > 0 ? selectedRecordCategoryPaths : undefined,
          mimeTypes: selectedMimeTypes.length > 0 ? selectedMimeTypes : undefined,
          createdByList: selectedCreators.length > 0 ? selectedCreators : undefined,
          tags: selectedTags.length > 0 ? selectedTags : undefined,
          categories: selectedCategories.length > 0 ? selectedCategories : undefined,
          modifiedFrom: resolveModifiedFromDate(dateRange),
          minSize,
          maxSize,
          previewStatuses: ['FAILED'],
        },
        reason: previewBatchDryRunSummary?.reason || undefined,
        maxDocuments: previewBatchMaxDocuments,
        workerCount: previewBatchWorkerCount,
      };

      const startTask = await nodeService.startDryRunFailedPreviewsCsvExportAsyncBySearch(payload);
      const taskId = startTask.taskId?.trim();
      if (!taskId) {
        throw new Error(startTask.error || startTask.message || 'Failed to start dry-run CSV export task');
      }
      setExportDryRunCsvTaskId(taskId);
      void loadPreviewBatchExportTasks(true);

      let lastKnownStatus: string | null | undefined = startTask.status;
      let lastKnownError: string | null | undefined = startTask.error || startTask.message;

      for (let attempt = 0; attempt < EXPORT_DRY_RUN_CSV_POLL_MAX_ATTEMPTS; attempt += 1) {
        const taskStatus = await nodeService.getDryRunFailedPreviewsCsvExportAsyncBySearchTask(taskId);
        const taskPhase = resolveExportDryRunCsvTaskPhase(taskStatus.status);
        lastKnownStatus = taskStatus.status || lastKnownStatus;
        lastKnownError = taskStatus.error || taskStatus.message || lastKnownError;

        if (taskPhase === 'failed') {
          if ((taskStatus.status || '').trim().toUpperCase() === 'CANCELLED') {
            throw new Error('EXPORT_CANCELLED_BY_USER');
          }
          throw new Error(taskStatus.error || taskStatus.message || 'Dry-run CSV export task failed');
        }

        if (taskPhase === 'completed') {
          setExportDryRunCsvPhase('downloading');
          const blob = await nodeService.downloadDryRunFailedPreviewsCsvExportAsyncBySearch(taskId);
          const timestamp = format(new Date(), 'yyyyMMdd-HHmmss');
          const url = window.URL.createObjectURL(blob);
          const anchor = document.createElement('a');
          anchor.href = url;
          anchor.download = `search-preview-dry-run-${timestamp}.csv`;
          document.body.appendChild(anchor);
          anchor.click();
          anchor.remove();
          window.URL.revokeObjectURL(url);
          toast.success('Dry-run CSV exported');
          return;
        }

        setExportDryRunCsvPhase(taskPhase === 'running' ? 'running' : 'preparing');

        if (attempt < EXPORT_DRY_RUN_CSV_POLL_MAX_ATTEMPTS - 1) {
          await waitForDelay(EXPORT_DRY_RUN_CSV_POLL_INTERVAL_MS);
        }
      }

      throw new Error(lastKnownError || `Dry-run CSV export task timed out (${lastKnownStatus || 'UNKNOWN'})`);
    } catch (error) {
      const message = extractErrorMessage(error);
      if (exportDryRunCsvCancelledByUserRef.current || message === 'EXPORT_CANCELLED_BY_USER') {
        toast.info('Dry-run CSV export cancelled');
      } else {
        toast.error(message || 'Failed to export dry-run CSV');
      }
    } finally {
      setExportingDryRunCsv(false);
      setExportDryRunCsvPhase('idle');
      setExportDryRunCsvTaskId(null);
      setCancelingDryRunCsv(false);
      exportDryRunCsvCancelledByUserRef.current = false;
      void loadPreviewBatchExportTasks(true);
    }
  }, [
    checkoutState,
    checkoutUser,
    dateRange,
    loadPreviewBatchExportTasks,
    lockOwner,
    lockState,
    maxSize,
    minSize,
    previewBatchDryRunSummary?.reason,
    previewBatchMaxDocuments,
    previewBatchWorkerCount,
    query,
    recordOnly,
    selectedRecordCategoryPaths,
    selectedCategories,
    selectedCreators,
    selectedMimeTypes,
    selectedTags,
  ]);

  const handleCancelExportDryRunCsv = useCallback(async () => {
    if (!exportDryRunCsvTaskId || cancelingDryRunCsv) {
      return;
    }
    exportDryRunCsvCancelledByUserRef.current = true;
    setCancelingDryRunCsv(true);
    try {
      await nodeService.cancelDryRunFailedPreviewsCsvExportAsyncBySearchTask(exportDryRunCsvTaskId);
      void loadPreviewBatchExportTasks(true);
    } catch (error) {
      exportDryRunCsvCancelledByUserRef.current = false;
      toast.error(extractErrorMessage(error) || 'Failed to cancel dry-run CSV export');
    } finally {
      setCancelingDryRunCsv(false);
    }
  }, [cancelingDryRunCsv, exportDryRunCsvTaskId, loadPreviewBatchExportTasks]);

  const handleCancelExportTask = useCallback(async (taskId: string) => {
    try {
      await nodeService.cancelDryRunFailedPreviewsCsvExportAsyncBySearchTask(taskId);
      toast.info('Export task cancelled');
      await loadPreviewBatchExportTasks(true);
    } catch (error) {
      toast.error(extractErrorMessage(error) || 'Failed to cancel export task');
    }
  }, [loadPreviewBatchExportTasks]);

  const handleDownloadExportTask = useCallback(async (task: PreviewBatchExportTask) => {
    try {
      const blob = await nodeService.downloadDryRunFailedPreviewsCsvExportAsyncBySearch(task.taskId);
      const timestamp = format(new Date(), 'yyyyMMdd-HHmmss');
      const filename = task.filename || `search-preview-dry-run-${timestamp}.csv`;
      const url = window.URL.createObjectURL(blob);
      const anchor = document.createElement('a');
      anchor.href = url;
      anchor.download = filename;
      document.body.appendChild(anchor);
      anchor.click();
      anchor.remove();
      window.URL.revokeObjectURL(url);
      toast.success('Export task downloaded');
    } catch (error) {
      toast.error(extractErrorMessage(error) || 'Failed to download export task');
    }
  }, []);

  const buildReasonDeadLetterActionKey = useCallback((
    reason: string,
    action: 'replay' | 'clear',
    category: string,
    retryable: boolean | null | undefined
  ) => {
    const normalizedRetryable = retryable == null ? 'ANY' : retryable ? 'TRUE' : 'FALSE';
    return `${action}:${normalizePreviewFailureReason(reason)}:${(category || 'ANY').trim().toUpperCase()}:${normalizedRetryable}`;
  }, []);

  const handleRetryFailedPreviews = useCallback(async (force = false) => {
    await runPreviewBatchAction(failedPreviewResults, { force });
  }, [failedPreviewResults, runPreviewBatchAction]);

  const handleRetryFailedReason = useCallback(async (reason: string, force = false) => {
    const targets = failedPreviewResults.filter((result) => {
      const nodeReason = normalizePreviewFailureReason(result.previewFailureReason);
      return nodeReason === reason;
    });
    await runPreviewBatchAction(targets, { force, reason });
  }, [failedPreviewResults, runPreviewBatchAction]);

  const handleRetryFailedPreviewsAllMatched = useCallback(async (force = false) => {
    setBatchTargetResolving(true);
    setBatchRetrying(true);
    try {
      const response = await nodeService.queueFailedPreviewsBySearch({
        query,
        filters: {
          locked: lockState === 'locked' ? true : lockState === 'unlocked' ? false : undefined,
          lockedBy: lockOwner.trim() || undefined,
          checkedOut: checkoutState === 'checkedOut' ? true : checkoutState === 'available' ? false : undefined,
          checkoutUser: checkoutUser.trim() || undefined,
          recordOnly: recordOnly || undefined,
          recordCategoryPaths: selectedRecordCategoryPaths.length > 0 ? selectedRecordCategoryPaths : undefined,
          mimeTypes: selectedMimeTypes.length > 0 ? selectedMimeTypes : undefined,
          createdByList: selectedCreators.length > 0 ? selectedCreators : undefined,
          tags: selectedTags.length > 0 ? selectedTags : undefined,
          categories: selectedCategories.length > 0 ? selectedCategories : undefined,
          modifiedFrom: resolveModifiedFromDate(dateRange),
          minSize,
          maxSize,
          previewStatuses: ['FAILED'],
        },
        reason: undefined,
        maxDocuments: previewBatchMaxDocuments,
        workerCount: previewBatchWorkerCount,
        force,
      });
      applySearchScopeBatchResult(response, { force });
    } catch {
      try {
        const scope = await collectMatchedRetryableFailedTargets();
        if (scope.targets.length === 0) {
          toast.info('No retryable failed previews found in all matched results');
          return;
        }
        if (scope.truncated) {
          toast.info(`All-matched scan reached cap ${previewBatchMaxDocuments}. Running batch on first ${scope.targets.length}.`);
        }
        await runPreviewBatchAction(scope.targets, {
          force,
          scopeLabel: `all matched, pages ${scope.scannedPages}`,
        });
      } catch {
        toast.error('Failed to load matched failed previews');
      }
    } finally {
      setBatchTargetResolving(false);
      setBatchRetrying(false);
    }
  }, [
    applySearchScopeBatchResult,
    collectMatchedRetryableFailedTargets,
    checkoutState,
    checkoutUser,
    dateRange,
    lockOwner,
    lockState,
    maxSize,
    minSize,
    query,
    runPreviewBatchAction,
    recordOnly,
    selectedRecordCategoryPaths,
    selectedCategories,
    selectedCreators,
    selectedMimeTypes,
    selectedTags,
    previewBatchMaxDocuments,
    previewBatchWorkerCount,
  ]);

  const handleRetryFailedReasonAllMatched = useCallback(async (reason: string, force = false) => {
    setBatchTargetResolving(true);
    setBatchRetrying(true);
    try {
      const response = await nodeService.queueFailedPreviewsBySearch({
        query,
        filters: {
          locked: lockState === 'locked' ? true : lockState === 'unlocked' ? false : undefined,
          lockedBy: lockOwner.trim() || undefined,
          recordOnly: recordOnly || undefined,
          recordCategoryPaths: selectedRecordCategoryPaths.length > 0 ? selectedRecordCategoryPaths : undefined,
          mimeTypes: selectedMimeTypes.length > 0 ? selectedMimeTypes : undefined,
          createdByList: selectedCreators.length > 0 ? selectedCreators : undefined,
          tags: selectedTags.length > 0 ? selectedTags : undefined,
          categories: selectedCategories.length > 0 ? selectedCategories : undefined,
          modifiedFrom: resolveModifiedFromDate(dateRange),
          minSize,
          maxSize,
          previewStatuses: ['FAILED'],
        },
        reason,
        maxDocuments: previewBatchMaxDocuments,
        workerCount: previewBatchWorkerCount,
        force,
      });
      applySearchScopeBatchResult(response, { force, reason });
    } catch {
      try {
        const scope = await collectMatchedRetryableFailedTargets(reason);
        if (scope.targets.length === 0) {
          toast.info(`No retryable failed previews found for reason: ${formatPreviewFailureReasonLabel(reason)}`);
          return;
        }
        if (scope.truncated) {
          toast.info(`Reason-scope scan reached cap ${previewBatchMaxDocuments}. Running batch on first ${scope.targets.length}.`);
        }
        await runPreviewBatchAction(scope.targets, {
          force,
          reason,
          scopeLabel: `all matched, pages ${scope.scannedPages}`,
        });
      } catch {
        toast.error('Failed to load matched failed previews for selected reason');
      }
    } finally {
      setBatchTargetResolving(false);
      setBatchRetrying(false);
    }
  }, [
    applySearchScopeBatchResult,
    collectMatchedRetryableFailedTargets,
    dateRange,
    lockOwner,
    lockState,
    maxSize,
    minSize,
    query,
    runPreviewBatchAction,
    recordOnly,
    selectedRecordCategoryPaths,
    selectedCategories,
    selectedCreators,
    selectedMimeTypes,
    selectedTags,
    previewBatchMaxDocuments,
    previewBatchWorkerCount,
  ]);

  const handleReplayDeadLetterReasonAllMatched = useCallback(async (
    reason: string,
    options?: { category?: string; retryable?: boolean | null }
  ) => {
    const reasonLabel = formatPreviewFailureReasonLabel(reason);
    const category = (options?.category || 'TEMPORARY').trim().toUpperCase() || 'TEMPORARY';
    const retryable = options?.retryable ?? true;
    const actionKey = buildReasonDeadLetterActionKey(reason, 'replay', category, retryable);
    setReasonDeadLetterActionKey(actionKey);
    setBatchTargetResolving(true);
    try {
      const response = await opsRecoveryService.replayByFilter({
        domain: 'PREVIEW',
        reason,
        category,
        retryable,
        maxDocuments: previewBatchMaxDocuments,
        days: resolveWindowDaysFromDateRange(dateRange),
        force: true,
      });
      if (response.matched === 0) {
        toast.info(`No dead-letter matched for reason: ${reasonLabel}`);
      } else if (response.failed === 0 && response.skipped === 0 && response.queued > 0) {
        toast.success(`Dead-letter replay queued: ${response.queued}/${response.matched} (${reasonLabel})`);
      } else if (response.queued > 0) {
        toast.warning(`Dead-letter replay partial: matched=${response.matched}, queued=${response.queued}, skipped=${response.skipped}, failed=${response.failed}`);
      } else {
        toast.error(`Dead-letter replay failed for reason: ${reasonLabel}`);
      }
      await handleSearch(page);
    } catch (error) {
      toast.error(extractErrorMessage(error) || `Failed to replay dead-letter for reason: ${reasonLabel}`);
    } finally {
      setBatchTargetResolving(false);
    setReasonDeadLetterActionKey(null);
    }
  }, [
    buildReasonDeadLetterActionKey,
    dateRange,
    handleSearch,
    page,
    previewBatchMaxDocuments,
  ]);

  const handleClearDeadLetterReasonAllMatched = useCallback(async (
    reason: string,
    options?: { category?: string; retryable?: boolean | null }
  ) => {
    const reasonLabel = formatPreviewFailureReasonLabel(reason);
    const category = (options?.category || 'TEMPORARY').trim().toUpperCase() || 'TEMPORARY';
    const retryable = options?.retryable ?? true;
    const actionKey = buildReasonDeadLetterActionKey(reason, 'clear', category, retryable);
    setReasonDeadLetterActionKey(actionKey);
    setBatchTargetResolving(true);
    try {
      const response = await opsRecoveryService.clearByFilter({
        domain: 'PREVIEW',
        reason,
        category,
        retryable,
        maxDocuments: previewBatchMaxDocuments,
        days: resolveWindowDaysFromDateRange(dateRange),
      });
      if (response.matched === 0) {
        toast.info(`No dead-letter matched for reason: ${reasonLabel}`);
      } else if (response.failed === 0 && response.queued === response.matched) {
        toast.success(`Dead-letter clear done: ${response.queued}/${response.matched} (${reasonLabel})`);
      } else if (response.queued > 0) {
        toast.warning(`Dead-letter clear partial: matched=${response.matched}, cleared=${response.queued}, failed=${response.failed}`);
      } else {
        toast.error(`Dead-letter clear failed for reason: ${reasonLabel}`);
      }
      await handleSearch(page);
    } catch (error) {
      toast.error(extractErrorMessage(error) || `Failed to clear dead-letter for reason: ${reasonLabel}`);
    } finally {
      setBatchTargetResolving(false);
      setReasonDeadLetterActionKey(null);
    }
  }, [
    buildReasonDeadLetterActionKey,
    dateRange,
    handleSearch,
    page,
    previewBatchMaxDocuments,
  ]);

  const batchActionBusy = batchRetrying || batchTargetResolving || batchDryRunning;
  const exportDryRunCsvProgressText = formatExportDryRunCsvProgressText(exportDryRunCsvPhase);
  const previewBatchWorkerOptions = useMemo(
    () => Array.from({ length: Math.max(1, previewBatchWorkerMax) }, (_, index) => index + 1),
    [previewBatchWorkerMax]
  );

  return (
    <Box p={3} sx={{ height: 'calc(100vh - 64px)', display: 'flex', flexDirection: 'column' }}>
      <Typography variant="h4" gutterBottom>
        Advanced Search
      </Typography>

      <Paper sx={{ p: 2, mb: 3 }}>
        <Grid container spacing={2} alignItems="center">
          <Grid item xs={12} md={8}>
            <TextField
              fullWidth
              label="Search query"
              value={query}
              onChange={(e) => setQuery(e.target.value)}
              onKeyPress={(e) => e.key === 'Enter' && handleSearch(1)}
              InputProps={{
                endAdornment: <SearchIcon color="action" />
              }}
            />
          </Grid>
          <Grid item xs={12} md={4}>
            <Button
              variant="contained"
              fullWidth
              size="large"
              onClick={() => handleSearch(1)}
              startIcon={<SearchIcon />}
            >
              Search
            </Button>
          </Grid>
        </Grid>
      </Paper>

      <Grid container spacing={3} sx={{ flex: 1, overflow: 'hidden' }}>
        {/* Sidebar Filters */}
        <Grid item xs={12} md={3} sx={{ height: '100%', overflow: 'auto' }}>
          <Paper sx={{ p: 2, height: '100%' }}>
            <Box display="flex" alignItems="center" mb={2}>
              <FilterIcon sx={{ mr: 1 }} color="primary" />
              <Typography variant="h6">Filters</Typography>
            </Box>

            <Divider sx={{ my: 2 }} />

            {/* Date Filter */}
            <Typography variant="subtitle2" gutterBottom>Date Modified</Typography>
            <FormControl fullWidth size="small" sx={{ mb: 3 }}>
              <Select
                value={dateRange}
                onChange={(e) => setDateRange(e.target.value as any)}
              >
                <MenuItem value="all">Any time</MenuItem>
                <MenuItem value="today">Past 24 hours</MenuItem>
                <MenuItem value="week">Past week</MenuItem>
                <MenuItem value="month">Past month</MenuItem>
              </Select>
            </FormControl>

            {/* Mime Type Facet */}
            {facets?.mimeType && facets.mimeType.length > 0 && (
              <>
                <Typography variant="subtitle2" gutterBottom>File Type</Typography>
                <FormControl fullWidth size="small" sx={{ mb: 3 }}>
                  <Select
                    multiple
                    value={selectedMimeTypes}
                    onChange={(e) => {
                        const val = e.target.value;
                        setSelectedMimeTypes(typeof val === 'string' ? val.split(',') : val);
                    }}
                    renderValue={(selected) => (
                        <Box sx={{ display: 'flex', flexWrap: 'wrap', gap: 0.5 }}>
                        {selected.map((value) => (
                            <Chip key={value} label={value.split('/')[1]} size="small" />
                        ))}
                        </Box>
                    )}
                  >
                    {facets.mimeType.map((facet) => (
                      <MenuItem key={facet.value} value={facet.value}>
                        <Checkbox checked={selectedMimeTypes.indexOf(facet.value) > -1} />
                        <ListItemText primary={facet.value} secondary={`(${facet.count})`} />
                      </MenuItem>
                    ))}
                  </Select>
                </FormControl>
              </>
            )}

            <Typography variant="subtitle2" gutterBottom>File Size (bytes)</Typography>
            <TextField
              fullWidth
              type="number"
              label="Min size"
              size="small"
              sx={{ mb: 2 }}
              value={minSize ?? ''}
              onChange={(e) => setMinSize(e.target.value ? Number(e.target.value) : undefined)}
            />
            <TextField
              fullWidth
              type="number"
              label="Max size"
              size="small"
              sx={{ mb: 3 }}
              value={maxSize ?? ''}
              onChange={(e) => setMaxSize(e.target.value ? Number(e.target.value) : undefined)}
            />

            <Typography variant="subtitle2" gutterBottom>Lock</Typography>
            <FormControl fullWidth size="small" sx={{ mb: 2 }}>
              <Select
                value={lockState}
                onChange={(e) => setLockState(e.target.value as LockSearchState)}
              >
                <MenuItem value="all">All documents</MenuItem>
                <MenuItem value="locked">Locked</MenuItem>
                <MenuItem value="unlocked">Unlocked only</MenuItem>
              </Select>
            </FormControl>
            <TextField
              fullWidth
              label="Lock owner"
              size="small"
              sx={{ mb: 3 }}
              value={lockOwner}
              onChange={(e) => setLockOwner(e.target.value)}
              placeholder="alice"
            />

            <Typography variant="subtitle2" gutterBottom>Checkout</Typography>
            <FormControl fullWidth size="small" sx={{ mb: 2 }}>
              <Select
                value={checkoutState}
                onChange={(e) => setCheckoutState(e.target.value as CheckoutSearchState)}
              >
                <MenuItem value="all">All documents</MenuItem>
                <MenuItem value="checkedOut">Checked out</MenuItem>
                <MenuItem value="available">Available only</MenuItem>
              </Select>
            </FormControl>
            <TextField
              fullWidth
              label="Checkout user"
              size="small"
              sx={{ mb: 3 }}
              value={checkoutUser}
              onChange={(e) => setCheckoutUser(e.target.value)}
              placeholder="alice"
            />
            <FormControlLabel
              sx={{ mb: 3 }}
              control={(
                <Checkbox
                  checked={recordOnly}
                  onChange={(event) => setRecordOnly(event.target.checked)}
                />
              )}
              label="Record declarations only"
            />

            {facets?.recordCategoryPath && facets.recordCategoryPath.length > 0 && (
              <>
                <Typography variant="subtitle2" gutterBottom>Record Category</Typography>
                <FormControl fullWidth size="small" sx={{ mb: 3 }}>
                  <Select
                    multiple
                    value={selectedRecordCategoryPaths}
                    onChange={(e) => {
                      const val = e.target.value;
                      setSelectedRecordCategoryPaths(typeof val === 'string' ? val.split(',') : val);
                    }}
                    renderValue={(selected) => (
                      <Box sx={{ display: 'flex', flexWrap: 'wrap', gap: 0.5 }}>
                        {selected.map((value) => (
                          <Chip key={value} label={value} size="small" />
                        ))}
                      </Box>
                    )}
                  >
                    {facets.recordCategoryPath.map((facet) => (
                      <MenuItem key={facet.value} value={facet.value}>
                        <Checkbox checked={selectedRecordCategoryPaths.indexOf(facet.value) > -1} />
                        <ListItemText primary={facet.value} secondary={`(${facet.count})`} />
                      </MenuItem>
                    ))}
                  </Select>
                </FormControl>
              </>
            )}

            {/* Tags Facet */}
            {facets?.tags && facets.tags.length > 0 && (
              <>
                <Typography variant="subtitle2" gutterBottom>Tags</Typography>
                <FormControl fullWidth size="small" sx={{ mb: 3 }}>
                  <Select
                    multiple
                    value={selectedTags}
                    onChange={(e) => {
                      const val = e.target.value;
                      setSelectedTags(typeof val === 'string' ? val.split(',') : val);
                    }}
                    renderValue={(selected) => (
                      <Box sx={{ display: 'flex', flexWrap: 'wrap', gap: 0.5 }}>
                        {selected.map((value) => (
                          <Chip key={value} label={value} size="small" />
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
              </>
            )}

            {/* Categories Facet */}
            {facets?.categories && facets.categories.length > 0 && (
              <>
                <Typography variant="subtitle2" gutterBottom>Categories</Typography>
                <FormControl fullWidth size="small" sx={{ mb: 3 }}>
                  <Select
                    multiple
                    value={selectedCategories}
                    onChange={(e) => {
                      const val = e.target.value;
                      setSelectedCategories(typeof val === 'string' ? val.split(',') : val);
                    }}
                    renderValue={(selected) => (
                      <Box sx={{ display: 'flex', flexWrap: 'wrap', gap: 0.5 }}>
                        {selected.map((value) => (
                          <Chip key={value} label={value} size="small" />
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
              </>
            )}

            {/* Creator Facet */}
            {facets?.createdBy && facets.createdBy.length > 0 && (
              <>
                <Typography variant="subtitle2" gutterBottom>Created By</Typography>
                <FormControl fullWidth size="small" sx={{ mb: 3 }}>
                  <Select
                    multiple
                    value={selectedCreators}
                    onChange={(e) => {
                        const val = e.target.value;
                        setSelectedCreators(typeof val === 'string' ? val.split(',') : val);
                    }}
                    renderValue={(selected) => (
                        <Box sx={{ display: 'flex', flexWrap: 'wrap', gap: 0.5 }}>
                        {selected.map((value) => (
                            <Chip key={value} label={value} size="small" />
                        ))}
                        </Box>
                    )}
                  >
                    {facets.createdBy.map((facet) => (
                      <MenuItem key={facet.value} value={facet.value}>
                        <Checkbox checked={selectedCreators.indexOf(facet.value) > -1} />
                        <ListItemText primary={facet.value} secondary={`(${facet.count})`} />
                      </MenuItem>
                    ))}
                  </Select>
                </FormControl>
              </>
            )}
            
            <Button 
                variant="outlined" 
                fullWidth 
                onClick={() => handleSearch(1)}
                disabled={loading}
            >
                Apply Filters
            </Button>
          </Paper>
        </Grid>

        {/* Results Area */}
        <Grid item xs={12} md={9} sx={{ height: '100%', display: 'flex', flexDirection: 'column' }}>
          <Box flex={1} overflow="auto">
            {searchError && (
              <Alert
                severity="error"
                sx={{ mb: 2 }}
                action={(
                  <Stack direction="row" spacing={1}>
                    <Button
                      color="inherit"
                      size="small"
                      disabled={!searchError.canRetry}
                      onClick={() => { void handleSearch(page); }}
                    >
                      Retry
                    </Button>
                    <Button color="inherit" size="small" onClick={handleGoHome}>
                      Back to folder
                    </Button>
                  </Stack>
                )}
              >
                <Typography variant="body2">{searchError.message}</Typography>
                <Typography variant="caption" display="block" sx={{ mt: 0.5 }}>
                  {searchError.hint}
                </Typography>
              </Alert>
            )}
            {shouldShowSuppressedFallbackNotice && !loading && (
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
            <Paper variant="outlined" sx={{ p: 1.5, mb: 2 }}>
              <Typography variant="subtitle2" gutterBottom>
                Governance Search Templates
              </Typography>
              <Typography variant="caption" color="text.secondary" display="block" sx={{ mb: 1 }}>
                Apply built-in governance filters with one click.
              </Typography>
              {templatesLoading && templates.length === 0 ? (
                <Typography variant="caption" color="text.secondary">
                  Loading governance templates...
                </Typography>
              ) : templates.length > 0 ? (
                <Box display="flex" flexWrap="wrap" gap={0.75}>
                  {templates.slice(0, 8).map((template) => {
                    const tooltipText = [template.description, ...(template.tags || [])]
                      .filter((value): value is string => Boolean(value && value.trim()))
                      .join(' • ');
                    const chip = (
                      <Chip
                        key={`governance-template-${template.id}`}
                        size="small"
                        variant="outlined"
                        label={template.name}
                        onClick={() => handleApplyTemplate(template)}
                      />
                    );
                    return tooltipText ? (
                      <Tooltip key={`governance-template-tooltip-${template.id}`} title={tooltipText}>
                        {chip}
                      </Tooltip>
                    ) : chip;
                  })}
                </Box>
              ) : (
                <Typography variant="caption" color={templatesError ? 'error.main' : 'text.secondary'}>
                  {templatesError || 'No governance templates available.'}
                </Typography>
              )}
            </Paper>
            {loading ? (
                <Typography sx={{ p: 2 }}>Searching...</Typography>
            ) : displayResults.length > 0 ? (
              <Stack spacing={2}>
                <Typography variant="body2" color="textSecondary">
                  {shouldShowFallback
                    ? `Showing previous results (${displayResults.length}) while the index refreshes`
                    : `Found ${totalResults} results`}
                </Typography>
                {shouldShowFallback && (
                  <Alert
                    severity="info"
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
                <Paper variant="outlined" sx={{ p: 1.5 }}>
                  <Typography variant="subtitle2" gutterBottom>
                    Search Stats
                  </Typography>
                  <Typography variant="caption" color="text.secondary" display="block" sx={{ mb: 1 }}>
                    {searchStats
                      ? `Total hits ${searchStats.totalHits} • Facet fields ${searchStats.facetFieldCount}`
                      : 'Stats unavailable for current query'}
                  </Typography>
                  <Stack spacing={1}>
                    {statsPreviewStatusTop.length > 0 && (
                      <Box display="flex" flexWrap="wrap" gap={0.5}>
                        {statsPreviewStatusTop.map((item) => (
                          <Chip
                            key={`stats-preview-status-${item.value}`}
                            size="small"
                            variant="outlined"
                            label={`Status ${item.value} (${item.count})`}
                          />
                        ))}
                      </Box>
                    )}
                    {statsMimeTypeTop.length > 0 && (
                      <Box display="flex" flexWrap="wrap" gap={0.5}>
                        {statsMimeTypeTop.map((item) => (
                          <Chip
                            key={`stats-mime-${item.value}`}
                            size="small"
                            variant="outlined"
                            label={`MIME ${item.value} (${item.count})`}
                          />
                        ))}
                      </Box>
                    )}
                    {statsCreatedByTop.length > 0 && (
                      <Box display="flex" flexWrap="wrap" gap={0.5}>
                        {statsCreatedByTop.map((item) => (
                          <Chip
                            key={`stats-creator-${item.value}`}
                            size="small"
                            variant="outlined"
                            label={`By ${item.value} (${item.count})`}
                          />
                        ))}
                      </Box>
                    )}
                    <Divider flexItem />
                    <Box>
                      <Typography variant="caption" color="text.secondary" display="block" sx={{ mb: 0.5 }}>
                        Preview Status × MIME Type
                      </Typography>
                      {statsPivotCellsTop.length > 0 ? (
                        <Grid container spacing={0.5}>
                          {statsPivotCellsTop.map((cell) => (
                            <Grid
                              item
                              xs={12}
                              sm={6}
                              key={`stats-pivot-cell-${cell.rowValue}-${cell.columnValue}`}
                            >
                              <Chip
                                size="small"
                                variant="outlined"
                                label={`${cell.rowValue} × ${cell.columnValue} (${cell.count})`}
                              />
                            </Grid>
                          ))}
                        </Grid>
                      ) : (
                        <Typography variant="caption" color="text.secondary">
                          Pivot stats unavailable for current query
                        </Typography>
                      )}
                    </Box>
                  </Stack>
                </Paper>
                <Paper variant="outlined" sx={{ p: 1.5 }}>
                  <Typography variant="subtitle2" gutterBottom>
                    Node Relations Summary
                  </Typography>
                  {representativeDocument && (
                    <Typography variant="caption" color="text.secondary" display="block" sx={{ mb: 1 }}>
                      Based on representative document: {representativeDocument.name}
                    </Typography>
                  )}
                  {nodeRelationsSummaryUnavailable ? (
                    <Typography variant="caption" color="text.secondary">
                      Relations summary unavailable
                    </Typography>
                  ) : nodeRelationsSummary ? (
                    <Stack spacing={1}>
                      <Box display="flex" flexWrap="wrap" gap={0.5}>
                        <Chip size="small" variant="outlined" label={`Parents ${nodeRelationsSummary.parentCount}`} />
                        <Chip size="small" variant="outlined" label={`Children ${nodeRelationsSummary.childCount}`} />
                        <Chip size="small" variant="outlined" label={`Sources ${nodeRelationsSummary.sourceRelationCount}`} />
                        <Chip size="small" variant="outlined" label={`Targets ${nodeRelationsSummary.targetRelationCount}`} />
                        <Chip size="small" variant="outlined" label={`Versions ${nodeRelationsSummary.versionCount}`} />
                        <Chip
                          size="small"
                          variant="outlined"
                          label={`Rendition ${nodeRelationsSummary.renditionAvailable ? 'Yes' : 'No'}`}
                        />
                        <Chip
                          size="small"
                          variant="outlined"
                          label={`Status ${(nodeRelationsSummary.previewStatus || 'UNKNOWN').toUpperCase()}`}
                        />
                        <Chip
                          size="small"
                          variant="outlined"
                          color={nodeRelationsSummary.checkedOut ? 'info' : 'default'}
                          label={nodeRelationsSummary.checkedOut
                            ? `Checkout ${nodeRelationsSummary.checkoutUser || 'active'}`
                            : 'Checkout none'}
                        />
                      </Box>
                      <Divider flexItem />
                      <Typography variant="caption" color="text.secondary" display="block">
                        Relation Details
                      </Typography>
                      {nodeRelationDetailsUnavailable ? (
                        <Typography variant="caption" color="text.secondary">
                          Relations details unavailable
                        </Typography>
                      ) : nodeRelationDetailsLoading ? (
                        <Typography variant="caption" color="text.secondary">
                          Loading relation details...
                        </Typography>
                      ) : hasNodeRelationDetails ? (
                        <Stack spacing={0.5}>
                          {nodeRelationParents.length > 0 && (
                            <Typography variant="caption" color="text.secondary">
                              Parents: {nodeRelationParents.slice(0, NODE_RELATIONS_DETAILS_PAGE_SIZE).map((parent) => parent.path || parent.name).join(' • ')}
                            </Typography>
                          )}
                          {nodeRelationSources.length > 0 && (
                            <Typography variant="caption" color="text.secondary">
                              Sources: {summarizeNodeAssociationEdges(
                                nodeRelationSources,
                                'source',
                                NODE_RELATIONS_DETAILS_PAGE_SIZE
                              )}
                            </Typography>
                          )}
                          {nodeRelationTargets.length > 0 && (
                            <Typography variant="caption" color="text.secondary">
                              Targets: {summarizeNodeAssociationEdges(
                                nodeRelationTargets,
                                'target',
                                NODE_RELATIONS_DETAILS_PAGE_SIZE
                              )}
                            </Typography>
                          )}
                          {nodeRelationSecondaryChildren.length > 0 && (
                            <Typography variant="caption" color="text.secondary">
                              Secondary children: {summarizeNodeAssociationEdges(
                                nodeRelationSecondaryChildren,
                                'secondaryChild',
                                NODE_RELATIONS_DETAILS_PAGE_SIZE
                              )}
                            </Typography>
                          )}
                          {nodeRelationSecondaryParents.length > 0 && (
                            <Typography variant="caption" color="text.secondary">
                              Secondary parents: {summarizeNodeAssociationEdges(
                                nodeRelationSecondaryParents,
                                'secondaryParent',
                                NODE_RELATIONS_DETAILS_PAGE_SIZE
                              )}
                            </Typography>
                          )}
                          {nodeRelationVersions.length > 0 && (
                            <Typography variant="caption" color="text.secondary">
                              Versions: {nodeRelationVersions
                                .slice(0, NODE_RELATIONS_DETAILS_PAGE_SIZE)
                                .map((version) => `${version.versionLabel}${version.checkoutBaseline ? ' [baseline]' : ''}${version.checkoutCurrent ? ' [current]' : ''} by ${version.creator}`)
                                .join(' • ')}
                            </Typography>
                          )}
                          {nodeRelationCheckout?.document && (
                            <Typography variant="caption" color="text.secondary">
                              Checkout: {nodeRelationCheckout.checkedOut
                                ? `${nodeRelationCheckout.checkoutUser || 'unknown'}${nodeRelationCheckout.checkoutBaselineVersionLabel ? ` from v${nodeRelationCheckout.checkoutBaselineVersionLabel}` : ''}${nodeRelationCheckout.currentVersionLabel ? ` • current v${nodeRelationCheckout.currentVersionLabel}` : ''}${nodeRelationCheckout.canKeepCheckedOut ? ' • keep-checked-out supported' : ''}`
                                : 'available'}
                            </Typography>
                          )}
                          {nodeRelationCheckoutGraph?.checkedOut && (
                            <Stack direction="row" spacing={1} alignItems="center" flexWrap="wrap">
                              <Typography variant="caption" color="text.secondary">
                                Checkout graph: {formatCheckoutGraphSummary(nodeRelationCheckoutGraph)}
                              </Typography>
                              <Button
                                size="small"
                                variant="text"
                                onClick={() => setCheckoutGraphDialogOpen(true)}
                              >
                                View checkout graph
                              </Button>
                              {nodeRelationCheckoutGraph.destinationNode?.id && (
                                <Button
                                  size="small"
                                  variant="text"
                                  onClick={() => navigate(`/browse/${nodeRelationCheckoutGraph.destinationNode?.id}`)}
                                >
                                  Open check-in target
                                </Button>
                              )}
                            </Stack>
                          )}
                          {nodeRelationRenditionDefinitions.length > 0 && (
                            <Stack spacing={0.5} alignItems="flex-start">
                              <Typography variant="caption" color="text.secondary">
                                Rendition registry: {summarizeRenditionDefinitions(
                                  nodeRelationRenditionDefinitions,
                                  NODE_RELATIONS_DETAILS_PAGE_SIZE
                                )}
                              </Typography>
                              {representativeDocument?.id && (
                                <Button
                                  size="small"
                                  variant="text"
                                  onClick={() => {
                                    setRenditionDialogNode({
                                      id: representativeDocument.id,
                                      name: representativeDocument.name,
                                    });
                                  }}
                                >
                                  View rendition registry
                                </Button>
                              )}
                            </Stack>
                          )}
                          {nodeRelationRenditions.length > 0 && (
                            <Typography variant="caption" color="text.secondary">
                              Renditions: {nodeRelationRenditions
                                .slice(0, NODE_RELATIONS_DETAILS_PAGE_SIZE)
                                .map((rendition) => {
                                  const statusLabel = (rendition.status || 'UNKNOWN').toUpperCase();
                                  const failureLabel = rendition.failureCategory
                                    ? `/${rendition.failureCategory}`
                                    : '';
                                  return `${rendition.label} ${statusLabel}${failureLabel}`;
                                })
                                .join(' • ')}
                            </Typography>
                          )}
                        </Stack>
                      ) : (
                        <Typography variant="caption" color="text.secondary">
                          No relation details
                        </Typography>
                      )}
                    </Stack>
                  ) : (
                    <Typography variant="caption" color="text.secondary">
                      {representativeDocument
                        ? (nodeRelationsSummaryLoading ? 'Loading relations summary...' : 'Relations summary unavailable')
                        : 'No representative document in current results'}
                    </Typography>
                  )}
                </Paper>
                <Paper variant="outlined" sx={{ p: 1.5 }}>
                  <Typography variant="subtitle2" gutterBottom>
                    Preview Status
                  </Typography>
                  <Typography variant="caption" color="text.secondary" display="block" sx={{ mb: 1 }}>
                    Filter results by preview generation state.
                  </Typography>
                  <Box display="flex" flexWrap="wrap" gap={1}>
                    {[
                      { value: 'READY', label: 'Ready', color: 'success' as const, count: previewStatusCounts.READY },
                      { value: 'PROCESSING', label: 'Processing', color: 'warning' as const, count: previewStatusCounts.PROCESSING },
                      { value: 'QUEUED', label: 'Queued', color: 'info' as const, count: previewStatusCounts.QUEUED },
                      { value: 'FAILED', label: 'Failed', color: 'error' as const, count: previewStatusCounts.FAILED },
                      { value: 'UNSUPPORTED', label: 'Unsupported', color: 'default' as const, count: previewStatusCounts.UNSUPPORTED },
                      { value: 'PENDING', label: 'Pending', color: 'default' as const, count: previewStatusCounts.PENDING },
                    ].map((status) => (
                      <Chip
                        key={`advanced-preview-status-${status.value}`}
                        label={`${status.label} (${status.count})`}
                        size="small"
                        color={selectedPreviewStatuses.includes(status.value) ? status.color : 'default'}
                        variant={selectedPreviewStatuses.includes(status.value) ? 'filled' : 'outlined'}
                        onClick={() => togglePreviewStatus(status.value)}
                      />
                    ))}
                    <Button
                      size="small"
                      disabled={selectedPreviewStatuses.length === 0}
                      onClick={() => {
                        setSelectedPreviewStatuses([]);
                        syncSearchStateToUrl({
                          query,
                          page: 1,
                          previewStatuses: [],
                          lockState,
                          lockOwner,
                          checkoutState,
                          checkoutUser,
                          recordOnly,
                          recordCategoryPaths: selectedRecordCategoryPaths,
                          dateRange,
                          mimeTypes: selectedMimeTypes,
                          creators: selectedCreators,
                          tags: selectedTags,
                          categories: selectedCategories,
                          minSize,
                          maxSize,
                        });
                        void handleSearch(1, {
                          previewStatuses: [],
                          lockStateOverride: lockState,
                          lockOwnerOverride: lockOwner,
                          checkoutStateOverride: checkoutState,
                          checkoutUserOverride: checkoutUser,
                          recordCategoryPathsOverride: selectedRecordCategoryPaths,
                        });
                      }}
                    >
                      Clear
                    </Button>
                  </Box>
                </Paper>
                {failedPreviewSummary.totalFailed > 0 && (
                  <Paper variant="outlined" sx={{ p: 1.5 }}>
                    <Stack spacing={1}>
                      <Typography variant="caption" color="text.secondary">
                        Preview issues on current page: {failedPreviewSummary.totalFailed}
                        {' • '}Retryable {failedPreviewSummary.retryableFailed}
                        {' • '}Unsupported {failedPreviewSummary.unsupportedFailed}
                        {failedPreviewSummary.permanentFailed > 0 && (
                          <>
                            {' • '}Permanent {failedPreviewSummary.permanentFailed}
                          </>
                        )}
                      </Typography>
                      <Box display="flex" flexWrap="wrap" gap={1} alignItems="center">
                        <FormControl size="small" sx={{ minWidth: 180 }}>
                          <Select
                            value={String(previewBatchWorkerCount)}
                            onChange={(event) => {
                              const parsed = Number(event.target.value);
                              if (!Number.isFinite(parsed)) {
                                return;
                              }
                              setPreviewBatchWorkerCount(clampNumber(parsed, 1, previewBatchWorkerMax));
                            }}
                            aria-label="Batch workers"
                          >
                            {previewBatchWorkerOptions.map((worker) => (
                              <MenuItem key={`preview-batch-worker-${worker}`} value={String(worker)}>
                                {worker} worker{worker > 1 ? 's' : ''}
                              </MenuItem>
                            ))}
                          </Select>
                        </FormControl>
                        <Typography variant="caption" color="text.secondary">
                          Scope cap {previewBatchMaxDocuments} documents
                          {' • '}Worker range 1-{previewBatchWorkerMax}
                          {previewBatchCapabilities?.scanLimit
                            ? ` • Scan limit ${previewBatchCapabilities.scanLimit}`
                            : ''}
                        </Typography>
                      </Box>
                      {previewBatchProgress && (
                        <Alert
                          severity={
                            batchRetrying
                              ? 'info'
                              : previewBatchProgress.failed > 0
                                ? 'warning'
                                : 'success'
                          }
                          sx={{ py: 0.25 }}
                        >
                          <Typography variant="caption" display="block">
                            {previewBatchLabel ? `${previewBatchLabel}: ` : ''}
                            {formatPreviewBatchOperationProgress(previewBatchProgress)}
                          </Typography>
                          {!batchRetrying && previewBatchFinishedAt && (
                            <Typography variant="caption" color="text.secondary" display="block">
                              Finished {format(previewBatchFinishedAt, 'PPp')}
                            </Typography>
                          )}
                        </Alert>
                      )}
                      {previewBatchReasonBreakdown && previewBatchReasonBreakdown.length > 0 && (
                        <>
                          <Typography variant="caption" color="text.secondary" display="block">
                            Batch reasons
                          </Typography>
                          <Box display="flex" flexWrap="wrap" gap={0.5}>
                            {previewBatchReasonBreakdown.slice(0, 5).map((item) => (
                              <Chip
                                key={`batch-reason-${item.reason}`}
                                size="small"
                                variant="outlined"
                                label={`${formatPreviewFailureReasonLabel(item.reason)} (${item.count})`}
                              />
                            ))}
                          </Box>
                        </>
                      )}
                      {previewBatchSkipBreakdown && previewBatchSkipBreakdown.length > 0 && (
                        <>
                          <Typography variant="caption" color="text.secondary" display="block">
                            Skipped diagnostics
                          </Typography>
                          <Box display="flex" flexWrap="wrap" gap={0.5}>
                            {previewBatchSkipBreakdown.slice(0, 6).map((item) => (
                              <Chip
                                key={`batch-skip-${item.reason}`}
                                size="small"
                                variant="outlined"
                                label={`${item.reason} (${item.count})`}
                              />
                            ))}
                          </Box>
                        </>
                      )}
                      {previewBatchDryRunSummary && (
                        <Alert severity="info" sx={{ py: 0.25 }}>
                          <Typography variant="caption" display="block">
                            Dry-run all matched
                            {previewBatchDryRunSummary.reason
                              ? ` (${formatPreviewFailureReasonLabel(previewBatchDryRunSummary.reason)})`
                              : ''}
                            : matched {previewBatchDryRunSummary.matched}
                            {' • '}sampled {previewBatchDryRunSummary.sampleCount}
                            {' • '}scanned {previewBatchDryRunSummary.scanned}
                            {' • '}skipped {previewBatchDryRunSummary.scanSkipped}
                            {' • '}workers {previewBatchDryRunSummary.workerCount}
                            {previewBatchDryRunSummary.truncated
                              ? ` • capped at ${previewBatchDryRunSummary.maxDocuments}`
                              : ''}
                          </Typography>
                          {previewBatchDryRunSummary.samples.length > 0 && (
                            <>
                              <Typography variant="caption" color="text.secondary" display="block">
                                Samples:
                                {' '}
                                {previewBatchDryRunSummary.samples
                                  .slice(0, 3)
                                  .map((sample) => sample.name || sample.documentId || 'unknown')
                                  .join(' • ')}
                              </Typography>
                              <Box display="flex" flexWrap="wrap" gap={0.5}>
                                {previewBatchDryRunSummary.samples
                                  .slice(0, 3)
                                  .map((sample, sampleIndex) => {
                                    const sampleLabel = sample.name || sample.documentId || 'unknown';
                                    const preflightStatus = sample.preflightStatus || 'UNKNOWN';
                                    const route = sample.preflightRoute || 'unknown';
                                    const profile = sample.preflightPolicyProfile || 'default';
                                    const declineReason = sample.preflightSkipReason ? ` • ${sample.preflightSkipReason}` : '';
                                    return (
                                      <Chip
                                        key={`dry-run-preflight-${sample.documentId || sample.name || sampleIndex}`}
                                        size="small"
                                        variant="outlined"
                                        label={`${sampleLabel}: ${preflightStatus} • ${route} • ${profile}${declineReason}`}
                                      />
                                    );
                                  })}
                              </Box>
                            </>
                          )}
                          {previewBatchDryRunSummary.reasonBreakdown.length > 0 && (
                            <>
                              <Typography variant="caption" color="text.secondary" display="block">
                                Reason breakdown
                              </Typography>
                              <Box display="flex" flexWrap="wrap" gap={0.5}>
                                {previewBatchDryRunSummary.reasonBreakdown.slice(0, 5).map((item) => (
                                  <Stack direction="row" spacing={0.5} key={`dry-run-reason-${item.reason}`}>
                                    <Chip
                                      size="small"
                                      variant="outlined"
                                      label={`${formatPreviewFailureReasonLabel(item.reason)} (${item.count})`}
                                    />
                                    <Button
                                      size="small"
                                      variant="text"
                                      onClick={() => { void handleRetryFailedReasonAllMatched(item.reason, false); }}
                                      disabled={batchActionBusy}
                                      aria-label={`Retry all matched for reason ${formatPreviewFailureReasonLabel(item.reason)}`}
                                    >
                                      Retry all
                                    </Button>
                                    <Button
                                      size="small"
                                      variant="text"
                                      onClick={() => { void handleRetryFailedReasonAllMatched(item.reason, true); }}
                                      disabled={batchActionBusy}
                                      aria-label={`Rebuild all matched for reason ${formatPreviewFailureReasonLabel(item.reason)}`}
                                    >
                                      Rebuild all
                                    </Button>
                                  </Stack>
                                ))}
                              </Box>
                            </>
                          )}
                          <Box display="flex" flexWrap="wrap" gap={1}>
                            <Button
                              size="small"
                              variant="text"
                              onClick={() => { void handleExportDryRunCsv(); }}
                              disabled={batchActionBusy || exportingDryRunCsv}
                            >
                              {exportingDryRunCsv ? exportDryRunCsvProgressText : 'Export dry-run CSV'}
                            </Button>
                            {exportingDryRunCsv && (
                              <Button
                                size="small"
                                variant="text"
                                color="warning"
                                onClick={() => { void handleCancelExportDryRunCsv(); }}
                                disabled={cancelingDryRunCsv || !exportDryRunCsvTaskId}
                              >
                                {cancelingDryRunCsv ? 'Cancelling...' : 'Cancel export'}
                              </Button>
                            )}
                            {exportingDryRunCsv && (
                              <Typography variant="caption" color="text.secondary" role="status" aria-live="polite">
                                {exportDryRunCsvProgressText}
                              </Typography>
                            )}
                          </Box>
                          <Box display="flex" flexWrap="wrap" gap={1} alignItems="center">
                            <FormControl size="small" sx={{ minWidth: 170 }}>
                              <Select
                                value={previewBatchExportTaskStatusFilter}
                                onChange={(event) => {
                                  setPreviewBatchExportTaskStatusFilter(event.target.value as ExportDryRunTaskStatusFilter);
                                }}
                                aria-label="Export task status filter"
                              >
                                {EXPORT_DRY_RUN_TASK_STATUS_FILTERS.map((statusOption) => (
                                  <MenuItem key={`export-task-status-filter-${statusOption}`} value={statusOption}>
                                    {statusOption}
                                  </MenuItem>
                                ))}
                              </Select>
                            </FormControl>
                            <Button
                              size="small"
                              variant="text"
                              onClick={() => { void loadPreviewBatchExportTasks(false); }}
                              disabled={
                                previewBatchExportTasksLoading
                                || previewBatchExportTasksCleanupLoading
                                || previewBatchExportTasksCancelActiveLoading
                              }
                            >
                              {previewBatchExportTasksLoading ? 'Refreshing tasks...' : 'Refresh export tasks'}
                            </Button>
                            <Button
                              size="small"
                              variant="text"
                              color="warning"
                              onClick={() => { void handleCancelActiveExportTasks(); }}
                              disabled={
                                previewBatchExportTasksCancelActiveLoading
                                || previewBatchExportTasksLoading
                                || previewBatchExportTasksCleanupLoading
                              }
                              aria-label="Cancel active export tasks"
                            >
                              {previewBatchExportTasksCancelActiveLoading ? 'Cancelling active...' : 'Cancel active tasks'}
                            </Button>
                            <Button
                              size="small"
                              variant="text"
                              color="warning"
                              onClick={() => { void handleCleanupExportTasks(); }}
                              disabled={
                                previewBatchExportTasksCleanupLoading
                                || previewBatchExportTasksLoading
                                || previewBatchExportTasksCancelActiveLoading
                              }
                            >
                              {previewBatchExportTasksCleanupLoading ? 'Cleaning up...' : 'Cleanup tasks'}
                            </Button>
                          </Box>
                          {previewBatchExportTaskSummary && (
                            <>
                              <Typography variant="caption" color="text.secondary" display="block">
                                Export task summary
                              </Typography>
                              <Box display="flex" flexWrap="wrap" gap={0.5}>
                                <Chip size="small" variant="outlined" label={`Total ${previewBatchExportTaskSummary.total}`} />
                                <Chip size="small" variant="outlined" label={`Queued ${previewBatchExportTaskSummary.queued}`} />
                                <Chip size="small" variant="outlined" label={`Running ${previewBatchExportTaskSummary.running}`} />
                                <Chip size="small" variant="outlined" label={`Completed ${previewBatchExportTaskSummary.completed}`} />
                                <Chip size="small" variant="outlined" label={`Cancelled ${previewBatchExportTaskSummary.cancelled}`} />
                                <Chip size="small" variant="outlined" label={`Failed ${previewBatchExportTaskSummary.failed}`} />
                                <Chip size="small" variant="outlined" label={`Terminal ${previewBatchExportTaskSummary.terminal}`} />
                                <Chip size="small" variant="outlined" label={`Active ${previewBatchExportTaskSummary.active}`} />
                              </Box>
                            </>
                          )}
                          {previewBatchExportTasks.length > 0 && (
                            <>
                              <Typography variant="caption" color="text.secondary" display="block">
                                Recent export tasks
                                {previewBatchExportTaskStatusFilter !== 'ALL'
                                  ? ` (${previewBatchExportTaskStatusFilter})`
                                  : ''}
                              </Typography>
                              <Stack spacing={0.75}>
                                {previewBatchExportTasks.slice(0, 5).map((task) => {
                                  const normalizedStatus = normalizeExportTaskStatus(task.status);
                                  const canDownload = EXPORT_DRY_RUN_CSV_COMPLETED_STATUSES.has(normalizedStatus);
                                  const canCancel = normalizedStatus === 'QUEUED' || EXPORT_DRY_RUN_CSV_RUNNING_STATUSES.has(normalizedStatus);
                                  return (
                                    <Box key={`export-task-${task.taskId}`} display="flex" flexWrap="wrap" gap={0.5} alignItems="center">
                                      <Chip size="small" variant="outlined" label={normalizedStatus} />
                                      <Typography variant="caption" color="text.secondary">
                                        {task.taskId.slice(0, 8)}
                                        {task.finishedAt ? ` • finished ${format(new Date(task.finishedAt), 'PPp')}` : ''}
                                      </Typography>
                                      {canDownload && (
                                        <Button
                                          size="small"
                                          variant="text"
                                          onClick={() => { void handleDownloadExportTask(task); }}
                                        >
                                          Download
                                        </Button>
                                      )}
                                      {canCancel && (
                                        <Button
                                          size="small"
                                          variant="text"
                                          color="warning"
                                          onClick={() => { void handleCancelExportTask(task.taskId); }}
                                        >
                                          Cancel
                                        </Button>
                                      )}
                                      {task.error && (
                                        <Typography variant="caption" color="error.main">
                                          {task.error}
                                        </Typography>
                                      )}
                                    </Box>
                                  );
                                })}
                              </Stack>
                            </>
                          )}
                          {!previewBatchExportTasksLoading && previewBatchExportTasks.length === 0 && (
                            <Typography variant="caption" color="text.secondary" display="block">
                              No export tasks for current filter.
                            </Typography>
                          )}
                          <Typography variant="caption" color="text.secondary" display="block">
                            Finished {format(previewBatchDryRunSummary.finishedAt, 'PPp')}
                          </Typography>
                        </Alert>
                      )}
                      {batchTargetResolving && (
                        <Alert severity="info" sx={{ py: 0.25 }}>
                          <Typography variant="caption" display="block">
                            {batchDryRunning
                              ? 'Collecting retryable failed previews across all matched results (dry-run)...'
                              : 'Collecting retryable failed previews across all matched results...'}
                          </Typography>
                        </Alert>
                      )}
                      {failedPreviewSummary.retryableFailed > 0 && (
                        <>
                          <Box display="flex" flexWrap="wrap" gap={1}>
                            <Button
                              size="small"
                              variant="outlined"
                              onClick={() => { void handleRetryFailedPreviews(false); }}
                              disabled={batchActionBusy}
                            >
                              Retry failed previews
                            </Button>
                            <Button
                              size="small"
                              variant="text"
                              onClick={() => { void handleRetryFailedPreviews(true); }}
                              disabled={batchActionBusy}
                            >
                              Force rebuild failed previews
                            </Button>
                          </Box>
                          <Box display="flex" flexWrap="wrap" gap={1}>
                            <Button
                              size="small"
                              variant="outlined"
                              onClick={() => { void handleRetryFailedPreviewsAllMatched(false); }}
                              disabled={batchActionBusy}
                            >
                              Retry all matched (max {previewBatchMaxDocuments})
                            </Button>
                            <Button
                              size="small"
                              variant="text"
                              onClick={() => { void handleRetryFailedPreviewsAllMatched(true); }}
                              disabled={batchActionBusy}
                            >
                              Rebuild all matched (max {previewBatchMaxDocuments})
                            </Button>
                            <Button
                              size="small"
                              variant="text"
                              onClick={() => { void handleDryRunFailedPreviewsAllMatched(); }}
                              disabled={batchActionBusy}
                            >
                              Dry-run all matched (max {previewBatchMaxDocuments})
                            </Button>
                          </Box>
                          <Typography variant="caption" color="text.secondary">
                            All-matched actions scan the current query + filters across pages and process up to {previewBatchMaxDocuments} retryable failures.
                          </Typography>
                        </>
                      )}
                      {failedPreviewSummary.totalFailed > 0 && failedPreviewSummary.retryableFailed === 0 && (
                        <>
                          <Typography variant="caption" color="text.secondary">
                            {nonRetryablePreviewSummaryMessage}
                          </Typography>
                          <Typography variant="caption" color="text.secondary">
                            Unsupported {failedPreviewSummary.unsupportedFailed}
                            {' • '}Permanent {failedPreviewSummary.permanentFailed}
                          </Typography>
                        </>
                      )}
                      {failedPreviewSummary.retryableFailed > 0 && failedPreviewReasonSummary.length > 0 && (
                        <>
                          <Typography variant="caption" color="text.secondary">
                            Retryable reasons
                            {failedPreviewSummary.retryableReasons.length > failedPreviewReasonSummary.length
                              ? ` (showing ${failedPreviewReasonSummary.length} of ${failedPreviewSummary.retryableReasons.length})`
                              : ` (${failedPreviewReasonSummary.length})`}
                          </Typography>
                          <Box display="flex" flexWrap="wrap" gap={1}>
                            {failedPreviewReasonSummary.map((item) => (
                              <Stack direction="row" spacing={0.5} key={`retry-reason-${item.reason}`}>
                                <Chip
                                  size="small"
                                  label={`${formatPreviewFailureReasonLabel(item.reason)} (${item.count})`}
                                  variant="outlined"
                                />
                                <Button
                                  size="small"
                                  variant="text"
                                  onClick={() => { void handleRetryFailedReason(item.reason, false); }}
                                  disabled={batchActionBusy}
                                >
                                  Retry
                                </Button>
                                <Button
                                  size="small"
                                  variant="text"
                                  onClick={() => { void handleRetryFailedReason(item.reason, true); }}
                                  disabled={batchActionBusy}
                                >
                                  Rebuild
                                </Button>
                                <Button
                                  size="small"
                                  variant="text"
                                  onClick={() => { void handleDryRunFailedPreviewsAllMatched(item.reason); }}
                                  disabled={batchActionBusy}
                                >
                                  Dry-run all
                                </Button>
                                <Button
                                  size="small"
                                  variant="text"
                                  onClick={() => { void handleRetryFailedReasonAllMatched(item.reason, false); }}
                                  disabled={batchActionBusy}
                                >
                                  Retry all
                                </Button>
                                <Button
                                  size="small"
                                  variant="text"
                                  onClick={() => { void handleRetryFailedReasonAllMatched(item.reason, true); }}
                                  disabled={batchActionBusy}
                                >
                                  Rebuild all
                                </Button>
                                <Button
                                  size="small"
                                  variant="text"
                                  onClick={() => {
                                    void handleReplayDeadLetterReasonAllMatched(item.reason, {
                                      category: 'TEMPORARY',
                                      retryable: true,
                                    });
                                  }}
                                  disabled={batchActionBusy}
                                  aria-label={`Replay dead-letter all matched for reason ${item.reason}`}
                                >
                                  {reasonDeadLetterActionKey === buildReasonDeadLetterActionKey(item.reason, 'replay', 'TEMPORARY', true)
                                    ? 'Replaying DL...'
                                    : 'Replay DL'}
                                </Button>
                                <Button
                                  size="small"
                                  variant="text"
                                  onClick={() => {
                                    void handleClearDeadLetterReasonAllMatched(item.reason, {
                                      category: 'TEMPORARY',
                                      retryable: true,
                                    });
                                  }}
                                  disabled={batchActionBusy}
                                  aria-label={`Clear dead-letter all matched for reason ${item.reason}`}
                                >
                                  {reasonDeadLetterActionKey === buildReasonDeadLetterActionKey(item.reason, 'clear', 'TEMPORARY', true)
                                    ? 'Clearing DL...'
                                    : 'Clear DL'}
                                </Button>
                              </Stack>
                            ))}
                            {failedPreviewSummary.retryableReasons.length > 4 && (
                              <Button
                                size="small"
                                variant="text"
                                onClick={() => setShowAllRetryReasons((prev) => !prev)}
                              >
                                {showAllRetryReasons ? 'Show fewer reasons' : 'Show all reasons'}
                              </Button>
                            )}
                          </Box>
                        </>
                      )}
                      {nonRetryableFailedReasonSummary.length > 0 && (
                        <>
                          <Typography variant="caption" color="text.secondary">
                            Non-retryable reasons
                            {nonRetryableFailedReasonSummary.length > nonRetryablePreviewReasonSummary.length
                              ? ` (showing ${nonRetryablePreviewReasonSummary.length} of ${nonRetryableFailedReasonSummary.length})`
                              : ` (${nonRetryablePreviewReasonSummary.length})`}
                          </Typography>
                          <Box display="flex" flexWrap="wrap" gap={1}>
                            {nonRetryablePreviewReasonSummary.map((item) => (
                              <Stack direction="row" spacing={0.5} key={`non-retry-reason-${item.reason}-${item.category}`}>
                                <Chip
                                  size="small"
                                  label={`${formatPreviewFailureReasonLabel(item.reason)} (${item.count})`}
                                  variant="outlined"
                                />
                                <Chip
                                  size="small"
                                  label={item.category}
                                  variant="outlined"
                                />
                                <Button
                                  size="small"
                                  variant="text"
                                  onClick={() => {
                                    void handleClearDeadLetterReasonAllMatched(item.reason, {
                                      category: item.category,
                                      retryable: item.retryable,
                                    });
                                  }}
                                  disabled={batchActionBusy}
                                  aria-label={`Clear dead-letter all matched for non-retryable reason ${item.reason}`}
                                >
                                  {reasonDeadLetterActionKey === buildReasonDeadLetterActionKey(
                                    item.reason,
                                    'clear',
                                    item.category,
                                    item.retryable
                                  )
                                    ? 'Clearing DL...'
                                    : 'Clear DL'}
                                </Button>
                              </Stack>
                            ))}
                            {nonRetryableFailedReasonSummary.length > 4 && (
                              <Button
                                size="small"
                                variant="text"
                                onClick={() => setShowAllNonRetryReasons((prev) => !prev)}
                              >
                                {showAllNonRetryReasons ? 'Show fewer reasons' : 'Show all reasons'}
                              </Button>
                            )}
                          </Box>
                          <Typography variant="caption" color="text.secondary">
                            Non-retryable reasons only support dead-letter clear actions.
                          </Typography>
                        </>
                      )}
                      {failedPreviewSummary.retryableFailed > 0 && previewRetrySummary && (
                        <Typography variant="caption" color="text.secondary">
                          Preview queue: {previewRetrySummary.count} item(s) • Next retry {format(previewRetrySummary.nextAt, 'PPp')}
                        </Typography>
                      )}
                    </Stack>
                  </Paper>
                )}
                {displayResults.map((result) => {
                  const recordDeclaration = getRecordDeclarationFromNode(result);
                  return (
                  <Paper 
                    key={result.id} 
                    sx={{ p: 2, cursor: 'pointer', '&:hover': { bgcolor: 'action.hover' } }}
                    onClick={() => {
                      if (result.nodeType === 'FOLDER') {
                        navigate(`/browse/${result.id}`);
                      } else {
                        navigate(`/browse/${result.parentId || 'root'}`);
                      }
                      if (sidebarAutoCollapse) {
                        dispatch(setSidebarOpen(false));
                      }
                    }}
                  >
                    <Box display="flex" justifyContent="space-between">
                      <Typography variant="h6" color="primary" sx={{ textDecoration: 'underline' }}>
                        {result.name}
                      </Typography>
                      <Typography variant="caption" color="textSecondary">
                        {format(new Date(result.createdDate), 'PPP')}
                      </Typography>
                    </Box>

                    {(() => {
                      const breadcrumb = formatBreadcrumbPath(result.path, { nodeName: result.name, maxSegments: 4 });
                      const creator = (result.createdBy || '').trim();
                      const parts = [breadcrumb, creator ? `By ${creator}` : null].filter(Boolean) as string[];
                      if (parts.length === 0) {
                        return null;
                      }
                      return (
                        <Tooltip title={result.path} placement="top-start" arrow disableHoverListener={!result.path}>
                          <Typography variant="caption" color="textSecondary" sx={{ mt: 0.5 }} noWrap>
                            {parts.join(' | ')}
                          </Typography>
                        </Tooltip>
                      );
                    })()}
                    
                    {/* Snippets / Highlights */}
                    {(() => {
                      const snippet = result.highlightSummary
                        || result.highlights?.description?.[0]
                        || result.highlights?.content?.[0]
                        || result.highlights?.textContent?.[0]
                        || result.highlights?.extractedText?.[0]
                        || result.highlights?.title?.[0]
                        || result.highlights?.name?.[0];
                      if (!snippet) {
                        return null;
                      }
                      return (
                        <Typography
                          variant="body2"
                          color="textSecondary"
                          sx={{ mt: 1 }}
                          dangerouslySetInnerHTML={{ __html: `...${snippet}...` }}
                        />
                      );
                    })()}

                    {(() => {
                      const matchFields = (result.matchFields && result.matchFields.length > 0)
                        ? result.matchFields
                        : Object.entries(result.highlights || {})
                            .filter(([, values]) => Array.isArray(values) && values.length > 0)
                            .map(([key]) => key);
                      if (matchFields.length === 0) {
                        return null;
                      }
                      const displayFields = matchFields.slice(0, 4);
                      const remaining = matchFields.length - displayFields.length;
                      const formatField = (field: string) =>
                        MATCH_FIELD_LABELS[field]
                        || field.replace(/([a-z])([A-Z])/g, '$1 $2').replace(/_/g, ' ');
                      return (
                        <Box mt={1} display="flex" gap={1} flexWrap="wrap">
                          <Chip label="Matched in" size="small" variant="outlined" />
                          {displayFields.map((field) => (
                            <Chip key={`${result.id}-match-${field}`} label={formatField(field)} size="small" />
                          ))}
                          {remaining > 0 && (
                            <Chip label={`+${remaining}`} size="small" variant="outlined" />
                          )}
                        </Box>
                      );
                    })()}
                    
                    <Box mt={1} display="flex" gap={1}>
                        {(() => {
                          const checkoutChip = getSearchResultCheckoutChip(result.checkedOut, result.checkoutUser);
                          if (!checkoutChip) {
                            return null;
                          }
                          return (
                            <Tooltip title={checkoutChip.tooltip} placement="top-start" arrow>
                              <Chip
                                label={checkoutChip.label}
                                size="small"
                                variant="outlined"
                                color={checkoutChip.color}
                              />
                            </Tooltip>
                          );
                        })()}
                        {(() => {
                          const lockChip = getSearchResultLockChip(result.locked, result.lockedBy);
                          if (!lockChip) {
                            return null;
                          }
                          return (
                            <Tooltip title={lockChip.tooltip} placement="top-start" arrow>
                              <Chip
                                label={lockChip.label}
                                size="small"
                                variant="outlined"
                                color={lockChip.color}
                              />
                            </Tooltip>
                          );
                        })()}
                        <Chip label={result.mimeType} size="small" variant="outlined" />
                        <Chip
                          label={`${((result.fileSize || 0) / 1024).toFixed(1)} KB`}
                          size="small"
                          variant="outlined"
                        />
                        {recordDeclaration && (
                          <RecordStatusChip declaration={recordDeclaration} />
                        )}
                        {formatScore(result.score) && (
                          <Chip label={formatScore(result.score)} size="small" variant="outlined" />
                        )}
                        {result.nodeType !== 'FOLDER' && getPreviewStatusMeta(
                          previewQueueStatusById[result.id]?.previewStatus ?? result.previewStatus,
                          result.mimeType,
                          previewQueueStatusById[result.id]?.previewFailureCategory ?? result.previewFailureCategory,
                          previewQueueStatusById[result.id]?.previewFailureReason ?? result.previewFailureReason
                        ) && (() => {
                          const previewStatus = previewQueueStatusById[result.id]?.previewStatus ?? result.previewStatus;
                          const previewFailureCategory = previewQueueStatusById[result.id]?.previewFailureCategory ?? result.previewFailureCategory;
                          const previewFailureReason = previewQueueStatusById[result.id]?.previewFailureReason ?? result.previewFailureReason;
                          const previewMeta = getPreviewStatusMeta(
                            previewStatus,
                            result.mimeType,
                            previewFailureCategory,
                            previewFailureReason
                          );
                          if (!previewMeta) {
                            return null;
                          }
                          const queueDetail = getQueueDetail(result.id);
                          const tooltipTitle = [previewFailureReason || '', queueDetail].filter(Boolean).join(' • ');
                          const queueState = previewQueueStatusById[result.id]?.queueState || null;
                          const canCancel = isActivePreviewQueueState(queueState);
                          const isFailed = (previewStatus || '').toUpperCase() === 'FAILED';
                          const canRetry = isFailed && isRetryablePreviewFailure(
                            previewFailureCategory,
                            result.mimeType,
                            previewFailureReason
                          );
                          return (
                            <Box display="flex" flexDirection="column" alignItems="flex-start" gap={0.5}>
                              <Box display="flex" alignItems="center" gap={0.5}>
                                <Tooltip
                                  title={tooltipTitle}
                                  placement="top-start"
                                  arrow
                                  disableHoverListener={!tooltipTitle}
                                >
                                  <Chip
                                    label={previewMeta.label}
                                    color={previewMeta.color}
                                    size="small"
                                    variant="outlined"
                                  />
                                </Tooltip>
                                {canRetry && (
                                  <Tooltip title="Retry preview" placement="top-start" arrow>
                                    <span>
                                      <IconButton
                                        size="small"
                                        aria-label="Retry preview"
                                        disabled={queueingPreviewId === result.id || cancelingPreviewId === result.id}
                                        onClick={(event) => {
                                          event.stopPropagation();
                                          void handleRetryPreview(result);
                                        }}
                                      >
                                        <RefreshIcon fontSize="small" />
                                      </IconButton>
                                    </span>
                                  </Tooltip>
                                )}
                                {canRetry && (
                                  <Tooltip title="Force rebuild preview" placement="top-start" arrow>
                                    <span>
                                      <IconButton
                                        size="small"
                                        aria-label="Force rebuild preview"
                                        disabled={queueingPreviewId === result.id || cancelingPreviewId === result.id}
                                        onClick={(event) => {
                                          event.stopPropagation();
                                          void handleRetryPreview(result, true);
                                        }}
                                      >
                                        <RebuildIcon fontSize="small" />
                                      </IconButton>
                                    </span>
                                  </Tooltip>
                                )}
                                {canCancel && (
                                  <Tooltip title="Cancel preview task" placement="top-start" arrow>
                                    <span>
                                      <IconButton
                                        size="small"
                                        aria-label="Cancel preview task"
                                        disabled={cancelingPreviewId === result.id}
                                        onClick={(event) => {
                                          event.stopPropagation();
                                          void handleCancelPreviewQueue(result);
                                        }}
                                      >
                                        <CancelQueueIcon fontSize="small" />
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
                        })()}
                    </Box>
                    {canWrite && result.nodeType !== 'FOLDER' && (
                      <Box mt={1} display="flex" gap={1} flexWrap="wrap">
                        {!result.checkedOut && (
                          <Tooltip
                            title={getAdvancedSearchCheckoutActionReason(result, currentUsername) || ''}
                            placement="top-start"
                            arrow
                            disableHoverListener={!getAdvancedSearchCheckoutActionReason(result, currentUsername)}
                          >
                            <span>
                              <Button
                                size="small"
                                variant="outlined"
                                startIcon={<CheckCircleOutline />}
                                disabled={
                                  checkoutActionNodeId === result.id
                                  || Boolean(getAdvancedSearchCheckoutActionReason(result, currentUsername))
                                }
                                onClick={(event) => {
                                  event.stopPropagation();
                                  void handleCheckoutResult(result);
                                }}
                              >
                                {checkoutActionNodeId === result.id ? 'Checking out...' : 'Check Out'}
                              </Button>
                            </span>
                          </Tooltip>
                        )}
                        {result.checkedOut && (
                          <Tooltip
                            title={getAdvancedSearchCheckInActionReason(result, currentUsername, isAdmin) || ''}
                            placement="top-start"
                            arrow
                            disableHoverListener={!getAdvancedSearchCheckInActionReason(result, currentUsername, isAdmin)}
                          >
                            <span>
                              <Button
                                size="small"
                                variant="outlined"
                                startIcon={<Publish />}
                                disabled={
                                  checkInSubmittingNodeId === result.id
                                  || Boolean(getAdvancedSearchCheckInActionReason(result, currentUsername, isAdmin))
                                }
                                onClick={(event) => {
                                  event.stopPropagation();
                                  openCheckInDialog(result);
                                }}
                              >
                                {checkInSubmittingNodeId === result.id ? 'Checking in...' : 'Check In'}
                              </Button>
                            </span>
                          </Tooltip>
                        )}
                        {result.checkedOut && (
                          <Tooltip
                            title={getAdvancedSearchCancelCheckoutReason(result, currentUsername, isAdmin) || ''}
                            placement="top-start"
                            arrow
                            disableHoverListener={!getAdvancedSearchCancelCheckoutReason(result, currentUsername, isAdmin)}
                          >
                            <span>
                              <Button
                                size="small"
                                variant="outlined"
                                color="inherit"
                                startIcon={<Undo />}
                                disabled={
                                  cancelCheckoutActionNodeId === result.id
                                  || Boolean(getAdvancedSearchCancelCheckoutReason(result, currentUsername, isAdmin))
                                }
                                onClick={(event) => {
                                  event.stopPropagation();
                                  void handleCancelCheckoutResult(result);
                                }}
                              >
                                {cancelCheckoutActionNodeId === result.id ? 'Cancelling...' : 'Cancel Checkout'}
                              </Button>
                            </span>
                          </Tooltip>
                        )}
                      </Box>
                    )}
                  </Paper>
                  );
                })}
              </Stack>
            ) : (
              <Box p={4} textAlign="center">
                <Typography color="textSecondary">
                    {query ? 'No results found.' : 'Enter a query to start searching.'}
                </Typography>
              </Box>
            )}
          </Box>

          {/* Pagination */}
          {!shouldShowFallback && totalPages > 1 && (
            <Box py={2} display="flex" justifyContent="center">
              <Pagination
                count={totalPages}
                page={page}
                onChange={handlePageChange}
                color="primary"
              />
            </Box>
          )}
        </Grid>
      </Grid>

      <Dialog
        open={Boolean(checkInDialogResult)}
        onClose={closeCheckInDialog}
        maxWidth="sm"
        fullWidth
      >
        <DialogTitle>Check In</DialogTitle>
        <DialogContent dividers sx={{ display: 'flex', flexDirection: 'column', gap: 2 }}>
          <Typography variant="body2" color="text.secondary">
            {checkInDialogResult ? `Check in "${checkInDialogResult.name}" with an optional new version file.` : ''}
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
                disabled={!checkInFile || (Boolean(checkInDialogResult?.checkoutUser) && checkInDialogResult?.checkoutUser !== currentUsername)}
              />
            )}
            label="Keep checked out after check-in"
          />
        </DialogContent>
        <DialogActions>
          <Button onClick={closeCheckInDialog} disabled={Boolean(checkInSubmittingNodeId)}>
            Cancel
          </Button>
          <Button variant="contained" onClick={() => void handleCheckInResult()} disabled={Boolean(checkInSubmittingNodeId)}>
            {checkInSubmittingNodeId ? 'Checking in...' : 'Check In'}
          </Button>
        </DialogActions>
      </Dialog>
      <CheckoutGraphDialog
        open={checkoutGraphDialogOpen}
        nodeId={representativeDocument?.id || null}
        nodeName={representativeDocument?.name}
        onClose={() => setCheckoutGraphDialogOpen(false)}
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

export default AdvancedSearchPage;
