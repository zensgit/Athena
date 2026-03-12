import React, { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import {
  Alert,
  Box,
  Button,
  Checkbox,
  Chip,
  CircularProgress,
  FormControl,
  FormControlLabel,
  IconButton,
  MenuItem,
  Paper,
  Select,
  Stack,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  TextField,
  Tooltip,
  Typography,
} from '@mui/material';
import {
  Autorenew as RetryIcon,
  Build as ForceIcon,
  ContentCopy as CopyIcon,
  DeleteOutline as ClearIcon,
  Download as DownloadIcon,
  FolderOpen as FolderOpenIcon,
  LockOpen as UnblockIcon,
  Refresh as RefreshIcon,
  Replay as RequeueIcon,
  Restore as RollbackIcon,
  Search as SearchIcon,
} from '@mui/icons-material';
import { format } from 'date-fns';
import { toast } from 'react-toastify';
import { useNavigate } from 'react-router-dom';
import nodeService from 'services/nodeService';
import opsPolicyService, { OpsPolicyHistoryEntry, OpsPolicyProfile } from 'services/opsPolicyService';
import opsRecoveryService, {
  RecoveryHistoryExportAsyncActiveStatusFilter,
  RecoveryHistoryExportAsyncTaskSummary,
  RecoveryHistoryExportAsyncStatusFilter,
  RecoveryHistoryCompareActorSort,
  RecoveryHistoryCompareBreakdownSort,
  RecoveryHistoryExportAsyncTaskStatus,
  RecoveryHistoryExportAsyncType,
  RecoveryHistoryActorSummaryItem,
  RecoveryDryRunResult,
  RecoveryHistoryEventType,
  RecoveryHistoryItem,
  RecoveryHistoryMode,
  RecoveryHistorySummaryCompareActorItem,
  RecoveryHistorySummaryCompareBreakdownItem,
  RecoveryHistorySummaryItem,
  RecoveryHistoryTrendItem,
} from 'services/opsRecoveryService';
import previewDiagnosticsService, {
  PreviewCadFailoverDiagnostics,
  PreviewDeadLetterDiagnostics,
  PreviewFailureLedgerDiagnostics,
  PreviewQueueDeclinedExportTaskActiveStatusFilter,
  PreviewQueueDeclinedExportTask,
  PreviewQueueDeclinedExportTaskRetryTerminalDryRunResponse,
  PreviewQueueDeclinedExportTaskStatusFilter,
  PreviewQueueDeclinedExportTaskTerminalStatusFilter,
  PreviewQueueDeclinedExportTaskSummary,
  PreviewQueueDeclinedRequeueDryRunExportTaskActiveStatusFilter,
  PreviewQueueDeclinedRequeueDryRunExportTask,
  PreviewQueueDeclinedRequeueDryRunExportTaskRetryTerminalDryRunResponse,
  PreviewQueueDeclinedRequeueDryRunExportTaskStatusFilter,
  PreviewQueueDeclinedRequeueDryRunExportTaskTerminalStatusFilter,
  PreviewQueueDeclinedRequeueDryRunExportTaskSummary,
  PreviewQueueDeclinedRequeueDryRunResult,
  PreviewQueueDeclinedSummary,
  PreviewQueueDeclinedWindowHours,
  PreviewQueueDiagnosticsStateFilter,
  PreviewQueueDiagnosticsSummary,
  PreviewFailureSample,
  PreviewRenditionResourcesExportTaskActiveStatusFilter,
  PreviewFailureSummary as PreviewFailureSummaryDto,
  PreviewRenditionResource,
  PreviewRenditionResourcesExportTask,
  PreviewRenditionResourcesExportTaskStatusFilter,
  PreviewRenditionResourcesExportTaskSummary,
  PreviewRenditionSummary,
  PreviewRenditionPreventionDiagnostics,
  PreviewTransformTrace,
} from 'services/previewDiagnosticsService';
import {
  formatPreviewFailureReasonLabel,
  getFailedPreviewMeta,
  isRetryablePreviewFailure,
  normalizePreviewFailureReason,
  summarizeFailedPreviews,
} from 'utils/previewStatusUtils';

const LIMIT_OPTIONS = [25, 50, 100, 200] as const;
const TRACE_LIMIT_OPTIONS = [10, 20, 50] as const;
const BACKEND_SUMMARY_SAMPLE_LIMIT = 500;
const RENDITION_TOP_REASONS_LIMIT = 5;
const RENDITION_EXPORT_TASK_LIMIT = 10;
const PREVENTION_LIST_LIMIT = 100;
const DEAD_LETTER_LIST_LIMIT = 100;
const FAILURE_LEDGER_LIST_LIMIT = 100;
const FAILURE_LEDGER_EXPORT_LIMIT = 500;
const QUEUE_DIAGNOSTICS_LIMIT = 20;
const QUEUE_DIAGNOSTICS_EXPORT_LIMIT = 200;
const QUEUE_DECLINED_LIMIT = 20;
const QUEUE_DECLINED_EXPORT_LIMIT = 500;
const QUEUE_DECLINED_EXPORT_TASK_PAGE_SIZE_OPTIONS = [10, 20, 50] as const;
const QUEUE_DECLINED_EXPORT_TASK_DEFAULT_MAX_ITEMS = 20;
const QUEUE_DECLINED_REQUEUE_DRY_RUN_EXPORT_TASK_DEFAULT_MAX_ITEMS = 20;
const ASYNC_TASK_POLL_BASE_MS = 2000;
const ASYNC_TASK_POLL_MAX_MS = 15000;
const QUEUE_DIAGNOSTICS_STATE_FILTER_OPTIONS: Array<{
  value: PreviewQueueDiagnosticsStateFilter;
  label: string;
}> = [
  { value: 'ALL', label: 'All states' },
  { value: 'QUEUED', label: 'Queued' },
  { value: 'RUNNING', label: 'Running' },
  { value: 'CANCEL_REQUESTED', label: 'Cancel requested' },
];
const QUEUE_DECLINED_FORCE_REQUIRED_FILTER_OPTIONS = [
  { value: 'ANY', label: 'Force required: any' },
  { value: 'YES', label: 'Force required: yes' },
  { value: 'NO', label: 'Force required: no' },
] as const;
const QUEUE_DECLINED_WINDOW_HOURS_OPTIONS = [
  { value: 0, label: 'Any' },
  { value: 1, label: '1h' },
  { value: 6, label: '6h' },
  { value: 24, label: '24h' },
  { value: 168, label: '7d' },
] as const;
const RECOVERY_HISTORY_EXPORT_LIMIT = 500;
const RECOVERY_HISTORY_EXPORT_ASYNC_TASK_LIMIT = 20;
const RECOVERY_HISTORY_PAGE_LIMIT = 20;
const RECOVERY_HISTORY_AUTO_REFRESH_OPTIONS = [15, 30, 60] as const;
const WINDOW_DAY_OPTIONS = [
  { value: 7, label: 'Last 7 days' },
  { value: 30, label: 'Last 30 days' },
  { value: 0, label: 'All time' },
] as const;
const DRY_RUN_MODE_OPTIONS = [
  { value: 'QUEUE_BY_WINDOW', label: 'Queue by Window' },
  { value: 'QUEUE_BY_REASON', label: 'Queue by Reason' },
  { value: 'REPLAY_BY_FILTER', label: 'Replay by Filter' },
  { value: 'CLEAR_BY_FILTER', label: 'Clear by Filter' },
] as const;
const DRY_RUN_CATEGORY_OPTIONS = ['ANY', 'TEMPORARY', 'PERMANENT', 'UNSUPPORTED'] as const;
const DRY_RUN_RETRYABLE_OPTIONS = ['ANY', 'YES', 'NO'] as const;
const RECOVERY_HISTORY_MODE_OPTIONS = [
  { value: 'ALL', label: 'All modes' },
  { value: 'QUEUE_BY_WINDOW', label: 'Queue by Window' },
  { value: 'QUEUE_BY_REASON', label: 'Queue by Reason' },
  { value: 'CLEAR_BATCH', label: 'Clear Batch' },
  { value: 'CLEAR_BY_FILTER', label: 'Clear by Filter' },
  { value: 'REPLAY_BATCH', label: 'Replay Batch' },
  { value: 'REPLAY_BY_FILTER', label: 'Replay by Filter' },
  { value: 'DRY_RUN', label: 'Dry-run' },
] as const;
const RECOVERY_HISTORY_EVENT_TYPE_OPTIONS = [
  { value: 'ALL', label: 'All event types' },
  { value: 'OPS_RECOVERY_QUEUE_BY_WINDOW', label: 'Queue by Window Event' },
  { value: 'OPS_RECOVERY_QUEUE_BY_REASON', label: 'Queue by Reason Event' },
  { value: 'OPS_RECOVERY_CLEAR_BATCH', label: 'Clear Batch Event' },
  { value: 'OPS_RECOVERY_CLEAR_BY_FILTER', label: 'Clear by Filter Event' },
  { value: 'OPS_RECOVERY_REPLAY_BATCH', label: 'Replay Batch Event' },
  { value: 'OPS_RECOVERY_REPLAY_BY_FILTER', label: 'Replay by Filter Event' },
  { value: 'OPS_RECOVERY_DRY_RUN', label: 'Dry-run Event' },
  { value: 'OPS_RECOVERY_HISTORY_EXPORT', label: 'History Export Event' },
  { value: 'OPS_RECOVERY_HISTORY_SUMMARY_EXPORT', label: 'History Summary Export Event' },
  { value: 'OPS_RECOVERY_HISTORY_TREND_EXPORT', label: 'History Trend Export Event' },
  { value: 'OPS_RECOVERY_HISTORY_COMPARE_EXPORT', label: 'History Compare Export Event' },
  { value: 'OPS_RECOVERY_HISTORY_COMPARE_BREAKDOWN_EXPORT', label: 'History Compare Breakdown Export Event' },
  { value: 'OPS_RECOVERY_HISTORY_ACTOR_COMPARE_EXPORT', label: 'History Actor Compare Export Event' },
] as const;
const RECOVERY_HISTORY_COMPARE_BREAKDOWN_LIMIT_OPTIONS = [5, 10, 20, 50] as const;
const RECOVERY_HISTORY_COMPARE_BREAKDOWN_SORT_OPTIONS: Array<{
  value: RecoveryHistoryCompareBreakdownSort;
  label: string;
}> = [
  { value: 'DELTA_ABS_DESC', label: 'Delta Abs Desc' },
  { value: 'DELTA_DESC', label: 'Delta Desc' },
  { value: 'DELTA_ASC', label: 'Delta Asc' },
  { value: 'CURRENT_DESC', label: 'Current Desc' },
  { value: 'PREVIOUS_DESC', label: 'Previous Desc' },
  { value: 'EVENT_TYPE_ASC', label: 'Event Type Asc' },
];
const RECOVERY_HISTORY_COMPARE_ACTOR_LIMIT_OPTIONS = [5, 10, 20, 50] as const;
const RECOVERY_HISTORY_COMPARE_ACTOR_SORT_OPTIONS: Array<{
  value: RecoveryHistoryCompareActorSort;
  label: string;
}> = [
  { value: 'DELTA_ABS_DESC', label: 'Delta Abs Desc' },
  { value: 'DELTA_DESC', label: 'Delta Desc' },
  { value: 'DELTA_ASC', label: 'Delta Asc' },
  { value: 'CURRENT_DESC', label: 'Current Desc' },
  { value: 'PREVIOUS_DESC', label: 'Previous Desc' },
  { value: 'ACTOR_ASC', label: 'Actor Asc' },
];
const RECOVERY_HISTORY_EXPORT_ASYNC_TYPE_OPTIONS: Array<{
  value: RecoveryHistoryExportAsyncType;
  label: string;
}> = [
  { value: 'HISTORY', label: 'History CSV' },
  { value: 'HISTORY_SUMMARY', label: 'Summary CSV' },
  { value: 'HISTORY_TREND', label: 'Trend CSV' },
  { value: 'HISTORY_COMPARE', label: 'Compare CSV' },
  { value: 'HISTORY_COMPARE_BREAKDOWN', label: 'Compare Breakdown CSV' },
  { value: 'HISTORY_COMPARE_ACTORS', label: 'Actor Compare CSV' },
];
const RECOVERY_HISTORY_EXPORT_ASYNC_STATUS_FILTER_OPTIONS: Array<{
  value: 'ALL' | RecoveryHistoryExportAsyncStatusFilter;
  label: string;
}> = [
  { value: 'ALL', label: 'All statuses' },
  { value: 'QUEUED', label: 'Queued' },
  { value: 'RUNNING', label: 'Running' },
  { value: 'COMPLETED', label: 'Completed' },
  { value: 'CANCELLED', label: 'Cancelled' },
  { value: 'FAILED', label: 'Failed' },
  { value: 'TIMED_OUT', label: 'Timed out' },
  { value: 'EXPIRED', label: 'Expired' },
];
const RENDITION_EXPORT_TASK_STATUS_FILTER_OPTIONS: Array<{
  value: 'ALL' | PreviewRenditionResourcesExportTaskStatusFilter;
  label: string;
}> = [
  { value: 'ALL', label: 'All statuses' },
  { value: 'QUEUED', label: 'Queued' },
  { value: 'RUNNING', label: 'Running' },
  { value: 'COMPLETED', label: 'Completed' },
  { value: 'CANCELLED', label: 'Cancelled' },
  { value: 'FAILED', label: 'Failed' },
  { value: 'TIMED_OUT', label: 'Timed out' },
  { value: 'EXPIRED', label: 'Expired' },
];
const QUEUE_DECLINED_EXPORT_TASK_STATUS_FILTER_OPTIONS: Array<{
  value: 'ALL' | PreviewQueueDeclinedExportTaskStatusFilter;
  label: string;
}> = [
  { value: 'ALL', label: 'All statuses' },
  { value: 'QUEUED', label: 'Queued' },
  { value: 'RUNNING', label: 'Running' },
  { value: 'COMPLETED', label: 'Completed' },
  { value: 'CANCELLED', label: 'Cancelled' },
  { value: 'FAILED', label: 'Failed' },
  { value: 'TIMED_OUT', label: 'Timed out' },
  { value: 'EXPIRED', label: 'Expired' },
];
const QUEUE_DECLINED_REQUEUE_DRY_RUN_EXPORT_TASK_STATUS_FILTER_OPTIONS: Array<{
  value: 'ALL' | PreviewQueueDeclinedRequeueDryRunExportTaskStatusFilter;
  label: string;
}> = [
  { value: 'ALL', label: 'All statuses' },
  { value: 'QUEUED', label: 'Queued' },
  { value: 'RUNNING', label: 'Running' },
  { value: 'COMPLETED', label: 'Completed' },
  { value: 'CANCELLED', label: 'Cancelled' },
  { value: 'FAILED', label: 'Failed' },
  { value: 'TIMED_OUT', label: 'Timed out' },
  { value: 'EXPIRED', label: 'Expired' },
];
const RENDITION_EXPORT_TASK_RUNNING_STATUSES = new Set(['QUEUED', 'RUNNING', 'PROCESSING', 'IN_PROGRESS', 'STARTED']);
const RENDITION_EXPORT_TASK_COMPLETED_STATUSES = new Set(['COMPLETED', 'DONE', 'SUCCESS', 'SUCCEEDED', 'FINISHED']);
const RENDITION_EXPORT_TASK_RETRYABLE_STATUSES = new Set(['FAILED', 'CANCELLED', 'TIMED_OUT', 'EXPIRED']);
const QUEUE_DECLINED_EXPORT_TASK_RUNNING_STATUSES = new Set(['QUEUED', 'RUNNING', 'PROCESSING', 'IN_PROGRESS', 'STARTED']);
const QUEUE_DECLINED_EXPORT_TASK_COMPLETED_STATUSES = new Set(['COMPLETED', 'DONE', 'SUCCESS', 'SUCCEEDED', 'FINISHED']);
const QUEUE_DECLINED_EXPORT_TASK_RETRYABLE_STATUSES = new Set(['FAILED', 'CANCELLED', 'TIMED_OUT', 'EXPIRED']);
const QUEUE_DECLINED_REQUEUE_DRY_RUN_EXPORT_TASK_RUNNING_STATUSES = new Set(['QUEUED', 'RUNNING', 'PROCESSING', 'IN_PROGRESS', 'STARTED']);
const QUEUE_DECLINED_REQUEUE_DRY_RUN_EXPORT_TASK_COMPLETED_STATUSES = new Set(['COMPLETED', 'DONE', 'SUCCESS', 'SUCCEEDED', 'FINISHED']);
const QUEUE_DECLINED_REQUEUE_DRY_RUN_EXPORT_TASK_RETRYABLE_STATUSES = new Set(['FAILED', 'CANCELLED', 'TIMED_OUT', 'EXPIRED']);
const RECOVERY_HISTORY_EXPORT_ASYNC_RUNNING_STATUSES = new Set(['QUEUED', 'RUNNING', 'PROCESSING', 'IN_PROGRESS', 'STARTED']);
const RECOVERY_HISTORY_EXPORT_ASYNC_COMPLETED_STATUSES = new Set(['COMPLETED', 'DONE', 'SUCCESS', 'SUCCEEDED', 'FINISHED']);
const RECOVERY_HISTORY_EXPORT_ASYNC_RETRYABLE_STATUSES = new Set(['FAILED', 'CANCELLED', 'TIMED_OUT', 'EXPIRED']);
type WindowDays = (typeof WINDOW_DAY_OPTIONS)[number]['value'];

const normalizeRenditionExportTaskStatus = (status?: string | null): string => {
  const normalized = (status || '').trim().toUpperCase();
  return normalized || 'UNKNOWN';
};

const normalizeRecoveryHistoryExportAsyncStatus = (status?: string | null): string => {
  const normalized = (status || '').trim().toUpperCase();
  return normalized || 'UNKNOWN';
};

const normalizeQueueDeclinedExportTaskStatus = (status?: string | null): string => {
  const normalized = (status || '').trim().toUpperCase();
  return normalized || 'UNKNOWN';
};

const normalizeQueueDeclinedRequeueDryRunExportTaskStatus = (status?: string | null): string => {
  const normalized = (status || '').trim().toUpperCase();
  return normalized || 'UNKNOWN';
};

const formatLastUpdated = (raw?: string | null) => {
  if (!raw) {
    return '—';
  }
  const date = new Date(raw);
  if (Number.isNaN(date.getTime())) {
    return raw;
  }
  return format(date, 'PPp');
};

const formatDeadLetterTtl = (ttlMs?: number | null) => {
  const normalizedMs = Number(ttlMs);
  if (!Number.isFinite(normalizedMs) || normalizedMs <= 0) {
    return 'disabled';
  }

  const toUnitLabel = (value: number, unit: 'second' | 'minute' | 'hour') => (
    `${value} ${unit}${value === 1 ? '' : 's'}`
  );

  const seconds = normalizedMs / 1000;
  if (seconds < 60) {
    const roundedSeconds = Math.max(1, Math.round(seconds));
    return toUnitLabel(roundedSeconds, 'second');
  }

  const minutes = normalizedMs / (60 * 1000);
  if (minutes < 60) {
    const roundedMinutes = Number.isInteger(minutes) ? minutes : Number(minutes.toFixed(1));
    return toUnitLabel(roundedMinutes, 'minute');
  }

  const hours = normalizedMs / (60 * 60 * 1000);
  const roundedHours = Number.isInteger(hours) ? hours : Number(hours.toFixed(1));
  return toUnitLabel(roundedHours, 'hour');
};

const formatQueueDeclinedWindowHoursLabel = (windowHours?: number | null): string => {
  if (!Number.isFinite(windowHours as number) || Number(windowHours) <= 0) {
    return 'Any';
  }
  const normalized = Number(windowHours);
  return QUEUE_DECLINED_WINDOW_HOURS_OPTIONS.find((option) => option.value === normalized)?.label || `Last ${normalized}h`;
};

const cadCircuitChipColor = (state?: string | null): 'success' | 'warning' | 'default' | 'error' => {
  const normalized = (state || '').toUpperCase();
  if (normalized === 'OPEN') {
    return 'error';
  }
  if (normalized === 'HALF_OPEN') {
    return 'warning';
  }
  if (normalized === 'CLOSED') {
    return 'success';
  }
  return 'default';
};

type FailurePolicyDraft = {
  maxAttempts: string;
  retryDelayMs: string;
  backoffMultiplier: string;
  quietPeriodMs: string;
};

const toPolicyDraft = (policy: OpsPolicyProfile): FailurePolicyDraft => ({
  maxAttempts: String(policy.maxAttempts),
  retryDelayMs: String(policy.retryDelayMs),
  backoffMultiplier: String(policy.backoffMultiplier),
  quietPeriodMs: String(policy.quietPeriodMs),
});

const formatRetryPlan = (draft: FailurePolicyDraft) => {
  const attempts = Math.max(1, Number(draft.maxAttempts) || 1);
  const baseDelayMs = Math.max(1000, Number(draft.retryDelayMs) || 1000);
  const backoff = Math.max(1, Number(draft.backoffMultiplier) || 1);
  const retryTimes: number[] = [];
  const retryCount = Math.max(0, attempts - 1);
  for (let i = 0; i < Math.min(retryCount, 3); i += 1) {
    retryTimes.push(Math.round((baseDelayMs * Math.pow(backoff, i)) / 1000));
  }
  if (retryTimes.length === 0) {
    return 'No automatic retry';
  }
  return `Retry delays: ${retryTimes.map((seconds) => `${seconds}s`).join(', ')}`;
};

const PreviewDiagnosticsPage: React.FC = () => {
  const navigate = useNavigate();
  const [limit, setLimit] = useState<(typeof LIMIT_OPTIONS)[number]>(50);
  const [windowDays, setWindowDays] = useState<WindowDays>(7);
  const [filterText, setFilterText] = useState('');
  const [items, setItems] = useState<PreviewFailureSample[]>([]);
  const [backendSummary, setBackendSummary] = useState<PreviewFailureSummaryDto | null>(null);
  const [renditionSummary, setRenditionSummary] = useState<PreviewRenditionSummary | null>(null);
  const [renditionResources, setRenditionResources] = useState<PreviewRenditionResource[]>([]);
  const [renditionSummaryRefreshing, setRenditionSummaryRefreshing] = useState(false);
  const [renditionExportTasks, setRenditionExportTasks] = useState<PreviewRenditionResourcesExportTask[]>([]);
  const [renditionExportTaskStatusFilter, setRenditionExportTaskStatusFilter] = useState<
    'ALL' | PreviewRenditionResourcesExportTaskStatusFilter
  >('ALL');
  const [renditionExportTaskSummary, setRenditionExportTaskSummary] = useState<PreviewRenditionResourcesExportTaskSummary | null>(null);
  const [renditionExportTasksLoading, setRenditionExportTasksLoading] = useState(false);
  const [renditionExportTaskStarting, setRenditionExportTaskStarting] = useState(false);
  const [renditionExportTaskActionId, setRenditionExportTaskActionId] = useState<string | null>(null);
  const [renditionExportTaskActionType, setRenditionExportTaskActionType] = useState<'cancel' | 'download' | 'retry' | null>(null);
  const [renditionExportTaskCleaning, setRenditionExportTaskCleaning] = useState(false);
  const [renditionExportTaskCancellingActive, setRenditionExportTaskCancellingActive] = useState(false);
  const [cadFailoverDiagnostics, setCadFailoverDiagnostics] = useState<PreviewCadFailoverDiagnostics | null>(null);
  const [transformTraces, setTransformTraces] = useState<PreviewTransformTrace[]>([]);
  const [failurePolicies, setFailurePolicies] = useState<OpsPolicyProfile[]>([]);
  const [policyVersion, setPolicyVersion] = useState<number | null>(null);
  const [policyHistory, setPolicyHistory] = useState<OpsPolicyHistoryEntry[]>([]);
  const [rollbackTargetVersion, setRollbackTargetVersion] = useState<number | ''>('');
  const [preventionDiagnostics, setPreventionDiagnostics] = useState<PreviewRenditionPreventionDiagnostics | null>(null);
  const [deadLetterDiagnostics, setDeadLetterDiagnostics] = useState<PreviewDeadLetterDiagnostics | null>(null);
  const [failureLedgerDiagnostics, setFailureLedgerDiagnostics] = useState<PreviewFailureLedgerDiagnostics | null>(null);
  const [queueDiagnosticsSummary, setQueueDiagnosticsSummary] = useState<PreviewQueueDiagnosticsSummary | null>(null);
  const [queueDeclinedSummary, setQueueDeclinedSummary] = useState<PreviewQueueDeclinedSummary | null>(null);
  const [queueDiagnosticsStateFilter, setQueueDiagnosticsStateFilter] = useState<PreviewQueueDiagnosticsStateFilter>('ALL');
  const [queueDiagnosticsQueryInput, setQueueDiagnosticsQueryInput] = useState('');
  const [queueDiagnosticsQueryFilter, setQueueDiagnosticsQueryFilter] = useState('');
  const [queueDiagnosticsCancelling, setQueueDiagnosticsCancelling] = useState(false);
  const [queueDeclinedCategoryFilter, setQueueDeclinedCategoryFilter] = useState('ANY');
  const [queueDeclinedForceRequiredFilter, setQueueDeclinedForceRequiredFilter] = useState<
    (typeof QUEUE_DECLINED_FORCE_REQUIRED_FILTER_OPTIONS)[number]['value']
  >('ANY');
  const [queueDeclinedWindowHoursFilter, setQueueDeclinedWindowHoursFilter] = useState<PreviewQueueDeclinedWindowHours>(0);
  const [queueDeclinedQueryInput, setQueueDeclinedQueryInput] = useState('');
  const [queueDeclinedQueryFilter, setQueueDeclinedQueryFilter] = useState('');
  const [queueDeclinedForce, setQueueDeclinedForce] = useState(true);
  const [queueDeclinedDryRun, setQueueDeclinedDryRun] = useState<PreviewQueueDeclinedRequeueDryRunResult | null>(null);
  const [queueDeclinedDryRunning, setQueueDeclinedDryRunning] = useState(false);
  const [queueDeclinedDryRunExporting, setQueueDeclinedDryRunExporting] = useState(false);
  const [queueDeclinedRequeueing, setQueueDeclinedRequeueing] = useState(false);
  const [queueDeclinedClearing, setQueueDeclinedClearing] = useState(false);
  const [queueDeclinedExportTasks, setQueueDeclinedExportTasks] = useState<PreviewQueueDeclinedExportTask[]>([]);
  const [queueDeclinedExportTaskStatusFilter, setQueueDeclinedExportTaskStatusFilter] = useState<
    'ALL' | PreviewQueueDeclinedExportTaskStatusFilter
  >('ALL');
  const [queueDeclinedExportTaskPage, setQueueDeclinedExportTaskPage] = useState(0);
  const [queueDeclinedExportTaskMaxItems, setQueueDeclinedExportTaskMaxItems] = useState<
    (typeof QUEUE_DECLINED_EXPORT_TASK_PAGE_SIZE_OPTIONS)[number]
  >(QUEUE_DECLINED_EXPORT_TASK_DEFAULT_MAX_ITEMS);
  const [queueDeclinedExportTaskTotalItems, setQueueDeclinedExportTaskTotalItems] = useState(0);
  const [queueDeclinedExportTaskHasMoreItems, setQueueDeclinedExportTaskHasMoreItems] = useState(false);
  const [queueDeclinedExportTaskSummary, setQueueDeclinedExportTaskSummary] = useState<PreviewQueueDeclinedExportTaskSummary | null>(null);
  const [queueDeclinedExportTasksLoading, setQueueDeclinedExportTasksLoading] = useState(false);
  const [queueDeclinedExportTaskStarting, setQueueDeclinedExportTaskStarting] = useState(false);
  const [queueDeclinedExportTaskActionId, setQueueDeclinedExportTaskActionId] = useState<string | null>(null);
  const [queueDeclinedExportTaskActionType, setQueueDeclinedExportTaskActionType] = useState<'cancel' | 'download' | 'retry' | null>(null);
  const [queueDeclinedExportTaskCleaning, setQueueDeclinedExportTaskCleaning] = useState(false);
  const [queueDeclinedExportTaskCancellingActive, setQueueDeclinedExportTaskCancellingActive] = useState(false);
  const [queueDeclinedExportTaskDryRunningTerminal, setQueueDeclinedExportTaskDryRunningTerminal] = useState(false);
  const [queueDeclinedExportTaskDryRunExporting, setQueueDeclinedExportTaskDryRunExporting] = useState(false);
  const [queueDeclinedExportTaskRetryDryRun, setQueueDeclinedExportTaskRetryDryRun] =
    useState<PreviewQueueDeclinedExportTaskRetryTerminalDryRunResponse | null>(null);
  const [queueDeclinedExportTaskRetryDryRunSelectedTaskIds, setQueueDeclinedExportTaskRetryDryRunSelectedTaskIds] = useState<string[]>([]);
  const [queueDeclinedExportTaskRetryingSelected, setQueueDeclinedExportTaskRetryingSelected] = useState(false);
  const [queueDeclinedExportTaskRetryingTerminal, setQueueDeclinedExportTaskRetryingTerminal] = useState(false);
  const [queueDeclinedRequeueDryRunExportTasks, setQueueDeclinedRequeueDryRunExportTasks] =
    useState<PreviewQueueDeclinedRequeueDryRunExportTask[]>([]);
  const [queueDeclinedRequeueDryRunExportTaskStatusFilter, setQueueDeclinedRequeueDryRunExportTaskStatusFilter] = useState<
    'ALL' | PreviewQueueDeclinedRequeueDryRunExportTaskStatusFilter
  >('ALL');
  const [queueDeclinedRequeueDryRunExportTaskPage, setQueueDeclinedRequeueDryRunExportTaskPage] = useState(0);
  const [queueDeclinedRequeueDryRunExportTaskMaxItems, setQueueDeclinedRequeueDryRunExportTaskMaxItems] = useState<
    (typeof QUEUE_DECLINED_EXPORT_TASK_PAGE_SIZE_OPTIONS)[number]
  >(QUEUE_DECLINED_REQUEUE_DRY_RUN_EXPORT_TASK_DEFAULT_MAX_ITEMS);
  const [queueDeclinedRequeueDryRunExportTaskTotalItems, setQueueDeclinedRequeueDryRunExportTaskTotalItems] = useState(0);
  const [queueDeclinedRequeueDryRunExportTaskHasMoreItems, setQueueDeclinedRequeueDryRunExportTaskHasMoreItems] = useState(false);
  const [queueDeclinedRequeueDryRunExportTaskSummary, setQueueDeclinedRequeueDryRunExportTaskSummary] =
    useState<PreviewQueueDeclinedRequeueDryRunExportTaskSummary | null>(null);
  const [queueDeclinedRequeueDryRunExportTasksLoading, setQueueDeclinedRequeueDryRunExportTasksLoading] = useState(false);
  const [queueDeclinedRequeueDryRunExportTaskStarting, setQueueDeclinedRequeueDryRunExportTaskStarting] = useState(false);
  const [queueDeclinedRequeueDryRunExportTaskActionId, setQueueDeclinedRequeueDryRunExportTaskActionId] = useState<string | null>(null);
  const [queueDeclinedRequeueDryRunExportTaskActionType, setQueueDeclinedRequeueDryRunExportTaskActionType] =
    useState<'cancel' | 'download' | 'retry' | null>(null);
  const [queueDeclinedRequeueDryRunExportTaskCleaning, setQueueDeclinedRequeueDryRunExportTaskCleaning] = useState(false);
  const [queueDeclinedRequeueDryRunExportTaskCancellingActive, setQueueDeclinedRequeueDryRunExportTaskCancellingActive] = useState(false);
  const [queueDeclinedRequeueDryRunExportTaskDryRunningTerminal, setQueueDeclinedRequeueDryRunExportTaskDryRunningTerminal] = useState(false);
  const [queueDeclinedRequeueDryRunExportTaskDryRunExporting, setQueueDeclinedRequeueDryRunExportTaskDryRunExporting] = useState(false);
  const [queueDeclinedRequeueDryRunExportTaskRetryDryRun, setQueueDeclinedRequeueDryRunExportTaskRetryDryRun] =
    useState<PreviewQueueDeclinedRequeueDryRunExportTaskRetryTerminalDryRunResponse | null>(null);
  const [
    queueDeclinedRequeueDryRunExportTaskRetryDryRunSelectedTaskIds,
    setQueueDeclinedRequeueDryRunExportTaskRetryDryRunSelectedTaskIds,
  ] = useState<string[]>([]);
  const [queueDeclinedRequeueDryRunExportTaskRetryingSelected, setQueueDeclinedRequeueDryRunExportTaskRetryingSelected] = useState(false);
  const [queueDeclinedRequeueDryRunExportTaskRetryingTerminal, setQueueDeclinedRequeueDryRunExportTaskRetryingTerminal] = useState(false);
  const queueDeclinedExportTaskPollDelayRef = useRef(ASYNC_TASK_POLL_BASE_MS);
  const queueDeclinedRequeueDryRunExportTaskPollDelayRef = useRef(ASYNC_TASK_POLL_BASE_MS);
  const [preventionFilterText, setPreventionFilterText] = useState('');
  const [deadLetterFilterText, setDeadLetterFilterText] = useState('');
  const [failureLedgerFilterText, setFailureLedgerFilterText] = useState('');
  const [selectedPreventionIds, setSelectedPreventionIds] = useState<string[]>([]);
  const [selectedDeadLetterEntryKeys, setSelectedDeadLetterEntryKeys] = useState<string[]>([]);
  const [selectedFailureLedgerIds, setSelectedFailureLedgerIds] = useState<string[]>([]);
  const [policyDrafts, setPolicyDrafts] = useState<Record<string, FailurePolicyDraft>>({});
  const [policySavingKey, setPolicySavingKey] = useState<string | null>(null);
  const [policyRollbacking, setPolicyRollbacking] = useState(false);
  const [preventionActionId, setPreventionActionId] = useState<string | null>(null);
  const [deadLetterActionId, setDeadLetterActionId] = useState<string | null>(null);
  const [failureLedgerActionId, setFailureLedgerActionId] = useState<string | null>(null);
  const [renditionResourceActionId, setRenditionResourceActionId] = useState<string | null>(null);
  const [traceLimit, setTraceLimit] = useState<(typeof TRACE_LIMIT_OPTIONS)[number]>(20);
  const [traceRequestId, setTraceRequestId] = useState('');
  const [loading, setLoading] = useState(false);
  const [queueingId, setQueueingId] = useState<string | null>(null);
  const [reasonBatchActionKey, setReasonBatchActionKey] = useState<string | null>(null);
  const [reasonDryRunActionKey, setReasonDryRunActionKey] = useState<string | null>(null);
  const [reasonClearActionKey, setReasonClearActionKey] = useState<string | null>(null);
  const [reasonReplayDeadLetterActionKey, setReasonReplayDeadLetterActionKey] = useState<string | null>(null);
  const [reasonLedgerResetActionKey, setReasonLedgerResetActionKey] = useState<string | null>(null);
  const [dryRunMode, setDryRunMode] = useState<(typeof DRY_RUN_MODE_OPTIONS)[number]['value']>('QUEUE_BY_WINDOW');
  const [dryRunReason, setDryRunReason] = useState('');
  const [dryRunCategory, setDryRunCategory] = useState<(typeof DRY_RUN_CATEGORY_OPTIONS)[number]>('ANY');
  const [dryRunRetryable, setDryRunRetryable] = useState<(typeof DRY_RUN_RETRYABLE_OPTIONS)[number]>('ANY');
  const [dryRunMaxDocuments, setDryRunMaxDocuments] = useState(100);
  const [dryRunForce, setDryRunForce] = useState(false);
  const [dryRunLoading, setDryRunLoading] = useState(false);
  const [dryRunExecuting, setDryRunExecuting] = useState(false);
  const [dryRunResult, setDryRunResult] = useState<RecoveryDryRunResult | null>(null);
  const [dryRunRequestSignature, setDryRunRequestSignature] = useState<string | null>(null);
  const [recoveryHistory, setRecoveryHistory] = useState<RecoveryHistoryItem[]>([]);
  const [recoveryHistoryMode, setRecoveryHistoryMode] = useState<'ALL' | RecoveryHistoryMode>('ALL');
  const [recoveryHistoryActor, setRecoveryHistoryActor] = useState('');
  const [recoveryHistoryEventType, setRecoveryHistoryEventType] = useState<'ALL' | RecoveryHistoryEventType>('ALL');
  const [recoveryHistoryPage, setRecoveryHistoryPage] = useState(0);
  const [recoveryHistoryTotal, setRecoveryHistoryTotal] = useState(0);
  const [recoveryHistoryTotalPages, setRecoveryHistoryTotalPages] = useState(0);
  const [recoveryHistoryAutoRefresh, setRecoveryHistoryAutoRefresh] = useState(false);
  const [recoveryHistoryAutoRefreshSeconds, setRecoveryHistoryAutoRefreshSeconds] = useState<(typeof RECOVERY_HISTORY_AUTO_REFRESH_OPTIONS)[number]>(30);
  const [recoveryHistorySummary, setRecoveryHistorySummary] = useState<RecoveryHistorySummaryItem[]>([]);
  const [recoveryHistoryActorSummary, setRecoveryHistoryActorSummary] = useState<RecoveryHistoryActorSummaryItem[]>([]);
  const [recoveryHistorySummaryTotal, setRecoveryHistorySummaryTotal] = useState(0);
  const [recoveryHistoryTrend, setRecoveryHistoryTrend] = useState<RecoveryHistoryTrendItem[]>([]);
  const [recoveryHistoryTrendTotal, setRecoveryHistoryTrendTotal] = useState(0);
  const [recoveryHistoryTrendTruncated, setRecoveryHistoryTrendTruncated] = useState(false);
  const [recoveryHistoryCompareCurrent, setRecoveryHistoryCompareCurrent] = useState(0);
  const [recoveryHistoryComparePrevious, setRecoveryHistoryComparePrevious] = useState(0);
  const [recoveryHistoryCompareDelta, setRecoveryHistoryCompareDelta] = useState(0);
  const [recoveryHistoryCompareDeltaPercent, setRecoveryHistoryCompareDeltaPercent] = useState<number | null>(null);
  const [recoveryHistoryCompareAvailable, setRecoveryHistoryCompareAvailable] = useState(false);
  const [recoveryHistoryCompareTruncated, setRecoveryHistoryCompareTruncated] = useState(false);
  const [recoveryHistoryCompareBreakdown, setRecoveryHistoryCompareBreakdown] = useState<RecoveryHistorySummaryCompareBreakdownItem[]>([]);
  const [recoveryHistoryCompareBreakdownLimit, setRecoveryHistoryCompareBreakdownLimit] = useState<(typeof RECOVERY_HISTORY_COMPARE_BREAKDOWN_LIMIT_OPTIONS)[number]>(10);
  const [recoveryHistoryCompareBreakdownSort, setRecoveryHistoryCompareBreakdownSort] = useState<RecoveryHistoryCompareBreakdownSort>('DELTA_ABS_DESC');
  const [recoveryHistoryCompareBreakdownTotalItems, setRecoveryHistoryCompareBreakdownTotalItems] = useState(0);
  const [recoveryHistoryCompareBreakdownLimited, setRecoveryHistoryCompareBreakdownLimited] = useState(false);
  const [recoveryHistoryCompareActors, setRecoveryHistoryCompareActors] = useState<RecoveryHistorySummaryCompareActorItem[]>([]);
  const [recoveryHistoryCompareActorLimit, setRecoveryHistoryCompareActorLimit] = useState<(typeof RECOVERY_HISTORY_COMPARE_ACTOR_LIMIT_OPTIONS)[number]>(10);
  const [recoveryHistoryCompareActorSort, setRecoveryHistoryCompareActorSort] = useState<RecoveryHistoryCompareActorSort>('DELTA_ABS_DESC');
  const [recoveryHistoryCompareActorTotalItems, setRecoveryHistoryCompareActorTotalItems] = useState(0);
  const [recoveryHistoryCompareActorLimited, setRecoveryHistoryCompareActorLimited] = useState(false);
  const [recoveryHistoryExportAsyncType, setRecoveryHistoryExportAsyncType] = useState<RecoveryHistoryExportAsyncType>('HISTORY');
  const [recoveryHistoryExportAsyncFilterType, setRecoveryHistoryExportAsyncFilterType] = useState<
    'ALL' | RecoveryHistoryExportAsyncType
  >('ALL');
  const [recoveryHistoryExportAsyncFilterStatus, setRecoveryHistoryExportAsyncFilterStatus] = useState<
    'ALL' | RecoveryHistoryExportAsyncStatusFilter
  >('ALL');
  const [recoveryHistoryExportAsyncSummary, setRecoveryHistoryExportAsyncSummary] = useState<RecoveryHistoryExportAsyncTaskSummary | null>(null);
  const [recoveryHistoryExportAsyncSummaryLoading, setRecoveryHistoryExportAsyncSummaryLoading] = useState(false);
  const [recoveryHistoryExportAsyncCleaning, setRecoveryHistoryExportAsyncCleaning] = useState(false);
  const [recoveryHistoryExportAsyncCancellingActive, setRecoveryHistoryExportAsyncCancellingActive] = useState(false);
  const [recoveryHistoryExportAsyncTasks, setRecoveryHistoryExportAsyncTasks] = useState<RecoveryHistoryExportAsyncTaskStatus[]>([]);
  const [recoveryHistoryExportAsyncTasksLoading, setRecoveryHistoryExportAsyncTasksLoading] = useState(false);
  const [recoveryHistoryExportAsyncStarting, setRecoveryHistoryExportAsyncStarting] = useState(false);
  const [recoveryHistoryExportAsyncActionTaskId, setRecoveryHistoryExportAsyncActionTaskId] = useState<string | null>(null);
  const [recoveryHistoryExportAsyncActionType, setRecoveryHistoryExportAsyncActionType] = useState<'cancel' | 'download' | 'retry' | null>(null);

  const loadRenditionExportTasks = useCallback(async (silent = false) => {
    try {
      setRenditionExportTasksLoading(true);
      const statusFilter = renditionExportTaskStatusFilter === 'ALL'
        ? undefined
        : renditionExportTaskStatusFilter;
      const response = await previewDiagnosticsService.listRenditionResourcesExportTasks(
        RENDITION_EXPORT_TASK_LIMIT,
        statusFilter
      );
      const items = Array.isArray(response?.items) ? response.items : [];
      setRenditionExportTasks(items);
      const summary = await previewDiagnosticsService.getRenditionResourcesExportTaskSummary(statusFilter).catch(() => null);
      setRenditionExportTaskSummary(summary || null);
    } catch {
      if (!silent) {
        toast.error('Failed to refresh rendition export tasks');
      }
    } finally {
      setRenditionExportTasksLoading(false);
    }
  }, [renditionExportTaskStatusFilter]);

  const loadQueueDeclinedExportTasks = useCallback(async (silent = false) => {
    try {
      setQueueDeclinedExportTasksLoading(true);
      const statusFilter = queueDeclinedExportTaskStatusFilter === 'ALL'
        ? undefined
        : queueDeclinedExportTaskStatusFilter;
      const skipCount = queueDeclinedExportTaskPage * queueDeclinedExportTaskMaxItems;
      const response = await previewDiagnosticsService.listQueueDeclinedExportTasks(
        queueDeclinedExportTaskMaxItems,
        statusFilter,
        skipCount
      );
      const items = Array.isArray(response?.items) ? response.items : [];
      setQueueDeclinedExportTasks(items);
      const paging = response?.paging || null;
      const totalItems = Number.isFinite(Number(paging?.totalItems))
        ? Number(paging?.totalItems)
        : items.length;
      const hasMoreItems = typeof paging?.hasMoreItems === 'boolean'
        ? paging.hasMoreItems
        : (skipCount + items.length) < totalItems;
      setQueueDeclinedExportTaskTotalItems(totalItems);
      setQueueDeclinedExportTaskHasMoreItems(hasMoreItems);
      const summary = await previewDiagnosticsService.getQueueDeclinedExportTaskSummary(statusFilter).catch(() => null);
      setQueueDeclinedExportTaskSummary(summary || null);
    } catch {
      if (!silent) {
        toast.error('Failed to refresh queue declined export tasks');
      }
    } finally {
      setQueueDeclinedExportTasksLoading(false);
    }
  }, [queueDeclinedExportTaskMaxItems, queueDeclinedExportTaskPage, queueDeclinedExportTaskStatusFilter]);

  const loadQueueDeclinedRequeueDryRunExportTasks = useCallback(async (silent = false) => {
    try {
      setQueueDeclinedRequeueDryRunExportTasksLoading(true);
      const statusFilter = queueDeclinedRequeueDryRunExportTaskStatusFilter === 'ALL'
        ? undefined
        : queueDeclinedRequeueDryRunExportTaskStatusFilter;
      const skipCount = queueDeclinedRequeueDryRunExportTaskPage * queueDeclinedRequeueDryRunExportTaskMaxItems;
      const response = await previewDiagnosticsService.listQueueDeclinedRequeueDryRunExportTasks(
        queueDeclinedRequeueDryRunExportTaskMaxItems,
        statusFilter,
        skipCount
      );
      const items = Array.isArray(response?.items) ? response.items : [];
      setQueueDeclinedRequeueDryRunExportTasks(items);
      const paging = response?.paging || null;
      const totalItems = Number.isFinite(Number(paging?.totalItems))
        ? Number(paging?.totalItems)
        : items.length;
      const hasMoreItems = typeof paging?.hasMoreItems === 'boolean'
        ? paging.hasMoreItems
        : (skipCount + items.length) < totalItems;
      setQueueDeclinedRequeueDryRunExportTaskTotalItems(totalItems);
      setQueueDeclinedRequeueDryRunExportTaskHasMoreItems(hasMoreItems);
      const summary = await previewDiagnosticsService.getQueueDeclinedRequeueDryRunExportTaskSummary(statusFilter).catch(() => null);
      setQueueDeclinedRequeueDryRunExportTaskSummary(summary || null);
    } catch {
      if (!silent) {
        toast.error('Failed to refresh queue declined requeue dry-run async export tasks');
      }
    } finally {
      setQueueDeclinedRequeueDryRunExportTasksLoading(false);
    }
  }, [
    queueDeclinedRequeueDryRunExportTaskMaxItems,
    queueDeclinedRequeueDryRunExportTaskPage,
    queueDeclinedRequeueDryRunExportTaskStatusFilter,
  ]);

  const hasActiveQueueDeclinedExportTasks = useMemo(() => {
    if ((queueDeclinedExportTaskSummary?.activeCount || 0) > 0) {
      return true;
    }
    return queueDeclinedExportTasks.some((task) => {
      const normalizedStatus = String(task?.status || '').toUpperCase();
      return normalizedStatus === 'QUEUED' || normalizedStatus === 'RUNNING';
    });
  }, [queueDeclinedExportTaskSummary, queueDeclinedExportTasks]);

  const hasActiveQueueDeclinedRequeueDryRunExportTasks = useMemo(() => {
    if ((queueDeclinedRequeueDryRunExportTaskSummary?.activeCount || 0) > 0) {
      return true;
    }
    return queueDeclinedRequeueDryRunExportTasks.some((task) => {
      const normalizedStatus = String(task?.status || '').toUpperCase();
      return normalizedStatus === 'QUEUED' || normalizedStatus === 'RUNNING';
    });
  }, [queueDeclinedRequeueDryRunExportTaskSummary, queueDeclinedRequeueDryRunExportTasks]);

  const loadRecoveryHistoryExportAsyncTasks = useCallback(async (silent = false) => {
    try {
      setRecoveryHistoryExportAsyncTasksLoading(true);
      const response = await opsRecoveryService.listHistoryExportAsyncTasks(
        RECOVERY_HISTORY_EXPORT_ASYNC_TASK_LIMIT,
        recoveryHistoryExportAsyncFilterType === 'ALL' ? undefined : recoveryHistoryExportAsyncFilterType,
        recoveryHistoryExportAsyncFilterStatus === 'ALL' ? undefined : recoveryHistoryExportAsyncFilterStatus
      );
      const items = Array.isArray(response?.items) ? response.items : [];
      setRecoveryHistoryExportAsyncTasks(items);
      const summary = await opsRecoveryService.getHistoryExportAsyncTaskSummaryFiltered(
        recoveryHistoryExportAsyncFilterType === 'ALL' ? undefined : recoveryHistoryExportAsyncFilterType,
        recoveryHistoryExportAsyncFilterStatus === 'ALL' ? undefined : recoveryHistoryExportAsyncFilterStatus
      ).catch(() => null);
      if (summary) {
        setRecoveryHistoryExportAsyncSummary(summary);
      }
    } catch {
      if (!silent) {
        toast.error('Failed to refresh ops recovery async export tasks');
      }
    } finally {
      setRecoveryHistoryExportAsyncTasksLoading(false);
    }
  }, [recoveryHistoryExportAsyncFilterType, recoveryHistoryExportAsyncFilterStatus]);

  const loadFailures = useCallback(async () => {
    try {
      setLoading(true);
      const [
        data,
        summaryData,
        renditionSummaryData,
        renditionResourcesData,
        cadFailoverData,
        transformTraceData,
        policyData,
        policyHistoryData,
        preventionData,
        failureLedgerData,
        queueSummaryData,
        queueDeclinedData,
        deadLetterData,
        recoveryHistoryData,
        recoveryHistorySummaryData,
        recoveryHistoryTrendData,
        recoveryHistoryCompareData,
        recoveryHistoryCompareBreakdownData,
        recoveryHistoryCompareActorsData,
      ] = await Promise.all([
        previewDiagnosticsService.listRecentFailures(limit, windowDays),
        previewDiagnosticsService.getFailureSummary(BACKEND_SUMMARY_SAMPLE_LIMIT, windowDays),
        previewDiagnosticsService.getRenditionSummary(windowDays, BACKEND_SUMMARY_SAMPLE_LIMIT).catch(() => null),
        previewDiagnosticsService.getRenditionResources(windowDays, BACKEND_SUMMARY_SAMPLE_LIMIT).catch(() => []),
        previewDiagnosticsService.getCadFailoverDiagnostics().catch(() => null),
        previewDiagnosticsService
          .getTransformTraces(traceLimit, traceRequestId.trim() || undefined)
          .catch(() => []),
        opsPolicyService.getDomain('PREVIEW').catch(() => null),
        opsPolicyService.getHistory('PREVIEW', 20).catch(() => null),
        previewDiagnosticsService.getRenditionPreventionBlocked(PREVENTION_LIST_LIMIT).catch(() => null),
        previewDiagnosticsService.getFailureLedger(FAILURE_LEDGER_LIST_LIMIT, windowDays).catch(() => null),
        previewDiagnosticsService.getQueueDiagnosticsSummary(
          QUEUE_DIAGNOSTICS_LIMIT,
          queueDiagnosticsStateFilter,
          queueDiagnosticsQueryFilter || undefined
        ).catch(() => null),
        previewDiagnosticsService.getQueueDeclinedSummary(
          QUEUE_DECLINED_LIMIT,
          queueDeclinedCategoryFilter,
          queueDeclinedForceRequiredFilter,
          queueDeclinedQueryFilter || undefined,
          queueDeclinedWindowHoursFilter
        ).catch(() => null),
        previewDiagnosticsService.getDeadLetter(DEAD_LETTER_LIST_LIMIT).catch(() => null),
        opsRecoveryService.getHistory(
          RECOVERY_HISTORY_PAGE_LIMIT,
          windowDays,
          recoveryHistoryMode === 'ALL' ? undefined : recoveryHistoryMode,
          recoveryHistoryPage,
          recoveryHistoryActor.trim() || undefined,
          recoveryHistoryEventType === 'ALL' ? undefined : recoveryHistoryEventType
        ).catch(() => null),
        opsRecoveryService.getHistorySummary(
          windowDays,
          recoveryHistoryMode === 'ALL' ? undefined : recoveryHistoryMode,
          recoveryHistoryActor.trim() || undefined,
          recoveryHistoryEventType === 'ALL' ? undefined : recoveryHistoryEventType
        ).catch(() => null),
        opsRecoveryService.getHistorySummaryTrend(
          windowDays,
          recoveryHistoryMode === 'ALL' ? undefined : recoveryHistoryMode,
          recoveryHistoryActor.trim() || undefined,
          recoveryHistoryEventType === 'ALL' ? undefined : recoveryHistoryEventType
        ).catch(() => null),
        opsRecoveryService.getHistorySummaryCompare(
          windowDays,
          recoveryHistoryMode === 'ALL' ? undefined : recoveryHistoryMode,
          recoveryHistoryActor.trim() || undefined,
          recoveryHistoryEventType === 'ALL' ? undefined : recoveryHistoryEventType
        ).catch(() => null),
        opsRecoveryService.getHistorySummaryCompareBreakdown(
          windowDays,
          recoveryHistoryMode === 'ALL' ? undefined : recoveryHistoryMode,
          recoveryHistoryActor.trim() || undefined,
          recoveryHistoryEventType === 'ALL' ? undefined : recoveryHistoryEventType,
          recoveryHistoryCompareBreakdownLimit,
          recoveryHistoryCompareBreakdownSort
        ).catch(() => null),
        opsRecoveryService.getHistorySummaryCompareActors(
          windowDays,
          recoveryHistoryMode === 'ALL' ? undefined : recoveryHistoryMode,
          recoveryHistoryActor.trim() || undefined,
          recoveryHistoryEventType === 'ALL' ? undefined : recoveryHistoryEventType,
          recoveryHistoryCompareActorLimit,
          recoveryHistoryCompareActorSort
        ).catch(() => null),
      ]);
      setItems(Array.isArray(data) ? data : []);
      setBackendSummary(summaryData || null);
      setRenditionSummary(renditionSummaryData || null);
      setRenditionResources(Array.isArray(renditionResourcesData) ? renditionResourcesData : []);
      setCadFailoverDiagnostics(cadFailoverData || null);
      setTransformTraces(Array.isArray(transformTraceData) ? transformTraceData : []);
      setPreventionDiagnostics(preventionData || null);
      setFailureLedgerDiagnostics(failureLedgerData || null);
      setQueueDiagnosticsSummary(queueSummaryData || null);
      setQueueDeclinedSummary(queueDeclinedData || null);
      setDeadLetterDiagnostics(deadLetterData || null);
      const historyItems = recoveryHistoryData && Array.isArray(recoveryHistoryData.items)
        ? recoveryHistoryData.items
        : [];
      setRecoveryHistory(historyItems);
      setRecoveryHistoryTotal(typeof recoveryHistoryData?.total === 'number' ? recoveryHistoryData.total : historyItems.length);
      setRecoveryHistoryTotalPages(typeof recoveryHistoryData?.totalPages === 'number' ? recoveryHistoryData.totalPages : 0);
      const summaryItems = recoveryHistorySummaryData && Array.isArray(recoveryHistorySummaryData.items)
        ? recoveryHistorySummaryData.items
        : [];
      const summaryActorItems = recoveryHistorySummaryData && Array.isArray(recoveryHistorySummaryData.actorItems)
        ? recoveryHistorySummaryData.actorItems
        : [];
      setRecoveryHistorySummary(summaryItems);
      setRecoveryHistoryActorSummary(summaryActorItems);
      setRecoveryHistorySummaryTotal(
        typeof recoveryHistorySummaryData?.total === 'number'
          ? recoveryHistorySummaryData.total
          : summaryItems.reduce((sum, item) => sum + (Number(item.count) || 0), 0)
      );
      const summaryTrendItems = recoveryHistoryTrendData && Array.isArray(recoveryHistoryTrendData.items)
        ? recoveryHistoryTrendData.items
        : [];
      setRecoveryHistoryTrend(summaryTrendItems);
      setRecoveryHistoryTrendTotal(
        typeof recoveryHistoryTrendData?.total === 'number'
          ? recoveryHistoryTrendData.total
          : summaryTrendItems.reduce((sum, item) => sum + (Number(item.count) || 0), 0)
      );
      setRecoveryHistoryTrendTruncated(Boolean(recoveryHistoryTrendData?.truncated));
      setRecoveryHistoryCompareCurrent(typeof recoveryHistoryCompareData?.currentTotal === 'number' ? recoveryHistoryCompareData.currentTotal : 0);
      setRecoveryHistoryComparePrevious(typeof recoveryHistoryCompareData?.previousTotal === 'number' ? recoveryHistoryCompareData.previousTotal : 0);
      setRecoveryHistoryCompareDelta(typeof recoveryHistoryCompareData?.delta === 'number' ? recoveryHistoryCompareData.delta : 0);
      setRecoveryHistoryCompareDeltaPercent(
        typeof recoveryHistoryCompareData?.deltaPercent === 'number'
          ? recoveryHistoryCompareData.deltaPercent
          : null
      );
      setRecoveryHistoryCompareAvailable(Boolean(recoveryHistoryCompareData?.compareAvailable));
      setRecoveryHistoryCompareTruncated(
        Boolean(recoveryHistoryCompareData?.truncated)
        || Boolean(recoveryHistoryCompareBreakdownData?.truncated)
        || Boolean(recoveryHistoryCompareActorsData?.truncated)
      );
      const summaryCompareBreakdownItems = recoveryHistoryCompareBreakdownData && Array.isArray(recoveryHistoryCompareBreakdownData.items)
        ? recoveryHistoryCompareBreakdownData.items
        : [];
      setRecoveryHistoryCompareBreakdown(summaryCompareBreakdownItems);
      setRecoveryHistoryCompareBreakdownTotalItems(
        typeof recoveryHistoryCompareBreakdownData?.totalItems === 'number'
          ? recoveryHistoryCompareBreakdownData.totalItems
          : summaryCompareBreakdownItems.length
      );
      setRecoveryHistoryCompareBreakdownLimited(Boolean(recoveryHistoryCompareBreakdownData?.limited));
      const summaryCompareActorItems = recoveryHistoryCompareActorsData && Array.isArray(recoveryHistoryCompareActorsData.items)
        ? recoveryHistoryCompareActorsData.items
        : [];
      setRecoveryHistoryCompareActors(summaryCompareActorItems);
      setRecoveryHistoryCompareActorTotalItems(
        typeof recoveryHistoryCompareActorsData?.totalItems === 'number'
          ? recoveryHistoryCompareActorsData.totalItems
          : summaryCompareActorItems.length
      );
      setRecoveryHistoryCompareActorLimited(Boolean(recoveryHistoryCompareActorsData?.limited));
      const normalizedPolicies = Array.isArray(policyData?.policies) ? policyData!.policies : [];
      const currentVersion = typeof policyData?.currentVersion === 'number' ? policyData.currentVersion : null;
      const historyPayload = policyHistoryData?.history;
      const normalizedHistory = Array.isArray(historyPayload) ? historyPayload : [];
      setPolicyVersion(currentVersion);
      setPolicyHistory(normalizedHistory);
      setRollbackTargetVersion((current) => {
        if (current !== '' && normalizedHistory.some((entry) => entry.version === current)) {
          return current;
        }
        const fallback = normalizedHistory.find((entry) => currentVersion === null || entry.version < currentVersion);
        return fallback ? fallback.version : '';
      });
      setFailurePolicies(normalizedPolicies);
      setPolicyDrafts((current) => {
        const next: Record<string, FailurePolicyDraft> = {};
        normalizedPolicies.forEach((policy) => {
          next[policy.key] = current[policy.key] || toPolicyDraft(policy);
        });
        return next;
      });
    } catch {
      toast.error('Failed to load preview diagnostics (admin required)');
      setItems([]);
      setBackendSummary(null);
      setRenditionSummary(null);
      setRenditionResources([]);
      setCadFailoverDiagnostics(null);
      setTransformTraces([]);
      setPreventionDiagnostics(null);
      setQueueDiagnosticsSummary(null);
      setQueueDeclinedSummary(null);
      setDeadLetterDiagnostics(null);
      setRecoveryHistory([]);
      setRecoveryHistoryTotal(0);
      setRecoveryHistoryTotalPages(0);
      setRecoveryHistorySummary([]);
      setRecoveryHistoryActorSummary([]);
      setRecoveryHistorySummaryTotal(0);
      setRecoveryHistoryTrend([]);
      setRecoveryHistoryTrendTotal(0);
      setRecoveryHistoryTrendTruncated(false);
      setRecoveryHistoryCompareCurrent(0);
      setRecoveryHistoryComparePrevious(0);
      setRecoveryHistoryCompareDelta(0);
      setRecoveryHistoryCompareDeltaPercent(null);
      setRecoveryHistoryCompareAvailable(false);
      setRecoveryHistoryCompareTruncated(false);
      setRecoveryHistoryCompareBreakdown([]);
      setRecoveryHistoryCompareBreakdownTotalItems(0);
      setRecoveryHistoryCompareBreakdownLimited(false);
      setRecoveryHistoryCompareActors([]);
      setRecoveryHistoryCompareActorTotalItems(0);
      setRecoveryHistoryCompareActorLimited(false);
      setFailurePolicies([]);
      setPolicyVersion(null);
      setPolicyHistory([]);
      setRollbackTargetVersion('');
      setPolicyDrafts({});
    } finally {
      setLoading(false);
    }
  }, [
    limit,
    windowDays,
    traceLimit,
    traceRequestId,
    queueDiagnosticsStateFilter,
    queueDiagnosticsQueryFilter,
    queueDeclinedCategoryFilter,
    queueDeclinedForceRequiredFilter,
    queueDeclinedWindowHoursFilter,
    queueDeclinedQueryFilter,
    recoveryHistoryMode,
    recoveryHistoryPage,
    recoveryHistoryActor,
    recoveryHistoryEventType,
    recoveryHistoryCompareBreakdownLimit,
    recoveryHistoryCompareBreakdownSort,
    recoveryHistoryCompareActorLimit,
    recoveryHistoryCompareActorSort,
  ]);

  useEffect(() => {
    void loadFailures();
  }, [loadFailures]);

  useEffect(() => {
    void loadRenditionExportTasks(true);
  }, [loadRenditionExportTasks]);

  useEffect(() => {
    void loadQueueDeclinedExportTasks(true);
  }, [loadQueueDeclinedExportTasks]);

  useEffect(() => {
    void loadQueueDeclinedRequeueDryRunExportTasks(true);
  }, [loadQueueDeclinedRequeueDryRunExportTasks]);

  useEffect(() => {
    if (!hasActiveQueueDeclinedExportTasks) {
      queueDeclinedExportTaskPollDelayRef.current = ASYNC_TASK_POLL_BASE_MS;
      return undefined;
    }
    if (queueDeclinedExportTasksLoading) {
      return undefined;
    }
    const currentDelay = queueDeclinedExportTaskPollDelayRef.current;
    const timeoutId = window.setTimeout(() => {
      void loadQueueDeclinedExportTasks(true);
    }, currentDelay);
    queueDeclinedExportTaskPollDelayRef.current = Math.min(
      Math.floor(currentDelay * 1.5),
      ASYNC_TASK_POLL_MAX_MS
    );
    return () => window.clearTimeout(timeoutId);
  }, [
    hasActiveQueueDeclinedExportTasks,
    queueDeclinedExportTasksLoading,
    loadQueueDeclinedExportTasks,
  ]);

  useEffect(() => {
    if (!hasActiveQueueDeclinedRequeueDryRunExportTasks) {
      queueDeclinedRequeueDryRunExportTaskPollDelayRef.current = ASYNC_TASK_POLL_BASE_MS;
      return undefined;
    }
    if (queueDeclinedRequeueDryRunExportTasksLoading) {
      return undefined;
    }
    const currentDelay = queueDeclinedRequeueDryRunExportTaskPollDelayRef.current;
    const timeoutId = window.setTimeout(() => {
      void loadQueueDeclinedRequeueDryRunExportTasks(true);
    }, currentDelay);
    queueDeclinedRequeueDryRunExportTaskPollDelayRef.current = Math.min(
      Math.floor(currentDelay * 1.5),
      ASYNC_TASK_POLL_MAX_MS
    );
    return () => window.clearTimeout(timeoutId);
  }, [
    hasActiveQueueDeclinedRequeueDryRunExportTasks,
    queueDeclinedRequeueDryRunExportTasksLoading,
    loadQueueDeclinedRequeueDryRunExportTasks,
  ]);

  useEffect(() => {
    void loadRecoveryHistoryExportAsyncTasks(true);
  }, [loadRecoveryHistoryExportAsyncTasks]);

  useEffect(() => {
    setRecoveryHistoryPage(0);
  }, [windowDays, recoveryHistoryMode, recoveryHistoryActor, recoveryHistoryEventType]);

  useEffect(() => {
    if (!recoveryHistoryAutoRefresh) {
      return undefined;
    }
    const intervalId = window.setInterval(() => {
      void loadFailures();
    }, recoveryHistoryAutoRefreshSeconds * 1000);
    return () => window.clearInterval(intervalId);
  }, [recoveryHistoryAutoRefresh, recoveryHistoryAutoRefreshSeconds, loadFailures]);

  useEffect(() => {
    const currentIds = new Set((preventionDiagnostics?.items || []).map((item) => item.documentId));
    setSelectedPreventionIds((current) => current.filter((id) => currentIds.has(id)));
  }, [preventionDiagnostics]);

  useEffect(() => {
    const currentEntryKeys = new Set((deadLetterDiagnostics?.items || []).map((item) => item.entryKey || item.documentId));
    setSelectedDeadLetterEntryKeys((current) => current.filter((entryKey) => currentEntryKeys.has(entryKey)));
  }, [deadLetterDiagnostics]);

  useEffect(() => {
    const currentIds = new Set((failureLedgerDiagnostics?.items || []).map((item) => item.documentId));
    setSelectedFailureLedgerIds((current) => current.filter((id) => currentIds.has(id)));
  }, [failureLedgerDiagnostics]);

  useEffect(() => {
    if (dryRunMode === 'CLEAR_BY_FILTER' && dryRunForce) {
      setDryRunForce(false);
    }
  }, [dryRunMode, dryRunForce]);

  const normalizedDryRunReason = dryRunReason.trim() || undefined;
  const normalizedDryRunRetryable = dryRunRetryable === 'ANY' ? undefined : dryRunRetryable === 'YES';
  const normalizedDryRunMaxDocuments = Math.max(1, Math.min(500, Number(dryRunMaxDocuments) || 100));
  const normalizedDryRunForce = dryRunMode === 'CLEAR_BY_FILTER' ? false : dryRunForce;
  const currentDryRunRequestSignature = JSON.stringify({
    mode: dryRunMode,
    reason: normalizedDryRunReason ?? null,
    category: dryRunCategory,
    retryable: normalizedDryRunRetryable ?? null,
    maxDocuments: normalizedDryRunMaxDocuments,
    days: windowDays,
    force: normalizedDryRunForce,
  });
  const dryRunPlanStale = Boolean(
    dryRunResult
    && dryRunRequestSignature
    && dryRunRequestSignature !== currentDryRunRequestSignature
  );
  const canExecuteDryRunPlan = !dryRunLoading && !dryRunExecuting && Boolean(dryRunResult) && !dryRunPlanStale;

  const summary = useMemo(() => {
    return summarizeFailedPreviews(
      items.map((item) => ({
        previewStatus: item.previewStatus,
        previewFailureCategory: item.previewFailureCategory,
        previewFailureReason: item.previewFailureReason,
        mimeType: item.mimeType,
      }))
    );
  }, [items]);

  const filteredItems = useMemo(() => {
    const query = filterText.trim().toLowerCase();
    if (!query) {
      return items;
    }
    return items.filter((item) => {
      const haystacks = [
        item.name,
        item.path,
        item.mimeType,
        item.previewStatus,
        item.previewFailureCategory,
        item.previewFailureReason,
      ]
        .filter(Boolean)
        .map((value) => String(value).toLowerCase());
      return haystacks.some((value) => value.includes(query));
    });
  }, [filterText, items]);

  const filteredPreventionItems = useMemo(() => {
    const query = preventionFilterText.trim().toLowerCase();
    const source = preventionDiagnostics?.items || [];
    if (!query) {
      return source;
    }
    return source.filter((item) => {
      const haystacks = [
        item.name,
        item.path,
        item.mimeType,
        item.category,
        item.reason,
        item.documentId,
      ]
        .filter(Boolean)
        .map((value) => String(value).toLowerCase());
      return haystacks.some((value) => value.includes(query));
    });
  }, [preventionDiagnostics, preventionFilterText]);

  const filteredDeadLetterItems = useMemo(() => {
    const query = deadLetterFilterText.trim().toLowerCase();
    const source = deadLetterDiagnostics?.items || [];
    if (!query) {
      return source;
    }
    return source.filter((item) => {
      const haystacks = [
        item.name,
        item.path,
        item.mimeType,
        item.reason,
        item.category,
        item.policyKey,
        item.sourceStage,
        item.documentId,
        item.entryKey,
        item.renditionKey,
      ]
        .filter(Boolean)
        .map((value) => String(value).toLowerCase());
      return haystacks.some((value) => value.includes(query));
    });
  }, [deadLetterDiagnostics, deadLetterFilterText]);

  const filteredFailureLedgerItems = useMemo(() => {
    const query = failureLedgerFilterText.trim().toLowerCase();
    const source = failureLedgerDiagnostics?.items || [];
    if (!query) {
      return source;
    }
    return source.filter((item) => {
      const haystacks = [
        item.name,
        item.path,
        item.mimeType,
        item.previewStatus,
        item.lastReason,
        item.category,
        item.documentId,
      ]
        .filter(Boolean)
        .map((value) => String(value).toLowerCase());
      return haystacks.some((value) => value.includes(query));
    });
  }, [failureLedgerDiagnostics, failureLedgerFilterText]);

  const queueDeclinedCategoryOptions = useMemo(() => {
    const categories = new Set<string>(['ANY']);
    (queueDeclinedSummary?.items || []).forEach((item) => {
      const category = (item.category || '').trim().toUpperCase();
      if (category) {
        categories.add(category);
      }
    });
    return Array.from(categories);
  }, [queueDeclinedSummary]);

  const confidenceColor = backendSummary?.confidenceLevel === 'LOW' ? 'warning' : 'success';
  const confidenceLabel =
    backendSummary?.confidenceLevel === 'LOW'
      ? 'LOW confidence (sample truncated)'
      : 'HIGH confidence (sample complete)';
  const renditionTopReasons = useMemo(
    () => (renditionSummary?.topReasons || []).slice(0, RENDITION_TOP_REASONS_LIMIT),
    [renditionSummary]
  );
  const selectedPreventionSet = useMemo(() => new Set(selectedPreventionIds), [selectedPreventionIds]);
  const visiblePreventionSelectedCount = useMemo(
    () => filteredPreventionItems.filter((item) => selectedPreventionSet.has(item.documentId)).length,
    [filteredPreventionItems, selectedPreventionSet]
  );
  const allVisiblePreventionSelected = filteredPreventionItems.length > 0
    && visiblePreventionSelectedCount === filteredPreventionItems.length;
  const someVisiblePreventionSelected = visiblePreventionSelectedCount > 0 && !allVisiblePreventionSelected;
  const selectedDeadLetterSet = useMemo(() => new Set(selectedDeadLetterEntryKeys), [selectedDeadLetterEntryKeys]);
  const visibleDeadLetterSelectedCount = useMemo(
    () => filteredDeadLetterItems.filter((item) => selectedDeadLetterSet.has(item.entryKey || item.documentId)).length,
    [filteredDeadLetterItems, selectedDeadLetterSet]
  );
  const rollbackOptions = useMemo(
    () => policyHistory.filter((entry) => policyVersion === null || entry.version < policyVersion),
    [policyHistory, policyVersion]
  );
  const allVisibleDeadLetterSelected = filteredDeadLetterItems.length > 0
    && visibleDeadLetterSelectedCount === filteredDeadLetterItems.length;
  const someVisibleDeadLetterSelected = visibleDeadLetterSelectedCount > 0 && !allVisibleDeadLetterSelected;
  const selectedFailureLedgerSet = useMemo(() => new Set(selectedFailureLedgerIds), [selectedFailureLedgerIds]);
  const visibleFailureLedgerSelectedCount = useMemo(
    () => filteredFailureLedgerItems.filter((item) => selectedFailureLedgerSet.has(item.documentId)).length,
    [filteredFailureLedgerItems, selectedFailureLedgerSet]
  );
  const allVisibleFailureLedgerSelected = filteredFailureLedgerItems.length > 0
    && visibleFailureLedgerSelectedCount === filteredFailureLedgerItems.length;
  const someVisibleFailureLedgerSelected = visibleFailureLedgerSelectedCount > 0 && !allVisibleFailureLedgerSelected;

  const getReasonActionKey = (reason: string, category: string, force: boolean) =>
    `${category.toUpperCase()}|${reason}|${force ? 'force' : 'retry'}`;
  const getReasonLedgerResetActionKey = (reason: string, category: string) =>
    `${category.toUpperCase()}|${reason}|ledger-reset`;

  const matchReasonEntriesInCurrentList = useCallback(
    (reason: string, category: string, retryable: boolean) => {
      const normalizedReason = normalizePreviewFailureReason(reason);
      const normalizedCategory = (category || '').toUpperCase();
      return items.filter((item) => {
        const itemRetryable = isRetryablePreviewFailure(
          item.previewFailureCategory,
          item.mimeType,
          item.previewFailureReason
        );
        const itemReason = normalizePreviewFailureReason(item.previewFailureReason);
        const itemCategory = (item.previewFailureCategory || '').toUpperCase();
        return (
          itemRetryable === retryable
          && itemReason === normalizedReason
          && itemCategory === normalizedCategory
        );
      });
    },
    [items]
  );

  const getParentFolderPath = (rawPath?: string | null): string | null => {
    if (!rawPath) {
      return null;
    }
    const cleaned = rawPath.trim();
    if (!cleaned.startsWith('/')) {
      return null;
    }
    const parts = cleaned.split('/').filter(Boolean);
    if (parts.length < 2) {
      return null;
    }
    return `/${parts.slice(0, -1).join('/')}`;
  };

  const handleOpenInSearch = (item: PreviewFailureSample) => {
    const params = new URLSearchParams();
    const q = (item.name || '').trim();
    if (q) {
      params.set('q', q);
    }
    const previewStatus = (item.previewStatus || '').toUpperCase();
    if (previewStatus === 'FAILED' || previewStatus === 'UNSUPPORTED') {
      params.set('previewStatus', previewStatus);
    }
    const query = params.toString();
    navigate(query ? `/search?${query}` : '/search');
  };

  const handleCopyDocumentId = async (documentId: string) => {
    try {
      await navigator.clipboard.writeText(documentId);
      toast.success('Document id copied');
    } catch {
      toast.error('Failed to copy document id');
    }
  };

  const handleCopyId = async (item: PreviewFailureSample) => {
    await handleCopyDocumentId(item.id);
  };

  const handleOpenParentFolder = async (item: PreviewFailureSample) => {
    const parentPath = getParentFolderPath(item.path);
    if (!parentPath) {
      return;
    }
    try {
      const folder = await nodeService.getFolderByPath(parentPath);
      navigate(`/browse/${folder.id}`);
    } catch {
      toast.error('Failed to open parent folder');
    }
  };

  const handleQueuePreview = async (item: PreviewFailureSample, force: boolean) => {
    const retryable = isRetryablePreviewFailure(item.previewFailureCategory, item.mimeType, item.previewFailureReason);
    if (!retryable) {
      return;
    }
    try {
      setQueueingId(item.id);
      await nodeService.queuePreview(item.id, force);
      toast.success(`Preview ${force ? 'rebuild' : 'retry'} queued`);
      await loadFailures();
    } catch {
      toast.error('Failed to queue preview');
    } finally {
      setQueueingId(null);
    }
  };

  const handleQueueRenditionResource = async (resource: PreviewRenditionResource, force: boolean) => {
    const documentId = resource.documentId;
    if (!documentId) {
      return;
    }
    const normalizedStatus = (resource.status || '').toUpperCase();
    if (!force && normalizedStatus === 'UNSUPPORTED') {
      return;
    }
    try {
      setRenditionResourceActionId(documentId);
      const batch = await previewDiagnosticsService.queueFailuresBatch([documentId], force);
      const result = Array.isArray(batch.results) ? batch.results[0] : null;
      if (batch.queued > 0) {
        toast.success(`Rendition resource ${force ? 'rebuild' : 'retry'} queued`);
      } else if (batch.skipped > 0) {
        toast.warning(result?.message ? `Rendition resource skipped: ${result.message}` : 'Rendition resource skipped');
      } else {
        toast.error(result?.message ? `Failed to queue rendition resource: ${result.message}` : 'Failed to queue rendition resource');
      }
      await loadFailures();
    } catch {
      toast.error('Failed to queue rendition resource');
    } finally {
      setRenditionResourceActionId(null);
    }
  };

  const handleQueuePreviewByReason = async (
    reason: string,
    category: string,
    retryable: boolean,
    force: boolean
  ) => {
    if (!retryable) {
      return;
    }
    const reasonLabel = reason === 'UNSPECIFIED' ? 'Unspecified reason' : formatPreviewFailureReasonLabel(reason);

    const actionKey = getReasonActionKey(reason, category, force);
    setReasonBatchActionKey(actionKey);
    try {
      const batch = await opsRecoveryService.queueByReason({
        domain: 'PREVIEW',
        reason,
        category,
        retryable,
        maxDocuments: 100,
        days: windowDays,
        force,
      });

      const actionLabel = force ? 'Force rebuild' : 'Retry';
      if (batch.matched === 0) {
        toast.info(`No matched failures found for reason: ${reasonLabel} (window ${batch.windowDays || 'all'}d)`);
      } else if (batch.queued > 0 && batch.failed === 0 && batch.skipped === 0) {
        toast.success(`${actionLabel} queued for ${batch.queued}/${batch.matched} document(s): ${reasonLabel}`);
      } else if (batch.queued > 0) {
        toast.warning(
          `${actionLabel} matched=${batch.matched}, queued=${batch.queued}, skipped=${batch.skipped}, failed=${batch.failed}: ${reasonLabel}`
        );
      } else {
        toast.error(
          `${actionLabel} matched=${batch.matched}, skipped=${batch.skipped}, failed=${batch.failed} for reason group: ${reasonLabel}`
        );
      }
      await loadFailures();
    } finally {
      setReasonBatchActionKey(null);
    }
  };

  const handleDryRunByReason = async (
    reason: string,
    category: string,
    retryable: boolean
  ) => {
    const reasonLabel = reason === 'UNSPECIFIED' ? 'Unspecified reason' : formatPreviewFailureReasonLabel(reason);
    const actionKey = getReasonActionKey(reason, category, false);
    setReasonDryRunActionKey(actionKey);
    try {
      const result = await opsRecoveryService.dryRun({
        domain: 'PREVIEW',
        mode: 'QUEUE_BY_REASON',
        reason,
        category,
        retryable,
        maxDocuments: 100,
        days: windowDays,
        force: false,
      });
      if (result.matched === 0) {
        toast.info(`Dry-run matched 0 for reason: ${reasonLabel}`);
        return;
      }
      toast.info(
        `Dry-run ${reasonLabel}: matched=${result.matched}, queued=${result.estimatedQueued}, skipped=${result.estimatedSkipped}, failed=${result.estimatedFailed}`
      );
    } catch {
      toast.error(`Dry-run failed for reason: ${reasonLabel}`);
    } finally {
      setReasonDryRunActionKey(null);
    }
  };

  const handleClearDeadLetterByReason = async (
    reason: string,
    category: string,
    retryable: boolean
  ) => {
    const reasonLabel = reason === 'UNSPECIFIED' ? 'Unspecified reason' : formatPreviewFailureReasonLabel(reason);
    const actionKey = getReasonActionKey(reason, category, false);
    setReasonClearActionKey(actionKey);
    try {
      const result = await opsRecoveryService.clearByFilter({
        domain: 'PREVIEW',
        reason,
        category,
        retryable,
        maxDocuments: 100,
        days: windowDays,
      });
      const cleared = result.queued || 0;
      if (result.matched === 0) {
        toast.info(`No dead-letter matched for clear: ${reasonLabel}`);
      } else if (result.failed === 0 && cleared === result.matched) {
        toast.success(`Dead-letter clear done: ${cleared}/${result.matched} (${reasonLabel})`);
      } else if (cleared > 0) {
        toast.warning(`Dead-letter clear partial: matched=${result.matched}, cleared=${cleared}, failed=${result.failed}`);
      } else {
        toast.error(`Dead-letter clear failed for reason group: ${reasonLabel}`);
      }
      await loadFailures();
    } catch {
      toast.error(`Dead-letter clear request failed: ${reasonLabel}`);
    } finally {
      setReasonClearActionKey(null);
    }
  };

  const handleReplayDeadLetterByReason = async (
    reason: string,
    category: string,
    retryable: boolean
  ) => {
    const reasonLabel = reason === 'UNSPECIFIED' ? 'Unspecified reason' : formatPreviewFailureReasonLabel(reason);
    const actionKey = getReasonActionKey(reason, category, true);
    setReasonReplayDeadLetterActionKey(actionKey);
    try {
      const result = await opsRecoveryService.replayByFilter({
        domain: 'PREVIEW',
        reason,
        category,
        retryable,
        maxDocuments: 100,
        days: windowDays,
        force: true,
      });
      if (result.matched === 0) {
        toast.info(`No dead-letter matched for replay: ${reasonLabel}`);
      } else if (result.failed === 0 && result.skipped === 0 && result.queued > 0) {
        toast.success(`Dead-letter replay queued: ${result.queued}/${result.matched} (${reasonLabel})`);
      } else if (result.queued > 0) {
        toast.warning(`Dead-letter replay partial: matched=${result.matched}, queued=${result.queued}, skipped=${result.skipped}, failed=${result.failed}`);
      } else {
        toast.error(`Dead-letter replay failed for reason group: ${reasonLabel}`);
      }
      await loadFailures();
    } catch {
      toast.error(`Dead-letter replay request failed: ${reasonLabel}`);
    } finally {
      setReasonReplayDeadLetterActionKey(null);
    }
  };

  const handleResetFailureLedgerByReason = async (
    reason: string,
    category: string,
    retryable: boolean
  ) => {
    const reasonLabel = reason === 'UNSPECIFIED' ? 'Unspecified reason' : formatPreviewFailureReasonLabel(reason);
    const actionKey = getReasonLedgerResetActionKey(reason, category);
    setReasonLedgerResetActionKey(actionKey);
    try {
      const result = await previewDiagnosticsService.resetFailureLedgerByFilter({
        reason,
        category,
        retryable,
        maxDocuments: 100,
        days: windowDays,
      });
      if (result.matched === 0) {
        toast.info(`No failure ledger matched for reason: ${reasonLabel}`);
      } else if (result.failed === 0 && result.reset === result.matched) {
        toast.success(`Failure ledger reset done: ${result.reset}/${result.matched} (${reasonLabel})`);
      } else if (result.reset > 0) {
        toast.warning(
          `Failure ledger reset partial: matched=${result.matched}, reset=${result.reset}, skipped=${result.skipped}, failed=${result.failed}`
        );
      } else {
        toast.error(`Failure ledger reset failed for reason group: ${reasonLabel}`);
      }
      await loadFailures();
    } catch {
      toast.error(`Failure ledger reset request failed: ${reasonLabel}`);
    } finally {
      setReasonLedgerResetActionKey(null);
    }
  };

  const handlePolicyDraftChange = (
    profileKey: string,
    field: keyof FailurePolicyDraft,
    value: string
  ) => {
    setPolicyDrafts((current) => ({
      ...current,
      [profileKey]: {
        ...(current[profileKey] || {
          maxAttempts: '',
          retryDelayMs: '',
          backoffMultiplier: '',
          quietPeriodMs: '',
        }),
        [field]: value,
      },
    }));
  };

  const handleSavePolicy = async (profileKey: string) => {
    const draft = policyDrafts[profileKey];
    if (!draft) {
      return;
    }
    const maxAttempts = Number(draft.maxAttempts);
    const retryDelayMs = Number(draft.retryDelayMs);
    const backoffMultiplier = Number(draft.backoffMultiplier);
    const quietPeriodMs = Number(draft.quietPeriodMs);
    if (
      !Number.isFinite(maxAttempts)
      || !Number.isFinite(retryDelayMs)
      || !Number.isFinite(backoffMultiplier)
      || !Number.isFinite(quietPeriodMs)
    ) {
      toast.error('Policy values must be numeric');
      return;
    }

    try {
      setPolicySavingKey(profileKey);
      const updated = await opsPolicyService.updatePolicy('PREVIEW', {
        profileKey,
        maxAttempts: Math.floor(maxAttempts),
        retryDelayMs: Math.floor(retryDelayMs),
        backoffMultiplier,
        quietPeriodMs: Math.floor(quietPeriodMs),
        reason: `ui_update:${profileKey}`,
      });
      const nextPolicies = Array.isArray(updated.policies) ? updated.policies : [];
      setFailurePolicies(nextPolicies);
      setPolicyVersion(typeof updated.currentVersion === 'number' ? updated.currentVersion : policyVersion);
      setPolicyDrafts((current) => ({
        ...Object.fromEntries(nextPolicies.map((item) => [item.key, toPolicyDraft(item)])),
        [profileKey]: updated.updatedPolicy ? toPolicyDraft(updated.updatedPolicy) : current[profileKey],
      }));
      toast.success(`Policy saved: ${profileKey}${updated.currentVersion ? ` (v${updated.currentVersion})` : ''}`);
    } catch {
      toast.error(`Failed to save policy: ${profileKey}`);
    } finally {
      setPolicySavingKey(null);
    }
  };

  const handleRollbackPolicy = async () => {
    try {
      setPolicyRollbacking(true);
      const targetVersion = rollbackTargetVersion === '' ? undefined : rollbackTargetVersion;
      const rolled = await opsPolicyService.rollback('PREVIEW', {
        targetVersion,
        reason: targetVersion ? `ui_rollback_target:${targetVersion}` : 'ui_rollback_latest',
      });
      const nextPolicies = Array.isArray(rolled.policies) ? rolled.policies : [];
      setFailurePolicies(nextPolicies);
      setPolicyVersion(typeof rolled.currentVersion === 'number' ? rolled.currentVersion : policyVersion);
      setPolicyDrafts(Object.fromEntries(nextPolicies.map((item) => [item.key, toPolicyDraft(item)])));
      toast.success(
        `Policy rolled back: v${rolled.previousVersion} -> v${rolled.rolledBackToVersion} (current v${rolled.currentVersion})`
      );
      await loadFailures();
    } catch {
      toast.error('Failed to rollback policy');
    } finally {
      setPolicyRollbacking(false);
    }
  };

  const handlePreventionAction = async (documentId: string, requeue: boolean) => {
    try {
      setPreventionActionId(documentId);
      const result = requeue
        ? await previewDiagnosticsService.unblockAndRequeueRendition(documentId, true)
        : await previewDiagnosticsService.unblockRenditionPrevention(documentId);
      if (result.queued) {
        toast.success(`Unblocked and queued: ${documentId}`);
      } else if (result.unblocked) {
        toast.success(`Unblocked: ${documentId}`);
      } else {
        toast.warning(result.message || `Action finished: ${documentId}`);
      }
      await loadFailures();
    } catch {
      toast.error(requeue ? 'Failed to unblock and queue preview' : 'Failed to unblock prevention marker');
    } finally {
      setPreventionActionId(null);
    }
  };

  const handleTogglePreventionSelection = (documentId: string, checked: boolean) => {
    setSelectedPreventionIds((current) => {
      if (checked) {
        if (current.includes(documentId)) {
          return current;
        }
        return [...current, documentId];
      }
      return current.filter((id) => id !== documentId);
    });
  };

  const handleToggleAllPreventionSelection = (checked: boolean) => {
    if (checked) {
      setSelectedPreventionIds(filteredPreventionItems.map((item) => item.documentId));
    } else {
      setSelectedPreventionIds([]);
    }
  };

  const handlePreventionBatchAction = async (requeue: boolean) => {
    if (selectedPreventionIds.length === 0) {
      return;
    }
    try {
      setPreventionActionId('__batch__');
      const result = requeue
        ? await previewDiagnosticsService.unblockAndRequeueRenditionBatch(selectedPreventionIds, true)
        : await previewDiagnosticsService.unblockRenditionPreventionBatch(selectedPreventionIds);
      if (result.failed === 0) {
        toast.success(
          requeue
            ? `Requeue batch done: queued ${result.queued}/${result.deduplicated}`
            : `Unblock batch done: ${result.unblocked}/${result.deduplicated}`
        );
      } else {
        toast.warning(
          requeue
            ? `Requeue batch partial: queued=${result.queued}, failed=${result.failed}`
            : `Unblock batch partial: unblocked=${result.unblocked}, failed=${result.failed}`
        );
      }
      setSelectedPreventionIds([]);
      await loadFailures();
    } catch {
      toast.error(requeue ? 'Failed to run batch requeue' : 'Failed to run batch unblock');
    } finally {
      setPreventionActionId(null);
    }
  };

  const handleToggleFailureLedgerSelection = (documentId: string, checked: boolean) => {
    setSelectedFailureLedgerIds((current) => {
      if (checked) {
        if (current.includes(documentId)) {
          return current;
        }
        return [...current, documentId];
      }
      return current.filter((id) => id !== documentId);
    });
  };

  const handleToggleAllFailureLedgerSelection = (checked: boolean) => {
    if (checked) {
      setSelectedFailureLedgerIds(filteredFailureLedgerItems.map((item) => item.documentId));
    } else {
      setSelectedFailureLedgerIds([]);
    }
  };

  const handleResetFailureLedger = async (documentId: string) => {
    if (!documentId) {
      return;
    }
    try {
      setFailureLedgerActionId(documentId);
      const result = await previewDiagnosticsService.resetFailureLedger(documentId);
      if (result.outcome === 'RESET') {
        toast.success(`Failure ledger reset: ${documentId}`);
      } else if (result.outcome === 'SKIPPED') {
        toast.info(result.message || `Failure ledger already empty: ${documentId}`);
      } else {
        toast.error(result.message || `Failure ledger reset failed: ${documentId}`);
      }
      await loadFailures();
    } catch {
      toast.error('Failed to reset failure ledger');
    } finally {
      setFailureLedgerActionId(null);
    }
  };

  const handleResetFailureLedgerBatch = async () => {
    if (selectedFailureLedgerIds.length === 0) {
      return;
    }
    try {
      setFailureLedgerActionId('__batch__');
      const result = await previewDiagnosticsService.resetFailureLedgerBatch(selectedFailureLedgerIds);
      if (result.failed === 0) {
        toast.success(`Failure ledger reset: ${result.reset}/${result.deduplicated}`);
      } else if (result.reset > 0) {
        toast.warning(`Failure ledger partial reset: reset=${result.reset}, failed=${result.failed}`);
      } else {
        toast.error(`Failure ledger reset failed: failed=${result.failed}`);
      }
      setSelectedFailureLedgerIds([]);
      await loadFailures();
    } catch {
      toast.error('Failed to run failure ledger batch reset');
    } finally {
      setFailureLedgerActionId(null);
    }
  };

  const handleExportFailureLedgerCsv = async () => {
    try {
      await previewDiagnosticsService.exportFailureLedgerCsv(windowDays, FAILURE_LEDGER_EXPORT_LIMIT);
      toast.success('Failure ledger CSV exported');
    } catch {
      toast.error('Failed to export failure ledger CSV');
    }
  };

  const handleApplyQueueDiagnosticsFilters = () => {
    const normalizedQuery = queueDiagnosticsQueryInput.trim();
    if (normalizedQuery === queueDiagnosticsQueryFilter) {
      void loadFailures();
      return;
    }
    setQueueDiagnosticsQueryFilter(normalizedQuery);
  };

  const handleClearQueueDiagnosticsFilters = () => {
    const hasStateFilter = queueDiagnosticsStateFilter !== 'ALL';
    const hasQueryFilter = queueDiagnosticsQueryFilter.length > 0 || queueDiagnosticsQueryInput.length > 0;
    setQueueDiagnosticsStateFilter('ALL');
    setQueueDiagnosticsQueryInput('');
    setQueueDiagnosticsQueryFilter('');
    if (!hasStateFilter && !hasQueryFilter) {
      void loadFailures();
    }
  };

  const handleExportQueueDiagnosticsCsv = async () => {
    try {
      await previewDiagnosticsService.exportQueueDiagnosticsCsv(
        QUEUE_DIAGNOSTICS_EXPORT_LIMIT,
        queueDiagnosticsStateFilter,
        queueDiagnosticsQueryFilter || undefined
      );
      toast.success('Queue diagnostics CSV exported');
    } catch {
      toast.error('Failed to export queue diagnostics CSV');
    }
  };

  const handleCancelQueueDiagnosticsActive = async () => {
    try {
      setQueueDiagnosticsCancelling(true);
      const result = await previewDiagnosticsService.cancelQueueDiagnosticsActive(
        QUEUE_DIAGNOSTICS_EXPORT_LIMIT,
        queueDiagnosticsStateFilter,
        queueDiagnosticsQueryFilter || undefined
      );
      if (result.cancelled > 0 && result.failed === 0) {
        toast.success(`Queue cancel done: ${result.cancelled}/${result.requested}`);
      } else if (result.cancelled > 0) {
        toast.warning(`Queue cancel partial: cancelled=${result.cancelled}, failed=${result.failed}`);
      } else if (result.failed > 0) {
        toast.error(`Queue cancel failed: failed=${result.failed}`);
      } else {
        toast.info('No queue tasks matched current filter');
      }
      await loadFailures();
    } catch {
      toast.error('Failed to cancel filtered queue tasks');
    } finally {
      setQueueDiagnosticsCancelling(false);
    }
  };

  const handleApplyQueueDeclinedFilters = () => {
    const normalizedQuery = queueDeclinedQueryInput.trim();
    setQueueDeclinedDryRun(null);
    if (normalizedQuery === queueDeclinedQueryFilter) {
      void loadFailures();
      return;
    }
    setQueueDeclinedQueryFilter(normalizedQuery);
  };

  const handleClearQueueDeclinedFilters = () => {
    const hasCategoryFilter = queueDeclinedCategoryFilter !== 'ANY';
    const hasForceRequiredFilter = queueDeclinedForceRequiredFilter !== 'ANY';
    const hasWindowHoursFilter = queueDeclinedWindowHoursFilter !== 0;
    const hasQueryFilter = queueDeclinedQueryFilter.length > 0 || queueDeclinedQueryInput.length > 0;
    setQueueDeclinedCategoryFilter('ANY');
    setQueueDeclinedForceRequiredFilter('ANY');
    setQueueDeclinedWindowHoursFilter(0);
    setQueueDeclinedQueryInput('');
    setQueueDeclinedQueryFilter('');
    setQueueDeclinedDryRun(null);
    if (!hasCategoryFilter && !hasForceRequiredFilter && !hasWindowHoursFilter && !hasQueryFilter) {
      void loadFailures();
    }
  };

  const handleExportQueueDeclinedCsv = async () => {
    try {
      await previewDiagnosticsService.exportQueueDeclinedCsv(
        QUEUE_DECLINED_EXPORT_LIMIT,
        queueDeclinedCategoryFilter,
        queueDeclinedForceRequiredFilter,
        queueDeclinedQueryFilter || undefined,
        queueDeclinedWindowHoursFilter
      );
      toast.success('Queue declined CSV exported');
    } catch {
      toast.error('Failed to export queue declined CSV');
    }
  };

  const handleStartQueueDeclinedExportTask = async () => {
    try {
      setQueueDeclinedExportTaskStarting(true);
      const task = await previewDiagnosticsService.startQueueDeclinedExportTask(
        QUEUE_DECLINED_EXPORT_LIMIT,
        queueDeclinedCategoryFilter,
        queueDeclinedForceRequiredFilter,
        queueDeclinedQueryFilter || undefined,
        queueDeclinedWindowHoursFilter
      );
      const taskId = task.taskId?.trim();
      if (!taskId) {
        throw new Error('missing_task_id');
      }
      setQueueDeclinedExportTaskPage(0);
      const deduplicated = task?.deduplicated === true;
      if (deduplicated) {
        const reusedTaskId = (task?.deduplicatedFromTaskId || taskId || '').trim() || taskId;
        toast.info(`Queue declined async export task reused: ${reusedTaskId}`);
      } else {
        toast.success(`Queue declined async export task started: ${taskId}`);
      }
      await loadQueueDeclinedExportTasks(true);
    } catch {
      toast.error('Failed to start queue declined async export task');
    } finally {
      setQueueDeclinedExportTaskStarting(false);
    }
  };

  const handleCancelQueueDeclinedExportTask = async (taskId: string) => {
    try {
      setQueueDeclinedExportTaskActionId(taskId);
      setQueueDeclinedExportTaskActionType('cancel');
      await previewDiagnosticsService.cancelQueueDeclinedExportTask(taskId);
      toast.success(`Queue declined async export task cancelled: ${taskId}`);
      await loadQueueDeclinedExportTasks(true);
    } catch {
      toast.error(`Failed to cancel queue declined async export task: ${taskId}`);
    } finally {
      setQueueDeclinedExportTaskActionId(null);
      setQueueDeclinedExportTaskActionType(null);
    }
  };

  const handleRetryQueueDeclinedExportTask = async (taskId: string) => {
    try {
      setQueueDeclinedExportTaskActionId(taskId);
      setQueueDeclinedExportTaskActionType('retry');
      const retriedTask = await previewDiagnosticsService.retryQueueDeclinedExportTask(taskId);
      const retriedTaskId = retriedTask.taskId?.trim();
      if (!retriedTaskId) {
        throw new Error('missing_task_id');
      }
      if (retriedTask?.deduplicated === true) {
        const reusedTaskId = (retriedTask?.deduplicatedFromTaskId || retriedTaskId || '').trim() || retriedTaskId;
        toast.info(`Queue declined async export task reused: ${reusedTaskId}`);
      } else {
        toast.success(`Queue declined async export task retried: ${retriedTaskId}`);
      }
      await loadQueueDeclinedExportTasks(true);
    } catch {
      toast.error(`Failed to retry queue declined async export task: ${taskId}`);
    } finally {
      setQueueDeclinedExportTaskActionId(null);
      setQueueDeclinedExportTaskActionType(null);
    }
  };

  const handleDownloadQueueDeclinedExportTask = async (task: PreviewQueueDeclinedExportTask) => {
    try {
      setQueueDeclinedExportTaskActionId(task.taskId);
      setQueueDeclinedExportTaskActionType('download');
      const latest = await previewDiagnosticsService.getQueueDeclinedExportTask(task.taskId).catch(() => task);
      const blob = await previewDiagnosticsService.downloadQueueDeclinedExportTask(task.taskId);
      const fallback = `preview_queue_declined_async_${format(new Date(), 'yyyyMMdd-HHmmss')}.csv`;
      const filename = (latest.filename || task.filename || '').trim() || fallback;
      const url = window.URL.createObjectURL(blob);
      const anchor = document.createElement('a');
      anchor.href = url;
      anchor.download = filename;
      document.body.appendChild(anchor);
      anchor.click();
      anchor.remove();
      window.URL.revokeObjectURL(url);
      toast.success(`Queue declined async export downloaded: ${filename}`);
    } catch {
      toast.error(`Failed to download queue declined async export task: ${task.taskId}`);
    } finally {
      setQueueDeclinedExportTaskActionId(null);
      setQueueDeclinedExportTaskActionType(null);
    }
  };

  const handleCancelActiveQueueDeclinedExportTasks = async () => {
    try {
      setQueueDeclinedExportTaskCancellingActive(true);
      const statusFilter: PreviewQueueDeclinedExportTaskActiveStatusFilter | undefined = (
        queueDeclinedExportTaskStatusFilter === 'QUEUED'
        || queueDeclinedExportTaskStatusFilter === 'RUNNING'
      ) ? queueDeclinedExportTaskStatusFilter : undefined;
      const response = await previewDiagnosticsService.cancelActiveQueueDeclinedExportTasks(statusFilter);
      const cancelledCount = Number.isFinite(response?.cancelledCount) ? response.cancelledCount : 0;
      if (cancelledCount > 0) {
        toast.success(response?.message || `Cancelled ${cancelledCount} active queue declined async export task(s)`);
      } else {
        toast.info(response?.message || 'No active queue declined async export tasks matched cancel-active filters');
      }
      await loadQueueDeclinedExportTasks(true);
    } catch {
      toast.error('Failed to cancel active queue declined async export tasks');
    } finally {
      setQueueDeclinedExportTaskCancellingActive(false);
    }
  };

  const handleCleanupQueueDeclinedExportTasks = async () => {
    try {
      setQueueDeclinedExportTaskCleaning(true);
      const statusFilter = queueDeclinedExportTaskStatusFilter === 'ALL'
        ? undefined
        : queueDeclinedExportTaskStatusFilter;
      const response = await previewDiagnosticsService.cleanupQueueDeclinedExportTasks(statusFilter);
      const deletedCount = Number.isFinite(response?.deletedCount) ? response.deletedCount : 0;
      if (deletedCount > 0) {
        toast.success(response?.message || `Queue declined async export cleanup removed ${deletedCount} task(s)`);
      } else {
        toast.info(response?.message || 'No queue declined async export tasks matched cleanup filter');
      }
      setQueueDeclinedExportTaskPage(0);
      await loadQueueDeclinedExportTasks(true);
    } catch {
      toast.error('Failed to cleanup queue declined async export tasks');
    } finally {
      setQueueDeclinedExportTaskCleaning(false);
    }
  };

  const handleRetryTerminalQueueDeclinedExportTasks = async () => {
    try {
      setQueueDeclinedExportTaskRetryingTerminal(true);
      const statusFilter: PreviewQueueDeclinedExportTaskTerminalStatusFilter | undefined = (
        queueDeclinedExportTaskStatusFilter === 'FAILED'
        || queueDeclinedExportTaskStatusFilter === 'CANCELLED'
        || queueDeclinedExportTaskStatusFilter === 'COMPLETED'
        || queueDeclinedExportTaskStatusFilter === 'TIMED_OUT'
        || queueDeclinedExportTaskStatusFilter === 'EXPIRED'
      ) ? queueDeclinedExportTaskStatusFilter : undefined;
      const response = await previewDiagnosticsService.retryTerminalQueueDeclinedExportTasks(
        statusFilter,
        QUEUE_DECLINED_EXPORT_TASK_DEFAULT_MAX_ITEMS
      );
      const retriedCount = Number.isFinite(response?.retried) ? response.retried : 0;
      const reusedCount = Number.isFinite(response?.reused) ? response.reused : 0;
      const skippedCount = Number.isFinite(response?.skipped) ? response.skipped : 0;
      const failedCount = Number.isFinite(response?.failed) ? response.failed : 0;
      const summary = `retried=${retriedCount}, reused=${reusedCount}, skipped=${skippedCount}, failed=${failedCount}`;
      if (retriedCount > 0) {
        toast.success(`Queue declined async terminal retry done: ${summary}`);
      } else {
        toast.info(response?.message || `No queue declined async terminal tasks matched retry filters (${summary})`);
      }
      await loadQueueDeclinedExportTasks(true);
    } catch {
      toast.error('Failed to retry terminal queue declined async export tasks');
    } finally {
      setQueueDeclinedExportTaskRetryingTerminal(false);
    }
  };

  const handleDryRunRetryTerminalQueueDeclinedExportTasks = async () => {
    try {
      setQueueDeclinedExportTaskDryRunningTerminal(true);
      const statusFilter: PreviewQueueDeclinedExportTaskTerminalStatusFilter | undefined = (
        queueDeclinedExportTaskStatusFilter === 'FAILED'
        || queueDeclinedExportTaskStatusFilter === 'CANCELLED'
        || queueDeclinedExportTaskStatusFilter === 'COMPLETED'
        || queueDeclinedExportTaskStatusFilter === 'TIMED_OUT'
        || queueDeclinedExportTaskStatusFilter === 'EXPIRED'
      ) ? queueDeclinedExportTaskStatusFilter : undefined;
      const response = await previewDiagnosticsService.dryRunRetryTerminalQueueDeclinedExportTasks(
        statusFilter,
        QUEUE_DECLINED_EXPORT_TASK_DEFAULT_MAX_ITEMS
      );
      setQueueDeclinedExportTaskRetryDryRun(response || null);
      const retryableTaskIds = (response?.results || [])
        .filter((item) => (item?.outcome || '').toUpperCase() === 'RETRYABLE')
        .map((item) => String(item?.sourceTaskId || '').trim())
        .filter((taskId) => taskId.length > 0);
      setQueueDeclinedExportTaskRetryDryRunSelectedTaskIds(Array.from(new Set(retryableTaskIds)));
      const retryableCount = Number.isFinite(response?.retryable) ? response.retryable : 0;
      const skippedCount = Number.isFinite(response?.skipped) ? response.skipped : 0;
      const summary = `retryable=${retryableCount}, skipped=${skippedCount}`;
      if (retryableCount > 0) {
        toast.info(`Queue declined async terminal dry-run: ${summary}`);
      } else {
        toast.info(response?.message || `No queue declined async terminal tasks matched dry-run filters (${summary})`);
      }
    } catch {
      toast.error('Failed to dry-run terminal retry for queue declined async export tasks');
    } finally {
      setQueueDeclinedExportTaskDryRunningTerminal(false);
    }
  };

  const handleExportDryRunRetryTerminalQueueDeclinedExportTasks = async () => {
    try {
      setQueueDeclinedExportTaskDryRunExporting(true);
      const statusFilter: PreviewQueueDeclinedExportTaskTerminalStatusFilter | undefined = (
        queueDeclinedExportTaskStatusFilter === 'FAILED'
        || queueDeclinedExportTaskStatusFilter === 'CANCELLED'
        || queueDeclinedExportTaskStatusFilter === 'COMPLETED'
        || queueDeclinedExportTaskStatusFilter === 'TIMED_OUT'
        || queueDeclinedExportTaskStatusFilter === 'EXPIRED'
      ) ? queueDeclinedExportTaskStatusFilter : undefined;
      await previewDiagnosticsService.exportDryRunRetryTerminalQueueDeclinedExportTasks(
        statusFilter,
        QUEUE_DECLINED_EXPORT_TASK_DEFAULT_MAX_ITEMS
      );
      toast.success('Queue declined async terminal dry-run CSV exported');
    } catch {
      toast.error('Failed to export queue declined async terminal dry-run CSV');
    } finally {
      setQueueDeclinedExportTaskDryRunExporting(false);
    }
  };

  const handleRetrySelectedQueueDeclinedExportTasks = async () => {
    if (queueDeclinedExportTaskRetryDryRunSelectedTaskIds.length === 0) {
      toast.info('No selected terminal async export tasks to retry');
      return;
    }
    try {
      setQueueDeclinedExportTaskRetryingSelected(true);
      const response = await previewDiagnosticsService.retryTerminalQueueDeclinedExportTasksByTaskIds(
        queueDeclinedExportTaskRetryDryRunSelectedTaskIds
      );
      const retriedCount = Number.isFinite(response?.retried) ? response.retried : 0;
      const reusedCount = Number.isFinite(response?.reused) ? response.reused : 0;
      const skippedCount = Number.isFinite(response?.skipped) ? response.skipped : 0;
      const failedCount = Number.isFinite(response?.failed) ? response.failed : 0;
      const summary = `retried=${retriedCount}, reused=${reusedCount}, skipped=${skippedCount}, failed=${failedCount}`;
      if (retriedCount > 0) {
        toast.success(`Queue declined async selected retry done: ${summary}`);
      } else {
        toast.info(response?.message || `No selected queue declined async export tasks were retried (${summary})`);
      }
      setQueueDeclinedExportTaskRetryDryRun(null);
      setQueueDeclinedExportTaskRetryDryRunSelectedTaskIds([]);
      await loadQueueDeclinedExportTasks(true);
    } catch {
      toast.error('Failed to retry selected terminal queue declined async export tasks');
    } finally {
      setQueueDeclinedExportTaskRetryingSelected(false);
    }
  };

  const handleRequeueQueueDeclined = async () => {
    try {
      setQueueDeclinedRequeueing(true);
      const result = await previewDiagnosticsService.requeueQueueDeclined(
        QUEUE_DECLINED_EXPORT_LIMIT,
        queueDeclinedCategoryFilter,
        queueDeclinedForceRequiredFilter,
        queueDeclinedQueryFilter || undefined,
        queueDeclinedForce,
        queueDeclinedWindowHoursFilter
      );
      if (result.queued > 0 && result.failed === 0) {
        toast.success(`Declined requeue done: ${result.queued}/${result.requested}`);
      } else if (result.queued > 0) {
        toast.warning(`Declined requeue partial: queued=${result.queued}, failed=${result.failed}`);
      } else if (result.failed > 0) {
        toast.error(`Declined requeue failed: failed=${result.failed}`);
      } else {
        toast.info('No declined queue items matched current filter');
      }
      setQueueDeclinedDryRun(null);
      await loadFailures();
    } catch {
      toast.error('Failed to requeue declined queue items');
    } finally {
      setQueueDeclinedRequeueing(false);
    }
  };

  const handleDryRunQueueDeclinedRequeue = async () => {
    try {
      setQueueDeclinedDryRunning(true);
      const result = await previewDiagnosticsService.dryRunQueueDeclinedRequeue(
        QUEUE_DECLINED_EXPORT_LIMIT,
        queueDeclinedCategoryFilter,
        queueDeclinedForceRequiredFilter,
        queueDeclinedQueryFilter || undefined,
        queueDeclinedForce,
        queueDeclinedWindowHoursFilter
      );
      setQueueDeclinedDryRun(result);
      toast.info(
        `Declined dry-run: queued=${result.estimatedQueued}, skipped=${result.estimatedSkipped}, failed=${result.estimatedFailed}`
      );
    } catch {
      toast.error('Failed to dry-run declined requeue');
    } finally {
      setQueueDeclinedDryRunning(false);
    }
  };

  const handleExportQueueDeclinedRequeueDryRunCsv = async () => {
    try {
      setQueueDeclinedDryRunExporting(true);
      await previewDiagnosticsService.exportQueueDeclinedRequeueDryRunCsv(
        QUEUE_DECLINED_EXPORT_LIMIT,
        queueDeclinedCategoryFilter,
        queueDeclinedForceRequiredFilter,
        queueDeclinedQueryFilter || undefined,
        queueDeclinedForce,
        queueDeclinedWindowHoursFilter
      );
      toast.success('Queue declined requeue dry-run CSV exported');
    } catch {
      toast.error('Failed to export queue declined requeue dry-run CSV');
    } finally {
      setQueueDeclinedDryRunExporting(false);
    }
  };

  const handleStartQueueDeclinedRequeueDryRunExportTask = async () => {
    try {
      setQueueDeclinedRequeueDryRunExportTaskStarting(true);
      const task = await previewDiagnosticsService.startQueueDeclinedRequeueDryRunExportTask(
        QUEUE_DECLINED_EXPORT_LIMIT,
        queueDeclinedCategoryFilter,
        queueDeclinedForceRequiredFilter,
        queueDeclinedQueryFilter || undefined,
        queueDeclinedForce,
        queueDeclinedWindowHoursFilter
      );
      const taskId = task.taskId?.trim();
      if (!taskId) {
        throw new Error('missing_task_id');
      }
      setQueueDeclinedRequeueDryRunExportTaskPage(0);
      const deduplicated = task?.deduplicated === true;
      if (deduplicated) {
        const reusedTaskId = (task?.deduplicatedFromTaskId || taskId || '').trim() || taskId;
        toast.info(`Queue declined requeue dry-run async export task reused: ${reusedTaskId}`);
      } else {
        toast.success(`Queue declined requeue dry-run async export task started: ${taskId}`);
      }
      await loadQueueDeclinedRequeueDryRunExportTasks(true);
    } catch {
      toast.error('Failed to start queue declined requeue dry-run async export task');
    } finally {
      setQueueDeclinedRequeueDryRunExportTaskStarting(false);
    }
  };

  const handleCancelQueueDeclinedRequeueDryRunExportTask = async (taskId: string) => {
    try {
      setQueueDeclinedRequeueDryRunExportTaskActionId(taskId);
      setQueueDeclinedRequeueDryRunExportTaskActionType('cancel');
      await previewDiagnosticsService.cancelQueueDeclinedRequeueDryRunExportTask(taskId);
      toast.success(`Queue declined requeue dry-run async export task cancelled: ${taskId}`);
      await loadQueueDeclinedRequeueDryRunExportTasks(true);
    } catch {
      toast.error(`Failed to cancel queue declined requeue dry-run async export task: ${taskId}`);
    } finally {
      setQueueDeclinedRequeueDryRunExportTaskActionId(null);
      setQueueDeclinedRequeueDryRunExportTaskActionType(null);
    }
  };

  const handleRetryQueueDeclinedRequeueDryRunExportTask = async (taskId: string) => {
    try {
      setQueueDeclinedRequeueDryRunExportTaskActionId(taskId);
      setQueueDeclinedRequeueDryRunExportTaskActionType('retry');
      const retriedTask = await previewDiagnosticsService.retryQueueDeclinedRequeueDryRunExportTask(taskId);
      const retriedTaskId = retriedTask.taskId?.trim();
      if (!retriedTaskId) {
        throw new Error('missing_task_id');
      }
      if (retriedTask?.deduplicated === true) {
        const reusedTaskId = (retriedTask?.deduplicatedFromTaskId || retriedTaskId || '').trim() || retriedTaskId;
        toast.info(`Queue declined requeue dry-run async export task reused: ${reusedTaskId}`);
      } else {
        toast.success(`Queue declined requeue dry-run async export task retried: ${retriedTaskId}`);
      }
      await loadQueueDeclinedRequeueDryRunExportTasks(true);
    } catch {
      toast.error(`Failed to retry queue declined requeue dry-run async export task: ${taskId}`);
    } finally {
      setQueueDeclinedRequeueDryRunExportTaskActionId(null);
      setQueueDeclinedRequeueDryRunExportTaskActionType(null);
    }
  };

  const handleDownloadQueueDeclinedRequeueDryRunExportTask = async (
    task: PreviewQueueDeclinedRequeueDryRunExportTask
  ) => {
    try {
      setQueueDeclinedRequeueDryRunExportTaskActionId(task.taskId);
      setQueueDeclinedRequeueDryRunExportTaskActionType('download');
      const latest = await previewDiagnosticsService.getQueueDeclinedRequeueDryRunExportTask(task.taskId).catch(() => task);
      const blob = await previewDiagnosticsService.downloadQueueDeclinedRequeueDryRunExportTask(task.taskId);
      const fallback = `preview_queue_declined_requeue_dry_run_async_${format(new Date(), 'yyyyMMdd-HHmmss')}.csv`;
      const filename = (latest.filename || task.filename || '').trim() || fallback;
      const url = window.URL.createObjectURL(blob);
      const anchor = document.createElement('a');
      anchor.href = url;
      anchor.download = filename;
      document.body.appendChild(anchor);
      anchor.click();
      anchor.remove();
      window.URL.revokeObjectURL(url);
      toast.success(`Queue declined requeue dry-run async export downloaded: ${filename}`);
    } catch {
      toast.error(`Failed to download queue declined requeue dry-run async export task: ${task.taskId}`);
    } finally {
      setQueueDeclinedRequeueDryRunExportTaskActionId(null);
      setQueueDeclinedRequeueDryRunExportTaskActionType(null);
    }
  };

  const handleCancelActiveQueueDeclinedRequeueDryRunExportTasks = async () => {
    try {
      setQueueDeclinedRequeueDryRunExportTaskCancellingActive(true);
      const statusFilter: PreviewQueueDeclinedRequeueDryRunExportTaskActiveStatusFilter | undefined = (
        queueDeclinedRequeueDryRunExportTaskStatusFilter === 'QUEUED'
        || queueDeclinedRequeueDryRunExportTaskStatusFilter === 'RUNNING'
      ) ? queueDeclinedRequeueDryRunExportTaskStatusFilter : undefined;
      const response = await previewDiagnosticsService.cancelActiveQueueDeclinedRequeueDryRunExportTasks(statusFilter);
      const cancelledCount = Number.isFinite(response?.cancelledCount) ? response.cancelledCount : 0;
      if (cancelledCount > 0) {
        toast.success(response?.message || `Cancelled ${cancelledCount} active queue declined requeue dry-run async export task(s)`);
      } else {
        toast.info(response?.message || 'No active queue declined requeue dry-run async export tasks matched cancel-active filters');
      }
      await loadQueueDeclinedRequeueDryRunExportTasks(true);
    } catch {
      toast.error('Failed to cancel active queue declined requeue dry-run async export tasks');
    } finally {
      setQueueDeclinedRequeueDryRunExportTaskCancellingActive(false);
    }
  };

  const handleCleanupQueueDeclinedRequeueDryRunExportTasks = async () => {
    try {
      setQueueDeclinedRequeueDryRunExportTaskCleaning(true);
      const statusFilter = queueDeclinedRequeueDryRunExportTaskStatusFilter === 'ALL'
        ? undefined
        : queueDeclinedRequeueDryRunExportTaskStatusFilter;
      const response = await previewDiagnosticsService.cleanupQueueDeclinedRequeueDryRunExportTasks(statusFilter);
      const deletedCount = Number.isFinite(response?.deletedCount) ? response.deletedCount : 0;
      if (deletedCount > 0) {
        toast.success(response?.message || `Queue declined requeue dry-run async export cleanup removed ${deletedCount} task(s)`);
      } else {
        toast.info(response?.message || 'No queue declined requeue dry-run async export tasks matched cleanup filter');
      }
      setQueueDeclinedRequeueDryRunExportTaskPage(0);
      await loadQueueDeclinedRequeueDryRunExportTasks(true);
    } catch {
      toast.error('Failed to cleanup queue declined requeue dry-run async export tasks');
    } finally {
      setQueueDeclinedRequeueDryRunExportTaskCleaning(false);
    }
  };

  const handleRetryTerminalQueueDeclinedRequeueDryRunExportTasks = async () => {
    try {
      setQueueDeclinedRequeueDryRunExportTaskRetryingTerminal(true);
      const statusFilter: PreviewQueueDeclinedRequeueDryRunExportTaskTerminalStatusFilter | undefined = (
        queueDeclinedRequeueDryRunExportTaskStatusFilter === 'FAILED'
        || queueDeclinedRequeueDryRunExportTaskStatusFilter === 'CANCELLED'
        || queueDeclinedRequeueDryRunExportTaskStatusFilter === 'COMPLETED'
        || queueDeclinedRequeueDryRunExportTaskStatusFilter === 'TIMED_OUT'
        || queueDeclinedRequeueDryRunExportTaskStatusFilter === 'EXPIRED'
      ) ? queueDeclinedRequeueDryRunExportTaskStatusFilter : undefined;
      const response = await previewDiagnosticsService.retryTerminalQueueDeclinedRequeueDryRunExportTasks(
        statusFilter,
        QUEUE_DECLINED_REQUEUE_DRY_RUN_EXPORT_TASK_DEFAULT_MAX_ITEMS
      );
      const retriedCount = Number.isFinite(response?.retried) ? response.retried : 0;
      const reusedCount = Number.isFinite(response?.reused) ? response.reused : 0;
      const skippedCount = Number.isFinite(response?.skipped) ? response.skipped : 0;
      const failedCount = Number.isFinite(response?.failed) ? response.failed : 0;
      const summary = `retried=${retriedCount}, reused=${reusedCount}, skipped=${skippedCount}, failed=${failedCount}`;
      if (retriedCount > 0) {
        toast.success(`Queue declined requeue dry-run async terminal retry done: ${summary}`);
      } else {
        toast.info(response?.message || `No terminal queue declined requeue dry-run async export tasks matched retry filters (${summary})`);
      }
      await loadQueueDeclinedRequeueDryRunExportTasks(true);
    } catch {
      toast.error('Failed to retry terminal queue declined requeue dry-run async export tasks');
    } finally {
      setQueueDeclinedRequeueDryRunExportTaskRetryingTerminal(false);
    }
  };

  const handleDryRunRetryTerminalQueueDeclinedRequeueDryRunExportTasks = async () => {
    try {
      setQueueDeclinedRequeueDryRunExportTaskDryRunningTerminal(true);
      const statusFilter: PreviewQueueDeclinedRequeueDryRunExportTaskTerminalStatusFilter | undefined = (
        queueDeclinedRequeueDryRunExportTaskStatusFilter === 'FAILED'
        || queueDeclinedRequeueDryRunExportTaskStatusFilter === 'CANCELLED'
        || queueDeclinedRequeueDryRunExportTaskStatusFilter === 'COMPLETED'
        || queueDeclinedRequeueDryRunExportTaskStatusFilter === 'TIMED_OUT'
        || queueDeclinedRequeueDryRunExportTaskStatusFilter === 'EXPIRED'
      ) ? queueDeclinedRequeueDryRunExportTaskStatusFilter : undefined;
      const response = await previewDiagnosticsService.dryRunRetryTerminalQueueDeclinedRequeueDryRunExportTasks(
        statusFilter,
        QUEUE_DECLINED_REQUEUE_DRY_RUN_EXPORT_TASK_DEFAULT_MAX_ITEMS
      );
      setQueueDeclinedRequeueDryRunExportTaskRetryDryRun(response || null);
      const retryableTaskIds = (response?.results || [])
        .filter((item) => (item?.outcome || '').toUpperCase() === 'RETRYABLE')
        .map((item) => String(item?.sourceTaskId || '').trim())
        .filter((taskId) => taskId.length > 0);
      setQueueDeclinedRequeueDryRunExportTaskRetryDryRunSelectedTaskIds(Array.from(new Set(retryableTaskIds)));
      const retryableCount = Number.isFinite(response?.retryable) ? response.retryable : 0;
      const skippedCount = Number.isFinite(response?.skipped) ? response.skipped : 0;
      const summary = `retryable=${retryableCount}, skipped=${skippedCount}`;
      if (retryableCount > 0) {
        toast.info(`Queue declined requeue dry-run async terminal dry-run: ${summary}`);
      } else {
        toast.info(response?.message || `No terminal queue declined requeue dry-run async export tasks matched dry-run filters (${summary})`);
      }
    } catch {
      toast.error('Failed to dry-run terminal retry for queue declined requeue dry-run async export tasks');
    } finally {
      setQueueDeclinedRequeueDryRunExportTaskDryRunningTerminal(false);
    }
  };

  const handleExportDryRunRetryTerminalQueueDeclinedRequeueDryRunExportTasks = async () => {
    try {
      setQueueDeclinedRequeueDryRunExportTaskDryRunExporting(true);
      const statusFilter: PreviewQueueDeclinedRequeueDryRunExportTaskTerminalStatusFilter | undefined = (
        queueDeclinedRequeueDryRunExportTaskStatusFilter === 'FAILED'
        || queueDeclinedRequeueDryRunExportTaskStatusFilter === 'CANCELLED'
        || queueDeclinedRequeueDryRunExportTaskStatusFilter === 'COMPLETED'
        || queueDeclinedRequeueDryRunExportTaskStatusFilter === 'TIMED_OUT'
        || queueDeclinedRequeueDryRunExportTaskStatusFilter === 'EXPIRED'
      ) ? queueDeclinedRequeueDryRunExportTaskStatusFilter : undefined;
      await previewDiagnosticsService.exportDryRunRetryTerminalQueueDeclinedRequeueDryRunExportTasks(
        statusFilter,
        QUEUE_DECLINED_REQUEUE_DRY_RUN_EXPORT_TASK_DEFAULT_MAX_ITEMS
      );
      toast.success('Queue declined requeue dry-run async terminal dry-run CSV exported');
    } catch {
      toast.error('Failed to export queue declined requeue dry-run async terminal dry-run CSV');
    } finally {
      setQueueDeclinedRequeueDryRunExportTaskDryRunExporting(false);
    }
  };

  const handleRetrySelectedQueueDeclinedRequeueDryRunExportTasks = async () => {
    if (queueDeclinedRequeueDryRunExportTaskRetryDryRunSelectedTaskIds.length === 0) {
      toast.info('No selected terminal queue declined requeue dry-run async export tasks to retry');
      return;
    }
    try {
      setQueueDeclinedRequeueDryRunExportTaskRetryingSelected(true);
      const response = await previewDiagnosticsService.retryTerminalQueueDeclinedRequeueDryRunExportTasksByTaskIds(
        queueDeclinedRequeueDryRunExportTaskRetryDryRunSelectedTaskIds
      );
      const retriedCount = Number.isFinite(response?.retried) ? response.retried : 0;
      const reusedCount = Number.isFinite(response?.reused) ? response.reused : 0;
      const skippedCount = Number.isFinite(response?.skipped) ? response.skipped : 0;
      const failedCount = Number.isFinite(response?.failed) ? response.failed : 0;
      const summary = `retried=${retriedCount}, reused=${reusedCount}, skipped=${skippedCount}, failed=${failedCount}`;
      if (retriedCount > 0) {
        toast.success(`Queue declined requeue dry-run async selected retry done: ${summary}`);
      } else {
        toast.info(response?.message || `No selected queue declined requeue dry-run async export tasks were retried (${summary})`);
      }
      setQueueDeclinedRequeueDryRunExportTaskRetryDryRun(null);
      setQueueDeclinedRequeueDryRunExportTaskRetryDryRunSelectedTaskIds([]);
      await loadQueueDeclinedRequeueDryRunExportTasks(true);
    } catch {
      toast.error('Failed to retry selected terminal queue declined requeue dry-run async export tasks');
    } finally {
      setQueueDeclinedRequeueDryRunExportTaskRetryingSelected(false);
    }
  };

  const handleClearQueueDeclined = async () => {
    try {
      setQueueDeclinedClearing(true);
      const result = await previewDiagnosticsService.clearQueueDeclined(
        QUEUE_DECLINED_EXPORT_LIMIT,
        queueDeclinedCategoryFilter,
        queueDeclinedForceRequiredFilter,
        queueDeclinedQueryFilter || undefined,
        queueDeclinedWindowHoursFilter
      );
      if (result.cleared > 0 && result.failed === 0) {
        toast.success(`Declined clear done: ${result.cleared}/${result.requested}`);
      } else if (result.cleared > 0) {
        toast.warning(`Declined clear partial: cleared=${result.cleared}, failed=${result.failed}`);
      } else if (result.failed > 0) {
        toast.error(`Declined clear failed: failed=${result.failed}`);
      } else {
        toast.info('No declined queue items matched current filter');
      }
      setQueueDeclinedDryRun(null);
      await loadFailures();
    } catch {
      toast.error('Failed to clear declined queue items');
    } finally {
      setQueueDeclinedClearing(false);
    }
  };

  const handleToggleDeadLetterSelection = (entryKey: string, checked: boolean) => {
    setSelectedDeadLetterEntryKeys((current) => {
      if (checked) {
        if (current.includes(entryKey)) {
          return current;
        }
        return [...current, entryKey];
      }
      return current.filter((id) => id !== entryKey);
    });
  };

  const handleToggleAllDeadLetterSelection = (checked: boolean) => {
    if (checked) {
      setSelectedDeadLetterEntryKeys(
        filteredDeadLetterItems.map((item) => item.entryKey || item.documentId)
      );
    } else {
      setSelectedDeadLetterEntryKeys([]);
    }
  };

  const handleReplayDeadLetter = async (entryKeys: string[]) => {
    if (entryKeys.length === 0) {
      return;
    }
    const actionKey = entryKeys.length === 1 ? entryKeys[0] : '__batch__';
    try {
      setDeadLetterActionId(actionKey);
      const result = await opsRecoveryService.replayBatch({
        domain: 'PREVIEW',
        entryKeys,
        force: true,
      });
      if (result.failed === 0 && result.skipped === 0) {
        toast.success(`Replay queued: ${result.queued}/${result.deduplicated}`);
      } else if (result.queued > 0) {
        toast.warning(`Replay partial: queued=${result.queued}, skipped=${result.skipped}, failed=${result.failed}`);
      } else {
        toast.error(`Replay failed: skipped=${result.skipped}, failed=${result.failed}`);
      }
      setSelectedDeadLetterEntryKeys([]);
      await loadFailures();
    } catch {
      toast.error('Failed to replay dead-letter items');
    } finally {
      setDeadLetterActionId(null);
    }
  };

  const handleClearDeadLetter = async (entryKeys: string[]) => {
    if (entryKeys.length === 0) {
      return;
    }
    const actionKey = entryKeys.length === 1 ? `clear:${entryKeys[0]}` : '__clear_batch__';
    try {
      setDeadLetterActionId(actionKey);
      const result = await opsRecoveryService.clearBatch({
        domain: 'PREVIEW',
        entryKeys,
      });
      const cleared = result.queued || 0;
      if (result.failed === 0 && cleared === result.deduplicated) {
        toast.success(`Dead-letter cleared: ${cleared}/${result.deduplicated}`);
      } else if (cleared > 0) {
        toast.warning(`Dead-letter partial clear: cleared=${cleared}, failed=${result.failed}`);
      } else {
        toast.error(`Dead-letter clear failed: failed=${result.failed}`);
      }
      setSelectedDeadLetterEntryKeys([]);
      await loadFailures();
    } catch {
      toast.error('Failed to clear dead-letter items');
    } finally {
      setDeadLetterActionId(null);
    }
  };

  const handleExportDeadLetterCsv = async () => {
    try {
      await previewDiagnosticsService.exportDeadLetterCsv(DEAD_LETTER_LIST_LIMIT);
      toast.success('Dead-letter CSV exported');
    } catch {
      toast.error('Failed to export dead-letter CSV');
    }
  };

  const handleExportRecoveryHistoryCsv = async () => {
    try {
      await opsRecoveryService.exportHistoryCsv(
        RECOVERY_HISTORY_EXPORT_LIMIT,
        windowDays,
        recoveryHistoryMode === 'ALL' ? undefined : recoveryHistoryMode,
        recoveryHistoryActor.trim() || undefined,
        recoveryHistoryEventType === 'ALL' ? undefined : recoveryHistoryEventType
      );
      toast.success('Ops recovery history CSV exported');
    } catch {
      toast.error('Failed to export ops recovery history CSV');
    }
  };

  const handleExportRecoveryHistorySummaryCsv = async () => {
    try {
      await opsRecoveryService.exportHistorySummaryCsv(
        windowDays,
        recoveryHistoryMode === 'ALL' ? undefined : recoveryHistoryMode,
        recoveryHistoryActor.trim() || undefined,
        recoveryHistoryEventType === 'ALL' ? undefined : recoveryHistoryEventType
      );
      toast.success('Ops recovery history summary CSV exported');
    } catch {
      toast.error('Failed to export ops recovery history summary CSV');
    }
  };

  const handleExportRecoveryHistoryTrendCsv = async () => {
    try {
      await opsRecoveryService.exportHistorySummaryTrendCsv(
        windowDays,
        recoveryHistoryMode === 'ALL' ? undefined : recoveryHistoryMode,
        recoveryHistoryActor.trim() || undefined,
        recoveryHistoryEventType === 'ALL' ? undefined : recoveryHistoryEventType
      );
      toast.success('Ops recovery history trend CSV exported');
    } catch {
      toast.error('Failed to export ops recovery history trend CSV');
    }
  };

  const handleExportRecoveryHistoryCompareCsv = async () => {
    try {
      await opsRecoveryService.exportHistorySummaryCompareCsv(
        windowDays,
        recoveryHistoryMode === 'ALL' ? undefined : recoveryHistoryMode,
        recoveryHistoryActor.trim() || undefined,
        recoveryHistoryEventType === 'ALL' ? undefined : recoveryHistoryEventType
      );
      toast.success('Ops recovery history compare CSV exported');
    } catch {
      toast.error('Failed to export ops recovery history compare CSV');
    }
  };

  const handleExportRecoveryHistoryCompareActorsCsv = async () => {
    try {
      await opsRecoveryService.exportHistorySummaryCompareActorsCsv(
        windowDays,
        recoveryHistoryMode === 'ALL' ? undefined : recoveryHistoryMode,
        recoveryHistoryActor.trim() || undefined,
        recoveryHistoryEventType === 'ALL' ? undefined : recoveryHistoryEventType,
        recoveryHistoryCompareActorLimit,
        recoveryHistoryCompareActorSort
      );
      toast.success('Ops recovery history actor compare CSV exported');
    } catch {
      toast.error('Failed to export ops recovery history actor compare CSV');
    }
  };

  const handleExportRecoveryHistoryCompareBreakdownCsv = async () => {
    try {
      await opsRecoveryService.exportHistorySummaryCompareBreakdownCsv(
        windowDays,
        recoveryHistoryMode === 'ALL' ? undefined : recoveryHistoryMode,
        recoveryHistoryActor.trim() || undefined,
        recoveryHistoryEventType === 'ALL' ? undefined : recoveryHistoryEventType,
        recoveryHistoryCompareBreakdownLimit,
        recoveryHistoryCompareBreakdownSort
      );
      toast.success('Ops recovery history compare breakdown CSV exported');
    } catch {
      toast.error('Failed to export ops recovery history compare breakdown CSV');
    }
  };

  const handleStartRecoveryHistoryExportAsync = async () => {
    try {
      setRecoveryHistoryExportAsyncStarting(true);
      const response = await opsRecoveryService.startHistoryExportAsync({
        exportType: recoveryHistoryExportAsyncType,
        limit: RECOVERY_HISTORY_EXPORT_LIMIT,
        days: windowDays,
        mode: recoveryHistoryMode === 'ALL' ? undefined : recoveryHistoryMode,
        actor: recoveryHistoryActor.trim() || undefined,
        eventType: recoveryHistoryEventType === 'ALL' ? undefined : recoveryHistoryEventType,
        compareBreakdownLimit: recoveryHistoryCompareBreakdownLimit,
        compareBreakdownSort: recoveryHistoryCompareBreakdownSort,
        compareActorLimit: recoveryHistoryCompareActorLimit,
        compareActorSort: recoveryHistoryCompareActorSort,
      });
      const taskId = response.taskId?.trim();
      if (!taskId) {
        throw new Error('missing_task_id');
      }
      toast.success(`Ops recovery async export task started: ${taskId}`);
      await loadRecoveryHistoryExportAsyncTasks(true);
    } catch {
      toast.error('Failed to start ops recovery async export task');
    } finally {
      setRecoveryHistoryExportAsyncStarting(false);
    }
  };

  const handleCancelRecoveryHistoryExportAsyncTask = async (taskId: string) => {
    try {
      setRecoveryHistoryExportAsyncActionTaskId(taskId);
      setRecoveryHistoryExportAsyncActionType('cancel');
      await opsRecoveryService.cancelHistoryExportAsyncTask(taskId);
      toast.success(`Ops recovery async export task cancelled: ${taskId}`);
      await loadRecoveryHistoryExportAsyncTasks(true);
    } catch {
      toast.error(`Failed to cancel ops recovery async export task: ${taskId}`);
    } finally {
      setRecoveryHistoryExportAsyncActionTaskId(null);
      setRecoveryHistoryExportAsyncActionType(null);
    }
  };

  const handleRetryRecoveryHistoryExportAsyncTask = async (taskId: string) => {
    try {
      setRecoveryHistoryExportAsyncActionTaskId(taskId);
      setRecoveryHistoryExportAsyncActionType('retry');
      const retriedTask = await opsRecoveryService.retryHistoryExportAsyncTask(taskId);
      const newTaskId = retriedTask.taskId?.trim();
      if (!newTaskId) {
        throw new Error('missing_task_id');
      }
      toast.success(`Ops recovery async export task retried: ${taskId} -> ${newTaskId}`);
      await loadRecoveryHistoryExportAsyncTasks(true);
      await handleRefreshRecoveryHistoryExportAsyncSummary(true);
    } catch {
      toast.error(`Failed to retry ops recovery async export task: ${taskId}`);
    } finally {
      setRecoveryHistoryExportAsyncActionTaskId(null);
      setRecoveryHistoryExportAsyncActionType(null);
    }
  };

  const handleDownloadRecoveryHistoryExportAsyncTask = async (task: RecoveryHistoryExportAsyncTaskStatus) => {
    try {
      setRecoveryHistoryExportAsyncActionTaskId(task.taskId);
      setRecoveryHistoryExportAsyncActionType('download');
      const latest = await opsRecoveryService.getHistoryExportAsyncTask(task.taskId).catch(() => task);
      const blob = await opsRecoveryService.downloadHistoryExportAsyncTask(task.taskId);
      const normalizedType = (latest.exportType || task.exportType || 'HISTORY').toUpperCase();
      const fallbackPrefix = (() => {
        switch (normalizedType) {
          case 'HISTORY_SUMMARY':
            return 'ops_recovery_history_summary';
          case 'HISTORY_TREND':
            return 'ops_recovery_history_trend';
          case 'HISTORY_COMPARE':
            return 'ops_recovery_history_compare';
          case 'HISTORY_COMPARE_BREAKDOWN':
            return 'ops_recovery_history_compare_breakdown';
          case 'HISTORY_COMPARE_ACTORS':
            return 'ops_recovery_history_compare_actors';
          default:
            return 'ops_recovery_history';
        }
      })();
      const fallbackFilename = `${fallbackPrefix}_${format(new Date(), 'yyyyMMdd-HHmmss')}.csv`;
      const filename = (latest.filename || task.filename || '').trim() || fallbackFilename;
      const url = window.URL.createObjectURL(blob);
      const anchor = document.createElement('a');
      anchor.href = url;
      anchor.download = filename;
      document.body.appendChild(anchor);
      anchor.click();
      anchor.remove();
      window.URL.revokeObjectURL(url);
      toast.success(`Ops recovery async export downloaded: ${filename}`);
    } catch {
      toast.error(`Failed to download ops recovery async export task: ${task.taskId}`);
    } finally {
      setRecoveryHistoryExportAsyncActionTaskId(null);
      setRecoveryHistoryExportAsyncActionType(null);
    }
  };

  const handleRefreshRecoveryHistoryExportAsyncSummary = async (silent = false) => {
    try {
      if (!silent) {
        setRecoveryHistoryExportAsyncSummaryLoading(true);
      }
      const response = await opsRecoveryService.getHistoryExportAsyncTaskSummaryFiltered(
        recoveryHistoryExportAsyncFilterType === 'ALL' ? undefined : recoveryHistoryExportAsyncFilterType,
        recoveryHistoryExportAsyncFilterStatus === 'ALL' ? undefined : recoveryHistoryExportAsyncFilterStatus
      );
      setRecoveryHistoryExportAsyncSummary(response);
    } catch {
      if (!silent) {
        toast.error('Failed to refresh ops recovery async export summary');
      }
    } finally {
      if (!silent) {
        setRecoveryHistoryExportAsyncSummaryLoading(false);
      }
    }
  };

  const handleCleanupRecoveryHistoryExportAsyncTasks = async () => {
    try {
      setRecoveryHistoryExportAsyncCleaning(true);
      const exportTypeFilter = recoveryHistoryExportAsyncFilterType === 'ALL'
        ? undefined
        : recoveryHistoryExportAsyncFilterType;
      const cleanupStatusFilter = (
        recoveryHistoryExportAsyncFilterStatus === 'COMPLETED'
        || recoveryHistoryExportAsyncFilterStatus === 'CANCELLED'
        || recoveryHistoryExportAsyncFilterStatus === 'FAILED'
      ) ? recoveryHistoryExportAsyncFilterStatus : undefined;
      const response = await opsRecoveryService.cleanupHistoryExportAsyncTasks(
        exportTypeFilter,
        cleanupStatusFilter
      );
      const deletedCount = Number.isFinite(response?.deletedCount) ? response.deletedCount : 0;
      if (deletedCount > 0) {
        toast.success(response?.message || `Cleaned ${deletedCount} ops recovery async export tasks`);
      } else {
        toast.info(response?.message || 'No ops recovery async export tasks matched cleanup filter');
      }
      await loadRecoveryHistoryExportAsyncTasks(true);
      await handleRefreshRecoveryHistoryExportAsyncSummary(true);
    } catch {
      toast.error('Failed to cleanup ops recovery async export tasks');
    } finally {
      setRecoveryHistoryExportAsyncCleaning(false);
    }
  };

  const handleCancelActiveRecoveryHistoryExportAsyncTasks = async () => {
    try {
      setRecoveryHistoryExportAsyncCancellingActive(true);
      const exportTypeFilter = recoveryHistoryExportAsyncFilterType === 'ALL'
        ? undefined
        : recoveryHistoryExportAsyncFilterType;
      const statusFilter: RecoveryHistoryExportAsyncActiveStatusFilter | undefined = (
        recoveryHistoryExportAsyncFilterStatus === 'QUEUED'
        || recoveryHistoryExportAsyncFilterStatus === 'RUNNING'
      ) ? recoveryHistoryExportAsyncFilterStatus : undefined;
      const response = await opsRecoveryService.cancelActiveHistoryExportAsyncTasks(
        exportTypeFilter,
        statusFilter
      );
      const cancelledCount = Number.isFinite(response?.cancelledCount) ? response.cancelledCount : 0;
      if (cancelledCount > 0) {
        toast.success(response?.message || `Cancelled ${cancelledCount} active async export task(s)`);
      } else {
        toast.info(response?.message || 'No active ops recovery async export tasks matched cancel-active filters');
      }
      await loadRecoveryHistoryExportAsyncTasks(true);
      await handleRefreshRecoveryHistoryExportAsyncSummary(true);
    } catch {
      toast.error('Failed to cancel active ops recovery async export tasks');
    } finally {
      setRecoveryHistoryExportAsyncCancellingActive(false);
    }
  };

  const handleRefreshRenditionSummary = useCallback(async () => {
    try {
      setRenditionSummaryRefreshing(true);
      const [summaryData, resourcesData] = await Promise.all([
        previewDiagnosticsService.getRenditionSummary(windowDays, BACKEND_SUMMARY_SAMPLE_LIMIT),
        previewDiagnosticsService.getRenditionResources(windowDays, BACKEND_SUMMARY_SAMPLE_LIMIT).catch(() => []),
      ]);
      setRenditionSummary(summaryData || null);
      setRenditionResources(Array.isArray(resourcesData) ? resourcesData : []);
    } catch {
      toast.error('Failed to refresh rendition resource summary');
    } finally {
      setRenditionSummaryRefreshing(false);
    }
  }, [windowDays]);

  const handleExportRenditionResourcesCsv = async () => {
    try {
      await previewDiagnosticsService.exportRenditionResourcesCsv(windowDays, BACKEND_SUMMARY_SAMPLE_LIMIT);
      toast.success('Rendition resources CSV exported');
    } catch {
      toast.error('Failed to export rendition resources CSV');
    }
  };

  const handleStartRenditionExportTask = async () => {
    try {
      setRenditionExportTaskStarting(true);
      const task = await previewDiagnosticsService.startRenditionResourcesExportTask(
        windowDays,
        BACKEND_SUMMARY_SAMPLE_LIMIT
      );
      const taskId = task.taskId?.trim();
      if (!taskId) {
        throw new Error('missing_task_id');
      }
      toast.success(`Rendition async export task started: ${taskId}`);
      await loadRenditionExportTasks(true);
    } catch {
      toast.error('Failed to start rendition async export task');
    } finally {
      setRenditionExportTaskStarting(false);
    }
  };

  const handleCleanupRenditionExportTasks = async () => {
    try {
      setRenditionExportTaskCleaning(true);
      const statusFilter = renditionExportTaskStatusFilter === 'ALL'
        ? undefined
        : renditionExportTaskStatusFilter;
      const response = await previewDiagnosticsService.cleanupRenditionResourcesExportTasks(statusFilter);
      const deletedCount = Number.isFinite(response?.deletedCount) ? response.deletedCount : 0;
      if (deletedCount > 0) {
        toast.success(response?.message || `Rendition async export cleanup removed ${deletedCount} task(s)`);
      } else {
        toast.info(response?.message || 'No rendition async export tasks matched cleanup filter');
      }
      await loadRenditionExportTasks(true);
    } catch {
      toast.error('Failed to cleanup rendition async export tasks');
    } finally {
      setRenditionExportTaskCleaning(false);
    }
  };

  const handleCancelActiveRenditionExportTasks = async () => {
    try {
      setRenditionExportTaskCancellingActive(true);
      const statusFilter: PreviewRenditionResourcesExportTaskActiveStatusFilter | undefined = (
        renditionExportTaskStatusFilter === 'QUEUED'
        || renditionExportTaskStatusFilter === 'RUNNING'
      ) ? renditionExportTaskStatusFilter : undefined;
      const response = await previewDiagnosticsService.cancelActiveRenditionResourcesExportTasks(statusFilter);
      const cancelledCount = Number.isFinite(response?.cancelledCount) ? response.cancelledCount : 0;
      if (cancelledCount > 0) {
        toast.success(response?.message || `Cancelled ${cancelledCount} active rendition async export task(s)`);
      } else {
        toast.info(response?.message || 'No active rendition async export tasks matched cancel-active filters');
      }
      await loadRenditionExportTasks(true);
    } catch {
      toast.error('Failed to cancel active rendition async export tasks');
    } finally {
      setRenditionExportTaskCancellingActive(false);
    }
  };

  const handleCancelRenditionExportTask = async (taskId: string) => {
    try {
      setRenditionExportTaskActionId(taskId);
      setRenditionExportTaskActionType('cancel');
      await previewDiagnosticsService.cancelRenditionResourcesExportTask(taskId);
      toast.success(`Rendition async export task cancelled: ${taskId}`);
      await loadRenditionExportTasks(true);
    } catch {
      toast.error(`Failed to cancel rendition async export task: ${taskId}`);
    } finally {
      setRenditionExportTaskActionId(null);
      setRenditionExportTaskActionType(null);
    }
  };

  const handleRetryRenditionExportTask = async (taskId: string) => {
    try {
      setRenditionExportTaskActionId(taskId);
      setRenditionExportTaskActionType('retry');
      const retriedTask = await previewDiagnosticsService.retryRenditionResourcesExportTask(taskId);
      const newTaskId = retriedTask.taskId?.trim();
      if (!newTaskId) {
        throw new Error('missing_task_id');
      }
      toast.success(`Rendition async export task retried: ${taskId} -> ${newTaskId}`);
      await loadRenditionExportTasks(true);
    } catch {
      toast.error(`Failed to retry rendition async export task: ${taskId}`);
    } finally {
      setRenditionExportTaskActionId(null);
      setRenditionExportTaskActionType(null);
    }
  };

  const handleDownloadRenditionExportTask = async (task: PreviewRenditionResourcesExportTask) => {
    try {
      setRenditionExportTaskActionId(task.taskId);
      setRenditionExportTaskActionType('download');
      const blob = await previewDiagnosticsService.downloadRenditionResourcesExportTask(task.taskId);
      const fallback = `preview_rendition_resources_async_${format(new Date(), 'yyyyMMdd-HHmmss')}.csv`;
      const filename = (task.filename || '').trim() || fallback;
      const url = window.URL.createObjectURL(blob);
      const anchor = document.createElement('a');
      anchor.href = url;
      anchor.download = filename;
      document.body.appendChild(anchor);
      anchor.click();
      anchor.remove();
      window.URL.revokeObjectURL(url);
      toast.success(`Rendition async export downloaded: ${filename}`);
    } catch {
      toast.error(`Failed to download rendition async export task: ${task.taskId}`);
    } finally {
      setRenditionExportTaskActionId(null);
      setRenditionExportTaskActionType(null);
    }
  };

  const handleRunGlobalDryRun = async () => {
    if (dryRunMode === 'QUEUE_BY_REASON' && !dryRunReason.trim()) {
      toast.error('Reason is required in "Queue by Reason" mode');
      return;
    }
    try {
      setDryRunLoading(true);
      const result = await opsRecoveryService.dryRun({
        domain: 'PREVIEW',
        mode: dryRunMode,
        reason: normalizedDryRunReason,
        category: dryRunCategory,
        retryable: normalizedDryRunRetryable,
        maxDocuments: normalizedDryRunMaxDocuments,
        days: windowDays,
        force: normalizedDryRunForce,
      });
      setDryRunResult(result);
      setDryRunRequestSignature(currentDryRunRequestSignature);
      const primaryLabel = result.mode === 'CLEAR_BY_FILTER'
        ? 'cleared'
        : result.mode === 'REPLAY_BY_FILTER'
          ? 'replayQueued'
          : 'queued';
      toast.info(
        `Dry-run ${result.mode}: matched=${result.matched}, ${primaryLabel}=${result.estimatedQueued}, skipped=${result.estimatedSkipped}, failed=${result.estimatedFailed}`
      );
    } catch {
      toast.error('Failed to run ops recovery dry-run');
    } finally {
      setDryRunLoading(false);
    }
  };

  const handleExecuteDryRunPlan = async () => {
    if (!dryRunResult) {
      toast.info('Run dry-run first, then execute recovery');
      return;
    }
    if (dryRunPlanStale) {
      toast.warning('Dry-run plan is stale. Run Dry-run again before Execute Recovery.');
      return;
    }
    if (dryRunMode === 'QUEUE_BY_REASON' && !dryRunReason.trim()) {
      toast.error('Reason is required in "Queue by Reason" mode');
      return;
    }
    try {
      setDryRunExecuting(true);
      const batch = dryRunMode === 'QUEUE_BY_REASON'
        ? await opsRecoveryService.queueByReason({
          domain: 'PREVIEW',
          reason: dryRunReason.trim(),
          category: dryRunCategory,
          retryable: normalizedDryRunRetryable,
          maxDocuments: normalizedDryRunMaxDocuments,
          days: windowDays,
          force: normalizedDryRunForce,
        })
        : dryRunMode === 'QUEUE_BY_WINDOW'
          ? await opsRecoveryService.queueByWindow({
            domain: 'PREVIEW',
            reason: normalizedDryRunReason,
            category: dryRunCategory,
            retryable: normalizedDryRunRetryable,
            maxDocuments: normalizedDryRunMaxDocuments,
            days: windowDays,
            force: normalizedDryRunForce,
          })
          : dryRunMode === 'CLEAR_BY_FILTER'
            ? await opsRecoveryService.clearByFilter({
              domain: 'PREVIEW',
              reason: normalizedDryRunReason,
              category: dryRunCategory,
              retryable: normalizedDryRunRetryable,
              maxDocuments: normalizedDryRunMaxDocuments,
              days: windowDays,
            })
            : await opsRecoveryService.replayByFilter({
              domain: 'PREVIEW',
              reason: normalizedDryRunReason,
              category: dryRunCategory,
              retryable: normalizedDryRunRetryable,
              maxDocuments: normalizedDryRunMaxDocuments,
              days: windowDays,
              force: normalizedDryRunForce,
            });

      const primaryLabel = dryRunMode === 'CLEAR_BY_FILTER' ? 'cleared' : 'queued';
      if (batch.failed === 0 && batch.skipped === 0) {
        toast.success(`Recovery executed: ${primaryLabel}=${batch.queued}, skipped=${batch.skipped}, failed=${batch.failed}`);
      } else if (batch.queued > 0) {
        toast.warning(`Recovery partial: ${primaryLabel}=${batch.queued}, skipped=${batch.skipped}, failed=${batch.failed}`);
      } else {
        toast.error(`Recovery execution failed: ${primaryLabel}=${batch.queued}, skipped=${batch.skipped}, failed=${batch.failed}`);
      }
      await loadFailures();
    } catch {
      toast.error('Failed to execute recovery');
    } finally {
      setDryRunExecuting(false);
    }
  };

  return (
    <Box p={3}>
      <Stack direction="row" alignItems="flex-start" justifyContent="space-between" flexWrap="wrap" gap={2} mb={2}>
        <Box>
          <Typography variant="h4">Preview Diagnostics</Typography>
          <Typography variant="body2" color="text.secondary">
            Recent preview failures (FAILED/UNSUPPORTED) from backend diagnostics.
          </Typography>
        </Box>
        <Stack direction="row" alignItems="center" gap={1} flexWrap="wrap">
          <FormControl size="small">
            <Select
              aria-label="Preview diagnostics limit"
              value={limit}
              onChange={(event) => setLimit(event.target.value as (typeof LIMIT_OPTIONS)[number])}
            >
              {LIMIT_OPTIONS.map((option) => (
                <MenuItem key={option} value={option}>
                  Limit {option}
                </MenuItem>
              ))}
            </Select>
          </FormControl>
          <FormControl size="small">
            <Select
              aria-label="Preview diagnostics days"
              value={windowDays}
              onChange={(event) => setWindowDays(event.target.value as WindowDays)}
            >
              {WINDOW_DAY_OPTIONS.map((option) => (
                <MenuItem key={option.value} value={option.value}>
                  {option.label}
                </MenuItem>
              ))}
            </Select>
          </FormControl>
          <Button
            variant="outlined"
            startIcon={<RefreshIcon />}
            onClick={() => void loadFailures()}
            disabled={loading}
          >
            Refresh
          </Button>
        </Stack>
      </Stack>

      <Stack direction="row" alignItems="center" gap={1} flexWrap="wrap" mb={2}>
        <TextField
          size="small"
          placeholder="Filter by name, path, mime type..."
          value={filterText}
          onChange={(event) => setFilterText(event.target.value)}
          sx={{ minWidth: 320, flex: '1 1 320px' }}
        />
        <Chip label={`Total ${summary.totalFailed}`} />
        <Chip label={`Retryable ${summary.retryableFailed}`} color="warning" variant="outlined" />
        <Chip label={`Permanent ${summary.permanentFailed}`} color="error" variant="outlined" />
        <Chip label={`Unsupported ${summary.unsupportedFailed}`} variant="outlined" />
      </Stack>

      {backendSummary && (
        <Paper sx={{ p: 2, mb: 2 }}>
          <Stack direction="row" alignItems="center" justifyContent="space-between" gap={1} flexWrap="wrap" mb={1}>
            <Typography variant="h6">Backend Failure Summary</Typography>
            <Stack direction="row" gap={1} flexWrap="wrap">
              <Chip
                label={confidenceLabel}
                color={confidenceColor}
                variant={backendSummary.confidenceLevel === 'LOW' ? 'filled' : 'outlined'}
              />
              <Chip label={`Sampled ${backendSummary.sampledFailures}/${backendSummary.totalFailures}`} />
            </Stack>
          </Stack>

          {backendSummary.sampleTruncated && (
            <Alert severity="warning" sx={{ mb: 2 }}>
              Summary is sampled ({backendSummary.sampledFailures}/{backendSummary.totalFailures}) and may miss long-tail
              reasons. Increase sample limit in backend endpoint if you need full precision.
            </Alert>
          )}

          <Stack direction="row" gap={1} flexWrap="wrap" mb={1}>
            {backendSummary.statusCounts.map((entry) => (
              <Chip
                key={`status-${entry.status}`}
                size="small"
                label={`Status ${entry.status}: ${entry.count}`}
                variant="outlined"
              />
            ))}
          </Stack>

          <Stack direction="row" gap={1} flexWrap="wrap" mb={2}>
            {backendSummary.categoryCounts.map((entry) => (
              <Chip
                key={`category-${entry.category}`}
                size="small"
                color={entry.retryable ? 'warning' : 'default'}
                label={`${entry.category}: ${entry.count}${entry.retryable ? ' (retryable)' : ''}`}
                variant="outlined"
              />
            ))}
          </Stack>

          {backendSummary.topReasons.length === 0 ? (
            <Alert severity="info">No failure reasons in current summary sample.</Alert>
          ) : (
            <TableContainer>
              <Table size="small" aria-label="Preview diagnostics top reasons">
                <TableHead>
                  <TableRow>
                    <TableCell>Top Failure Reason</TableCell>
                    <TableCell>Category</TableCell>
                    <TableCell>Retryable</TableCell>
                    <TableCell align="right">Current List</TableCell>
                    <TableCell align="right">Actions</TableCell>
                    <TableCell align="right">Count</TableCell>
                  </TableRow>
                </TableHead>
                <TableBody>
                  {backendSummary.topReasons.map((entry) => {
                    const reasonLabel =
                      entry.reason === 'UNSPECIFIED'
                        ? 'Unspecified reason'
                        : formatPreviewFailureReasonLabel(entry.reason);
                    const matchedInCurrentList = matchReasonEntriesInCurrentList(
                      entry.reason,
                      entry.category,
                      entry.retryable
                    ).length;
                    const retryActionKey = getReasonActionKey(entry.reason, entry.category, false);
                    const forceActionKey = getReasonActionKey(entry.reason, entry.category, true);
                    const dryRunActionKey = getReasonActionKey(entry.reason, entry.category, false);
                    const clearActionKey = getReasonActionKey(entry.reason, entry.category, false);
                    const replayDeadLetterActionKey = getReasonActionKey(entry.reason, entry.category, true);
                    const resetLedgerActionKey = getReasonLedgerResetActionKey(entry.reason, entry.category);
                    const rowBusy = reasonBatchActionKey === retryActionKey
                      || reasonBatchActionKey === forceActionKey
                      || reasonDryRunActionKey === dryRunActionKey
                      || reasonClearActionKey === clearActionKey
                      || reasonReplayDeadLetterActionKey === replayDeadLetterActionKey
                      || reasonLedgerResetActionKey === resetLedgerActionKey;
                    return (
                      <TableRow key={`${entry.reason}-${entry.category}-${entry.retryable}`}>
                        <TableCell sx={{ maxWidth: 520 }}>
                          <Tooltip title={reasonLabel} placement="top-start" arrow>
                            <Typography variant="body2" noWrap>
                              {reasonLabel}
                            </Typography>
                          </Tooltip>
                        </TableCell>
                        <TableCell>
                          <Chip size="small" label={entry.category || 'UNKNOWN'} variant="outlined" />
                        </TableCell>
                        <TableCell>
                          <Chip
                            size="small"
                            label={entry.retryable ? 'Yes' : 'No'}
                            color={entry.retryable ? 'warning' : 'default'}
                            variant={entry.retryable ? 'filled' : 'outlined'}
                          />
                        </TableCell>
                        <TableCell align="right">{matchedInCurrentList}</TableCell>
                        <TableCell align="right">
                          <Stack direction="row" justifyContent="flex-end" gap={0.5} flexWrap="wrap">
                            <Button
                              size="small"
                              variant="text"
                              disabled={Boolean(reasonBatchActionKey) || Boolean(reasonDryRunActionKey) || Boolean(reasonClearActionKey) || Boolean(reasonReplayDeadLetterActionKey) || Boolean(reasonLedgerResetActionKey)}
                              onClick={() => void handleDryRunByReason(entry.reason, entry.category, entry.retryable)}
                              aria-label={`Dry run reason group ${reasonLabel}`}
                            >
                              {reasonDryRunActionKey === dryRunActionKey ? 'Dry-run...' : 'Dry run'}
                            </Button>
                            <Button
                              size="small"
                              variant="outlined"
                              disabled={!entry.retryable || Boolean(reasonBatchActionKey) || Boolean(reasonDryRunActionKey) || Boolean(reasonClearActionKey) || Boolean(reasonReplayDeadLetterActionKey) || Boolean(reasonLedgerResetActionKey)}
                              onClick={() => void handleQueuePreviewByReason(entry.reason, entry.category, entry.retryable, false)}
                              aria-label={`Retry reason group ${reasonLabel}`}
                            >
                              {rowBusy ? 'Running...' : 'Retry'}
                            </Button>
                            <Button
                              size="small"
                              variant="outlined"
                              disabled={!entry.retryable || Boolean(reasonBatchActionKey) || Boolean(reasonDryRunActionKey) || Boolean(reasonClearActionKey) || Boolean(reasonReplayDeadLetterActionKey) || Boolean(reasonLedgerResetActionKey)}
                              onClick={() => void handleQueuePreviewByReason(entry.reason, entry.category, entry.retryable, true)}
                              aria-label={`Force rebuild reason group ${reasonLabel}`}
                            >
                              Force
                            </Button>
                            <Button
                              size="small"
                              variant="outlined"
                              startIcon={<RequeueIcon />}
                              disabled={!entry.retryable || Boolean(reasonBatchActionKey) || Boolean(reasonDryRunActionKey) || Boolean(reasonClearActionKey) || Boolean(reasonReplayDeadLetterActionKey) || Boolean(reasonLedgerResetActionKey)}
                              onClick={() => void handleReplayDeadLetterByReason(entry.reason, entry.category, entry.retryable)}
                              aria-label={`Replay dead-letter reason group ${reasonLabel}`}
                            >
                              {reasonReplayDeadLetterActionKey === replayDeadLetterActionKey ? 'Replaying...' : 'Replay DL'}
                            </Button>
                            <Button
                              size="small"
                              variant="outlined"
                              startIcon={<ClearIcon />}
                              disabled={Boolean(reasonBatchActionKey) || Boolean(reasonDryRunActionKey) || Boolean(reasonClearActionKey) || Boolean(reasonReplayDeadLetterActionKey) || Boolean(reasonLedgerResetActionKey)}
                              onClick={() => void handleClearDeadLetterByReason(entry.reason, entry.category, entry.retryable)}
                              aria-label={`Clear dead-letter reason group ${reasonLabel}`}
                            >
                              {reasonClearActionKey === clearActionKey ? 'Clearing...' : 'Clear DL'}
                            </Button>
                            <Button
                              size="small"
                              variant="outlined"
                              startIcon={<RollbackIcon />}
                              disabled={Boolean(reasonBatchActionKey) || Boolean(reasonDryRunActionKey) || Boolean(reasonClearActionKey) || Boolean(reasonReplayDeadLetterActionKey) || Boolean(reasonLedgerResetActionKey)}
                              onClick={() => void handleResetFailureLedgerByReason(entry.reason, entry.category, entry.retryable)}
                              aria-label={`Reset failure ledger reason group ${reasonLabel}`}
                            >
                              {reasonLedgerResetActionKey === resetLedgerActionKey ? 'Resetting...' : 'Reset Ledger'}
                            </Button>
                          </Stack>
                        </TableCell>
                        <TableCell align="right">{entry.count}</TableCell>
                      </TableRow>
                    );
                  })}
                </TableBody>
              </Table>
            </TableContainer>
          )}
        </Paper>
      )}

      {queueDiagnosticsSummary && (
        <Paper sx={{ p: 2, mb: 2 }}>
          <Stack direction="row" alignItems="center" justifyContent="space-between" gap={1} flexWrap="wrap" mb={1}>
            <Typography variant="h6">Preview Queue Health</Typography>
            <Stack direction="row" gap={1} flexWrap="wrap">
              <Chip size="small" label={`Backend ${queueDiagnosticsSummary.backend || 'UNKNOWN'}`} variant="outlined" />
              <Chip
                size="small"
                label={queueDiagnosticsSummary.queueEnabled ? 'Queue enabled' : 'Queue disabled'}
                color={queueDiagnosticsSummary.queueEnabled ? 'success' : 'default'}
                variant={queueDiagnosticsSummary.queueEnabled ? 'outlined' : 'filled'}
              />
              <Chip size="small" label={`Scheduled ${queueDiagnosticsSummary.scheduledCount}`} variant="outlined" />
              <Chip size="small" label={`Running ${queueDiagnosticsSummary.runningCount}`} variant="outlined" />
              <Chip
                size="small"
                label={`Sample ${queueDiagnosticsSummary.filteredSampledItems ?? queueDiagnosticsSummary.items.length}/${queueDiagnosticsSummary.totalSampledItems ?? queueDiagnosticsSummary.items.length}`}
                variant="outlined"
              />
              <Chip
                size="small"
                label={`Cancel requested ${queueDiagnosticsSummary.cancellationRequestedCount}`}
                color={queueDiagnosticsSummary.cancellationRequestedCount > 0 ? 'warning' : 'default'}
                variant="outlined"
              />
              {queueDiagnosticsSummary.sampleTruncated && (
                <Chip size="small" label="Sample truncated" color="warning" variant="outlined" />
              )}
              {!queueDiagnosticsSummary.runningCountAccurate && (
                <Chip size="small" label="Running estimated" color="warning" variant="outlined" />
              )}
              <Button
                size="small"
                variant="outlined"
                startIcon={<RefreshIcon />}
                onClick={() => void loadFailures()}
              >
                Refresh Queue
              </Button>
              <Button
                size="small"
                variant="outlined"
                color="warning"
                startIcon={<ClearIcon />}
                disabled={queueDiagnosticsCancelling || (queueDiagnosticsSummary.filteredSampledItems ?? 0) === 0}
                onClick={() => void handleCancelQueueDiagnosticsActive()}
              >
                {queueDiagnosticsCancelling ? 'Cancelling...' : 'Cancel Filtered'}
              </Button>
              <Button
                size="small"
                variant="outlined"
                startIcon={<DownloadIcon />}
                onClick={() => void handleExportQueueDiagnosticsCsv()}
              >
                Export Queue CSV
              </Button>
            </Stack>
          </Stack>
          <Stack direction={{ xs: 'column', md: 'row' }} gap={1} mb={1.5} alignItems={{ xs: 'stretch', md: 'center' }}>
            <FormControl size="small" sx={{ minWidth: 160 }}>
              <Select
                value={queueDiagnosticsStateFilter}
                onChange={(event) => setQueueDiagnosticsStateFilter(event.target.value as PreviewQueueDiagnosticsStateFilter)}
                inputProps={{ 'aria-label': 'Queue state filter' }}
              >
                {QUEUE_DIAGNOSTICS_STATE_FILTER_OPTIONS.map((option) => (
                  <MenuItem key={`queue-state-filter-${option.value}`} value={option.value}>
                    {option.label}
                  </MenuItem>
                ))}
              </Select>
            </FormControl>
            <TextField
              size="small"
              label="Queue query"
              placeholder="name/path/mime/documentId"
              value={queueDiagnosticsQueryInput}
              onChange={(event) => setQueueDiagnosticsQueryInput(event.target.value)}
              onKeyDown={(event) => {
                if (event.key === 'Enter') {
                  event.preventDefault();
                  handleApplyQueueDiagnosticsFilters();
                }
              }}
              sx={{ minWidth: 260 }}
            />
            <Button size="small" variant="outlined" startIcon={<SearchIcon />} onClick={handleApplyQueueDiagnosticsFilters}>
              Apply filters
            </Button>
            <Button
              size="small"
              variant="text"
              onClick={handleClearQueueDiagnosticsFilters}
              disabled={queueDiagnosticsStateFilter === 'ALL' && !queueDiagnosticsQueryInput && !queueDiagnosticsQueryFilter}
            >
              Clear
            </Button>
          </Stack>

          {queueDiagnosticsSummary.items.length === 0 ? (
            <Alert severity="info">No queued preview tasks at the moment.</Alert>
          ) : (
            <TableContainer>
              <Table size="small" aria-label="Preview queue health">
                <TableHead>
                  <TableRow>
                    <TableCell>Document</TableCell>
                    <TableCell align="right">Attempts</TableCell>
                    <TableCell>Next Attempt At</TableCell>
                    <TableCell>State</TableCell>
                    <TableCell>Governance Key</TableCell>
                  </TableRow>
                </TableHead>
                <TableBody>
                  {queueDiagnosticsSummary.items.map((item) => (
                    <TableRow key={`${item.documentId}-${item.governanceKey || 'none'}`}>
                      <TableCell>
                        <Stack spacing={0.25}>
                          <Typography variant="body2" fontWeight={600}>
                            {item.name || item.documentId}
                          </Typography>
                          {item.path && (
                            <Typography variant="caption" color="text.secondary">
                              {item.path}
                            </Typography>
                          )}
                          <Typography variant="caption" color="text.secondary">
                            {item.documentId}
                          </Typography>
                          <Stack direction="row" gap={0.5} flexWrap="wrap">
                            {item.mimeType && <Chip size="small" variant="outlined" label={item.mimeType} />}
                            {item.previewStatus && (
                              <Chip size="small" variant="outlined" label={`Preview ${item.previewStatus}`} />
                            )}
                          </Stack>
                        </Stack>
                      </TableCell>
                      <TableCell align="right">{item.attempts}</TableCell>
                      <TableCell>{formatLastUpdated(item.nextAttemptAt)}</TableCell>
                      <TableCell>
                        <Stack direction="row" gap={0.5} flexWrap="wrap">
                          <Chip
                            size="small"
                            label={item.queueState || (item.running ? 'RUNNING' : 'QUEUED')}
                            color={
                              item.queueState === 'RUNNING' || item.queueState === 'CANCEL_REQUESTED'
                                ? 'warning'
                                : 'default'
                            }
                            variant={item.queueState === 'RUNNING' ? 'filled' : 'outlined'}
                          />
                        </Stack>
                      </TableCell>
                      <TableCell sx={{ maxWidth: 360 }}>
                        <Tooltip title={item.governanceKey || '—'} placement="top-start" arrow>
                          <Typography variant="body2" noWrap>
                            {item.governanceKey || '—'}
                          </Typography>
                        </Tooltip>
                      </TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            </TableContainer>
          )}
        </Paper>
      )}

      {queueDeclinedSummary && (
        <Paper sx={{ p: 2, mb: 2 }}>
          <Stack direction="row" alignItems="center" justifyContent="space-between" gap={1} flexWrap="wrap" mb={1}>
            <Typography variant="h6">Preview Queue Declined</Typography>
            <Stack direction="row" gap={1} flexWrap="wrap">
              <Chip
                size="small"
                label={queueDeclinedSummary.queueEnabled ? 'Queue enabled' : 'Queue disabled'}
                color={queueDeclinedSummary.queueEnabled ? 'success' : 'default'}
                variant={queueDeclinedSummary.queueEnabled ? 'outlined' : 'filled'}
              />
              <Chip size="small" label={`Declined ${queueDeclinedSummary.totalDeclined}`} variant="outlined" />
              <Chip
                size="small"
                label={`Sample ${queueDeclinedSummary.filteredSampledItems ?? queueDeclinedSummary.items.length}/${queueDeclinedSummary.totalSampledItems ?? queueDeclinedSummary.items.length}`}
                variant="outlined"
              />
              <Chip
                size="small"
                label={`Force required ${queueDeclinedSummary.forceRequiredCount ?? 0}`}
                variant="outlined"
              />
              <Chip
                size="small"
                label={`Filter forceRequired=${queueDeclinedSummary.forceRequiredFilter || 'ANY'}`}
                variant="outlined"
              />
              <Chip
                size="small"
                label={`Filter window=${formatQueueDeclinedWindowHoursLabel(
                  queueDeclinedSummary.windowHoursFilter ?? queueDeclinedWindowHoursFilter
                )}`}
                variant="outlined"
              />
              {(queueDeclinedSummary.categoryCounts || []).map((entry) => (
                <Chip
                  key={`queue-declined-category-count-${entry.category}`}
                  size="small"
                  label={`${entry.category}: ${entry.count} (force ${entry.forceRequiredCount})`}
                  variant="outlined"
                />
              ))}
              {queueDeclinedSummary.sampleTruncated && (
                <Chip size="small" label="Sample truncated" color="warning" variant="outlined" />
              )}
              <Button
                size="small"
                variant="outlined"
                startIcon={<RefreshIcon />}
                onClick={() => void loadFailures()}
              >
                Refresh Declined
              </Button>
              <Button
                size="small"
                variant="outlined"
                startIcon={<RequeueIcon />}
                disabled={
                  queueDeclinedRequeueing
                  || queueDeclinedDryRunning
                  || (queueDeclinedSummary.filteredSampledItems ?? 0) === 0
                }
                onClick={() => void handleRequeueQueueDeclined()}
              >
                {queueDeclinedRequeueing ? 'Requeueing...' : 'Requeue Declined'}
              </Button>
              <Button
                size="small"
                variant="outlined"
                startIcon={<SearchIcon />}
                disabled={queueDeclinedDryRunning || queueDeclinedRequeueing || (queueDeclinedSummary.filteredSampledItems ?? 0) === 0}
                onClick={() => void handleDryRunQueueDeclinedRequeue()}
              >
                {queueDeclinedDryRunning ? 'Dry-running...' : 'Dry-run Requeue'}
              </Button>
              <Button
                size="small"
                variant="outlined"
                startIcon={<DownloadIcon />}
                disabled={
                  queueDeclinedDryRunExporting
                  || queueDeclinedDryRunning
                  || queueDeclinedRequeueing
                  || (queueDeclinedSummary.filteredSampledItems ?? 0) === 0
                }
                onClick={() => void handleExportQueueDeclinedRequeueDryRunCsv()}
              >
                {queueDeclinedDryRunExporting ? 'Exporting dry-run CSV...' : 'Export Requeue Dry-run CSV'}
              </Button>
              <Button
                size="small"
                variant="outlined"
                color="warning"
                startIcon={<ClearIcon />}
                disabled={queueDeclinedClearing || queueDeclinedDryRunning || (queueDeclinedSummary.filteredSampledItems ?? 0) === 0}
                onClick={() => void handleClearQueueDeclined()}
              >
                {queueDeclinedClearing ? 'Clearing...' : 'Clear Declined'}
              </Button>
              <Button
                size="small"
                variant="outlined"
                startIcon={<DownloadIcon />}
                onClick={() => void handleExportQueueDeclinedCsv()}
              >
                Export Declined CSV
              </Button>
              <Button
                size="small"
                variant="outlined"
                startIcon={<DownloadIcon />}
                onClick={() => void handleStartQueueDeclinedExportTask()}
                disabled={queueDeclinedExportTaskStarting}
                aria-label="Start queue declined async export"
              >
                {queueDeclinedExportTaskStarting ? 'Starting...' : 'Start Async Export'}
              </Button>
              <Button
                size="small"
                variant="outlined"
                startIcon={<DownloadIcon />}
                onClick={() => void handleStartQueueDeclinedRequeueDryRunExportTask()}
                disabled={queueDeclinedRequeueDryRunExportTaskStarting}
                aria-label="Start queue declined requeue dry-run async export"
              >
                {queueDeclinedRequeueDryRunExportTaskStarting ? 'Starting...' : 'Start Requeue Dry-run Async Export'}
              </Button>
              <FormControl size="small" sx={{ minWidth: 190 }}>
                <Select
                  aria-label="Queue declined async task filter status"
                  value={queueDeclinedExportTaskStatusFilter}
                  onChange={(event) => {
                    setQueueDeclinedExportTaskPage(0);
                    setQueueDeclinedExportTaskStatusFilter(
                      event.target.value as 'ALL' | PreviewQueueDeclinedExportTaskStatusFilter
                    );
                    setQueueDeclinedExportTaskRetryDryRun(null);
                    setQueueDeclinedExportTaskRetryDryRunSelectedTaskIds([]);
                  }}
                >
                  {QUEUE_DECLINED_EXPORT_TASK_STATUS_FILTER_OPTIONS.map((option) => (
                    <MenuItem key={`queue-declined-task-filter-status-${option.value}`} value={option.value}>
                      {option.label}
                    </MenuItem>
                  ))}
                </Select>
              </FormControl>
              <FormControl size="small" sx={{ minWidth: 150 }}>
                <Select
                  aria-label="Queue declined async task page size"
                  value={queueDeclinedExportTaskMaxItems}
                  onChange={(event) => {
                    setQueueDeclinedExportTaskPage(0);
                    setQueueDeclinedExportTaskMaxItems(
                      Number(event.target.value) as (typeof QUEUE_DECLINED_EXPORT_TASK_PAGE_SIZE_OPTIONS)[number]
                    );
                  }}
                >
                  {QUEUE_DECLINED_EXPORT_TASK_PAGE_SIZE_OPTIONS.map((option) => (
                    <MenuItem key={`queue-declined-task-page-size-${option}`} value={option}>
                      {`Rows ${option}`}
                    </MenuItem>
                  ))}
                </Select>
              </FormControl>
              <Button
                size="small"
                variant="outlined"
                startIcon={<RefreshIcon />}
                onClick={() => void loadQueueDeclinedExportTasks(false)}
                disabled={queueDeclinedExportTasksLoading}
                aria-label="Refresh queue declined export tasks"
              >
                {queueDeclinedExportTasksLoading ? 'Refreshing tasks...' : 'Refresh'}
              </Button>
              <Button
                size="small"
                variant="outlined"
                startIcon={<RetryIcon />}
                onClick={() => void handleCancelActiveQueueDeclinedExportTasks()}
                disabled={queueDeclinedExportTaskCancellingActive}
                aria-label="Cancel active queue declined export tasks"
              >
                {queueDeclinedExportTaskCancellingActive ? 'Cancelling active...' : 'Cancel Active'}
              </Button>
              <Button
                size="small"
                variant="outlined"
                startIcon={<SearchIcon />}
                onClick={() => void handleDryRunRetryTerminalQueueDeclinedExportTasks()}
                disabled={queueDeclinedExportTaskDryRunningTerminal}
                aria-label="Dry-run retry terminal queue declined export tasks"
              >
                {queueDeclinedExportTaskDryRunningTerminal ? 'Dry-running terminal...' : 'Dry-run Terminal'}
              </Button>
              <Button
                size="small"
                variant="outlined"
                startIcon={<DownloadIcon />}
                onClick={() => void handleExportDryRunRetryTerminalQueueDeclinedExportTasks()}
                disabled={queueDeclinedExportTaskDryRunExporting}
                aria-label="Export queue declined terminal dry-run CSV"
              >
                {queueDeclinedExportTaskDryRunExporting ? 'Exporting dry-run...' : 'Export Dry-run CSV'}
              </Button>
              <Button
                size="small"
                variant="outlined"
                startIcon={<RetryIcon />}
                onClick={() => void handleRetryTerminalQueueDeclinedExportTasks()}
                disabled={queueDeclinedExportTaskRetryingTerminal}
                aria-label="Retry terminal queue declined export tasks"
              >
                {queueDeclinedExportTaskRetryingTerminal ? 'Retrying terminal...' : 'Retry Terminal'}
              </Button>
              <Button
                size="small"
                variant="outlined"
                startIcon={<ForceIcon />}
                onClick={() => void handleCleanupQueueDeclinedExportTasks()}
                disabled={queueDeclinedExportTaskCleaning}
                aria-label="Cleanup queue declined export tasks"
              >
                {queueDeclinedExportTaskCleaning ? 'Cleaning...' : 'Cleanup'}
              </Button>
            </Stack>
          </Stack>
          <Stack direction={{ xs: 'column', md: 'row' }} gap={1} mb={1.5} alignItems={{ xs: 'stretch', md: 'center' }}>
            <FormControl size="small" sx={{ minWidth: 180 }}>
              <Select
                value={queueDeclinedCategoryFilter}
                onChange={(event) => {
                  setQueueDeclinedCategoryFilter(String(event.target.value || 'ANY'));
                  setQueueDeclinedDryRun(null);
                }}
                inputProps={{ 'aria-label': 'Queue declined category filter' }}
              >
                {queueDeclinedCategoryOptions.map((option) => (
                  <MenuItem key={`queue-declined-category-${option}`} value={option}>
                    {option === 'ANY' ? 'All categories' : option}
                  </MenuItem>
                ))}
              </Select>
            </FormControl>
            <FormControl size="small" sx={{ minWidth: 210 }}>
              <Select
                value={queueDeclinedForceRequiredFilter}
                onChange={(event) => {
                  setQueueDeclinedForceRequiredFilter(
                    event.target.value as (typeof QUEUE_DECLINED_FORCE_REQUIRED_FILTER_OPTIONS)[number]['value']
                  );
                  setQueueDeclinedDryRun(null);
                }}
                inputProps={{ 'aria-label': 'Queue declined force required filter' }}
              >
                {QUEUE_DECLINED_FORCE_REQUIRED_FILTER_OPTIONS.map((option) => (
                  <MenuItem key={`queue-declined-force-required-${option.value}`} value={option.value}>
                    {option.label}
                  </MenuItem>
                ))}
              </Select>
            </FormControl>
            <FormControl size="small" sx={{ minWidth: 120 }}>
              <Select
                value={queueDeclinedWindowHoursFilter}
                onChange={(event) => {
                  setQueueDeclinedWindowHoursFilter(
                    Number(event.target.value) as PreviewQueueDeclinedWindowHours
                  );
                  setQueueDeclinedDryRun(null);
                }}
                inputProps={{ 'aria-label': 'Queue declined window hours filter' }}
              >
                {QUEUE_DECLINED_WINDOW_HOURS_OPTIONS.map((option) => (
                  <MenuItem key={`queue-declined-window-hours-${option.value}`} value={option.value}>
                    {option.label}
                  </MenuItem>
                ))}
              </Select>
            </FormControl>
            <FormControlLabel
              control={(
                <Checkbox
                  size="small"
                  checked={queueDeclinedForce}
                  onChange={(event) => {
                    setQueueDeclinedForce(event.target.checked);
                    setQueueDeclinedDryRun(null);
                  }}
                />
              )}
              label="Force requeue"
              sx={{ ml: { xs: 0, md: 0.5 } }}
            />
            <TextField
              size="small"
              label="Declined query"
              placeholder="reason/category/name/path/documentId"
              value={queueDeclinedQueryInput}
              onChange={(event) => setQueueDeclinedQueryInput(event.target.value)}
              onKeyDown={(event) => {
                if (event.key === 'Enter') {
                  event.preventDefault();
                  handleApplyQueueDeclinedFilters();
                }
              }}
              sx={{ minWidth: 300 }}
            />
            <Button size="small" variant="outlined" startIcon={<SearchIcon />} onClick={handleApplyQueueDeclinedFilters}>
              Apply declined filters
            </Button>
            <Button
              size="small"
              variant="text"
              onClick={handleClearQueueDeclinedFilters}
              disabled={
                queueDeclinedCategoryFilter === 'ANY'
                && queueDeclinedForceRequiredFilter === 'ANY'
                && queueDeclinedWindowHoursFilter === 0
                && !queueDeclinedQueryInput
                && !queueDeclinedQueryFilter
              }
            >
              Clear
            </Button>
          </Stack>

          {queueDeclinedDryRun && (
            <Box sx={{ mb: 1.5 }}>
              <Alert severity="info" sx={{ mb: 1 }}>
                Dry-run result: requested={queueDeclinedDryRun.requested}, queued={queueDeclinedDryRun.estimatedQueued}, skipped={queueDeclinedDryRun.estimatedSkipped}, failed={queueDeclinedDryRun.estimatedFailed}, force={queueDeclinedDryRun.force ? 'true' : 'false'}, forceRequiredFilter={queueDeclinedDryRun.forceRequiredFilter || 'ANY'}, windowHours={queueDeclinedDryRun.windowHoursFilter ?? 'ANY'}.
              </Alert>
              {(queueDeclinedDryRun.reasonBreakdown || []).length > 0 && (
                <Stack direction="row" gap={0.75} alignItems="center" flexWrap="wrap" sx={{ mb: 1 }}>
                  {(queueDeclinedDryRun.reasonBreakdown || []).slice(0, 5).map((item, index) => (
                    <Chip
                      key={`queue-declined-dry-run-reason-${item?.reasonCode || 'unknown'}-${index}`}
                      size="small"
                      variant="outlined"
                      label={`${item?.reasonCode || 'UNKNOWN'}: ${item?.count ?? 0}`}
                    />
                  ))}
                </Stack>
              )}
              {(queueDeclinedDryRun.results || []).length > 0 && (
                <TableContainer>
                  <Table size="small" aria-label="Queue declined requeue dry-run results">
                    <TableHead>
                      <TableRow>
                        <TableCell>Document ID</TableCell>
                        <TableCell>Category</TableCell>
                        <TableCell>Outcome</TableCell>
                        <TableCell>Reason Code</TableCell>
                        <TableCell>Preflight</TableCell>
                        <TableCell>Message</TableCell>
                        <TableCell>Next Attempt</TableCell>
                      </TableRow>
                    </TableHead>
                    <TableBody>
                      {queueDeclinedDryRun.results.map((item, index) => (
                        <TableRow key={`queue-declined-dry-run-result-${item?.documentId || index}`}>
                          <TableCell>{item?.documentId || '-'}</TableCell>
                          <TableCell>{item?.category || '-'}</TableCell>
                          <TableCell>{item?.outcome || '-'}</TableCell>
                          <TableCell>{item?.reasonCode || '-'}</TableCell>
                          <TableCell>
                            <Stack spacing={0.25}>
                              <Typography variant="caption" component="div">
                                {`status: ${item?.preflightStatus || '-'}`}
                              </Typography>
                              <Typography variant="caption" component="div">
                                {`skip: ${item?.preflightSkipReason || '-'}`}
                              </Typography>
                              <Typography variant="caption" component="div">
                                {`route: ${item?.preflightRoute || '-'}`}
                              </Typography>
                              <Typography variant="caption" component="div">
                                {`pipeline: ${item?.preflightPipeline || '-'}`}
                              </Typography>
                              <Typography variant="caption" component="div">
                                {`profile: ${item?.preflightPolicyProfile || '-'}`}
                              </Typography>
                            </Stack>
                          </TableCell>
                          <TableCell>{item?.message || '-'}</TableCell>
                          <TableCell>{formatLastUpdated(item?.nextAttemptAt)}</TableCell>
                        </TableRow>
                      ))}
                    </TableBody>
                  </Table>
                </TableContainer>
              )}
            </Box>
          )}

          <Stack direction="row" gap={1} alignItems="center" flexWrap="wrap" sx={{ mb: 1 }}>
            <Chip size="small" variant="outlined" label={`Async total ${queueDeclinedExportTaskSummary?.totalCount ?? 0}`} />
            <Chip size="small" variant="outlined" color="info" label={`Active ${queueDeclinedExportTaskSummary?.activeCount ?? 0}`} />
            <Chip size="small" variant="outlined" color="info" label={`Queued ${queueDeclinedExportTaskSummary?.queuedCount ?? 0}`} />
            <Chip size="small" variant="outlined" color="info" label={`Running ${queueDeclinedExportTaskSummary?.runningCount ?? 0}`} />
            <Chip size="small" variant="outlined" color="success" label={`Completed ${queueDeclinedExportTaskSummary?.completedCount ?? 0}`} />
            <Chip size="small" variant="outlined" color="warning" label={`Cancelled ${queueDeclinedExportTaskSummary?.cancelledCount ?? 0}`} />
            <Chip size="small" variant="outlined" color="error" label={`Failed ${queueDeclinedExportTaskSummary?.failedCount ?? 0}`} />
            <Chip size="small" variant="outlined" color="warning" label={`Timed out ${queueDeclinedExportTaskSummary?.timedOutCount ?? 0}`} />
            <Chip size="small" variant="outlined" label={`Expired ${queueDeclinedExportTaskSummary?.expiredCount ?? 0}`} />
            <Chip size="small" variant="outlined" label={`Terminal ${queueDeclinedExportTaskSummary?.terminalCount ?? 0}`} />
            <Chip
              size="small"
              variant="outlined"
              label={`Page ${Math.min(
                queueDeclinedExportTaskPage + 1,
                Math.max(Math.ceil(queueDeclinedExportTaskTotalItems / Math.max(queueDeclinedExportTaskMaxItems, 1)), 1)
              )}/${Math.max(Math.ceil(queueDeclinedExportTaskTotalItems / Math.max(queueDeclinedExportTaskMaxItems, 1)), 1)}`}
            />
            <Chip
              size="small"
              variant="outlined"
              label={`Listed ${queueDeclinedExportTasks.length}/${queueDeclinedExportTaskTotalItems}`}
            />
            <Button
              size="small"
              variant="outlined"
              onClick={() => setQueueDeclinedExportTaskPage((current) => Math.max(0, current - 1))}
              disabled={queueDeclinedExportTasksLoading || queueDeclinedExportTaskPage <= 0}
            >
              Prev page
            </Button>
            <Button
              size="small"
              variant="outlined"
              onClick={() => setQueueDeclinedExportTaskPage((current) => current + 1)}
              disabled={queueDeclinedExportTasksLoading || !queueDeclinedExportTaskHasMoreItems}
            >
              Next page
            </Button>
          </Stack>

          {queueDeclinedExportTaskRetryDryRun && (
            <Paper variant="outlined" sx={{ p: 1.5, mb: 1.5 }}>
              <Stack direction={{ xs: 'column', md: 'row' }} gap={1} alignItems={{ xs: 'flex-start', md: 'center' }} sx={{ mb: 1 }}>
                <Typography variant="subtitle2">Terminal Retry Dry-run Candidates</Typography>
                <Chip size="small" variant="outlined" label={`Requested ${queueDeclinedExportTaskRetryDryRun.requested ?? 0}`} />
                <Chip size="small" variant="outlined" color="info" label={`Retryable ${queueDeclinedExportTaskRetryDryRun.retryable ?? 0}`} />
                <Chip size="small" variant="outlined" label={`Skipped ${queueDeclinedExportTaskRetryDryRun.skipped ?? 0}`} />
                <Chip size="small" variant="outlined" label={`Selected ${queueDeclinedExportTaskRetryDryRunSelectedTaskIds.length}`} />
                {(queueDeclinedExportTaskRetryDryRun.reasonBreakdown || []).slice(0, 5).map((item, index) => (
                  <Chip
                    key={`queue-declined-retry-dry-run-reason-${item?.reasonCode || 'unknown'}-${index}`}
                    size="small"
                    variant="outlined"
                    label={`${item?.reasonCode || 'UNKNOWN'}:${item?.count ?? 0}`}
                  />
                ))}
                <Button
                  size="small"
                  variant="outlined"
                  onClick={() => {
                    const retryableTaskIds = (queueDeclinedExportTaskRetryDryRun.results || [])
                      .filter((item) => (item?.outcome || '').toUpperCase() === 'RETRYABLE')
                      .map((item) => String(item?.sourceTaskId || '').trim())
                      .filter((taskId) => taskId.length > 0);
                    setQueueDeclinedExportTaskRetryDryRunSelectedTaskIds(Array.from(new Set(retryableTaskIds)));
                  }}
                >
                  Select All
                </Button>
                <Button
                  size="small"
                  variant="outlined"
                  onClick={() => setQueueDeclinedExportTaskRetryDryRunSelectedTaskIds([])}
                  disabled={queueDeclinedExportTaskRetryDryRunSelectedTaskIds.length === 0}
                >
                  Clear Selection
                </Button>
                <Button
                  size="small"
                  variant="contained"
                  startIcon={<RetryIcon />}
                  onClick={() => void handleRetrySelectedQueueDeclinedExportTasks()}
                  disabled={queueDeclinedExportTaskRetryingSelected || queueDeclinedExportTaskRetryDryRunSelectedTaskIds.length === 0}
                  aria-label="Retry selected queue declined export tasks"
                >
                  {queueDeclinedExportTaskRetryingSelected ? 'Retrying selected...' : 'Retry Selected'}
                </Button>
              </Stack>
              {(queueDeclinedExportTaskRetryDryRun.results || []).length === 0 ? (
                <Alert severity="info">No dry-run candidates returned.</Alert>
              ) : (
                <TableContainer>
                  <Table size="small" aria-label="Queue declined async retry dry-run candidates">
                    <TableHead>
                      <TableRow>
                        <TableCell padding="checkbox">Select</TableCell>
                        <TableCell>Source Task ID</TableCell>
                        <TableCell>Status</TableCell>
                        <TableCell>Outcome</TableCell>
                        <TableCell>Reason</TableCell>
                        <TableCell>Message</TableCell>
                      </TableRow>
                    </TableHead>
                    <TableBody>
                      {queueDeclinedExportTaskRetryDryRun.results.map((item, index) => {
                        const sourceTaskId = String(item?.sourceTaskId || '').trim();
                        const selectable = sourceTaskId.length > 0 && (item?.outcome || '').toUpperCase() === 'RETRYABLE';
                        const checked = selectable && queueDeclinedExportTaskRetryDryRunSelectedTaskIds.includes(sourceTaskId);
                        return (
                          <TableRow key={`queue-declined-retry-dry-run-${sourceTaskId || index}`}>
                            <TableCell padding="checkbox">
                              <Checkbox
                                size="small"
                                checked={checked}
                                disabled={!selectable}
                                inputProps={{ 'aria-label': `Select queue declined retry source task ${sourceTaskId || 'unknown'}` }}
                                onChange={(event) => {
                                  if (!selectable) {
                                    return;
                                  }
                                  setQueueDeclinedExportTaskRetryDryRunSelectedTaskIds((prev) => {
                                    if (event.target.checked) {
                                      return prev.includes(sourceTaskId) ? prev : [...prev, sourceTaskId];
                                    }
                                    return prev.filter((taskId) => taskId !== sourceTaskId);
                                  });
                                }}
                              />
                            </TableCell>
                            <TableCell>{sourceTaskId || '-'}</TableCell>
                            <TableCell>{item?.sourceStatus || '-'}</TableCell>
                            <TableCell>{item?.outcome || '-'}</TableCell>
                            <TableCell>{item?.reasonCode || '-'}</TableCell>
                            <TableCell>{item?.message || '-'}</TableCell>
                          </TableRow>
                        );
                      })}
                    </TableBody>
                  </Table>
                </TableContainer>
              )}
            </Paper>
          )}

          <Typography variant="subtitle2" sx={{ mb: 1 }}>Declined Async Export Tasks</Typography>
          {queueDeclinedExportTasks.length === 0 ? (
            <Alert severity="info" sx={{ mb: 1.5 }}>No queue declined async export tasks yet.</Alert>
          ) : (
            <TableContainer sx={{ mb: 1.5 }}>
              <Table size="small" aria-label="Queue declined async export tasks">
                <TableHead>
                  <TableRow>
                    <TableCell>Task ID</TableCell>
                    <TableCell>Status</TableCell>
                    <TableCell>Created</TableCell>
                    <TableCell>Finished</TableCell>
                    <TableCell>Filename</TableCell>
                    <TableCell align="right">Actions</TableCell>
                  </TableRow>
                </TableHead>
                <TableBody>
                  {queueDeclinedExportTasks.map((task) => {
                    const normalizedStatus = normalizeQueueDeclinedExportTaskStatus(task.status);
                    const canRetry = QUEUE_DECLINED_EXPORT_TASK_RETRYABLE_STATUSES.has(normalizedStatus);
                    const canCancel = QUEUE_DECLINED_EXPORT_TASK_RUNNING_STATUSES.has(normalizedStatus);
                    const canDownload = QUEUE_DECLINED_EXPORT_TASK_COMPLETED_STATUSES.has(normalizedStatus);
                    const actionBusy = queueDeclinedExportTaskActionId === task.taskId;
                    const retryBusy = actionBusy && queueDeclinedExportTaskActionType === 'retry';
                    const cancelBusy = actionBusy && queueDeclinedExportTaskActionType === 'cancel';
                    const downloadBusy = actionBusy && queueDeclinedExportTaskActionType === 'download';
                    return (
                      <TableRow key={`queue-declined-export-task-${task.taskId}`}>
                        <TableCell sx={{ maxWidth: 320 }}>
                          <Tooltip title={task.taskId} placement="top-start" arrow>
                            <Box sx={{ minWidth: 0 }}>
                              <Typography variant="body2" noWrap>{task.taskId}</Typography>
                              <Typography variant="caption" color="text.secondary" aria-hidden>
                                {`Updated ${formatLastUpdated(task.updatedAt)} · Timeout ${formatLastUpdated(task.timeoutAt)} · Expires ${formatLastUpdated(task.expiresAt)}`}
                              </Typography>
                            </Box>
                          </Tooltip>
                        </TableCell>
                        <TableCell>
                          <Stack spacing={0.5}>
                            <Chip size="small" variant="outlined" label={normalizedStatus} />
                            <Typography variant="caption" color="text.secondary" aria-hidden>
                              {`Actor ${task.updatedBy || task.createdBy || '-'}`}
                            </Typography>
                          </Stack>
                        </TableCell>
                        <TableCell>{formatLastUpdated(task.createdAt)}</TableCell>
                        <TableCell>{formatLastUpdated(task.finishedAt)}</TableCell>
                        <TableCell sx={{ maxWidth: 320 }}>
                          <Tooltip title={task.filename || '—'} placement="top-start" arrow>
                            <Typography variant="body2" noWrap>{task.filename || '—'}</Typography>
                          </Tooltip>
                        </TableCell>
                        <TableCell align="right">
                          <Stack direction="row" gap={0.5} justifyContent="flex-end">
                            {canRetry && (
                              <Button
                                size="small"
                                variant="outlined"
                                startIcon={<RetryIcon />}
                                disabled={Boolean(queueDeclinedExportTaskActionId)}
                                aria-label={`Retry queue declined export task ${task.taskId}`}
                                onClick={() => void handleRetryQueueDeclinedExportTask(task.taskId)}
                              >
                                {retryBusy ? 'Retrying...' : 'Retry'}
                              </Button>
                            )}
                            {canCancel && (
                              <Button
                                size="small"
                                variant="outlined"
                                color="warning"
                                disabled={Boolean(queueDeclinedExportTaskActionId)}
                                aria-label={`Cancel queue declined export task ${task.taskId}`}
                                onClick={() => void handleCancelQueueDeclinedExportTask(task.taskId)}
                              >
                                {cancelBusy ? 'Cancelling...' : 'Cancel'}
                              </Button>
                            )}
                            {canDownload && (
                              <Button
                                size="small"
                                variant="outlined"
                                startIcon={<DownloadIcon />}
                                disabled={Boolean(queueDeclinedExportTaskActionId)}
                                aria-label={`Download queue declined export task ${task.taskId}`}
                                onClick={() => void handleDownloadQueueDeclinedExportTask(task)}
                              >
                                {downloadBusy ? 'Downloading...' : 'Download'}
                              </Button>
                            )}
                          </Stack>
                        </TableCell>
                      </TableRow>
                    );
                  })}
                </TableBody>
              </Table>
            </TableContainer>
          )}

          <Stack direction={{ xs: 'column', md: 'row' }} gap={1} alignItems={{ xs: 'flex-start', md: 'center' }} sx={{ mb: 1 }}>
            <Typography variant="subtitle2">Requeue Dry-run Async Export Tasks</Typography>
            <FormControl size="small" sx={{ minWidth: 190 }}>
              <Select
                aria-label="Queue declined requeue dry-run async task filter status"
                value={queueDeclinedRequeueDryRunExportTaskStatusFilter}
                onChange={(event) => {
                  setQueueDeclinedRequeueDryRunExportTaskPage(0);
                  setQueueDeclinedRequeueDryRunExportTaskStatusFilter(
                    event.target.value as 'ALL' | PreviewQueueDeclinedRequeueDryRunExportTaskStatusFilter
                  );
                }}
              >
                {QUEUE_DECLINED_REQUEUE_DRY_RUN_EXPORT_TASK_STATUS_FILTER_OPTIONS.map((option) => (
                  <MenuItem key={`queue-declined-requeue-dry-run-task-filter-status-${option.value}`} value={option.value}>
                    {option.label}
                  </MenuItem>
                ))}
              </Select>
            </FormControl>
            <FormControl size="small" sx={{ minWidth: 150 }}>
              <Select
                aria-label="Queue declined requeue dry-run async task page size"
                value={queueDeclinedRequeueDryRunExportTaskMaxItems}
                onChange={(event) => {
                  setQueueDeclinedRequeueDryRunExportTaskPage(0);
                  setQueueDeclinedRequeueDryRunExportTaskMaxItems(
                    Number(event.target.value) as (typeof QUEUE_DECLINED_EXPORT_TASK_PAGE_SIZE_OPTIONS)[number]
                  );
                }}
              >
                {QUEUE_DECLINED_EXPORT_TASK_PAGE_SIZE_OPTIONS.map((option) => (
                  <MenuItem key={`queue-declined-requeue-dry-run-task-page-size-${option}`} value={option}>
                    {`Rows ${option}`}
                  </MenuItem>
                ))}
              </Select>
            </FormControl>
            <Button
              size="small"
              variant="outlined"
              startIcon={<RefreshIcon />}
              onClick={() => void loadQueueDeclinedRequeueDryRunExportTasks(false)}
              disabled={queueDeclinedRequeueDryRunExportTasksLoading}
              aria-label="Refresh queue declined requeue dry-run export tasks"
            >
              {queueDeclinedRequeueDryRunExportTasksLoading ? 'Refreshing tasks...' : 'Refresh'}
            </Button>
            <Button
              size="small"
              variant="outlined"
              startIcon={<RetryIcon />}
              onClick={() => void handleCancelActiveQueueDeclinedRequeueDryRunExportTasks()}
              disabled={queueDeclinedRequeueDryRunExportTaskCancellingActive}
              aria-label="Cancel active queue declined requeue dry-run export tasks"
            >
              {queueDeclinedRequeueDryRunExportTaskCancellingActive ? 'Cancelling active...' : 'Cancel Active'}
            </Button>
            <Button
              size="small"
              variant="outlined"
              startIcon={<SearchIcon />}
              onClick={() => void handleDryRunRetryTerminalQueueDeclinedRequeueDryRunExportTasks()}
              disabled={queueDeclinedRequeueDryRunExportTaskDryRunningTerminal}
              aria-label="Dry-run retry terminal queue declined requeue dry-run export tasks"
            >
              {queueDeclinedRequeueDryRunExportTaskDryRunningTerminal ? 'Dry-running terminal...' : 'Dry-run Terminal'}
            </Button>
            <Button
              size="small"
              variant="outlined"
              startIcon={<DownloadIcon />}
              onClick={() => void handleExportDryRunRetryTerminalQueueDeclinedRequeueDryRunExportTasks()}
              disabled={queueDeclinedRequeueDryRunExportTaskDryRunExporting}
              aria-label="Export queue declined requeue dry-run terminal dry-run CSV"
            >
              {queueDeclinedRequeueDryRunExportTaskDryRunExporting ? 'Exporting dry-run...' : 'Export Dry-run CSV'}
            </Button>
            <Button
              size="small"
              variant="outlined"
              startIcon={<RetryIcon />}
              onClick={() => void handleRetryTerminalQueueDeclinedRequeueDryRunExportTasks()}
              disabled={queueDeclinedRequeueDryRunExportTaskRetryingTerminal}
              aria-label="Retry terminal queue declined requeue dry-run export tasks"
            >
              {queueDeclinedRequeueDryRunExportTaskRetryingTerminal ? 'Retrying terminal...' : 'Retry Terminal'}
            </Button>
            <Button
              size="small"
              variant="outlined"
              startIcon={<ForceIcon />}
              onClick={() => void handleCleanupQueueDeclinedRequeueDryRunExportTasks()}
              disabled={queueDeclinedRequeueDryRunExportTaskCleaning}
              aria-label="Cleanup queue declined requeue dry-run export tasks"
            >
              {queueDeclinedRequeueDryRunExportTaskCleaning ? 'Cleaning...' : 'Cleanup'}
            </Button>
          </Stack>

          <Stack direction="row" gap={1} alignItems="center" flexWrap="wrap" sx={{ mb: 1 }}>
            <Chip size="small" variant="outlined" label={`Total ${queueDeclinedRequeueDryRunExportTaskSummary?.totalCount ?? 0}`} />
            <Chip size="small" variant="outlined" color="info" label={`Active ${queueDeclinedRequeueDryRunExportTaskSummary?.activeCount ?? 0}`} />
            <Chip size="small" variant="outlined" color="success" label={`Completed ${queueDeclinedRequeueDryRunExportTaskSummary?.completedCount ?? 0}`} />
            <Chip size="small" variant="outlined" color="error" label={`Failed ${queueDeclinedRequeueDryRunExportTaskSummary?.failedCount ?? 0}`} />
            <Chip size="small" variant="outlined" color="warning" label={`Timed out ${queueDeclinedRequeueDryRunExportTaskSummary?.timedOutCount ?? 0}`} />
            <Chip size="small" variant="outlined" label={`Expired ${queueDeclinedRequeueDryRunExportTaskSummary?.expiredCount ?? 0}`} />
            <Chip
              size="small"
              variant="outlined"
              label={`Page ${Math.min(
                queueDeclinedRequeueDryRunExportTaskPage + 1,
                Math.max(
                  Math.ceil(
                    queueDeclinedRequeueDryRunExportTaskTotalItems
                    / Math.max(queueDeclinedRequeueDryRunExportTaskMaxItems, 1)
                  ),
                  1
                )
              )}/${Math.max(
                Math.ceil(
                  queueDeclinedRequeueDryRunExportTaskTotalItems
                  / Math.max(queueDeclinedRequeueDryRunExportTaskMaxItems, 1)
                ),
                1
              )}`}
            />
            <Chip
              size="small"
              variant="outlined"
              label={`Listed ${queueDeclinedRequeueDryRunExportTasks.length}/${queueDeclinedRequeueDryRunExportTaskTotalItems}`}
            />
            <Button
              size="small"
              variant="outlined"
              onClick={() => setQueueDeclinedRequeueDryRunExportTaskPage((current) => Math.max(0, current - 1))}
              disabled={queueDeclinedRequeueDryRunExportTasksLoading || queueDeclinedRequeueDryRunExportTaskPage <= 0}
            >
              Prev page
            </Button>
            <Button
              size="small"
              variant="outlined"
              onClick={() => setQueueDeclinedRequeueDryRunExportTaskPage((current) => current + 1)}
              disabled={queueDeclinedRequeueDryRunExportTasksLoading || !queueDeclinedRequeueDryRunExportTaskHasMoreItems}
            >
              Next page
            </Button>
          </Stack>

          {queueDeclinedRequeueDryRunExportTaskRetryDryRun && (
            <Paper variant="outlined" sx={{ p: 1.5, mb: 1.5 }}>
              <Stack direction={{ xs: 'column', md: 'row' }} gap={1} alignItems={{ xs: 'flex-start', md: 'center' }} sx={{ mb: 1 }}>
                <Typography variant="subtitle2">Requeue Dry-run Terminal Retry Candidates</Typography>
                <Chip size="small" variant="outlined" label={`Requested ${queueDeclinedRequeueDryRunExportTaskRetryDryRun.requested ?? 0}`} />
                <Chip size="small" variant="outlined" color="info" label={`Retryable ${queueDeclinedRequeueDryRunExportTaskRetryDryRun.retryable ?? 0}`} />
                <Chip size="small" variant="outlined" label={`Skipped ${queueDeclinedRequeueDryRunExportTaskRetryDryRun.skipped ?? 0}`} />
                <Chip size="small" variant="outlined" label={`Selected ${queueDeclinedRequeueDryRunExportTaskRetryDryRunSelectedTaskIds.length}`} />
                {(queueDeclinedRequeueDryRunExportTaskRetryDryRun.reasonBreakdown || []).slice(0, 5).map((item, index) => (
                  <Chip
                    key={`queue-declined-requeue-retry-dry-run-reason-${item?.reasonCode || 'unknown'}-${index}`}
                    size="small"
                    variant="outlined"
                    label={`${item?.reasonCode || 'UNKNOWN'}:${item?.count ?? 0}`}
                  />
                ))}
                <Button
                  size="small"
                  variant="outlined"
                  onClick={() => {
                    const retryableTaskIds = (queueDeclinedRequeueDryRunExportTaskRetryDryRun.results || [])
                      .filter((item) => (item?.outcome || '').toUpperCase() === 'RETRYABLE')
                      .map((item) => String(item?.sourceTaskId || '').trim())
                      .filter((taskId) => taskId.length > 0);
                    setQueueDeclinedRequeueDryRunExportTaskRetryDryRunSelectedTaskIds(Array.from(new Set(retryableTaskIds)));
                  }}
                >
                  Select All
                </Button>
                <Button
                  size="small"
                  variant="outlined"
                  onClick={() => setQueueDeclinedRequeueDryRunExportTaskRetryDryRunSelectedTaskIds([])}
                  disabled={queueDeclinedRequeueDryRunExportTaskRetryDryRunSelectedTaskIds.length === 0}
                >
                  Clear Selection
                </Button>
                <Button
                  size="small"
                  variant="contained"
                  startIcon={<RetryIcon />}
                  onClick={() => void handleRetrySelectedQueueDeclinedRequeueDryRunExportTasks()}
                  disabled={
                    queueDeclinedRequeueDryRunExportTaskRetryingSelected
                    || queueDeclinedRequeueDryRunExportTaskRetryDryRunSelectedTaskIds.length === 0
                  }
                  aria-label="Retry selected queue declined requeue dry-run export tasks"
                >
                  {queueDeclinedRequeueDryRunExportTaskRetryingSelected ? 'Retrying selected...' : 'Retry Selected'}
                </Button>
              </Stack>
              {(queueDeclinedRequeueDryRunExportTaskRetryDryRun.results || []).length === 0 ? (
                <Alert severity="info">No dry-run candidates returned.</Alert>
              ) : (
                <TableContainer>
                  <Table size="small" aria-label="Queue declined requeue dry-run async retry dry-run candidates">
                    <TableHead>
                      <TableRow>
                        <TableCell padding="checkbox">Select</TableCell>
                        <TableCell>Source Task ID</TableCell>
                        <TableCell>Status</TableCell>
                        <TableCell>Outcome</TableCell>
                        <TableCell>Reason</TableCell>
                        <TableCell>Message</TableCell>
                      </TableRow>
                    </TableHead>
                    <TableBody>
                      {queueDeclinedRequeueDryRunExportTaskRetryDryRun.results.map((item, index) => {
                        const sourceTaskId = String(item?.sourceTaskId || '').trim();
                        const selectable = sourceTaskId.length > 0 && (item?.outcome || '').toUpperCase() === 'RETRYABLE';
                        const checked = selectable && queueDeclinedRequeueDryRunExportTaskRetryDryRunSelectedTaskIds.includes(sourceTaskId);
                        return (
                          <TableRow key={`queue-declined-requeue-retry-dry-run-${sourceTaskId || index}`}>
                            <TableCell padding="checkbox">
                              <Checkbox
                                size="small"
                                checked={checked}
                                disabled={!selectable}
                                inputProps={{ 'aria-label': `Select queue declined requeue retry source task ${sourceTaskId || 'unknown'}` }}
                                onChange={(event) => {
                                  if (!selectable) {
                                    return;
                                  }
                                  setQueueDeclinedRequeueDryRunExportTaskRetryDryRunSelectedTaskIds((prev) => {
                                    if (event.target.checked) {
                                      return prev.includes(sourceTaskId) ? prev : [...prev, sourceTaskId];
                                    }
                                    return prev.filter((taskId) => taskId !== sourceTaskId);
                                  });
                                }}
                              />
                            </TableCell>
                            <TableCell>{sourceTaskId || '-'}</TableCell>
                            <TableCell>{item?.sourceStatus || '-'}</TableCell>
                            <TableCell>{item?.outcome || '-'}</TableCell>
                            <TableCell>{item?.reasonCode || '-'}</TableCell>
                            <TableCell>{item?.message || '-'}</TableCell>
                          </TableRow>
                        );
                      })}
                    </TableBody>
                  </Table>
                </TableContainer>
              )}
            </Paper>
          )}

          {queueDeclinedRequeueDryRunExportTasks.length === 0 ? (
            <Alert severity="info" sx={{ mb: 1.5 }}>No queue declined requeue dry-run async export tasks yet.</Alert>
          ) : (
            <TableContainer sx={{ mb: 1.5 }}>
              <Table size="small" aria-label="Queue declined requeue dry-run async export tasks">
                <TableHead>
                  <TableRow>
                    <TableCell>Task ID</TableCell>
                    <TableCell>Status</TableCell>
                    <TableCell>Created</TableCell>
                    <TableCell>Finished</TableCell>
                    <TableCell align="right">Actions</TableCell>
                  </TableRow>
                </TableHead>
                <TableBody>
                  {queueDeclinedRequeueDryRunExportTasks.map((task) => {
                    const normalizedStatus = normalizeQueueDeclinedRequeueDryRunExportTaskStatus(task.status);
                    const canRetry = QUEUE_DECLINED_REQUEUE_DRY_RUN_EXPORT_TASK_RETRYABLE_STATUSES.has(normalizedStatus);
                    const canCancel = QUEUE_DECLINED_REQUEUE_DRY_RUN_EXPORT_TASK_RUNNING_STATUSES.has(normalizedStatus);
                    const canDownload = QUEUE_DECLINED_REQUEUE_DRY_RUN_EXPORT_TASK_COMPLETED_STATUSES.has(normalizedStatus);
                    const actionBusy = queueDeclinedRequeueDryRunExportTaskActionId === task.taskId;
                    const retryBusy = actionBusy && queueDeclinedRequeueDryRunExportTaskActionType === 'retry';
                    const cancelBusy = actionBusy && queueDeclinedRequeueDryRunExportTaskActionType === 'cancel';
                    const downloadBusy = actionBusy && queueDeclinedRequeueDryRunExportTaskActionType === 'download';
                    return (
                      <TableRow key={`queue-declined-requeue-dry-run-export-task-${task.taskId}`}>
                        <TableCell sx={{ maxWidth: 320 }}>
                          <Tooltip title={task.taskId} placement="top-start" arrow>
                            <Box sx={{ minWidth: 0 }}>
                              <Typography variant="body2" noWrap>{task.taskId}</Typography>
                              <Typography variant="caption" color="text.secondary" aria-hidden>
                                {`Updated ${formatLastUpdated(task.updatedAt)} · Timeout ${formatLastUpdated(task.timeoutAt)} · Expires ${formatLastUpdated(task.expiresAt)}`}
                              </Typography>
                            </Box>
                          </Tooltip>
                        </TableCell>
                        <TableCell>
                          <Stack spacing={0.5}>
                            <Chip size="small" variant="outlined" label={normalizedStatus} />
                            <Typography variant="caption" color="text.secondary" aria-hidden>
                              {`Actor ${task.updatedBy || task.createdBy || '-'}`}
                            </Typography>
                          </Stack>
                        </TableCell>
                        <TableCell>{formatLastUpdated(task.createdAt)}</TableCell>
                        <TableCell>{formatLastUpdated(task.finishedAt)}</TableCell>
                        <TableCell align="right">
                          <Stack direction="row" gap={0.5} justifyContent="flex-end">
                            {canRetry && (
                              <Button
                                size="small"
                                variant="outlined"
                                startIcon={<RetryIcon />}
                                disabled={Boolean(queueDeclinedRequeueDryRunExportTaskActionId)}
                                aria-label={`Retry queue declined requeue dry-run export task ${task.taskId}`}
                                onClick={() => void handleRetryQueueDeclinedRequeueDryRunExportTask(task.taskId)}
                              >
                                {retryBusy ? 'Retrying...' : 'Retry'}
                              </Button>
                            )}
                            {canCancel && (
                              <Button
                                size="small"
                                variant="outlined"
                                color="warning"
                                disabled={Boolean(queueDeclinedRequeueDryRunExportTaskActionId)}
                                aria-label={`Cancel queue declined requeue dry-run export task ${task.taskId}`}
                                onClick={() => void handleCancelQueueDeclinedRequeueDryRunExportTask(task.taskId)}
                              >
                                {cancelBusy ? 'Cancelling...' : 'Cancel'}
                              </Button>
                            )}
                            {canDownload && (
                              <Button
                                size="small"
                                variant="outlined"
                                startIcon={<DownloadIcon />}
                                disabled={Boolean(queueDeclinedRequeueDryRunExportTaskActionId)}
                                aria-label={`Download queue declined requeue dry-run export task ${task.taskId}`}
                                onClick={() => void handleDownloadQueueDeclinedRequeueDryRunExportTask(task)}
                              >
                                {downloadBusy ? 'Downloading...' : 'Download'}
                              </Button>
                            )}
                          </Stack>
                        </TableCell>
                      </TableRow>
                    );
                  })}
                </TableBody>
              </Table>
            </TableContainer>
          )}

          {queueDeclinedSummary.items.length === 0 ? (
            <Alert severity="info">No declined queue decisions.</Alert>
          ) : (
            <TableContainer>
              <Table size="small" aria-label="Preview queue declined">
                <TableHead>
                  <TableRow>
                    <TableCell>Document</TableCell>
                    <TableCell>Category</TableCell>
                    <TableCell>Reason</TableCell>
                    <TableCell>Declined At</TableCell>
                    <TableCell>Next Eligible</TableCell>
                    <TableCell>Force Required</TableCell>
                  </TableRow>
                </TableHead>
                <TableBody>
                  {queueDeclinedSummary.items.map((item) => (
                    <TableRow key={`${item.documentId || 'unknown'}-${item.declinedAt || 'none'}`}>
                      <TableCell>
                        <Stack spacing={0.25}>
                          <Typography variant="body2" fontWeight={600}>
                            {item.name || item.documentId || 'Unknown'}
                          </Typography>
                          {item.path && (
                            <Typography variant="caption" color="text.secondary">
                              {item.path}
                            </Typography>
                          )}
                          <Typography variant="caption" color="text.secondary">
                            {item.documentId || '—'}
                          </Typography>
                          <Stack direction="row" gap={0.5} flexWrap="wrap">
                            {item.mimeType && <Chip size="small" variant="outlined" label={item.mimeType} />}
                            {item.previewStatus && (
                              <Chip size="small" variant="outlined" label={`Preview ${item.previewStatus}`} />
                            )}
                          </Stack>
                        </Stack>
                      </TableCell>
                      <TableCell>
                        <Chip size="small" label={item.category || 'DECLINED'} color="warning" variant="outlined" />
                      </TableCell>
                      <TableCell sx={{ maxWidth: 420 }}>
                        <Tooltip title={item.governanceKey || '—'} placement="top-start" arrow>
                          <Typography variant="body2">{item.reason || '—'}</Typography>
                        </Tooltip>
                      </TableCell>
                      <TableCell>{formatLastUpdated(item.declinedAt)}</TableCell>
                      <TableCell>{formatLastUpdated(item.nextEligibleAt)}</TableCell>
                      <TableCell>{item.forceRequired ? 'Yes' : 'No'}</TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            </TableContainer>
          )}
        </Paper>
      )}

      {renditionSummary && (
        <Paper sx={{ p: 2, mb: 2 }}>
          <Stack direction="row" alignItems="center" justifyContent="space-between" gap={1} flexWrap="wrap" mb={1}>
            <Typography variant="h6">Rendition Resource Summary</Typography>
            <Stack direction="row" gap={1} flexWrap="wrap" alignItems="center">
              <Chip label={`Sampled ${renditionSummary.sampledResources}/${renditionSummary.totalResources}`} />
              {renditionSummary.sampleTruncated && (
                <Chip size="small" label="Sample truncated" color="warning" variant="outlined" />
              )}
              <Button
                size="small"
                variant="outlined"
                startIcon={<RefreshIcon />}
                onClick={() => void handleRefreshRenditionSummary()}
                disabled={renditionSummaryRefreshing}
                aria-label="Refresh rendition resource summary"
              >
                {renditionSummaryRefreshing ? 'Refreshing...' : 'Refresh'}
              </Button>
              <Button
                size="small"
                variant="outlined"
                startIcon={<DownloadIcon />}
                onClick={() => void handleExportRenditionResourcesCsv()}
                aria-label="Export rendition resources CSV"
              >
                Export Resources CSV
              </Button>
              <Button
                size="small"
                variant="outlined"
                startIcon={<DownloadIcon />}
                onClick={() => void handleStartRenditionExportTask()}
                disabled={renditionExportTaskStarting}
                aria-label="Start rendition resources async export"
              >
                {renditionExportTaskStarting ? 'Starting...' : 'Start Async Export'}
              </Button>
              <FormControl size="small" sx={{ minWidth: 190 }}>
                <Select
                  aria-label="Rendition async task filter status"
                  value={renditionExportTaskStatusFilter}
                  onChange={(event) => {
                    setRenditionExportTaskStatusFilter(
                      event.target.value as 'ALL' | PreviewRenditionResourcesExportTaskStatusFilter
                    );
                  }}
                >
                  {RENDITION_EXPORT_TASK_STATUS_FILTER_OPTIONS.map((option) => (
                    <MenuItem key={`rendition-task-filter-status-${option.value}`} value={option.value}>
                      {option.label}
                    </MenuItem>
                  ))}
                </Select>
              </FormControl>
              <Button
                size="small"
                variant="outlined"
                startIcon={<RefreshIcon />}
                onClick={() => void loadRenditionExportTasks(false)}
                disabled={renditionExportTasksLoading}
                aria-label="Refresh rendition export tasks"
              >
                {renditionExportTasksLoading ? 'Refreshing tasks...' : 'Refresh export tasks'}
              </Button>
              <Button
                size="small"
                variant="outlined"
                startIcon={<RetryIcon />}
                onClick={() => void handleCancelActiveRenditionExportTasks()}
                disabled={renditionExportTaskCancellingActive}
                aria-label="Cancel active rendition export tasks"
              >
                {renditionExportTaskCancellingActive ? 'Cancelling active...' : 'Cancel active tasks'}
              </Button>
              <Button
                size="small"
                variant="outlined"
                startIcon={<ForceIcon />}
                onClick={() => void handleCleanupRenditionExportTasks()}
                disabled={renditionExportTaskCleaning}
                aria-label="Cleanup rendition export tasks"
              >
                {renditionExportTaskCleaning ? 'Cleaning...' : 'Cleanup export tasks'}
              </Button>
            </Stack>
          </Stack>

          {renditionSummary.statusCounts.length === 0 ? (
            <Alert severity="info" sx={{ mb: 2 }}>No rendition statuses in current summary sample.</Alert>
          ) : (
            <Stack direction="row" gap={1} flexWrap="wrap" mb={2}>
              {renditionSummary.statusCounts.map((entry) => (
                <Chip
                  key={`rendition-status-${entry.status}`}
                  size="small"
                  label={`Status ${entry.status}: ${entry.count}`}
                  variant="outlined"
                />
              ))}
            </Stack>
          )}

          {renditionTopReasons.length === 0 ? (
            <Alert severity="info">No rendition reasons in current summary sample.</Alert>
          ) : (
            <TableContainer>
              <Table size="small" aria-label="Rendition resource top reasons">
                <TableHead>
                  <TableRow>
                    <TableCell>Top Reason</TableCell>
                    <TableCell align="right">Count</TableCell>
                  </TableRow>
                </TableHead>
                <TableBody>
                  {renditionTopReasons.map((entry, index) => (
                    <TableRow key={`rendition-reason-${index}-${entry.reason}`}>
                      <TableCell sx={{ maxWidth: 720 }}>
                        <Tooltip title={entry.reason || '—'} placement="top-start" arrow>
                          <Typography variant="body2" noWrap>{entry.reason || '—'}</Typography>
                        </Tooltip>
                      </TableCell>
                      <TableCell align="right">{entry.count}</TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            </TableContainer>
          )}

          <Stack direction="row" gap={1} alignItems="center" flexWrap="wrap" sx={{ mt: 2, mb: 1 }}>
            <Chip size="small" variant="outlined" label={`Async total ${renditionExportTaskSummary?.totalCount ?? 0}`} />
            <Chip size="small" variant="outlined" color="info" label={`Active ${renditionExportTaskSummary?.activeCount ?? 0}`} />
            <Chip size="small" variant="outlined" color="info" label={`Queued ${renditionExportTaskSummary?.queuedCount ?? 0}`} />
            <Chip size="small" variant="outlined" color="info" label={`Running ${renditionExportTaskSummary?.runningCount ?? 0}`} />
            <Chip size="small" variant="outlined" color="success" label={`Completed ${renditionExportTaskSummary?.completedCount ?? 0}`} />
            <Chip size="small" variant="outlined" color="warning" label={`Cancelled ${renditionExportTaskSummary?.cancelledCount ?? 0}`} />
            <Chip size="small" variant="outlined" color="error" label={`Failed ${renditionExportTaskSummary?.failedCount ?? 0}`} />
            <Chip size="small" variant="outlined" color="warning" label={`Timed out ${renditionExportTaskSummary?.timedOutCount ?? 0}`} />
            <Chip size="small" variant="outlined" color="default" label={`Expired ${renditionExportTaskSummary?.expiredCount ?? 0}`} />
            <Chip size="small" variant="outlined" label={`Terminal ${renditionExportTaskSummary?.terminalCount ?? 0}`} />
          </Stack>

          <Typography variant="subtitle2" sx={{ mt: 2, mb: 1 }}>Recent Async Export Tasks</Typography>
          {renditionExportTasks.length === 0 ? (
            <Alert severity="info">No rendition async export tasks yet.</Alert>
          ) : (
            <TableContainer>
              <Table size="small" aria-label="Rendition resources async export tasks">
                <TableHead>
                  <TableRow>
                    <TableCell>Task ID</TableCell>
                    <TableCell>Status</TableCell>
                    <TableCell>Created</TableCell>
                    <TableCell>Finished</TableCell>
                    <TableCell>Filename</TableCell>
                    <TableCell align="right">Actions</TableCell>
                  </TableRow>
                </TableHead>
                <TableBody>
                {renditionExportTasks.map((task) => {
                  const normalizedStatus = normalizeRenditionExportTaskStatus(task.status);
                  const canCancel = RENDITION_EXPORT_TASK_RUNNING_STATUSES.has(normalizedStatus);
                  const canDownload = RENDITION_EXPORT_TASK_COMPLETED_STATUSES.has(normalizedStatus);
                  const canRetry = RENDITION_EXPORT_TASK_RETRYABLE_STATUSES.has(normalizedStatus);
                  const actionBusy = renditionExportTaskActionId === task.taskId;
                  const retryBusy = actionBusy && renditionExportTaskActionType === 'retry';
                  const cancelBusy = actionBusy && renditionExportTaskActionType === 'cancel';
                  const downloadBusy = actionBusy && renditionExportTaskActionType === 'download';
                  return (
                    <TableRow key={`rendition-export-task-${task.taskId}`}>
                        <TableCell sx={{ maxWidth: 320 }}>
                          <Tooltip title={task.taskId} placement="top-start" arrow>
                            <Typography variant="body2" noWrap>{task.taskId}</Typography>
                          </Tooltip>
                          <Typography variant="caption" color="text.secondary" aria-hidden>
                            {`Updated ${formatLastUpdated(task.updatedAt)} · Timeout ${formatLastUpdated(task.timeoutAt)} · Expires ${formatLastUpdated(task.expiresAt)}`}
                          </Typography>
                          <Typography variant="caption" color="text.secondary" aria-hidden>
                            {`Actor ${task.updatedBy || task.createdBy || 'system'}`}
                          </Typography>
                        </TableCell>
                        <TableCell>
                          <Chip size="small" variant="outlined" label={normalizedStatus} />
                        </TableCell>
                        <TableCell>{formatLastUpdated(task.createdAt)}</TableCell>
                        <TableCell>{formatLastUpdated(task.finishedAt)}</TableCell>
                        <TableCell sx={{ maxWidth: 280 }}>
                          <Tooltip title={task.filename || '—'} placement="top-start" arrow>
                            <Typography variant="body2" noWrap>{task.filename || '—'}</Typography>
                          </Tooltip>
                        </TableCell>
                        <TableCell align="right">
                          <Stack direction="row" gap={0.5} justifyContent="flex-end">
                            {canRetry && (
                              <Button
                                size="small"
                                variant="outlined"
                                color="info"
                                disabled={Boolean(renditionExportTaskActionId)}
                                aria-label={`Retry rendition export task ${task.taskId}`}
                                onClick={() => void handleRetryRenditionExportTask(task.taskId)}
                              >
                                {retryBusy ? 'Retrying...' : 'Retry'}
                              </Button>
                            )}
                            {canCancel && (
                              <Button
                                size="small"
                                variant="outlined"
                                color="warning"
                                disabled={Boolean(renditionExportTaskActionId)}
                                aria-label={`Cancel rendition export task ${task.taskId}`}
                                onClick={() => void handleCancelRenditionExportTask(task.taskId)}
                              >
                                {cancelBusy ? 'Cancelling...' : 'Cancel'}
                              </Button>
                            )}
                            {canDownload && (
                              <Button
                                size="small"
                                variant="outlined"
                                startIcon={<DownloadIcon />}
                                disabled={Boolean(renditionExportTaskActionId)}
                                aria-label={`Download rendition export task ${task.taskId}`}
                                onClick={() => void handleDownloadRenditionExportTask(task)}
                              >
                                {downloadBusy ? 'Downloading...' : 'Download'}
                              </Button>
                            )}
                          </Stack>
                        </TableCell>
                      </TableRow>
                    );
                  })}
                </TableBody>
              </Table>
            </TableContainer>
          )}

          <Typography variant="subtitle2" sx={{ mt: 2, mb: 1 }}>Rendition Resources</Typography>
          {renditionResources.length === 0 ? (
            <Alert severity="info">No rendition resources in current window sample.</Alert>
          ) : (
            <TableContainer>
              <Table size="small" aria-label="Rendition resources">
                <TableHead>
                  <TableRow>
                    <TableCell>Name</TableCell>
                    <TableCell>Status</TableCell>
                    <TableCell>Mime</TableCell>
                    <TableCell>Reason</TableCell>
                    <TableCell>Updated</TableCell>
                    <TableCell align="right">Actions</TableCell>
                  </TableRow>
                </TableHead>
                <TableBody>
                  {renditionResources.map((resource, index) => {
                    const rowKey = [
                      resource.name || 'resource',
                      resource.status || 'unknown',
                      resource.updatedAt || String(index),
                    ].join('|');
                    const documentId = resource.documentId || '';
                    const actionBusy = Boolean(documentId) && renditionResourceActionId === documentId;
                    const normalizedStatus = (resource.status || '').toUpperCase();
                    const disableRetry = !documentId || Boolean(renditionResourceActionId) || normalizedStatus === 'UNSUPPORTED';
                    const disableForce = !documentId || Boolean(renditionResourceActionId);
                    return (
                      <TableRow key={rowKey}>
                        <TableCell sx={{ maxWidth: 300 }}>
                          <Tooltip title={resource.name || '—'} placement="top-start" arrow>
                            <Typography variant="body2" noWrap>{resource.name || '—'}</Typography>
                          </Tooltip>
                        </TableCell>
                        <TableCell>{resource.status || '—'}</TableCell>
                        <TableCell>{resource.mimeType || '—'}</TableCell>
                        <TableCell sx={{ maxWidth: 460 }}>
                          <Tooltip title={resource.reason || '—'} placement="top-start" arrow>
                            <Typography variant="body2" noWrap>{resource.reason || '—'}</Typography>
                          </Tooltip>
                        </TableCell>
                        <TableCell>{formatLastUpdated(resource.updatedAt)}</TableCell>
                        <TableCell align="right">
                          <Stack direction="row" gap={0.5} justifyContent="flex-end">
                            <Button
                              size="small"
                              variant="outlined"
                              startIcon={<RetryIcon />}
                              disabled={disableRetry}
                              aria-label={`Retry rendition resource ${documentId || rowKey}`}
                              onClick={() => void handleQueueRenditionResource(resource, false)}
                            >
                              {actionBusy ? 'Queuing...' : 'Retry'}
                            </Button>
                            <Button
                              size="small"
                              variant="outlined"
                              startIcon={<ForceIcon />}
                              disabled={disableForce}
                              aria-label={`Force rebuild rendition resource ${documentId || rowKey}`}
                              onClick={() => void handleQueueRenditionResource(resource, true)}
                            >
                              {actionBusy ? 'Queuing...' : 'Force'}
                            </Button>
                          </Stack>
                        </TableCell>
                      </TableRow>
                    );
                  })}
                </TableBody>
              </Table>
            </TableContainer>
          )}
        </Paper>
      )}

      <Paper sx={{ p: 2, mb: 2 }}>
        <Stack direction="row" alignItems="center" justifyContent="space-between" gap={1} flexWrap="wrap" mb={1}>
          <Typography variant="h6">Ops Recovery Dry-run</Typography>
          <Stack direction="row" gap={1} alignItems="center" flexWrap="wrap">
            <Chip size="small" label={`Window ${windowDays === 0 ? 'All time' : `${windowDays}d`}`} variant="outlined" />
            <Button
              variant="outlined"
              size="small"
              onClick={() => void handleRunGlobalDryRun()}
              disabled={dryRunLoading}
            >
              {dryRunLoading ? 'Running...' : 'Run Dry-run'}
            </Button>
            <Button
              variant="outlined"
              size="small"
              startIcon={<RetryIcon />}
              onClick={() => void handleCancelActiveRecoveryHistoryExportAsyncTasks()}
              disabled={recoveryHistoryExportAsyncCancellingActive}
              aria-label="Cancel active ops recovery async export tasks"
            >
              {recoveryHistoryExportAsyncCancellingActive ? 'Cancelling active...' : 'Cancel active tasks'}
            </Button>
            <Button
              variant="outlined"
              size="small"
              startIcon={<ForceIcon />}
              onClick={() => void handleExecuteDryRunPlan()}
              disabled={!canExecuteDryRunPlan}
            >
              {dryRunExecuting ? 'Executing...' : 'Execute Recovery'}
            </Button>
          </Stack>
        </Stack>
        <Stack direction="row" gap={1} flexWrap="wrap" alignItems="center" mb={2}>
          <FormControl size="small" sx={{ minWidth: 180 }}>
            <Select
              aria-label="Recovery dry-run mode"
              value={dryRunMode}
              onChange={(event) => setDryRunMode(event.target.value as (typeof DRY_RUN_MODE_OPTIONS)[number]['value'])}
            >
              {DRY_RUN_MODE_OPTIONS.map((option) => (
                <MenuItem key={option.value} value={option.value}>
                  {option.label}
                </MenuItem>
              ))}
            </Select>
          </FormControl>
          {dryRunMode !== 'QUEUE_BY_WINDOW' && (
            <TextField
              size="small"
              placeholder={dryRunMode === 'QUEUE_BY_REASON' ? 'Failure reason (required)' : 'Failure reason (optional)'}
              value={dryRunReason}
              onChange={(event) => setDryRunReason(event.target.value)}
              sx={{ minWidth: 280, flex: '1 1 280px' }}
            />
          )}
          <FormControl size="small" sx={{ minWidth: 150 }}>
            <Select
              aria-label="Recovery dry-run category"
              value={dryRunCategory}
              onChange={(event) => setDryRunCategory(event.target.value as (typeof DRY_RUN_CATEGORY_OPTIONS)[number])}
            >
              {DRY_RUN_CATEGORY_OPTIONS.map((option) => (
                <MenuItem key={option} value={option}>
                  {`Category ${option}`}
                </MenuItem>
              ))}
            </Select>
          </FormControl>
          <FormControl size="small" sx={{ minWidth: 130 }}>
            <Select
              aria-label="Recovery dry-run retryable"
              value={dryRunRetryable}
              onChange={(event) => setDryRunRetryable(event.target.value as (typeof DRY_RUN_RETRYABLE_OPTIONS)[number])}
            >
              {DRY_RUN_RETRYABLE_OPTIONS.map((option) => (
                <MenuItem key={option} value={option}>
                  {`Retryable ${option}`}
                </MenuItem>
              ))}
            </Select>
          </FormControl>
          <TextField
            size="small"
            type="number"
            label="Max docs"
            value={dryRunMaxDocuments}
            onChange={(event) => setDryRunMaxDocuments(Number(event.target.value || 0))}
            inputProps={{ min: 1, max: 500, step: 1, 'aria-label': 'Recovery dry-run max docs' }}
            sx={{ width: 130 }}
          />
          {dryRunMode !== 'CLEAR_BY_FILTER' && (
            <Stack direction="row" alignItems="center" spacing={0}>
              <Checkbox
                size="small"
                checked={dryRunForce}
                onChange={(event) => setDryRunForce(event.target.checked)}
                inputProps={{ 'aria-label': 'Recovery dry-run force' }}
              />
              <Typography variant="body2">Force</Typography>
            </Stack>
          )}
        </Stack>

        {!dryRunResult ? (
          <Alert severity="info">Run dry-run to estimate queue/skip/failure outcomes before batch recovery.</Alert>
        ) : (
          <>
            {dryRunPlanStale && (
              <Alert severity="warning" sx={{ mb: 1 }}>
                Dry-run plan is stale. Run Dry-run again before Execute Recovery.
              </Alert>
            )}
            <Stack direction="row" gap={1} flexWrap="wrap" mb={2}>
              <Chip size="small" label={`Mode ${dryRunResult.mode}`} variant="outlined" />
              <Chip size="small" label={`Matched ${dryRunResult.matched}`} color="info" variant="outlined" />
              <Chip
                size="small"
                label={`${
                  dryRunResult.mode === 'CLEAR_BY_FILTER'
                    ? 'Estimated cleared'
                    : dryRunResult.mode === 'REPLAY_BY_FILTER'
                      ? 'Estimated replay queued'
                      : 'Estimated queued'
                } ${dryRunResult.estimatedQueued}`}
                color="success"
                variant="outlined"
              />
              <Chip size="small" label={`Estimated skipped ${dryRunResult.estimatedSkipped}`} color="warning" variant="outlined" />
              <Chip size="small" label={`Estimated failed ${dryRunResult.estimatedFailed}`} color="error" variant="outlined" />
              <Chip size="small" label={`Scanned ${dryRunResult.scanned}/${dryRunResult.totalCandidates}`} variant="outlined" />
              {dryRunResult.truncated && <Chip size="small" label="Sample truncated" color="warning" />}
            </Stack>

            {dryRunResult.samples.length === 0 ? (
              <Alert severity="info">No dry-run samples in current criteria.</Alert>
            ) : (
              <TableContainer>
                <Table size="small" aria-label="Recovery dry-run samples">
                  <TableHead>
                    <TableRow>
                      <TableCell>Document</TableCell>
                      <TableCell>Category</TableCell>
                      <TableCell>Predicted Outcome</TableCell>
                      <TableCell>Predicted State</TableCell>
                      <TableCell>Reason</TableCell>
                    </TableRow>
                  </TableHead>
                  <TableBody>
                    {dryRunResult.samples.slice(0, 20).map((sample, index) => (
                      <TableRow key={`${sample.documentId || 'sample'}-${index}`}>
                        <TableCell sx={{ maxWidth: 340 }}>
                          <Tooltip title={sample.path || sample.name || sample.documentId} placement="top-start" arrow>
                            <Typography variant="body2" noWrap>{sample.name || sample.documentId}</Typography>
                          </Tooltip>
                        </TableCell>
                        <TableCell>
                          <Chip size="small" label={sample.failureCategory || 'UNKNOWN'} variant="outlined" />
                        </TableCell>
                        <TableCell>{sample.predictedOutcome || '—'}</TableCell>
                        <TableCell>{sample.predictedState || '—'}</TableCell>
                        <TableCell sx={{ maxWidth: 420 }}>
                          <Tooltip title={sample.predictedReason || '—'} placement="top-start" arrow>
                            <Typography variant="body2" noWrap>{sample.predictedReason || '—'}</Typography>
                          </Tooltip>
                        </TableCell>
                      </TableRow>
                    ))}
                  </TableBody>
                </Table>
              </TableContainer>
            )}
          </>
        )}
      </Paper>

      <Paper sx={{ p: 2, mb: 2 }}>
        <Stack direction="row" alignItems="center" justifyContent="space-between" gap={1} flexWrap="wrap" mb={1}>
          <Typography variant="h6">Ops Recovery Execution History</Typography>
          <Stack direction="row" gap={1} alignItems="center" flexWrap="wrap">
            <FormControl size="small" sx={{ minWidth: 190 }}>
              <Select
                aria-label="Ops recovery history mode"
                value={recoveryHistoryMode}
                onChange={(event) => setRecoveryHistoryMode(event.target.value as 'ALL' | RecoveryHistoryMode)}
              >
                {RECOVERY_HISTORY_MODE_OPTIONS.map((option) => (
                  <MenuItem key={option.value} value={option.value}>
                    {option.label}
                  </MenuItem>
                ))}
              </Select>
            </FormControl>
            <FormControl size="small" sx={{ minWidth: 250 }}>
              <Select
                aria-label="Ops recovery history event type"
                value={recoveryHistoryEventType}
                onChange={(event) => setRecoveryHistoryEventType(event.target.value as 'ALL' | RecoveryHistoryEventType)}
              >
                {RECOVERY_HISTORY_EVENT_TYPE_OPTIONS.map((option) => (
                  <MenuItem key={option.value} value={option.value}>
                    {option.label}
                  </MenuItem>
                ))}
              </Select>
            </FormControl>
            <Chip size="small" label={`Window ${windowDays === 0 ? 'All time' : `${windowDays}d`}`} variant="outlined" />
            {recoveryHistoryMode !== 'ALL' && (
              <Chip size="small" label={`Mode ${recoveryHistoryMode}`} color="info" variant="outlined" />
            )}
            {recoveryHistoryActor.trim() && (
              <Chip size="small" label={`Actor ${recoveryHistoryActor.trim()}`} color="info" variant="outlined" />
            )}
            {recoveryHistoryEventType !== 'ALL' && (
              <Chip size="small" label={`Event ${recoveryHistoryEventType}`} color="info" variant="outlined" />
            )}
            {recoveryHistoryAutoRefresh && (
              <Chip size="small" label={`Auto refresh ${recoveryHistoryAutoRefreshSeconds}s`} color="success" variant="outlined" />
            )}
            <Chip size="small" label={`Events ${recoveryHistory.length}/${recoveryHistoryTotal}`} variant="outlined" />
            <Chip
              size="small"
              label={`Page ${Math.min(recoveryHistoryPage + 1, Math.max(recoveryHistoryTotalPages, 1))}/${Math.max(recoveryHistoryTotalPages, 1)}`}
              variant="outlined"
            />
            <Button
              variant="outlined"
              size="small"
              onClick={() => setRecoveryHistoryPage((current) => Math.max(0, current - 1))}
              disabled={loading || recoveryHistoryPage <= 0}
            >
              Prev page
            </Button>
            <Button
              variant="outlined"
              size="small"
              onClick={() => setRecoveryHistoryPage((current) => current + 1)}
              disabled={loading || recoveryHistoryTotalPages <= 0 || recoveryHistoryPage >= recoveryHistoryTotalPages - 1}
            >
              Next page
            </Button>
            <Button
              variant="outlined"
              size="small"
              startIcon={<RefreshIcon />}
              onClick={() => void loadFailures()}
              disabled={loading}
            >
              Refresh history
            </Button>
            <FormControl size="small" sx={{ minWidth: 110 }}>
              <Select
                aria-label="Ops recovery auto refresh seconds"
                value={recoveryHistoryAutoRefreshSeconds}
                onChange={(event) => {
                  setRecoveryHistoryAutoRefreshSeconds(event.target.value as (typeof RECOVERY_HISTORY_AUTO_REFRESH_OPTIONS)[number]);
                }}
              >
                {RECOVERY_HISTORY_AUTO_REFRESH_OPTIONS.map((seconds) => (
                  <MenuItem key={seconds} value={seconds}>
                    {seconds}s
                  </MenuItem>
                ))}
              </Select>
            </FormControl>
            <Button
              variant={recoveryHistoryAutoRefresh ? 'contained' : 'outlined'}
              size="small"
              onClick={() => setRecoveryHistoryAutoRefresh((current) => !current)}
            >
              {recoveryHistoryAutoRefresh ? 'Auto Refresh On' : 'Auto Refresh Off'}
            </Button>
            <Button
              variant="outlined"
              size="small"
              startIcon={<DownloadIcon />}
              onClick={() => void handleExportRecoveryHistoryCsv()}
            >
              Export History CSV
            </Button>
            <Button
              variant="outlined"
              size="small"
              startIcon={<DownloadIcon />}
              onClick={() => void handleExportRecoveryHistorySummaryCsv()}
            >
              Export Summary CSV
            </Button>
            <Button
              variant="outlined"
              size="small"
              startIcon={<DownloadIcon />}
              onClick={() => void handleExportRecoveryHistoryTrendCsv()}
            >
              Export Trend CSV
            </Button>
            <Button
              variant="outlined"
              size="small"
              startIcon={<DownloadIcon />}
              onClick={() => void handleExportRecoveryHistoryCompareCsv()}
            >
              Export Compare CSV
            </Button>
            <Button
              variant="outlined"
              size="small"
              startIcon={<DownloadIcon />}
              onClick={() => void handleExportRecoveryHistoryCompareActorsCsv()}
            >
              Export Actor Compare CSV
            </Button>
            <Button
              variant="outlined"
              size="small"
              startIcon={<DownloadIcon />}
              onClick={() => void handleExportRecoveryHistoryCompareBreakdownCsv()}
            >
              Export Compare Breakdown CSV
            </Button>
            <FormControl size="small" sx={{ minWidth: 210 }}>
              <Select
                aria-label="Ops recovery async export type"
                value={recoveryHistoryExportAsyncType}
                onChange={(event) => {
                  setRecoveryHistoryExportAsyncType(event.target.value as RecoveryHistoryExportAsyncType);
                }}
              >
                {RECOVERY_HISTORY_EXPORT_ASYNC_TYPE_OPTIONS.map((option) => (
                  <MenuItem key={option.value} value={option.value}>
                    {option.label}
                  </MenuItem>
                ))}
              </Select>
            </FormControl>
            <FormControl size="small" sx={{ minWidth: 210 }}>
              <Select
                aria-label="Ops recovery async task filter type"
                value={recoveryHistoryExportAsyncFilterType}
                onChange={(event) => {
                  setRecoveryHistoryExportAsyncFilterType(
                    event.target.value as 'ALL' | RecoveryHistoryExportAsyncType
                  );
                }}
              >
                <MenuItem value="ALL">All task types</MenuItem>
                {RECOVERY_HISTORY_EXPORT_ASYNC_TYPE_OPTIONS.map((option) => (
                  <MenuItem key={`task-filter-${option.value}`} value={option.value}>
                    {option.label}
                  </MenuItem>
                ))}
              </Select>
            </FormControl>
            <FormControl size="small" sx={{ minWidth: 190 }}>
              <Select
                aria-label="Ops recovery async task filter status"
                value={recoveryHistoryExportAsyncFilterStatus}
                onChange={(event) => {
                  setRecoveryHistoryExportAsyncFilterStatus(
                    event.target.value as 'ALL' | RecoveryHistoryExportAsyncStatusFilter
                  );
                }}
              >
                {RECOVERY_HISTORY_EXPORT_ASYNC_STATUS_FILTER_OPTIONS.map((option) => (
                  <MenuItem key={`task-filter-status-${option.value}`} value={option.value}>
                    {option.label}
                  </MenuItem>
                ))}
              </Select>
            </FormControl>
            <Button
              variant="outlined"
              size="small"
              startIcon={<DownloadIcon />}
              onClick={() => void handleStartRecoveryHistoryExportAsync()}
              disabled={recoveryHistoryExportAsyncStarting}
              aria-label="Start ops recovery async export"
            >
              {recoveryHistoryExportAsyncStarting ? 'Starting...' : 'Start Async Export'}
            </Button>
            <Button
              variant="outlined"
              size="small"
              startIcon={<RefreshIcon />}
              onClick={() => void loadRecoveryHistoryExportAsyncTasks(false)}
              disabled={recoveryHistoryExportAsyncTasksLoading}
              aria-label="Refresh ops recovery async export tasks"
            >
              {recoveryHistoryExportAsyncTasksLoading ? 'Refreshing tasks...' : 'Refresh async tasks'}
            </Button>
            <Button
              variant="outlined"
              size="small"
              startIcon={<ForceIcon />}
              onClick={() => void handleCleanupRecoveryHistoryExportAsyncTasks()}
              disabled={recoveryHistoryExportAsyncCleaning}
              aria-label="Cleanup ops recovery async export tasks"
            >
              {recoveryHistoryExportAsyncCleaning ? 'Cleaning...' : 'Cleanup async tasks'}
            </Button>
          </Stack>
        </Stack>
        <Stack direction="row" gap={1} alignItems="center" flexWrap="wrap" mb={1.5}>
          <TextField
            size="small"
            label="Actor filter"
            placeholder="e.g. admin"
            value={recoveryHistoryActor}
            onChange={(event) => setRecoveryHistoryActor(event.target.value)}
            sx={{ minWidth: 220 }}
          />
        </Stack>
        <Stack direction="row" gap={1} alignItems="center" flexWrap="wrap" mb={1}>
          <Chip
            size="small"
            variant="outlined"
            label={`Async total ${recoveryHistoryExportAsyncSummary?.totalCount ?? 0}`}
          />
          <Chip
            size="small"
            variant="outlined"
            color="info"
            label={`Active ${recoveryHistoryExportAsyncSummary?.activeCount ?? 0}`}
          />
          <Chip
            size="small"
            variant="outlined"
            color="success"
            label={`Completed ${recoveryHistoryExportAsyncSummary?.completedCount ?? 0}`}
          />
          <Chip
            size="small"
            variant="outlined"
            color="warning"
            label={`Cancelled ${recoveryHistoryExportAsyncSummary?.cancelledCount ?? 0}`}
          />
          <Chip
            size="small"
            variant="outlined"
            color="error"
            label={`Failed ${recoveryHistoryExportAsyncSummary?.failedCount ?? 0}`}
          />
          <Chip
            size="small"
            variant="outlined"
            color="warning"
            label={`Timed out ${recoveryHistoryExportAsyncSummary?.timedOutCount ?? 0}`}
          />
          <Chip
            size="small"
            variant="outlined"
            label={`Expired ${recoveryHistoryExportAsyncSummary?.expiredCount ?? 0}`}
          />
          {recoveryHistoryExportAsyncSummaryLoading && (
            <Chip size="small" variant="outlined" label="Refreshing async summary..." />
          )}
          <Button
            variant="text"
            size="small"
            startIcon={<RefreshIcon />}
            onClick={() => void handleRefreshRecoveryHistoryExportAsyncSummary(false)}
            disabled={recoveryHistoryExportAsyncSummaryLoading}
            aria-label="Refresh ops recovery async export summary"
          >
            Refresh summary
          </Button>
        </Stack>
        <Typography variant="subtitle2" sx={{ mb: 1 }}>Async Export Tasks</Typography>
        {recoveryHistoryExportAsyncTasks.length === 0 ? (
          <Alert severity="info" sx={{ mb: 1.5 }}>No ops recovery async export tasks yet.</Alert>
        ) : (
          <TableContainer sx={{ mb: 1.5 }}>
            <Table size="small" aria-label="Ops recovery async export tasks">
              <TableHead>
                <TableRow>
                  <TableCell>Task ID</TableCell>
                  <TableCell>Status</TableCell>
                  <TableCell>Created</TableCell>
                  <TableCell>Finished</TableCell>
                  <TableCell>Filename</TableCell>
                  <TableCell align="right">Actions</TableCell>
                </TableRow>
              </TableHead>
              <TableBody>
                {recoveryHistoryExportAsyncTasks.map((task) => {
                  const normalizedStatus = normalizeRecoveryHistoryExportAsyncStatus(task.status);
                  const canCancel = RECOVERY_HISTORY_EXPORT_ASYNC_RUNNING_STATUSES.has(normalizedStatus);
                  const canDownload = RECOVERY_HISTORY_EXPORT_ASYNC_COMPLETED_STATUSES.has(normalizedStatus);
                  const canRetry = RECOVERY_HISTORY_EXPORT_ASYNC_RETRYABLE_STATUSES.has(normalizedStatus);
                  const actionBusy = recoveryHistoryExportAsyncActionTaskId === task.taskId;
                  const retryBusy = actionBusy && recoveryHistoryExportAsyncActionType === 'retry';
                  const cancelBusy = actionBusy && recoveryHistoryExportAsyncActionType === 'cancel';
                  const downloadBusy = actionBusy && recoveryHistoryExportAsyncActionType === 'download';
                  return (
                    <TableRow key={`ops-recovery-export-async-${task.taskId}`}>
                      <TableCell sx={{ maxWidth: 300 }}>
                        <Tooltip title={task.taskId} placement="top-start" arrow>
                          <Typography variant="body2" noWrap>{task.taskId}</Typography>
                        </Tooltip>
                        <Typography variant="caption" color="text.secondary">
                          {task.exportType || 'HISTORY'}
                        </Typography>
                        <Typography variant="caption" color="text.secondary" aria-hidden>
                          {`Updated ${formatLastUpdated(task.updatedAt)} · Timeout ${formatLastUpdated(task.timeoutAt)} · Expires ${formatLastUpdated(task.expiresAt)}`}
                        </Typography>
                        <Typography variant="caption" color="text.secondary" aria-hidden>
                          {`Actor ${task.updatedBy || task.createdBy || 'system'}`}
                        </Typography>
                      </TableCell>
                      <TableCell>
                        <Chip size="small" variant="outlined" label={normalizedStatus} />
                      </TableCell>
                      <TableCell>{formatLastUpdated(task.createdAt)}</TableCell>
                      <TableCell>{formatLastUpdated(task.finishedAt)}</TableCell>
                      <TableCell sx={{ maxWidth: 280 }}>
                        <Tooltip title={task.filename || '—'} placement="top-start" arrow>
                          <Typography variant="body2" noWrap>{task.filename || '—'}</Typography>
                        </Tooltip>
                      </TableCell>
                      <TableCell align="right">
                        <Stack direction="row" gap={0.5} justifyContent="flex-end">
                          {canRetry && (
                            <Button
                              size="small"
                              variant="outlined"
                              color="info"
                              disabled={Boolean(recoveryHistoryExportAsyncActionTaskId)}
                              aria-label={`Retry ops recovery async export task ${task.taskId}`}
                              onClick={() => void handleRetryRecoveryHistoryExportAsyncTask(task.taskId)}
                            >
                              {retryBusy ? 'Retrying...' : 'Retry'}
                            </Button>
                          )}
                          {canCancel && (
                            <Button
                              size="small"
                              variant="outlined"
                              color="warning"
                              disabled={Boolean(recoveryHistoryExportAsyncActionTaskId)}
                              aria-label={`Cancel ops recovery async export task ${task.taskId}`}
                              onClick={() => void handleCancelRecoveryHistoryExportAsyncTask(task.taskId)}
                            >
                              {cancelBusy ? 'Cancelling...' : 'Cancel'}
                            </Button>
                          )}
                          {canDownload && (
                            <Button
                              size="small"
                              variant="outlined"
                              startIcon={<DownloadIcon />}
                              disabled={Boolean(recoveryHistoryExportAsyncActionTaskId)}
                              aria-label={`Download ops recovery async export task ${task.taskId}`}
                              onClick={() => void handleDownloadRecoveryHistoryExportAsyncTask(task)}
                            >
                              {downloadBusy ? 'Downloading...' : 'Download'}
                            </Button>
                          )}
                        </Stack>
                      </TableCell>
                    </TableRow>
                  );
                })}
              </TableBody>
            </Table>
          </TableContainer>
        )}
        <Stack direction="row" gap={1} alignItems="center" flexWrap="wrap" mb={1.5}>
          <Chip size="small" label={`Summary total ${recoveryHistorySummaryTotal}`} variant="outlined" />
          {recoveryHistorySummary.length === 0 ? (
            <Chip size="small" label="No summary in current filters" variant="outlined" />
          ) : (
            recoveryHistorySummary.slice(0, 6).map((item) => (
              <Tooltip key={`${item.eventType}-${item.mode}`} title={item.eventType} placement="top" arrow>
                <Chip
                  size="small"
                  variant="outlined"
                  color="info"
                  label={`${item.mode || 'UNKNOWN'} ${item.count}`}
                />
              </Tooltip>
            ))
          )}
        </Stack>
        <Stack direction="row" gap={1} alignItems="center" flexWrap="wrap" mb={1.5}>
          {recoveryHistoryActorSummary.length === 0 ? (
            <Chip size="small" label="No actor summary in current filters" variant="outlined" />
          ) : (
            recoveryHistoryActorSummary.slice(0, 4).map((item) => (
              <Chip
                key={`actor-${item.actor}-${item.count}`}
                size="small"
                variant="outlined"
                color="secondary"
                label={`Top actor ${item.actor || 'unknown'} ${item.count}`}
              />
            ))
          )}
        </Stack>
        <Stack direction="row" gap={1} alignItems="center" flexWrap="wrap" mb={1.5}>
          <FormControl size="small" sx={{ minWidth: 180 }}>
            <Select
              aria-label="Ops recovery actor compare sort"
              value={recoveryHistoryCompareActorSort}
              onChange={(event) => setRecoveryHistoryCompareActorSort(event.target.value as RecoveryHistoryCompareActorSort)}
            >
              {RECOVERY_HISTORY_COMPARE_ACTOR_SORT_OPTIONS.map((option) => (
                <MenuItem key={option.value} value={option.value}>
                  {option.label}
                </MenuItem>
              ))}
            </Select>
          </FormControl>
          <FormControl size="small" sx={{ minWidth: 120 }}>
            <Select
              aria-label="Ops recovery actor compare top"
              value={recoveryHistoryCompareActorLimit}
              onChange={(event) => {
                setRecoveryHistoryCompareActorLimit(
                  event.target.value as (typeof RECOVERY_HISTORY_COMPARE_ACTOR_LIMIT_OPTIONS)[number]
                );
              }}
            >
              {RECOVERY_HISTORY_COMPARE_ACTOR_LIMIT_OPTIONS.map((option) => (
                <MenuItem key={option} value={option}>
                  {option}
                </MenuItem>
              ))}
            </Select>
          </FormControl>
          <Chip size="small" label={`Actor top ${recoveryHistoryCompareActorLimit}`} variant="outlined" />
          {recoveryHistoryCompareActorLimited && (
            <Chip
              size="small"
              label={`Actor showing ${recoveryHistoryCompareActors.length}/${recoveryHistoryCompareActorTotalItems}`}
              color="warning"
              variant="outlined"
            />
          )}
        </Stack>
        <Stack direction="row" gap={1} alignItems="center" flexWrap="wrap" mb={1.5}>
          {recoveryHistoryCompareActors.length === 0 ? (
            <Chip
              size="small"
              label={recoveryHistoryCompareAvailable ? 'No actor compare in current filters' : 'Actor compare unavailable for current window'}
              variant="outlined"
            />
          ) : (
            recoveryHistoryCompareActors.map((item) => (
              <Tooltip
                key={`actor-compare-${item.actor}`}
                title={`Current ${item.currentCount}, Previous ${item.previousCount}${item.deltaPercent !== null && item.deltaPercent !== undefined ? `, Delta% ${item.deltaPercent.toFixed(1)}%` : ''}`}
                placement="top"
                arrow
              >
                <Chip
                  size="small"
                  variant="outlined"
                  color={item.delta > 0 ? 'success' : item.delta < 0 ? 'warning' : 'default'}
                  label={`Actor Δ ${item.actor || 'unknown'} ${item.delta >= 0 ? '+' : ''}${item.delta}`}
                />
              </Tooltip>
            ))
          )}
        </Stack>
        <Stack direction="row" gap={1} alignItems="center" flexWrap="wrap" mb={1.5}>
          {recoveryHistoryCompareAvailable ? (
            <>
              <Chip size="small" label={`Compare current ${recoveryHistoryCompareCurrent}`} variant="outlined" />
              <Chip size="small" label={`Previous ${recoveryHistoryComparePrevious}`} variant="outlined" />
              <Chip
                size="small"
                color={recoveryHistoryCompareDelta >= 0 ? 'success' : 'warning'}
                variant="outlined"
                label={`Delta ${recoveryHistoryCompareDelta >= 0 ? '+' : ''}${recoveryHistoryCompareDelta}`}
              />
              {recoveryHistoryCompareDeltaPercent !== null && (
                <Chip
                  size="small"
                  variant="outlined"
                  color={recoveryHistoryCompareDeltaPercent >= 0 ? 'success' : 'warning'}
                  label={`Delta% ${recoveryHistoryCompareDeltaPercent >= 0 ? '+' : ''}${recoveryHistoryCompareDeltaPercent.toFixed(1)}%`}
                />
              )}
              {recoveryHistoryCompareTruncated && (
                <Chip size="small" label="Compare truncated" color="warning" variant="outlined" />
              )}
            </>
          ) : (
            <Chip size="small" label="Compare unavailable for current window" variant="outlined" />
          )}
        </Stack>
        <Stack direction="row" gap={1} alignItems="center" flexWrap="wrap" mb={1.5}>
          <FormControl size="small" sx={{ minWidth: 180 }}>
            <Select
              aria-label="Ops recovery compare breakdown sort"
              value={recoveryHistoryCompareBreakdownSort}
              onChange={(event) => setRecoveryHistoryCompareBreakdownSort(event.target.value as RecoveryHistoryCompareBreakdownSort)}
            >
              {RECOVERY_HISTORY_COMPARE_BREAKDOWN_SORT_OPTIONS.map((option) => (
                <MenuItem key={option.value} value={option.value}>
                  {option.label}
                </MenuItem>
              ))}
            </Select>
          </FormControl>
          <FormControl size="small" sx={{ minWidth: 120 }}>
            <Select
              aria-label="Ops recovery compare breakdown top"
              value={recoveryHistoryCompareBreakdownLimit}
              onChange={(event) => {
                setRecoveryHistoryCompareBreakdownLimit(
                  event.target.value as (typeof RECOVERY_HISTORY_COMPARE_BREAKDOWN_LIMIT_OPTIONS)[number]
                );
              }}
            >
              {RECOVERY_HISTORY_COMPARE_BREAKDOWN_LIMIT_OPTIONS.map((option) => (
                <MenuItem key={option} value={option}>
                  {option}
                </MenuItem>
              ))}
            </Select>
          </FormControl>
          <Chip size="small" label={`Top ${recoveryHistoryCompareBreakdownLimit}`} variant="outlined" />
          {recoveryHistoryCompareBreakdownLimited && (
            <Chip
              size="small"
              label={`Showing ${recoveryHistoryCompareBreakdown.length}/${recoveryHistoryCompareBreakdownTotalItems}`}
              color="warning"
              variant="outlined"
            />
          )}
        </Stack>
        <Stack direction="row" gap={1} alignItems="center" flexWrap="wrap" mb={1.5}>
          {recoveryHistoryCompareBreakdown.length === 0 ? (
            <Chip
              size="small"
              label={recoveryHistoryCompareAvailable ? 'No compare breakdown in current filters' : 'Compare breakdown unavailable for current window'}
              variant="outlined"
            />
          ) : (
            recoveryHistoryCompareBreakdown.map((item) => (
              <Tooltip
                key={`compare-breakdown-${item.eventType}`}
                title={`Current ${item.currentCount}, Previous ${item.previousCount}${item.deltaPercent !== null && item.deltaPercent !== undefined ? `, Delta% ${item.deltaPercent.toFixed(1)}%` : ''}`}
                placement="top"
                arrow
              >
                <Chip
                  size="small"
                  variant="outlined"
                  color={item.delta > 0 ? 'success' : item.delta < 0 ? 'warning' : 'default'}
                  label={`Δ ${item.mode || 'UNKNOWN'} ${item.delta >= 0 ? '+' : ''}${item.delta}`}
                />
              </Tooltip>
            ))
          )}
        </Stack>
        <Stack direction="row" gap={1} alignItems="center" flexWrap="wrap" mb={1.5}>
          <Chip size="small" label={`Trend total ${recoveryHistoryTrendTotal}`} variant="outlined" />
          {recoveryHistoryTrendTruncated && (
            <Chip size="small" label="Trend truncated" color="warning" variant="outlined" />
          )}
          {recoveryHistoryTrend.length === 0 ? (
            <Chip size="small" label="No trend in current filters" variant="outlined" />
          ) : (
            recoveryHistoryTrend.slice(0, 7).map((item) => (
              <Chip
                key={`trend-${item.day}-${item.count}`}
                size="small"
                variant="outlined"
                color="default"
                label={`Trend ${item.day} ${item.count}`}
              />
            ))
          )}
        </Stack>

        {recoveryHistory.length === 0 ? (
          <Alert severity="info">No ops recovery history events in current window.</Alert>
        ) : (
          <TableContainer>
            <Table size="small" aria-label="Ops recovery history">
              <TableHead>
                <TableRow>
                  <TableCell>Time</TableCell>
                  <TableCell>Mode</TableCell>
                  <TableCell>Actor</TableCell>
                  <TableCell>Event Type</TableCell>
                  <TableCell>Details</TableCell>
                </TableRow>
              </TableHead>
              <TableBody>
                {recoveryHistory.map((entry, index) => (
                  <TableRow key={`${entry.id || entry.eventType || 'ops'}-${index}`}>
                    <TableCell>{formatLastUpdated(entry.eventTime)}</TableCell>
                    <TableCell>
                      <Chip size="small" label={entry.mode || 'UNKNOWN'} variant="outlined" />
                    </TableCell>
                    <TableCell>{entry.actor || '—'}</TableCell>
                    <TableCell>{entry.eventType || '—'}</TableCell>
                    <TableCell sx={{ maxWidth: 680 }}>
                      <Tooltip title={entry.details || '—'} placement="top-start" arrow>
                        <Typography variant="body2" noWrap>{entry.details || '—'}</Typography>
                      </Tooltip>
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </TableContainer>
        )}
      </Paper>

      {cadFailoverDiagnostics && (
        <Paper sx={{ p: 2, mb: 2 }}>
          <Stack direction="row" alignItems="center" justifyContent="space-between" gap={1} flexWrap="wrap" mb={1}>
            <Typography variant="h6">CAD Failover Diagnostics</Typography>
            <Stack direction="row" gap={1} flexWrap="wrap">
              <Chip
                size="small"
                label={cadFailoverDiagnostics.cadPreviewEnabled ? 'CAD preview enabled' : 'CAD preview disabled'}
                color={cadFailoverDiagnostics.cadPreviewEnabled ? 'success' : 'default'}
                variant={cadFailoverDiagnostics.cadPreviewEnabled ? 'outlined' : 'filled'}
              />
              <Chip
                size="small"
                label={cadFailoverDiagnostics.configured ? `Endpoints ${cadFailoverDiagnostics.endpoints.length}` : 'No endpoint configured'}
                color={cadFailoverDiagnostics.configured ? 'info' : 'warning'}
                variant="outlined"
              />
              <Chip
                size="small"
                label={cadFailoverDiagnostics.circuitBreakerEnabled ? 'Circuit breaker on' : 'Circuit breaker off'}
                color={cadFailoverDiagnostics.circuitBreakerEnabled ? 'success' : 'default'}
                variant={cadFailoverDiagnostics.circuitBreakerEnabled ? 'outlined' : 'filled'}
              />
              {cadFailoverDiagnostics.circuitBreakerEnabled && (
                <Chip
                  size="small"
                  label={`Threshold ${cadFailoverDiagnostics.circuitFailureThreshold}, Open ${Math.round(
                    cadFailoverDiagnostics.circuitOpenMs / 1000
                  )}s`}
                  color="default"
                  variant="outlined"
                />
              )}
            </Stack>
          </Stack>

          {cadFailoverDiagnostics.endpointStats.length === 0 ? (
            <Alert severity="info">No CAD failover statistics yet. Trigger CAD preview once to populate counters.</Alert>
          ) : (
            <TableContainer>
              <Table size="small" aria-label="CAD failover diagnostics">
                <TableHead>
                  <TableRow>
                    <TableCell>Endpoint</TableCell>
                    <TableCell>Circuit</TableCell>
                    <TableCell align="right">Consecutive Failure</TableCell>
                    <TableCell align="right">Success</TableCell>
                    <TableCell align="right">Failure</TableCell>
                    <TableCell>Open Until</TableCell>
                    <TableCell>Last Success</TableCell>
                    <TableCell>Last Failure</TableCell>
                    <TableCell>Failure Reason</TableCell>
                  </TableRow>
                </TableHead>
                <TableBody>
                  {cadFailoverDiagnostics.endpointStats.map((entry) => (
                    <TableRow key={entry.endpoint}>
                      <TableCell sx={{ maxWidth: 460 }}>
                        <Tooltip title={entry.endpoint} placement="top-start" arrow>
                          <Typography variant="body2" noWrap>{entry.endpoint}</Typography>
                        </Tooltip>
                      </TableCell>
                      <TableCell>
                        <Chip
                          size="small"
                          label={entry.circuitState || 'UNKNOWN'}
                          color={cadCircuitChipColor(entry.circuitState)}
                          variant="outlined"
                        />
                      </TableCell>
                      <TableCell align="right">{entry.consecutiveFailureCount ?? 0}</TableCell>
                      <TableCell align="right">{entry.successCount}</TableCell>
                      <TableCell align="right">{entry.failureCount}</TableCell>
                      <TableCell>{formatLastUpdated(entry.circuitOpenUntil)}</TableCell>
                      <TableCell>{formatLastUpdated(entry.lastSuccessAt)}</TableCell>
                      <TableCell>{formatLastUpdated(entry.lastFailureAt)}</TableCell>
                      <TableCell sx={{ maxWidth: 320 }}>
                        <Tooltip title={entry.lastFailureReason || '—'} placement="top-start" arrow>
                          <Typography variant="body2" noWrap>{entry.lastFailureReason || '—'}</Typography>
                        </Tooltip>
                      </TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            </TableContainer>
          )}
        </Paper>
      )}

      <Paper sx={{ p: 2, mb: 2 }}>
        <Stack direction="row" alignItems="center" justifyContent="space-between" gap={1} flexWrap="wrap" mb={1}>
          <Typography variant="h6">Transform Trace Diagnostics</Typography>
          <Stack direction="row" gap={1} flexWrap="wrap" alignItems="center">
            <FormControl size="small">
              <Select
                aria-label="Transform trace limit"
                value={traceLimit}
                onChange={(event) => setTraceLimit(event.target.value as (typeof TRACE_LIMIT_OPTIONS)[number])}
              >
                {TRACE_LIMIT_OPTIONS.map((option) => (
                  <MenuItem key={option} value={option}>
                    Trace {option}
                  </MenuItem>
                ))}
              </Select>
            </FormControl>
            <TextField
              size="small"
              placeholder="Filter request id (e.g. pv-12)"
              value={traceRequestId}
              onChange={(event) => setTraceRequestId(event.target.value)}
              sx={{ minWidth: 220 }}
            />
            <Chip size="small" label={`Loaded ${transformTraces.length}`} variant="outlined" />
            <Button
              variant="outlined"
              startIcon={<RefreshIcon />}
              onClick={() => void loadFailures()}
              disabled={loading}
            >
              Refresh traces
            </Button>
          </Stack>
        </Stack>

        {transformTraces.length === 0 ? (
          <Alert severity="info">No preview traces found for current filter.</Alert>
        ) : (
          <TableContainer>
            <Table size="small" aria-label="Preview transform traces">
              <TableHead>
                <TableRow>
                  <TableCell>Request ID</TableCell>
                  <TableCell>Document</TableCell>
                  <TableCell>Status</TableCell>
                  <TableCell>Mime Type</TableCell>
                  <TableCell>Started</TableCell>
                  <TableCell>Finished</TableCell>
                  <TableCell align="right">Events</TableCell>
                  <TableCell>Latest</TableCell>
                </TableRow>
              </TableHead>
              <TableBody>
                {transformTraces.map((trace) => {
                  const statusLabel = trace.status || 'RUNNING';
                  const normalizedStatus = statusLabel.toUpperCase();
                  const chipColor =
                    normalizedStatus === 'FAILED'
                      ? 'error'
                      : normalizedStatus === 'RUNNING'
                        ? 'info'
                        : trace.retryNeeded
                          ? 'warning'
                          : 'success';
                  return (
                    <TableRow key={trace.requestId}>
                      <TableCell>
                        <Typography variant="body2">{trace.requestId}</Typography>
                      </TableCell>
                      <TableCell>
                        <Typography variant="body2" noWrap>
                          {trace.documentId || '—'}
                        </Typography>
                      </TableCell>
                      <TableCell>
                        <Chip
                          size="small"
                          label={trace.retryNeeded ? `${statusLabel} (retry)` : statusLabel}
                          color={chipColor}
                          variant="outlined"
                        />
                      </TableCell>
                      <TableCell>
                        <Typography variant="body2" noWrap>
                          {trace.mimeType || '—'}
                        </Typography>
                      </TableCell>
                      <TableCell>{formatLastUpdated(trace.startedAt)}</TableCell>
                      <TableCell>{formatLastUpdated(trace.finishedAt)}</TableCell>
                      <TableCell align="right">{trace.events.length}</TableCell>
                      <TableCell sx={{ maxWidth: 420 }}>
                        <Tooltip title={trace.latestMessage || trace.failureReason || '—'} placement="top-start" arrow>
                          <Typography variant="body2" noWrap>
                            {trace.latestMessage || trace.failureReason || '—'}
                          </Typography>
                        </Tooltip>
                      </TableCell>
                    </TableRow>
                  );
                })}
              </TableBody>
            </Table>
          </TableContainer>
        )}
      </Paper>

      <Paper sx={{ p: 2, mb: 2 }}>
        <Stack direction="row" alignItems="center" justifyContent="space-between" gap={1} flexWrap="wrap" mb={1}>
          <Typography variant="h6">Failure Policy Profiles</Typography>
          <Stack direction="row" alignItems="center" gap={1} flexWrap="wrap">
            <Typography variant="body2" color="text.secondary">
              Tune max attempts, base delay, backoff slope, and quiet period by mime profile.
            </Typography>
            <FormControl size="small" sx={{ minWidth: 190 }}>
              <Select
                aria-label="Policy rollback target version"
                displayEmpty
                value={rollbackTargetVersion === '' ? '' : rollbackTargetVersion}
                onChange={(event) => {
                  const raw = event.target.value;
                  setRollbackTargetVersion(raw === '' ? '' : Number(raw));
                }}
              >
                {rollbackOptions.length === 0 ? (
                  <MenuItem value="">No rollback target</MenuItem>
                ) : (
                  rollbackOptions.map((entry) => (
                    <MenuItem key={entry.version} value={entry.version}>
                      {`Rollback target v${entry.version}`}
                    </MenuItem>
                  ))
                )}
              </Select>
            </FormControl>
            <Button
              size="small"
              variant="outlined"
              startIcon={<RollbackIcon />}
              onClick={() => void handleRollbackPolicy()}
              disabled={Boolean(policySavingKey) || policyRollbacking || rollbackOptions.length === 0}
            >
              {policyRollbacking
                ? 'Rolling back...'
                : (rollbackTargetVersion === '' ? 'Rollback latest' : `Rollback to v${rollbackTargetVersion}`)}
            </Button>
            {policyVersion !== null && (
              <Chip size="small" label={`Policy version v${policyVersion}`} variant="outlined" />
            )}
          </Stack>
        </Stack>

        {failurePolicies.length === 0 ? (
          <Alert severity="info">No policy profiles loaded.</Alert>
        ) : (
          <TableContainer>
            <Table size="small" aria-label="Preview failure policy profiles">
              <TableHead>
                <TableRow>
                  <TableCell>Profile</TableCell>
                  <TableCell align="right">Max Attempts</TableCell>
                  <TableCell align="right">Base Delay (ms)</TableCell>
                  <TableCell align="right">Backoff</TableCell>
                  <TableCell align="right">Quiet Period (ms)</TableCell>
                  <TableCell>Impact Preview</TableCell>
                  <TableCell align="right">Action</TableCell>
                </TableRow>
              </TableHead>
              <TableBody>
                {failurePolicies.map((policy) => {
                  const draft = policyDrafts[policy.key] || toPolicyDraft(policy);
                  const saving = policySavingKey === policy.key;
                  return (
                    <TableRow key={policy.key}>
                      <TableCell>
                        <Stack direction="row" alignItems="center" spacing={1}>
                          <Typography variant="body2">{policy.label}</Typography>
                          {policy.builtIn && <Chip size="small" label="Built-in" variant="outlined" />}
                        </Stack>
                        <Typography variant="caption" color="text.secondary">
                          key: {policy.key}
                        </Typography>
                      </TableCell>
                      <TableCell align="right">
                        <TextField
                          size="small"
                          type="number"
                          value={draft.maxAttempts}
                          onChange={(event) => handlePolicyDraftChange(policy.key, 'maxAttempts', event.target.value)}
                          inputProps={{ min: 1, max: 10, step: 1 }}
                          sx={{ width: 110 }}
                        />
                      </TableCell>
                      <TableCell align="right">
                        <TextField
                          size="small"
                          type="number"
                          value={draft.retryDelayMs}
                          onChange={(event) => handlePolicyDraftChange(policy.key, 'retryDelayMs', event.target.value)}
                          inputProps={{ min: 1000, max: 3600000, step: 1000 }}
                          sx={{ width: 130 }}
                        />
                      </TableCell>
                      <TableCell align="right">
                        <TextField
                          size="small"
                          type="number"
                          value={draft.backoffMultiplier}
                          onChange={(event) => handlePolicyDraftChange(policy.key, 'backoffMultiplier', event.target.value)}
                          inputProps={{ min: 1, max: 10, step: 0.1 }}
                          sx={{ width: 110 }}
                        />
                      </TableCell>
                      <TableCell align="right">
                        <TextField
                          size="small"
                          type="number"
                          value={draft.quietPeriodMs}
                          onChange={(event) => handlePolicyDraftChange(policy.key, 'quietPeriodMs', event.target.value)}
                          inputProps={{ min: 0, max: 86400000, step: 1000 }}
                          sx={{ width: 140 }}
                        />
                      </TableCell>
                      <TableCell sx={{ maxWidth: 260 }}>
                        <Typography variant="body2" noWrap title={formatRetryPlan(draft)}>
                          {formatRetryPlan(draft)}
                        </Typography>
                      </TableCell>
                      <TableCell align="right">
                        <Button
                          size="small"
                          variant="outlined"
                          onClick={() => void handleSavePolicy(policy.key)}
                          disabled={Boolean(policySavingKey)}
                        >
                          {saving ? 'Saving...' : 'Save'}
                        </Button>
                      </TableCell>
                    </TableRow>
                  );
                })}
              </TableBody>
            </Table>
          </TableContainer>
        )}

        <Box mt={2}>
          <Typography variant="subtitle2" gutterBottom>
            Recent Policy Versions
          </Typography>
          {policyHistory.length === 0 ? (
            <Alert severity="info">No policy history available.</Alert>
          ) : (
            <TableContainer>
              <Table size="small" aria-label="Preview failure policy history">
                <TableHead>
                  <TableRow>
                    <TableCell>Version</TableCell>
                    <TableCell>Updated</TableCell>
                    <TableCell>Actor</TableCell>
                    <TableCell>Reason</TableCell>
                    <TableCell align="right">Current</TableCell>
                  </TableRow>
                </TableHead>
                <TableBody>
                  {policyHistory.map((entry) => (
                    <TableRow key={entry.version}>
                      <TableCell>{`v${entry.version}`}</TableCell>
                      <TableCell>{formatLastUpdated(entry.updatedAt)}</TableCell>
                      <TableCell>{entry.actor || '—'}</TableCell>
                      <TableCell sx={{ maxWidth: 420 }}>
                        <Tooltip title={entry.reason || '—'} placement="top-start" arrow>
                          <Typography variant="body2" noWrap>
                            {entry.reason || '—'}
                          </Typography>
                        </Tooltip>
                      </TableCell>
                      <TableCell align="right">
                        {policyVersion === entry.version ? <Chip size="small" label="Current" color="success" variant="outlined" /> : '—'}
                      </TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            </TableContainer>
          )}
        </Box>
      </Paper>

      {failureLedgerDiagnostics && (
        <Paper sx={{ p: 2, mb: 2 }}>
          <Stack direction="row" alignItems="center" justifyContent="space-between" gap={1} flexWrap="wrap" mb={1}>
            <Typography variant="h6">Preview Failure Ledger</Typography>
            <Stack direction="row" gap={1} flexWrap="wrap">
              <Chip
                size="small"
                label={`Sampled ${failureLedgerDiagnostics.sampledEntries}/${failureLedgerDiagnostics.totalEntries}`}
                color={failureLedgerDiagnostics.totalEntries > 0 ? 'warning' : 'default'}
                variant="outlined"
              />
              <Chip
                size="small"
                label={`Window ${failureLedgerDiagnostics.windowDays === 0 ? 'All time' : `${failureLedgerDiagnostics.windowDays}d`}`}
                variant="outlined"
              />
              {failureLedgerDiagnostics.sampleTruncated && (
                <Chip size="small" label="Sample truncated" color="warning" variant="outlined" />
              )}
            </Stack>
          </Stack>

          <Stack direction="row" alignItems="center" gap={1} flexWrap="wrap" mb={2}>
            <TextField
              size="small"
              placeholder="Filter failure ledger entries..."
              value={failureLedgerFilterText}
              onChange={(event) => setFailureLedgerFilterText(event.target.value)}
              sx={{ minWidth: 280, flex: '1 1 280px' }}
            />
            <Chip size="small" label={`Selected ${selectedFailureLedgerIds.length}`} variant="outlined" />
            <Button
              size="small"
              variant="outlined"
              startIcon={<RollbackIcon />}
              disabled={selectedFailureLedgerIds.length === 0 || Boolean(failureLedgerActionId)}
              onClick={() => void handleResetFailureLedgerBatch()}
            >
              Reset Selected
            </Button>
            <Button
              size="small"
              variant="outlined"
              startIcon={<DownloadIcon />}
              onClick={() => void handleExportFailureLedgerCsv()}
            >
              Export Ledger CSV
            </Button>
          </Stack>

          {filteredFailureLedgerItems.length === 0 ? (
            <Alert severity="info">No failure ledger entries in selected window.</Alert>
          ) : (
            <TableContainer>
              <Table size="small" aria-label="Preview failure ledger">
                <TableHead>
                  <TableRow>
                    <TableCell padding="checkbox">
                      <Checkbox
                        size="small"
                        indeterminate={someVisibleFailureLedgerSelected}
                        checked={allVisibleFailureLedgerSelected}
                        onChange={(event) => handleToggleAllFailureLedgerSelection(event.target.checked)}
                        inputProps={{ 'aria-label': 'Select all failure ledger entries' }}
                      />
                    </TableCell>
                    <TableCell>Name</TableCell>
                    <TableCell>Category</TableCell>
                    <TableCell align="right">Failure Count</TableCell>
                    <TableCell>Last Failed At</TableCell>
                    <TableCell>Last Reason</TableCell>
                    <TableCell>Hash State</TableCell>
                    <TableCell align="right">Action</TableCell>
                  </TableRow>
                </TableHead>
                <TableBody>
                  {filteredFailureLedgerItems.map((item) => (
                    <TableRow key={item.documentId}>
                      <TableCell padding="checkbox">
                        <Checkbox
                          size="small"
                          checked={selectedFailureLedgerSet.has(item.documentId)}
                          onChange={(event) => handleToggleFailureLedgerSelection(item.documentId, event.target.checked)}
                          inputProps={{ 'aria-label': `Select failure ledger entry ${item.documentId}` }}
                        />
                      </TableCell>
                      <TableCell sx={{ maxWidth: 280 }}>
                        <Tooltip title={item.path || item.name || item.documentId} placement="top-start" arrow>
                          <Typography variant="body2" noWrap>{item.name || item.documentId}</Typography>
                        </Tooltip>
                      </TableCell>
                      <TableCell>
                        <Stack direction="row" gap={0.5} alignItems="center" flexWrap="wrap">
                          <Chip size="small" label={item.category || 'UNKNOWN'} variant="outlined" />
                          <Chip
                            size="small"
                            label={item.retryable ? 'Retryable' : 'Non-retryable'}
                            color={item.retryable ? 'warning' : 'default'}
                            variant={item.retryable ? 'filled' : 'outlined'}
                          />
                        </Stack>
                      </TableCell>
                      <TableCell align="right">{item.failureCount}</TableCell>
                      <TableCell>{formatLastUpdated(item.failedAt)}</TableCell>
                      <TableCell sx={{ maxWidth: 320 }}>
                        <Tooltip title={item.lastReason || '—'} placement="top-start" arrow>
                          <Typography variant="body2" noWrap>{item.lastReason || '—'}</Typography>
                        </Tooltip>
                      </TableCell>
                      <TableCell>
                        {item.staleByContentChange ? (
                          <Chip size="small" label="Stale by content hash" color="warning" variant="outlined" />
                        ) : (
                          <Chip size="small" label="Current" color="success" variant="outlined" />
                        )}
                      </TableCell>
                      <TableCell align="right">
                        <Button
                          size="small"
                          variant="outlined"
                          startIcon={<RollbackIcon />}
                          disabled={Boolean(failureLedgerActionId)}
                          onClick={() => void handleResetFailureLedger(item.documentId)}
                          aria-label={`Reset failure ledger ${item.documentId}`}
                        >
                          {failureLedgerActionId === item.documentId ? 'Resetting...' : 'Reset'}
                        </Button>
                      </TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            </TableContainer>
          )}
        </Paper>
      )}

      {deadLetterDiagnostics && (
        <Paper sx={{ p: 2, mb: 2 }}>
          <Stack direction="row" alignItems="center" justifyContent="space-between" gap={1} flexWrap="wrap" mb={1}>
            <Typography variant="h6">Preview Dead Letter Queue</Typography>
            <Stack direction="row" gap={1} flexWrap="wrap">
              <Chip
                size="small"
                label={deadLetterDiagnostics.enabled ? 'Dead-letter enabled' : 'Dead-letter disabled'}
                color={deadLetterDiagnostics.enabled ? 'success' : 'default'}
                variant={deadLetterDiagnostics.enabled ? 'outlined' : 'filled'}
              />
              <Chip
                size="small"
                label={`Backend ${deadLetterDiagnostics.backendMode || 'unknown'}`}
                variant="outlined"
              />
              <Chip
                size="small"
                label={`TTL ${formatDeadLetterTtl(deadLetterDiagnostics.ttlMs)}`}
                variant="outlined"
              />
              <Chip
                size="small"
                label={`Entries ${deadLetterDiagnostics.itemCount}/${deadLetterDiagnostics.maxEntries}`}
                color={deadLetterDiagnostics.itemCount > 0 ? 'warning' : 'default'}
                variant="outlined"
              />
            </Stack>
          </Stack>

          <Stack direction="row" alignItems="center" gap={1} flexWrap="wrap" mb={2}>
            <TextField
              size="small"
              placeholder="Filter dead-letter items..."
              value={deadLetterFilterText}
              onChange={(event) => setDeadLetterFilterText(event.target.value)}
              sx={{ minWidth: 280, flex: '1 1 280px' }}
            />
            <Chip size="small" label={`Selected ${selectedDeadLetterEntryKeys.length}`} variant="outlined" />
            <Button
              size="small"
              variant="outlined"
              startIcon={<RequeueIcon />}
              disabled={selectedDeadLetterEntryKeys.length === 0 || Boolean(deadLetterActionId)}
              onClick={() => void handleReplayDeadLetter(selectedDeadLetterEntryKeys)}
            >
              Replay Selected
            </Button>
            <Button
              size="small"
              variant="outlined"
              startIcon={<ClearIcon />}
              disabled={selectedDeadLetterEntryKeys.length === 0 || Boolean(deadLetterActionId)}
              onClick={() => void handleClearDeadLetter(selectedDeadLetterEntryKeys)}
            >
              Clear Selected
            </Button>
            <Button
              size="small"
              variant="outlined"
              startIcon={<DownloadIcon />}
              onClick={() => void handleExportDeadLetterCsv()}
            >
              Export CSV
            </Button>
          </Stack>

          {!deadLetterDiagnostics.enabled ? (
            <Alert severity="info">Dead-letter registry is disabled.</Alert>
          ) : filteredDeadLetterItems.length === 0 ? (
            <Alert severity="info">No dead-letter preview entries.</Alert>
          ) : (
            <TableContainer>
              <Table size="small" aria-label="Preview dead-letter queue">
                <TableHead>
                  <TableRow>
                    <TableCell padding="checkbox">
                      <Checkbox
                        size="small"
                        indeterminate={someVisibleDeadLetterSelected}
                        checked={allVisibleDeadLetterSelected}
                        onChange={(event) => handleToggleAllDeadLetterSelection(event.target.checked)}
                        inputProps={{ 'aria-label': 'Select all dead-letter entries' }}
                      />
                    </TableCell>
                    <TableCell>Name</TableCell>
                    <TableCell>Category</TableCell>
                    <TableCell>Policy</TableCell>
                    <TableCell align="right">Attempts</TableCell>
                    <TableCell align="right">Occurrences</TableCell>
                    <TableCell>Failed At</TableCell>
                    <TableCell>Reason</TableCell>
                    <TableCell align="right">Action</TableCell>
                  </TableRow>
                </TableHead>
                <TableBody>
                  {filteredDeadLetterItems.map((item) => (
                    <TableRow key={item.entryKey || item.documentId}>
                      <TableCell padding="checkbox">
                        <Checkbox
                          size="small"
                          checked={selectedDeadLetterSet.has(item.entryKey || item.documentId)}
                          onChange={(event) => handleToggleDeadLetterSelection(item.entryKey || item.documentId, event.target.checked)}
                          inputProps={{ 'aria-label': `Select dead-letter entry ${item.entryKey || item.documentId}` }}
                        />
                      </TableCell>
                      <TableCell sx={{ maxWidth: 260 }}>
                        <Tooltip title={item.path || item.name || item.documentId} placement="top-start" arrow>
                          <Typography variant="body2" noWrap>{item.name || item.documentId}</Typography>
                        </Tooltip>
                      </TableCell>
                      <TableCell>
                        <Chip size="small" label={item.category || 'UNKNOWN'} variant="outlined" />
                      </TableCell>
                      <TableCell>
                        <Stack spacing={0.25}>
                          <Typography variant="body2">{item.policyKey || 'default'}</Typography>
                          <Typography variant="caption" color="text.secondary">
                            {item.renditionKey || 'preview'}
                          </Typography>
                        </Stack>
                      </TableCell>
                      <TableCell align="right">{item.attempts}</TableCell>
                      <TableCell align="right">{item.occurrences}</TableCell>
                      <TableCell>{formatLastUpdated(item.failedAt)}</TableCell>
                      <TableCell sx={{ maxWidth: 340 }}>
                        <Tooltip title={item.reason || '—'} placement="top-start" arrow>
                          <Typography variant="body2" noWrap>{item.reason || '—'}</Typography>
                        </Tooltip>
                      </TableCell>
                      <TableCell align="right">
                        <Stack direction="row" spacing={0.75} justifyContent="flex-end">
                          <Button
                            size="small"
                            variant="outlined"
                            startIcon={<RequeueIcon />}
                            disabled={Boolean(deadLetterActionId)}
                            onClick={() => void handleReplayDeadLetter([item.entryKey || item.documentId])}
                            aria-label={`Replay dead-letter ${item.entryKey || item.documentId}`}
                          >
                            {deadLetterActionId === (item.entryKey || item.documentId) ? 'Running...' : 'Replay'}
                          </Button>
                          <Button
                            size="small"
                            variant="outlined"
                            startIcon={<ClearIcon />}
                            disabled={Boolean(deadLetterActionId)}
                            onClick={() => void handleClearDeadLetter([item.entryKey || item.documentId])}
                            aria-label={`Clear dead-letter ${item.entryKey || item.documentId}`}
                          >
                            {deadLetterActionId === `clear:${item.entryKey || item.documentId}` ? 'Running...' : 'Clear'}
                          </Button>
                        </Stack>
                      </TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            </TableContainer>
          )}
        </Paper>
      )}

      {preventionDiagnostics && (
        <Paper sx={{ p: 2, mb: 2 }}>
          <Stack direction="row" alignItems="center" justifyContent="space-between" gap={1} flexWrap="wrap" mb={1}>
            <Typography variant="h6">Rendition Prevention Registry</Typography>
            <Stack direction="row" gap={1} flexWrap="wrap">
              <Chip
                size="small"
                label={preventionDiagnostics.enabled ? 'Prevention enabled' : 'Prevention disabled'}
                color={preventionDiagnostics.enabled ? 'success' : 'default'}
                variant={preventionDiagnostics.enabled ? 'outlined' : 'filled'}
              />
              <Chip
                size="small"
                label={`Blocked ${preventionDiagnostics.blockedCount}/${preventionDiagnostics.maxBlocked}`}
                color={preventionDiagnostics.blockedCount > 0 ? 'warning' : 'default'}
                variant="outlined"
              />
              <Chip size="small" label={`Auto-block ${preventionDiagnostics.autoBlockCategories.join(', ') || 'none'}`} variant="outlined" />
            </Stack>
          </Stack>

          <Stack direction="row" alignItems="center" gap={1} flexWrap="wrap" mb={2}>
            <TextField
              size="small"
              placeholder="Filter blocked items..."
              value={preventionFilterText}
              onChange={(event) => setPreventionFilterText(event.target.value)}
              sx={{ minWidth: 280, flex: '1 1 280px' }}
            />
            <Chip size="small" label={`Selected ${selectedPreventionIds.length}`} variant="outlined" />
            <Button
              size="small"
              variant="outlined"
              startIcon={<UnblockIcon />}
              disabled={selectedPreventionIds.length === 0 || Boolean(preventionActionId)}
              onClick={() => void handlePreventionBatchAction(false)}
            >
              Unblock Selected
            </Button>
            <Button
              size="small"
              variant="outlined"
              startIcon={<RequeueIcon />}
              disabled={selectedPreventionIds.length === 0 || Boolean(preventionActionId)}
              onClick={() => void handlePreventionBatchAction(true)}
            >
              Requeue Selected
            </Button>
          </Stack>

          {!preventionDiagnostics.enabled ? (
            <Alert severity="info">Rendition prevention is disabled. Queue retries will not be blocked by prevention markers.</Alert>
          ) : filteredPreventionItems.length === 0 ? (
            <Alert severity="info">No blocked rendition entries.</Alert>
          ) : (
            <TableContainer>
              <Table size="small" aria-label="Preview rendition prevention registry">
                <TableHead>
                  <TableRow>
                    <TableCell padding="checkbox">
                      <Checkbox
                        size="small"
                        indeterminate={someVisiblePreventionSelected}
                        checked={allVisiblePreventionSelected}
                        onChange={(event) => handleToggleAllPreventionSelection(event.target.checked)}
                        inputProps={{ 'aria-label': 'Select all blocked entries' }}
                      />
                    </TableCell>
                    <TableCell>Document</TableCell>
                    <TableCell>Category</TableCell>
                    <TableCell>Reason</TableCell>
                    <TableCell align="right">Hits</TableCell>
                    <TableCell>Blocked At</TableCell>
                    <TableCell>Last Hit</TableCell>
                    <TableCell align="right">Actions</TableCell>
                  </TableRow>
                </TableHead>
                <TableBody>
                  {filteredPreventionItems.map((item) => {
                    const actionBusy = preventionActionId === item.documentId;
                    const checked = selectedPreventionSet.has(item.documentId);
                    return (
                      <TableRow key={item.documentId} hover>
                        <TableCell padding="checkbox">
                          <Checkbox
                            size="small"
                            checked={checked}
                            onChange={(event) => handleTogglePreventionSelection(item.documentId, event.target.checked)}
                            inputProps={{ 'aria-label': `Select blocked entry ${item.documentId}` }}
                          />
                        </TableCell>
                        <TableCell sx={{ maxWidth: 360 }}>
                          <Tooltip title={item.path || item.name || item.documentId} placement="top-start" arrow>
                            <Typography variant="body2" noWrap>
                              {item.name || item.documentId}
                            </Typography>
                          </Tooltip>
                          <Typography variant="caption" color="text.secondary" noWrap>
                            {item.path || item.documentId}
                          </Typography>
                        </TableCell>
                        <TableCell>
                          <Chip size="small" label={item.category || 'UNKNOWN'} variant="outlined" />
                        </TableCell>
                        <TableCell sx={{ maxWidth: 420 }}>
                          <Tooltip title={item.reason || '—'} placement="top-start" arrow>
                            <Typography variant="body2" noWrap>
                              {item.reason || '—'}
                            </Typography>
                          </Tooltip>
                        </TableCell>
                        <TableCell align="right">{item.hitCount}</TableCell>
                        <TableCell>{formatLastUpdated(item.blockedAt)}</TableCell>
                        <TableCell>{formatLastUpdated(item.lastHitAt)}</TableCell>
                        <TableCell align="right">
                          <Stack direction="row" justifyContent="flex-end" gap={0.5}>
                            <Tooltip title="Copy document id" placement="top-start" arrow>
                              <IconButton
                                aria-label={`Copy blocked document id ${item.documentId}`}
                                size="small"
                                onClick={() => void handleCopyDocumentId(item.documentId)}
                              >
                                <CopyIcon fontSize="small" />
                              </IconButton>
                            </Tooltip>
                            <Button
                              size="small"
                              variant="outlined"
                              startIcon={<UnblockIcon />}
                              disabled={Boolean(preventionActionId)}
                              onClick={() => void handlePreventionAction(item.documentId, false)}
                              aria-label={`Unblock prevention ${item.documentId}`}
                            >
                              {actionBusy ? 'Running...' : 'Unblock'}
                            </Button>
                            <Button
                              size="small"
                              variant="outlined"
                              startIcon={<RequeueIcon />}
                              disabled={Boolean(preventionActionId)}
                              onClick={() => void handlePreventionAction(item.documentId, true)}
                              aria-label={`Unblock and requeue ${item.documentId}`}
                            >
                              Requeue
                            </Button>
                          </Stack>
                        </TableCell>
                      </TableRow>
                    );
                  })}
                </TableBody>
              </Table>
            </TableContainer>
          )}
        </Paper>
      )}

      <Paper sx={{ p: 2 }}>
        {loading ? (
          <Box display="flex" justifyContent="center" py={6}>
            <CircularProgress />
          </Box>
        ) : filteredItems.length === 0 ? (
          <Alert severity="info">No preview failures found.</Alert>
        ) : (
          <TableContainer>
            <Table size="small" aria-label="Preview diagnostics failures">
              <TableHead>
                <TableRow>
                  <TableCell>Name</TableCell>
                  <TableCell>Status</TableCell>
                  <TableCell>Mime Type</TableCell>
                  <TableCell>Category</TableCell>
                  <TableCell>Reason</TableCell>
                  <TableCell>Last Updated</TableCell>
                  <TableCell align="right">Actions</TableCell>
                </TableRow>
              </TableHead>
              <TableBody>
                {filteredItems.map((item) => {
                  const meta = getFailedPreviewMeta(item.mimeType, item.previewFailureCategory, item.previewFailureReason);
                  const retryable = isRetryablePreviewFailure(
                    item.previewFailureCategory,
                    item.mimeType,
                    item.previewFailureReason
                  );
                  const queueBusy = queueingId === item.id || Boolean(reasonBatchActionKey);
                  const reasonLabel = formatPreviewFailureReasonLabel(item.previewFailureReason);
                  const parentPath = getParentFolderPath(item.path);

                  return (
                    <TableRow key={item.id} hover>
                      <TableCell sx={{ maxWidth: 360 }}>
                        <Tooltip title={item.path || item.name || item.id} placement="top-start" arrow>
                          <Typography variant="body2" noWrap>
                            {item.name || item.id}
                          </Typography>
                        </Tooltip>
                        {item.path && (
                          <Typography variant="caption" color="text.secondary" noWrap>
                            {item.path}
                          </Typography>
                        )}
                      </TableCell>
                      <TableCell>
                        <Chip label={meta.label} color={meta.color} size="small" />
                      </TableCell>
                      <TableCell sx={{ maxWidth: 220 }}>
                        <Typography variant="body2" noWrap>
                          {item.mimeType || '—'}
                        </Typography>
                      </TableCell>
                      <TableCell>
                        <Chip label={item.previewFailureCategory || '—'} size="small" variant="outlined" />
                      </TableCell>
                      <TableCell sx={{ maxWidth: 420 }}>
                        <Tooltip title={reasonLabel} placement="top-start" arrow>
                          <Typography variant="body2" noWrap>
                            {reasonLabel}
                          </Typography>
                        </Tooltip>
                      </TableCell>
                      <TableCell>
                        <Typography variant="body2">{formatLastUpdated(item.previewLastUpdated)}</Typography>
                      </TableCell>
                      <TableCell align="right">
                        <Stack direction="row" justifyContent="flex-end" gap={0.5}>
                          <Tooltip title="Copy document id" placement="top-start" arrow>
                            <IconButton
                              aria-label="Copy document id"
                              size="small"
                              onClick={() => void handleCopyId(item)}
                            >
                              <CopyIcon fontSize="small" />
                            </IconButton>
                          </Tooltip>

                          <Tooltip
                            title={parentPath ? 'Open parent folder' : 'Path is missing; cannot open parent folder'}
                            placement="top-start"
                            arrow
                          >
                            <span>
                              <IconButton
                                aria-label="Open parent folder"
                                size="small"
                                disabled={!parentPath}
                                onClick={() => void handleOpenParentFolder(item)}
                              >
                                <FolderOpenIcon fontSize="small" />
                              </IconButton>
                            </span>
                          </Tooltip>

                          <Tooltip title="Open in Advanced Search" placement="top-start" arrow>
                            <IconButton
                              aria-label="Open in Advanced Search"
                              size="small"
                              onClick={() => handleOpenInSearch(item)}
                            >
                              <SearchIcon fontSize="small" />
                            </IconButton>
                          </Tooltip>

                          <Tooltip
                            title={
                              retryable
                                ? 'Retry preview'
                                : 'Retry is only available for temporary failures (unsupported/permanent are disabled)'
                            }
                            placement="top-start"
                            arrow
                          >
                            <span>
                              <IconButton
                                aria-label="Retry preview"
                                size="small"
                                disabled={!retryable || queueBusy}
                                onClick={() => void handleQueuePreview(item, false)}
                              >
                                <RetryIcon fontSize="small" />
                              </IconButton>
                            </span>
                          </Tooltip>

                          <Tooltip
                            title={
                              retryable
                                ? 'Force rebuild preview'
                                : 'Force rebuild is only available for temporary failures (unsupported/permanent are disabled)'
                            }
                            placement="top-start"
                            arrow
                          >
                            <span>
                              <IconButton
                                aria-label="Force rebuild preview"
                                size="small"
                                disabled={!retryable || queueBusy}
                                onClick={() => void handleQueuePreview(item, true)}
                              >
                                <ForceIcon fontSize="small" />
                              </IconButton>
                            </span>
                          </Tooltip>
                        </Stack>
                      </TableCell>
                    </TableRow>
                  );
                })}
              </TableBody>
            </Table>
          </TableContainer>
        )}
      </Paper>
    </Box>
  );
};

export default PreviewDiagnosticsPage;
