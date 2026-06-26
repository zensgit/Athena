import React, { useEffect, useRef, useState } from 'react';
import {
  Box,
  Container,
  Grid,
  Paper,
  Typography,
  Stack,
  Card,
  CardContent,
  List,
  ListItem,
  ListItemText,
  ListItemIcon,
  Divider,
  LinearProgress,
  CircularProgress,
  Chip,
  IconButton,
  Tabs,
  Tab,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  Button,
  Alert,
  Dialog,
  DialogTitle,
  DialogContent,
  DialogActions,
  TextField,
  FormControl,
  FormControlLabel,
  InputLabel,
  MenuItem,
  Select,
  Switch,
  Autocomplete,
  Collapse,
  Tooltip,
} from '@mui/material';
import {
  Storage,
  Description,
  People,
  TrendingUp,
  Refresh,
  History,
  PictureAsPdf,
  Image,
  Article,
  InsertDriveFile,
  Delete as DeleteIcon,
  Group as GroupIcon,
  PersonAdd,
  GroupAdd,
  WorkspacePremium,
  Download as DownloadIcon,
  CleaningServices as CleanupIcon,
  PlayArrow,
  Star,
  MailOutline,
  Block,
  OpenInNew,
  ExpandMore,
  ExpandLess,
} from '@mui/icons-material';
import { format } from 'date-fns';
import apiService from '../services/api';
import ScheduledJobsCard from '../components/admin/ScheduledJobsCard';
import QueueBacklogCard from '../components/admin/QueueBacklogCard';
import { toast } from 'react-toastify';
import userGroupService, { Group } from 'services/userGroupService';
import savedSearchService, { SavedSearch } from 'services/savedSearchService';
import mailAutomationService, { MailFetchSummaryStatus } from 'services/mailAutomationService';
import authService from 'services/authService';
import shareLinkServiceImport, { AccessLogEntry, AccessStats } from 'services/shareLinkService';
import nodeService, { BatchDownloadAsyncTask } from 'services/nodeService';
import { useLocation, useNavigate } from 'react-router-dom';
import { useAppDispatch } from 'store';
import { executeSavedSearch, setLastSearchCriteria } from 'store/slices/nodeSlice';
import { User } from 'types';
import { buildSearchCriteriaFromSavedSearch } from 'utils/savedSearchUtils';

// Types matching backend Analytics DTOs
interface SystemSummary {
  totalDocuments: number;
  totalFolders: number;
  totalSizeBytes: number;
  formattedTotalSize: string;
}

interface MimeTypeStats {
  mimeType: string;
  count: number;
  sizeBytes: number;
}

interface DailyActivity {
  date: string;
  eventCount: number;
}

interface UserActivity {
  username: string;
  activityCount: number;
}

interface AuditLog {
  id: string;
  eventType: string;
  nodeName: string;
  username: string;
  eventTime: string;
  details: string;
}

interface PageResponse<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  number: number;
  size: number;
}

interface AuditPreset {
  id: string;
  label: string;
  requiresUser: boolean;
  requiresEventType: boolean;
}

interface AuditEventTypeOption {
  eventType: string;
  count: number;
}

interface DashboardData {
  summary: SystemSummary;
  storage: MimeTypeStats[];
  activity: DailyActivity[];
  topUsers: UserActivity[];
  recentLogs?: AuditLog[]; // Fetched separately
}

interface LicenseInfo {
  edition: string;
  maxUsers: number;
  maxStorageGb: number;
  expirationDate?: string | null;
  features?: string[];
  valid: boolean;
}

interface AuditRetentionInfo {
  retentionDays: number;
  expiredLogCount: number;
  exportMaxRangeDays?: number;
}

interface AuditReportSummary {
  windowDays: number;
  totalEvents: number;
  countsByCategory: Record<string, number>;
}

interface AuditCategorySetting {
  category: string;
  enabled: boolean;
}

interface AuditExportAsyncTask {
  taskId: string;
  status: string;
  error?: string | null;
  createdAt?: string | null;
  finishedAt?: string | null;
  filename?: string | null;
  rowCount?: number | null;
}

interface AuditExportAsyncListResponse {
  count: number;
  items: AuditExportAsyncTask[];
}

interface AuditExportAsyncSummaryResponse {
  totalCount: number;
  queuedCount: number;
  runningCount: number;
  completedCount: number;
  cancelledCount: number;
  failedCount: number;
  activeCount: number;
  terminalCount: number;
}

interface AsyncExportHealthSummary {
  total: number;
  active: number;
  terminal: number;
  queued: number;
  running: number;
  completed: number;
  failed: number;
  cancelled: number;
  timedOut: number;
  expired: number;
  failureRate: number;
}

type AsyncExportHealthDomainKey = 'audit' | 'ops' | 'search' | 'preview' | 'batchdownload' | 'propertyencryption';
type AsyncExportRiskLevel = 'LOW' | 'MEDIUM' | 'HIGH' | 'CRITICAL';

interface AsyncExportHealthDomainConfig {
  key: AsyncExportHealthDomainKey;
  label: string;
  endpoint?: string;
}

interface AsyncExportHealthDomainState extends AsyncExportHealthDomainConfig {
  status: 'healthy' | 'degraded';
  riskLevel: AsyncExportRiskLevel;
  summary: AsyncExportHealthSummary;
  error: string | null;
}

interface AsyncExportGovernanceOverviewDomain {
  key: string;
  label: string;
  status?: string | null;
  riskLevel?: string | null;
  error?: string | null;
  totalCount?: number;
  activeCount?: number;
  terminalCount?: number;
  queuedCount?: number;
  runningCount?: number;
  completedCount?: number;
  cancelledCount?: number;
  failedCount?: number;
  timedOutCount?: number;
  expiredCount?: number;
  failureRate?: number;
}

interface AsyncExportGovernanceOverviewResponse {
  generatedAt?: string | null;
  overallStatus?: string | null;
  overallRiskLevel?: string | null;
  totalDomains?: number;
  degradedDomainCount?: number;
  totalCount?: number;
  activeCount?: number;
  terminalCount?: number;
  queuedCount?: number;
  runningCount?: number;
  completedCount?: number;
  cancelledCount?: number;
  failedCount?: number;
  timedOutCount?: number;
  expiredCount?: number;
  failureRate?: number;
  domains?: AsyncExportGovernanceOverviewDomain[];
}

type RecentAsyncTaskDomainFilter =
  | 'ALL'
  | 'audit'
  | 'ops'
  | 'search'
  | 'preview'
  | 'batchdownload'
  | 'propertyencryption';

type RecentAsyncTaskStatusFilter = 'ALL' | 'QUEUED' | 'RUNNING' | 'COMPLETED' | 'CANCELLED' | 'FAILED';

type RecentAsyncTaskActionType = 'cancel' | 'download' | 'cleanup' | 'acknowledge' | 'unacknowledge';

interface RecentAsyncTaskItem {
  domainKey: string;
  domainLabel: string;
  taskId: string;
  status: string;
  error?: string | null;
  createdAt?: string | null;
  startedAt?: string | null;
  updatedAt?: string | null;
  timeoutAt?: string | null;
  expiresAt?: string | null;
  finishedAt?: string | null;
  filename?: string | null;
  createdBy?: string | null;
  updatedBy?: string | null;
  fingerprint?: string | null;
  acknowledged?: boolean;
  acknowledgedAt?: string | null;
  cancelUrl?: string | null;
  downloadUrl?: string | null;
  cleanupUrl?: string | null;
  cancellable: boolean;
  cleanupEligible: boolean;
  downloadReady: boolean;
}

interface RecentAsyncTaskPaging {
  skipCount: number;
  maxItems: number;
  totalItems: number;
  hasMoreItems: boolean;
}

interface RecentAsyncTaskListResponse {
  generatedAt?: string | null;
  domainFilter?: string | null;
  statusFilter?: string | null;
  count: number;
  totalCount: number;
  paging: RecentAsyncTaskPaging;
  items: RecentAsyncTaskItem[];
}

interface RecentAsyncTaskAcknowledgementResponse {
  domainKey?: string | null;
  taskId?: string | null;
  fingerprint: string;
  acknowledged: boolean;
  acknowledgedAt?: string | null;
  changed: boolean;
}

interface AuditExportAsyncCreateResponse {
  taskId: string;
  status: string;
  createdAt?: string | null;
}

interface AuditExportAsyncCleanupResponse {
  deletedCount: number;
  remainingCount: number;
  statusFilter?: string | null;
  message: string;
}

interface AuditExportAsyncCancelActiveResponse {
  cancelledCount: number;
  remainingActiveCount: number;
  statusFilter?: string | null;
  message: string;
}

interface AuditExportRequestPayload {
  from?: string;
  to?: string;
  preset?: string;
  username?: string;
  eventType?: string;
  category?: string;
  nodeId?: string;
  days?: number;
}

type AuditExportAsyncTaskStatusFilter =
  | 'ALL'
  | 'QUEUED'
  | 'RUNNING'
  | 'COMPLETED'
  | 'CANCELLED'
  | 'FAILED';

type BatchDownloadTaskStatusFilter =
  | 'ALL'
  | 'QUEUED'
  | 'RUNNING'
  | 'CANCEL_REQUESTED'
  | 'COMPLETED'
  | 'CANCELLED'
  | 'FAILED';

interface BatchDownloadAdminSummary {
  totalCount: number;
  activeCount: number;
  terminalCount: number;
  queuedCount: number;
  runningCount: number;
  cancelRequestedCount: number;
  cancelledCount: number;
  completedCount: number;
  failedCount: number;
}

interface BatchDownloadAdminPaging {
  maxItems: number;
  skipCount: number;
  totalItems: number;
  hasMoreItems: boolean;
}

const BATCH_DOWNLOAD_ADMIN_AUTO_REFRESH_MS = 15000;
const BATCH_DOWNLOAD_ADMIN_DEFAULT_ROWS = 10;
const BATCH_DOWNLOAD_ADMIN_ROW_OPTIONS = [10, 25, 50];

interface RuleExecutionSummary {
  windowDays: number;
  executions: number;
  failures: number;
  successRate: number;
  scheduledBatches: number;
  scheduledFailures: number;
  countsByType: Record<string, number>;
}

const RULE_EVENT_TYPES = [
  'RULE_EXECUTED',
  'RULE_EXECUTION_FAILED',
  'SCHEDULED_RULE_BATCH_COMPLETED',
  'SCHEDULED_RULE_BATCH_PARTIAL',
];

const RULE_EVENT_LABELS: Record<string, string> = {
  RULE_EXECUTED: 'Rule Executed',
  RULE_EXECUTION_FAILED: 'Rule Failed',
  SCHEDULED_RULE_BATCH_COMPLETED: 'Scheduled Batch OK',
  SCHEDULED_RULE_BATCH_PARTIAL: 'Scheduled Batch Partial',
};

const UUID_PATTERN = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;

const AUDIT_QUERY_KEYS = {
  user: 'auditUser',
  eventType: 'auditEventType',
  category: 'auditCategory',
  nodeId: 'auditNodeId',
  from: 'auditFrom',
  to: 'auditTo',
} as const;

const ASYNC_TASK_QUERY_KEYS = {
  domain: 'asyncTaskDomain',
  status: 'asyncTaskStatus',
  includeAcknowledged: 'asyncTaskIncludeAcknowledged',
} as const;

const AUDIT_EXPORT_ASYNC_TASK_LIMIT = 10;
const AUDIT_EXPORT_ASYNC_RUNNING_STATUSES = new Set(['QUEUED', 'RUNNING', 'PROCESSING', 'IN_PROGRESS', 'STARTED']);
const AUDIT_EXPORT_ASYNC_COMPLETED_STATUSES = new Set(['COMPLETED', 'DONE', 'SUCCESS', 'SUCCEEDED', 'FINISHED']);
const AUDIT_EXPORT_ASYNC_STATUS_FILTER_OPTIONS: Array<{
  value: AuditExportAsyncTaskStatusFilter;
  label: string;
}> = [
  { value: 'ALL', label: 'All statuses' },
  { value: 'QUEUED', label: 'Queued' },
  { value: 'RUNNING', label: 'Running' },
  { value: 'COMPLETED', label: 'Completed' },
  { value: 'CANCELLED', label: 'Cancelled' },
  { value: 'FAILED', label: 'Failed' },
];

const ASYNC_EXPORT_HEALTH_DOMAINS: AsyncExportHealthDomainConfig[] = [
  { key: 'audit', label: 'Audit', endpoint: '/analytics/audit/export-async/summary' },
  { key: 'ops', label: 'Ops Recovery', endpoint: '/ops/recovery/history/export-async/summary' },
  { key: 'search', label: 'Search', endpoint: '/search/preview/queue-failed/dry-run/export-async/summary' },
  { key: 'preview', label: 'Preview', endpoint: '/preview/diagnostics/renditions/resources/export-async/summary' },
  { key: 'batchdownload', label: 'Batch Download', endpoint: '/nodes/download/batch-async/summary' },
  { key: 'propertyencryption', label: 'Property Encryption' },
];

const RECENT_ASYNC_TASK_MAX_ITEM_OPTIONS = [5, 10, 20, 50];

const RECENT_ASYNC_TASK_DOMAIN_OPTIONS: Array<{ value: RecentAsyncTaskDomainFilter; label: string }> = [
  { value: 'ALL', label: 'All domains' },
  { value: 'audit', label: 'Audit' },
  { value: 'ops', label: 'Ops Recovery' },
  { value: 'search', label: 'Search' },
  { value: 'preview', label: 'Preview' },
  { value: 'batchdownload', label: 'Batch Download' },
  { value: 'propertyencryption', label: 'Property Encryption' },
];

const RECENT_ASYNC_TASK_STATUS_OPTIONS: Array<{ value: RecentAsyncTaskStatusFilter; label: string }> = [
  { value: 'ALL', label: 'All statuses' },
  { value: 'QUEUED', label: 'Queued' },
  { value: 'RUNNING', label: 'Running' },
  { value: 'COMPLETED', label: 'Completed' },
  { value: 'CANCELLED', label: 'Cancelled' },
  { value: 'FAILED', label: 'Failed' },
];

const emptyAsyncExportHealthSummary = (): AsyncExportHealthSummary => ({
  total: 0,
  active: 0,
  terminal: 0,
  queued: 0,
  running: 0,
  completed: 0,
  failed: 0,
  cancelled: 0,
  timedOut: 0,
  expired: 0,
  failureRate: 0,
});

const toFiniteNumber = (value: unknown): number => {
  const numeric = Number(value);
  return Number.isFinite(numeric) ? numeric : 0;
};

const normalizeAsyncExportHealthSummary = (payload: Record<string, unknown> | null | undefined): AsyncExportHealthSummary => {
  const source = payload || {};
  const total = toFiniteNumber(source.totalCount ?? source.total);
  const failed = toFiniteNumber(source.failedCount ?? source.failed);
  const timedOut = toFiniteNumber(source.timedOutCount ?? source.timedOut);
  const expired = toFiniteNumber(source.expiredCount ?? source.expired);
  const fallbackFailureRate = total > 0 ? (failed + timedOut + expired) / total : 0;
  return {
    total,
    active: toFiniteNumber(source.activeCount ?? source.active),
    terminal: toFiniteNumber(source.terminalCount ?? source.terminal),
    queued: toFiniteNumber(source.queuedCount ?? source.queued),
    running: toFiniteNumber(source.runningCount ?? source.running),
    completed: toFiniteNumber(source.completedCount ?? source.completed),
    failed,
    cancelled: toFiniteNumber(source.cancelledCount ?? source.cancelled),
    timedOut,
    expired,
    failureRate: toFiniteNumber(source.failureRate ?? fallbackFailureRate),
  };
};

const normalizeAsyncExportRiskLevel = (value?: string | null): AsyncExportRiskLevel => {
  const normalized = (value || '').trim().toUpperCase();
  if (normalized === 'CRITICAL' || normalized === 'HIGH' || normalized === 'MEDIUM' || normalized === 'LOW') {
    return normalized;
  }
  return 'LOW';
};

const maxAsyncExportRiskLevel = (left: AsyncExportRiskLevel, right: AsyncExportRiskLevel): AsyncExportRiskLevel => {
  const rank: Record<AsyncExportRiskLevel, number> = {
    LOW: 0,
    MEDIUM: 1,
    HIGH: 2,
    CRITICAL: 3,
  };
  return rank[left] >= rank[right] ? left : right;
};

const inferAsyncExportRiskLevel = (
  summary: AsyncExportHealthSummary,
  degraded: boolean
): AsyncExportRiskLevel => {
  if (degraded) {
    return 'CRITICAL';
  }
  if (summary.timedOut > 0 || summary.expired > 0) {
    return 'HIGH';
  }
  if (summary.active >= 20 || summary.failureRate >= 0.35) {
    return 'HIGH';
  }
  if (summary.active >= 8 || summary.failed > 0 || summary.cancelled >= 5) {
    return 'MEDIUM';
  }
  return 'LOW';
};

const toAsyncExportRiskChipColor = (riskLevel: AsyncExportRiskLevel): 'success' | 'warning' | 'error' | 'default' => {
  if (riskLevel === 'LOW') {
    return 'success';
  }
  if (riskLevel === 'MEDIUM') {
    return 'warning';
  }
  return 'error';
};

const RECENT_ASYNC_TASK_DOMAIN_LABELS: Record<Exclude<RecentAsyncTaskDomainFilter, 'ALL'>, string> = {
  audit: 'Audit',
  ops: 'Ops Recovery',
  search: 'Search',
  preview: 'Preview',
  batchdownload: 'Batch Download',
  propertyencryption: 'Property Encryption',
};

const normalizeRecentAsyncTaskDomainFilter = (value?: string | null): RecentAsyncTaskDomainFilter | null => {
  const normalized = (value || '').trim().toLowerCase();
  switch (normalized) {
    case '':
      return null;
    case 'all':
      return 'ALL';
    case 'audit':
      return 'audit';
    case 'ops':
      return 'ops';
    case 'search':
      return 'search';
    case 'preview':
      return 'preview';
    case 'batchdownload':
    case 'batch-download':
    case 'batch_download':
      return 'batchdownload';
    case 'propertyencryption':
    case 'property-encryption':
    case 'property_encryption':
    case 'propertyencryptionjobs':
      return 'propertyencryption';
    default:
      return null;
  }
};

const normalizeRecentAsyncTaskStatusFilter = (value?: string | null): RecentAsyncTaskStatusFilter | null => {
  const normalized = (value || '').trim().toUpperCase();
  if (!normalized) {
    return null;
  }
  return RECENT_ASYNC_TASK_STATUS_OPTIONS.some((option) => option.value === normalized)
    ? normalized as RecentAsyncTaskStatusFilter
    : null;
};

const normalizeRecentAsyncTaskStatus = (status?: string | null) => {
  const normalized = (status || '').trim().toUpperCase();
  return normalized || 'UNKNOWN';
};

const toRecentAsyncTaskStatusChipColor = (
  status: string
): 'success' | 'warning' | 'info' | 'default' => {
  const normalized = (status || '').trim().toUpperCase();
  if (normalized === 'COMPLETED' || normalized === 'SUCCEEDED') {
    return 'success';
  }
  if (normalized === 'FAILED' || normalized === 'CANCELLED' || normalized === 'CANCEL_REQUESTED') {
    return 'warning';
  }
  if (normalized === 'RUNNING' || normalized === 'QUEUED') {
    return 'info';
  }
  return 'default';
};

const formatRecentAsyncTaskTimestamp = (value?: string | null) => {
  if (!value) {
    return '—';
  }
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) {
    return '—';
  }
  return format(date, 'PPp');
};

const normalizeRecentAsyncTaskActionUrl = (value?: string | null) => {
  const trimmed = (value || '').trim();
  if (!trimmed) {
    return null;
  }
  return trimmed.startsWith('/api/v1/') ? trimmed.slice('/api/v1'.length) : trimmed;
};

const normalizeAuditAsyncTaskStatus = (status?: string | null) => {
  const normalized = (status || '').trim().toUpperCase();
  return normalized || 'UNKNOWN';
};

const normalizeBatchDownloadTaskStatus = (status?: string | null) => {
  const normalized = (status || '').trim().toUpperCase();
  return normalized || 'UNKNOWN';
};

const BATCH_DOWNLOAD_TASK_FILTER_OPTIONS: Array<{ value: BatchDownloadTaskStatusFilter; label: string }> = [
  { value: 'ALL', label: 'All tasks' },
  { value: 'QUEUED', label: 'Queued' },
  { value: 'RUNNING', label: 'Running' },
  { value: 'CANCEL_REQUESTED', label: 'Cancel requested' },
  { value: 'COMPLETED', label: 'Completed' },
  { value: 'CANCELLED', label: 'Cancelled' },
  { value: 'FAILED', label: 'Failed' },
];

const BATCH_DOWNLOAD_ACTIVE_STATUSES = new Set(['QUEUED', 'RUNNING', 'CANCEL_REQUESTED']);
const BATCH_DOWNLOAD_DOWNLOADABLE_STATUSES = new Set(['COMPLETED']);

const sanitizeFilenameSegment = (value: string) => {
  return value
    .trim()
    .replace(/[\s/\\?%*:|"<>]+/g, '-')
    .replace(/[^a-zA-Z0-9._-]+/g, '-')
    .replace(/-+/g, '-')
    .replace(/^[-_.]+|[-_.]+$/g, '');
};

const AdminDashboard: React.FC = () => {
  const navigate = useNavigate();
  const location = useLocation();
  const dispatch = useAppDispatch();
  const asyncTaskUrlParams = new URLSearchParams(location.search);
  const initialRecentAsyncTaskDomainFilter =
    normalizeRecentAsyncTaskDomainFilter(asyncTaskUrlParams.get(ASYNC_TASK_QUERY_KEYS.domain)) || 'ALL';
  const initialRecentAsyncTaskStatusFilter =
    normalizeRecentAsyncTaskStatusFilter(asyncTaskUrlParams.get(ASYNC_TASK_QUERY_KEYS.status)) || 'ALL';
  const initialRecentAsyncTasksIncludeAcknowledged =
    asyncTaskUrlParams.get(ASYNC_TASK_QUERY_KEYS.includeAcknowledged)?.trim().toLowerCase() === 'true';
  const [tab, setTab] = useState(0);

  // Overview/dashboard state
  const [data, setData] = useState<DashboardData | null>(null);
  const [logs, setLogs] = useState<AuditLog[]>([]);
  const [ruleSummary, setRuleSummary] = useState<RuleExecutionSummary | null>(null);
  const [ruleEvents, setRuleEvents] = useState<AuditLog[]>([]);
  const [ruleEventFilter, setRuleEventFilter] = useState<string[]>(RULE_EVENT_TYPES);
  const [licenseInfo, setLicenseInfo] = useState<LicenseInfo | null>(null);
  const [loadingDashboard, setLoadingDashboard] = useState(true);
  const [mailFetchSummary, setMailFetchSummary] = useState<MailFetchSummaryStatus | null>(null);
  const [mailFetchSummaryLoading, setMailFetchSummaryLoading] = useState(false);
  const [mailFetchSummaryError, setMailFetchSummaryError] = useState<string | null>(null);
  const [mailFetchTriggering, setMailFetchTriggering] = useState(false);
  const [retentionInfo, setRetentionInfo] = useState<AuditRetentionInfo | null>(null);
  const [auditReport, setAuditReport] = useState<AuditReportSummary | null>(null);
  const [exportingAudit, setExportingAudit] = useState(false);
  const [cleaningAudit, setCleaningAudit] = useState(false);
  const [auditPresets, setAuditPresets] = useState<AuditPreset[]>([]);
  const [auditEventTypes, setAuditEventTypes] = useState<AuditEventTypeOption[]>([]);
  const [auditUserSuggestions, setAuditUserSuggestions] = useState<string[]>([]);
  const [auditCategories, setAuditCategories] = useState<AuditCategorySetting[]>([]);
  const [auditCategoriesLoading, setAuditCategoriesLoading] = useState(false);
  const [auditCategoriesUpdating, setAuditCategoriesUpdating] = useState(false);
  const [auditExportPreset, setAuditExportPreset] = useState('custom');
  const [auditFilterUser, setAuditFilterUser] = useState('');
  const [auditFilterEventType, setAuditFilterEventType] = useState('');
  const [auditFilterCategory, setAuditFilterCategory] = useState('');
  const [auditFilterNodeId, setAuditFilterNodeId] = useState('');
  const [auditQuickRange, setAuditQuickRange] = useState<'24h' | '7d' | '30d' | 'custom'>('30d');
  const [filteringAudit, setFilteringAudit] = useState(false);
  const [auditExportAsyncTasks, setAuditExportAsyncTasks] = useState<AuditExportAsyncTask[]>([]);
  const [auditExportAsyncStatusFilter, setAuditExportAsyncStatusFilter] = useState<AuditExportAsyncTaskStatusFilter>('ALL');
  const [auditExportAsyncTasksLoading, setAuditExportAsyncTasksLoading] = useState(false);
  const [auditExportAsyncSummary, setAuditExportAsyncSummary] = useState<AuditExportAsyncSummaryResponse | null>(null);
  const [auditExportAsyncSummaryLoading, setAuditExportAsyncSummaryLoading] = useState(false);
  const [auditExportAsyncStarting, setAuditExportAsyncStarting] = useState(false);
  const [auditExportAsyncCleaning, setAuditExportAsyncCleaning] = useState(false);
  const [auditExportAsyncCancellingActive, setAuditExportAsyncCancellingActive] = useState(false);
  const [auditExportAsyncActionTaskId, setAuditExportAsyncActionTaskId] = useState<string | null>(null);
  const [auditExportAsyncActionType, setAuditExportAsyncActionType] = useState<'cancel' | 'download' | null>(null);
  const [batchDownloadAdminTasks, setBatchDownloadAdminTasks] = useState<BatchDownloadAsyncTask[]>([]);
  const [batchDownloadAdminStatusFilter, setBatchDownloadAdminStatusFilter] = useState<BatchDownloadTaskStatusFilter>('ALL');
  const [batchDownloadAdminTasksLoading, setBatchDownloadAdminTasksLoading] = useState(false);
  const [batchDownloadAdminPage, setBatchDownloadAdminPage] = useState(0);
  const [batchDownloadAdminRowsPerPage, setBatchDownloadAdminRowsPerPage] = useState(BATCH_DOWNLOAD_ADMIN_DEFAULT_ROWS);
  const [batchDownloadAdminQuery, setBatchDownloadAdminQuery] = useState('');
  const [batchDownloadAdminQueryInput, setBatchDownloadAdminQueryInput] = useState('');
  const [batchDownloadAdminOwnerFilter, setBatchDownloadAdminOwnerFilter] = useState('');
  const [batchDownloadAdminOwnerInput, setBatchDownloadAdminOwnerInput] = useState('');
  const [batchDownloadAdminPaging, setBatchDownloadAdminPaging] = useState<BatchDownloadAdminPaging>({
    maxItems: BATCH_DOWNLOAD_ADMIN_DEFAULT_ROWS,
    skipCount: 0,
    totalItems: 0,
    hasMoreItems: false,
  });
  const [batchDownloadAdminSummary, setBatchDownloadAdminSummary] = useState<BatchDownloadAdminSummary>({
    totalCount: 0,
    activeCount: 0,
    terminalCount: 0,
    queuedCount: 0,
    runningCount: 0,
    cancelRequestedCount: 0,
    cancelledCount: 0,
    completedCount: 0,
    failedCount: 0,
  });
  const [batchDownloadAdminError, setBatchDownloadAdminError] = useState<string | null>(null);
  const [batchDownloadAdminCleaning, setBatchDownloadAdminCleaning] = useState(false);
  const [batchDownloadAdminCancellingActive, setBatchDownloadAdminCancellingActive] = useState(false);
  const [batchDownloadAdminActionTaskId, setBatchDownloadAdminActionTaskId] = useState<string | null>(null);
  const [batchDownloadAdminActionType, setBatchDownloadAdminActionType] = useState<'cancel' | 'download' | 'cleanup' | null>(null);
  const [batchDownloadAdminAutoRefresh, setBatchDownloadAdminAutoRefresh] = useState(true);
  const [batchDownloadAdminUpdatedAt, setBatchDownloadAdminUpdatedAt] = useState<string | null>(null);
  const [asyncExportHealthLoading, setAsyncExportHealthLoading] = useState(false);
  const [asyncExportHealthUpdatedAt, setAsyncExportHealthUpdatedAt] = useState<string | null>(null);
  const [asyncExportHealthOverallStatus, setAsyncExportHealthOverallStatus] = useState<'healthy' | 'degraded'>('healthy');
  const [asyncExportHealthOverallRiskLevel, setAsyncExportHealthOverallRiskLevel] = useState<AsyncExportRiskLevel>('LOW');
  const [asyncExportHealthDomains, setAsyncExportHealthDomains] = useState<AsyncExportHealthDomainState[]>(
    () => ASYNC_EXPORT_HEALTH_DOMAINS.map((domain) => ({
      ...domain,
      status: 'healthy',
      riskLevel: 'LOW',
      summary: emptyAsyncExportHealthSummary(),
      error: null,
    }))
  );
  const [recentAsyncTasks, setRecentAsyncTasks] = useState<RecentAsyncTaskItem[]>([]);
  const [recentAsyncTasksTotalCount, setRecentAsyncTasksTotalCount] = useState(0);
  const [recentAsyncTasksLoading, setRecentAsyncTasksLoading] = useState(false);
  const [recentAsyncTasksError, setRecentAsyncTasksError] = useState<string | null>(null);
  const [recentAsyncTasksUpdatedAt, setRecentAsyncTasksUpdatedAt] = useState<string | null>(null);
  const [recentAsyncTasksMaxItems, setRecentAsyncTasksMaxItems] = useState(10);
  const [recentAsyncTasksDomainFilter, setRecentAsyncTasksDomainFilter] =
    useState<RecentAsyncTaskDomainFilter>(initialRecentAsyncTaskDomainFilter);
  const [recentAsyncTasksStatusFilter, setRecentAsyncTasksStatusFilter] =
    useState<RecentAsyncTaskStatusFilter>(initialRecentAsyncTaskStatusFilter);
  const [recentAsyncTasksIncludeAcknowledged, setRecentAsyncTasksIncludeAcknowledged] =
    useState(initialRecentAsyncTasksIncludeAcknowledged);
  const [recentAsyncTasksActionTaskKey, setRecentAsyncTasksActionTaskKey] = useState<string | null>(null);
  const [recentAsyncTasksActionType, setRecentAsyncTasksActionType] = useState<RecentAsyncTaskActionType | null>(null);
  const [pinnedSearches, setPinnedSearches] = useState<SavedSearch[]>([]);
  const [pinnedLoading, setPinnedLoading] = useState(false);
  const [pinnedError, setPinnedError] = useState<string | null>(null);

  // Users state
  const [users, setUsers] = useState<User[]>([]);
  const [usersLoading, setUsersLoading] = useState(false);
  const [userQuery, setUserQuery] = useState('');
  const [createUserOpen, setCreateUserOpen] = useState(false);
  const [newUser, setNewUser] = useState({
    username: '',
    email: '',
    password: '',
    firstName: '',
    lastName: '',
    enabled: true,
  });

  // Groups state
  const [groups, setGroups] = useState<Group[]>([]);
  const [groupsLoading, setGroupsLoading] = useState(false);
  const [createGroupOpen, setCreateGroupOpen] = useState(false);
  const [newGroup, setNewGroup] = useState({ name: '', displayName: '' });
  const [membersGroup, setMembersGroup] = useState<Group | null>(null);
  const [availableUsernames, setAvailableUsernames] = useState<string[]>([]);
  const [memberToAdd, setMemberToAdd] = useState('');

  const formatDateTimeInput = (date: Date) => format(date, "yyyy-MM-dd'T'HH:mm");
  const formatDateTimeOffset = (date: Date) => format(date, "yyyy-MM-dd'T'HH:mm:ssXXX");
  const [auditExportFrom, setAuditExportFrom] = useState(() => {
    const from = new Date();
    from.setDate(from.getDate() - 30);
    return formatDateTimeInput(from);
  });
  const [auditExportTo, setAuditExportTo] = useState(() => formatDateTimeInput(new Date()));
  const maxExportRangeDays = retentionInfo?.exportMaxRangeDays ?? 90;

  const suppressAuditUrlSyncRef = useRef(false);

  const syncAuditUrlFilters = (values: {
    username: string;
    eventType: string;
    category: string;
    nodeId: string;
    from?: string;
    to?: string;
  }) => {
    const params = new URLSearchParams(location.search);
    const setOrDelete = (key: string, value?: string) => {
      const trimmed = value?.trim() ?? '';
      if (trimmed) {
        params.set(key, trimmed);
      } else {
        params.delete(key);
      }
    };

    setOrDelete(AUDIT_QUERY_KEYS.user, values.username);
    setOrDelete(AUDIT_QUERY_KEYS.eventType, values.eventType);
    setOrDelete(AUDIT_QUERY_KEYS.category, values.category);
    setOrDelete(AUDIT_QUERY_KEYS.nodeId, values.nodeId);
    setOrDelete(AUDIT_QUERY_KEYS.from, values.from);
    setOrDelete(AUDIT_QUERY_KEYS.to, values.to);

    suppressAuditUrlSyncRef.current = true;
    const nextSearch = params.toString();
    navigate({ pathname: location.pathname, search: nextSearch ? `?${nextSearch}` : '' }, { replace: true });
  };

  const buildAuditExportFilename = (
    downloadLabel: string,
    filters: { username?: string; eventType?: string; category?: string; nodeId?: string; preset?: string }
  ) => {
    const parts: string[] = ['audit_logs', downloadLabel];

    const username = (filters.username ?? '').trim();
    const eventType = (filters.eventType ?? '').trim();
    const category = (filters.category ?? '').trim();
    const nodeId = (filters.nodeId ?? '').trim();
    const preset = (filters.preset ?? '').trim();

    if (preset && preset !== 'custom') {
      parts.push(`preset-${sanitizeFilenameSegment(preset)}`);
    }
    if (username) {
      parts.push(`user-${sanitizeFilenameSegment(username)}`);
    }
    if (eventType) {
      parts.push(`event-${sanitizeFilenameSegment(eventType)}`);
    }
    if (category) {
      parts.push(`cat-${sanitizeFilenameSegment(category)}`);
    }
    if (nodeId) {
      parts.push(`node-${nodeId.slice(0, 8)}`);
    }

    const raw = parts.filter(Boolean).join('_').replace(/_+/g, '_');
    const truncated = raw.length > 180 ? raw.slice(0, 180) : raw;
    return `${truncated}.csv`;
  };

  const normalizeAuditEventType = (input: string) => {
    const trimmed = input.trim();
    if (!trimmed) {
      return '';
    }

    const directMatch = auditEventTypes.find((item) => item.eventType === trimmed);
    if (directMatch) {
      return trimmed;
    }

    const canonical = trimmed.toUpperCase().replace(/\s+/g, '_');
    const canonicalMatch = auditEventTypes.find((item) => item.eventType === canonical);
    if (canonicalMatch) {
      return canonical;
    }

    const normalizedLabel = trimmed.toLowerCase();
    const labelMatch = auditEventTypes.find((item) => (
      formatAuditEventTypeLabel(item.eventType).toLowerCase() === normalizedLabel
    ));
    if (labelMatch) {
      return labelMatch.eventType;
    }

    return canonical;
  };

  const resolveAuditExportRange = () => {
    const fallbackTo = new Date();
    const fallbackFrom = new Date();
    fallbackFrom.setDate(fallbackFrom.getDate() - 30);

    const fromInput = auditExportFrom?.trim() ? new Date(auditExportFrom) : fallbackFrom;
    const toInput = auditExportTo?.trim() ? new Date(auditExportTo) : fallbackTo;
    return { fromInput, toInput };
  };

  const getAuditExportRangeError = (fromInput: Date, toInput: Date) => {
    if (Number.isNaN(fromInput.getTime()) || Number.isNaN(toInput.getTime())) {
      return 'Invalid audit export date range';
    }
    if (fromInput >= toInput) {
      return 'Audit export start time must be before end time';
    }
    if (maxExportRangeDays > 0) {
      const maxRangeMs = maxExportRangeDays * 24 * 60 * 60 * 1000;
      if (toInput.getTime() - fromInput.getTime() > maxRangeMs) {
        return `Audit export range cannot exceed ${maxExportRangeDays} days`;
      }
    }
    return null;
  };

  const { fromInput: previewFrom, toInput: previewTo } = resolveAuditExportRange();
  const isCustomExport = auditExportPreset === 'custom';
  const auditExportRangeError = isCustomExport ? getAuditExportRangeError(previewFrom, previewTo) : null;
  const auditExportHelperText = isCustomExport
    ? (auditExportRangeError
        ?? (maxExportRangeDays > 0 ? `Max range: ${maxExportRangeDays} days` : 'No max range limit'))
    : 'Preset selected';
  const exportPresetNeedsUser = auditExportPreset === 'user';
  const exportPresetNeedsEvent = auditExportPreset === 'event';
  const exportPresetError = (exportPresetNeedsUser && !auditFilterUser.trim())
    || (exportPresetNeedsEvent && !auditFilterEventType.trim());

  const resolveApiBaseUrl = () => {
    return process.env.REACT_APP_API_URL
      || process.env.REACT_APP_API_BASE_URL
      || '/api/v1';
  };

  const resolveAccessToken = async () => {
    const refreshedToken = await authService.refreshToken().catch(() => undefined);
    return refreshedToken
      || authService.getToken()
      || localStorage.getItem('token')
      || localStorage.getItem('access_token')
      || '';
  };

  const readResponseErrorMessage = async (response: Response, fallback: string) => {
    try {
      const text = await response.text();
      if (!text) {
        return fallback;
      }
      try {
        const payload = JSON.parse(text) as { message?: string; error?: string };
        return payload.message || payload.error || text;
      } catch {
        return text;
      }
    } catch {
      return fallback;
    }
  };

  const buildAuditExportRequest = (): {
    params: URLSearchParams;
    payload: AuditExportRequestPayload;
    downloadLabel: string;
    nodeId: string;
    eventType: string;
  } => {
    let downloadLabel = format(new Date(), 'yyyyMMdd');
    const nodeId = auditFilterNodeId.trim();
    const eventType = normalizeAuditEventType(auditFilterEventType);
    if (nodeId && !UUID_PATTERN.test(nodeId)) {
      throw new Error('Invalid Node ID (expected UUID)');
    }

    const params = new URLSearchParams();
    const payload: AuditExportRequestPayload = {};

    if (auditExportPreset && auditExportPreset !== 'custom') {
      params.append('preset', auditExportPreset);
      payload.preset = auditExportPreset;
      const presetDays = Math.min(maxExportRangeDays || 30, 30);
      if (auditExportPreset === 'user' && auditFilterUser.trim()) {
        params.append('username', auditFilterUser.trim());
        params.append('days', String(presetDays));
        payload.username = auditFilterUser.trim();
        payload.days = presetDays;
      }
      if (auditExportPreset === 'event' && eventType) {
        params.append('eventType', eventType);
        params.append('days', String(presetDays));
        payload.eventType = eventType;
        payload.days = presetDays;
      }
      if (auditFilterCategory.trim()) {
        params.append('category', auditFilterCategory.trim());
        payload.category = auditFilterCategory.trim();
      }
      if (nodeId) {
        params.append('nodeId', nodeId);
        payload.nodeId = nodeId;
      }
    } else {
      const { fromInput, toInput } = resolveAuditExportRange();
      const rangeError = getAuditExportRangeError(fromInput, toInput);
      if (rangeError) {
        throw new Error(rangeError);
      }
      downloadLabel = `${format(fromInput, 'yyyyMMdd')}_to_${format(toInput, 'yyyyMMdd')}`;
      const fromValue = formatDateTimeOffset(fromInput);
      const toValue = formatDateTimeOffset(toInput);
      params.append('from', fromValue);
      params.append('to', toValue);
      payload.from = fromValue;
      payload.to = toValue;
      if (auditFilterUser.trim()) {
        params.append('username', auditFilterUser.trim());
        payload.username = auditFilterUser.trim();
      }
      if (eventType) {
        params.append('eventType', eventType);
        payload.eventType = eventType;
      }
      if (auditFilterCategory.trim()) {
        params.append('category', auditFilterCategory.trim());
        payload.category = auditFilterCategory.trim();
      }
      if (nodeId) {
        params.append('nodeId', nodeId);
        payload.nodeId = nodeId;
      }
    }

    return {
      params,
      payload,
      downloadLabel,
      nodeId,
      eventType,
    };
  };

  const auditCategoryLabels: Record<string, string> = {
    NODE: 'Nodes',
    VERSION: 'Versions',
    RULE: 'Rules',
    WORKFLOW: 'Workflows',
    MAIL: 'Mail',
    INTEGRATION: 'Integrations',
    SECURITY: 'Security',
    PDF: 'PDF',
    OTHER: 'Other',
  };

  const formatAuditCategoryLabel = (category: string) => {
    return auditCategoryLabels[category]
      || category
        .toLowerCase()
        .replace(/_/g, ' ')
        .replace(/\b\w/g, (ch) => ch.toUpperCase());
  };

  const formatAuditEventTypeLabel = (eventType: string) => {
    if (!eventType) {
      return '';
    }
    return eventType
      .toLowerCase()
      .replace(/_/g, ' ')
      .replace(/\b\w/g, (ch) => ch.toUpperCase());
  };

  const auditCategoryOptions = (auditCategories.length > 0
    ? auditCategories.map((category) => category.category)
    : Object.keys(auditCategoryLabels)
  ).map((category) => ({
    value: category,
    label: formatAuditCategoryLabel(category),
  }));

  const fetchDashboard = async () => {
    try {
      setLoadingDashboard(true);
      setAuditCategoriesLoading(true);
      const auditAsyncQuery = new URLSearchParams();
      auditAsyncQuery.set('limit', String(AUDIT_EXPORT_ASYNC_TASK_LIMIT));
      if (auditExportAsyncStatusFilter !== 'ALL') {
        auditAsyncQuery.set('status', auditExportAsyncStatusFilter);
      }
      const auditAsyncSummaryQuery = new URLSearchParams();
      if (auditExportAsyncStatusFilter !== 'ALL') {
        auditAsyncSummaryQuery.set('status', auditExportAsyncStatusFilter);
      }
      const [dashboardRes, logsRes, licenseRes, retentionRes, auditReportRes, ruleSummaryRes, ruleEventsRes, presetsRes, categoriesRes, eventTypesRes, auditExportAsyncRes, auditExportAsyncSummaryRes] = await Promise.all([
        apiService.get<DashboardData>('/analytics/dashboard'),
        apiService.get<AuditLog[]>('/analytics/audit/recent?limit=10'),
        apiService.get<LicenseInfo>('/system/license').catch(() => null),
        apiService.get<AuditRetentionInfo>('/analytics/audit/retention').catch(() => null),
        apiService.get<AuditReportSummary>('/analytics/audit/report?days=30').catch(() => null),
        apiService.get<RuleExecutionSummary>('/analytics/rules/summary?days=7').catch(() => null),
        apiService.get<AuditLog[]>('/analytics/rules/recent?limit=20').catch(() => []),
        apiService.get<AuditPreset[]>('/analytics/audit/presets').catch(() => []),
        apiService.get<AuditCategorySetting[]>('/analytics/audit/categories').catch(() => []),
        apiService.get<AuditEventTypeOption[]>('/analytics/audit/event-types?limit=50').catch(() => []),
        apiService
          .get<AuditExportAsyncListResponse>(`/analytics/audit/export-async?${auditAsyncQuery.toString()}`)
          .catch(() => ({ count: 0, items: [] })),
        apiService
          .get<AuditExportAsyncSummaryResponse>(`/analytics/audit/export-async/summary?${auditAsyncSummaryQuery.toString()}`)
          .catch(() => null),
      ]);
      setData(dashboardRes);
      const auditParams = new URLSearchParams(location.search);
      const hasAuditFiltersInUrl = Boolean(
        auditParams.get(AUDIT_QUERY_KEYS.nodeId)
        || auditParams.get(AUDIT_QUERY_KEYS.user)
        || auditParams.get(AUDIT_QUERY_KEYS.eventType)
        || auditParams.get(AUDIT_QUERY_KEYS.category)
        || auditParams.get(AUDIT_QUERY_KEYS.from)
        || auditParams.get(AUDIT_QUERY_KEYS.to)
      );
      if (!hasAuditFiltersInUrl) {
        setLogs(logsRes);
      }
      setLicenseInfo(licenseRes);
      setRetentionInfo(retentionRes);
      setAuditReport(auditReportRes);
      setRuleSummary(ruleSummaryRes);
      setRuleEvents(ruleEventsRes || []);
      setAuditPresets(presetsRes || []);
      setAuditCategories(categoriesRes || []);
      setAuditEventTypes(eventTypesRes || []);
      setAuditExportAsyncTasks(Array.isArray(auditExportAsyncRes?.items) ? auditExportAsyncRes.items : []);
      setAuditExportAsyncSummary(auditExportAsyncSummaryRes);
      setAuditUserSuggestions((dashboardRes?.topUsers || []).map((user) => user.username));
    } catch {
      toast.error('Failed to load dashboard data');
    } finally {
      setLoadingDashboard(false);
      setAuditCategoriesLoading(false);
    }
  };

  const loadAsyncExportHealthOverview = async (silent = false) => {
    if (!silent) {
      setAsyncExportHealthLoading(true);
    }

    try {
      const overview = await apiService.get<AsyncExportGovernanceOverviewResponse>('/analytics/async-governance/overview');
      const domainsFromOverview = Array.isArray(overview?.domains) ? overview.domains : [];
      const overviewDomainMap = new Map<string, AsyncExportGovernanceOverviewDomain>();
      domainsFromOverview.forEach((domain) => {
        if (!domain?.key) {
          return;
        }
        overviewDomainMap.set(String(domain.key).trim().toLowerCase(), domain);
      });

      const next = ASYNC_EXPORT_HEALTH_DOMAINS.map((domain) => {
        const source = overviewDomainMap.get(domain.key);
        if (!source) {
          return {
            ...domain,
            status: 'degraded' as const,
            riskLevel: 'CRITICAL' as const,
            summary: emptyAsyncExportHealthSummary(),
            error: 'missing-domain-summary',
          };
        }
        const status: 'healthy' | 'degraded' =
          String(source.status || '').trim().toUpperCase() === 'DEGRADED' ? 'degraded' : 'healthy';
        const summary = normalizeAsyncExportHealthSummary(source as unknown as Record<string, unknown>);
        const riskLevel = source.riskLevel
          ? normalizeAsyncExportRiskLevel(source.riskLevel)
          : inferAsyncExportRiskLevel(summary, status === 'degraded');
        return {
          ...domain,
          status,
          riskLevel: riskLevel || inferAsyncExportRiskLevel(summary, status === 'degraded'),
          summary,
          error: source.error?.trim() || null,
        };
      });

      const degradedCount = next.filter((domain) => domain.status === 'degraded').length;
      const overviewStatus = String(overview?.overallStatus || '').trim().toUpperCase();
      const overallStatus: 'healthy' | 'degraded' =
        overviewStatus === 'DEGRADED' || degradedCount > 0 ? 'degraded' : 'healthy';
      const baselineRisk = normalizeAsyncExportRiskLevel(overview?.overallRiskLevel);
      const overallRiskLevel = next.reduce(
        (risk, domain) => maxAsyncExportRiskLevel(risk, domain.riskLevel),
        overallStatus === 'degraded' ? maxAsyncExportRiskLevel(baselineRisk, 'CRITICAL') : baselineRisk
      );

      setAsyncExportHealthDomains(next);
      setAsyncExportHealthOverallStatus(overallStatus);
      setAsyncExportHealthOverallRiskLevel(overallRiskLevel);
      setAsyncExportHealthUpdatedAt(overview?.generatedAt || new Date().toISOString());

      if (!silent && degradedCount > 0) {
        if (degradedCount === ASYNC_EXPORT_HEALTH_DOMAINS.length) {
          toast.error('Failed to load async task health overview');
        } else {
          toast.warning(`Async task health overview degraded (${degradedCount}/${ASYNC_EXPORT_HEALTH_DOMAINS.length})`);
        }
      }
      return;
    } catch {
      // Fallback to multi-endpoint client-side aggregation.
    }

    try {
      const settled = await Promise.allSettled(
        ASYNC_EXPORT_HEALTH_DOMAINS.map((domain) => (
          domain.endpoint
            ? apiService.get<Record<string, unknown>>(domain.endpoint)
            : Promise.reject(new Error('overview-required'))
        ))
      );

      let degradedCount = 0;
      let overallRiskLevel: AsyncExportRiskLevel = 'LOW';
      const next = ASYNC_EXPORT_HEALTH_DOMAINS.map((domain, index) => {
        const result = settled[index];
        if (result.status === 'fulfilled') {
          const summary = normalizeAsyncExportHealthSummary(result.value);
          const riskLevel = inferAsyncExportRiskLevel(summary, false);
          overallRiskLevel = maxAsyncExportRiskLevel(overallRiskLevel, riskLevel);
          return {
            ...domain,
            status: 'healthy' as const,
            riskLevel,
            summary,
            error: null,
          };
        }
        degradedCount += 1;
        overallRiskLevel = maxAsyncExportRiskLevel(overallRiskLevel, 'CRITICAL');
        return {
          ...domain,
          status: 'degraded' as const,
          riskLevel: 'CRITICAL' as const,
          summary: emptyAsyncExportHealthSummary(),
          error: domain.endpoint ? 'failed-to-load' : 'overview-required',
        };
      });

      setAsyncExportHealthDomains(next);
      setAsyncExportHealthOverallStatus(degradedCount > 0 ? 'degraded' : 'healthy');
      setAsyncExportHealthOverallRiskLevel(overallRiskLevel);
      setAsyncExportHealthUpdatedAt(new Date().toISOString());

        if (!silent && degradedCount > 0) {
          if (degradedCount === ASYNC_EXPORT_HEALTH_DOMAINS.length) {
            toast.error('Failed to load async task health overview');
          } else {
            toast.warning(`Async task health overview degraded (${degradedCount}/${ASYNC_EXPORT_HEALTH_DOMAINS.length})`);
          }
        }
    } catch {
      if (!silent) {
        toast.error('Failed to load async task health overview');
      }
      setAsyncExportHealthDomains(
        ASYNC_EXPORT_HEALTH_DOMAINS.map((domain) => ({
          ...domain,
          status: 'degraded',
          riskLevel: 'CRITICAL',
          summary: emptyAsyncExportHealthSummary(),
          error: 'failed-to-load',
        }))
      );
      setAsyncExportHealthOverallStatus('degraded');
      setAsyncExportHealthOverallRiskLevel('CRITICAL');
      setAsyncExportHealthUpdatedAt(new Date().toISOString());
    } finally {
      setAsyncExportHealthLoading(false);
    }
  };

  const loadRecentAsyncTasks = async (
    silent = false,
    maxItems: number = recentAsyncTasksMaxItems,
    domainFilter: RecentAsyncTaskDomainFilter = recentAsyncTasksDomainFilter,
    statusFilter: RecentAsyncTaskStatusFilter = recentAsyncTasksStatusFilter,
    includeAcknowledged: boolean = recentAsyncTasksIncludeAcknowledged
  ) => {
    try {
      setRecentAsyncTasksLoading(true);
      setRecentAsyncTasksError(null);
      const params = new URLSearchParams();
      params.set('maxItems', String(maxItems));
      if (domainFilter !== 'ALL') {
        params.set('domain', domainFilter);
      }
      if (statusFilter !== 'ALL') {
        params.set('status', statusFilter);
      }
      if (includeAcknowledged) {
        params.set('includeAcknowledged', 'true');
      }
      const response = await apiService.get<RecentAsyncTaskListResponse>(
        `/analytics/async-governance/tasks?${params.toString()}`
      );
      setRecentAsyncTasks(Array.isArray(response?.items) ? response.items : []);
      setRecentAsyncTasksTotalCount(
        Number.isFinite(response?.totalCount) ? Number(response.totalCount) : (Array.isArray(response?.items) ? response.items.length : 0)
      );
      setRecentAsyncTasksUpdatedAt(response?.generatedAt || new Date().toISOString());
      return;
    } catch {
      setRecentAsyncTasks([]);
      setRecentAsyncTasksTotalCount(0);
      setRecentAsyncTasksError('Failed to load recent async tasks');
      setRecentAsyncTasksUpdatedAt(new Date().toISOString());
      if (!silent) {
        toast.error('Failed to load recent async tasks');
      }
    } finally {
      setRecentAsyncTasksLoading(false);
    }
  };

  const handleRecentAsyncTaskAction = async (
    task: RecentAsyncTaskItem,
    actionType: RecentAsyncTaskActionType
  ) => {
    const taskKey = `${task.domainKey}:${task.taskId}`;
    const actionUrl = normalizeRecentAsyncTaskActionUrl(
      actionType === 'cancel'
        ? task.cancelUrl
        : actionType === 'download'
          ? task.downloadUrl
          : actionType === 'cleanup'
            ? task.cleanupUrl
            : undefined
    );

    if ((actionType === 'cancel' || actionType === 'download' || actionType === 'cleanup') && !actionUrl) {
      return;
    }

    try {
      setRecentAsyncTasksActionTaskKey(taskKey);
      setRecentAsyncTasksActionType(actionType);
      if (actionType === 'download') {
        await apiService.downloadFile(actionUrl!, task.filename || `${task.taskId}.bin`);
        toast.success(`Downloaded async task artifact: ${task.taskId}`);
        return;
      }

      if (actionType === 'acknowledge' || actionType === 'unacknowledge') {
        if (!task.fingerprint) {
          return;
        }
        const response = await apiService.post<RecentAsyncTaskAcknowledgementResponse>(
          `/analytics/async-governance/tasks/${actionType === 'acknowledge' ? 'acknowledge' : 'unacknowledge'}`,
          {
            domainKey: task.domainKey,
            taskId: task.taskId,
            fingerprint: task.fingerprint,
          }
        );
        if (actionType === 'acknowledge') {
          toast.success(response?.changed ? `Acknowledged async task: ${task.taskId}` : `Async task already acknowledged: ${task.taskId}`);
        } else {
          toast.success(response?.changed ? `Restored async task: ${task.taskId}` : `Async task was already visible: ${task.taskId}`);
        }
      } else {
        await apiService.post(actionUrl!, {});
        if (actionType === 'cancel') {
          toast.success(`Async task updated: ${task.taskId}`);
        } else {
          toast.success(`Async cleanup triggered: ${task.domainLabel || task.domainKey}`);
        }
      }
      await loadRecentAsyncTasks(
        true,
        recentAsyncTasksMaxItems,
        recentAsyncTasksDomainFilter,
        recentAsyncTasksStatusFilter,
        recentAsyncTasksIncludeAcknowledged
      );
    } catch {
      if (actionType === 'download') {
        toast.error(`Failed to download async task artifact: ${task.taskId}`);
      } else if (actionType === 'cancel') {
        toast.error(`Failed to update async task: ${task.taskId}`);
      } else if (actionType === 'acknowledge') {
        toast.error(`Failed to acknowledge async task: ${task.taskId}`);
      } else if (actionType === 'unacknowledge') {
        toast.error(`Failed to restore async task: ${task.taskId}`);
      } else {
        toast.error(`Failed to trigger async cleanup: ${task.domainLabel || task.domainKey}`);
      }
    } finally {
      setRecentAsyncTasksActionTaskKey(null);
      setRecentAsyncTasksActionType(null);
    }
  };

  const handleToggleAuditCategory = async (category: string) => {
    if (auditCategoriesUpdating) {
      return;
    }
    const previous = auditCategories;
    const next = auditCategories.map((item) => (
      item.category === category ? { ...item, enabled: !item.enabled } : item
    ));
    setAuditCategories(next);
    setAuditCategoriesUpdating(true);
    try {
      const updated = await apiService.put<AuditCategorySetting[]>('/analytics/audit/categories', next);
      setAuditCategories(updated || next);
      toast.success('Audit categories updated');
    } catch {
      setAuditCategories(previous);
      toast.error('Failed to update audit categories');
    } finally {
      setAuditCategoriesUpdating(false);
    }
  };

  const loadPinnedSearches = async () => {
    setPinnedLoading(true);
    try {
      const searches = await savedSearchService.list();
      setPinnedSearches(searches.filter((item) => item.pinned));
      setPinnedError(null);
    } catch {
      setPinnedError('Failed to load pinned saved searches');
      setPinnedSearches([]);
    } finally {
      setPinnedLoading(false);
    }
  };

  const fetchMailFetchSummary = async (options?: { silent?: boolean }) => {
    const silent = options?.silent === true;
    if (!silent) {
      setMailFetchSummaryLoading(true);
    }
    setMailFetchSummaryError(null);
    try {
      const summary = await mailAutomationService.getFetchSummary();
      setMailFetchSummary(summary);
    } catch {
      setMailFetchSummaryError('Failed to load mail fetch summary');
    } finally {
      setMailFetchSummaryLoading(false);
    }
  };

  const handleTriggerMailFetch = async () => {
    if (mailFetchTriggering) {
      return;
    }
    setMailFetchTriggering(true);
    try {
      await mailAutomationService.triggerFetch();
      toast.success('Mail fetch triggered');
      await fetchMailFetchSummary();
    } catch {
      toast.error('Failed to trigger mail fetch');
    } finally {
      setMailFetchTriggering(false);
    }
  };

  const handleRunPinnedSearch = async (item: SavedSearch) => {
    try {
      await dispatch(executeSavedSearch(item.id)).unwrap();
      dispatch(setLastSearchCriteria(buildSearchCriteriaFromSavedSearch(item)));
      navigate('/search-results');
    } catch {
      toast.error('Failed to execute saved search');
    }
  };

  const handleTogglePinnedSearch = (item: SavedSearch) => {
    savedSearchService
      .setPinned(item.id, !item.pinned)
      .then(() => loadPinnedSearches())
      .catch(() => toast.error('Failed to update pin'));
  };

  const handleExportAuditLogs = async () => {
    try {
      setExportingAudit(true);
      const {
        params,
        downloadLabel,
        nodeId,
        eventType,
      } = buildAuditExportRequest();

      const accessToken = await resolveAccessToken();
      const response = await fetch(
        `${resolveApiBaseUrl()}/analytics/audit/export?${params.toString()}`,
        {
          headers: {
            Authorization: accessToken ? `Bearer ${accessToken}` : '',
          },
        }
      );

      if (!response.ok) {
        throw new Error(await readResponseErrorMessage(response, 'Failed to export audit logs'));
      }

      const exportCountHeader = response.headers.get('X-Audit-Export-Count');
      const exportCount = exportCountHeader ? Number.parseInt(exportCountHeader, 10) : null;
      const blob = await response.blob();
      const url = window.URL.createObjectURL(blob);
      const a = document.createElement('a');
      a.href = url;
      a.download = buildAuditExportFilename(downloadLabel, {
        preset: auditExportPreset,
        username: auditFilterUser,
        eventType,
        category: auditFilterCategory,
        nodeId,
      });
      document.body.appendChild(a);
      a.click();
      window.URL.revokeObjectURL(url);
      document.body.removeChild(a);

      if (exportCount === 0) {
        toast.info('No audit logs found for selected range');
      } else {
        toast.success('Audit logs exported successfully');
      }
    } catch (error) {
      console.error(error);
      const message = error instanceof Error && error.message
        ? error.message
        : 'Failed to export audit logs';
      toast.error(message);
    } finally {
      setExportingAudit(false);
    }
  };

  const loadAuditExportAsyncSummary = async (
    silent = false,
    statusFilter: AuditExportAsyncTaskStatusFilter = auditExportAsyncStatusFilter
  ) => {
    try {
      if (!silent) {
        setAuditExportAsyncSummaryLoading(true);
      }
      const params = new URLSearchParams();
      if (statusFilter !== 'ALL') {
        params.set('status', statusFilter);
      }
      const response = await apiService.get<AuditExportAsyncSummaryResponse>(
        `/analytics/audit/export-async/summary?${params.toString()}`
      );
      setAuditExportAsyncSummary(response);
    } catch {
      if (!silent) {
        toast.error('Failed to refresh audit async export summary');
      }
    } finally {
      if (!silent) {
        setAuditExportAsyncSummaryLoading(false);
      }
    }
  };

  const loadAuditExportAsyncTasks = async (
    silent = false,
    statusFilter: AuditExportAsyncTaskStatusFilter = auditExportAsyncStatusFilter
  ) => {
    try {
      setAuditExportAsyncTasksLoading(true);
      const params = new URLSearchParams();
      params.set('limit', String(AUDIT_EXPORT_ASYNC_TASK_LIMIT));
      if (statusFilter !== 'ALL') {
        params.set('status', statusFilter);
      }
      const response = await apiService.get<AuditExportAsyncListResponse>(
        `/analytics/audit/export-async?${params.toString()}`
      );
      setAuditExportAsyncTasks(Array.isArray(response?.items) ? response.items : []);
      await loadAuditExportAsyncSummary(silent, statusFilter);
    } catch {
      if (!silent) {
        toast.error('Failed to refresh audit async export tasks');
      }
    } finally {
      setAuditExportAsyncTasksLoading(false);
    }
  };

  const handleStartAuditExportAsync = async () => {
    try {
      setAuditExportAsyncStarting(true);
      const { payload } = buildAuditExportRequest();
      const response = await apiService.post<AuditExportAsyncCreateResponse>(
        '/analytics/audit/export-async',
        payload
      );
      const taskId = response?.taskId?.trim();
      if (!taskId) {
        throw new Error('Missing task id');
      }
      toast.success(`Audit async export task started: ${taskId}`);
      await loadAuditExportAsyncTasks(true);
    } catch (error) {
      const message = error instanceof Error && error.message
        ? error.message
        : 'Failed to start audit async export';
      toast.error(message);
    } finally {
      setAuditExportAsyncStarting(false);
    }
  };

  const handleCancelAuditExportAsyncTask = async (taskId: string) => {
    try {
      setAuditExportAsyncActionTaskId(taskId);
      setAuditExportAsyncActionType('cancel');
      await apiService.post(`/analytics/audit/export-async/${encodeURIComponent(taskId)}/cancel`, {});
      toast.success(`Audit async export task cancelled: ${taskId}`);
      await loadAuditExportAsyncTasks(true);
    } catch {
      toast.error(`Failed to cancel audit async export task: ${taskId}`);
    } finally {
      setAuditExportAsyncActionTaskId(null);
      setAuditExportAsyncActionType(null);
    }
  };

  const handleDownloadAuditExportAsyncTask = async (task: AuditExportAsyncTask) => {
    try {
      setAuditExportAsyncActionTaskId(task.taskId);
      setAuditExportAsyncActionType('download');
      const latest = await apiService.get<AuditExportAsyncTask>(
        `/analytics/audit/export-async/${encodeURIComponent(task.taskId)}`
      ).catch(() => task);
      const accessToken = await resolveAccessToken();
      const response = await fetch(
        `${resolveApiBaseUrl()}/analytics/audit/export-async/${encodeURIComponent(task.taskId)}/download`,
        {
          headers: {
            Authorization: accessToken ? `Bearer ${accessToken}` : '',
          },
        }
      );
      if (!response.ok) {
        throw new Error(await readResponseErrorMessage(response, 'Failed to download audit async export'));
      }
      const blob = await response.blob();
      const fallbackFilename = `audit_logs_async_${format(new Date(), 'yyyyMMdd_HHmmss')}.csv`;
      const filename = (latest.filename || task.filename || '').trim() || fallbackFilename;
      const url = window.URL.createObjectURL(blob);
      const a = document.createElement('a');
      a.href = url;
      a.download = filename;
      document.body.appendChild(a);
      a.click();
      window.URL.revokeObjectURL(url);
      document.body.removeChild(a);
      toast.success(`Audit async export downloaded: ${filename}`);
    } catch {
      toast.error(`Failed to download audit async export task: ${task.taskId}`);
    } finally {
      setAuditExportAsyncActionTaskId(null);
      setAuditExportAsyncActionType(null);
    }
  };

  const handleCleanupAuditExportAsyncTasks = async () => {
    const cleanupStatusFilter = (
      auditExportAsyncStatusFilter === 'COMPLETED'
      || auditExportAsyncStatusFilter === 'CANCELLED'
      || auditExportAsyncStatusFilter === 'FAILED'
    ) ? auditExportAsyncStatusFilter : null;
    const query = cleanupStatusFilter ? `?status=${encodeURIComponent(cleanupStatusFilter)}` : '';

    try {
      setAuditExportAsyncCleaning(true);
      const response = await apiService.post<AuditExportAsyncCleanupResponse>(
        `/analytics/audit/export-async/cleanup${query}`,
        {}
      );
      const deletedCount = Number.isFinite(response?.deletedCount) ? response.deletedCount : 0;
      if (deletedCount > 0) {
        toast.success(response?.message || `Cleaned ${deletedCount} audit async export tasks`);
      } else {
        toast.info(response?.message || 'No audit async export tasks matched cleanup filter');
      }
      await loadAuditExportAsyncTasks(true, auditExportAsyncStatusFilter);
    } catch (error) {
      const message = error instanceof Error && error.message
        ? error.message
        : 'Failed to cleanup audit async export tasks';
      toast.error(message);
    } finally {
      setAuditExportAsyncCleaning(false);
    }
  };

  const handleCancelActiveAuditExportAsyncTasks = async () => {
    const activeStatusFilter = (
      auditExportAsyncStatusFilter === 'QUEUED'
      || auditExportAsyncStatusFilter === 'RUNNING'
    ) ? auditExportAsyncStatusFilter : null;
    const query = activeStatusFilter ? `?status=${encodeURIComponent(activeStatusFilter)}` : '';
    try {
      setAuditExportAsyncCancellingActive(true);
      const response = await apiService.post<AuditExportAsyncCancelActiveResponse>(
        `/analytics/audit/export-async/cancel-active${query}`,
        {}
      );
      const cancelledCount = Number.isFinite(response?.cancelledCount) ? response.cancelledCount : 0;
      if (cancelledCount > 0) {
        toast.success(response?.message || `Cancelled ${cancelledCount} active audit async export tasks`);
      } else {
        toast.info(response?.message || 'No active async audit export tasks matched cancel-active filter');
      }
      await loadAuditExportAsyncTasks(true, auditExportAsyncStatusFilter);
    } catch (error) {
      const message = error instanceof Error && error.message
        ? error.message
        : 'Failed to cancel active audit async export tasks';
      toast.error(message);
    } finally {
      setAuditExportAsyncCancellingActive(false);
    }
  };

  const loadBatchDownloadAdminTasks = async (
    silent = false,
    statusFilter: BatchDownloadTaskStatusFilter = batchDownloadAdminStatusFilter,
    page: number = batchDownloadAdminPage,
    rowsPerPage: number = batchDownloadAdminRowsPerPage,
    query: string = batchDownloadAdminQuery,
    owner: string = batchDownloadAdminOwnerFilter
  ): Promise<void> => {
    try {
      setBatchDownloadAdminTasksLoading(true);
      setBatchDownloadAdminError(null);
      const [listResponse, summaryResponse] = await Promise.all([
        nodeService.listBatchDownloadAsyncTasks(
          rowsPerPage,
          statusFilter !== 'ALL' ? statusFilter : undefined,
          page * rowsPerPage,
          query,
          owner
        ),
        nodeService.getBatchDownloadAsyncSummary(),
      ]);
      const items = Array.isArray(listResponse?.items) ? listResponse.items : [];
      const paging = {
        maxItems: Number.isFinite(listResponse?.paging?.maxItems) ? Number(listResponse?.paging?.maxItems) : rowsPerPage,
        skipCount: Number.isFinite(listResponse?.paging?.skipCount) ? Number(listResponse?.paging?.skipCount) : page * rowsPerPage,
        totalItems: Number.isFinite(listResponse?.paging?.totalItems) ? Number(listResponse?.paging?.totalItems) : items.length,
        hasMoreItems: Boolean(listResponse?.paging?.hasMoreItems),
      };
      if (page > 0 && items.length === 0 && paging.totalItems <= page * rowsPerPage) {
        const previousPage = page - 1;
        setBatchDownloadAdminPage(previousPage);
        return await loadBatchDownloadAdminTasks(silent, statusFilter, previousPage, rowsPerPage, query, owner);
      }
      setBatchDownloadAdminTasks(items);
      setBatchDownloadAdminPaging(paging);
      setBatchDownloadAdminSummary({
        totalCount: Number.isFinite(summaryResponse?.totalCount) ? summaryResponse.totalCount : items.length,
        activeCount: Number.isFinite(summaryResponse?.activeCount)
          ? summaryResponse.activeCount
          : items.filter((task) => BATCH_DOWNLOAD_ACTIVE_STATUSES.has(normalizeBatchDownloadTaskStatus(task.status))).length,
        terminalCount: Number(summaryResponse?.terminalCount || 0),
        queuedCount: Number(summaryResponse?.queuedCount || 0),
        runningCount: Number(summaryResponse?.runningCount || 0),
        cancelRequestedCount: Number(summaryResponse?.cancelRequestedCount || 0),
        cancelledCount: Number(summaryResponse?.cancelledCount || 0),
        completedCount: Number(summaryResponse?.completedCount || 0),
        failedCount: Number(summaryResponse?.failedCount || 0),
      });
      setBatchDownloadAdminUpdatedAt(new Date().toISOString());
    } catch {
      setBatchDownloadAdminTasks([]);
      setBatchDownloadAdminPaging({
        maxItems: rowsPerPage,
        skipCount: page * rowsPerPage,
        totalItems: 0,
        hasMoreItems: false,
      });
      setBatchDownloadAdminSummary({
        totalCount: 0,
        activeCount: 0,
        terminalCount: 0,
        queuedCount: 0,
        runningCount: 0,
        cancelRequestedCount: 0,
        cancelledCount: 0,
        completedCount: 0,
        failedCount: 0,
      });
      setBatchDownloadAdminError('Failed to refresh batch download tasks');
      if (!silent) {
        toast.error('Failed to refresh batch download tasks');
      }
    } finally {
      setBatchDownloadAdminTasksLoading(false);
    }
  };

  const handleCancelBatchDownloadAdminTask = async (taskId: string) => {
    try {
      setBatchDownloadAdminActionTaskId(taskId);
      setBatchDownloadAdminActionType('cancel');
      await nodeService.cancelBatchDownloadAsyncTask(taskId);
      toast.success(`Batch download task updated: ${taskId}`);
      await loadBatchDownloadAdminTasks(
        true,
        batchDownloadAdminStatusFilter,
        batchDownloadAdminPage,
        batchDownloadAdminRowsPerPage,
        batchDownloadAdminQuery,
        batchDownloadAdminOwnerFilter
      );
    } catch {
      toast.error(`Failed to cancel batch download task: ${taskId}`);
    } finally {
      setBatchDownloadAdminActionTaskId(null);
      setBatchDownloadAdminActionType(null);
    }
  };

  const handleCleanupBatchDownloadAdminTasks = async () => {
    const cleanupStatusFilter = (
      batchDownloadAdminStatusFilter === 'COMPLETED'
      || batchDownloadAdminStatusFilter === 'CANCELLED'
      || batchDownloadAdminStatusFilter === 'FAILED'
    ) ? batchDownloadAdminStatusFilter : undefined;

    try {
      setBatchDownloadAdminCleaning(true);
      const response = await nodeService.cleanupBatchDownloadAsyncTasks(cleanupStatusFilter);
      const deletedCount = Number.isFinite(response?.deletedCount) ? response.deletedCount : 0;
      if (deletedCount > 0) {
        toast.success(response?.message || `Cleaned ${deletedCount} batch download tasks`);
      } else {
        toast.info(response?.message || 'No batch download tasks matched cleanup filter');
      }
      await loadBatchDownloadAdminTasks(
        true,
        batchDownloadAdminStatusFilter,
        batchDownloadAdminPage,
        batchDownloadAdminRowsPerPage,
        batchDownloadAdminQuery,
        batchDownloadAdminOwnerFilter
      );
    } catch {
      toast.error('Failed to cleanup batch download tasks');
    } finally {
      setBatchDownloadAdminCleaning(false);
    }
  };

  const handleCancelActiveBatchDownloadAdminTasks = async () => {
    const activeStatusFilter = (
      batchDownloadAdminStatusFilter === 'QUEUED'
      || batchDownloadAdminStatusFilter === 'RUNNING'
      || batchDownloadAdminStatusFilter === 'CANCEL_REQUESTED'
    ) ? batchDownloadAdminStatusFilter : undefined;

    try {
      setBatchDownloadAdminCancellingActive(true);
      const response = await nodeService.cancelActiveBatchDownloadAsyncTasks(activeStatusFilter);
      const cancelledCount = Number.isFinite(response?.cancelledCount) ? response.cancelledCount : 0;
      if (cancelledCount > 0) {
        toast.success(response?.message || `Cancelled ${cancelledCount} batch download tasks`);
      } else {
        toast.info(response?.message || 'No active batch download tasks matched cancel filter');
      }
      await loadBatchDownloadAdminTasks(
        true,
        batchDownloadAdminStatusFilter,
        batchDownloadAdminPage,
        batchDownloadAdminRowsPerPage,
        batchDownloadAdminQuery,
        batchDownloadAdminOwnerFilter
      );
    } catch {
      toast.error('Failed to cancel active batch download tasks');
    } finally {
      setBatchDownloadAdminCancellingActive(false);
    }
  };

  const handleDownloadBatchDownloadAdminTask = async (task: BatchDownloadAsyncTask) => {
    try {
      setBatchDownloadAdminActionTaskId(task.taskId);
      setBatchDownloadAdminActionType('download');
      await nodeService.downloadBatchDownloadAsyncTask(task.taskId, task.filename);
      toast.success(`Batch download downloaded: ${task.filename || task.taskId}`);
    } catch {
      toast.error(`Failed to download batch download task: ${task.taskId}`);
    } finally {
      setBatchDownloadAdminActionTaskId(null);
      setBatchDownloadAdminActionType(null);
    }
  };

  const handleCleanupBatchDownloadAdminTask = async (task: BatchDownloadAsyncTask) => {
    try {
      setBatchDownloadAdminActionTaskId(task.taskId);
      setBatchDownloadAdminActionType('cleanup');
      const response = await nodeService.cleanupBatchDownloadAsyncTask(task.taskId);
      if (Number.isFinite(response?.deletedCount) && response.deletedCount > 0) {
        toast.success(response.message || `Cleaned batch download task ${task.taskId}`);
      } else {
        toast.info(response?.message || `Batch download task ${task.taskId} was already cleaned`);
      }
      await loadBatchDownloadAdminTasks(
        true,
        batchDownloadAdminStatusFilter,
        batchDownloadAdminPage,
        batchDownloadAdminRowsPerPage,
        batchDownloadAdminQuery,
        batchDownloadAdminOwnerFilter
      );
    } catch {
      toast.error(`Failed to cleanup batch download task: ${task.taskId}`);
    } finally {
      setBatchDownloadAdminActionTaskId(null);
      setBatchDownloadAdminActionType(null);
    }
  };

  const handleBatchDownloadAdminPrevPage = () => {
    if (batchDownloadAdminPage <= 0 || batchDownloadAdminTasksLoading) {
      return;
    }
    const previousPage = batchDownloadAdminPage - 1;
    setBatchDownloadAdminPage(previousPage);
    void loadBatchDownloadAdminTasks(
      true,
      batchDownloadAdminStatusFilter,
      previousPage,
      batchDownloadAdminRowsPerPage,
      batchDownloadAdminQuery,
      batchDownloadAdminOwnerFilter
    );
  };

  const handleBatchDownloadAdminNextPage = () => {
    if (!batchDownloadAdminPaging.hasMoreItems || batchDownloadAdminTasksLoading) {
      return;
    }
    const nextPage = batchDownloadAdminPage + 1;
    setBatchDownloadAdminPage(nextPage);
    void loadBatchDownloadAdminTasks(
      true,
      batchDownloadAdminStatusFilter,
      nextPage,
      batchDownloadAdminRowsPerPage,
      batchDownloadAdminQuery,
      batchDownloadAdminOwnerFilter
    );
  };

  const handleBatchDownloadAdminRowsPerPageChange = (value: number) => {
    const nextRows = Number.isFinite(value) ? value : BATCH_DOWNLOAD_ADMIN_DEFAULT_ROWS;
    setBatchDownloadAdminRowsPerPage(nextRows);
    setBatchDownloadAdminPage(0);
    void loadBatchDownloadAdminTasks(
      true,
      batchDownloadAdminStatusFilter,
      0,
      nextRows,
      batchDownloadAdminQuery,
      batchDownloadAdminOwnerFilter
    );
  };

  const handleApplyBatchDownloadAdminQuery = () => {
    const nextQuery = batchDownloadAdminQueryInput.trim();
    setBatchDownloadAdminQuery(nextQuery);
    setBatchDownloadAdminPage(0);
    void loadBatchDownloadAdminTasks(
      true,
      batchDownloadAdminStatusFilter,
      0,
      batchDownloadAdminRowsPerPage,
      nextQuery,
      batchDownloadAdminOwnerFilter
    );
  };

  const handleClearBatchDownloadAdminQuery = () => {
    setBatchDownloadAdminQueryInput('');
    setBatchDownloadAdminQuery('');
    setBatchDownloadAdminPage(0);
    void loadBatchDownloadAdminTasks(
      true,
      batchDownloadAdminStatusFilter,
      0,
      batchDownloadAdminRowsPerPage,
      '',
      batchDownloadAdminOwnerFilter
    );
  };

  const handleApplyBatchDownloadAdminOwner = () => {
    const nextOwner = batchDownloadAdminOwnerInput.trim();
    setBatchDownloadAdminOwnerFilter(nextOwner);
    setBatchDownloadAdminPage(0);
    void loadBatchDownloadAdminTasks(
      true,
      batchDownloadAdminStatusFilter,
      0,
      batchDownloadAdminRowsPerPage,
      batchDownloadAdminQuery,
      nextOwner
    );
  };

  const handleClearBatchDownloadAdminOwner = () => {
    setBatchDownloadAdminOwnerInput('');
    setBatchDownloadAdminOwnerFilter('');
    setBatchDownloadAdminPage(0);
    void loadBatchDownloadAdminTasks(
      true,
      batchDownloadAdminStatusFilter,
      0,
      batchDownloadAdminRowsPerPage,
      batchDownloadAdminQuery,
      ''
    );
  };

  const handleFilterAuditLogs = async (
    overrides: Partial<{
      username: string;
      eventType: string;
      category: string;
      nodeId: string;
    }> = {}
  ) => {
    const username = (overrides.username ?? auditFilterUser).trim();
    const eventType = normalizeAuditEventType(overrides.eventType ?? auditFilterEventType);
    const category = (overrides.category ?? auditFilterCategory).trim();
    const nodeId = (overrides.nodeId ?? auditFilterNodeId).trim();
    if (nodeId && !UUID_PATTERN.test(nodeId)) {
      toast.error('Invalid Node ID (expected UUID)');
      return;
    }

    syncAuditUrlFilters({
      username,
      eventType,
      category,
      nodeId,
      from: isCustomExport && auditExportFrom?.trim() ? auditExportFrom : undefined,
      to: isCustomExport && auditExportTo?.trim() ? auditExportTo : undefined,
    });

    try {
      setFilteringAudit(true);
      const { fromInput, toInput } = resolveAuditExportRange();
      const params = new URLSearchParams();
      if (username) {
        params.append('username', username);
      }
      if (eventType) {
        params.append('eventType', eventType);
      }
      if (category) {
        params.append('category', category);
      }
      if (nodeId) {
        params.append('nodeId', nodeId);
      }
      if (isCustomExport) {
        if (auditExportFrom?.trim()) {
          params.append('from', formatDateTimeOffset(fromInput));
        }
        if (auditExportTo?.trim()) {
          params.append('to', formatDateTimeOffset(toInput));
        }
      }
      const query = params.toString();
      const response = await apiService.get<PageResponse<AuditLog>>(
        `/analytics/audit/search${query ? `?${query}` : ''}`
      );
      setLogs(response.content || []);
    } catch {
      toast.error('Failed to filter audit logs');
    } finally {
      setFilteringAudit(false);
    }
  };

  const handleResetAuditLogs = async () => {
    try {
      setAuditFilterUser('');
      setAuditFilterEventType('');
      setAuditFilterCategory('');
      setAuditFilterNodeId('');
      syncAuditUrlFilters({
        username: '',
        eventType: '',
        category: '',
        nodeId: '',
        from: undefined,
        to: undefined,
      });
      const logsRes = await apiService.get<AuditLog[]>('/analytics/audit/recent?limit=10');
      setLogs(logsRes);
    } catch {
      toast.error('Failed to reload recent audit logs');
    }
  };

  const applyAuditQuickRange = (range: '24h' | '7d' | '30d') => {
    const to = new Date();
    const from = new Date(to);
    if (range === '24h') {
      from.setHours(from.getHours() - 24);
    } else if (range === '7d') {
      from.setDate(from.getDate() - 7);
    } else {
      from.setDate(from.getDate() - 30);
    }
    setAuditExportPreset('custom');
    setAuditExportFrom(formatDateTimeInput(from));
    setAuditExportTo(formatDateTimeInput(to));
    setAuditQuickRange(range);
  };

  const handleCleanupAuditLogs = async () => {
    if (!window.confirm('Are you sure you want to cleanup expired audit logs? This action cannot be undone.')) {
      return;
    }

    try {
      setCleaningAudit(true);
      const result = await apiService.post<{ deletedCount: number; message: string }>('/analytics/audit/cleanup', {});
      toast.success(result.message);
      // Refresh retention info
      const retentionRes = await apiService.get<AuditRetentionInfo>('/analytics/audit/retention').catch(() => null);
      setRetentionInfo(retentionRes);
    } catch {
      toast.error('Failed to cleanup audit logs');
    } finally {
      setCleaningAudit(false);
    }
  };

  const loadUsers = async (query = '') => {
    setUsersLoading(true);
    try {
      const res = query ? await userGroupService.searchUsers(query) : await userGroupService.listUsers();
      setUsers(res);
    } catch {
      toast.error('Failed to load users');
    } finally {
      setUsersLoading(false);
    }
  };

  const loadGroups = async () => {
    setGroupsLoading(true);
    try {
      const res = await userGroupService.listGroups();
      setGroups(res);
    } catch {
      toast.error('Failed to load groups');
    } finally {
      setGroupsLoading(false);
    }
  };

  useEffect(() => {
    fetchDashboard();
    loadPinnedSearches();
    fetchMailFetchSummary({ silent: true });
    loadBatchDownloadAdminTasks(
      true,
      batchDownloadAdminStatusFilter,
      batchDownloadAdminPage,
      batchDownloadAdminRowsPerPage,
      batchDownloadAdminQuery,
      batchDownloadAdminOwnerFilter
    );
    loadAsyncExportHealthOverview(true);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  useEffect(() => {
    if (!batchDownloadAdminAutoRefresh) {
      return;
    }

    const timer = window.setInterval(() => {
      void loadBatchDownloadAdminTasks(
        true,
        batchDownloadAdminStatusFilter,
        batchDownloadAdminPage,
        batchDownloadAdminRowsPerPage,
        batchDownloadAdminQuery,
        batchDownloadAdminOwnerFilter
      );
    }, BATCH_DOWNLOAD_ADMIN_AUTO_REFRESH_MS);

    return () => {
      window.clearInterval(timer);
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [
    batchDownloadAdminAutoRefresh,
    batchDownloadAdminStatusFilter,
    batchDownloadAdminPage,
    batchDownloadAdminRowsPerPage,
    batchDownloadAdminQuery,
    batchDownloadAdminOwnerFilter,
  ]);

  useEffect(() => {
    const params = new URLSearchParams(location.search);
    const nextDomain = normalizeRecentAsyncTaskDomainFilter(params.get(ASYNC_TASK_QUERY_KEYS.domain)) || 'ALL';
    const nextStatus = normalizeRecentAsyncTaskStatusFilter(params.get(ASYNC_TASK_QUERY_KEYS.status)) || 'ALL';
    const nextIncludeAcknowledged =
      params.get(ASYNC_TASK_QUERY_KEYS.includeAcknowledged)?.trim().toLowerCase() === 'true';

    setRecentAsyncTasksDomainFilter((current) => (current === nextDomain ? current : nextDomain));
    setRecentAsyncTasksStatusFilter((current) => (current === nextStatus ? current : nextStatus));
    setRecentAsyncTasksIncludeAcknowledged((current) => (
      current === nextIncludeAcknowledged ? current : nextIncludeAcknowledged
    ));
  }, [location.search]);

  useEffect(() => {
    const params = new URLSearchParams(location.search);
    if (recentAsyncTasksDomainFilter === 'ALL') {
      params.delete(ASYNC_TASK_QUERY_KEYS.domain);
    } else {
      params.set(ASYNC_TASK_QUERY_KEYS.domain, recentAsyncTasksDomainFilter);
    }

    if (recentAsyncTasksStatusFilter === 'ALL') {
      params.delete(ASYNC_TASK_QUERY_KEYS.status);
    } else {
      params.set(ASYNC_TASK_QUERY_KEYS.status, recentAsyncTasksStatusFilter);
    }

    if (recentAsyncTasksIncludeAcknowledged) {
      params.set(ASYNC_TASK_QUERY_KEYS.includeAcknowledged, 'true');
    } else {
      params.delete(ASYNC_TASK_QUERY_KEYS.includeAcknowledged);
    }

    const nextSearch = params.toString();
    const currentSearch = location.search.startsWith('?') ? location.search.slice(1) : location.search;
    if (nextSearch !== currentSearch) {
      navigate(
        {
          pathname: location.pathname,
          search: nextSearch ? `?${nextSearch}` : '',
          hash: location.hash,
        },
        { replace: true }
      );
    }
  }, [
    recentAsyncTasksDomainFilter,
    recentAsyncTasksStatusFilter,
    recentAsyncTasksIncludeAcknowledged,
    location.pathname,
    location.search,
    location.hash,
    navigate,
  ]);

  useEffect(() => {
    void loadRecentAsyncTasks(true);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [recentAsyncTasksMaxItems, recentAsyncTasksDomainFilter, recentAsyncTasksStatusFilter, recentAsyncTasksIncludeAcknowledged]);

  useEffect(() => {
    if (suppressAuditUrlSyncRef.current) {
      suppressAuditUrlSyncRef.current = false;
      return;
    }

    const auditParams = new URLSearchParams(location.search);
    const auditNodeId = auditParams.get(AUDIT_QUERY_KEYS.nodeId)?.trim() ?? '';
    const auditUser = auditParams.get(AUDIT_QUERY_KEYS.user)?.trim() ?? '';
    const auditEventType = normalizeAuditEventType(
      auditParams.get(AUDIT_QUERY_KEYS.eventType)?.trim() ?? ''
    );
    const auditCategory = auditParams.get(AUDIT_QUERY_KEYS.category)?.trim() ?? '';
    const auditFromRaw = auditParams.get(AUDIT_QUERY_KEYS.from)?.trim() ?? '';
    const auditToRaw = auditParams.get(AUDIT_QUERY_KEYS.to)?.trim() ?? '';

    const hasAnyAuditFilters = Boolean(
      auditNodeId || auditUser || auditEventType || auditCategory || auditFromRaw || auditToRaw
    );
    if (!hasAnyAuditFilters) {
      return;
    }

    if (auditNodeId && !UUID_PATTERN.test(auditNodeId)) {
      toast.error('Invalid auditNodeId in URL');
      return;
    }

    const fromDate = auditFromRaw ? new Date(auditFromRaw) : null;
    const toDate = auditToRaw ? new Date(auditToRaw) : null;
    if (fromDate && Number.isNaN(fromDate.getTime())) {
      toast.error('Invalid auditFrom in URL');
      return;
    }
    if (toDate && Number.isNaN(toDate.getTime())) {
      toast.error('Invalid auditTo in URL');
      return;
    }

    setTab(0);
    setAuditFilterUser(auditUser);
    setAuditFilterEventType(auditEventType);
    setAuditFilterCategory(auditCategory);
    setAuditFilterNodeId(auditNodeId);

    if (auditFromRaw) {
      setAuditExportFrom(auditFromRaw);
    }
    if (auditToRaw) {
      setAuditExportTo(auditToRaw);
    }
    if (auditFromRaw || auditToRaw) {
      setAuditExportPreset('custom');
      setAuditQuickRange('custom');
    }

    void (async () => {
      try {
        setFilteringAudit(true);
        const params = new URLSearchParams();
        if (auditUser) {
          params.append('username', auditUser);
        }
        if (auditEventType) {
          params.append('eventType', auditEventType);
        }
        if (auditCategory) {
          params.append('category', auditCategory);
        }
        if (auditNodeId) {
          params.append('nodeId', auditNodeId);
        }
        if (fromDate) {
          params.append('from', formatDateTimeOffset(fromDate));
        }
        if (toDate) {
          params.append('to', formatDateTimeOffset(toDate));
        }

        const query = params.toString();
        const response = await apiService.get<PageResponse<AuditLog>>(
          `/analytics/audit/search${query ? `?${query}` : ''}`
        );
        setLogs(response.content || []);
      } catch {
        toast.error('Failed to filter audit logs');
      } finally {
        setFilteringAudit(false);
      }
    })();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [location.search]);

  useEffect(() => {
    if (tab === 1) {
      loadUsers();
    } else if (tab === 2) {
      loadGroups();
    }
  }, [tab]);

  useEffect(() => {
    if (membersGroup) {
      userGroupService
        .listUsers()
        .then((res) => setAvailableUsernames(res.map((u) => u.username)))
        .catch(() => undefined);
    }
  }, [membersGroup]);

  const getFileIcon = (mimeType: string) => {
    if (mimeType.includes('pdf')) return <PictureAsPdf color="error" />;
    if (mimeType.includes('image')) return <Image color="primary" />;
    if (mimeType.includes('word') || mimeType.includes('document')) return <Article color="info" />;
    return <InsertDriveFile color="action" />;
  };

  const formatFileSize = (bytes: number) => {
    if (bytes < 1024) return bytes + ' B';
    if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(1) + ' KB';
    if (bytes < 1024 * 1024 * 1024) return (bytes / (1024 * 1024)).toFixed(1) + ' MB';
    return (bytes / (1024 * 1024 * 1024)).toFixed(1) + ' GB';
  };

  const formatDuration = (durationMs?: number | null) => {
    if (durationMs === null || durationMs === undefined) return '—';
    if (durationMs < 1000) return `${durationMs} ms`;
    if (durationMs < 60000) return `${(durationMs / 1000).toFixed(1)} s`;
    return `${(durationMs / 60000).toFixed(1)} min`;
  };

  const OverviewPanel = () => {
    if (loadingDashboard && !data) {
      return (
        <Box p={4}>
          <LinearProgress />
        </Box>
      );
    }

    const filteredRuleEvents = ruleEvents.filter(
      (event) => ruleEventFilter.length === 0 || ruleEventFilter.includes(event.eventType)
    );
    const asyncExportHealthAggregate = asyncExportHealthDomains.reduce<AsyncExportHealthSummary>((acc, domain) => ({
      total: acc.total + domain.summary.total,
      active: acc.active + domain.summary.active,
      terminal: acc.terminal + domain.summary.terminal,
      queued: acc.queued + domain.summary.queued,
      running: acc.running + domain.summary.running,
      completed: acc.completed + domain.summary.completed,
      failed: acc.failed + domain.summary.failed,
      cancelled: acc.cancelled + domain.summary.cancelled,
      timedOut: acc.timedOut + domain.summary.timedOut,
      expired: acc.expired + domain.summary.expired,
      failureRate: 0,
    }), emptyAsyncExportHealthSummary());
    const aggregateFailureRate = asyncExportHealthAggregate.total > 0
      ? (asyncExportHealthAggregate.failed + asyncExportHealthAggregate.timedOut + asyncExportHealthAggregate.expired)
        / asyncExportHealthAggregate.total
      : 0;
    const recentAsyncTasksListedFrom = recentAsyncTasks.length > 0 ? 1 : 0;
    const recentAsyncTasksListedTo = recentAsyncTasks.length;
    const recentAsyncTaskDomainLabel = recentAsyncTasksDomainFilter === 'ALL'
      ? 'All domains'
      : RECENT_ASYNC_TASK_DOMAIN_LABELS[recentAsyncTasksDomainFilter];
    const batchDownloadAdminListedFrom = batchDownloadAdminPaging.totalItems === 0
      ? 0
      : batchDownloadAdminPaging.skipCount + 1;
    const batchDownloadAdminListedTo = Math.min(
      batchDownloadAdminPaging.skipCount + batchDownloadAdminTasks.length,
      batchDownloadAdminPaging.totalItems
    );

    return (
      <>
        <Box display="flex" justifyContent="space-between" alignItems="center" mb={3}>
          <Typography variant="h4" component="h1">
            System Dashboard
          </Typography>
          <IconButton
            onClick={() => {
              fetchDashboard();
              loadPinnedSearches();
              fetchMailFetchSummary();
              loadRecentAsyncTasks(true);
              loadBatchDownloadAdminTasks(
                true,
                batchDownloadAdminStatusFilter,
                batchDownloadAdminPage,
                batchDownloadAdminRowsPerPage,
                batchDownloadAdminQuery,
                batchDownloadAdminOwnerFilter
              );
              loadAsyncExportHealthOverview();
            }}
            color="primary"
          >
            <Refresh />
          </IconButton>
        </Box>

        <Grid container spacing={3} mb={4}>
          <Grid item xs={12} sm={6} md={3}>
            <SummaryCard
              title="Total Documents"
              value={data?.summary.totalDocuments || 0}
              icon={<Description fontSize="large" color="primary" />}
            />
          </Grid>
          <Grid item xs={12} sm={6} md={3}>
            <SummaryCard
              title="Storage Used"
              value={formatFileSize(data?.summary.totalSizeBytes || 0)}
              icon={<Storage fontSize="large" color="secondary" />}
            />
          </Grid>
          <Grid item xs={12} sm={6} md={3}>
            <SummaryCard
              title="Active Users"
              value={data?.topUsers.length || 0}
              icon={<People fontSize="large" color="success" />}
            />
          </Grid>
          <Grid item xs={12} sm={6} md={3}>
            <SummaryCard
              title="Today's Activity"
              value={data?.activity[data.activity.length - 1]?.eventCount || 0}
              icon={<TrendingUp fontSize="large" color="warning" />}
            />
          </Grid>
        </Grid>

        <Box mb={3}>
          <ScheduledJobsCard />
        </Box>

        <Box mb={3}>
          <QueueBacklogCard />
        </Box>

        <Paper sx={{ p: 2, mb: 3 }}>
          <Box display="flex" justifyContent="space-between" alignItems="center" mb={1}>
            <Typography component="h2" variant="h6" color="primary">
              Async Task Health Overview
            </Typography>
            <Box display="flex" alignItems="center" gap={1}>
              {asyncExportHealthLoading && <CircularProgress size={14} />}
              <Button
                size="small"
                variant="outlined"
                startIcon={<Refresh />}
                onClick={() => void loadAsyncExportHealthOverview()}
                disabled={asyncExportHealthLoading}
                aria-label="Refresh async task health overview"
              >
                {asyncExportHealthLoading ? 'Refreshing...' : 'Refresh'}
              </Button>
            </Box>
          </Box>
          <Box display="flex" gap={1} flexWrap="wrap" mb={1}>
            <Chip
              size="small"
              label={asyncExportHealthOverallStatus === 'degraded' ? 'Status DEGRADED' : 'Status HEALTHY'}
              color={asyncExportHealthOverallStatus === 'degraded' ? 'warning' : 'success'}
              variant="outlined"
            />
            <Chip
              size="small"
              label={`Risk ${asyncExportHealthOverallRiskLevel}`}
              color={toAsyncExportRiskChipColor(asyncExportHealthOverallRiskLevel)}
              variant="outlined"
            />
            <Chip size="small" label={`Total ${asyncExportHealthAggregate.total}`} variant="outlined" />
            <Chip size="small" label={`Active ${asyncExportHealthAggregate.active}`} color="info" variant="outlined" />
            <Chip size="small" label={`Queued ${asyncExportHealthAggregate.queued}`} variant="outlined" />
            <Chip size="small" label={`Running ${asyncExportHealthAggregate.running}`} variant="outlined" />
            <Chip size="small" label={`Terminal ${asyncExportHealthAggregate.terminal}`} variant="outlined" />
            <Chip size="small" label={`Completed ${asyncExportHealthAggregate.completed}`} color="success" variant="outlined" />
            <Chip size="small" label={`Failed ${asyncExportHealthAggregate.failed}`} color="warning" variant="outlined" />
            <Chip size="small" label={`Cancelled ${asyncExportHealthAggregate.cancelled}`} variant="outlined" />
            <Chip size="small" label={`Timed out ${asyncExportHealthAggregate.timedOut}`} color="warning" variant="outlined" />
            <Chip size="small" label={`Expired ${asyncExportHealthAggregate.expired}`} color="warning" variant="outlined" />
            <Chip
              size="small"
              label={`Failure ${(aggregateFailureRate * 100).toFixed(1)}%`}
              variant="outlined"
            />
          </Box>
          <TableContainer>
            <Table size="small" aria-label="Async task health overview">
              <TableHead>
                <TableRow>
                  <TableCell>Domain</TableCell>
                  <TableCell>Status</TableCell>
                  <TableCell>Risk</TableCell>
                  <TableCell align="right">Total</TableCell>
                  <TableCell align="right">Active</TableCell>
                  <TableCell align="right">Terminal</TableCell>
                  <TableCell align="right">Completed</TableCell>
                  <TableCell align="right">Failed</TableCell>
                  <TableCell align="right">Cancelled</TableCell>
                  <TableCell align="right">Timed Out</TableCell>
                  <TableCell align="right">Expired</TableCell>
                </TableRow>
              </TableHead>
              <TableBody>
                {asyncExportHealthDomains.map((domain) => (
                  <TableRow key={`async-export-health-${domain.key}`}>
                    <TableCell>{domain.label}</TableCell>
                    <TableCell>
                      <Box display="flex" alignItems="center" gap={1} flexWrap="wrap">
                        <Chip
                          size="small"
                          label={domain.status === 'degraded' ? 'degraded' : 'healthy'}
                          color={domain.status === 'degraded' ? 'warning' : 'success'}
                          variant="outlined"
                        />
                        {domain.status === 'degraded' && (
                          <Typography variant="caption" color="warning.main">
                            {domain.error || 'failed-to-load'}
                          </Typography>
                        )}
                      </Box>
                    </TableCell>
                    <TableCell>
                      <Chip
                        size="small"
                        label={domain.riskLevel}
                        color={toAsyncExportRiskChipColor(domain.riskLevel)}
                        variant="outlined"
                      />
                    </TableCell>
                    <TableCell align="right">{domain.summary.total}</TableCell>
                    <TableCell align="right">{domain.summary.active}</TableCell>
                    <TableCell align="right">{domain.summary.terminal}</TableCell>
                    <TableCell align="right">{domain.summary.completed}</TableCell>
                    <TableCell align="right">{domain.summary.failed}</TableCell>
                    <TableCell align="right">{domain.summary.cancelled}</TableCell>
                    <TableCell align="right">{domain.summary.timedOut}</TableCell>
                    <TableCell align="right">{domain.summary.expired}</TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </TableContainer>
          {asyncExportHealthUpdatedAt && (
            <Typography variant="caption" color="text.secondary" sx={{ mt: 1, display: 'block' }}>
              Last updated {format(new Date(asyncExportHealthUpdatedAt), 'PPp')}
            </Typography>
          )}
        </Paper>

        <Paper sx={{ p: 2, mb: 3 }}>
          <Box
            display="flex"
            justifyContent="space-between"
            alignItems={{ xs: 'flex-start', md: 'center' }}
            gap={1}
            flexDirection={{ xs: 'column', md: 'row' }}
            mb={1}
          >
            <Box display="flex" alignItems="center" gap={1}>
              <History fontSize="small" color="primary" />
              <Typography component="h2" variant="h6" color="primary">
                Recent Async Tasks
              </Typography>
            </Box>
            <Box display="flex" alignItems="center" gap={1} flexWrap="wrap">
              <FormControl size="small" sx={{ minWidth: 140 }}>
                <InputLabel id="recent-async-task-max-items-label">Max items</InputLabel>
                <Select
                  labelId="recent-async-task-max-items-label"
                  value={recentAsyncTasksMaxItems}
                  label="Max items"
                  onChange={(event) => setRecentAsyncTasksMaxItems(Number(event.target.value))}
                  inputProps={{ 'aria-label': 'Recent async task max items' }}
                >
                  {RECENT_ASYNC_TASK_MAX_ITEM_OPTIONS.map((option) => (
                    <MenuItem key={`recent-async-task-max-${option}`} value={option}>
                      {option}
                    </MenuItem>
                  ))}
                </Select>
              </FormControl>
              <FormControl size="small" sx={{ minWidth: 180 }}>
                <InputLabel id="recent-async-task-domain-label">Domain</InputLabel>
                <Select
                  labelId="recent-async-task-domain-label"
                  value={recentAsyncTasksDomainFilter}
                  label="Domain"
                  onChange={(event) => setRecentAsyncTasksDomainFilter(event.target.value as RecentAsyncTaskDomainFilter)}
                  inputProps={{ 'aria-label': 'Recent async task domain filter' }}
                >
                  {RECENT_ASYNC_TASK_DOMAIN_OPTIONS.map((option) => (
                    <MenuItem key={`recent-async-task-domain-${option.value}`} value={option.value}>
                      {option.label}
                    </MenuItem>
                  ))}
                </Select>
              </FormControl>
              <FormControl size="small" sx={{ minWidth: 160 }}>
                <InputLabel id="recent-async-task-status-label">Status</InputLabel>
                <Select
                  labelId="recent-async-task-status-label"
                  value={recentAsyncTasksStatusFilter}
                  label="Status"
                  onChange={(event) => setRecentAsyncTasksStatusFilter(event.target.value as RecentAsyncTaskStatusFilter)}
                  inputProps={{ 'aria-label': 'Recent async task status filter' }}
                >
                  {RECENT_ASYNC_TASK_STATUS_OPTIONS.map((option) => (
                    <MenuItem key={`recent-async-task-status-${option.value}`} value={option.value}>
                      {option.label}
                    </MenuItem>
                  ))}
                </Select>
              </FormControl>
              <FormControlLabel
                control={(
                  <Switch
                    size="small"
                    checked={recentAsyncTasksIncludeAcknowledged}
                    onChange={(event) => setRecentAsyncTasksIncludeAcknowledged(event.target.checked)}
                    inputProps={{ 'aria-label': 'Show acknowledged async tasks' }}
                  />
                )}
                label="Show acknowledged"
              />
              <Button
                size="small"
                variant="outlined"
                startIcon={<Refresh />}
                onClick={() => void loadRecentAsyncTasks(false)}
                disabled={recentAsyncTasksLoading}
                aria-label="Refresh recent async tasks"
              >
                {recentAsyncTasksLoading ? 'Refreshing...' : 'Refresh'}
              </Button>
            </Box>
          </Box>
          <Box display="flex" gap={1} flexWrap="wrap" mb={1}>
            {recentAsyncTasksLoading && <CircularProgress size={14} />}
            <Chip size="small" variant="outlined" label={`Total ${recentAsyncTasksTotalCount}`} />
            <Chip
              size="small"
              variant="outlined"
              label={`Listed ${recentAsyncTasksListedFrom}-${recentAsyncTasksListedTo}`}
            />
            <Chip size="small" variant="outlined" label={`Domain ${recentAsyncTaskDomainLabel}`} />
            <Chip size="small" variant="outlined" label={`Status ${recentAsyncTasksStatusFilter === 'ALL' ? 'All' : recentAsyncTasksStatusFilter}`} />
            <Chip
              size="small"
              variant="outlined"
              label={recentAsyncTasksIncludeAcknowledged ? 'Including acknowledged' : 'Operator-visible only'}
            />
          </Box>
          {recentAsyncTasksUpdatedAt && (
            <Typography variant="caption" color="text.secondary" sx={{ display: 'block', mb: 1 }}>
              Last updated {format(new Date(recentAsyncTasksUpdatedAt), 'PPp')}
            </Typography>
          )}
          {recentAsyncTasksError ? (
            <Alert severity="warning" sx={{ mb: 1 }}>
              {recentAsyncTasksError}
            </Alert>
          ) : recentAsyncTasks.length === 0 ? (
            <Alert severity="info">No recent async tasks found.</Alert>
          ) : (
            <TableContainer>
              <Table size="small" aria-label="Recent async task list">
                <TableHead>
                  <TableRow>
                    <TableCell>Domain</TableCell>
                    <TableCell>Status</TableCell>
                    <TableCell>Task ID</TableCell>
                    <TableCell>Created / Updated</TableCell>
                    <TableCell>Filename</TableCell>
                    <TableCell align="right">Actions</TableCell>
                  </TableRow>
                </TableHead>
                <TableBody>
                  {recentAsyncTasks.map((task) => {
                    const taskKey = `${task.domainKey}:${task.taskId}`;
                    const actionBusy = recentAsyncTasksActionTaskKey === taskKey;
                    const statusLabel = normalizeRecentAsyncTaskStatus(task.status);
                    return (
                      <TableRow key={`recent-async-task-${taskKey}`} sx={task.acknowledged ? { opacity: 0.72 } : undefined}>
                        <TableCell>
                          <Stack spacing={0.25}>
                            <Typography variant="body2">{task.domainLabel || RECENT_ASYNC_TASK_DOMAIN_LABELS[task.domainKey as Exclude<RecentAsyncTaskDomainFilter, 'ALL'>] || task.domainKey}</Typography>
                            <Typography variant="caption" color="text.secondary" noWrap>
                              {task.domainKey}
                            </Typography>
                          </Stack>
                        </TableCell>
                        <TableCell>
                          <Stack spacing={0.75} alignItems="flex-start">
                            <Stack direction="row" spacing={0.75} flexWrap="wrap">
                              <Chip
                                size="small"
                                variant="outlined"
                                label={statusLabel}
                                color={toRecentAsyncTaskStatusChipColor(statusLabel)}
                              />
                              {task.acknowledged && (
                                <Chip
                                  size="small"
                                  variant="outlined"
                                  color="success"
                                  label="Acknowledged"
                                />
                              )}
                            </Stack>
                            {task.acknowledgedAt && (
                              <Typography variant="caption" color="text.secondary">
                                Acked {formatRecentAsyncTaskTimestamp(task.acknowledgedAt)}
                              </Typography>
                            )}
                            {task.error && (
                              <Typography variant="caption" color="warning.main">
                                {task.error}
                              </Typography>
                            )}
                          </Stack>
                        </TableCell>
                        <TableCell sx={{ maxWidth: 260 }}>
                          <Typography variant="body2" noWrap>
                            {task.taskId}
                          </Typography>
                        </TableCell>
                        <TableCell>
                          <Stack spacing={0.25}>
                            <Typography variant="body2">{formatRecentAsyncTaskTimestamp(task.createdAt)}</Typography>
                            <Typography variant="caption" color="text.secondary">
                              {task.updatedAt ? `Updated ${formatRecentAsyncTaskTimestamp(task.updatedAt)}` : 'No updates yet'}
                            </Typography>
                          </Stack>
                        </TableCell>
                        <TableCell sx={{ maxWidth: 240 }}>
                          <Stack spacing={0.25}>
                            <Typography variant="body2" noWrap>{task.filename || '—'}</Typography>
                            <Typography variant="caption" color="text.secondary">
                              {task.createdBy || task.updatedBy || '—'}
                            </Typography>
                          </Stack>
                        </TableCell>
                        <TableCell align="right">
                          <Box display="flex" justifyContent="flex-end" gap={0.5} flexWrap="wrap">
                            {task.cancellable && task.cancelUrl && (
                              <Button
                                size="small"
                                variant="outlined"
                                color="warning"
                                disabled={Boolean(recentAsyncTasksActionTaskKey)}
                                onClick={() => void handleRecentAsyncTaskAction(task, 'cancel')}
                              >
                                {actionBusy && recentAsyncTasksActionType === 'cancel' ? 'Cancelling...' : 'Cancel'}
                              </Button>
                            )}
                            {task.cleanupEligible && task.cleanupUrl && (
                              <Button
                                size="small"
                                variant="outlined"
                                color="secondary"
                                startIcon={<CleanupIcon />}
                                disabled={Boolean(recentAsyncTasksActionTaskKey)}
                                onClick={() => void handleRecentAsyncTaskAction(task, 'cleanup')}
                              >
                                {actionBusy && recentAsyncTasksActionType === 'cleanup' ? 'Cleaning...' : 'Cleanup'}
                              </Button>
                            )}
                            {task.downloadReady && task.downloadUrl && (
                              <Button
                                size="small"
                                variant="outlined"
                                startIcon={<DownloadIcon />}
                                disabled={Boolean(recentAsyncTasksActionTaskKey)}
                                onClick={() => void handleRecentAsyncTaskAction(task, 'download')}
                              >
                                {actionBusy && recentAsyncTasksActionType === 'download' ? 'Downloading...' : 'Download'}
                              </Button>
                            )}
                            {task.fingerprint && task.acknowledged ? (
                              <Button
                                size="small"
                                variant="outlined"
                                color="success"
                                disabled={Boolean(recentAsyncTasksActionTaskKey)}
                                onClick={() => void handleRecentAsyncTaskAction(task, 'unacknowledge')}
                              >
                                {actionBusy && recentAsyncTasksActionType === 'unacknowledge' ? 'Restoring...' : 'Restore'}
                              </Button>
                            ) : task.fingerprint && !task.cancellable ? (
                              <Button
                                size="small"
                                variant="outlined"
                                color="success"
                                disabled={Boolean(recentAsyncTasksActionTaskKey)}
                                onClick={() => void handleRecentAsyncTaskAction(task, 'acknowledge')}
                              >
                                {actionBusy && recentAsyncTasksActionType === 'acknowledge' ? 'Acknowledging...' : 'Acknowledge'}
                              </Button>
                            ) : null}
                          </Box>
                        </TableCell>
                      </TableRow>
                    );
                  })}
                </TableBody>
              </Table>
            </TableContainer>
          )}
        </Paper>

        <Paper sx={{ p: 2, mb: 3 }}>
          <Box
            display="flex"
            justifyContent="space-between"
            alignItems={{ xs: 'flex-start', md: 'center' }}
            gap={1}
            flexDirection={{ xs: 'column', md: 'row' }}
            mb={1}
          >
            <Box display="flex" alignItems="center" gap={1}>
              <DownloadIcon fontSize="small" color="primary" />
              <Typography component="h2" variant="h6" color="primary">
                Batch Download Task Center
              </Typography>
            </Box>
            <Box display="flex" alignItems="center" gap={1} flexWrap="wrap">
              <TextField
                size="small"
                label="Search tasks"
                placeholder="Task ID or filename"
                value={batchDownloadAdminQueryInput}
                onChange={(event) => setBatchDownloadAdminQueryInput(event.target.value)}
                onKeyDown={(event) => {
                  if (event.key === 'Enter') {
                    event.preventDefault();
                    handleApplyBatchDownloadAdminQuery();
                  }
                }}
                sx={{ minWidth: 220 }}
              />
              <TextField
                size="small"
                label="Owner"
                placeholder="Created by"
                value={batchDownloadAdminOwnerInput}
                onChange={(event) => setBatchDownloadAdminOwnerInput(event.target.value)}
                onKeyDown={(event) => {
                  if (event.key === 'Enter') {
                    event.preventDefault();
                    handleApplyBatchDownloadAdminOwner();
                  }
                }}
                sx={{ minWidth: 180 }}
              />
              <Button
                size="small"
                variant="outlined"
                onClick={handleApplyBatchDownloadAdminOwner}
                disabled={batchDownloadAdminTasksLoading}
              >
                Filter
              </Button>
              <Button
                size="small"
                variant="text"
                onClick={handleClearBatchDownloadAdminOwner}
                disabled={batchDownloadAdminTasksLoading && !batchDownloadAdminOwnerFilter}
              >
                Clear owner
              </Button>
              <Button
                size="small"
                variant="outlined"
                onClick={handleApplyBatchDownloadAdminQuery}
                disabled={batchDownloadAdminTasksLoading}
              >
                Search
              </Button>
              <Button
                size="small"
                variant="text"
                onClick={handleClearBatchDownloadAdminQuery}
                disabled={batchDownloadAdminTasksLoading && !batchDownloadAdminQuery}
              >
                Clear
              </Button>
              <FormControl size="small" sx={{ minWidth: 180 }}>
                <InputLabel id="batch-download-status-filter-label">Task status</InputLabel>
                <Select
                  labelId="batch-download-status-filter-label"
                  value={batchDownloadAdminStatusFilter}
                  label="Task status"
                  onChange={(event) => {
                    const nextFilter = event.target.value as BatchDownloadTaskStatusFilter;
                    setBatchDownloadAdminStatusFilter(nextFilter);
                    setBatchDownloadAdminPage(0);
                    void loadBatchDownloadAdminTasks(
                      true,
                      nextFilter,
                      0,
                      batchDownloadAdminRowsPerPage,
                      batchDownloadAdminQuery,
                      batchDownloadAdminOwnerFilter
                    );
                  }}
                  inputProps={{ 'aria-label': 'Batch download task status filter' }}
                >
                  {BATCH_DOWNLOAD_TASK_FILTER_OPTIONS.map((option) => (
                    <MenuItem key={`batch-download-status-${option.value}`} value={option.value}>
                      {option.label}
                    </MenuItem>
                  ))}
                </Select>
              </FormControl>
              <Button
                size="small"
                variant="outlined"
                startIcon={<Refresh />}
                onClick={() => void loadBatchDownloadAdminTasks(
                  false,
                  batchDownloadAdminStatusFilter,
                  batchDownloadAdminPage,
                  batchDownloadAdminRowsPerPage,
                  batchDownloadAdminQuery,
                  batchDownloadAdminOwnerFilter
                )}
                disabled={batchDownloadAdminTasksLoading}
              >
                {batchDownloadAdminTasksLoading ? 'Refreshing...' : 'Refresh'}
              </Button>
              <FormControlLabel
                label="Auto refresh"
                control={(
                  <Switch
                    size="small"
                    checked={batchDownloadAdminAutoRefresh}
                    onChange={(event) => setBatchDownloadAdminAutoRefresh(event.target.checked)}
                  />
                )}
              />
              <Button
                size="small"
                variant="outlined"
                color="warning"
                startIcon={<DeleteIcon />}
                onClick={() => void handleCancelActiveBatchDownloadAdminTasks()}
                disabled={batchDownloadAdminCancellingActive}
              >
                {batchDownloadAdminCancellingActive ? 'Cancelling active...' : 'Cancel active'}
              </Button>
              <Button
                size="small"
                variant="outlined"
                color="warning"
                startIcon={<CleanupIcon />}
                onClick={() => void handleCleanupBatchDownloadAdminTasks()}
                disabled={batchDownloadAdminCleaning}
              >
                {batchDownloadAdminCleaning ? 'Cleaning...' : 'Cleanup'}
              </Button>
            </Box>
          </Box>

          <Box display="flex" gap={1} flexWrap="wrap" mb={1}>
            {batchDownloadAdminTasksLoading && <CircularProgress size={14} />}
            <Chip size="small" variant="outlined" label={`Total ${batchDownloadAdminSummary.totalCount}`} />
            <Chip size="small" color="info" variant="outlined" label={`Active ${batchDownloadAdminSummary.activeCount}`} />
            <Chip size="small" variant="outlined" label={`Queued ${batchDownloadAdminSummary.queuedCount}`} />
            <Chip size="small" variant="outlined" label={`Running ${batchDownloadAdminSummary.runningCount}`} />
            <Chip size="small" variant="outlined" label={`Cancel requested ${batchDownloadAdminSummary.cancelRequestedCount}`} />
            <Chip
              size="small"
              variant="outlined"
              label={`Completed ${batchDownloadAdminSummary.completedCount}`}
            />
            <Chip
              size="small"
              variant="outlined"
              label={`Failed ${batchDownloadAdminSummary.failedCount}`}
            />
            <Chip
              size="small"
              variant="outlined"
              label={`Cancelled ${batchDownloadAdminSummary.cancelledCount}`}
            />
            {batchDownloadAdminQuery && (
              <Chip
                size="small"
                variant="outlined"
                color="primary"
                label={`Query ${batchDownloadAdminQuery}`}
              />
            )}
            {batchDownloadAdminOwnerFilter && (
              <Chip
                size="small"
                variant="outlined"
                color="secondary"
                label={`Owner ${batchDownloadAdminOwnerFilter}`}
              />
            )}
          </Box>
          {batchDownloadAdminUpdatedAt && (
            <Typography variant="caption" color="text.secondary" sx={{ display: 'block', mb: 1 }}>
              Last updated {format(new Date(batchDownloadAdminUpdatedAt), 'PPp')}
              {batchDownloadAdminAutoRefresh ? ` · auto refresh every ${BATCH_DOWNLOAD_ADMIN_AUTO_REFRESH_MS / 1000}s` : ''}
            </Typography>
          )}

          <Box
            display="flex"
            justifyContent="space-between"
            alignItems={{ xs: 'flex-start', md: 'center' }}
            gap={1}
            flexDirection={{ xs: 'column', md: 'row' }}
            mb={1}
          >
            <Box display="flex" alignItems="center" gap={1} flexWrap="wrap">
              <FormControl size="small" sx={{ minWidth: 120 }}>
                <InputLabel id="batch-download-rows-per-page-label">Rows / page</InputLabel>
                <Select
                  labelId="batch-download-rows-per-page-label"
                  value={batchDownloadAdminRowsPerPage}
                  label="Rows / page"
                  onChange={(event) => handleBatchDownloadAdminRowsPerPageChange(Number(event.target.value))}
                  inputProps={{ 'aria-label': 'Batch download rows per page' }}
                >
                  {BATCH_DOWNLOAD_ADMIN_ROW_OPTIONS.map((option) => (
                    <MenuItem key={`batch-download-rows-${option}`} value={option}>
                      {option}
                    </MenuItem>
                  ))}
                </Select>
              </FormControl>
              <Typography variant="body2" color="text.secondary">
                Listed {batchDownloadAdminListedFrom}-{batchDownloadAdminListedTo} of {batchDownloadAdminPaging.totalItems}
              </Typography>
            </Box>
            <Box display="flex" alignItems="center" gap={1}>
              <Button
                size="small"
                variant="outlined"
                onClick={handleBatchDownloadAdminPrevPage}
                disabled={batchDownloadAdminPage <= 0 || batchDownloadAdminTasksLoading}
              >
                Prev
              </Button>
              <Typography variant="body2" color="text.secondary">
                Page {batchDownloadAdminPage + 1}
              </Typography>
              <Button
                size="small"
                variant="outlined"
                onClick={handleBatchDownloadAdminNextPage}
                disabled={!batchDownloadAdminPaging.hasMoreItems || batchDownloadAdminTasksLoading}
              >
                Next
              </Button>
            </Box>
          </Box>

          {batchDownloadAdminError && (
            <Alert severity="warning" sx={{ mb: 1 }}>
              {batchDownloadAdminError}
            </Alert>
          )}

          {batchDownloadAdminTasks.length === 0 ? (
            <Alert severity="info">No batch download tasks yet.</Alert>
          ) : (
            <TableContainer>
              <Table size="small" aria-label="Batch download admin task list">
                <TableHead>
                  <TableRow>
                    <TableCell>Task ID</TableCell>
                    <TableCell>Status</TableCell>
                    <TableCell>Owner</TableCell>
                    <TableCell>Created</TableCell>
                    <TableCell>Finished</TableCell>
                    <TableCell>Progress</TableCell>
                    <TableCell>Filename</TableCell>
                    <TableCell align="right">Actions</TableCell>
                  </TableRow>
                </TableHead>
                <TableBody>
                  {batchDownloadAdminTasks.map((task) => {
                    const normalizedStatus = normalizeBatchDownloadTaskStatus(task.status);
                    const canCancel = BATCH_DOWNLOAD_ACTIVE_STATUSES.has(normalizedStatus);
                    const canCleanup = Boolean(task.cleanupEligible);
                    const canDownload = BATCH_DOWNLOAD_DOWNLOADABLE_STATUSES.has(normalizedStatus);
                    const actionBusy = batchDownloadAdminActionTaskId === task.taskId;
                    return (
                      <TableRow key={`batch-download-admin-task-${task.taskId}`}>
                        <TableCell sx={{ maxWidth: 320 }}>
                          <Typography variant="body2" noWrap>{task.taskId}</Typography>
                        </TableCell>
                        <TableCell>
                          <Stack spacing={0.75} alignItems="flex-start">
                            <Chip size="small" variant="outlined" label={normalizedStatus} />
                            {task.cleanupEligible && (
                              <Chip
                                size="small"
                                color="secondary"
                                variant="outlined"
                                label={task.artifactPresent ? 'Cleanup eligible' : 'Cleanup eligible, artifact missing'}
                              />
                            )}
                          </Stack>
                        </TableCell>
                        <TableCell>
                          <Typography variant="body2" noWrap>
                            {task.createdBy || '—'}
                          </Typography>
                        </TableCell>
                        <TableCell>
                          <Stack spacing={0.25}>
                            <Typography variant="body2">{task.createdAt ? format(new Date(task.createdAt), 'PPp') : '—'}</Typography>
                            <Typography variant="caption" color="text.secondary">
                              {task.startedAt ? `Started ${format(new Date(task.startedAt), 'PPp')}` : 'Not started yet'}
                            </Typography>
                          </Stack>
                        </TableCell>
                        <TableCell>
                          <Stack spacing={0.25}>
                            <Typography variant="body2">{task.completedAt ? format(new Date(task.completedAt), 'PPp') : '—'}</Typography>
                            <Typography variant="caption" color="text.secondary">
                              {task.retentionExpiresAt
                                ? `Expires ${format(new Date(task.retentionExpiresAt), 'PPp')}`
                                : task.cleanupEligible
                                  ? 'Ready for cleanup'
                                  : 'Active task'}
                            </Typography>
                          </Stack>
                        </TableCell>
                        <TableCell>
                          <Stack spacing={0.25}>
                            <Typography variant="body2">{`${task.filesAdded}/${task.totalFiles} files`}</Typography>
                            <Typography variant="caption" color="text.secondary">
                              {task.bytesAdded > 0 ? `${formatFileSize(task.bytesAdded)} written` : 'No bytes written yet'}
                            </Typography>
                          </Stack>
                        </TableCell>
                        <TableCell sx={{ maxWidth: 220 }}>
                          <Stack spacing={0.25}>
                            <Typography variant="body2" noWrap>{task.filename || '—'}</Typography>
                            <Typography variant="caption" color="text.secondary">
                              {task.archiveSizeBytes !== undefined && task.archiveSizeBytes !== null
                                ? `${formatFileSize(task.archiveSizeBytes)} archive`
                                : task.downloadReady
                                  ? 'Archive size unavailable'
                                  : 'Archive pending'}
                            </Typography>
                          </Stack>
                        </TableCell>
                        <TableCell align="right">
                          <Box display="flex" justifyContent="flex-end" gap={0.5}>
                            {canCancel && (
                              <Button
                                size="small"
                                variant="outlined"
                                color="warning"
                                disabled={Boolean(batchDownloadAdminActionTaskId)}
                                onClick={() => void handleCancelBatchDownloadAdminTask(task.taskId)}
                              >
                                {actionBusy && batchDownloadAdminActionType === 'cancel' ? 'Cancelling...' : 'Cancel'}
                              </Button>
                            )}
                            {canCleanup && (
                              <Button
                                size="small"
                                variant="outlined"
                                color="secondary"
                                startIcon={<CleanupIcon />}
                                disabled={Boolean(batchDownloadAdminActionTaskId)}
                                onClick={() => void handleCleanupBatchDownloadAdminTask(task)}
                              >
                                {actionBusy && batchDownloadAdminActionType === 'cleanup' ? 'Cleaning...' : 'Cleanup'}
                              </Button>
                            )}
                            {canDownload && (
                              <Button
                                size="small"
                                variant="outlined"
                                startIcon={<DownloadIcon />}
                                disabled={Boolean(batchDownloadAdminActionTaskId)}
                                onClick={() => void handleDownloadBatchDownloadAdminTask(task)}
                              >
                                {actionBusy && batchDownloadAdminActionType === 'download' ? 'Downloading...' : 'Download'}
                              </Button>
                            )}
                          </Box>
                        </TableCell>
                      </TableRow>
                    );
                  })}
                </TableBody>
              </Table>
            </TableContainer>
          )}
        </Paper>

        <Paper sx={{ p: 2, mb: 3 }}>
          <Box display="flex" justifyContent="space-between" alignItems="center" mb={1}>
            <Box display="flex" alignItems="center" gap={1}>
              <Star fontSize="small" color="warning" />
              <Typography component="h2" variant="h6" color="primary">
                Pinned Saved Searches
              </Typography>
            </Box>
            <Button variant="outlined" size="small" onClick={() => navigate('/saved-searches')}>
              Manage
            </Button>
          </Box>
          {pinnedLoading ? (
            <Box display="flex" alignItems="center" gap={2} py={1}>
              <CircularProgress size={20} />
              <Typography variant="body2" color="text.secondary">
                Loading pinned searches…
              </Typography>
            </Box>
          ) : pinnedError ? (
            <Typography variant="body2" color="error">
              {pinnedError}
            </Typography>
          ) : pinnedSearches.length === 0 ? (
            <Typography variant="body2" color="text.secondary">
              No pinned searches yet. Pin a saved search to see it here.
            </Typography>
          ) : (
            <List dense>
              {pinnedSearches.map((item, index) => (
                <React.Fragment key={item.id}>
                  <ListItem
                    secondaryAction={(
                      <Box display="flex" gap={1}>
                        <IconButton
                          size="small"
                          aria-label={`Run saved search ${item.name}`}
                          onClick={() => handleRunPinnedSearch(item)}
                        >
                          <PlayArrow fontSize="small" />
                        </IconButton>
                        <IconButton
                          size="small"
                          aria-label={`Unpin saved search ${item.name}`}
                          onClick={() => handleTogglePinnedSearch(item)}
                        >
                          <Star fontSize="small" color="warning" />
                        </IconButton>
                      </Box>
                    )}
                  >
                    <ListItemText
                      primary={item.name}
                      secondary={item.createdAt ? `Created ${format(new Date(item.createdAt), 'PPp')}` : undefined}
                    />
                  </ListItem>
                  {index < pinnedSearches.length - 1 && <Divider component="li" />}
                </React.Fragment>
              ))}
            </List>
          )}
        </Paper>

        <Paper sx={{ p: 2, mb: 3 }}>
          <Box display="flex" justifyContent="space-between" alignItems="center" mb={1}>
            <Box display="flex" alignItems="center" gap={1}>
              <MailOutline fontSize="small" color="primary" />
              <Typography component="h2" variant="h6" color="primary">
                Mail Automation
              </Typography>
            </Box>
            <Box display="flex" alignItems="center" gap={1}>
              <Button variant="outlined" size="small" onClick={() => navigate('/admin/mail#diagnostics')}>
                Open
              </Button>
              <Button
                variant="contained"
                size="small"
                startIcon={mailFetchTriggering ? <CircularProgress size={14} /> : <PlayArrow fontSize="small" />}
                onClick={handleTriggerMailFetch}
                disabled={mailFetchTriggering}
              >
                Trigger Fetch
              </Button>
              <IconButton
                size="small"
                onClick={() => fetchMailFetchSummary()}
                aria-label="Refresh mail fetch summary"
              >
                <Refresh fontSize="small" />
              </IconButton>
            </Box>
          </Box>

          {mailFetchSummaryLoading ? (
            <Box display="flex" alignItems="center" gap={2} py={1}>
              <CircularProgress size={20} />
              <Typography variant="body2" color="text.secondary">
                Loading mail fetch summary…
              </Typography>
            </Box>
          ) : mailFetchSummaryError ? (
            <Typography variant="body2" color="error">
              {mailFetchSummaryError}
            </Typography>
          ) : mailFetchSummary?.summary ? (
            <>
              {(mailFetchSummary.summary.accountErrors > 0 || mailFetchSummary.summary.errorMessages > 0) && (
                <Box mb={1} display="flex" alignItems="center" gap={1} flexWrap="wrap">
                  <Typography variant="body2" color="error">
                    Attention: errors detected in the last mail fetch run.
                  </Typography>
                  <Button variant="outlined" size="small" onClick={() => navigate('/admin/mail#diagnostics')}>
                    Open diagnostics
                  </Button>
                </Box>
              )}
              <Box display="flex" gap={1} flexWrap="wrap" mb={1}>
                <Chip
                  size="small"
                  label={`Accounts ${mailFetchSummary.summary.attemptedAccounts}/${mailFetchSummary.summary.accounts}`}
                  variant="outlined"
                />
                <Chip
                  size="small"
                  label={`Skipped ${mailFetchSummary.summary.skippedAccounts}`}
                  variant="outlined"
                />
                <Chip
                  size="small"
                  label={`Account errors ${mailFetchSummary.summary.accountErrors}`}
                  color={mailFetchSummary.summary.accountErrors > 0 ? 'warning' : 'default'}
                  variant="outlined"
                />
                <Chip
                  size="small"
                  label={`Duration ${formatDuration(mailFetchSummary.summary.durationMs)}`}
                  variant="outlined"
                />
                {mailFetchSummary.summary.runId && (
                  <Chip
                    size="small"
                    label={`Run ${mailFetchSummary.summary.runId.slice(0, 8)}`}
                    variant="outlined"
                  />
                )}
              </Box>
              <Box display="flex" gap={1} flexWrap="wrap" mb={1}>
                <Chip size="small" label={`Found ${mailFetchSummary.summary.foundMessages}`} />
                <Chip size="small" label={`Matched ${mailFetchSummary.summary.matchedMessages}`} />
                <Chip size="small" label={`Processed ${mailFetchSummary.summary.processedMessages}`} color="success" />
                <Chip size="small" label={`Skipped ${mailFetchSummary.summary.skippedMessages}`} />
                <Chip
                  size="small"
                  label={`Errors ${mailFetchSummary.summary.errorMessages}`}
                  color={mailFetchSummary.summary.errorMessages > 0 ? 'warning' : 'default'}
                />
              </Box>
              {mailFetchSummary.fetchedAt && (
                <Typography variant="caption" color="text.secondary" display="block">
                  Last fetched {format(new Date(mailFetchSummary.fetchedAt), 'PPp')}
                </Typography>
              )}
            </>
          ) : (
            <Typography variant="body2" color="text.secondary">
              No mail fetch summary yet. Trigger a fetch from Mail Automation to populate this card.
            </Typography>
          )}
        </Paper>

        <Paper sx={{ p: 2, mb: 3 }}>
          <Box display="flex" justifyContent="space-between" alignItems="center" mb={1}>
            <Box display="flex" alignItems="center" gap={1}>
              <WorkspacePremium color="primary" fontSize="small" />
              <Typography component="h2" variant="h6" color="primary">
                License
              </Typography>
            </Box>
            {licenseInfo ? (
              <Chip
                size="small"
                label={licenseInfo.valid ? 'Valid' : 'Invalid'}
                color={licenseInfo.valid ? 'success' : 'error'}
                variant="outlined"
              />
            ) : (
              <Chip size="small" label="Unavailable" variant="outlined" />
            )}
          </Box>
          {licenseInfo ? (
            <>
              <Box display="flex" gap={1} flexWrap="wrap">
                <Chip size="small" label={`Edition: ${licenseInfo.edition}`} />
                <Chip size="small" label={`Max Users: ${licenseInfo.maxUsers}`} variant="outlined" />
                <Chip size="small" label={`Max Storage: ${licenseInfo.maxStorageGb} GB`} variant="outlined" />
                <Chip
                  size="small"
                  label={
                    licenseInfo.expirationDate
                      ? `Expires: ${format(new Date(licenseInfo.expirationDate), 'PP')}`
                      : 'Expires: Never'
                  }
                  variant="outlined"
                />
              </Box>
              {(licenseInfo.features || []).length > 0 && (
                <Box display="flex" gap={1} flexWrap="wrap" mt={1}>
                  {(licenseInfo.features || []).map((feature) => (
                    <Chip key={feature} size="small" label={feature} color="info" variant="outlined" />
                  ))}
                </Box>
              )}
            </>
          ) : (
            <Typography variant="body2" color="text.secondary">
              Unable to fetch license info.
            </Typography>
          )}
        </Paper>

        <Paper sx={{ p: 2, mb: 3 }}>
          <Box display="flex" justifyContent="space-between" alignItems="center" mb={1}>
            <Typography component="h2" variant="h6" color="primary">
              Rule Execution
            </Typography>
            <Chip
              size="small"
              label={`Last ${ruleSummary?.windowDays ?? 7} days`}
              variant="outlined"
            />
          </Box>
          <Grid container spacing={2} mb={1}>
            <Grid item xs={12} sm={6} md={3}>
              <SummaryCard
                title="Executions"
                value={ruleSummary?.executions ?? 0}
                icon={<History fontSize="large" color="primary" />}
              />
            </Grid>
            <Grid item xs={12} sm={6} md={3}>
              <SummaryCard
                title="Failures"
                value={ruleSummary?.failures ?? 0}
                icon={<History fontSize="large" color="error" />}
              />
            </Grid>
            <Grid item xs={12} sm={6} md={3}>
              <SummaryCard
                title="Success Rate"
                value={`${(ruleSummary?.successRate ?? 0).toFixed(1)}%`}
                icon={<TrendingUp fontSize="large" color="success" />}
              />
            </Grid>
            <Grid item xs={12} sm={6} md={3}>
              <SummaryCard
                title="Scheduled Batches"
                value={ruleSummary?.scheduledBatches ?? 0}
                icon={<History fontSize="large" color="info" />}
              />
            </Grid>
          </Grid>

          <Box display="flex" justifyContent="space-between" alignItems="center" mb={1} mt={2}>
            <Typography component="h3" variant="subtitle1" color="text.primary">
              Recent Rule Activity
            </Typography>
            <Autocomplete
              size="small"
              multiple
              options={RULE_EVENT_TYPES}
              value={ruleEventFilter}
              onChange={(_, value) => setRuleEventFilter(value)}
              renderTags={(value, getTagProps) =>
                value.map((option, index) => (
                  <Chip
                    {...getTagProps({ index })}
                    key={option}
                    size="small"
                    label={RULE_EVENT_LABELS[option] || option}
                    variant="outlined"
                  />
                ))
              }
              renderInput={(params) => (
                <TextField {...params} label="Filter events" placeholder="Event types" />
              )}
              sx={{ minWidth: 260 }}
            />
          </Box>

          <List dense>
            {filteredRuleEvents.map((log, index) => (
              <React.Fragment key={log.id}>
                <ListItem alignItems="flex-start">
                  <ListItemIcon>
                    <History />
                  </ListItemIcon>
                  <ListItemText
                    primary={
                      <Box display="flex" justifyContent="space-between">
                        <Typography variant="subtitle2">
                          {RULE_EVENT_LABELS[log.eventType] || log.eventType}
                        </Typography>
                        <Typography variant="caption" color="text.secondary">
                          {format(new Date(log.eventTime), 'PPpp')}
                        </Typography>
                      </Box>
                    }
                    secondary={
                      <Typography variant="body2" color="text.secondary" sx={{ mt: 0.5 }}>
                        {log.details || log.nodeName}
                      </Typography>
                    }
                  />
                </ListItem>
                {index < filteredRuleEvents.length - 1 && <Divider variant="inset" component="li" />}
              </React.Fragment>
            ))}
            {filteredRuleEvents.length === 0 && (
              <Typography variant="body2" sx={{ p: 2 }}>
                No rule activity found for the selected filters.
              </Typography>
            )}
          </List>
        </Paper>

        <Grid container spacing={3}>
          <Grid item xs={12} md={8}>
            <Paper sx={{ p: 2, display: 'flex', flexDirection: 'column', height: '100%' }}>
              <Typography component="h2" variant="h6" color="primary" gutterBottom>
                Storage Distribution
              </Typography>
              <List>
                {data?.storage.map((stat, index) => (
                  <React.Fragment key={stat.mimeType}>
                    <ListItem>
                      <ListItemIcon>{getFileIcon(stat.mimeType)}</ListItemIcon>
                      <ListItemText
                        primary={stat.mimeType}
                        secondary={`${stat.count} files`}
                      />
                      <Box sx={{ minWidth: 100, textAlign: 'right' }}>
                        <Typography variant="body2" color="text.secondary">
                          {formatFileSize(stat.sizeBytes)}
                        </Typography>
                      </Box>
                    </ListItem>
                    <Box sx={{ px: 2, pb: 1 }}>
                      <LinearProgress
                        variant="determinate"
                        value={(stat.sizeBytes / (data?.summary.totalSizeBytes || 1)) * 100}
                        sx={{ height: 8, borderRadius: 4 }}
                      />
                    </Box>
                    {index < (data?.storage.length || 0) - 1 && <Divider component="li" />}
                  </React.Fragment>
                ))}
              </List>
            </Paper>
          </Grid>

          <Grid item xs={12} md={4}>
            <Paper sx={{ p: 2, display: 'flex', flexDirection: 'column', height: '100%' }}>
              <Typography component="h2" variant="h6" color="primary" gutterBottom>
                Top Active Users
              </Typography>
              <List dense>
                {data?.topUsers.map((user) => (
                  <ListItem key={user.username}>
                    <ListItemIcon>
                      <People fontSize="small" />
                    </ListItemIcon>
                    <ListItemText primary={user.username} />
                    <Chip
                      label={user.activityCount}
                      size="small"
                      color="primary"
                      variant="outlined"
                    />
                  </ListItem>
                ))}
              </List>
            </Paper>
          </Grid>

          <Grid item xs={12}>
            <Paper sx={{ p: 2, display: 'flex', flexDirection: 'column' }}>
              <Box display="flex" justifyContent="space-between" alignItems="center" mb={1}>
                <Typography component="h2" variant="h6" color="primary">
                  Recent System Activity
                </Typography>
                <Box display="flex" gap={1} alignItems="center" flexWrap="wrap">
                  {retentionInfo && (
                    <Chip
                      size="small"
                      label={`Retention: ${retentionInfo.retentionDays} days`}
                      variant="outlined"
                    />
                  )}
                  {retentionInfo?.exportMaxRangeDays && (
                    <Chip
                      size="small"
                      label={`Export max: ${retentionInfo.exportMaxRangeDays} days`}
                      variant="outlined"
                    />
                  )}
                  {retentionInfo && retentionInfo.expiredLogCount > 0 && (
                    <Chip
                      size="small"
                      label={`${retentionInfo.expiredLogCount} expired`}
                      color="warning"
                      variant="outlined"
                    />
                  )}
                  {auditReport && (
                    <Chip
                      size="small"
                      label={`Audit last ${auditReport.windowDays}d: ${auditReport.totalEvents}`}
                      variant="outlined"
                    />
                  )}
                  <FormControl size="small" sx={{ minWidth: 180 }}>
                    <InputLabel id="audit-export-preset-label">Export Preset</InputLabel>
                    <Select
                      labelId="audit-export-preset-label"
                      label="Export Preset"
                      value={auditExportPreset}
                      onChange={(event) => {
                        const value = String(event.target.value);
                        setAuditExportPreset(value);
                        if (value !== 'custom') {
                          setAuditQuickRange('custom');
                        }
                      }}
                    >
                      <MenuItem value="custom">Custom range</MenuItem>
                      {auditPresets.map((preset) => (
                        <MenuItem key={preset.id} value={preset.id}>
                          {preset.label}
                        </MenuItem>
                      ))}
                    </Select>
                  </FormControl>
                  <Autocomplete
                    freeSolo
                    options={auditUserSuggestions}
                    value={auditFilterUser}
                    onInputChange={(_, value) => setAuditFilterUser(value)}
                    renderInput={(params) => (
                      <TextField
                        {...params}
                        label="User"
                        size="small"
                        sx={{ minWidth: 160 }}
                      />
                    )}
                  />
                  <Autocomplete
                    freeSolo
                    options={auditEventTypes.map((item) => item.eventType)}
                    value={auditFilterEventType}
                    onInputChange={(_, value) => setAuditFilterEventType(normalizeAuditEventType(value))}
                    getOptionLabel={(option) => formatAuditEventTypeLabel(String(option))}
                    renderOption={(props, option) => (
                      <li {...props}>{formatAuditEventTypeLabel(String(option))}</li>
                    )}
                    renderInput={(params) => (
                      <TextField
                        {...params}
                        label="Event Type"
                        size="small"
                        sx={{ minWidth: 180 }}
                      />
                    )}
                  />
                  <FormControl size="small" sx={{ minWidth: 160 }}>
                    <InputLabel id="audit-category-label">Category</InputLabel>
                    <Select
                      labelId="audit-category-label"
                      label="Category"
                      value={auditFilterCategory}
                      onChange={(event) => setAuditFilterCategory(String(event.target.value))}
                    >
                      <MenuItem value="">All</MenuItem>
                      {auditCategoryOptions.map((option) => (
                        <MenuItem key={option.value} value={option.value}>
                          {option.label}
                        </MenuItem>
                      ))}
                    </Select>
                  </FormControl>
                  <TextField
                    label="Node ID"
                    size="small"
                    value={auditFilterNodeId}
                    onChange={(event) => setAuditFilterNodeId(event.target.value)}
                    sx={{ minWidth: 280 }}
                  />
                  <Box display="flex" alignItems="center" gap={0.5}>
                    <Typography variant="caption" color="text.secondary">
                      Quick range
                    </Typography>
                    <Button
                      size="small"
                      variant={auditQuickRange === '24h' ? 'contained' : 'outlined'}
                      onClick={() => applyAuditQuickRange('24h')}
                    >
                      24h
                    </Button>
                    <Button
                      size="small"
                      variant={auditQuickRange === '7d' ? 'contained' : 'outlined'}
                      onClick={() => applyAuditQuickRange('7d')}
                    >
                      7d
                    </Button>
                    <Button
                      size="small"
                      variant={auditQuickRange === '30d' ? 'contained' : 'outlined'}
                      onClick={() => applyAuditQuickRange('30d')}
                    >
                      30d
                    </Button>
                  </Box>
                  <TextField
                    label="From"
                    type="datetime-local"
                    size="small"
                    value={auditExportFrom}
                    onChange={(event) => {
                      setAuditExportFrom(event.target.value);
                      setAuditQuickRange('custom');
                    }}
                    InputLabelProps={{ shrink: true }}
                    error={Boolean(auditExportRangeError)}
                    disabled={!isCustomExport}
                    sx={{ minWidth: 210 }}
                  />
                  <TextField
                    label="To"
                    type="datetime-local"
                    size="small"
                    value={auditExportTo}
                    onChange={(event) => {
                      setAuditExportTo(event.target.value);
                      setAuditQuickRange('custom');
                    }}
                    InputLabelProps={{ shrink: true }}
                    error={Boolean(auditExportRangeError)}
                    helperText={auditExportHelperText}
                    disabled={!isCustomExport}
                    sx={{ minWidth: 210 }}
                  />
                  <Button
                    size="small"
                    variant="outlined"
                    onClick={() => void handleFilterAuditLogs()}
                    disabled={filteringAudit}
                  >
                    {filteringAudit ? 'Filtering...' : 'Filter Logs'}
                  </Button>
                  <Button
                    size="small"
                    variant="text"
                    onClick={handleResetAuditLogs}
                  >
                    Reset
                  </Button>
                  <Button
                    size="small"
                    variant="outlined"
                    startIcon={<DownloadIcon />}
                    onClick={handleExportAuditLogs}
                    disabled={exportingAudit || Boolean(auditExportRangeError) || exportPresetError}
                  >
                    {exportingAudit ? 'Exporting...' : 'Export CSV'}
                  </Button>
                  <Button
                    size="small"
                    variant="outlined"
                    startIcon={<DownloadIcon />}
                    onClick={() => void handleStartAuditExportAsync()}
                    disabled={auditExportAsyncStarting || Boolean(auditExportRangeError) || exportPresetError}
                    aria-label="Start audit async export"
                  >
                    {auditExportAsyncStarting ? 'Starting...' : 'Start Async Export'}
                  </Button>
                  <Button
                    size="small"
                    variant="outlined"
                    startIcon={<Refresh />}
                    onClick={() => void loadAuditExportAsyncTasks(false)}
                    disabled={auditExportAsyncTasksLoading}
                    aria-label="Refresh audit async export tasks"
                  >
                    {auditExportAsyncTasksLoading ? 'Refreshing tasks...' : 'Refresh async tasks'}
                  </Button>
                  <Button
                    size="small"
                    variant="outlined"
                    color="warning"
                    startIcon={<DeleteIcon />}
                    onClick={() => void handleCancelActiveAuditExportAsyncTasks()}
                    disabled={auditExportAsyncCancellingActive}
                    aria-label="Cancel active audit async export tasks"
                  >
                    {auditExportAsyncCancellingActive ? 'Cancelling active...' : 'Cancel active tasks'}
                  </Button>
                  <Button
                    size="small"
                    variant="outlined"
                    color="warning"
                    startIcon={<CleanupIcon />}
                    onClick={() => void handleCleanupAuditExportAsyncTasks()}
                    disabled={auditExportAsyncCleaning}
                    aria-label="Cleanup audit async export tasks"
                  >
                    {auditExportAsyncCleaning ? 'Cleaning tasks...' : 'Cleanup tasks'}
                  </Button>
                  {retentionInfo && retentionInfo.expiredLogCount > 0 && (
                    <Button
                      size="small"
                      variant="outlined"
                      color="warning"
                      startIcon={<CleanupIcon />}
                      onClick={handleCleanupAuditLogs}
                      disabled={cleaningAudit}
                    >
                      {cleaningAudit ? 'Cleaning...' : 'Cleanup'}
                    </Button>
                  )}
                </Box>
              </Box>
              {exportPresetError && (
                <Alert severity="warning" sx={{ mb: 1 }}>
                  {exportPresetNeedsUser && !auditFilterUser.trim()
                    ? 'Selected preset requires a user.'
                    : 'Selected preset requires an event type.'}
                </Alert>
              )}
              <Box mb={2}>
                <Box
                  display="flex"
                  alignItems={{ xs: 'flex-start', md: 'center' }}
                  justifyContent="space-between"
                  gap={1}
                  flexDirection={{ xs: 'column', md: 'row' }}
                  sx={{ mb: 1 }}
                >
                  <Typography variant="subtitle2" color="text.secondary">
                    Audit Async Export Tasks
                  </Typography>
                  <FormControl size="small" sx={{ minWidth: 180 }}>
                    <InputLabel id="audit-async-status-filter-label">Task status</InputLabel>
                    <Select
                      labelId="audit-async-status-filter-label"
                      value={auditExportAsyncStatusFilter}
                      label="Task status"
                      onChange={(event) => {
                        const nextFilter = event.target.value as AuditExportAsyncTaskStatusFilter;
                        setAuditExportAsyncStatusFilter(nextFilter);
                        void loadAuditExportAsyncTasks(true, nextFilter);
                      }}
                      inputProps={{ 'aria-label': 'Audit async task status filter' }}
                    >
                      {AUDIT_EXPORT_ASYNC_STATUS_FILTER_OPTIONS.map((option) => (
                        <MenuItem key={`audit-async-status-filter-${option.value}`} value={option.value}>
                          {option.label}
                        </MenuItem>
                      ))}
                    </Select>
                  </FormControl>
                </Box>
                <Box display="flex" gap={1} flexWrap="wrap" sx={{ mb: 1 }}>
                  {auditExportAsyncSummaryLoading && <CircularProgress size={14} />}
                  <Chip size="small" label={`Total ${auditExportAsyncSummary?.totalCount ?? 0}`} variant="outlined" />
                  <Chip size="small" label={`Active ${auditExportAsyncSummary?.activeCount ?? 0}`} color="info" variant="outlined" />
                  <Chip size="small" label={`Terminal ${auditExportAsyncSummary?.terminalCount ?? 0}`} variant="outlined" />
                  <Chip size="small" label={`Completed ${auditExportAsyncSummary?.completedCount ?? 0}`} color="success" variant="outlined" />
                  <Chip size="small" label={`Failed ${auditExportAsyncSummary?.failedCount ?? 0}`} color="warning" variant="outlined" />
                  <Chip size="small" label={`Cancelled ${auditExportAsyncSummary?.cancelledCount ?? 0}`} variant="outlined" />
                </Box>
                {auditExportAsyncTasks.length === 0 ? (
                  <Alert severity="info">No audit async export tasks yet.</Alert>
                ) : (
                  <TableContainer>
                    <Table size="small" aria-label="Audit async export tasks">
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
                        {auditExportAsyncTasks.map((task) => {
                          const normalizedStatus = normalizeAuditAsyncTaskStatus(task.status);
                          const canCancel = AUDIT_EXPORT_ASYNC_RUNNING_STATUSES.has(normalizedStatus);
                          const canDownload = AUDIT_EXPORT_ASYNC_COMPLETED_STATUSES.has(normalizedStatus);
                          const actionBusy = auditExportAsyncActionTaskId === task.taskId;
                          const cancelBusy = actionBusy && auditExportAsyncActionType === 'cancel';
                          const downloadBusy = actionBusy && auditExportAsyncActionType === 'download';
                          return (
                            <TableRow key={`audit-export-task-${task.taskId}`}>
                              <TableCell sx={{ maxWidth: 320 }}>
                                <Typography variant="body2" noWrap>{task.taskId}</Typography>
                              </TableCell>
                              <TableCell>
                                <Chip size="small" label={normalizedStatus} variant="outlined" />
                              </TableCell>
                              <TableCell>{task.createdAt ? format(new Date(task.createdAt), 'PPp') : '—'}</TableCell>
                              <TableCell>{task.finishedAt ? format(new Date(task.finishedAt), 'PPp') : '—'}</TableCell>
                              <TableCell sx={{ maxWidth: 260 }}>
                                <Typography variant="body2" noWrap>{task.filename || '—'}</Typography>
                              </TableCell>
                              <TableCell align="right">
                                <Box display="flex" justifyContent="flex-end" gap={0.5}>
                                  {canCancel && (
                                    <Button
                                      size="small"
                                      variant="outlined"
                                      color="warning"
                                      disabled={Boolean(auditExportAsyncActionTaskId)}
                                      aria-label={`Cancel audit async export task ${task.taskId}`}
                                      onClick={() => void handleCancelAuditExportAsyncTask(task.taskId)}
                                    >
                                      {cancelBusy ? 'Cancelling...' : 'Cancel'}
                                    </Button>
                                  )}
                                  {canDownload && (
                                    <Button
                                      size="small"
                                      variant="outlined"
                                      startIcon={<DownloadIcon />}
                                      disabled={Boolean(auditExportAsyncActionTaskId)}
                                      aria-label={`Download audit async export task ${task.taskId}`}
                                      onClick={() => void handleDownloadAuditExportAsyncTask(task)}
                                    >
                                      {downloadBusy ? 'Downloading...' : 'Download'}
                                    </Button>
                                  )}
                                </Box>
                              </TableCell>
                            </TableRow>
                          );
                        })}
                      </TableBody>
                    </Table>
                  </TableContainer>
                )}
              </Box>
              <Box display="flex" alignItems="center" gap={1} flexWrap="wrap" mb={1}>
                <Typography variant="subtitle2" color="text.secondary">
                  Audit Categories
                </Typography>
                {(auditCategoriesLoading || auditCategoriesUpdating) && (
                  <CircularProgress size={16} />
                )}
              </Box>
              <Box display="flex" flexWrap="wrap" gap={2} mb={2}>
                {auditCategories.map((category) => (
                  <FormControlLabel
                    key={category.category}
                    control={
                      <Switch
                        size="small"
                        checked={category.enabled}
                        onChange={() => handleToggleAuditCategory(category.category)}
                        disabled={auditCategoriesUpdating}
                      />
                    }
                    label={formatAuditCategoryLabel(category.category)}
                  />
                ))}
                {!auditCategoriesLoading && auditCategories.length === 0 && (
                  <Typography variant="body2" color="text.secondary">
                    No audit categories available.
                  </Typography>
                )}
              </Box>
              {auditReport && Object.keys(auditReport.countsByCategory || {}).length > 0 && (
                <Box display="flex" flexWrap="wrap" gap={1} mb={2}>
                  {Object.entries(auditReport.countsByCategory).map(([category, count]) => (
                    <Chip
                      key={category}
                      size="small"
                      variant="outlined"
                      label={`${formatAuditCategoryLabel(category)}: ${count}`}
                    />
                  ))}
                </Box>
              )}
              <List>
                {logs.map((log, index) => (
                  <React.Fragment key={log.id}>
                    <ListItem alignItems="flex-start">
                      <ListItemIcon>
                        <History />
                      </ListItemIcon>
                      <ListItemText
                        primary={
                          <Box display="flex" justifyContent="space-between">
                            <Typography variant="subtitle2">
                              {log.username} - {formatAuditEventTypeLabel(log.eventType)}
                            </Typography>
                            <Typography variant="caption" color="text.secondary">
                              {format(new Date(log.eventTime), 'PPpp')}
                            </Typography>
                          </Box>
                        }
                        secondary={
                          <Typography
                            variant="body2"
                            color="text.secondary"
                            sx={{ mt: 0.5 }}
                          >
                            {log.details || `Action on ${log.nodeName}`}
                          </Typography>
                        }
                      />
                    </ListItem>
                    {index < logs.length - 1 && <Divider variant="inset" component="li" />}
                  </React.Fragment>
                ))}
                {logs.length === 0 && (
                  <Typography variant="body2" sx={{ p: 2 }}>
                    No recent activity found.
                  </Typography>
                )}
              </List>
            </Paper>
          </Grid>
        </Grid>
      </>
    );
  };

  return (
    <Container maxWidth="lg" sx={{ mt: 4, mb: 4 }}>
      <Tabs value={tab} onChange={(_, v) => setTab(v)} sx={{ mb: 3 }}>
        <Tab label="Overview" />
        <Tab label="Users" />
        <Tab label="Groups" />
        <Tab label="Share Links" />
      </Tabs>

      {tab === 0 && <OverviewPanel />}

      {tab === 1 && (
        <>
          <Box display="flex" justifyContent="space-between" alignItems="center" mb={2}>
            <TextField
              placeholder="Search users..."
              size="small"
              value={userQuery}
              onChange={(e) => setUserQuery(e.target.value)}
              onKeyDown={(e) => e.key === 'Enter' && loadUsers(userQuery)}
              sx={{ width: 280 }}
            />
            <Box display="flex" gap={1}>
              <Button variant="outlined" onClick={() => loadUsers(userQuery)}>
                Search
              </Button>
              <Button
                variant="contained"
                startIcon={<PersonAdd />}
                onClick={() => setCreateUserOpen(true)}
              >
                New User
              </Button>
            </Box>
          </Box>

          <TableContainer component={Paper}>
            <Table size="small">
              <TableHead>
                <TableRow>
                  <TableCell>Username</TableCell>
                  <TableCell>Email</TableCell>
                  <TableCell>Full Name</TableCell>
                  <TableCell>Roles</TableCell>
                  <TableCell align="center">Enabled</TableCell>
                </TableRow>
              </TableHead>
              <TableBody>
                {usersLoading ? (
                  <TableRow>
                    <TableCell colSpan={5} align="center">
                      <CircularProgress size={20} />
                    </TableCell>
                  </TableRow>
                ) : (
                  users.map((u) => (
                    <TableRow key={u.username} hover>
                      <TableCell>{u.username}</TableCell>
                      <TableCell>{u.email}</TableCell>
                      <TableCell>{`${u.firstName || ''} ${u.lastName || ''}`.trim() || '-'}</TableCell>
                      <TableCell>
                        {u.roles?.length ? (
                          <Box display="flex" gap={0.5} flexWrap="wrap">
                            {u.roles.map((role) => (
                              <Chip key={role} label={role} size="small" variant="outlined" sx={{ mb: 0.5 }} />
                            ))}
                          </Box>
                        ) : (
                          '-'
                        )}
                      </TableCell>
                      <TableCell align="center">
                        <Switch
                          size="small"
                          checked={u.enabled !== false}
                          onChange={async (e) => {
                            try {
                              await userGroupService.updateUser(u.username, { enabled: e.target.checked });
                              toast.success('User updated');
                              loadUsers(userQuery);
                            } catch {
                              toast.error('Failed to update user');
                            }
                          }}
                        />
                      </TableCell>
                    </TableRow>
                  ))
                )}
                {!usersLoading && users.length === 0 && (
                  <TableRow>
                    <TableCell colSpan={5} align="center">
                      No users
                    </TableCell>
                  </TableRow>
                )}
              </TableBody>
            </Table>
          </TableContainer>

          <Dialog open={createUserOpen} onClose={() => setCreateUserOpen(false)} maxWidth="sm" fullWidth>
            <DialogTitle>Create User</DialogTitle>
            <DialogContent>
              <Box display="flex" flexDirection="column" gap={2} mt={1}>
                <TextField
                  label="Username"
                  value={newUser.username}
                  onChange={(e) => setNewUser({ ...newUser, username: e.target.value })}
                  size="small"
                />
                <TextField
                  label="Email"
                  value={newUser.email}
                  onChange={(e) => setNewUser({ ...newUser, email: e.target.value })}
                  size="small"
                />
                <TextField
                  label="Password"
                  type="password"
                  value={newUser.password}
                  onChange={(e) => setNewUser({ ...newUser, password: e.target.value })}
                  size="small"
                />
                <TextField
                  label="First Name"
                  value={newUser.firstName}
                  onChange={(e) => setNewUser({ ...newUser, firstName: e.target.value })}
                  size="small"
                />
                <TextField
                  label="Last Name"
                  value={newUser.lastName}
                  onChange={(e) => setNewUser({ ...newUser, lastName: e.target.value })}
                  size="small"
                />
              </Box>
            </DialogContent>
            <DialogActions>
              <Button onClick={() => setCreateUserOpen(false)}>Cancel</Button>
              <Button
                variant="contained"
                onClick={async () => {
                  try {
                    await userGroupService.createUser({
                      username: newUser.username,
                      email: newUser.email,
                      password: newUser.password,
                      firstName: newUser.firstName || undefined,
                      lastName: newUser.lastName || undefined,
                    });
                    toast.success('User created');
                    setCreateUserOpen(false);
                    setNewUser({
                      username: '',
                      email: '',
                      password: '',
                      firstName: '',
                      lastName: '',
                      enabled: true,
                    });
                    loadUsers();
                  } catch {
                    toast.error('Failed to create user');
                  }
                }}
                disabled={!newUser.username || !newUser.email || !newUser.password}
              >
                Create
              </Button>
            </DialogActions>
          </Dialog>
        </>
      )}

      {tab === 2 && (
        <>
          <Box display="flex" justifyContent="space-between" alignItems="center" mb={2}>
            <Typography variant="h6">Groups</Typography>
            <Button
              variant="contained"
              startIcon={<GroupAdd />}
              onClick={() => setCreateGroupOpen(true)}
            >
              New Group
            </Button>
          </Box>

          <TableContainer component={Paper}>
            <Table size="small">
              <TableHead>
                <TableRow>
                  <TableCell>Name</TableCell>
                  <TableCell>Display Name</TableCell>
                  <TableCell align="center">Members</TableCell>
                  <TableCell align="right" width={140}>Actions</TableCell>
                </TableRow>
              </TableHead>
              <TableBody>
                {groupsLoading ? (
                  <TableRow>
                    <TableCell colSpan={4} align="center">
                      <CircularProgress size={20} />
                    </TableCell>
                  </TableRow>
                ) : (
                  groups.map((g) => (
                    <TableRow key={g.name} hover>
                      <TableCell>
                        <Box display="flex" alignItems="center" gap={1}>
                          <GroupIcon fontSize="small" />
                          {g.name}
                        </Box>
                      </TableCell>
                      <TableCell>{g.displayName || '-'}</TableCell>
                      <TableCell align="center">{g.users?.length ?? '-'}</TableCell>
                      <TableCell align="right">
                        <Button size="small" onClick={() => setMembersGroup(g)}>
                          Members
                        </Button>
                        <IconButton
                          size="small"
                          color="error"
                          onClick={async () => {
                            if (!window.confirm(`Delete group "${g.name}"?`)) return;
                            try {
                              await userGroupService.deleteGroup(g.name);
                              toast.success('Group deleted');
                              loadGroups();
                            } catch {
                              toast.error('Failed to delete group');
                            }
                          }}
                        >
                          <DeleteIcon fontSize="small" />
                        </IconButton>
                      </TableCell>
                    </TableRow>
                  ))
                )}
                {!groupsLoading && groups.length === 0 && (
                  <TableRow>
                    <TableCell colSpan={4} align="center">
                      No groups
                    </TableCell>
                  </TableRow>
                )}
              </TableBody>
            </Table>
          </TableContainer>

          <Dialog open={createGroupOpen} onClose={() => setCreateGroupOpen(false)} maxWidth="sm" fullWidth>
            <DialogTitle>Create Group</DialogTitle>
            <DialogContent>
              <Box display="flex" flexDirection="column" gap={2} mt={1}>
                <TextField
                  label="Name"
                  value={newGroup.name}
                  onChange={(e) => setNewGroup({ ...newGroup, name: e.target.value })}
                  size="small"
                />
                <TextField
                  label="Display Name"
                  value={newGroup.displayName}
                  onChange={(e) => setNewGroup({ ...newGroup, displayName: e.target.value })}
                  size="small"
                />
              </Box>
            </DialogContent>
            <DialogActions>
              <Button onClick={() => setCreateGroupOpen(false)}>Cancel</Button>
              <Button
                variant="contained"
                onClick={async () => {
                  try {
                    await userGroupService.createGroup(newGroup.name, newGroup.displayName || undefined);
                    toast.success('Group created');
                    setCreateGroupOpen(false);
                    setNewGroup({ name: '', displayName: '' });
                    loadGroups();
                  } catch {
                    toast.error('Failed to create group');
                  }
                }}
                disabled={!newGroup.name}
              >
                Create
              </Button>
            </DialogActions>
          </Dialog>

          <Dialog open={Boolean(membersGroup)} onClose={() => setMembersGroup(null)} maxWidth="sm" fullWidth>
            <DialogTitle>Manage Members</DialogTitle>
            <DialogContent>
              <Box display="flex" gap={1} mt={1} mb={2}>
                <Autocomplete
                  options={availableUsernames}
                  value={memberToAdd}
                  onChange={(_, v) => setMemberToAdd(v || '')}
                  renderInput={(params) => <TextField {...params} label="Add user" size="small" />}
                  sx={{ flex: 1 }}
                />
                <Button
                  variant="contained"
                  onClick={async () => {
                    if (!membersGroup || !memberToAdd) return;
                    try {
                      await userGroupService.addUserToGroup(membersGroup.name, memberToAdd);
                      toast.success('Member added');
                      setMemberToAdd('');
                      loadGroups();
                    } catch {
                      toast.error('Failed to add member');
                    }
                  }}
                  disabled={!memberToAdd}
                >
                  Add
                </Button>
              </Box>

              <List dense>
                {(membersGroup?.users || []).map((u) => (
                  <ListItem
                    key={u.username}
                    secondaryAction={
                      <IconButton
                        edge="end"
                        onClick={async () => {
                          if (!membersGroup) return;
                          try {
                            await userGroupService.removeUserFromGroup(membersGroup.name, u.username);
                            toast.success('Member removed');
                            loadGroups();
                          } catch {
                            toast.error('Failed to remove member');
                          }
                        }}
                      >
                        <DeleteIcon fontSize="small" />
                      </IconButton>
                    }
                  >
                    <ListItemText primary={u.username} secondary={u.email} />
                  </ListItem>
                ))}
                {(membersGroup?.users || []).length === 0 && (
                  <Typography variant="body2" color="text.secondary">
                    Members not loaded; add by username above.
                  </Typography>
                )}
              </List>
            </DialogContent>
            <DialogActions>
              <Button onClick={() => setMembersGroup(null)}>Close</Button>
            </DialogActions>
          </Dialog>
        </>
      )}

      {tab === 3 && <ShareLinksAdminPanel />}
    </Container>
  );
};

/* ---------- Share Links Admin Governance Panel (tab 3) ---------- */

type SlStatusFilter = 'ALL' | 'ACTIVE' | 'INACTIVE' | 'EXPIRED';

const ShareLinksAdminPanel: React.FC = () => {
  const [links, setLinks] = React.useState<ShareLinkAdm[]>([]);
  const [loading, setLoading] = React.useState(false);
  const [statusFilter, setStatusFilter] = React.useState<SlStatusFilter>('ALL');
  const [creatorFilter, setCreatorFilter] = React.useState('');
  const [expandedToken, setExpandedToken] = React.useState<string | null>(null);
  const [statsCache, setStatsCache] = React.useState<Record<string, AccessStats>>({});
  const [logCache, setLogCache] = React.useState<Record<string, AccessLogEntry[]>>({});
  const [drillLoading, setDrillLoading] = React.useState(false);

  const load = async () => {
    setLoading(true);
    try {
      const res = await shareLinkServiceImport.listAllLinks();
      setLinks(res as ShareLinkAdm[]);
    } catch { /* handled by api interceptor */ }
    finally { setLoading(false); }
  };

  React.useEffect(() => { void load(); }, []);

  const statusOf = (l: ShareLinkAdm): 'ACTIVE' | 'INACTIVE' | 'EXPIRED' =>
    !l.active ? 'INACTIVE' : !l.isValid ? 'EXPIRED' : 'ACTIVE';

  const filtered = React.useMemo(() => {
    let result = links;
    if (statusFilter !== 'ALL') result = result.filter((l) => statusOf(l) === statusFilter);
    if (creatorFilter.trim()) {
      const q = creatorFilter.trim().toLowerCase();
      result = result.filter((l) => l.createdBy.toLowerCase().includes(q));
    }
    return result;
  }, [links, statusFilter, creatorFilter]);

  const handleDeactivate = async (token: string) => {
    try {
      await shareLinkServiceImport.deactivateLink(token);
      toast.success('Deactivated');
      await load();
    } catch { toast.error('Failed to deactivate'); }
  };

  const handleReactivate = async (token: string) => {
    try {
      await shareLinkServiceImport.reactivateLink(token);
      toast.success('Reactivated');
      await load();
    } catch { toast.error('Failed to reactivate'); }
  };

  const handleDelete = async (token: string) => {
    if (!window.confirm('Permanently delete this share link?')) return;
    try {
      await shareLinkServiceImport.deleteLink(token);
      toast.success('Deleted');
      await load();
    } catch { toast.error('Failed to delete'); }
  };

  const toggleDrill = async (token: string) => {
    if (expandedToken === token) { setExpandedToken(null); return; }
    setExpandedToken(token);
    if (statsCache[token]) return; // already loaded
    setDrillLoading(true);
    try {
      const [stats, log] = await Promise.all([
        shareLinkServiceImport.getAccessStats(token),
        shareLinkServiceImport.getAccessLog(token),
      ]);
      setStatsCache((p) => ({ ...p, [token]: stats }));
      setLogCache((p) => ({ ...p, [token]: log }));
    } catch { toast.error('Failed to load access data'); }
    finally { setDrillLoading(false); }
  };

  const navigateToNode = (nodeId: string) => {
    window.location.href = `/browse/${nodeId}`;
  };

  const statusChipColor = (s: string) =>
    s === 'ACTIVE' ? 'success' as const : s === 'EXPIRED' ? 'warning' as const : 'default' as const;

  if (loading) return <Box display="flex" justifyContent="center" p={4}><CircularProgress /></Box>;

  return (
    <>
      {/* header + filters */}
      <Box display="flex" justifyContent="space-between" alignItems="center" mb={2}>
        <Typography variant="h6">Share Links Governance ({filtered.length}/{links.length})</Typography>
        <Box display="flex" gap={1} alignItems="center">
          <TextField
            size="small"
            placeholder="Filter by creator..."
            value={creatorFilter}
            onChange={(e) => setCreatorFilter(e.target.value)}
            sx={{ width: 180 }}
          />
          <FormControl size="small" sx={{ minWidth: 120 }}>
            <InputLabel>Status</InputLabel>
            <Select label="Status" value={statusFilter} onChange={(e) => setStatusFilter(e.target.value as SlStatusFilter)}>
              <MenuItem value="ALL">All</MenuItem>
              <MenuItem value="ACTIVE">Active</MenuItem>
              <MenuItem value="INACTIVE">Inactive</MenuItem>
              <MenuItem value="EXPIRED">Expired</MenuItem>
            </Select>
          </FormControl>
          <Button variant="outlined" startIcon={<Refresh />} onClick={() => void load()}>Refresh</Button>
        </Box>
      </Box>

      <TableContainer component={Paper}>
        <Table size="small">
          <TableHead>
            <TableRow>
              <TableCell width={40} />
              <TableCell>Name</TableCell>
              <TableCell>Node</TableCell>
              <TableCell>Created By</TableCell>
              <TableCell>Status</TableCell>
              <TableCell>Permission</TableCell>
              <TableCell>Expires</TableCell>
              <TableCell>Access</TableCell>
              <TableCell>Protection</TableCell>
              <TableCell align="right" width={160}>Actions</TableCell>
            </TableRow>
          </TableHead>
          <TableBody>
            {filtered.map((link) => {
              const st = statusOf(link);
              return (
                <React.Fragment key={link.token}>
                  <TableRow hover>
                    <TableCell>
                      <IconButton size="small" onClick={() => void toggleDrill(link.token)}>
                        {expandedToken === link.token ? <ExpandLess fontSize="small" /> : <ExpandMore fontSize="small" />}
                      </IconButton>
                    </TableCell>
                    <TableCell>{link.name || link.token.slice(0, 8) + '...'}</TableCell>
                    <TableCell>
                      <Box display="flex" alignItems="center" gap={0.5}>
                        <Typography variant="body2" noWrap sx={{ maxWidth: 150 }}>{link.nodeName || link.nodeId}</Typography>
                        <Tooltip title="Open node in file browser">
                          <IconButton size="small" onClick={() => navigateToNode(link.nodeId)}><OpenInNew sx={{ fontSize: 14 }} /></IconButton>
                        </Tooltip>
                      </Box>
                    </TableCell>
                    <TableCell>{link.createdBy}</TableCell>
                    <TableCell><Chip label={st} size="small" color={statusChipColor(st)} /></TableCell>
                    <TableCell>{link.permissionLevel}</TableCell>
                    <TableCell>{link.expiryDate ? new Date(link.expiryDate).toLocaleString() : 'Never'}</TableCell>
                    <TableCell>{link.accessCount}/{link.maxAccessCount ?? '\u221e'}</TableCell>
                    <TableCell>
                      {link.passwordProtected && <Chip label="PWD" size="small" sx={{ mr: 0.5 }} variant="outlined" />}
                      {link.hasIpRestrictions && <Chip label="IP" size="small" variant="outlined" />}
                      {!link.passwordProtected && !link.hasIpRestrictions && '-'}
                    </TableCell>
                    <TableCell align="right">
                      {link.active ? (
                        <Tooltip title="Deactivate"><IconButton size="small" onClick={() => void handleDeactivate(link.token)}><Block fontSize="small" /></IconButton></Tooltip>
                      ) : (
                        <Tooltip title="Reactivate"><IconButton size="small" color="success" onClick={() => void handleReactivate(link.token)}><PlayArrow fontSize="small" /></IconButton></Tooltip>
                      )}
                      <Tooltip title="Delete"><IconButton size="small" color="error" onClick={() => void handleDelete(link.token)}><DeleteIcon fontSize="small" /></IconButton></Tooltip>
                    </TableCell>
                  </TableRow>

                  {/* drill-down row */}
                  <TableRow>
                    <TableCell colSpan={10} sx={{ p: 0, border: 0 }}>
                      <Collapse in={expandedToken === link.token} timeout="auto" unmountOnExit>
                        <Box sx={{ p: 2, bgcolor: 'action.hover' }}>
                          {drillLoading && !statsCache[link.token] ? <CircularProgress size={20} /> : (
                            <>
                              {statsCache[link.token] && (
                                <Box display="flex" gap={4} mb={2}>
                                  <Box><Typography variant="caption" color="text.secondary">Total</Typography><Typography variant="h6">{statsCache[link.token].totalAccesses}</Typography></Box>
                                  <Box><Typography variant="caption" color="text.secondary">Successful</Typography><Typography variant="h6" color="success.main">{statsCache[link.token].successfulAccesses}</Typography></Box>
                                  <Box><Typography variant="caption" color="text.secondary">Failed</Typography><Typography variant="h6" color="error.main">{statsCache[link.token].failedAccesses}</Typography></Box>
                                </Box>
                              )}
                              {logCache[link.token] && logCache[link.token].length > 0 ? (
                                <Table size="small">
                                  <TableHead><TableRow>
                                    <TableCell>Time</TableCell><TableCell>IP</TableCell><TableCell>Result</TableCell><TableCell>Reason</TableCell>
                                  </TableRow></TableHead>
                                  <TableBody>
                                    {logCache[link.token].slice(0, 15).map((e) => (
                                      <TableRow key={e.id}>
                                        <TableCell>{new Date(e.accessedAt).toLocaleString()}</TableCell>
                                        <TableCell>{e.clientIp || '-'}</TableCell>
                                        <TableCell>{e.success ? <Chip label="OK" size="small" color="success" /> : <Chip label="Fail" size="small" color="error" />}</TableCell>
                                        <TableCell>{e.failureReason || '-'}</TableCell>
                                      </TableRow>
                                    ))}
                                  </TableBody>
                                </Table>
                              ) : (
                                <Typography variant="body2" color="text.secondary">No access attempts recorded</Typography>
                              )}
                            </>
                          )}
                        </Box>
                      </Collapse>
                    </TableCell>
                  </TableRow>
                </React.Fragment>
              );
            })}
            {filtered.length === 0 && (
              <TableRow><TableCell colSpan={10} align="center">{links.length === 0 ? 'No share links in the system' : 'No links match the current filters'}</TableCell></TableRow>
            )}
          </TableBody>
        </Table>
      </TableContainer>
    </>
  );
};

interface ShareLinkAdm {
  id: string; token: string; nodeId: string; nodeName: string;
  createdBy: string; createdAt: string; expiryDate?: string;
  maxAccessCount?: number; accessCount: number; active: boolean;
  name?: string; permissionLevel: string; lastAccessedAt?: string;
  passwordProtected: boolean; hasIpRestrictions: boolean; isValid: boolean;
}

const SummaryCard: React.FC<{ title: string; value: string | number; icon: React.ReactNode }> = ({
  title,
  value,
  icon,
}) => (
  <Card sx={{ height: '100%' }}>
    <CardContent>
      <Box display="flex" alignItems="center" justifyContent="space-between">
        <Box>
          <Typography color="textSecondary" gutterBottom variant="subtitle2">
            {title}
          </Typography>
          <Typography variant="h4" component="div">
            {value}
          </Typography>
        </Box>
        {icon}
      </Box>
    </CardContent>
  </Card>
);

export default AdminDashboard;
