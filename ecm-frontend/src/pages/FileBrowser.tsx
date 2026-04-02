import React, { useCallback, useEffect, useState } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import {
  Box,
  Paper,
  IconButton,
  ToggleButton,
  ToggleButtonGroup,
  CircularProgress,
  Typography,
  Alert,
  Button,
  Chip,
  FormControl,
  FormControlLabel,
  InputLabel,
  MenuItem,
  Select,
  Switch,
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableRow,
  TextField,
} from '@mui/material';
import {
  ViewList,
  ViewModule,
  Delete,
  Download,
  EditNote,
  Refresh,
} from '@mui/icons-material';
import { useAppDispatch, useAppSelector } from 'store';
import { fetchNode, fetchChildren, clearSelection } from 'store/slices/nodeSlice';
import { setSidebarOpen, setViewMode } from 'store/slices/uiSlice';
import FileBreadcrumb from 'components/browser/FileBreadcrumb';
import FileList from 'components/browser/FileList';
import BulkMetadataDialog from 'components/dialogs/BulkMetadataDialog';
import { Node } from 'types';
import nodeService, { BatchDownloadAsyncStatus, BatchDownloadAsyncTask, BatchDownloadPreflightResponse } from 'services/nodeService';
import bulkOperationService, {
  BulkHistoryItem,
  BulkHistorySummaryResult,
  BulkHistoryTrendResult,
} from 'services/bulkOperationService';
import { toast } from 'react-toastify';

const DocumentPreview = React.lazy(() => import('components/preview/DocumentPreview'));
const resolvePositiveInt = (rawValue: string | undefined, fallback: number): number => {
  if (!rawValue) return fallback;
  const parsed = Number(rawValue);
  if (!Number.isFinite(parsed) || parsed <= 0) return fallback;
  return Math.floor(parsed);
};
const FILE_BROWSER_LOADING_WATCHDOG_MS = resolvePositiveInt(
  process.env.REACT_APP_FILE_BROWSER_LOADING_WATCHDOG_MS,
  12_000
);
const BULK_HISTORY_LIMIT = 20;
const BULK_HISTORY_EXPORT_LIMIT = 500;
const BULK_HISTORY_SUMMARY_TOP_LIMIT = 6;
const BULK_HISTORY_TREND_LIMIT = 7;
type BatchDownloadTaskStatusFilter = 'ALL' | BatchDownloadAsyncStatus;
const BATCH_DOWNLOAD_TASK_LIST_LIMIT = 8;
const BATCH_DOWNLOAD_TASK_ROW_OPTIONS = [5, 8, 12, 20];
const BATCH_DOWNLOAD_AUTO_REFRESH_MS = 3000;
const ACTIVE_BATCH_DOWNLOAD_TASK_STATUSES = new Set(['QUEUED', 'RUNNING', 'CANCEL_REQUESTED']);
const BATCH_DOWNLOAD_TASK_FILTER_OPTIONS: Array<{ value: BatchDownloadTaskStatusFilter; label: string }> = [
  { value: 'ALL', label: 'All tasks' },
  { value: 'QUEUED', label: 'Queued' },
  { value: 'RUNNING', label: 'Running' },
  { value: 'CANCEL_REQUESTED', label: 'Cancel requested' },
  { value: 'COMPLETED', label: 'Completed' },
  { value: 'FAILED', label: 'Failed' },
  { value: 'CANCELLED', label: 'Cancelled' },
];
const createEmptyBulkHistorySummary = (): BulkHistorySummaryResult => ({
  total: 0,
  eventTypeItems: [],
  actorItems: [],
});
const createEmptyBulkHistoryTrend = (): BulkHistoryTrendResult => ({
  items: [],
  truncated: false,
  scanLimit: null,
});

const formatBulkHistoryTime = (value: string | null): string => {
  if (!value) {
    return '-';
  }
  const parsed = new Date(value);
  if (Number.isNaN(parsed.getTime())) {
    return value;
  }
  return parsed.toLocaleString();
};

const formatBulkHistoryTrendDate = (value: string | null): string => {
  if (!value) {
    return 'Unknown day';
  }
  const parsed = new Date(value);
  if (Number.isNaN(parsed.getTime())) {
    return value;
  }
  return parsed.toLocaleDateString();
};

const formatBytes = (value: number): string => {
  if (!Number.isFinite(value) || value <= 0) {
    return '0 B';
  }
  const units = ['B', 'KB', 'MB', 'GB'];
  let size = value;
  let unitIndex = 0;
  while (size >= 1024 && unitIndex < units.length - 1) {
    size /= 1024;
    unitIndex += 1;
  }
  return `${size >= 10 || unitIndex === 0 ? size.toFixed(0) : size.toFixed(1)} ${units[unitIndex]}`;
};

const buildBatchDownloadPreflightToast = (preflight: BatchDownloadPreflightResponse): string => {
  if (preflight.message?.trim()) {
    return preflight.message.trim();
  }
  if (preflight.decision === 'BLOCKED' || !preflight.executable) {
    return 'No readable files available for batch download';
  }
  if (preflight.decision === 'PARTIAL') {
    return `Ready to download ${preflight.includedFileCount} file(s); some selected items will be skipped`;
  }
  return `Ready to download ${preflight.includedFileCount} file(s)`;
};

const FileBrowser: React.FC = () => {
  const { nodeId = 'root' } = useParams<{ nodeId: string }>();
  const navigate = useNavigate();
  const dispatch = useAppDispatch();
  
  const { currentNode, nodes, nodesTotal, loading, selectedNodes, error } = useAppSelector((state) => state.node);
  const { user } = useAppSelector((state) => state.auth);
  const { viewMode, sortBy, sortAscending, compactMode, sidebarAutoCollapse } = useAppSelector((state) => state.ui);
  const canWrite = Boolean(user?.roles?.includes('ROLE_ADMIN') || user?.roles?.includes('ROLE_EDITOR'));
  const [page, setPage] = useState(0);
  const [pageSize, setPageSize] = useState(50);
  const [previewNode, setPreviewNode] = useState<Node | null>(null);
  const [previewAnnotate, setPreviewAnnotate] = useState(false);
  const [bulkMetadataOpen, setBulkMetadataOpen] = useState(false);
  const [loadingStartedAt, setLoadingStartedAt] = useState<number | null>(null);
  const [loadingWatchdogTriggered, setLoadingWatchdogTriggered] = useState(false);
  const [bulkHistory, setBulkHistory] = useState<BulkHistoryItem[]>([]);
  const [bulkHistorySummary, setBulkHistorySummary] = useState<BulkHistorySummaryResult>(createEmptyBulkHistorySummary);
  const [bulkHistoryTrend, setBulkHistoryTrend] = useState<BulkHistoryTrendResult>(createEmptyBulkHistoryTrend);
  const [bulkHistoryLoading, setBulkHistoryLoading] = useState(false);
  const [bulkHistoryError, setBulkHistoryError] = useState<string | null>(null);
  const [bulkHistoryExporting, setBulkHistoryExporting] = useState(false);
  const [bulkHistorySummaryExporting, setBulkHistorySummaryExporting] = useState(false);
  const [bulkHistoryTrendExporting, setBulkHistoryTrendExporting] = useState(false);
  const [batchDownloadTasks, setBatchDownloadTasks] = useState<BatchDownloadAsyncTask[]>([]);
  const [batchDownloadTasksLoading, setBatchDownloadTasksLoading] = useState(false);
  const [batchDownloadTaskError, setBatchDownloadTaskError] = useState<string | null>(null);
  const [batchDownloadStarting, setBatchDownloadStarting] = useState(false);
  const [batchDownloadTaskPage, setBatchDownloadTaskPage] = useState(0);
  const [batchDownloadTaskRowsPerPage, setBatchDownloadTaskRowsPerPage] = useState(BATCH_DOWNLOAD_TASK_LIST_LIMIT);
  const [batchDownloadTaskStatusFilter, setBatchDownloadTaskStatusFilter] = useState<BatchDownloadTaskStatusFilter>('ALL');
  const [batchDownloadTaskQuery, setBatchDownloadTaskQuery] = useState('');
  const [batchDownloadTaskQueryInput, setBatchDownloadTaskQueryInput] = useState('');
  const [batchDownloadTaskAutoRefresh, setBatchDownloadTaskAutoRefresh] = useState(true);
  const [batchDownloadTaskUpdatedAt, setBatchDownloadTaskUpdatedAt] = useState<string | null>(null);
  const [batchDownloadTaskActiveCount, setBatchDownloadTaskActiveCount] = useState(0);
  const [batchDownloadTaskPaging, setBatchDownloadTaskPaging] = useState({
    maxItems: BATCH_DOWNLOAD_TASK_LIST_LIMIT,
    skipCount: 0,
    totalItems: 0,
    hasMoreItems: false,
  });
  const previewOpen = Boolean(previewNode);

  const loadNodeData = useCallback(async () => {
    setLoadingStartedAt(Date.now());
    setLoadingWatchdogTriggered(false);
    await dispatch(fetchNode(nodeId));
    await dispatch(fetchChildren({ nodeId, sortBy, ascending: sortAscending, page, size: pageSize }));
  }, [dispatch, nodeId, sortAscending, sortBy, page, pageSize]);

  const loadBulkGovernanceData = useCallback(async () => {
    setBulkHistoryLoading(true);
    setBulkHistoryError(null);
    const [historyResult, summaryResult, trendResult] = await Promise.allSettled([
      bulkOperationService.listHistory({ limit: BULK_HISTORY_LIMIT }),
      bulkOperationService.listHistorySummary(),
      bulkOperationService.listHistoryTrend({ limit: BULK_HISTORY_TREND_LIMIT }),
    ]);

    if (historyResult.status === 'fulfilled') {
      setBulkHistory(historyResult.value.items);
    } else {
      setBulkHistory([]);
    }

    if (summaryResult.status === 'fulfilled') {
      setBulkHistorySummary(summaryResult.value);
    } else {
      setBulkHistorySummary(createEmptyBulkHistorySummary());
    }

    if (trendResult.status === 'fulfilled') {
      setBulkHistoryTrend(trendResult.value);
    } else {
      setBulkHistoryTrend(createEmptyBulkHistoryTrend());
    }

    if (
      historyResult.status === 'rejected'
      || summaryResult.status === 'rejected'
      || trendResult.status === 'rejected'
    ) {
      setBulkHistoryError('Failed to load bulk governance timeline');
    }
    setBulkHistoryLoading(false);
  }, []);

  const loadBatchDownloadTasks = useCallback(async (
    silent = false,
    statusFilter: BatchDownloadTaskStatusFilter = batchDownloadTaskStatusFilter,
    pageValue: number = batchDownloadTaskPage,
    rowsPerPage: number = batchDownloadTaskRowsPerPage,
    query: string = batchDownloadTaskQuery
  ): Promise<void> => {
    if (!silent) {
      setBatchDownloadTasksLoading(true);
    }
    setBatchDownloadTaskError(null);
    try {
      const response = await nodeService.listBatchDownloadAsyncTasks(
        rowsPerPage,
        statusFilter !== 'ALL' ? statusFilter : undefined,
        pageValue * rowsPerPage,
        query,
        user?.username
      );
      const items = Array.isArray(response?.items) ? response.items : [];
      const paging = {
        maxItems: Number.isFinite(response?.paging?.maxItems) ? Number(response?.paging?.maxItems) : rowsPerPage,
        skipCount: Number.isFinite(response?.paging?.skipCount) ? Number(response?.paging?.skipCount) : pageValue * rowsPerPage,
        totalItems: Number.isFinite(response?.paging?.totalItems) ? Number(response?.paging?.totalItems) : Number(response?.totalCount || items.length),
        hasMoreItems: Boolean(response?.paging?.hasMoreItems),
      };
      if (pageValue > 0 && items.length === 0 && paging.totalItems <= pageValue * rowsPerPage) {
        const previousPage = pageValue - 1;
        setBatchDownloadTaskPage(previousPage);
        return await loadBatchDownloadTasks(silent, statusFilter, previousPage, rowsPerPage, query);
      }
      setBatchDownloadTasks(items);
      setBatchDownloadTaskPaging(paging);
      setBatchDownloadTaskActiveCount(
        Number.isFinite(response?.activeCount)
          ? Number(response.activeCount)
          : items.filter((task) => ACTIVE_BATCH_DOWNLOAD_TASK_STATUSES.has((task.status || '').toUpperCase())).length
      );
      setBatchDownloadTaskUpdatedAt(new Date().toISOString());
    } catch {
      setBatchDownloadTasks([]);
      setBatchDownloadTaskPaging({
        maxItems: rowsPerPage,
        skipCount: pageValue * rowsPerPage,
        totalItems: 0,
        hasMoreItems: false,
      });
      setBatchDownloadTaskActiveCount(0);
      setBatchDownloadTaskError('Failed to load batch download tasks');
    } finally {
      if (!silent) {
        setBatchDownloadTasksLoading(false);
      }
    }
  }, [batchDownloadTaskPage, batchDownloadTaskQuery, batchDownloadTaskRowsPerPage, batchDownloadTaskStatusFilter, user?.username]);

  useEffect(() => {
    loadNodeData();
  }, [nodeId, loadNodeData]);

  useEffect(() => {
    if (!canWrite) {
      setBulkHistory([]);
      setBulkHistorySummary(createEmptyBulkHistorySummary());
      setBulkHistoryTrend(createEmptyBulkHistoryTrend());
      setBulkHistoryError(null);
      return;
    }
    void loadBulkGovernanceData();
  }, [canWrite, loadBulkGovernanceData]);

  useEffect(() => {
    void loadBatchDownloadTasks();
  }, [loadBatchDownloadTasks]);

  useEffect(() => {
    if (!batchDownloadTaskAutoRefresh) {
      return undefined;
    }
    if (!batchDownloadTasks.some((task) => ACTIVE_BATCH_DOWNLOAD_TASK_STATUSES.has((task.status || '').toUpperCase()))) {
      return undefined;
    }
    const timer = window.setTimeout(() => {
      void loadBatchDownloadTasks(true);
    }, BATCH_DOWNLOAD_AUTO_REFRESH_MS);
    return () => {
      window.clearTimeout(timer);
    };
  }, [batchDownloadTaskAutoRefresh, batchDownloadTasks, loadBatchDownloadTasks]);
  
  useEffect(() => {
    setPage(0);
  }, [nodeId, sortBy, sortAscending]);

  useEffect(() => {
    if (!loading) {
      setLoadingStartedAt(null);
      setLoadingWatchdogTriggered(false);
      return;
    }
    if (loadingStartedAt === null) {
      setLoadingStartedAt(Date.now());
    }
  }, [loading, loadingStartedAt]);

  useEffect(() => {
    if (!loading || loadingStartedAt === null || loadingWatchdogTriggered) {
      return undefined;
    }
    const elapsedMs = Date.now() - loadingStartedAt;
    const remainingMs = FILE_BROWSER_LOADING_WATCHDOG_MS - elapsedMs;
    if (remainingMs <= 0) {
      setLoadingWatchdogTriggered(true);
      return undefined;
    }
    const timer = window.setTimeout(() => {
      setLoadingWatchdogTriggered(true);
    }, remainingMs);
    return () => {
      window.clearTimeout(timer);
    };
  }, [loading, loadingStartedAt, loadingWatchdogTriggered]);

  const isDocumentNode = (node: Node) => {
    if (node.nodeType === 'DOCUMENT') {
      return true;
    }
    if (node.nodeType === 'FOLDER') {
      return false;
    }
    const name = node.name?.toLowerCase() || '';
    const contentTypeHint = node.contentType
      || node.properties?.mimeType
      || node.properties?.contentType;
    const sizeHint = node.size
      || node.properties?.fileSize
      || node.properties?.size;
    const hasExtension = name.includes('.') && !name.endsWith('.');
    return Boolean(contentTypeHint || sizeHint || node.currentVersionLabel || hasExtension);
  };

  const handleNodeDoubleClick = (node: Node) => {
    if (node.nodeType === 'FOLDER') {
      navigate(`/browse/${node.id}`);
      if (sidebarAutoCollapse) {
        dispatch(setSidebarOpen(false));
      }
      return;
    }
    if (isDocumentNode(node)) {
      setPreviewAnnotate(false);
      setPreviewNode(node);
    }
  };

  const handlePreviewNode = (node: Node, options?: { annotate?: boolean }) => {
    if (isDocumentNode(node)) {
      setPreviewAnnotate(Boolean(options?.annotate));
      setPreviewNode(node);
    }
  };

  const handleClosePreview = () => {
    setPreviewNode(null);
    setPreviewAnnotate(false);
  };

  const handleNavigate = (path: string) => {
    if (path === '/') {
      navigate('/browse/root');
      if (sidebarAutoCollapse) {
        dispatch(setSidebarOpen(false));
      }
      return;
    }

    void (async () => {
      try {
        const targetFolder = await nodeService.getFolderByPath(path);
        navigate(`/browse/${targetFolder.id}`);
        if (sidebarAutoCollapse) {
          dispatch(setSidebarOpen(false));
        }
      } catch (error) {
        console.error('Failed to navigate by path:', error);
        toast.error('Failed to navigate to folder');
      }
    })();
  };

  const handleViewModeChange = (_: any, newMode: 'list' | 'grid' | null) => {
    if (newMode !== null) {
      dispatch(setViewMode(newMode));
    }
  };

  const handleDelete = async () => {
    if (selectedNodes.length === 0) return;

    if (!window.confirm(`Delete ${selectedNodes.length} selected item(s)?`)) {
      return;
    }

    const ids = [...selectedNodes];
    try {
      const result = await bulkOperationService.bulkDelete(ids);
      const successCount = Number.isFinite(result.successCount)
        ? result.successCount
        : Array.isArray(result.successfulIds)
          ? result.successfulIds.length
          : 0;
      const fallbackFailureCount = Math.max(0, ids.length - successCount);
      const failureCount = Number.isFinite(result.failureCount)
        ? result.failureCount
        : (result.failures ? Object.keys(result.failures).length : fallbackFailureCount);

      if (successCount > 0 && failureCount > 0) {
        toast.warn(`Deleted ${successCount} item(s); ${failureCount} failed`);
      } else if (successCount > 0) {
        toast.success(`Deleted ${successCount} item(s)`);
      } else {
        toast.error(`Failed to delete ${failureCount || ids.length} item(s)`);
      }

      dispatch(clearSelection());
      await loadNodeData();
    } catch {
      toast.error('Failed to delete items');
    } finally {
      await loadBulkGovernanceData();
    }
  };

  const handleBatchDownload = async () => {
    if (selectedNodes.length === 0) {
      return;
    }

    const baseName = currentNode?.name ? `${currentNode.name}-selection` : 'selection';
    let effectiveNodeIds = selectedNodes;

    try {
      setBatchDownloadStarting(true);
      const preflight = await nodeService.preflightBatchDownloadAsync(selectedNodes, baseName);
      if (!preflight.executable || preflight.includedNodeIds.length === 0 || preflight.includedFileCount === 0) {
        toast.error(buildBatchDownloadPreflightToast(preflight));
        return;
      }
      if (preflight.warnings.length > 0 || preflight.skippedCount > 0 || preflight.duplicateCount > 0) {
        toast.warn(buildBatchDownloadPreflightToast(preflight));
      }

      effectiveNodeIds = preflight.includedNodeIds.length > 0 ? preflight.includedNodeIds : selectedNodes;
      const task = await nodeService.startBatchDownloadAsync(effectiveNodeIds, baseName);
      toast.success(`Batch download queued: ${task.filename}`);
      setBatchDownloadTaskPage(0);
      await loadBatchDownloadTasks(true, batchDownloadTaskStatusFilter, 0, batchDownloadTaskRowsPerPage, batchDownloadTaskQuery);
    } catch {
      try {
        await nodeService.downloadNodesAsZip(effectiveNodeIds, baseName);
        toast.success('Async batch download unavailable, started direct download');
      } catch {
        toast.error('Failed to download selection');
      }
    } finally {
      setBatchDownloadStarting(false);
    }
  };

  const handleRefreshBatchDownloadTasks = () => {
    void loadBatchDownloadTasks();
  };

  const handleCancelBatchDownloadTask = async (taskId: string) => {
    try {
      await nodeService.cancelBatchDownloadAsyncTask(taskId);
      toast.success('Batch download task updated');
      await loadBatchDownloadTasks(true);
    } catch {
      toast.error('Failed to cancel batch download task');
    }
  };

  const handleCleanupBatchDownloadTask = async (taskId: string) => {
    try {
      await nodeService.cleanupBatchDownloadAsyncTask(taskId);
      toast.success('Batch download task removed from your task center');
      await loadBatchDownloadTasks(true);
    } catch {
      toast.error('Failed to clean up batch download task');
    }
  };

  const handleDownloadBatchDownloadTask = async (task: BatchDownloadAsyncTask) => {
    try {
      await nodeService.downloadBatchDownloadAsyncTask(task.taskId, task.filename);
      toast.success('Batch download started');
    } catch {
      toast.error('Failed to download ZIP artifact');
    }
  };

  const handleBatchDownloadTaskPrevPage = () => {
    if (batchDownloadTaskPage <= 0 || batchDownloadTasksLoading) {
      return;
    }
    const previousPage = batchDownloadTaskPage - 1;
    setBatchDownloadTaskPage(previousPage);
    void loadBatchDownloadTasks(true, batchDownloadTaskStatusFilter, previousPage, batchDownloadTaskRowsPerPage, batchDownloadTaskQuery);
  };

  const handleBatchDownloadTaskNextPage = () => {
    if (!batchDownloadTaskPaging.hasMoreItems || batchDownloadTasksLoading) {
      return;
    }
    const nextPage = batchDownloadTaskPage + 1;
    setBatchDownloadTaskPage(nextPage);
    void loadBatchDownloadTasks(true, batchDownloadTaskStatusFilter, nextPage, batchDownloadTaskRowsPerPage, batchDownloadTaskQuery);
  };

  const handleBatchDownloadTaskRowsPerPageChange = (value: number) => {
    const nextRows = Number.isFinite(value) ? value : BATCH_DOWNLOAD_TASK_LIST_LIMIT;
    setBatchDownloadTaskRowsPerPage(nextRows);
    setBatchDownloadTaskPage(0);
    void loadBatchDownloadTasks(true, batchDownloadTaskStatusFilter, 0, nextRows, batchDownloadTaskQuery);
  };

  const handleApplyBatchDownloadTaskQuery = () => {
    const nextQuery = batchDownloadTaskQueryInput.trim();
    setBatchDownloadTaskQuery(nextQuery);
    setBatchDownloadTaskPage(0);
    void loadBatchDownloadTasks(true, batchDownloadTaskStatusFilter, 0, batchDownloadTaskRowsPerPage, nextQuery);
  };

  const handleClearBatchDownloadTaskQuery = () => {
    setBatchDownloadTaskQueryInput('');
    setBatchDownloadTaskQuery('');
    setBatchDownloadTaskPage(0);
    void loadBatchDownloadTasks(true, batchDownloadTaskStatusFilter, 0, batchDownloadTaskRowsPerPage, '');
  };

  const handleRetryLoad = () => {
    void loadNodeData();
  };

  const handleBackToRoot = () => {
    if (nodeId === 'root') {
      void loadNodeData();
      return;
    }
    navigate('/browse/root');
    if (sidebarAutoCollapse) {
      dispatch(setSidebarOpen(false));
    }
  };

  const handleRefreshBulkHistory = () => {
    void loadBulkGovernanceData();
  };

  const handleExportBulkHistoryCsv = async () => {
    setBulkHistoryExporting(true);
    try {
      await bulkOperationService.exportHistoryCsv({ limit: BULK_HISTORY_EXPORT_LIMIT });
      toast.success('Bulk governance timeline CSV exported');
    } catch {
      toast.error('Failed to export bulk governance timeline CSV');
    } finally {
      setBulkHistoryExporting(false);
    }
  };

  const handleExportBulkHistorySummaryCsv = async () => {
    setBulkHistorySummaryExporting(true);
    try {
      await bulkOperationService.exportHistorySummaryCsv();
      toast.success('Bulk governance summary CSV exported');
    } catch {
      toast.error('Failed to export bulk governance summary CSV');
    } finally {
      setBulkHistorySummaryExporting(false);
    }
  };

  const handleExportBulkHistoryTrendCsv = async () => {
    setBulkHistoryTrendExporting(true);
    try {
      await bulkOperationService.exportHistoryTrendCsv({ limit: BULK_HISTORY_EXPORT_LIMIT });
      toast.success('Bulk governance trend CSV exported');
    } catch {
      toast.error('Failed to export bulk governance trend CSV');
    } finally {
      setBulkHistoryTrendExporting(false);
    }
  };

  const renderLoadingWatchdogAlert = () => {
    if (!loadingWatchdogTriggered) {
      return null;
    }
    return (
      <Alert
        severity="warning"
        data-testid="filebrowser-loading-watchdog-alert"
        action={(
          <Box sx={{ display: 'flex', gap: 1 }}>
            <Button
              color="inherit"
              size="small"
              onClick={handleRetryLoad}
              data-testid="filebrowser-loading-watchdog-retry"
            >
              Retry
            </Button>
            <Button
              color="inherit"
              size="small"
              onClick={handleBackToRoot}
              data-testid="filebrowser-loading-watchdog-back-root"
            >
              Back to root
            </Button>
          </Box>
        )}
      >
        Folder loading is taking longer than expected. You can retry or go back to root.
      </Alert>
    );
  };

  if (loading && !currentNode) {
    return (
      <Box display="flex" flexDirection="column" justifyContent="center" alignItems="center" gap={2} height="400px">
        <CircularProgress data-testid="filebrowser-loading-spinner" />
        {renderLoadingWatchdogAlert()}
      </Box>
    );
  }

  const batchDownloadTaskListedFrom = batchDownloadTaskPaging.totalItems === 0
    ? 0
    : batchDownloadTaskPaging.skipCount + 1;
  const batchDownloadTaskListedTo = Math.min(
    batchDownloadTaskPaging.skipCount + batchDownloadTasks.length,
    batchDownloadTaskPaging.totalItems
  );

  if (!currentNode) {
    return (
      <Box display="flex" justifyContent="center" alignItems="center" height="400px">
        <Typography variant="h6" color="text.secondary">
          Folder not found
        </Typography>
      </Box>
    );
  }

  return (
    <Box>
      <Paper sx={{ p: compactMode ? 1.5 : 2, mb: compactMode ? 1.5 : 2 }}>
        <Box display="flex" alignItems="center" justifyContent="space-between">
          <FileBreadcrumb path={currentNode.path} onNavigate={handleNavigate} />
          
          <Box display="flex" alignItems="center" gap={1}>
            {selectedNodes.length > 0 && (
              <>
                <Typography variant="body2" color="text.secondary" sx={{ mr: 2 }}>
              {selectedNodes.length} selected
            </Typography>
            <IconButton
              onClick={handleBatchDownload}
              color="primary"
              size="small"
              aria-label="Download selected"
              disabled={batchDownloadStarting}
            >
              <Download />
            </IconButton>
            {canWrite && (
              <IconButton
                onClick={() => setBulkMetadataOpen(true)}
                color="primary"
                size="small"
                aria-label="Edit metadata for selected"
              >
                <EditNote />
              </IconButton>
            )}
            {canWrite && (
              <IconButton onClick={handleDelete} color="error" size="small" aria-label="Delete selected">
                <Delete />
              </IconButton>
            )}
              </>
            )}
            
            <ToggleButtonGroup
              value={viewMode}
              exclusive
              onChange={handleViewModeChange}
              size="small"
            >
              <ToggleButton value="list" aria-label="list view">
                <ViewList />
              </ToggleButton>
              <ToggleButton value="grid" aria-label="grid view">
                <ViewModule />
              </ToggleButton>
            </ToggleButtonGroup>
          </Box>
        </Box>
      </Paper>

      <Paper>
        {loading && loadingWatchdogTriggered && (
          <Box sx={{ p: 2 }}>
            {renderLoadingWatchdogAlert()}
          </Box>
        )}
        {error && !loading && (
          <Alert
            severity="warning"
            action={(
              <Button color="inherit" size="small" onClick={handleRetryLoad}>
                Retry
              </Button>
            )}
            sx={{ m: 2 }}
          >
            {error}
          </Alert>
        )}
        {loading ? (
          <Box display="flex" justifyContent="center" p={4}>
            <CircularProgress />
          </Box>
        ) : nodes.length === 0 ? (
          <Box p={4} textAlign="center">
            <Typography variant="body1" color="text.secondary">
              This folder is empty
            </Typography>
          </Box>
        ) : (
          <FileList
            nodes={nodes}
            onNodeDoubleClick={handleNodeDoubleClick}
            onPreviewNode={handlePreviewNode}
            page={page}
            pageSize={pageSize}
            totalCount={nodesTotal}
            onPageChange={setPage}
            onPageSizeChange={(size) => {
              setPageSize(size);
              setPage(0);
            }}
          />
        )}
      </Paper>

      {(selectedNodes.length > 0 || batchDownloadTasks.length > 0 || batchDownloadTasksLoading || Boolean(batchDownloadTaskError)) && (
        <Paper sx={{ p: compactMode ? 1.5 : 2, mt: compactMode ? 1.5 : 2 }}>
          <Box
            display="flex"
            alignItems={{ xs: 'flex-start', md: 'center' }}
            justifyContent="space-between"
            flexDirection={{ xs: 'column', md: 'row' }}
            gap={1}
            mb={2}
          >
            <Box display="flex" alignItems="center" gap={1} flexWrap="wrap">
              <Typography variant="h6">My Batch Download Tasks</Typography>
              {user?.username && (
                <Chip size="small" variant="outlined" label={`Owner ${user.username}`} />
              )}
              <Chip size="small" variant="outlined" label={`Total ${batchDownloadTaskPaging.totalItems}`} />
              <Chip size="small" color="info" variant="outlined" label={`Active ${batchDownloadTaskActiveCount}`} />
              {batchDownloadTaskQuery && (
                <Chip size="small" color="primary" variant="outlined" label={`Query ${batchDownloadTaskQuery}`} />
              )}
            </Box>
            <Box display="flex" alignItems="center" gap={1} flexWrap="wrap">
              <TextField
                size="small"
                label="Search tasks"
                placeholder="Filename or task ID"
                value={batchDownloadTaskQueryInput}
                onChange={(event) => setBatchDownloadTaskQueryInput(event.target.value)}
                onKeyDown={(event) => {
                  if (event.key === 'Enter') {
                    event.preventDefault();
                    handleApplyBatchDownloadTaskQuery();
                  }
                }}
                sx={{ minWidth: 200 }}
              />
              <Button
                variant="outlined"
                size="small"
                onClick={handleApplyBatchDownloadTaskQuery}
                disabled={batchDownloadTasksLoading}
              >
                Search
              </Button>
              <Button
                variant="text"
                size="small"
                onClick={handleClearBatchDownloadTaskQuery}
                disabled={batchDownloadTasksLoading && !batchDownloadTaskQuery}
              >
                Clear
              </Button>
              <FormControl size="small" sx={{ minWidth: 160 }}>
                <InputLabel id="file-browser-batch-download-status-filter-label">Task status</InputLabel>
                <Select
                  labelId="file-browser-batch-download-status-filter-label"
                  value={batchDownloadTaskStatusFilter}
                  label="Task status"
                  onChange={(event) => {
                    const nextFilter = event.target.value as BatchDownloadTaskStatusFilter;
                    setBatchDownloadTaskStatusFilter(nextFilter);
                    setBatchDownloadTaskPage(0);
                    void loadBatchDownloadTasks(true, nextFilter, 0, batchDownloadTaskRowsPerPage, batchDownloadTaskQuery);
                  }}
                >
                  {BATCH_DOWNLOAD_TASK_FILTER_OPTIONS.map((option) => (
                    <MenuItem key={`file-browser-batch-download-status-${option.value}`} value={option.value}>
                      {option.label}
                    </MenuItem>
                  ))}
                </Select>
              </FormControl>
              <Button
                variant="outlined"
                size="small"
                onClick={handleRefreshBatchDownloadTasks}
                disabled={batchDownloadTasksLoading}
                startIcon={<Refresh fontSize="small" />}
              >
                Refresh
              </Button>
              <FormControlLabel
                label="Auto refresh"
                control={(
                  <Switch
                    size="small"
                    checked={batchDownloadTaskAutoRefresh}
                    onChange={(event) => setBatchDownloadTaskAutoRefresh(event.target.checked)}
                  />
                )}
              />
            </Box>
          </Box>

          {batchDownloadTaskUpdatedAt && (
            <Typography variant="caption" color="text.secondary" sx={{ display: 'block', mb: 1 }}>
              Last updated {formatBulkHistoryTime(batchDownloadTaskUpdatedAt)}
              {batchDownloadTaskAutoRefresh ? ` · auto refresh every ${BATCH_DOWNLOAD_AUTO_REFRESH_MS / 1000}s` : ''}
            </Typography>
          )}

          <Box
            display="flex"
            alignItems={{ xs: 'flex-start', md: 'center' }}
            justifyContent="space-between"
            flexDirection={{ xs: 'column', md: 'row' }}
            gap={1}
            mb={1}
          >
            <Box display="flex" alignItems="center" gap={1} flexWrap="wrap">
              <FormControl size="small" sx={{ minWidth: 120 }}>
                <InputLabel id="file-browser-batch-download-rows-label">Rows / page</InputLabel>
                <Select
                  labelId="file-browser-batch-download-rows-label"
                  value={batchDownloadTaskRowsPerPage}
                  label="Rows / page"
                  onChange={(event) => handleBatchDownloadTaskRowsPerPageChange(Number(event.target.value))}
                >
                  {BATCH_DOWNLOAD_TASK_ROW_OPTIONS.map((option) => (
                    <MenuItem key={`file-browser-batch-download-rows-${option}`} value={option}>
                      {option}
                    </MenuItem>
                  ))}
                </Select>
              </FormControl>
              <Typography variant="body2" color="text.secondary">
                Listed {batchDownloadTaskListedFrom}-{batchDownloadTaskListedTo} of {batchDownloadTaskPaging.totalItems}
              </Typography>
            </Box>
            <Box display="flex" alignItems="center" gap={1}>
              <Button
                size="small"
                variant="outlined"
                onClick={handleBatchDownloadTaskPrevPage}
                disabled={batchDownloadTaskPage <= 0 || batchDownloadTasksLoading}
              >
                Prev
              </Button>
              <Typography variant="body2" color="text.secondary">
                Page {batchDownloadTaskPage + 1}
              </Typography>
              <Button
                size="small"
                variant="outlined"
                onClick={handleBatchDownloadTaskNextPage}
                disabled={!batchDownloadTaskPaging.hasMoreItems || batchDownloadTasksLoading}
              >
                Next
              </Button>
            </Box>
          </Box>

          {batchDownloadTaskError && (
            <Alert severity="warning" sx={{ mb: 2 }}>
              {batchDownloadTaskError}
            </Alert>
          )}

          {batchDownloadTasksLoading ? (
            <Box display="flex" justifyContent="center" py={3}>
              <CircularProgress size={24} />
            </Box>
          ) : batchDownloadTasks.length === 0 ? (
            <Alert severity="info">No batch download tasks recorded for the current user yet.</Alert>
          ) : (
            <Table size="small" aria-label="Batch download task list">
              <TableHead>
                <TableRow>
                  <TableCell>Filename</TableCell>
                  <TableCell>Status</TableCell>
                  <TableCell>Progress</TableCell>
                  <TableCell>Created</TableCell>
                  <TableCell align="right">Actions</TableCell>
                </TableRow>
              </TableHead>
              <TableBody>
                {batchDownloadTasks.map((task) => (
                  <TableRow key={task.taskId}>
                    <TableCell>{task.filename}</TableCell>
                    <TableCell>
                      <Chip
                        size="small"
                        color={task.downloadReady ? 'success' : task.status === 'FAILED' ? 'error' : 'default'}
                        variant="outlined"
                        label={task.status}
                      />
                    </TableCell>
                    <TableCell>
                      <Typography variant="caption" color="text.secondary">
                        Files {task.filesAdded}/{task.totalFiles || 0} • Bytes {formatBytes(task.bytesAdded)}/{formatBytes(task.totalBytes)}
                      </Typography>
                      {task.errorMessage && (
                        <Typography variant="caption" color="error" display="block">
                          {task.errorMessage}
                        </Typography>
                      )}
                    </TableCell>
                    <TableCell>{formatBulkHistoryTime(task.createdAt)}</TableCell>
                    <TableCell align="right">
                      <Box display="flex" justifyContent="flex-end" gap={1}>
                        {task.downloadReady && (
                          <Button
                            variant="outlined"
                            size="small"
                            onClick={() => handleDownloadBatchDownloadTask(task)}
                          >
                            Download
                          </Button>
                        )}
                        {task.cancellable && (
                          <Button
                            variant="outlined"
                            size="small"
                            color="warning"
                            onClick={() => handleCancelBatchDownloadTask(task.taskId)}
                          >
                            Cancel
                          </Button>
                        )}
                        {task.cleanupEligible && (
                          <Button
                            variant="text"
                            size="small"
                            color="secondary"
                            onClick={() => handleCleanupBatchDownloadTask(task.taskId)}
                          >
                            Dismiss
                          </Button>
                        )}
                      </Box>
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          )}
        </Paper>
      )}

      {canWrite && (
        <Paper sx={{ p: compactMode ? 1.5 : 2, mt: compactMode ? 1.5 : 2 }}>
          <Box display="flex" alignItems="center" justifyContent="space-between" mb={2}>
            <Typography variant="h6">Bulk Governance Timeline</Typography>
            <Box display="flex" alignItems="center" gap={1}>
              <Button
                variant="outlined"
                size="small"
                onClick={handleRefreshBulkHistory}
                disabled={bulkHistoryLoading}
                startIcon={<Refresh fontSize="small" />}
              >
                Refresh
              </Button>
              <Button
                variant="outlined"
                size="small"
                onClick={handleExportBulkHistoryCsv}
                disabled={bulkHistoryExporting}
              >
                {bulkHistoryExporting ? 'Exporting...' : 'Export CSV'}
              </Button>
              <Button
                variant="outlined"
                size="small"
                onClick={handleExportBulkHistorySummaryCsv}
                disabled={bulkHistorySummaryExporting}
              >
                {bulkHistorySummaryExporting ? 'Exporting...' : 'Export Summary CSV'}
              </Button>
              <Button
                variant="outlined"
                size="small"
                onClick={handleExportBulkHistoryTrendCsv}
                disabled={bulkHistoryTrendExporting}
              >
                {bulkHistoryTrendExporting ? 'Exporting...' : 'Export Trend CSV'}
              </Button>
            </Box>
          </Box>

          <Box mb={2}>
            <Typography variant="subtitle2" color="text.secondary" gutterBottom>
              Summary
            </Typography>
            <Box display="flex" alignItems="center" gap={1} flexWrap="wrap" mb={1}>
              <Chip size="small" variant="outlined" label={`Total events ${bulkHistorySummary.total}`} />
            </Box>
            <Box display="flex" alignItems="center" gap={1} flexWrap="wrap" mb={1}>
              {bulkHistorySummary.eventTypeItems.length === 0 ? (
                <Chip size="small" variant="outlined" label="No event type summary" />
              ) : (
                bulkHistorySummary.eventTypeItems.slice(0, BULK_HISTORY_SUMMARY_TOP_LIMIT).map((item, index) => (
                  <Chip
                    key={`event-type-${item.eventType || 'unknown'}-${index}`}
                    size="small"
                    variant="outlined"
                    color="info"
                    label={`${item.eventType || 'UNKNOWN'} ${item.count}`}
                  />
                ))
              )}
            </Box>
            <Box display="flex" alignItems="center" gap={1} flexWrap="wrap">
              {bulkHistorySummary.actorItems.length === 0 ? (
                <Chip size="small" variant="outlined" label="No actor summary" />
              ) : (
                bulkHistorySummary.actorItems.slice(0, BULK_HISTORY_SUMMARY_TOP_LIMIT).map((item, index) => (
                  <Chip
                    key={`actor-${item.actor || 'unknown'}-${index}`}
                    size="small"
                    variant="outlined"
                    color="secondary"
                    label={`${item.actor || 'unknown'} ${item.count}`}
                  />
                ))
              )}
            </Box>
            <Box mt={1.5}>
              <Typography variant="subtitle2" color="text.secondary" gutterBottom>
                Trend
              </Typography>
              <Box display="flex" alignItems="center" gap={1} flexWrap="wrap">
                {bulkHistoryTrend.items.length === 0 ? (
                  <Chip size="small" variant="outlined" label="No trend data" />
                ) : (
                  bulkHistoryTrend.items.slice(-BULK_HISTORY_TREND_LIMIT).reverse().map((item, index) => (
                    <Chip
                      key={`trend-${item.date || 'unknown'}-${index}`}
                      size="small"
                      variant="outlined"
                      color="success"
                      label={`${formatBulkHistoryTrendDate(item.date)} ${item.count}`}
                    />
                  ))
                )}
                {bulkHistoryTrend.truncated && (
                  <Chip size="small" variant="outlined" color="warning" label="Trend truncated" />
                )}
              </Box>
            </Box>
          </Box>

          {bulkHistoryError && (
            <Alert severity="warning" sx={{ mb: 2 }}>
              {bulkHistoryError}
            </Alert>
          )}

          {bulkHistoryLoading ? (
            <Box display="flex" justifyContent="center" py={3}>
              <CircularProgress size={24} />
            </Box>
          ) : bulkHistory.length === 0 ? (
            <Alert severity="info">No bulk governance events found.</Alert>
          ) : (
            <Table size="small" aria-label="Bulk governance timeline">
              <TableHead>
                <TableRow>
                  <TableCell>Event Type</TableCell>
                  <TableCell>Actor</TableCell>
                  <TableCell>Time</TableCell>
                  <TableCell>Details</TableCell>
                </TableRow>
              </TableHead>
              <TableBody>
                {bulkHistory.map((event, index) => {
                  const key = event.id || `${event.eventType || 'event'}-${event.time || 'time'}-${index}`;
                  return (
                    <TableRow key={key}>
                      <TableCell>{event.eventType || '-'}</TableCell>
                      <TableCell>{event.actor || '-'}</TableCell>
                      <TableCell>{formatBulkHistoryTime(event.time)}</TableCell>
                      <TableCell sx={{ whiteSpace: 'pre-wrap', wordBreak: 'break-word' }}>
                        {event.details || '-'}
                      </TableCell>
                    </TableRow>
                  );
                })}
              </TableBody>
            </Table>
          )}
        </Paper>
      )}

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

      <BulkMetadataDialog
        open={bulkMetadataOpen}
        nodeIds={selectedNodes}
        onClose={() => setBulkMetadataOpen(false)}
        onApplied={async () => {
          await loadNodeData();
          await loadBulkGovernanceData();
        }}
      />
    </Box>
  );
};

export default FileBrowser;
