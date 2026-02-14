import React, { useEffect, useRef, useState } from 'react';
import {
  Box,
  Container,
  Grid,
  Paper,
  Typography,
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
} from '@mui/icons-material';
import { format } from 'date-fns';
import apiService from '../services/api';
import { toast } from 'react-toastify';
import userGroupService, { Group } from 'services/userGroupService';
import savedSearchService, { SavedSearch } from 'services/savedSearchService';
import mailAutomationService, { MailFetchSummaryStatus } from 'services/mailAutomationService';
import authService from 'services/authService';
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
      const [dashboardRes, logsRes, licenseRes, retentionRes, auditReportRes, ruleSummaryRes, ruleEventsRes, presetsRes, categoriesRes, eventTypesRes] = await Promise.all([
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
      setAuditUserSuggestions((dashboardRes?.topUsers || []).map((user) => user.username));
    } catch {
      toast.error('Failed to load dashboard data');
    } finally {
      setLoadingDashboard(false);
      setAuditCategoriesLoading(false);
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
      let downloadLabel = format(new Date(), 'yyyyMMdd');
      const nodeId = auditFilterNodeId.trim();
      const eventType = normalizeAuditEventType(auditFilterEventType);
      if (nodeId && !UUID_PATTERN.test(nodeId)) {
        toast.error('Invalid Node ID (expected UUID)');
        return;
      }

      const params = new URLSearchParams();
      if (auditExportPreset && auditExportPreset !== 'custom') {
        params.append('preset', auditExportPreset);
        const presetDays = Math.min(maxExportRangeDays || 30, 30);
        if (auditExportPreset === 'user' && auditFilterUser.trim()) {
          params.append('username', auditFilterUser.trim());
          params.append('days', String(presetDays));
        }
        if (auditExportPreset === 'event' && eventType) {
          params.append('eventType', eventType);
          params.append('days', String(presetDays));
        }
        if (auditFilterCategory.trim()) {
          params.append('category', auditFilterCategory.trim());
        }
        if (nodeId) {
          params.append('nodeId', nodeId);
        }
      } else {
        const { fromInput, toInput } = resolveAuditExportRange();
        const rangeError = getAuditExportRangeError(fromInput, toInput);
        if (rangeError) {
          toast.error(rangeError);
          return;
        }
        downloadLabel = `${format(fromInput, 'yyyyMMdd')}_to_${format(toInput, 'yyyyMMdd')}`;
        params.append('from', formatDateTimeOffset(fromInput));
        params.append('to', formatDateTimeOffset(toInput));
        if (auditFilterUser.trim()) {
          params.append('username', auditFilterUser.trim());
        }
        if (eventType) {
          params.append('eventType', eventType);
        }
        if (auditFilterCategory.trim()) {
          params.append('category', auditFilterCategory.trim());
        }
        if (nodeId) {
          params.append('nodeId', nodeId);
        }
      }

      const apiBaseUrl = process.env.REACT_APP_API_URL
        || process.env.REACT_APP_API_BASE_URL
        || '/api/v1';
      const refreshedToken = await authService.refreshToken().catch(() => undefined);
      const accessToken = refreshedToken
        || authService.getToken()
        || localStorage.getItem('token')
        || localStorage.getItem('access_token')
        || '';

      const response = await fetch(
        `${apiBaseUrl}/analytics/audit/export?${params.toString()}`,
        {
          headers: {
            Authorization: accessToken ? `Bearer ${accessToken}` : '',
          },
        }
      );

      if (!response.ok) {
        let message = 'Failed to export audit logs';
        try {
          const text = await response.text();
          if (text) {
            try {
              const payload = JSON.parse(text) as { message?: string; error?: string };
              message = payload.message || payload.error || text;
            } catch {
              message = text;
            }
          }
        } catch {
          // Keep default message.
        }
        throw new Error(message);
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
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

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
    </Container>
  );
};

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
