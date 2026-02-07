import React, { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import {
  Box,
  Button,
  ButtonGroup,
  Card,
  CardContent,
  Chip,
  Checkbox,
  CircularProgress,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  Drawer,
  FormControl,
  FormControlLabel,
  FormHelperText,
  InputLabel,
  MenuItem,
  Select,
  Stack,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  TextField,
  Typography,
  IconButton,
  Tooltip,
} from '@mui/material';
import {
  Add,
  Delete,
  Edit,
  Link,
  Login,
  Refresh,
  Search,
  Visibility,
  ContentCopy,
  ArrowUpward,
  ArrowDownward,
} from '@mui/icons-material';
import { toast } from 'react-toastify';
import { useLocation, useNavigate } from 'react-router-dom';
import mailAutomationService, {
  MailAccount,
  MailAccountRequest,
  MailConnectionTestResult,
  MailDiagnosticsResult,
  MailFetchDebugResult,
  MailFetchSummary,
  MailFetchSummaryStatus,
  MailRule,
  MailRuleRequest,
  MailActionType,
  MailSecurityType,
  MailOAuthProvider,
  MailPostAction,
  MailDocumentDiagnosticItem,
  ProcessedMailDiagnosticItem,
  ProcessedMailRetentionStatus,
  MailRulePreviewResult,
  MailReportResponse,
  MailReportAccountRow,
  MailReportRuleRow,
  MailReportTrendRow,
  MailDiagnosticsSortField,
  MailDiagnosticsSortOrder,
  MailRuntimeMetrics,
} from 'services/mailAutomationService';
import tagService from 'services/tagService';
import nodeService from 'services/nodeService';

interface TagOption {
  id: string;
  name: string;
}

const normalizeCredentialKey = (value: string) =>
  value.trim().toUpperCase().replace(/[^A-Z0-9]+/g, '_');

const formatMissingOAuthKeys = (keys?: string[]) => {
  if (!keys || keys.length === 0) {
    return '';
  }
  if (keys.includes('oauthCredentialKey')) {
    return 'Missing oauthCredentialKey';
  }
  return keys.join(', ');
};

const DEFAULT_ACCOUNT_FORM: MailAccountRequest = {
  name: '',
  host: '',
  port: 993,
  username: '',
  password: '',
  security: 'SSL',
  enabled: true,
  pollIntervalMinutes: 10,
  oauthProvider: 'CUSTOM',
  oauthTokenEndpoint: '',
  oauthTenantId: '',
  oauthScope: '',
  oauthCredentialKey: '',
};

const PASSWORD_MASK = '********';

const DEFAULT_RULE_FORM: MailRuleRequest & { folderPath: string; folderIdOverride: string } = {
  name: '',
  accountId: '',
  priority: 100,
  enabled: true,
  folder: 'INBOX',
  subjectFilter: '',
  fromFilter: '',
  toFilter: '',
  bodyFilter: '',
  attachmentFilenameInclude: '',
  attachmentFilenameExclude: '',
  maxAgeDays: 0,
  includeInlineAttachments: false,
  actionType: 'ATTACHMENTS_ONLY',
  mailAction: 'MARK_READ',
  mailActionParam: '',
  assignTagId: '',
  assignFolderId: '',
  folderPath: '',
  folderIdOverride: '',
};
const DIAGNOSTICS_FILTERS_STORAGE_KEY = 'mailDiagnosticsFilters';
const DIAGNOSTICS_QUERY_PARAMS = {
  accountId: 'dAccount',
  ruleId: 'dRule',
  status: 'dStatus',
  subject: 'dSubject',
  errorContains: 'dError',
  processedFrom: 'dFrom',
  processedTo: 'dTo',
  sort: 'dSort',
  order: 'dOrder',
} as const;
const DIAGNOSTICS_SORT_FIELDS: MailDiagnosticsSortField[] = ['processedAt', 'status', 'rule', 'account'];
const DIAGNOSTICS_SORT_ORDERS: MailDiagnosticsSortOrder[] = ['desc', 'asc'];
type RuleDiagnosticsTimeRange = 'ALL' | '24H' | '7D' | '30D';
const RULE_DIAGNOSTICS_TIME_RANGES: Array<{ value: RuleDiagnosticsTimeRange; label: string }> = [
  { value: 'ALL', label: 'All time' },
  { value: '24H', label: 'Last 24 hours' },
  { value: '7D', label: 'Last 7 days' },
  { value: '30D', label: 'Last 30 days' },
];

const MailAutomationPage: React.FC = () => {
  const location = useLocation();
  const navigate = useNavigate();
  const [accounts, setAccounts] = useState<MailAccount[]>([]);
  const [rules, setRules] = useState<MailRule[]>([]);
  const [tags, setTags] = useState<TagOption[]>([]);
  const [loading, setLoading] = useState(false);
  const [fetching, setFetching] = useState(false);
  const [refreshing, setRefreshing] = useState(false);
  const [debugging, setDebugging] = useState(false);
  const [debugResult, setDebugResult] = useState<MailFetchDebugResult | null>(null);
  const [debugMaxMessages, setDebugMaxMessages] = useState(200);
  const [folderAccountId, setFolderAccountId] = useState('');
  const [summaryAccountId, setSummaryAccountId] = useState('');
  const [report, setReport] = useState<MailReportResponse | null>(null);
  const [reportLoading, setReportLoading] = useState(false);
  const [reportAccountId, setReportAccountId] = useState('');
  const [reportRuleId, setReportRuleId] = useState('');
  const [reportDays, setReportDays] = useState(7);
  const [runtimeMetrics, setRuntimeMetrics] = useState<MailRuntimeMetrics | null>(null);
  const [runtimeMetricsLoading, setRuntimeMetricsLoading] = useState(false);
  const [runtimeWindowMinutes, setRuntimeWindowMinutes] = useState(60);
  const [listingFolders, setListingFolders] = useState(false);
  const [availableFolders, setAvailableFolders] = useState<string[]>([]);
  const [hasListedFolders, setHasListedFolders] = useState(false);
  const [diagnostics, setDiagnostics] = useState<MailDiagnosticsResult | null>(null);
  const [diagnosticsLoading, setDiagnosticsLoading] = useState(false);
  const [diagnosticsAccountId, setDiagnosticsAccountId] = useState('');
  const [diagnosticsRuleId, setDiagnosticsRuleId] = useState('');
  const [diagnosticsStatus, setDiagnosticsStatus] = useState('');
  const [diagnosticsSubject, setDiagnosticsSubject] = useState('');
  const [diagnosticsErrorContains, setDiagnosticsErrorContains] = useState('');
  const [diagnosticsProcessedFrom, setDiagnosticsProcessedFrom] = useState('');
  const [diagnosticsProcessedTo, setDiagnosticsProcessedTo] = useState('');
  const [diagnosticsSort, setDiagnosticsSort] = useState<MailDiagnosticsSortField>('processedAt');
  const [diagnosticsOrder, setDiagnosticsOrder] = useState<MailDiagnosticsSortOrder>('desc');
  const [diagnosticsFiltersLoaded, setDiagnosticsFiltersLoaded] = useState(false);
  const diagnosticsFiltersActive = Boolean(
    diagnosticsAccountId
      || diagnosticsRuleId
      || diagnosticsStatus
      || diagnosticsSubject
      || diagnosticsErrorContains
      || diagnosticsProcessedFrom
      || diagnosticsProcessedTo
      || diagnosticsSort !== 'processedAt'
      || diagnosticsOrder !== 'desc',
  );
  const [processedRetention, setProcessedRetention] = useState<ProcessedMailRetentionStatus | null>(null);
  const [retentionLoading, setRetentionLoading] = useState(false);
  const [retentionCleaning, setRetentionCleaning] = useState(false);
  const [selectedProcessedIds, setSelectedProcessedIds] = useState<string[]>([]);
  const [replayingProcessedId, setReplayingProcessedId] = useState<string | null>(null);
  const [expandedRuleErrorRuleId, setExpandedRuleErrorRuleId] = useState('');
  const [ruleDiagnosticsDrawerOpen, setRuleDiagnosticsDrawerOpen] = useState(false);
  const [ruleDiagnosticsFocusRuleId, setRuleDiagnosticsFocusRuleId] = useState('');
  const [ruleDiagnosticsAccountId, setRuleDiagnosticsAccountId] = useState('');
  const [ruleDiagnosticsStatus, setRuleDiagnosticsStatus] = useState<'ALL' | 'ERROR' | 'PROCESSED'>('ALL');
  const [ruleDiagnosticsTimeRange, setRuleDiagnosticsTimeRange] = useState<RuleDiagnosticsTimeRange>('7D');
  const [connectingAccountId, setConnectingAccountId] = useState<string | null>(null);
  const [lastFetchSummary, setLastFetchSummary] = useState<MailFetchSummary | null>(null);
  const [lastFetchAt, setLastFetchAt] = useState<string | null>(null);
  const diagnosticsRef = useRef<HTMLDivElement | null>(null);
  const initialDiagnosticsSearchRef = useRef(location.search);
  const [exportOptions, setExportOptions] = useState(() => {
    const fallback = {
      includeProcessed: true,
      includeDocuments: true,
      includeSubject: true,
      includeError: true,
      includePath: true,
      includeMimeType: true,
      includeFileSize: true,
    };
    try {
      const raw = window.localStorage.getItem('mailDiagnosticsExportOptions');
      if (!raw) {
        return fallback;
      }
      const parsed = JSON.parse(raw) as Partial<typeof fallback>;
      return {
        ...fallback,
        ...parsed,
      };
    } catch {
      return fallback;
    }
  });
  const [testingAccountId, setTestingAccountId] = useState<string | null>(null);

  const [accountDialogOpen, setAccountDialogOpen] = useState(false);
  const [accountForm, setAccountForm] = useState<MailAccountRequest>(DEFAULT_ACCOUNT_FORM);
  const [editingAccount, setEditingAccount] = useState<MailAccount | null>(null);

  const [ruleDialogOpen, setRuleDialogOpen] = useState(false);
  const [ruleForm, setRuleForm] = useState(DEFAULT_RULE_FORM);
  const [editingRule, setEditingRule] = useState<MailRule | null>(null);
  const [ruleFilterText, setRuleFilterText] = useState('');
  const [ruleFilterAccountId, setRuleFilterAccountId] = useState('');
  const [ruleFilterStatus, setRuleFilterStatus] = useState<'ALL' | 'ENABLED' | 'DISABLED'>('ALL');
  const [selectedRuleIds, setSelectedRuleIds] = useState<string[]>([]);
  const [ruleBulkUpdating, setRuleBulkUpdating] = useState(false);
  const [ruleBulkDeleting, setRuleBulkDeleting] = useState(false);
  const [ruleBulkReindexing, setRuleBulkReindexing] = useState(false);

  const [previewDialogOpen, setPreviewDialogOpen] = useState(false);
  const [previewRule, setPreviewRule] = useState<MailRule | null>(null);
  const [previewAccountId, setPreviewAccountId] = useState('');
  const [previewMaxMessages, setPreviewMaxMessages] = useState(25);
  const [previewLoading, setPreviewLoading] = useState(false);
  const [previewResult, setPreviewResult] = useState<MailRulePreviewResult | null>(null);
  const [previewError, setPreviewError] = useState<string | null>(null);
  const [previewProcessableFilter, setPreviewProcessableFilter] = useState<'ALL' | 'PROCESSABLE' | 'UNPROCESSABLE'>('ALL');

  const securityOptions: MailSecurityType[] = ['SSL', 'STARTTLS', 'NONE', 'OAUTH2'];
  const oauthProviderOptions: MailOAuthProvider[] = ['MICROSOFT', 'GOOGLE', 'CUSTOM'];
  const actionOptions: MailActionType[] = ['ATTACHMENTS_ONLY', 'METADATA_ONLY', 'EVERYTHING'];
  const actionTypeLabels: Record<MailActionType, string> = {
    ATTACHMENTS_ONLY: 'Attachments only',
    METADATA_ONLY: 'Email (.eml) only',
    EVERYTHING: 'Email (.eml) + attachments',
  };
  const actionTypeDescriptions: Record<MailActionType, string> = {
    ATTACHMENTS_ONLY: 'Store only attachments that match the rule.',
    METADATA_ONLY: 'Store the full email as a single .eml document.',
    EVERYTHING: 'Store the full .eml and each attachment as separate documents.',
  };
  const postActionOptions: MailPostAction[] = ['MARK_READ', 'MOVE', 'DELETE', 'FLAG', 'TAG', 'NONE'];
  const diagnosticsLimit = 25;
  const isOauthAccount = accountForm.security === 'OAUTH2';
  const normalizedCredentialKey = accountForm.oauthCredentialKey
    ? normalizeCredentialKey(accountForm.oauthCredentialKey)
    : '';
  const hasCredentialKey = Boolean(normalizedCredentialKey);
  const providerEnvPrefix = accountForm.oauthProvider === 'GOOGLE'
    ? 'ECM_MAIL_OAUTH_GOOGLE_'
    : accountForm.oauthProvider === 'MICROSOFT'
      ? 'ECM_MAIL_OAUTH_MICROSOFT_'
      : '';
  const usesProviderEnv = isOauthAccount && !hasCredentialKey && accountForm.oauthProvider !== 'CUSTOM';
  const oauthEnvPrefix = normalizedCredentialKey
    ? `ECM_MAIL_OAUTH_${normalizedCredentialKey}_`
    : 'ECM_MAIL_OAUTH_<KEY>_';

  const formatDateTime = (value?: string | null) => {
    if (!value) {
      return 'N/A';
    }
    const date = new Date(value);
    if (Number.isNaN(date.getTime())) {
      return value;
    }
    return date.toLocaleString();
  };

  const formatDate = (value?: string | null) => {
    if (!value) {
      return 'N/A';
    }
    if (/^\\d{4}-\\d{2}-\\d{2}$/.test(value)) {
      return value;
    }
    const date = new Date(value);
    if (Number.isNaN(date.getTime())) {
      return value;
    }
    return date.toLocaleDateString();
  };

  const formatCount = (value?: number | null) => new Intl.NumberFormat().format(value ?? 0);

  const summarizeError = (value?: string | null, maxLength = 160) => {
    if (!value) {
      return '';
    }
    if (value.length <= maxLength) {
      return value;
    }
    return `${value.slice(0, Math.max(0, maxLength - 3))}...`;
  };

  const summarizeText = (value?: string | null, maxLength = 120) => {
    if (!value) {
      return '';
    }
    if (value.length <= maxLength) {
      return value;
    }
    return `${value.slice(0, Math.max(0, maxLength - 3))}...`;
  };

  const extractSkipReason = (errorMessage?: string | null) => {
    const raw = (errorMessage || '').trim();
    if (!raw) {
      return 'unknown';
    }
    const firstLine = raw.split(/\r?\n/, 1)[0] || raw;
    const token = firstLine.split(':', 1)[0]?.trim() || firstLine;
    return token.toLowerCase().replace(/\s+/g, '_');
  };

  const timeRangeStart = (range: RuleDiagnosticsTimeRange): Date | null => {
    const now = Date.now();
    if (range === '24H') {
      return new Date(now - 24 * 60 * 60 * 1000);
    }
    if (range === '7D') {
      return new Date(now - 7 * 24 * 60 * 60 * 1000);
    }
    if (range === '30D') {
      return new Date(now - 30 * 24 * 60 * 60 * 1000);
    }
    return null;
  };

  const statusColor = (status?: string | null): 'default' | 'success' | 'error' => {
    if (status === 'ERROR') {
      return 'error';
    }
    if (status === 'PROCESSED') {
      return 'success';
    }
    return 'default';
  };

  const runtimeStatusColor = (status?: string | null): 'default' | 'success' | 'warning' | 'error' => {
    if (status === 'HEALTHY') {
      return 'success';
    }
    if (status === 'DEGRADED') {
      return 'warning';
    }
    if (status === 'DOWN') {
      return 'error';
    }
    return 'default';
  };
  const runtimeTrendColor = (direction?: string | null): 'default' | 'success' | 'warning' => {
    if (direction === 'IMPROVING') {
      return 'success';
    }
    if (direction === 'WORSENING') {
      return 'warning';
    }
    return 'default';
  };

  const toSortedEntries = (map?: Record<string, number> | null) =>
    Object.entries(map || {}).sort((a, b) => b[1] - a[1]);

  const formatReason = (reason: string) => reason.replace(/_/g, ' ');
  const toDateTimeInputValue = (value: Date) => {
    const local = new Date(value.getTime() - value.getTimezoneOffset() * 60000);
    return local.toISOString().slice(0, 16);
  };
  const processedKey = (accountId?: string | null, uid?: string | null) => `${accountId || ''}::${uid || ''}`;

  const loadRetention = useCallback(async (options?: { silent?: boolean }) => {
    const silent = options?.silent === true;
    if (!silent) {
      setRetentionLoading(true);
    }
    try {
      const status = await mailAutomationService.getProcessedRetention();
      setProcessedRetention(status);
    } catch {
      if (!silent) {
        toast.error('Failed to load processed mail retention status');
      }
    } finally {
      setRetentionLoading(false);
    }
  }, []);

  const loadAll = useCallback(async (options?: { silent?: boolean }) => {
    const silent = options?.silent === true;
    if (!silent) {
      setLoading(true);
    }
    let ok = false;
    try {
      const [accountList, ruleList, tagList, fetchStatus] = await Promise.all([
        mailAutomationService.listAccounts(),
        mailAutomationService.listRules(),
        tagService.getAllTags(),
        mailAutomationService.getFetchSummary().catch(() => null),
      ]);
      setAccounts(accountList);
      setRules(ruleList);
      setTags(tagList.map((tag) => ({ id: tag.id, name: tag.name })));
      if (fetchStatus) {
        const typedStatus = fetchStatus as MailFetchSummaryStatus;
        setLastFetchSummary(typedStatus.summary ?? null);
        if (typedStatus.fetchedAt) {
          setLastFetchAt(typedStatus.fetchedAt);
        }
      }
      ok = true;
      loadRetention({ silent: true });
    } catch {
      toast.error('Failed to load mail automation data');
    } finally {
      if (!silent) {
        setLoading(false);
      }
    }
    return ok;
  }, [loadRetention]);

  const loadDiagnostics = useCallback(async (options?: { silent?: boolean }) => {
    const silent = options?.silent === true;
    if (!silent) {
      setDiagnosticsLoading(true);
    }
    try {
      const result = await mailAutomationService.getDiagnostics(diagnosticsLimit, {
        accountId: diagnosticsAccountId || undefined,
        ruleId: diagnosticsRuleId || undefined,
        status: diagnosticsStatus || undefined,
        subject: diagnosticsSubject || undefined,
        errorContains: diagnosticsErrorContains || undefined,
        processedFrom: diagnosticsProcessedFrom || undefined,
        processedTo: diagnosticsProcessedTo || undefined,
        sort: diagnosticsSort,
        order: diagnosticsOrder,
      });
      setDiagnostics(result);
      setSelectedProcessedIds([]);
    } catch {
      if (!silent) {
        toast.error('Failed to load mail diagnostics');
      }
    } finally {
      setDiagnosticsLoading(false);
    }
  }, [
    diagnosticsAccountId,
    diagnosticsRuleId,
    diagnosticsStatus,
    diagnosticsSubject,
    diagnosticsErrorContains,
    diagnosticsProcessedFrom,
    diagnosticsProcessedTo,
    diagnosticsSort,
    diagnosticsOrder,
  ]);

  const loadReport = useCallback(async (options?: { silent?: boolean }) => {
    const silent = options?.silent === true;
    if (!silent) {
      setReportLoading(true);
    }
    try {
      const result = await mailAutomationService.getReport({
        accountId: reportAccountId || undefined,
        ruleId: reportRuleId || undefined,
        days: reportDays,
      });
      setReport(result);
    } catch {
      if (!silent) {
        toast.error('Failed to load mail reporting');
      }
    } finally {
      setReportLoading(false);
    }
  }, [reportAccountId, reportRuleId, reportDays]);

  const loadRuntimeMetrics = useCallback(async (options?: { silent?: boolean }) => {
    const silent = options?.silent === true;
    if (!silent) {
      setRuntimeMetricsLoading(true);
    }
    try {
      const result = await mailAutomationService.getRuntimeMetrics(runtimeWindowMinutes);
      setRuntimeMetrics(result);
    } catch (error: unknown) {
      if (!silent) {
        const status = (error as { response?: { status?: number } })?.response?.status;
        if (status === 403) {
          toast.error('Permission denied: runtime metrics require admin role');
        } else {
          toast.error('Failed to load mail runtime metrics');
        }
      }
    } finally {
      setRuntimeMetricsLoading(false);
    }
  }, [runtimeWindowMinutes]);

  useEffect(() => {
    loadAll();
  }, [loadAll]);

  useEffect(() => {
    loadReport();
  }, [loadReport]);

  useEffect(() => {
    loadRuntimeMetrics();
  }, [loadRuntimeMetrics]);

  useEffect(() => {
    const params = new URLSearchParams(location.search);
    const oauthSuccess = params.get('oauth_success');
    if (!oauthSuccess) {
      return;
    }
    if (oauthSuccess === '1') {
      toast.success('OAuth connected');
    } else {
      toast.error('OAuth connection failed');
    }
    navigate('/admin/mail', { replace: true });
    loadAll({ silent: true });
  }, [location.search, navigate, loadAll]);

  useEffect(() => {
    if (loading) {
      return;
    }
    if (location.hash === '#diagnostics') {
      const target = diagnosticsRef.current;
      if (target) {
        target.scrollIntoView({ behavior: 'smooth', block: 'start' });
      }
    }
  }, [location.hash, loading]);

  useEffect(() => {
    if (!diagnosticsFiltersLoaded) {
      return;
    }
    loadDiagnostics({ silent: true });
  }, [
    diagnosticsFiltersLoaded,
    diagnosticsAccountId,
    diagnosticsRuleId,
    diagnosticsStatus,
    diagnosticsSubject,
    diagnosticsErrorContains,
    diagnosticsProcessedFrom,
    diagnosticsProcessedTo,
    diagnosticsSort,
    diagnosticsOrder,
    loadDiagnostics,
  ]);

  useEffect(() => {
    if (!folderAccountId && accounts.length > 0) {
      setFolderAccountId(accounts[0].id);
    }
  }, [accounts, folderAccountId]);

  useEffect(() => {
    if (!summaryAccountId && accounts.length > 0) {
      setSummaryAccountId(accounts[0].id);
    }
  }, [accounts, summaryAccountId]);

  const handleClearDiagnosticsFilters = () => {
    setDiagnosticsAccountId('');
    setDiagnosticsRuleId('');
    setDiagnosticsStatus('');
    setDiagnosticsSubject('');
    setDiagnosticsErrorContains('');
    setDiagnosticsProcessedFrom('');
    setDiagnosticsProcessedTo('');
    setDiagnosticsSort('processedAt');
    setDiagnosticsOrder('desc');
  };

  useEffect(() => {
    setAvailableFolders([]);
    setHasListedFolders(false);
  }, [folderAccountId]);

  useEffect(() => {
    try {
      const params = new URLSearchParams(initialDiagnosticsSearchRef.current);
      const queryFilters = {
        accountId: params.get(DIAGNOSTICS_QUERY_PARAMS.accountId) || '',
        ruleId: params.get(DIAGNOSTICS_QUERY_PARAMS.ruleId) || '',
        status: params.get(DIAGNOSTICS_QUERY_PARAMS.status) || '',
        subject: params.get(DIAGNOSTICS_QUERY_PARAMS.subject) || '',
        errorContains: params.get(DIAGNOSTICS_QUERY_PARAMS.errorContains) || '',
        processedFrom: params.get(DIAGNOSTICS_QUERY_PARAMS.processedFrom) || '',
        processedTo: params.get(DIAGNOSTICS_QUERY_PARAMS.processedTo) || '',
        sort: params.get(DIAGNOSTICS_QUERY_PARAMS.sort) || '',
        order: params.get(DIAGNOSTICS_QUERY_PARAMS.order) || '',
      };
      const resolvedQuerySort = DIAGNOSTICS_SORT_FIELDS.includes(queryFilters.sort as MailDiagnosticsSortField)
        ? (queryFilters.sort as MailDiagnosticsSortField)
        : 'processedAt';
      const resolvedQueryOrder = DIAGNOSTICS_SORT_ORDERS.includes(queryFilters.order as MailDiagnosticsSortOrder)
        ? (queryFilters.order as MailDiagnosticsSortOrder)
        : 'desc';
      const hasQueryFilters = Boolean(
        queryFilters.accountId
          || queryFilters.ruleId
          || queryFilters.status
          || queryFilters.subject
          || queryFilters.errorContains
          || queryFilters.processedFrom
          || queryFilters.processedTo
          || queryFilters.sort
          || queryFilters.order
      );
      if (hasQueryFilters) {
        setDiagnosticsAccountId(queryFilters.accountId);
        setDiagnosticsRuleId(queryFilters.ruleId);
        setDiagnosticsStatus(queryFilters.status);
        setDiagnosticsSubject(queryFilters.subject);
        setDiagnosticsErrorContains(queryFilters.errorContains);
        setDiagnosticsProcessedFrom(queryFilters.processedFrom);
        setDiagnosticsProcessedTo(queryFilters.processedTo);
        setDiagnosticsSort(resolvedQuerySort);
        setDiagnosticsOrder(resolvedQueryOrder);
        setDiagnosticsFiltersLoaded(true);
        return;
      }

      const raw = window.localStorage.getItem(DIAGNOSTICS_FILTERS_STORAGE_KEY);
      if (!raw) {
        setDiagnosticsFiltersLoaded(true);
        return;
      }
      const parsed = JSON.parse(raw) as {
        accountId?: string;
        ruleId?: string;
        status?: string;
        subject?: string;
        errorContains?: string;
        processedFrom?: string;
        processedTo?: string;
        sort?: string;
        order?: string;
      };
      const resolvedStorageSort = DIAGNOSTICS_SORT_FIELDS.includes(parsed.sort as MailDiagnosticsSortField)
        ? (parsed.sort as MailDiagnosticsSortField)
        : 'processedAt';
      const resolvedStorageOrder = DIAGNOSTICS_SORT_ORDERS.includes(parsed.order as MailDiagnosticsSortOrder)
        ? (parsed.order as MailDiagnosticsSortOrder)
        : 'desc';
      setDiagnosticsAccountId(parsed.accountId || '');
      setDiagnosticsRuleId(parsed.ruleId || '');
      setDiagnosticsStatus(parsed.status || '');
      setDiagnosticsSubject(parsed.subject || '');
      setDiagnosticsErrorContains(parsed.errorContains || '');
      setDiagnosticsProcessedFrom(parsed.processedFrom || '');
      setDiagnosticsProcessedTo(parsed.processedTo || '');
      setDiagnosticsSort(resolvedStorageSort);
      setDiagnosticsOrder(resolvedStorageOrder);
    } catch {
      // Ignore invalid local storage payloads.
    } finally {
      setDiagnosticsFiltersLoaded(true);
    }
  }, []);

  useEffect(() => {
    if (!diagnosticsFiltersLoaded) {
      return;
    }
    const params = new URLSearchParams(location.search);
    const setOrDelete = (key: string, value: string) => {
      if (value) {
        params.set(key, value);
      } else {
        params.delete(key);
      }
    };
    setOrDelete(DIAGNOSTICS_QUERY_PARAMS.accountId, diagnosticsAccountId);
    setOrDelete(DIAGNOSTICS_QUERY_PARAMS.ruleId, diagnosticsRuleId);
    setOrDelete(DIAGNOSTICS_QUERY_PARAMS.status, diagnosticsStatus);
    setOrDelete(DIAGNOSTICS_QUERY_PARAMS.subject, diagnosticsSubject);
    setOrDelete(DIAGNOSTICS_QUERY_PARAMS.errorContains, diagnosticsErrorContains);
    setOrDelete(DIAGNOSTICS_QUERY_PARAMS.processedFrom, diagnosticsProcessedFrom);
    setOrDelete(DIAGNOSTICS_QUERY_PARAMS.processedTo, diagnosticsProcessedTo);
    setOrDelete(DIAGNOSTICS_QUERY_PARAMS.sort, diagnosticsSort !== 'processedAt' ? diagnosticsSort : '');
    setOrDelete(DIAGNOSTICS_QUERY_PARAMS.order, diagnosticsOrder !== 'desc' ? diagnosticsOrder : '');

    const currentSearch = location.search.startsWith('?') ? location.search.slice(1) : location.search;
    const nextSearch = params.toString();
    if (nextSearch === currentSearch) {
      return;
    }
    navigate(
      {
        pathname: location.pathname,
        search: nextSearch ? `?${nextSearch}` : '',
        hash: location.hash,
      },
      { replace: true }
    );
  }, [
    diagnosticsFiltersLoaded,
    diagnosticsAccountId,
    diagnosticsRuleId,
    diagnosticsStatus,
    diagnosticsSubject,
    diagnosticsErrorContains,
    diagnosticsProcessedFrom,
    diagnosticsProcessedTo,
    diagnosticsSort,
    diagnosticsOrder,
    location.pathname,
    location.search,
    location.hash,
    navigate,
  ]);

  useEffect(() => {
    if (!diagnosticsFiltersLoaded) {
      return;
    }
    try {
      window.localStorage.setItem(DIAGNOSTICS_FILTERS_STORAGE_KEY, JSON.stringify({
        accountId: diagnosticsAccountId,
        ruleId: diagnosticsRuleId,
        status: diagnosticsStatus,
        subject: diagnosticsSubject,
        errorContains: diagnosticsErrorContains,
        processedFrom: diagnosticsProcessedFrom,
        processedTo: diagnosticsProcessedTo,
        sort: diagnosticsSort,
        order: diagnosticsOrder,
      }));
    } catch {
      // Ignore storage failures (private mode, disabled storage).
    }
  }, [
    diagnosticsFiltersLoaded,
    diagnosticsAccountId,
    diagnosticsRuleId,
    diagnosticsStatus,
    diagnosticsSubject,
    diagnosticsErrorContains,
    diagnosticsProcessedFrom,
    diagnosticsProcessedTo,
    diagnosticsSort,
    diagnosticsOrder,
  ]);

  useEffect(() => {
    try {
      window.localStorage.setItem('mailDiagnosticsExportOptions', JSON.stringify(exportOptions));
    } catch {
      // Ignore storage failures (private mode, disabled storage).
    }
  }, [exportOptions]);

  const accountNameById = useMemo(() => {
    return new Map(accounts.map((account) => [account.id, account.name]));
  }, [accounts]);

  const tagNameById = useMemo(() => {
    return new Map(tags.map((tag) => [tag.id, tag.name]));
  }, [tags]);

  const summaryAccount = useMemo(() => {
    if (accounts.length === 0) {
      return null;
    }
    return accounts.find((account) => account.id === summaryAccountId) ?? accounts[0];
  }, [accounts, summaryAccountId]);

  const latestSuccessAt = useMemo(() => {
    let latest: string | null = null;
    accounts.forEach((account) => {
      if (account.lastFetchStatus !== 'SUCCESS' || !account.lastFetchAt) {
        return;
      }
      if (!latest) {
        latest = account.lastFetchAt;
        return;
      }
      const nextTime = new Date(account.lastFetchAt).getTime();
      const prevTime = new Date(latest).getTime();
      if (!Number.isNaN(nextTime) && !Number.isNaN(prevTime) && nextTime > prevTime) {
        latest = account.lastFetchAt;
      }
    });
    return latest;
  }, [accounts]);

  const accountHealth = useMemo(() => {
    const now = Date.now();
    const total = accounts.length;
    const enabled = accounts.filter((account) => account.enabled).length;
    const disabled = total - enabled;
    const oauthAccounts = accounts.filter((account) => account.security === 'OAUTH2');
    const oauthConnected = oauthAccounts.filter((account) => account.oauthConnected === true).length;
    const oauthNotConnected = oauthAccounts.filter((account) => account.oauthConnected === false).length;
    const oauthMissingEnv = oauthAccounts.filter((account) => account.oauthEnvConfigured === false).length;
    const fetchSuccess = accounts.filter((account) => account.lastFetchStatus === 'SUCCESS').length;
    const fetchError = accounts.filter((account) => account.lastFetchStatus === 'ERROR').length;
    const fetchOther = accounts.filter(
      (account) => account.lastFetchStatus && !['SUCCESS', 'ERROR'].includes(account.lastFetchStatus)
    ).length;
    const neverFetched = accounts.filter((account) => !account.lastFetchAt).length;
    const staleFetches = accounts.filter((account) => {
      if (!account.lastFetchAt) {
        return false;
      }
      const lastFetch = new Date(account.lastFetchAt).getTime();
      if (Number.isNaN(lastFetch)) {
        return false;
      }
      const intervalMs = (account.pollIntervalMinutes || 10) * 60 * 1000 * 2;
      return now - lastFetch > intervalMs;
    }).length;
    const latestFetchAt = accounts.reduce<string | null>((latest, account) => {
      if (!account.lastFetchAt) {
        return latest;
      }
      if (!latest) {
        return account.lastFetchAt;
      }
      const nextTime = new Date(account.lastFetchAt).getTime();
      const prevTime = new Date(latest).getTime();
      if (Number.isNaN(nextTime) || Number.isNaN(prevTime)) {
        return latest;
      }
      return nextTime > prevTime ? account.lastFetchAt : latest;
    }, null);

    return {
      total,
      enabled,
      disabled,
      oauthAccounts: oauthAccounts.length,
      oauthConnected,
      oauthNotConnected,
      oauthMissingEnv,
      fetchSuccess,
      fetchError,
      fetchOther,
      neverFetched,
      staleFetches,
      latestFetchAt,
    };
  }, [accounts]);

  const recentProcessed = useMemo<ProcessedMailDiagnosticItem[]>(
    () => diagnostics?.recentProcessed ?? [],
    [diagnostics]
  );
  const recentDocuments = useMemo<MailDocumentDiagnosticItem[]>(
    () => diagnostics?.recentDocuments ?? [],
    [diagnostics]
  );
  const recentDocumentByProcessedKey = useMemo(() => {
    const map = new Map<string, MailDocumentDiagnosticItem>();
    recentDocuments.forEach((doc) => {
      const key = processedKey(doc.accountId, doc.uid);
      if (!key.endsWith('::')) {
        map.set(key, doc);
      }
    });
    return map;
  }, [recentDocuments]);
  const reportAccounts: MailReportAccountRow[] = report?.accounts ?? [];
  const reportRules: MailReportRuleRow[] = report?.rules ?? [];
  const reportTrend: MailReportTrendRow[] = report?.trend ?? [];
  const diagnosticsRuleNameById = useMemo(() => {
    return new Map(rules.map((rule) => [rule.id, rule.name]));
  }, [rules]);
  const reportTotals = report?.totals;
  const maxTrendTotal = Math.max(1, ...reportTrend.map((row) => row.total));
  const exportDisabled = !exportOptions.includeProcessed && !exportOptions.includeDocuments;
  const diagnosticsExportScopeSummary = useMemo(() => {
    const segments: string[] = [];
    if (diagnosticsAccountId) {
      segments.push(`Account=${accountNameById.get(diagnosticsAccountId) || diagnosticsAccountId}`);
    }
    if (diagnosticsRuleId) {
      segments.push(`Rule=${diagnosticsRuleNameById.get(diagnosticsRuleId) || diagnosticsRuleId}`);
    }
    if (diagnosticsStatus) {
      segments.push(`Status=${diagnosticsStatus}`);
    }
    if (diagnosticsSubject) {
      segments.push(`Subject~${summarizeText(diagnosticsSubject, 30)}`);
    }
    if (diagnosticsErrorContains) {
      segments.push(`Error~${summarizeText(diagnosticsErrorContains, 30)}`);
    }
    if (diagnosticsProcessedFrom) {
      segments.push(`From=${diagnosticsProcessedFrom}`);
    }
    if (diagnosticsProcessedTo) {
      segments.push(`To=${diagnosticsProcessedTo}`);
    }
    segments.push(`Sort=${diagnosticsSort}:${diagnosticsOrder}`);
    return segments.join(' | ');
  }, [
    diagnosticsAccountId,
    diagnosticsRuleId,
    diagnosticsStatus,
    diagnosticsSubject,
    diagnosticsErrorContains,
    diagnosticsProcessedFrom,
    diagnosticsProcessedTo,
    diagnosticsSort,
    diagnosticsOrder,
    accountNameById,
    diagnosticsRuleNameById,
  ]);
  const retentionEnabled = processedRetention?.enabled ?? false;
  const retentionDays = processedRetention?.retentionDays ?? 0;
  const retentionExpiredCount = processedRetention?.expiredCount ?? 0;
  const allProcessedSelected = recentProcessed.length > 0
    && recentProcessed.every((item) => selectedProcessedIds.includes(item.id));
  const someProcessedSelected = selectedProcessedIds.length > 0 && !allProcessedSelected;
  const diagnosticsRuleErrorLeaders = useMemo(() => {
    const grouped = new Map<
      string,
      { ruleId: string; ruleName: string; errorCount: number; latestProcessedAt: string | null }
    >();
    recentProcessed.forEach((item) => {
      if (item.status !== 'ERROR' || !item.ruleId) {
        return;
      }
      const ruleId = item.ruleId;
      const ruleName = item.ruleName?.trim() || ruleId;
      const existing = grouped.get(ruleId);
      if (!existing) {
        grouped.set(ruleId, {
          ruleId,
          ruleName,
          errorCount: 1,
          latestProcessedAt: item.processedAt || null,
        });
        return;
      }
      existing.errorCount += 1;
      const existingTime = new Date(existing.latestProcessedAt || '').getTime();
      const nextTime = new Date(item.processedAt).getTime();
      if (!Number.isNaN(nextTime) && (Number.isNaN(existingTime) || nextTime > existingTime)) {
        existing.latestProcessedAt = item.processedAt;
      }
    });
    return Array.from(grouped.values())
      .sort((left, right) => {
        if (right.errorCount !== left.errorCount) {
          return right.errorCount - left.errorCount;
        }
        const leftTime = new Date(left.latestProcessedAt || '').getTime();
        const rightTime = new Date(right.latestProcessedAt || '').getTime();
        if (!Number.isNaN(leftTime) && !Number.isNaN(rightTime)) {
          return rightTime - leftTime;
        }
        return left.ruleName.localeCompare(right.ruleName);
      })
      .slice(0, 8);
  }, [recentProcessed]);
  const diagnosticsRuleErrorSamples = useMemo(() => {
    const grouped = new Map<string, ProcessedMailDiagnosticItem[]>();
    recentProcessed.forEach((item) => {
      if (item.status !== 'ERROR' || !item.ruleId) {
        return;
      }
      const samples = grouped.get(item.ruleId) || [];
      samples.push(item);
      grouped.set(item.ruleId, samples);
    });
    grouped.forEach((samples, ruleId) => {
      samples.sort((left, right) => {
        const leftTime = new Date(left.processedAt || '').getTime();
        const rightTime = new Date(right.processedAt || '').getTime();
        if (!Number.isNaN(leftTime) && !Number.isNaN(rightTime)) {
          return rightTime - leftTime;
        }
        return right.id.localeCompare(left.id);
      });
      grouped.set(ruleId, samples.slice(0, 3));
    });
    return grouped;
  }, [recentProcessed]);
  const ruleDiagnosticsFocusRule = useMemo(
    () => rules.find((rule) => rule.id === ruleDiagnosticsFocusRuleId) || null,
    [rules, ruleDiagnosticsFocusRuleId]
  );
  const ruleDiagnosticsItems = useMemo(() => {
    if (!ruleDiagnosticsFocusRuleId) {
      return [] as ProcessedMailDiagnosticItem[];
    }
    return recentProcessed.filter((item) => item.ruleId === ruleDiagnosticsFocusRuleId);
  }, [recentProcessed, ruleDiagnosticsFocusRuleId]);
  const ruleDiagnosticsFilteredItems = useMemo(() => {
    const rangeStart = timeRangeStart(ruleDiagnosticsTimeRange);
    return ruleDiagnosticsItems.filter((item) => {
      if (ruleDiagnosticsAccountId && item.accountId !== ruleDiagnosticsAccountId) {
        return false;
      }
      if (ruleDiagnosticsStatus !== 'ALL' && item.status !== ruleDiagnosticsStatus) {
        return false;
      }
      if (rangeStart) {
        const itemTime = new Date(item.processedAt).getTime();
        if (!Number.isNaN(itemTime) && itemTime < rangeStart.getTime()) {
          return false;
        }
      }
      return true;
    });
  }, [
    ruleDiagnosticsItems,
    ruleDiagnosticsAccountId,
    ruleDiagnosticsStatus,
    ruleDiagnosticsTimeRange,
  ]);
  const ruleDiagnosticsSkipReasons = useMemo(() => {
    const grouped = new Map<string, number>();
    ruleDiagnosticsFilteredItems.forEach((item) => {
      if (item.status !== 'ERROR') {
        return;
      }
      const reason = extractSkipReason(item.errorMessage);
      grouped.set(reason, (grouped.get(reason) || 0) + 1);
    });
    return Array.from(grouped.entries())
      .sort((left, right) => right[1] - left[1])
      .slice(0, 10);
  }, [ruleDiagnosticsFilteredItems]);
  const ruleDiagnosticsRecentFailures = useMemo(() => {
    return ruleDiagnosticsFilteredItems
      .filter((item) => item.status === 'ERROR')
      .sort((left, right) => {
        const leftTime = new Date(left.processedAt || '').getTime();
        const rightTime = new Date(right.processedAt || '').getTime();
        if (!Number.isNaN(leftTime) && !Number.isNaN(rightTime)) {
          return rightTime - leftTime;
        }
        return right.id.localeCompare(left.id);
      })
      .slice(0, 8);
  }, [ruleDiagnosticsFilteredItems]);
  const ruleDiagnosticsProcessedCount = useMemo(
    () => ruleDiagnosticsFilteredItems.filter((item) => item.status === 'PROCESSED').length,
    [ruleDiagnosticsFilteredItems]
  );
  const ruleDiagnosticsErrorCount = useMemo(
    () => ruleDiagnosticsFilteredItems.filter((item) => item.status === 'ERROR').length,
    [ruleDiagnosticsFilteredItems]
  );
  useEffect(() => {
    if (!expandedRuleErrorRuleId) {
      return;
    }
    const stillVisible = diagnosticsRuleErrorLeaders.some((item) => item.ruleId === expandedRuleErrorRuleId);
    if (!stillVisible) {
      setExpandedRuleErrorRuleId('');
    }
  }, [expandedRuleErrorRuleId, diagnosticsRuleErrorLeaders]);
  useEffect(() => {
    if (!ruleDiagnosticsDrawerOpen || !ruleDiagnosticsFocusRuleId) {
      return;
    }
    const stillVisible = diagnosticsRuleErrorLeaders.some((item) => item.ruleId === ruleDiagnosticsFocusRuleId);
    if (!stillVisible) {
      setRuleDiagnosticsDrawerOpen(false);
    }
  }, [ruleDiagnosticsDrawerOpen, ruleDiagnosticsFocusRuleId, diagnosticsRuleErrorLeaders]);

  const exportReportCsv = async () => {
    if (!report) {
      toast.info('Run a report first');
      return;
    }
    try {
      const blob = await mailAutomationService.exportReportCsv({
        accountId: reportAccountId || undefined,
        ruleId: reportRuleId || undefined,
        days: reportDays,
      });
      const url = URL.createObjectURL(blob);
      const anchor = document.createElement('a');
      const timestamp = new Date().toISOString().slice(0, 19).replace(/[:T]/g, '-');
      anchor.href = url;
      anchor.download = `mail-report-${timestamp}.csv`;
      anchor.click();
      URL.revokeObjectURL(url);
    } catch {
      toast.error('Failed to export mail report');
    }
  };

  const exportDiagnosticsCsv = async () => {
    if (exportDisabled) {
      toast.error('Select at least one export section');
      return;
    }
    try {
      const blob = await mailAutomationService.exportDiagnosticsCsv(diagnosticsLimit, {
        accountId: diagnosticsAccountId || undefined,
        ruleId: diagnosticsRuleId || undefined,
        status: diagnosticsStatus || undefined,
        subject: diagnosticsSubject || undefined,
        errorContains: diagnosticsErrorContains || undefined,
        processedFrom: diagnosticsProcessedFrom || undefined,
        processedTo: diagnosticsProcessedTo || undefined,
        sort: diagnosticsSort,
        order: diagnosticsOrder,
      }, exportOptions);
      const url = URL.createObjectURL(blob);
      const anchor = document.createElement('a');
      const timestamp = new Date().toISOString().replace(/[:.]/g, '-');
      anchor.href = url;
      anchor.download = `mail-diagnostics-${timestamp}.csv`;
      anchor.style.display = 'none';
      document.body.appendChild(anchor);
      anchor.click();
      document.body.removeChild(anchor);
      URL.revokeObjectURL(url);
    } catch {
      toast.error('Failed to export mail diagnostics');
    }
  };

  const toggleProcessedSelection = (id: string) => {
    setSelectedProcessedIds((prev) =>
      prev.includes(id) ? prev.filter((existing) => existing !== id) : [...prev, id]
    );
  };

  const toggleSelectAllProcessed = () => {
    if (recentProcessed.length === 0) {
      return;
    }
    const allSelected = recentProcessed.every((item) => selectedProcessedIds.includes(item.id));
    if (allSelected) {
      setSelectedProcessedIds([]);
    } else {
      setSelectedProcessedIds(recentProcessed.map((item) => item.id));
    }
  };

  const focusDiagnosticsSection = () => {
    const target = diagnosticsRef.current;
    if (target) {
      target.scrollIntoView({ behavior: 'smooth', block: 'start' });
    }
    navigate(
      {
        pathname: location.pathname,
        search: location.search,
        hash: 'diagnostics',
      },
      { replace: true }
    );
  };
  const applyDiagnosticsRuleErrorFilter = (ruleId: string) => {
    setDiagnosticsStatus('ERROR');
    setDiagnosticsRuleId(ruleId);
    setDiagnosticsErrorContains('');
  };
  const applyRuntimeDiagnosticsErrorFilter = (errorMessage?: string | null) => {
    const normalized = (errorMessage || '').trim();
    setDiagnosticsStatus('ERROR');
    setDiagnosticsRuleId('');
    setDiagnosticsSubject('');
    setDiagnosticsErrorContains(normalized);
    focusDiagnosticsSection();
  };
  const openRuleDiagnosticsDrawer = (ruleId: string) => {
    setRuleDiagnosticsFocusRuleId(ruleId);
    setRuleDiagnosticsAccountId('');
    setRuleDiagnosticsStatus('ALL');
    setRuleDiagnosticsTimeRange('7D');
    setRuleDiagnosticsDrawerOpen(true);
  };
  const closeRuleDiagnosticsDrawer = () => {
    setRuleDiagnosticsDrawerOpen(false);
  };
  const applyRuleDiagnosticsToTableFilters = () => {
    if (!ruleDiagnosticsFocusRuleId) {
      return;
    }
    setDiagnosticsRuleId(ruleDiagnosticsFocusRuleId);
    setDiagnosticsAccountId(ruleDiagnosticsAccountId);
    setDiagnosticsStatus(ruleDiagnosticsStatus === 'ALL' ? '' : ruleDiagnosticsStatus);
    setDiagnosticsErrorContains('');
    const rangeStart = timeRangeStart(ruleDiagnosticsTimeRange);
    setDiagnosticsProcessedFrom(rangeStart ? toDateTimeInputValue(rangeStart) : '');
    setDiagnosticsProcessedTo('');
    setRuleDiagnosticsDrawerOpen(false);
  };
  const toggleDiagnosticsRuleErrorDetails = (ruleId: string) => {
    setExpandedRuleErrorRuleId((prev) => (prev === ruleId ? '' : ruleId));
  };
  const copyDiagnosticsRuleErrorMessage = async (item: ProcessedMailDiagnosticItem) => {
    const payload = item.errorMessage?.trim() || '(empty error message)';
    try {
      await navigator.clipboard.writeText(payload);
      toast.success('Error message copied');
    } catch {
      toast.error('Failed to copy error message');
    }
  };
  const handleCopyDiagnosticsLink = async () => {
    try {
      const shareUrl = `${window.location.origin}${location.pathname}${location.search}#diagnostics`;
      await navigator.clipboard.writeText(shareUrl);
      toast.success('Diagnostics link copied');
    } catch {
      toast.error('Failed to copy diagnostics link');
    }
  };

  const applyDiagnosticsQuickWindow = (hours: number) => {
    const now = new Date();
    const from = new Date(now.getTime() - hours * 60 * 60 * 1000);
    setDiagnosticsProcessedFrom(toDateTimeInputValue(from));
    setDiagnosticsProcessedTo(toDateTimeInputValue(now));
  };

  const handleBulkDeleteProcessed = async () => {
    if (selectedProcessedIds.length === 0) {
      return;
    }
    if (!window.confirm(`Delete ${selectedProcessedIds.length} processed message record(s)?`)) {
      return;
    }
    try {
      const result = await mailAutomationService.bulkDeleteProcessedMail(selectedProcessedIds);
      toast.success(`Deleted ${result.deleted} processed record(s)`);
      setSelectedProcessedIds([]);
      await loadDiagnostics();
      await loadRetention({ silent: true });
    } catch {
      toast.error('Failed to delete processed records');
    }
  };

  const handleReplayProcessedMail = async (item: ProcessedMailDiagnosticItem) => {
    if (replayingProcessedId) {
      return;
    }
    setReplayingProcessedId(item.id);
    try {
      const result = await mailAutomationService.replayProcessedMail(item.id);
      if (result.processed) {
        toast.success('Replay processed successfully');
      } else if (result.attempted) {
        toast.info(result.message || 'Replay finished without processing content');
      } else {
        toast.warn(result.message || 'Replay skipped');
      }
      await loadDiagnostics({ silent: true });
      await loadRetention({ silent: true });
    } catch (error: unknown) {
      const status = (error as { response?: { status?: number } })?.response?.status;
      if (status === 403) {
        toast.error('Permission denied: replay requires admin role');
      } else {
        toast.error('Failed to replay processed mail');
      }
    } finally {
      setReplayingProcessedId(null);
    }
  };

  const handleRetentionCleanup = async () => {
    if (!retentionEnabled) {
      toast.info('Processed mail retention is disabled');
      return;
    }
    if (retentionExpiredCount === 0) {
      toast.info('No expired processed mail to clean up');
      return;
    }
    if (!window.confirm(`Delete ${retentionExpiredCount} expired processed record(s)?`)) {
      return;
    }
    setRetentionCleaning(true);
    try {
      const result = await mailAutomationService.cleanupProcessedRetention();
      toast.success(`Deleted ${result.deleted} expired processed record(s)`);
      await loadDiagnostics({ silent: true });
      await loadRetention({ silent: true });
    } catch {
      toast.error('Failed to clean up processed mail');
    } finally {
      setRetentionCleaning(false);
    }
  };

  const handleTriggerFetch = async () => {
    setFetching(true);
    try {
      const summary: MailFetchSummary = await mailAutomationService.triggerFetch();
      setLastFetchSummary(summary);
      setLastFetchAt(new Date().toISOString());
      const durationSeconds = (summary.durationMs / 1000).toFixed(1);
      const message =
        `Processed ${summary.processedMessages} of ${summary.matchedMessages} matched ` +
        `(${summary.foundMessages} unread, ${summary.skippedMessages} skipped) in ${durationSeconds}s`;
      if (summary.errorMessages > 0 || summary.accountErrors > 0) {
        toast.warn(`${message}. Errors: ${summary.errorMessages}, account errors: ${summary.accountErrors}`);
      } else {
        toast.success(message);
      }
      await loadDiagnostics({ silent: true });
    } catch {
      toast.error('Failed to trigger mail fetch');
    } finally {
      setFetching(false);
    }
  };

  const handleDebugFetch = async () => {
    setDebugging(true);
    try {
      const maxMessagesPerFolder =
        Number.isFinite(debugMaxMessages) && debugMaxMessages > 0
          ? Math.floor(debugMaxMessages)
          : undefined;
      const result = await mailAutomationService.triggerFetchDebug({
        force: true,
        maxMessagesPerFolder,
      });
      setDebugResult(result);
      const durationSeconds = (result.summary.durationMs / 1000).toFixed(1);
      toast.success(
        `Diagnostics complete: matched ${result.summary.matchedMessages}, processable ${result.summary.processedMessages} in ${durationSeconds}s`
      );
      await loadDiagnostics({ silent: true });
    } catch {
      toast.error('Failed to run mail diagnostics');
    } finally {
      setDebugging(false);
    }
  };

  const handleListFolders = async () => {
    if (!folderAccountId) {
      toast.error('Select an account first');
      return;
    }
    setListingFolders(true);
    try {
      const folders = await mailAutomationService.listFolders(folderAccountId);
      setAvailableFolders(folders);
      setHasListedFolders(true);
      toast.success(`Found ${folders.length} folders`);
    } catch {
      setHasListedFolders(false);
      toast.error('Failed to list folders');
    } finally {
      setListingFolders(false);
    }
  };

  const handleRefreshStatus = async () => {
    setRefreshing(true);
    try {
      const ok = await loadAll({ silent: true });
      if (ok) {
        await loadDiagnostics({ silent: true });
        await loadRuntimeMetrics({ silent: true });
        toast.success('Mail status refreshed');
      }
    } finally {
      setRefreshing(false);
    }
  };

  const handleTestConnection = async (accountId: string) => {
    setTestingAccountId(accountId);
    try {
      const result: MailConnectionTestResult = await mailAutomationService.testConnection(accountId);
      const durationSeconds = (result.durationMs / 1000).toFixed(1);
      if (result.success) {
        toast.success(`Connection OK in ${durationSeconds}s`);
      } else {
        toast.error(`Connection failed: ${result.message}`);
      }
    } catch {
      toast.error('Failed to test connection');
    } finally {
      setTestingAccountId((current) => (current === accountId ? null : current));
    }
  };

  const handleConnectOAuth = async (account: MailAccount) => {
    if (!account.id) {
      return;
    }
    try {
      setConnectingAccountId(account.id);
      const redirectUrl = `${window.location.origin}/admin/mail`;
      const result = await mailAutomationService.getOAuthAuthorizeUrl(account.id, redirectUrl);
      window.location.assign(result.url);
    } catch {
      toast.error('Failed to start OAuth connect');
      setConnectingAccountId(null);
    }
  };

  const handleOauthProviderChange = (provider: MailOAuthProvider) => {
    const next = { ...accountForm, oauthProvider: provider };
    if (accountForm.security === 'OAUTH2') {
      if (provider === 'GOOGLE') {
        next.host = 'imap.gmail.com';
        next.port = 993;
      } else if (provider === 'MICROSOFT') {
        next.host = 'outlook.office365.com';
        next.port = 993;
      }
    }
    setAccountForm(next);
  };

  const openCreateAccount = () => {
    setEditingAccount(null);
    setAccountForm(DEFAULT_ACCOUNT_FORM);
    setAccountDialogOpen(true);
  };

  const openEditAccount = (account: MailAccount) => {
    setEditingAccount(account);
    setAccountForm({
      name: account.name,
      host: account.host,
      port: account.port,
      username: account.username,
      password: account.passwordConfigured ? PASSWORD_MASK : '',
      security: account.security,
      enabled: account.enabled,
      pollIntervalMinutes: account.pollIntervalMinutes,
      oauthProvider: account.oauthProvider || 'CUSTOM',
      oauthTokenEndpoint: account.oauthTokenEndpoint || '',
      oauthTenantId: account.oauthTenantId || '',
      oauthScope: account.oauthScope || '',
      oauthCredentialKey: account.oauthCredentialKey || '',
    });
    setAccountDialogOpen(true);
  };

  const handleSaveAccount = async () => {
    try {
      if (!accountForm.name || !accountForm.host || !accountForm.username || !accountForm.port) {
        toast.warn('Name, host, port, and username are required');
        return;
      }

      if (!editingAccount && !isOauthAccount && !accountForm.password) {
        toast.warn('Password is required for new accounts');
        return;
      }

      if (isOauthAccount) {
        if (accountForm.oauthProvider === 'CUSTOM' && !accountForm.oauthTokenEndpoint) {
          toast.warn('OAuth token endpoint is required for custom providers');
          return;
        }
        if (accountForm.oauthProvider === 'CUSTOM' && !accountForm.oauthCredentialKey) {
          toast.warn('OAuth credential key is required for custom providers');
          return;
        }
      }

      const payload: MailAccountRequest = { ...accountForm };
      if (isOauthAccount) {
        payload.password = undefined;
      } else {
        payload.oauthProvider = undefined;
        payload.oauthTokenEndpoint = undefined;
        payload.oauthTenantId = undefined;
        payload.oauthScope = undefined;
        payload.oauthCredentialKey = undefined;
        const trimmedPassword = accountForm.password?.trim() ?? '';
        if (editingAccount && (!trimmedPassword || trimmedPassword === PASSWORD_MASK)) {
          payload.password = undefined;
        }
      }

      if (editingAccount) {
        await mailAutomationService.updateAccount(editingAccount.id, payload);
        toast.success('Mail account updated');
      } else {
        await mailAutomationService.createAccount(payload);
        toast.success('Mail account created');
      }

      setAccountDialogOpen(false);
      await loadAll();
    } catch {
      toast.error('Failed to save mail account');
    }
  };

  const handleDeleteAccount = async (accountId: string) => {
    if (!window.confirm('Delete this mail account?')) {
      return;
    }
    try {
      await mailAutomationService.deleteAccount(accountId);
      toast.success('Mail account deleted');
      await loadAll();
    } catch {
      toast.error('Failed to delete mail account');
    }
  };

  const openCreateRule = () => {
    setEditingRule(null);
    setRuleForm(DEFAULT_RULE_FORM);
    setRuleDialogOpen(true);
  };

  const openEditRule = (rule: MailRule) => {
    setEditingRule(rule);
    setRuleForm({
      name: rule.name,
      accountId: rule.accountId || '',
      priority: rule.priority,
      enabled: rule.enabled ?? true,
      folder: rule.folder || 'INBOX',
      subjectFilter: rule.subjectFilter || '',
      fromFilter: rule.fromFilter || '',
      toFilter: rule.toFilter || '',
      bodyFilter: rule.bodyFilter || '',
      attachmentFilenameInclude: rule.attachmentFilenameInclude || '',
      attachmentFilenameExclude: rule.attachmentFilenameExclude || '',
      maxAgeDays: rule.maxAgeDays || 0,
      includeInlineAttachments: rule.includeInlineAttachments || false,
      actionType: rule.actionType,
      mailAction: rule.mailAction || 'MARK_READ',
      mailActionParam: rule.mailActionParam || '',
      assignTagId: rule.assignTagId || '',
      assignFolderId: rule.assignFolderId || '',
      folderPath: '',
      folderIdOverride: rule.assignFolderId || '',
    });
    setRuleDialogOpen(true);
  };

  const openPreviewRule = (rule: MailRule) => {
    setPreviewRule(rule);
    setPreviewAccountId(rule.accountId || accounts[0]?.id || '');
    setPreviewMaxMessages(25);
    setPreviewResult(null);
    setPreviewError(null);
    setPreviewProcessableFilter('ALL');
    setPreviewDialogOpen(true);
  };

  const closePreviewDialog = () => {
    setPreviewDialogOpen(false);
    setPreviewRule(null);
    setPreviewResult(null);
    setPreviewError(null);
  };

  const handleRunRulePreview = async () => {
    if (!previewRule) {
      return;
    }
    const accountId = previewRule.accountId || previewAccountId;
    if (!accountId) {
      toast.warn('Select a mail account to preview this rule');
      return;
    }
    try {
      setPreviewLoading(true);
      const result = await mailAutomationService.previewRule(
        previewRule.id,
        accountId,
        previewMaxMessages > 0 ? previewMaxMessages : undefined,
      );
      setPreviewResult(result);
      setPreviewError(null);
    } catch {
      setPreviewResult(null);
      setPreviewError('Failed to preview mail rule');
    } finally {
      setPreviewLoading(false);
    }
  };

  const handleCopyPreviewJson = async () => {
    if (!previewResult) {
      return;
    }
    try {
      await navigator.clipboard.writeText(JSON.stringify(previewResult, null, 2));
      toast.success('Preview JSON copied');
    } catch {
      toast.error('Failed to copy preview JSON');
    }
  };

  const resolveFolderId = async () => {
    if (ruleForm.folderPath.trim()) {
      const folder = await nodeService.getFolderByPath(ruleForm.folderPath.trim());
      return folder.id;
    }
    return ruleForm.folderIdOverride || ruleForm.assignFolderId || '';
  };

  const handleSaveRule = async () => {
    try {
      if (!ruleForm.name) {
        toast.warn('Rule name is required');
        return;
      }

      let folderId = '';
      if (ruleForm.folderPath.trim() || ruleForm.folderIdOverride.trim()) {
        try {
          folderId = await resolveFolderId();
        } catch {
          toast.error('Failed to resolve folder path');
          return;
        }
      }

      const payload: MailRuleRequest = {
        name: ruleForm.name,
        accountId: ruleForm.accountId || null,
        priority: ruleForm.priority,
        enabled: ruleForm.enabled ?? true,
        folder: ruleForm.folder || 'INBOX',
        subjectFilter: ruleForm.subjectFilter || null,
        fromFilter: ruleForm.fromFilter || null,
        toFilter: ruleForm.toFilter || null,
        bodyFilter: ruleForm.bodyFilter || null,
        attachmentFilenameInclude: ruleForm.attachmentFilenameInclude || null,
        attachmentFilenameExclude: ruleForm.attachmentFilenameExclude || null,
        maxAgeDays: ruleForm.maxAgeDays && ruleForm.maxAgeDays > 0 ? ruleForm.maxAgeDays : null,
        includeInlineAttachments: ruleForm.includeInlineAttachments || false,
        actionType: ruleForm.actionType,
        mailAction: ruleForm.mailAction || 'MARK_READ',
        mailActionParam: ruleForm.mailActionParam || null,
        assignTagId: ruleForm.assignTagId || null,
        assignFolderId: folderId || null,
      };

      if (editingRule) {
        await mailAutomationService.updateRule(editingRule.id, payload);
        toast.success('Mail rule updated');
      } else {
        await mailAutomationService.createRule(payload);
        toast.success('Mail rule created');
      }

      setRuleDialogOpen(false);
      await loadAll();
    } catch {
      toast.error('Failed to save mail rule');
    }
  };

  const handleDeleteRule = async (ruleId: string) => {
    if (!window.confirm('Delete this mail rule?')) {
      return;
    }
    try {
      await mailAutomationService.deleteRule(ruleId);
      toast.success('Mail rule deleted');
      await loadAll();
    } catch {
      toast.error('Failed to delete mail rule');
    }
  };

  const handleToggleRuleEnabled = async (rule: MailRule) => {
    try {
      await mailAutomationService.updateRule(rule.id, { enabled: !(rule.enabled ?? true) });
      toast.success(rule.enabled ? 'Rule disabled' : 'Rule enabled');
      await loadAll({ silent: true });
    } catch {
      toast.error('Failed to update rule status');
    }
  };

  const handleOpenMailDocument = async (documentId: string) => {
    try {
      const node = await nodeService.getNode(documentId);
      const parentId = node.parentId || 'root';
      navigate(`/browse/${parentId}`);
    } catch {
      toast.error('Failed to open mail document');
    }
  };

  const handleFindSimilarFromMailDocument = (doc: MailDocumentDiagnosticItem) => {
    navigate('/search', {
      state: {
        similarSourceId: doc.documentId,
        similarSourceName: doc.name,
      },
    });
  };

  const selectedTag = tags.find((tag) => tag.id === ruleForm.assignTagId) || null;
  const mailActionHelper =
    ruleForm.mailAction === 'MOVE'
      ? 'Target mailbox folder (IMAP)'
      : ruleForm.mailAction === 'TAG'
      ? 'IMAP keyword/label to apply'
      : 'Optional';
  const filteredPreviewMatches = useMemo(() => {
    if (!previewResult) {
      return [];
    }
    if (previewProcessableFilter === 'PROCESSABLE') {
      return previewResult.matches.filter((item) => item.processable);
    }
    if (previewProcessableFilter === 'UNPROCESSABLE') {
      return previewResult.matches.filter((item) => !item.processable);
    }
    return previewResult.matches;
  }, [previewProcessableFilter, previewResult]);
  const sortedRules = useMemo(() => {
    return [...rules].sort((left, right) => {
      const leftPriority = left.priority ?? 0;
      const rightPriority = right.priority ?? 0;
      if (leftPriority !== rightPriority) {
        return leftPriority - rightPriority;
      }
      return left.name.localeCompare(right.name);
    });
  }, [rules]);
  const filteredRules = useMemo(() => {
    const query = ruleFilterText.trim().toLowerCase();
    return sortedRules.filter((rule) => {
      if (ruleFilterAccountId && (rule.accountId || '') !== ruleFilterAccountId) {
        return false;
      }
      if (ruleFilterStatus !== 'ALL') {
        const enabled = rule.enabled ?? true;
        if (ruleFilterStatus === 'ENABLED' && !enabled) {
          return false;
        }
        if (ruleFilterStatus === 'DISABLED' && enabled) {
          return false;
        }
      }
      if (!query) {
        return true;
      }
      const accountName = rule.accountId ? accountNameById.get(rule.accountId) || '' : 'all accounts';
      const haystack = [
        rule.name,
        accountName,
        rule.folder || '',
        rule.subjectFilter || '',
        rule.fromFilter || '',
        rule.toFilter || '',
        rule.bodyFilter || '',
        rule.attachmentFilenameInclude || '',
        rule.attachmentFilenameExclude || '',
      ]
        .join(' ')
        .toLowerCase();
      return haystack.includes(query);
    });
  }, [sortedRules, ruleFilterText, ruleFilterAccountId, ruleFilterStatus, accountNameById]);

  const filteredRuleIds = useMemo(() => filteredRules.map((rule) => rule.id), [filteredRules]);
  const selectedFilteredCount = useMemo(
    () => filteredRuleIds.filter((id) => selectedRuleIds.includes(id)).length,
    [filteredRuleIds, selectedRuleIds]
  );
  const allFilteredSelected = filteredRuleIds.length > 0 && selectedFilteredCount === filteredRuleIds.length;
  const ruleBulkBusy = ruleBulkUpdating || ruleBulkDeleting || ruleBulkReindexing;

  useEffect(() => {
    setSelectedRuleIds((prev) => prev.filter((id) => rules.some((rule) => rule.id === id)));
  }, [rules]);

  const handleToggleRuleSelection = (ruleId: string) => {
    setSelectedRuleIds((prev) => (
      prev.includes(ruleId)
        ? prev.filter((id) => id !== ruleId)
        : [...prev, ruleId]
    ));
  };

  const handleToggleSelectAllFilteredRules = (checked: boolean) => {
    if (checked) {
      setSelectedRuleIds((prev) => Array.from(new Set([...prev, ...filteredRuleIds])));
      return;
    }
    setSelectedRuleIds((prev) => prev.filter((id) => !filteredRuleIds.includes(id)));
  };

  const handleBulkSetRuleEnabled = async (enabled: boolean) => {
    if (selectedRuleIds.length === 0) {
      toast.info('Select at least one rule');
      return;
    }
    setRuleBulkUpdating(true);
    const selectedSet = new Set(selectedRuleIds);
    const targets = sortedRules.filter((rule) => selectedSet.has(rule.id));
    const results = await Promise.allSettled(
      targets.map((rule) => mailAutomationService.updateRule(rule.id, { enabled }))
    );
    const updatedById = new Map<string, MailRule>();
    let failed = 0;
    results.forEach((result) => {
      if (result.status === 'fulfilled') {
        updatedById.set(result.value.id, result.value);
      } else {
        failed += 1;
      }
    });
    if (updatedById.size > 0) {
      setRules((prev) => prev.map((rule) => updatedById.get(rule.id) || rule));
    }
    if (failed === 0) {
      toast.success(`Updated ${updatedById.size} rule(s)`);
    } else if (updatedById.size > 0) {
      toast.warning(`Updated ${updatedById.size} rule(s), failed ${failed}`);
    } else {
      toast.error('Failed to update selected rules');
    }
    setRuleBulkUpdating(false);
  };

  const handleBulkDeleteRules = async () => {
    if (selectedRuleIds.length === 0) {
      toast.info('Select at least one rule');
      return;
    }
    const confirmed = window.confirm(`Delete ${selectedRuleIds.length} selected rule(s)? This action cannot be undone.`);
    if (!confirmed) {
      return;
    }
    setRuleBulkDeleting(true);
    const selectedSet = new Set(selectedRuleIds);
    const targets = sortedRules.filter((rule) => selectedSet.has(rule.id));
    const results = await Promise.allSettled(targets.map((rule) => mailAutomationService.deleteRule(rule.id)));
    const deletedIds = new Set<string>();
    const failedNames: string[] = [];
    results.forEach((result, index) => {
      const rule = targets[index];
      if (result.status === 'fulfilled') {
        deletedIds.add(rule.id);
      } else {
        failedNames.push(rule.name);
      }
    });
    if (deletedIds.size > 0) {
      setRules((prev) => prev.filter((rule) => !deletedIds.has(rule.id)));
      setSelectedRuleIds((prev) => prev.filter((id) => !deletedIds.has(id)));
    }
    const failedPreview = failedNames.slice(0, 3).join(', ');
    const failedSuffix = failedNames.length > 3 ? ', ...' : '';
    if (failedNames.length === 0) {
      toast.success(`Deleted ${deletedIds.size} rule(s)`);
    } else if (deletedIds.size > 0) {
      toast.warning(`Deleted ${deletedIds.size} rule(s), failed ${failedNames.length}: ${failedPreview}${failedSuffix}`);
    } else {
      toast.error(`Failed to delete selected rules: ${failedPreview}${failedSuffix}`);
    }
    setRuleBulkDeleting(false);
  };

  const handleBulkReindexRules = async () => {
    if (selectedRuleIds.length === 0) {
      toast.info('Select at least one rule');
      return;
    }
    setRuleBulkReindexing(true);
    const selectedSet = new Set(selectedRuleIds);
    const targets = sortedRules.filter((rule) => selectedSet.has(rule.id));
    if (targets.length === 0) {
      setRuleBulkReindexing(false);
      return;
    }

    const firstPriority = targets[0].priority ?? 100;
    const basePriority = Math.max(1, firstPriority);
    const updates = targets.map((rule, index) => ({
      rule,
      nextPriority: basePriority + index * 10,
    }));
    const results = await Promise.allSettled(
      updates.map((item) => mailAutomationService.updateRule(item.rule.id, { priority: item.nextPriority }))
    );
    const updatedById = new Map<string, MailRule>();
    let failed = 0;
    results.forEach((result) => {
      if (result.status === 'fulfilled') {
        updatedById.set(result.value.id, result.value);
      } else {
        failed += 1;
      }
    });
    if (updatedById.size > 0) {
      setRules((prev) => prev.map((rule) => updatedById.get(rule.id) || rule));
    }
    if (failed === 0) {
      toast.success(`Reindexed ${updatedById.size} rule(s)`);
    } else if (updatedById.size > 0) {
      toast.warning(`Reindexed ${updatedById.size} rule(s), failed ${failed}`);
    } else {
      toast.error('Failed to reindex selected rules');
    }
    setRuleBulkReindexing(false);
  };

  const handleMoveRule = async (rule: MailRule, direction: 'up' | 'down') => {
    const currentIndex = sortedRules.findIndex((item) => item.id === rule.id);
    if (currentIndex < 0) {
      return;
    }
    const targetIndex = direction === 'up' ? currentIndex - 1 : currentIndex + 1;
    if (targetIndex < 0 || targetIndex >= sortedRules.length) {
      return;
    }
    const targetRule = sortedRules[targetIndex];
    const currentPriority = rule.priority ?? 0;
    const targetPriority = targetRule.priority ?? 0;
    let newCurrentPriority = targetPriority;
    let newTargetPriority = currentPriority;
    if (newCurrentPriority === newTargetPriority) {
      newCurrentPriority = direction === 'up'
        ? Math.max(0, targetPriority - 1)
        : targetPriority + 1;
    }

    try {
      const [updatedCurrent, updatedTarget] = await Promise.all([
        mailAutomationService.updateRule(rule.id, { priority: newCurrentPriority }),
        mailAutomationService.updateRule(targetRule.id, { priority: newTargetPriority }),
      ]);
      setRules((prev) => prev.map((item) => {
        if (item.id === updatedCurrent.id) {
          return updatedCurrent;
        }
        if (item.id === updatedTarget.id) {
          return updatedTarget;
        }
        return item;
      }));
      toast.success('Rule priority updated');
    } catch {
      toast.error('Failed to update rule priority');
    }
  };

  return (
    <Box maxWidth={1100}>
      <Box display="flex" alignItems="center" justifyContent="space-between" mb={2}>
        <Typography variant="h5">Mail Automation</Typography>
        <Button
          variant="outlined"
          startIcon={fetching ? <CircularProgress size={16} /> : <Refresh />}
          onClick={handleTriggerFetch}
          disabled={fetching}
        >
          Trigger Fetch
        </Button>
      </Box>

      {loading ? (
        <Box display="flex" justifyContent="center" py={4}>
          <CircularProgress />
        </Box>
      ) : (
        <Stack spacing={3}>
          <Card variant="outlined">
            <CardContent>
              <Box display="flex" alignItems="center" justifyContent="space-between" mb={2}>
                <Typography variant="h6">Mail Reporting</Typography>
                <Stack direction="row" spacing={1}>
                  <Button
                    variant="outlined"
                    size="small"
                    startIcon={reportLoading ? <CircularProgress size={16} /> : <Refresh />}
                    onClick={() => loadReport()}
                    disabled={reportLoading}
                  >
                    {reportLoading ? 'Refreshing...' : 'Refresh'}
                  </Button>
                  <Button variant="outlined" size="small" onClick={exportReportCsv} disabled={!report}>
                    Export CSV
                  </Button>
                </Stack>
              </Box>

              <Stack direction="row" spacing={2} sx={{ flexWrap: 'wrap', gap: 2 }} mb={2}>
                <FormControl size="small" sx={{ minWidth: 220 }}>
                  <InputLabel id="report-account-label">Account</InputLabel>
                  <Select
                    labelId="report-account-label"
                    label="Account"
                    value={reportAccountId}
                    onChange={(event) => setReportAccountId(event.target.value)}
                  >
                    <MenuItem value="">All accounts</MenuItem>
                    {accounts.map((account) => (
                      <MenuItem key={account.id} value={account.id}>
                        {account.name}
                      </MenuItem>
                    ))}
                  </Select>
                </FormControl>
                <FormControl size="small" sx={{ minWidth: 220 }}>
                  <InputLabel id="report-rule-label">Rule</InputLabel>
                  <Select
                    labelId="report-rule-label"
                    label="Rule"
                    value={reportRuleId}
                    onChange={(event) => setReportRuleId(event.target.value)}
                  >
                    <MenuItem value="">All rules</MenuItem>
                    {rules.map((rule) => (
                      <MenuItem key={rule.id} value={rule.id}>
                        {rule.name}
                      </MenuItem>
                    ))}
                  </Select>
                </FormControl>
                <FormControl size="small" sx={{ minWidth: 160 }}>
                  <InputLabel id="report-days-label">Days</InputLabel>
                  <Select
                    labelId="report-days-label"
                    label="Days"
                    value={reportDays}
                    onChange={(event) => setReportDays(Number(event.target.value))}
                  >
                    {[7, 14, 30, 60, 90].map((days) => (
                      <MenuItem key={`report-days-${days}`} value={days}>
                        Last {days} days
                      </MenuItem>
                    ))}
                  </Select>
                </FormControl>
              </Stack>

              {reportLoading && !report ? (
                <Box display="flex" justifyContent="center" py={2}>
                  <CircularProgress />
                </Box>
              ) : report ? (
                <Stack spacing={2}>
                  <Stack direction="row" spacing={1} sx={{ flexWrap: 'wrap', gap: 1 }}>
                    <Chip size="small" label={`Processed ${formatCount(reportTotals?.processed)}`} />
                    <Chip
                      size="small"
                      color={reportTotals && reportTotals.errors > 0 ? 'error' : 'default'}
                      label={`Errors ${formatCount(reportTotals?.errors)}`}
                    />
                    <Chip size="small" label={`Total ${formatCount(reportTotals?.total)}`} />
                    <Chip
                      size="small"
                      variant="outlined"
                      label={`Range ${formatDate(report.startDate)} → ${formatDate(report.endDate)}`}
                    />
                  </Stack>

                  <Box>
                    <Typography variant="subtitle2" gutterBottom>
                      Account summary
                    </Typography>
                    {reportAccounts.length === 0 ? (
                      <Typography variant="body2" color="text.secondary">
                        No mail activity recorded for the selected range.
                      </Typography>
                    ) : (
                      <TableContainer sx={{ border: '1px solid', borderColor: 'divider', borderRadius: 1 }}>
                        <Table size="small">
                          <TableHead>
                            <TableRow>
                              <TableCell>Account</TableCell>
                              <TableCell>Processed</TableCell>
                              <TableCell>Errors</TableCell>
                              <TableCell>Total</TableCell>
                              <TableCell>Last processed</TableCell>
                              <TableCell>Last error</TableCell>
                            </TableRow>
                          </TableHead>
                          <TableBody>
                            {reportAccounts.map((row) => (
                              <TableRow key={`report-account-${row.accountId}`} hover>
                                <TableCell>{row.accountName || row.accountId}</TableCell>
                                <TableCell>{formatCount(row.processed)}</TableCell>
                                <TableCell>{formatCount(row.errors)}</TableCell>
                                <TableCell>{formatCount(row.total)}</TableCell>
                                <TableCell>{formatDateTime(row.lastProcessedAt)}</TableCell>
                                <TableCell>{formatDateTime(row.lastErrorAt)}</TableCell>
                              </TableRow>
                            ))}
                          </TableBody>
                        </Table>
                      </TableContainer>
                    )}
                  </Box>

                  <Box>
                    <Typography variant="subtitle2" gutterBottom>
                      Rule summary
                    </Typography>
                    {reportRules.length === 0 ? (
                      <Typography variant="body2" color="text.secondary">
                        No rule activity recorded for the selected range.
                      </Typography>
                    ) : (
                      <TableContainer sx={{ border: '1px solid', borderColor: 'divider', borderRadius: 1 }}>
                        <Table size="small">
                          <TableHead>
                            <TableRow>
                              <TableCell>Rule</TableCell>
                              <TableCell>Account</TableCell>
                              <TableCell>Processed</TableCell>
                              <TableCell>Errors</TableCell>
                              <TableCell>Total</TableCell>
                              <TableCell>Last processed</TableCell>
                              <TableCell>Last error</TableCell>
                            </TableRow>
                          </TableHead>
                          <TableBody>
                            {reportRules.map((row) => (
                              <TableRow key={`report-rule-${row.ruleId}`} hover>
                                <TableCell>{row.ruleName || row.ruleId}</TableCell>
                                <TableCell>{row.accountName || row.accountId || 'All accounts'}</TableCell>
                                <TableCell>{formatCount(row.processed)}</TableCell>
                                <TableCell>{formatCount(row.errors)}</TableCell>
                                <TableCell>{formatCount(row.total)}</TableCell>
                                <TableCell>{formatDateTime(row.lastProcessedAt)}</TableCell>
                                <TableCell>{formatDateTime(row.lastErrorAt)}</TableCell>
                              </TableRow>
                            ))}
                          </TableBody>
                        </Table>
                      </TableContainer>
                    )}
                  </Box>

                  <Box>
                    <Typography variant="subtitle2" gutterBottom>
                      Daily trend
                    </Typography>
                    <Stack spacing={1}>
                      {reportTrend.map((row) => {
                        const totalRatio = row.total > 0 ? (row.total / maxTrendTotal) * 100 : 0;
                        const errorRatio = row.total > 0 ? (row.errors / row.total) * 100 : 0;
                        return (
                          <Stack
                            key={`report-trend-${row.date}`}
                            direction="row"
                            spacing={2}
                            alignItems="center"
                          >
                            <Typography variant="caption" sx={{ width: 90 }}>
                              {formatDate(row.date)}
                            </Typography>
                            <Box
                              sx={{
                                flexGrow: 1,
                                height: 8,
                                bgcolor: 'grey.200',
                                borderRadius: 1,
                                overflow: 'hidden',
                              }}
                            >
                              <Box
                                sx={{
                                  width: `${totalRatio}%`,
                                  height: '100%',
                                  bgcolor: 'primary.light',
                                }}
                              >
                                {row.errors > 0 && (
                                  <Box
                                    sx={{
                                      width: `${errorRatio}%`,
                                      height: '100%',
                                      bgcolor: 'error.main',
                                    }}
                                  />
                                )}
                              </Box>
                            </Box>
                            <Typography variant="caption" sx={{ width: 140, textAlign: 'right' }}>
                              {formatCount(row.processed)} / {formatCount(row.errors)}
                            </Typography>
                          </Stack>
                        );
                      })}
                    </Stack>
                  </Box>
                </Stack>
              ) : (
                <Typography variant="body2" color="text.secondary">
                  No report data available yet.
                </Typography>
              )}
            </CardContent>
          </Card>

          <Box ref={diagnosticsRef} id="diagnostics">
            <Card variant="outlined">
              <CardContent>
              <Box display="flex" alignItems="center" justifyContent="space-between" mb={2}>
                <Typography variant="h6">Fetch Diagnostics (Dry Run)</Typography>
                <Stack direction="row" spacing={1}>
                  <FormControl size="small" sx={{ minWidth: 220 }}>
                    <InputLabel id="diagnostics-account-label">Account</InputLabel>
                    <Select
                      labelId="diagnostics-account-label"
                      label="Account"
                      value={folderAccountId}
                      onChange={(event) => setFolderAccountId(event.target.value)}
                      disabled={accounts.length === 0}
                    >
                      {accounts.map((account) => (
                        <MenuItem key={account.id} value={account.id}>
                          {account.name}
                        </MenuItem>
                      ))}
                    </Select>
                  </FormControl>
                  <TextField
                    label="Max messages / folder"
                    type="number"
                    size="small"
                    value={
                      Number.isFinite(debugMaxMessages) && debugMaxMessages > 0 ? debugMaxMessages : ''
                    }
                    onChange={(e) =>
                      setDebugMaxMessages(e.target.value === '' ? 0 : Number(e.target.value))
                    }
                    inputProps={{ min: 1, max: 2000 }}
                    sx={{ width: 220 }}
                  />
                  <Button
                    variant="outlined"
                    startIcon={listingFolders ? <CircularProgress size={16} /> : <Refresh />}
                    onClick={handleListFolders}
                    disabled={listingFolders || accounts.length === 0}
                  >
                    {listingFolders ? 'Listing...' : 'List Folders'}
                  </Button>
                  <Button
                    variant="outlined"
                    startIcon={debugging ? <CircularProgress size={16} /> : <Refresh />}
                    onClick={handleDebugFetch}
                    disabled={debugging}
                  >
                    {debugging ? 'Running...' : 'Run Diagnostics'}
                  </Button>
                </Stack>
              </Box>

              <Box mb={2}>
                <Box display="flex" alignItems="center" justifyContent="space-between" mb={1}>
                  <Typography variant="subtitle2">Last fetch summary</Typography>
                  <Button
                    variant="outlined"
                    size="small"
                    startIcon={refreshing ? <CircularProgress size={16} /> : <Refresh />}
                    onClick={handleRefreshStatus}
                    disabled={refreshing}
                  >
                    Refresh status
                  </Button>
                </Box>
                {lastFetchSummary ? (
                  <Stack direction="row" spacing={1} sx={{ flexWrap: 'wrap', gap: 1 }}>
                    <Chip size="small" label={`Accounts ${lastFetchSummary.accounts}`} />
                    <Chip size="small" label={`Attempted ${lastFetchSummary.attemptedAccounts}`} />
                    {lastFetchSummary.accountErrors > 0 && (
                      <Chip size="small" color="error" label={`Account errors ${lastFetchSummary.accountErrors}`} />
                    )}
                    <Chip size="small" label={`Found ${lastFetchSummary.foundMessages}`} />
                    <Chip size="small" label={`Matched ${lastFetchSummary.matchedMessages}`} />
                    <Chip size="small" color="success" label={`Processed ${lastFetchSummary.processedMessages}`} />
                    {lastFetchSummary.skippedMessages > 0 && (
                      <Chip size="small" label={`Skipped ${lastFetchSummary.skippedMessages}`} />
                    )}
                    {lastFetchSummary.errorMessages > 0 && (
                      <Chip size="small" color="error" label={`Errors ${lastFetchSummary.errorMessages}`} />
                    )}
                    <Chip size="small" label={`Duration ${(lastFetchSummary.durationMs / 1000).toFixed(1)}s`} />
                  </Stack>
                ) : (
                  <Typography variant="body2" color="text.secondary">
                    No fetch summary yet. Use Trigger Fetch to capture the latest run.
                  </Typography>
                )}
                <Typography variant="caption" color="text.secondary" display="block" mt={1}>
                  Last trigger: {lastFetchAt ? formatDateTime(lastFetchAt) : 'N/A'}
                </Typography>
              </Box>

              {(availableFolders.length > 0 || hasListedFolders) && (
                <Box sx={{ mb: 2 }}>
                  <Typography variant="caption" color="text.secondary">
                    Available folders ({availableFolders.length})
                  </Typography>
                  {availableFolders.length > 0 ? (
                    <Stack direction="row" spacing={1} sx={{ mt: 0.5, flexWrap: 'wrap', gap: 1 }}>
                      {availableFolders.slice(0, 40).map((folder) => (
                        <Chip key={`folder-${folder}`} size="small" variant="outlined" label={folder} />
                      ))}
                    </Stack>
                  ) : (
                    <Typography variant="body2" color="text.secondary" sx={{ mt: 0.5 }}>
                      No folders returned for this account. Try again or verify the mailbox permissions.
                    </Typography>
                  )}
                </Box>
              )}

              {debugResult ? (
                <Stack spacing={1.5}>
                  <Stack direction="row" spacing={1} sx={{ flexWrap: 'wrap', gap: 1 }}>
                    <Chip size="small" label={`Attempted: ${debugResult.summary.attemptedAccounts}`} />
                    <Chip size="small" label={`Found: ${debugResult.summary.foundMessages}`} />
                    <Chip size="small" label={`Matched: ${debugResult.summary.matchedMessages}`} />
                    <Chip size="small" label={`Processable: ${debugResult.summary.processedMessages}`} />
                    <Chip size="small" label={`Skipped: ${debugResult.summary.skippedMessages}`} />
                    <Chip size="small" label={`Errors: ${debugResult.summary.errorMessages}`} />
                    <Chip size="small" variant="outlined" label={`Max/folder: ${debugResult.maxMessagesPerFolder}`} />
                    <Chip
                      size="small"
                      variant="outlined"
                      label={`Duration: ${(debugResult.summary.durationMs / 1000).toFixed(1)}s`}
                    />
                  </Stack>

                  {toSortedEntries(debugResult.skipReasons).length > 0 && (
                    <Box>
                      <Typography variant="caption" color="text.secondary">
                        Top skip reasons
                      </Typography>
                      <Stack direction="row" spacing={1} sx={{ mt: 0.5, flexWrap: 'wrap', gap: 1 }}>
                        {toSortedEntries(debugResult.skipReasons)
                          .slice(0, 6)
                          .map(([reason, count]) => (
                            <Chip
                              key={`global-${reason}`}
                              size="small"
                              variant="outlined"
                              label={`${formatReason(reason)}: ${count}`}
                            />
                          ))}
                      </Stack>
                    </Box>
                  )}

                  <Stack spacing={1.5}>
                    {debugResult.accounts.map((account) => {
                      const accountSkipEntries = toSortedEntries(account.skipReasons);
                      const accountRuleEntries = toSortedEntries(account.ruleMatches);
                      return (
                        <Box
                          key={account.accountId}
                          sx={{
                            p: 1.5,
                            border: '1px dashed',
                            borderColor: 'divider',
                            borderRadius: 1,
                          }}
                        >
                          <Stack spacing={1}>
                            <Stack direction="row" spacing={1} alignItems="center" sx={{ flexWrap: 'wrap', gap: 1 }}>
                              <Typography variant="subtitle2">{account.accountName}</Typography>
                              {account.accountError && (
                                <Chip size="small" color="error" label="Account error" />
                              )}
                              {account.skipReason && !account.accountError && (
                                <Chip
                                  size="small"
                                  variant="outlined"
                                  label={`Skip: ${formatReason(account.skipReason)}`}
                                />
                              )}
                              <Chip size="small" label={`Found: ${account.foundMessages}`} />
                              <Chip size="small" label={`Matched: ${account.matchedMessages}`} />
                              <Chip size="small" label={`Processable: ${account.processableMessages}`} />
                            </Stack>

                            {account.accountError && (
                              <Typography variant="caption" color="error.main">
                                {summarizeError(account.accountError, 240)}
                              </Typography>
                            )}

                            {accountSkipEntries.length > 0 && (
                              <Stack direction="row" spacing={1} sx={{ flexWrap: 'wrap', gap: 1 }}>
                                {accountSkipEntries.slice(0, 6).map(([reason, count]) => (
                                  <Chip
                                    key={`${account.accountId}-skip-${reason}`}
                                    size="small"
                                    variant="outlined"
                                    label={`${formatReason(reason)}: ${count}`}
                                  />
                                ))}
                              </Stack>
                            )}

                            {accountRuleEntries.length > 0 && (
                              <Box>
                                <Typography variant="caption" color="text.secondary">
                                  Rule matches
                                </Typography>
                                <Stack direction="row" spacing={1} sx={{ mt: 0.5, flexWrap: 'wrap', gap: 1 }}>
                                  {accountRuleEntries.slice(0, 6).map(([ruleName, count]) => (
                                    <Chip
                                      key={`${account.accountId}-rule-${ruleName}`}
                                      size="small"
                                      label={`${ruleName}: ${count}`}
                                    />
                                  ))}
                                </Stack>
                              </Box>
                            )}

                            {account.folderResults.length > 0 && (
                              <TableContainer
                                sx={{ border: '1px solid', borderColor: 'divider', borderRadius: 1 }}
                              >
                                <Table size="small">
                                  <TableHead>
                                    <TableRow>
                                      <TableCell>Folder</TableCell>
                                      <TableCell>Found</TableCell>
                                      <TableCell>Scanned</TableCell>
                                      <TableCell>Matched</TableCell>
                                      <TableCell>Processable</TableCell>
                                      <TableCell>Skipped</TableCell>
                                      <TableCell>Errors</TableCell>
                                    </TableRow>
                                  </TableHead>
                                  <TableBody>
                                    {account.folderResults.map((folderResult) => (
                                      <TableRow key={`${account.accountId}-${folderResult.folder}`} hover>
                                        <TableCell>{folderResult.folder}</TableCell>
                                        <TableCell>{folderResult.foundMessages}</TableCell>
                                        <TableCell>{folderResult.scannedMessages}</TableCell>
                                        <TableCell>{folderResult.matchedMessages}</TableCell>
                                        <TableCell>{folderResult.processableMessages}</TableCell>
                                        <TableCell>{folderResult.skippedMessages}</TableCell>
                                        <TableCell>{folderResult.errorMessages}</TableCell>
                                      </TableRow>
                                    ))}
                                  </TableBody>
                                </Table>
                              </TableContainer>
                            )}
                          </Stack>
                        </Box>
                      );
                    })}
                  </Stack>
                </Stack>
              ) : (
                <Typography variant="body2" color="text.secondary">
                  Run a dry-run diagnostics pass to see skip reasons and match coverage without ingesting mail.
                </Typography>
              )}
              </CardContent>
            </Card>
          </Box>

          <Card variant="outlined">
            <CardContent>
              <Box display="flex" alignItems="center" justifyContent="space-between" mb={2}>
                <Typography variant="h6">Connection Summary</Typography>
                <Stack direction="row" spacing={1}>
                  <Button
                    variant="outlined"
                    startIcon={refreshing ? <CircularProgress size={16} /> : <Refresh />}
                    onClick={handleRefreshStatus}
                    disabled={refreshing}
                  >
                    Refresh status
                  </Button>
                  <Button
                    variant="outlined"
                    startIcon={testingAccountId ? <CircularProgress size={16} /> : <Link />}
                    onClick={() => summaryAccount && handleTestConnection(summaryAccount.id)}
                    disabled={!summaryAccount || testingAccountId === summaryAccount?.id}
                  >
                    Test Connection
                  </Button>
                </Stack>
              </Box>

              {summaryAccount ? (
                <Stack spacing={1.5}>
                  <Stack direction={{ xs: 'column', md: 'row' }} spacing={2} alignItems={{ md: 'center' }}>
                    <FormControl size="small" sx={{ minWidth: 240 }}>
                      <InputLabel id="summary-account-label">Account</InputLabel>
                      <Select
                        labelId="summary-account-label"
                        label="Account"
                        value={summaryAccountId}
                        onChange={(event) => setSummaryAccountId(event.target.value)}
                      >
                        {accounts.map((account) => (
                          <MenuItem key={account.id} value={account.id}>
                            {account.name}
                          </MenuItem>
                        ))}
                      </Select>
                    </FormControl>
                    <Stack direction="row" spacing={1} sx={{ flexWrap: 'wrap', gap: 1 }}>
                      <Chip
                        size="small"
                        label={`Status ${summaryAccount.lastFetchStatus || 'N/A'}`}
                        color={
                          summaryAccount.lastFetchStatus === 'SUCCESS'
                            ? 'success'
                            : summaryAccount.lastFetchStatus === 'ERROR'
                            ? 'error'
                            : 'default'
                        }
                      />
                      <Chip size="small" label={`Poll ${summaryAccount.pollIntervalMinutes} min`} />
                      {summaryAccount.security === 'OAUTH2' && summaryAccount.oauthConnected != null && (
                        <Chip
                          size="small"
                          color={summaryAccount.oauthConnected ? 'success' : 'warning'}
                          label={summaryAccount.oauthConnected ? 'OAuth connected' : 'OAuth not connected'}
                        />
                      )}
                      {summaryAccount.security === 'OAUTH2' && summaryAccount.oauthEnvConfigured === false && (
                        <Chip size="small" color="warning" label="OAuth env missing" />
                      )}
                    </Stack>
                  </Stack>

                  <Typography variant="caption" color="text.secondary">
                    Last fetch: {summaryAccount.lastFetchAt ? formatDateTime(summaryAccount.lastFetchAt) : 'N/A'}
                  </Typography>
                  <Typography variant="caption" color="text.secondary">
                    Last success (this account):{' '}
                    {summaryAccount.lastFetchStatus === 'SUCCESS' && summaryAccount.lastFetchAt
                      ? formatDateTime(summaryAccount.lastFetchAt)
                      : 'N/A'}
                  </Typography>
                  <Typography variant="caption" color="text.secondary">
                    Latest success (any account): {latestSuccessAt ? formatDateTime(latestSuccessAt) : 'N/A'}
                  </Typography>

                  {summaryAccount.lastFetchError && (
                    <Typography variant="body2" color="error.main">
                      Last failure: {summarizeError(summaryAccount.lastFetchError, 200)}
                    </Typography>
                  )}
                </Stack>
              ) : (
                <Typography variant="body2" color="text.secondary">
                  No accounts available. Add a mail account to test connectivity.
                </Typography>
              )}
            </CardContent>
          </Card>

          <Card variant="outlined">
            <CardContent>
              <Box display="flex" alignItems="center" justifyContent="space-between" mb={2}>
                <Typography variant="h6">Runtime Health</Typography>
                <Stack direction="row" spacing={1} alignItems="center">
                  <FormControl size="small" sx={{ minWidth: 140 }}>
                    <InputLabel id="runtime-window-label">Window</InputLabel>
                    <Select
                      labelId="runtime-window-label"
                      label="Window"
                      value={String(runtimeWindowMinutes)}
                      onChange={(event) => setRuntimeWindowMinutes(Number(event.target.value))}
                    >
                      <MenuItem value="60">Last 60 min</MenuItem>
                      <MenuItem value="180">Last 3 hours</MenuItem>
                      <MenuItem value="720">Last 12 hours</MenuItem>
                      <MenuItem value="1440">Last 24 hours</MenuItem>
                    </Select>
                  </FormControl>
                  <Button
                    variant="outlined"
                    size="small"
                    startIcon={runtimeMetricsLoading ? <CircularProgress size={16} /> : <Refresh />}
                    onClick={() => loadRuntimeMetrics()}
                    disabled={runtimeMetricsLoading}
                  >
                    {runtimeMetricsLoading ? 'Refreshing...' : 'Refresh'}
                  </Button>
                </Stack>
              </Box>
              {runtimeMetrics ? (
                <Stack spacing={1.5}>
                  <Stack direction="row" spacing={1} flexWrap="wrap" gap={1}>
                    <Chip
                      size="small"
                      color={runtimeStatusColor(runtimeMetrics.status)}
                      label={`Status ${runtimeMetrics.status}`}
                    />
                    <Chip size="small" label={`Attempts ${formatCount(runtimeMetrics.attempts)}`} />
                    <Chip size="small" color="success" label={`Success ${formatCount(runtimeMetrics.successes)}`} />
                    <Chip size="small" color={runtimeMetrics.errors > 0 ? 'error' : 'default'} label={`Errors ${formatCount(runtimeMetrics.errors)}`} />
                    <Chip
                      size="small"
                      label={`Error rate ${(runtimeMetrics.errorRate * 100).toFixed(1)}%`}
                    />
                    <Chip
                      size="small"
                      label={`Avg duration ${runtimeMetrics.avgDurationMs != null ? `${runtimeMetrics.avgDurationMs} ms` : 'N/A'}`}
                    />
                    {runtimeMetrics.trend && (
                      <Tooltip
                        title={runtimeMetrics.trend.summary || 'Runtime trend compared with previous window'}
                      >
                        <Chip
                          size="small"
                          color={runtimeTrendColor(runtimeMetrics.trend.direction)}
                          label={`Trend ${runtimeMetrics.trend.direction}`}
                        />
                      </Tooltip>
                    )}
                  </Stack>
                  <Typography variant="caption" color="text.secondary">
                    Last success: {runtimeMetrics.lastSuccessAt ? formatDateTime(runtimeMetrics.lastSuccessAt) : 'N/A'}
                  </Typography>
                  <Typography variant="caption" color="text.secondary">
                    Last error: {runtimeMetrics.lastErrorAt ? formatDateTime(runtimeMetrics.lastErrorAt) : 'N/A'}
                  </Typography>
                  {runtimeMetrics.topErrors && runtimeMetrics.topErrors.length > 0 && (
                    <Box>
                      <Typography variant="caption" color="text.secondary">
                        Top error reasons
                      </Typography>
                      <Stack direction="row" spacing={1} sx={{ mt: 0.75, flexWrap: 'wrap', gap: 1 }}>
                        {runtimeMetrics.topErrors.slice(0, 5).map((item, index) => (
                          <Tooltip
                            key={`${item.errorMessage}-${index}`}
                            title={item.lastSeenAt ? `Last seen: ${formatDateTime(item.lastSeenAt)}` : 'Last seen: N/A'}
                          >
                            <Chip
                              size="small"
                              color="warning"
                              label={`${summarizeError(item.errorMessage, 80)} (${formatCount(item.count)})`}
                              onClick={() => applyRuntimeDiagnosticsErrorFilter(item.errorMessage)}
                              data-testid={`runtime-top-error-${index}`}
                            />
                          </Tooltip>
                        ))}
                      </Stack>
                    </Box>
                  )}
                </Stack>
              ) : (
                <Typography variant="body2" color="text.secondary">
                  Runtime metrics unavailable.
                </Typography>
              )}
            </CardContent>
          </Card>

          <Card variant="outlined">
            <CardContent>
              <Box display="flex" alignItems="center" justifyContent="space-between" mb={2}>
                <Typography variant="h6">Recent Mail Activity</Typography>
                <Stack direction="row" spacing={1}>
                  <Button variant="outlined" onClick={exportDiagnosticsCsv} disabled={!diagnostics || exportDisabled}>
                    Export CSV
                  </Button>
                  <Button
                    variant="outlined"
                    startIcon={<ContentCopy />}
                    onClick={handleCopyDiagnosticsLink}
                  >
                    Copy link
                  </Button>
                  <Button
                    variant="outlined"
                    onClick={handleClearDiagnosticsFilters}
                    disabled={!diagnosticsFiltersActive}
                    data-testid="diagnostics-clear-filters"
                  >
                    Clear filters
                  </Button>
                  <Button
                    variant="outlined"
                    startIcon={diagnosticsLoading ? <CircularProgress size={16} /> : <Refresh />}
                    onClick={() => loadDiagnostics()}
                    disabled={diagnosticsLoading}
                  >
                    {diagnosticsLoading ? 'Refreshing...' : 'Refresh'}
                  </Button>
                </Stack>
              </Box>

              {diagnosticsLoading && !diagnostics ? (
                <Box display="flex" justifyContent="center" py={2}>
                  <CircularProgress size={24} />
                </Box>
              ) : (
                <Stack spacing={2}>
                  <Stack direction={{ xs: 'column', md: 'row' }} spacing={2}>
                    <FormControl size="small" sx={{ minWidth: 220 }}>
                      <InputLabel id="diagnostics-account-label">Account</InputLabel>
                      <Select
                        labelId="diagnostics-account-label"
                        label="Account"
                        value={diagnosticsAccountId}
                        onChange={(event) => setDiagnosticsAccountId(event.target.value)}
                      >
                        <MenuItem value="">
                          <em>All Accounts</em>
                        </MenuItem>
                        {accounts.map((account) => (
                          <MenuItem key={account.id} value={account.id}>
                            {account.name}
                          </MenuItem>
                        ))}
                      </Select>
                    </FormControl>
                    <FormControl size="small" sx={{ minWidth: 220 }}>
                      <InputLabel id="diagnostics-rule-label">Rule</InputLabel>
                      <Select
                        labelId="diagnostics-rule-label"
                        label="Rule"
                        value={diagnosticsRuleId}
                        onChange={(event) => setDiagnosticsRuleId(event.target.value)}
                      >
                        <MenuItem value="">
                          <em>All Rules</em>
                        </MenuItem>
                        {rules.map((rule) => (
                          <MenuItem key={rule.id} value={rule.id}>
                            {rule.name}
                          </MenuItem>
                        ))}
                      </Select>
                    </FormControl>
                    <FormControl size="small" sx={{ minWidth: 200 }}>
                      <InputLabel id="diagnostics-status-label">Status</InputLabel>
                      <Select
                        labelId="diagnostics-status-label"
                        label="Status"
                        value={diagnosticsStatus}
                        onChange={(event) => setDiagnosticsStatus(event.target.value)}
                      >
                        <MenuItem value="">
                          <em>All Statuses</em>
                        </MenuItem>
                        <MenuItem value="PROCESSED">Processed</MenuItem>
                        <MenuItem value="ERROR">Error</MenuItem>
                      </Select>
                    </FormControl>
                    <FormControl size="small" sx={{ minWidth: 180 }}>
                      <InputLabel id="diagnostics-sort-label">Sort by</InputLabel>
                      <Select
                        labelId="diagnostics-sort-label"
                        label="Sort by"
                        value={diagnosticsSort}
                        onChange={(event) => setDiagnosticsSort(event.target.value as MailDiagnosticsSortField)}
                      >
                        <MenuItem value="processedAt">Processed time</MenuItem>
                        <MenuItem value="status">Status</MenuItem>
                        <MenuItem value="rule">Rule</MenuItem>
                        <MenuItem value="account">Account</MenuItem>
                      </Select>
                    </FormControl>
                    <FormControl size="small" sx={{ minWidth: 180 }}>
                      <InputLabel id="diagnostics-order-label">Order</InputLabel>
                      <Select
                        labelId="diagnostics-order-label"
                        label="Order"
                        value={diagnosticsOrder}
                        onChange={(event) => setDiagnosticsOrder(event.target.value as MailDiagnosticsSortOrder)}
                      >
                        <MenuItem value="desc">Descending</MenuItem>
                        <MenuItem value="asc">Ascending</MenuItem>
                      </Select>
                    </FormControl>
                    <TextField
                      size="small"
                      label="Subject contains"
                      value={diagnosticsSubject}
                      onChange={(event) => setDiagnosticsSubject(event.target.value)}
                      sx={{ minWidth: 220 }}
                    />
                    <TextField
                      size="small"
                      label="Error contains"
                      value={diagnosticsErrorContains}
                      onChange={(event) => setDiagnosticsErrorContains(event.target.value)}
                      sx={{ minWidth: 220 }}
                    />
                    <TextField
                      size="small"
                      type="datetime-local"
                      label="Processed from"
                      value={diagnosticsProcessedFrom}
                      onChange={(event) => setDiagnosticsProcessedFrom(event.target.value)}
                      sx={{ minWidth: 220 }}
                      InputLabelProps={{ shrink: true }}
                    />
                    <TextField
                      size="small"
                      type="datetime-local"
                      label="Processed to"
                      value={diagnosticsProcessedTo}
                      onChange={(event) => setDiagnosticsProcessedTo(event.target.value)}
                      sx={{ minWidth: 220 }}
                      InputLabelProps={{ shrink: true }}
                    />
                  </Stack>
                  <Stack direction="row" spacing={1} alignItems="center" flexWrap="wrap" gap={1}>
                    <Typography variant="caption" color="text.secondary">
                      Quick range:
                    </Typography>
                    <Button size="small" variant="outlined" onClick={() => applyDiagnosticsQuickWindow(24)}>
                      Last 24h
                    </Button>
                    <Button size="small" variant="outlined" onClick={() => applyDiagnosticsQuickWindow(24 * 7)}>
                      Last 7d
                    </Button>
                    <Button size="small" variant="outlined" onClick={() => applyDiagnosticsQuickWindow(24 * 30)}>
                      Last 30d
                    </Button>
                    <Button
                      size="small"
                      variant="text"
                      onClick={() => {
                        setDiagnosticsProcessedFrom('');
                        setDiagnosticsProcessedTo('');
                      }}
                      disabled={!diagnosticsProcessedFrom && !diagnosticsProcessedTo}
                    >
                      Clear time
                    </Button>
                  </Stack>
                  {diagnosticsFiltersActive && (
                    <Stack direction="row" spacing={1} alignItems="center" flexWrap="wrap" gap={1}>
                      <Typography variant="caption" color="text.secondary">
                        Active filters:
                      </Typography>
                      {diagnosticsAccountId && (
                        <Chip
                          size="small"
                          label={`Account: ${accountNameById.get(diagnosticsAccountId) || diagnosticsAccountId}`}
                          onDelete={() => setDiagnosticsAccountId('')}
                        />
                      )}
                      {diagnosticsRuleId && (
                        <Chip
                          size="small"
                          label={`Rule: ${diagnosticsRuleNameById.get(diagnosticsRuleId) || diagnosticsRuleId}`}
                          onDelete={() => setDiagnosticsRuleId('')}
                        />
                      )}
                      {diagnosticsStatus && (
                        <Chip
                          size="small"
                          label={`Status: ${diagnosticsStatus}`}
                          onDelete={() => setDiagnosticsStatus('')}
                        />
                      )}
                      {diagnosticsSort !== 'processedAt' && (
                        <Chip
                          size="small"
                          label={`Sort: ${diagnosticsSort}`}
                          onDelete={() => setDiagnosticsSort('processedAt')}
                        />
                      )}
                      {diagnosticsOrder !== 'desc' && (
                        <Chip
                          size="small"
                          label={`Order: ${diagnosticsOrder}`}
                          onDelete={() => setDiagnosticsOrder('desc')}
                        />
                      )}
                      {diagnosticsSubject && (
                        <Chip
                          size="small"
                          label={`Subject: ${summarizeText(diagnosticsSubject, 40)}`}
                          onDelete={() => setDiagnosticsSubject('')}
                        />
                      )}
                      {diagnosticsErrorContains && (
                        <Chip
                          size="small"
                          label={`Error: ${summarizeText(diagnosticsErrorContains, 40)}`}
                          onDelete={() => setDiagnosticsErrorContains('')}
                        />
                      )}
                      {diagnosticsProcessedFrom && (
                        <Chip
                          size="small"
                          label={`From: ${diagnosticsProcessedFrom.replace('T', ' ')}`}
                          onDelete={() => setDiagnosticsProcessedFrom('')}
                        />
                      )}
                      {diagnosticsProcessedTo && (
                        <Chip
                          size="small"
                          label={`To: ${diagnosticsProcessedTo.replace('T', ' ')}`}
                          onDelete={() => setDiagnosticsProcessedTo('')}
                        />
                      )}
                    </Stack>
                  )}
                  <Stack spacing={1}>
                    <Typography variant="subtitle2">Export Fields</Typography>
                    <Stack direction="row" spacing={2} flexWrap="wrap">
                      <FormControlLabel
                        control={
                          <Checkbox
                            checked={exportOptions.includeProcessed}
                            onChange={(event) =>
                              setExportOptions((prev) => ({
                                ...prev,
                                includeProcessed: event.target.checked,
                              }))
                            }
                          />
                        }
                        label="Processed Messages"
                      />
                      <FormControlLabel
                        control={
                          <Checkbox
                            checked={exportOptions.includeDocuments}
                            onChange={(event) =>
                              setExportOptions((prev) => ({
                                ...prev,
                                includeDocuments: event.target.checked,
                              }))
                            }
                          />
                        }
                        label="Mail Documents"
                      />
                      <FormControlLabel
                        control={
                          <Checkbox
                            checked={exportOptions.includeSubject}
                            onChange={(event) =>
                              setExportOptions((prev) => ({
                                ...prev,
                                includeSubject: event.target.checked,
                              }))
                            }
                            disabled={!exportOptions.includeProcessed}
                          />
                        }
                        label="Subject"
                      />
                      <FormControlLabel
                        control={
                          <Checkbox
                            checked={exportOptions.includeError}
                            onChange={(event) =>
                              setExportOptions((prev) => ({
                                ...prev,
                                includeError: event.target.checked,
                              }))
                            }
                            disabled={!exportOptions.includeProcessed}
                          />
                        }
                        label="Error"
                      />
                      <FormControlLabel
                        control={
                          <Checkbox
                            checked={exportOptions.includePath}
                            onChange={(event) =>
                              setExportOptions((prev) => ({
                                ...prev,
                                includePath: event.target.checked,
                              }))
                            }
                            disabled={!exportOptions.includeDocuments}
                          />
                        }
                        label="Path"
                      />
                      <FormControlLabel
                        control={
                          <Checkbox
                            checked={exportOptions.includeMimeType}
                            onChange={(event) =>
                              setExportOptions((prev) => ({
                                ...prev,
                                includeMimeType: event.target.checked,
                              }))
                            }
                            disabled={!exportOptions.includeDocuments}
                          />
                        }
                        label="MIME Type"
                      />
                      <FormControlLabel
                        control={
                          <Checkbox
                            checked={exportOptions.includeFileSize}
                            onChange={(event) =>
                              setExportOptions((prev) => ({
                                ...prev,
                                includeFileSize: event.target.checked,
                              }))
                            }
                            disabled={!exportOptions.includeDocuments}
                          />
                        }
                        label="File Size"
                      />
                    </Stack>
                    <Typography variant="caption" color="text.secondary">
                      Export scope snapshot: {diagnosticsExportScopeSummary}
                    </Typography>
                  </Stack>
                  <Typography variant="caption" color="text.secondary">
                    Showing last {diagnostics?.limit ?? diagnosticsLimit} items tagged by mail ingestion.
                    {diagnostics?.limit
                      ? ` Exported CSV is limited to the same ${diagnostics.limit} items.`
                      : ''}
                  </Typography>

                  <Box>
                    <Box mb={1} display="flex" flexDirection="column" gap={1}>
                      <Box
                        display="flex"
                        alignItems="center"
                        justifyContent="space-between"
                        flexWrap="wrap"
                        gap={1}
                      >
                        <Stack direction="row" spacing={1} alignItems="center" flexWrap="wrap" gap={1}>
                          <Typography variant="subtitle2">Processed Messages</Typography>
                          <Chip
                            size="small"
                            label="All"
                            color={diagnosticsStatus === '' ? 'primary' : 'default'}
                            onClick={() => setDiagnosticsStatus('')}
                          />
                          <Chip
                            size="small"
                            label="Processed"
                            color={diagnosticsStatus === 'PROCESSED' ? 'primary' : 'default'}
                            onClick={() => setDiagnosticsStatus('PROCESSED')}
                          />
                          <Chip
                            size="small"
                            label="Error"
                            color={diagnosticsStatus === 'ERROR' ? 'primary' : 'default'}
                            onClick={() => setDiagnosticsStatus('ERROR')}
                          />
                        </Stack>
                        <Stack direction="row" spacing={1} alignItems="center">
                          <Button
                            variant="outlined"
                            size="small"
                            startIcon={retentionLoading ? <CircularProgress size={16} /> : <Refresh />}
                            onClick={() => loadRetention()}
                            disabled={retentionLoading}
                          >
                            {retentionLoading ? 'Refreshing...' : 'Refresh Retention'}
                          </Button>
                          <Button
                            variant="outlined"
                            size="small"
                            onClick={handleRetentionCleanup}
                            disabled={!retentionEnabled || retentionExpiredCount === 0 || retentionCleaning}
                          >
                            {retentionCleaning ? 'Cleaning...' : 'Clean up expired'}
                          </Button>
                          <Button
                            variant="outlined"
                            size="small"
                            onClick={handleBulkDeleteProcessed}
                            disabled={selectedProcessedIds.length === 0}
                          >
                            Delete Selected
                          </Button>
                        </Stack>
                      </Box>
                      <Stack direction="row" spacing={1} alignItems="center" flexWrap="wrap" gap={1}>
                        <Typography variant="caption" color="text.secondary">
                          Retention:
                        </Typography>
                        {processedRetention ? (
                          <>
                            <Chip
                              size="small"
                              color={retentionEnabled ? 'default' : 'warning'}
                              label={
                                retentionEnabled
                                  ? `Retention ${retentionDays}d`
                                  : 'Retention disabled'
                              }
                            />
                            <Chip
                              size="small"
                              variant="outlined"
                              label={`Expired ${retentionExpiredCount}`}
                            />
                          </>
                        ) : (
                          <Chip size="small" variant="outlined" label="Retention unknown" />
                        )}
                      </Stack>
                      <Stack spacing={1}>
                        <Box display="flex" alignItems="center" justifyContent="space-between" flexWrap="wrap" gap={1}>
                          <Typography variant="caption" color="text.secondary">
                            Rule error leaderboard (current diagnostics window):
                          </Typography>
                          <Button
                            variant="text"
                            size="small"
                            onClick={() => applyDiagnosticsRuleErrorFilter('')}
                            disabled={diagnosticsRuleErrorLeaders.length === 0}
                          >
                            View all errors
                          </Button>
                        </Box>
                        {diagnosticsRuleErrorLeaders.length === 0 ? (
                          <Typography variant="caption" color="text.secondary">
                            No rule-level errors found.
                          </Typography>
                        ) : (
                          <Stack spacing={1}>
                            {diagnosticsRuleErrorLeaders.map((item) => (
                              <Stack key={item.ruleId} spacing={0.5}>
                                <Stack direction="row" spacing={1} alignItems="center" flexWrap="wrap" gap={1}>
                                  <Button
                                    variant={
                                      diagnosticsStatus === 'ERROR' && diagnosticsRuleId === item.ruleId
                                        ? 'contained'
                                        : 'outlined'
                                    }
                                    size="small"
                                    onClick={() => applyDiagnosticsRuleErrorFilter(item.ruleId)}
                                  >
                                    {item.ruleName} ({item.errorCount})
                                  </Button>
                                  <Button
                                    variant="text"
                                    size="small"
                                    onClick={() => toggleDiagnosticsRuleErrorDetails(item.ruleId)}
                                    disabled={(diagnosticsRuleErrorSamples.get(item.ruleId) || []).length === 0}
                                  >
                                    {expandedRuleErrorRuleId === item.ruleId ? 'Hide details' : 'Show details'}
                                  </Button>
                                  <Button
                                    variant="text"
                                    size="small"
                                    onClick={() => openRuleDiagnosticsDrawer(item.ruleId)}
                                  >
                                    Open drawer
                                  </Button>
                                </Stack>
                                {expandedRuleErrorRuleId === item.ruleId && (
                                  <Stack spacing={0.5} sx={{ pl: 1 }}>
                                    {(diagnosticsRuleErrorSamples.get(item.ruleId) || []).map((sample) => (
                                      <Stack
                                        key={sample.id}
                                        direction="row"
                                        spacing={1}
                                        alignItems="center"
                                        flexWrap="wrap"
                                        gap={1}
                                      >
                                        <Typography variant="caption" color="text.secondary">
                                          {formatDateTime(sample.processedAt)}
                                        </Typography>
                                        <Typography variant="caption" title={sample.errorMessage || ''}>
                                          {summarizeError(sample.errorMessage, 160) || 'No error message'}
                                        </Typography>
                                        <Tooltip title="Copy error message">
                                          <IconButton
                                            size="small"
                                            onClick={() => copyDiagnosticsRuleErrorMessage(sample)}
                                          >
                                            <ContentCopy fontSize="inherit" />
                                          </IconButton>
                                        </Tooltip>
                                      </Stack>
                                    ))}
                                  </Stack>
                                )}
                              </Stack>
                            ))}
                          </Stack>
                        )}
                      </Stack>
                    </Box>
                    {recentProcessed.length === 0 ? (
                      <Typography variant="body2" color="text.secondary">
                        No processed messages recorded yet.
                      </Typography>
                    ) : (
                      <TableContainer sx={{ border: '1px solid', borderColor: 'divider', borderRadius: 1 }}>
                        <Table size="small">
                          <TableHead>
                            <TableRow>
                              <TableCell padding="checkbox">
                                <Checkbox
                                  checked={allProcessedSelected}
                                  indeterminate={someProcessedSelected}
                                  onChange={toggleSelectAllProcessed}
                                />
                              </TableCell>
                              <TableCell>Processed</TableCell>
                              <TableCell>Status</TableCell>
                              <TableCell>Account</TableCell>
                              <TableCell>Rule</TableCell>
                              <TableCell>Folder</TableCell>
                              <TableCell>UID</TableCell>
                              <TableCell>Linked Doc</TableCell>
                              <TableCell>Subject</TableCell>
                              <TableCell>Error Message</TableCell>
                            </TableRow>
                          </TableHead>
                          <TableBody>
                            {recentProcessed.map((item) => (
                              <TableRow key={item.id} hover>
                                <TableCell padding="checkbox">
                                  <Checkbox
                                    checked={selectedProcessedIds.includes(item.id)}
                                    onChange={() => toggleProcessedSelection(item.id)}
                                  />
                                </TableCell>
                                <TableCell>{formatDateTime(item.processedAt)}</TableCell>
                                <TableCell>
                                  <Chip size="small" color={statusColor(item.status)} label={item.status} />
                                </TableCell>
                                <TableCell>{item.accountName || item.accountId || '-'}</TableCell>
                                <TableCell>{item.ruleName || item.ruleId || '-'}</TableCell>
                                <TableCell>{item.folder}</TableCell>
                                <TableCell>{item.uid}</TableCell>
                                <TableCell>
                                  {(() => {
                                    const linkedDoc = recentDocumentByProcessedKey.get(
                                      processedKey(item.accountId, item.uid)
                                    );
                                    if (!linkedDoc) {
                                      return (
                                        <Typography variant="caption" color="text.secondary">
                                          -
                                        </Typography>
                                      );
                                    }
                                    return (
                                      <Stack direction="row" spacing={0.5} alignItems="center">
                                        <Tooltip title="Open linked document">
                                          <IconButton
                                            size="small"
                                            aria-label="open linked document"
                                            onClick={() => handleOpenMailDocument(linkedDoc.documentId)}
                                          >
                                            <Visibility fontSize="inherit" />
                                          </IconButton>
                                        </Tooltip>
                                        <Tooltip title="Find similar documents">
                                          <IconButton
                                            size="small"
                                            aria-label="find similar linked document"
                                            onClick={() => handleFindSimilarFromMailDocument(linkedDoc)}
                                          >
                                            <Search fontSize="inherit" />
                                          </IconButton>
                                        </Tooltip>
                                      </Stack>
                                    );
                                  })()}
                                </TableCell>
                                <TableCell>
                                  <Typography variant="caption" title={item.subject || ''}>
                                    {summarizeText(item.subject) || '-'}
                                  </Typography>
                                </TableCell>
                                <TableCell>
                                  {item.status === 'ERROR' ? (
                                    <Stack direction="row" spacing={0.5} alignItems="center">
                                      <Typography variant="caption" title={item.errorMessage || ''}>
                                        {summarizeError(item.errorMessage, 120) || 'No error message'}
                                      </Typography>
                                      <Tooltip title="Replay failed item">
                                        <span>
                                          <Button
                                            size="small"
                                            variant="text"
                                            onClick={() => handleReplayProcessedMail(item)}
                                            disabled={replayingProcessedId === item.id}
                                          >
                                            {replayingProcessedId === item.id ? 'Replaying...' : 'Replay'}
                                          </Button>
                                        </span>
                                      </Tooltip>
                                      <Tooltip title="Copy error message">
                                        <IconButton size="small" onClick={() => copyDiagnosticsRuleErrorMessage(item)}>
                                          <ContentCopy fontSize="inherit" />
                                        </IconButton>
                                      </Tooltip>
                                    </Stack>
                                  ) : (
                                    <Typography variant="caption" color="text.secondary">
                                      -
                                    </Typography>
                                  )}
                                </TableCell>
                              </TableRow>
                            ))}
                          </TableBody>
                        </Table>
                      </TableContainer>
                    )}
                  </Box>

                  <Box>
                    <Typography variant="subtitle2" gutterBottom>
                      Mail Documents
                    </Typography>
                    {recentDocuments.length === 0 ? (
                      <Typography variant="body2" color="text.secondary">
                        No mail documents found yet (new mail will appear here).
                      </Typography>
                    ) : (
                      <TableContainer sx={{ border: '1px solid', borderColor: 'divider', borderRadius: 1 }}>
                        <Table size="small">
                          <TableHead>
                            <TableRow>
                              <TableCell>Created</TableCell>
                              <TableCell>Name</TableCell>
                              <TableCell>Path</TableCell>
                              <TableCell>Account</TableCell>
                              <TableCell>Rule</TableCell>
                              <TableCell>Folder</TableCell>
                              <TableCell>UID</TableCell>
                              <TableCell align="right">Search</TableCell>
                              <TableCell align="right">Open</TableCell>
                            </TableRow>
                          </TableHead>
                          <TableBody>
                            {recentDocuments.map((doc) => (
                              <TableRow key={doc.documentId} hover>
                                <TableCell>{formatDateTime(doc.createdDate)}</TableCell>
                                <TableCell>{doc.name}</TableCell>
                                <TableCell>
                                  <Typography variant="caption" title={doc.path}>
                                    {summarizeText(doc.path, 80)}
                                  </Typography>
                                </TableCell>
                                <TableCell>{doc.accountName || doc.accountId || '-'}</TableCell>
                                <TableCell>{doc.ruleName || doc.ruleId || '-'}</TableCell>
                                <TableCell>{doc.folder || '-'}</TableCell>
                                <TableCell>{doc.uid || '-'}</TableCell>
                                <TableCell align="right">
                                  <Tooltip title="Find similar documents">
                                    <IconButton
                                      size="small"
                                      aria-label="find similar documents"
                                      onClick={() => handleFindSimilarFromMailDocument(doc)}
                                    >
                                      <Search fontSize="small" />
                                    </IconButton>
                                  </Tooltip>
                                </TableCell>
                                <TableCell align="right">
                                  <Tooltip title="Open in folder">
                                    <IconButton
                                      size="small"
                                      aria-label="open mail document"
                                      onClick={() => handleOpenMailDocument(doc.documentId)}
                                    >
                                      <Visibility fontSize="small" />
                                    </IconButton>
                                  </Tooltip>
                                </TableCell>
                              </TableRow>
                            ))}
                          </TableBody>
                        </Table>
                      </TableContainer>
                    )}
                  </Box>
                </Stack>
              )}
            </CardContent>
          </Card>

          <Card variant="outlined">
            <CardContent>
              <Box display="flex" alignItems="center" justifyContent="space-between" mb={2}>
                <Typography variant="h6">Mail Accounts</Typography>
                <Stack direction="row" spacing={1}>
                  <Button
                    variant="outlined"
                    startIcon={refreshing ? <CircularProgress size={16} /> : <Refresh />}
                    onClick={handleRefreshStatus}
                    disabled={refreshing}
                  >
                    Refresh Status
                  </Button>
                  <Button variant="contained" startIcon={<Add />} onClick={openCreateAccount}>
                    New Account
                  </Button>
                </Stack>
              </Box>
              {accountHealth.total > 0 && (
                <Box mb={2}>
                  <Typography variant="subtitle2" gutterBottom>
                    Account health
                  </Typography>
                  <Stack direction="row" spacing={1} sx={{ flexWrap: 'wrap', gap: 1 }}>
                    <Chip size="small" label={`Total ${accountHealth.total}`} />
                    <Chip size="small" color="success" label={`Enabled ${accountHealth.enabled}`} />
                    {accountHealth.disabled > 0 && (
                      <Chip size="small" label={`Disabled ${accountHealth.disabled}`} />
                    )}
                    <Chip size="small" label={`Fetch OK ${accountHealth.fetchSuccess}`} color="success" />
                    {accountHealth.fetchError > 0 && (
                      <Chip size="small" label={`Fetch errors ${accountHealth.fetchError}`} color="error" />
                    )}
                    {accountHealth.fetchOther > 0 && (
                      <Chip size="small" label={`Fetch other ${accountHealth.fetchOther}`} />
                    )}
                    {accountHealth.neverFetched > 0 && (
                      <Chip size="small" label={`Never fetched ${accountHealth.neverFetched}`} />
                    )}
                    {accountHealth.staleFetches > 0 && (
                      <Tooltip title="Last fetch is older than 2x the poll interval.">
                        <Chip size="small" label={`Stale ${accountHealth.staleFetches}`} color="warning" />
                      </Tooltip>
                    )}
                    {accountHealth.oauthAccounts > 0 && (
                      <Chip size="small" label={`OAuth ${accountHealth.oauthAccounts}`} />
                    )}
                    {accountHealth.oauthConnected > 0 && (
                      <Chip size="small" label={`OAuth connected ${accountHealth.oauthConnected}`} color="success" />
                    )}
                    {accountHealth.oauthNotConnected > 0 && (
                      <Chip size="small" label={`OAuth not connected ${accountHealth.oauthNotConnected}`} color="warning" />
                    )}
                    {accountHealth.oauthMissingEnv > 0 && (
                      <Chip size="small" label={`OAuth env missing ${accountHealth.oauthMissingEnv}`} color="warning" />
                    )}
                  </Stack>
                  <Typography variant="caption" color="text.secondary" display="block" mt={1}>
                    Latest fetch: {accountHealth.latestFetchAt ? formatDateTime(accountHealth.latestFetchAt) : 'N/A'}
                  </Typography>
                </Box>
              )}
              <TableContainer>
                <Table size="small">
                  <TableHead>
                    <TableRow>
                      <TableCell>Name</TableCell>
                      <TableCell>Host</TableCell>
                      <TableCell>Username</TableCell>
                      <TableCell>Security</TableCell>
                      <TableCell>Poll (min)</TableCell>
                      <TableCell>Last fetch</TableCell>
                      <TableCell>Status</TableCell>
                      <TableCell
                        align="right"
                        sx={{ position: 'sticky', right: 0, backgroundColor: 'background.paper', zIndex: 2 }}
                      >
                        Actions
                      </TableCell>
                    </TableRow>
                  </TableHead>
                  <TableBody>
                    {accounts.length === 0 && (
                      <TableRow>
                        <TableCell colSpan={8} align="center">
                          No mail accounts configured
                        </TableCell>
                      </TableRow>
                    )}
                    {accounts.map((account) => (
                      <TableRow key={account.id} hover>
                        <TableCell>{account.name}</TableCell>
                        <TableCell>{account.host}:{account.port}</TableCell>
                        <TableCell>{account.username}</TableCell>
                        <TableCell>{account.security}</TableCell>
                        <TableCell>{account.pollIntervalMinutes}</TableCell>
                        <TableCell>
                          <Stack spacing={0.5}>
                            <Typography variant="caption">
                              {formatDateTime(account.lastFetchAt)}
                            </Typography>
                            {account.lastFetchStatus && (
                              <Tooltip
                                title={account.lastFetchError || ''}
                                disableHoverListener={!account.lastFetchError}
                              >
                                <Chip
                                  size="small"
                                  variant="outlined"
                                  label={account.lastFetchStatus}
                                  color={
                                    account.lastFetchStatus === 'SUCCESS'
                                      ? 'success'
                                      : account.lastFetchStatus === 'ERROR'
                                      ? 'error'
                                      : 'default'
                                  }
                                />
                              </Tooltip>
                            )}
                            {account.security === 'OAUTH2' && account.oauthConnected != null && (
                              <Chip
                                size="small"
                                variant="outlined"
                                color={account.oauthConnected ? 'success' : 'warning'}
                                label={account.oauthConnected ? 'OAuth connected' : 'OAuth not connected'}
                              />
                            )}
                            {account.lastFetchStatus === 'ERROR' && account.lastFetchError && (
                              <Typography variant="caption" color="error.main">
                                {summarizeError(account.lastFetchError)}
                              </Typography>
                            )}
                            {account.security === 'OAUTH2' && account.oauthEnvConfigured === false && (
                              <Tooltip title={formatMissingOAuthKeys(account.oauthMissingEnvKeys)}>
                                <Chip
                                  size="small"
                                  variant="outlined"
                                  color="warning"
                                  label="OAuth env missing"
                                />
                              </Tooltip>
                            )}
                          </Stack>
                        </TableCell>
                        <TableCell>
                          <Chip
                            size="small"
                            label={account.enabled ? 'Enabled' : 'Disabled'}
                            color={account.enabled ? 'success' : 'default'}
                            variant="outlined"
                          />
                        </TableCell>
                        <TableCell align="right">
                          {account.security === 'OAUTH2'
                            && account.oauthProvider
                            && account.oauthProvider !== 'CUSTOM'
                            && account.oauthConnected !== true && (
                              <Tooltip title="Connect OAuth">
                                <span>
                                  <IconButton
                                    size="small"
                                    onClick={() => handleConnectOAuth(account)}
                                    disabled={
                                      connectingAccountId === account.id
                                      || account.oauthEnvConfigured === false
                                    }
                                  >
                                    {connectingAccountId === account.id ? (
                                      <CircularProgress size={16} />
                                    ) : (
                                      <Login fontSize="small" />
                                    )}
                                  </IconButton>
                                </span>
                              </Tooltip>
                          )}
                          <Tooltip title="Test connection">
                            <span>
                              <IconButton
                                size="small"
                                onClick={() => handleTestConnection(account.id)}
                                disabled={
                                  testingAccountId === account.id
                                  || (account.security === 'OAUTH2' && account.oauthEnvConfigured === false)
                                }
                              >
                                {testingAccountId === account.id ? (
                                  <CircularProgress size={16} />
                                ) : (
                                  <Link fontSize="small" />
                                )}
                              </IconButton>
                            </span>
                          </Tooltip>
                          {account.security === 'OAUTH2' && account.oauthEnvConfigured === false && (
                            <Typography variant="caption" color="warning.main" display="block">
                              OAuth env missing — test connection disabled
                            </Typography>
                          )}
                          <Tooltip title="Edit">
                            <IconButton size="small" onClick={() => openEditAccount(account)}>
                              <Edit fontSize="small" />
                            </IconButton>
                          </Tooltip>
                          <Tooltip title="Delete">
                            <IconButton size="small" color="error" onClick={() => handleDeleteAccount(account.id)}>
                              <Delete fontSize="small" />
                            </IconButton>
                          </Tooltip>
                        </TableCell>
                      </TableRow>
                    ))}
                  </TableBody>
                </Table>
              </TableContainer>
            </CardContent>
          </Card>

          <Card variant="outlined">
            <CardContent>
              <Box display="flex" alignItems="center" justifyContent="space-between" mb={2}>
                <Typography variant="h6">Mail Rules</Typography>
                <Button variant="contained" startIcon={<Add />} onClick={openCreateRule}>
                  New Rule
                </Button>
              </Box>
              <Stack direction="row" spacing={1} flexWrap="wrap" sx={{ mb: 2 }}>
                <TextField
                  label="Search rules"
                  size="small"
                  value={ruleFilterText}
                  onChange={(event) => setRuleFilterText(event.target.value)}
                  sx={{ minWidth: 220 }}
                />
                <FormControl size="small" sx={{ minWidth: 180 }}>
                  <InputLabel>Account</InputLabel>
                  <Select
                    label="Account"
                    value={ruleFilterAccountId}
                    onChange={(event) => setRuleFilterAccountId(String(event.target.value))}
                  >
                    <MenuItem value="">All accounts</MenuItem>
                    {accounts.map((account) => (
                      <MenuItem key={account.id} value={account.id}>
                        {account.name}
                      </MenuItem>
                    ))}
                  </Select>
                </FormControl>
                <FormControl size="small" sx={{ minWidth: 140 }}>
                  <InputLabel>Status</InputLabel>
                  <Select
                    label="Status"
                    value={ruleFilterStatus}
                    onChange={(event) => setRuleFilterStatus(event.target.value as 'ALL' | 'ENABLED' | 'DISABLED')}
                  >
                    <MenuItem value="ALL">All</MenuItem>
                    <MenuItem value="ENABLED">Enabled</MenuItem>
                    <MenuItem value="DISABLED">Disabled</MenuItem>
                  </Select>
                </FormControl>
                <Button
                  variant="outlined"
                  size="small"
                  onClick={() => {
                    setRuleFilterText('');
                    setRuleFilterAccountId('');
                    setRuleFilterStatus('ALL');
                  }}
                >
                  Clear filters
                </Button>
                <Button
                  variant="outlined"
                  size="small"
                  disabled={selectedRuleIds.length === 0 || ruleBulkBusy}
                  onClick={() => handleBulkSetRuleEnabled(true)}
                >
                  {ruleBulkUpdating ? 'Updating...' : 'Enable selected'}
                </Button>
                <Button
                  variant="outlined"
                  size="small"
                  disabled={selectedRuleIds.length === 0 || ruleBulkBusy}
                  onClick={() => handleBulkSetRuleEnabled(false)}
                >
                  Disable selected
                </Button>
                <Button
                  variant="outlined"
                  size="small"
                  disabled={selectedRuleIds.length === 0 || ruleBulkBusy}
                  onClick={handleBulkReindexRules}
                >
                  {ruleBulkReindexing ? 'Reindexing...' : 'Reindex selected'}
                </Button>
                <Button
                  variant="outlined"
                  color="error"
                  size="small"
                  disabled={selectedRuleIds.length === 0 || ruleBulkBusy}
                  onClick={handleBulkDeleteRules}
                >
                  {ruleBulkDeleting ? 'Deleting...' : 'Delete selected'}
                </Button>
                <Chip
                  size="small"
                  variant="outlined"
                  label={`Selected ${selectedRuleIds.length}`}
                />
              </Stack>
              <TableContainer>
                <Table size="small">
                  <TableHead>
                    <TableRow>
                      <TableCell padding="checkbox">
                        <Checkbox
                          size="small"
                          checked={allFilteredSelected}
                          indeterminate={selectedFilteredCount > 0 && !allFilteredSelected}
                          onChange={(event) => handleToggleSelectAllFilteredRules(event.target.checked)}
                          inputProps={{ 'aria-label': 'select all filtered rules' }}
                        />
                      </TableCell>
                      <TableCell>Name</TableCell>
                      <TableCell>Account</TableCell>
                      <TableCell>Priority</TableCell>
                      <TableCell>Status</TableCell>
                      <TableCell>Filters</TableCell>
                      <TableCell>Processing</TableCell>
                      <TableCell>Tag</TableCell>
                      <TableCell>Target Folder</TableCell>
                      <TableCell align="right">Actions</TableCell>
                    </TableRow>
                  </TableHead>
                  <TableBody>
                    {rules.length === 0 && (
                      <TableRow>
                      <TableCell colSpan={10} align="center">
                        No mail rules configured
                      </TableCell>
                    </TableRow>
                    )}
                    {rules.length > 0 && filteredRules.length === 0 && (
                      <TableRow>
                        <TableCell colSpan={10} align="center">
                          No rules match current filters
                        </TableCell>
                      </TableRow>
                    )}
                    {filteredRules.map((rule) => (
                      <TableRow key={rule.id} hover>
                      <TableCell padding="checkbox">
                        <Checkbox
                          size="small"
                          checked={selectedRuleIds.includes(rule.id)}
                          onChange={() => handleToggleRuleSelection(rule.id)}
                        />
                      </TableCell>
                      <TableCell>{rule.name}</TableCell>
                      <TableCell>{rule.accountId ? accountNameById.get(rule.accountId) : 'All accounts'}</TableCell>
                      <TableCell>{rule.priority}</TableCell>
                        <TableCell>
                          <Chip
                            size="small"
                            label={(rule.enabled ?? true) ? 'Enabled' : 'Disabled'}
                            color={(rule.enabled ?? true) ? 'success' : 'default'}
                            variant="outlined"
                          />
                        </TableCell>
                      <TableCell>
                        <Box display="flex" flexDirection="column" gap={0.5}>
                          <Typography variant="caption">Mailbox: {rule.folder || 'INBOX'}</Typography>
                            {rule.subjectFilter && <Typography variant="caption">Subject: {rule.subjectFilter}</Typography>}
                            {rule.fromFilter && <Typography variant="caption">From: {rule.fromFilter}</Typography>}
                            {rule.toFilter && <Typography variant="caption">To: {rule.toFilter}</Typography>}
                            {rule.bodyFilter && <Typography variant="caption">Body: {rule.bodyFilter}</Typography>}
                            {rule.attachmentFilenameInclude && (
                              <Typography variant="caption">Attach: {rule.attachmentFilenameInclude}</Typography>
                            )}
                            {rule.attachmentFilenameExclude && (
                              <Typography variant="caption">Exclude: {rule.attachmentFilenameExclude}</Typography>
                            )}
                            {rule.maxAgeDays && rule.maxAgeDays > 0 && (
                              <Typography variant="caption">Max age: {rule.maxAgeDays}d</Typography>
                            )}
                            {rule.includeInlineAttachments && (
                              <Typography variant="caption">Inline: yes</Typography>
                            )}
                          </Box>
                        </TableCell>
                        <TableCell>
                          <Box display="flex" flexDirection="column" gap={0.5}>
                            <Typography variant="caption">
                              Process: {actionTypeLabels[rule.actionType] ?? rule.actionType}
                            </Typography>
                            <Typography variant="caption">
                              Mail: {rule.mailAction || 'MARK_READ'}
                              {rule.mailActionParam ? ` (${rule.mailActionParam})` : ''}
                            </Typography>
                          </Box>
                        </TableCell>
                        <TableCell>{rule.assignTagId ? tagNameById.get(rule.assignTagId) : '-'}</TableCell>
                        <TableCell>{rule.assignFolderId || '-'}</TableCell>
                        <TableCell
                          align="right"
                          sx={{ position: 'sticky', right: 0, backgroundColor: 'background.paper', zIndex: 1 }}
                        >
                          <Tooltip title="Move up">
                            <span>
                              <IconButton
                                size="small"
                                disabled={sortedRules.findIndex((item) => item.id === rule.id) === 0}
                                onClick={() => handleMoveRule(rule, 'up')}
                              >
                                <ArrowUpward fontSize="small" />
                              </IconButton>
                            </span>
                          </Tooltip>
                          <Tooltip title="Move down">
                            <span>
                              <IconButton
                                size="small"
                                disabled={sortedRules.findIndex((item) => item.id === rule.id) === sortedRules.length - 1}
                                onClick={() => handleMoveRule(rule, 'down')}
                              >
                                <ArrowDownward fontSize="small" />
                              </IconButton>
                            </span>
                          </Tooltip>
                          <Tooltip title={(rule.enabled ?? true) ? 'Disable' : 'Enable'}>
                            <span>
                              <Checkbox
                                size="small"
                                checked={rule.enabled ?? true}
                                onChange={() => handleToggleRuleEnabled(rule)}
                              />
                            </span>
                          </Tooltip>
                          <Tooltip title="Preview">
                            <span>
                              <IconButton
                                size="small"
                                aria-label={`Preview rule ${rule.name}`}
                                onClick={() => openPreviewRule(rule)}
                              >
                                <Visibility fontSize="small" />
                              </IconButton>
                            </span>
                          </Tooltip>
                          <Tooltip title="Edit">
                            <IconButton size="small" onClick={() => openEditRule(rule)}>
                              <Edit fontSize="small" />
                            </IconButton>
                          </Tooltip>
                          <Tooltip title="Delete">
                            <IconButton size="small" color="error" onClick={() => handleDeleteRule(rule.id)}>
                              <Delete fontSize="small" />
                            </IconButton>
                          </Tooltip>
                        </TableCell>
                      </TableRow>
                    ))}
                  </TableBody>
                </Table>
              </TableContainer>
            </CardContent>
          </Card>
        </Stack>
      )}

      <Drawer
        anchor="right"
        open={ruleDiagnosticsDrawerOpen}
        onClose={closeRuleDiagnosticsDrawer}
      >
        <Box sx={{ width: { xs: 360, sm: 480 }, p: 2 }} role="presentation">
          <Stack spacing={2}>
            <Box display="flex" alignItems="center" justifyContent="space-between" gap={1}>
              <Typography variant="h6">
                Rule Diagnostics
              </Typography>
              <Button size="small" variant="text" onClick={closeRuleDiagnosticsDrawer}>
                Close
              </Button>
            </Box>
            <Typography variant="body2" color="text.secondary">
              {ruleDiagnosticsFocusRule
                ? `${ruleDiagnosticsFocusRule.name} (${ruleDiagnosticsFocusRule.id})`
                : ruleDiagnosticsFocusRuleId || 'No rule selected'}
            </Typography>
            <Stack direction="row" flexWrap="wrap" gap={1}>
              <Chip size="small" label={`Matched ${ruleDiagnosticsFilteredItems.length}`} />
              <Chip size="small" color="success" label={`Processed ${ruleDiagnosticsProcessedCount}`} />
              <Chip size="small" color="error" label={`Errors ${ruleDiagnosticsErrorCount}`} />
            </Stack>
            <Stack spacing={1}>
              <FormControl size="small" fullWidth>
                <InputLabel id="rule-diagnostics-account-label">Account</InputLabel>
                <Select
                  labelId="rule-diagnostics-account-label"
                  label="Account"
                  value={ruleDiagnosticsAccountId}
                  onChange={(event) => setRuleDiagnosticsAccountId(String(event.target.value))}
                >
                  <MenuItem value="">
                    <em>All Accounts</em>
                  </MenuItem>
                  {accounts.map((account) => (
                    <MenuItem key={account.id} value={account.id}>{account.name}</MenuItem>
                  ))}
                </Select>
              </FormControl>
              <FormControl size="small" fullWidth>
                <InputLabel id="rule-diagnostics-status-label">Status</InputLabel>
                <Select
                  labelId="rule-diagnostics-status-label"
                  label="Status"
                  value={ruleDiagnosticsStatus}
                  onChange={(event) => setRuleDiagnosticsStatus(event.target.value as 'ALL' | 'ERROR' | 'PROCESSED')}
                >
                  <MenuItem value="ALL">All</MenuItem>
                  <MenuItem value="PROCESSED">Processed</MenuItem>
                  <MenuItem value="ERROR">Error</MenuItem>
                </Select>
              </FormControl>
              <FormControl size="small" fullWidth>
                <InputLabel id="rule-diagnostics-range-label">Time range</InputLabel>
                <Select
                  labelId="rule-diagnostics-range-label"
                  label="Time range"
                  value={ruleDiagnosticsTimeRange}
                  onChange={(event) => setRuleDiagnosticsTimeRange(event.target.value as RuleDiagnosticsTimeRange)}
                >
                  {RULE_DIAGNOSTICS_TIME_RANGES.map((option) => (
                    <MenuItem key={option.value} value={option.value}>{option.label}</MenuItem>
                  ))}
                </Select>
              </FormControl>
            </Stack>
            <Box display="flex" justifyContent="space-between" gap={1} flexWrap="wrap">
              <Button size="small" variant="outlined" onClick={applyRuleDiagnosticsToTableFilters}>
                Apply To Main Filters
              </Button>
              <Button
                size="small"
                variant="text"
                onClick={() => {
                  setRuleDiagnosticsAccountId('');
                  setRuleDiagnosticsStatus('ALL');
                  setRuleDiagnosticsTimeRange('7D');
                }}
              >
                Reset
              </Button>
            </Box>
            <Box>
              <Typography variant="subtitle2" gutterBottom>
                Skip Reasons
              </Typography>
              {ruleDiagnosticsSkipReasons.length === 0 ? (
                <Typography variant="caption" color="text.secondary">
                  No skip reasons in current filtered window.
                </Typography>
              ) : (
                <Stack direction="row" flexWrap="wrap" gap={1}>
                  {ruleDiagnosticsSkipReasons.map(([reason, count]) => (
                    <Chip
                      key={reason}
                      size="small"
                      variant="outlined"
                      label={`${formatReason(reason)} (${count})`}
                    />
                  ))}
                </Stack>
              )}
            </Box>
            <Box>
              <Typography variant="subtitle2" gutterBottom>
                Recent Failure Samples
              </Typography>
              {ruleDiagnosticsRecentFailures.length === 0 ? (
                <Typography variant="caption" color="text.secondary">
                  No failure samples found for the selected filter set.
                </Typography>
              ) : (
                <Stack spacing={1}>
                  {ruleDiagnosticsRecentFailures.map((sample) => (
                    <Box
                      key={sample.id}
                      sx={{
                        border: '1px solid',
                        borderColor: 'divider',
                        borderRadius: 1,
                        p: 1,
                      }}
                    >
                      <Stack direction="row" spacing={1} alignItems="center" flexWrap="wrap" gap={1}>
                        <Chip size="small" color={statusColor(sample.status)} label={sample.status} />
                        <Typography variant="caption" color="text.secondary">
                          {formatDateTime(sample.processedAt)}
                        </Typography>
                        <Typography variant="caption" color="text.secondary">
                          UID {sample.uid}
                        </Typography>
                      </Stack>
                      <Typography variant="body2" sx={{ mt: 0.5 }} title={sample.subject || ''}>
                        {summarizeText(sample.subject, 100) || '-'}
                      </Typography>
                      <Typography
                        variant="caption"
                        color="text.secondary"
                        sx={{ mt: 0.5, display: 'block' }}
                        title={sample.errorMessage || ''}
                      >
                        {summarizeError(sample.errorMessage, 180) || 'No error message'}
                      </Typography>
                      <Box mt={0.5}>
                        <Button size="small" variant="text" onClick={() => copyDiagnosticsRuleErrorMessage(sample)}>
                          Copy Error
                        </Button>
                      </Box>
                    </Box>
                  ))}
                </Stack>
              )}
            </Box>
          </Stack>
        </Box>
      </Drawer>

      <Dialog open={accountDialogOpen} onClose={() => setAccountDialogOpen(false)} maxWidth="sm" fullWidth>
        <DialogTitle>{editingAccount ? 'Edit Mail Account' : 'New Mail Account'}</DialogTitle>
        <DialogContent>
          <Stack spacing={2} mt={1}>
            <TextField
              label="Name"
              value={accountForm.name}
              onChange={(event) => setAccountForm({ ...accountForm, name: event.target.value })}
              size="small"
              fullWidth
            />
            <TextField
              label="Host"
              value={accountForm.host}
              onChange={(event) => setAccountForm({ ...accountForm, host: event.target.value })}
              size="small"
              fullWidth
            />
            <TextField
              label="Port"
              type="number"
              value={accountForm.port}
              onChange={(event) => setAccountForm({ ...accountForm, port: Number(event.target.value) })}
              size="small"
              fullWidth
            />
            <TextField
              label="Username"
              value={accountForm.username}
              onChange={(event) => setAccountForm({ ...accountForm, username: event.target.value })}
              size="small"
              fullWidth
            />
            {!isOauthAccount && (
              <TextField
                label={editingAccount ? 'Password (leave masked or blank to keep)' : 'Password'}
                type="password"
                value={accountForm.password}
                onChange={(event) => setAccountForm({ ...accountForm, password: event.target.value })}
                size="small"
                helperText={editingAccount ? 'Keep the masked value or clear the field to retain the current password.' : undefined}
                fullWidth
              />
            )}
            <FormControl size="small" fullWidth>
              <InputLabel id="mail-security-label">Security</InputLabel>
              <Select
                labelId="mail-security-label"
                value={accountForm.security}
                label="Security"
                onChange={(event) => setAccountForm({
                  ...accountForm,
                  security: event.target.value as MailSecurityType,
                })}
              >
                {securityOptions.map((option) => (
                  <MenuItem key={option} value={option}>{option}</MenuItem>
                ))}
              </Select>
            </FormControl>
            {isOauthAccount && (
              <>
                <FormControl size="small" fullWidth>
                  <InputLabel id="oauth-provider-label">OAuth provider</InputLabel>
                  <Select
                    labelId="oauth-provider-label"
                    value={accountForm.oauthProvider || 'CUSTOM'}
                    label="OAuth provider"
                    onChange={(event) => handleOauthProviderChange(event.target.value as MailOAuthProvider)}
                  >
                    {oauthProviderOptions.map((option) => (
                      <MenuItem key={option} value={option}>{option}</MenuItem>
                    ))}
                  </Select>
                </FormControl>
                <TextField
                  label={accountForm.oauthProvider === 'CUSTOM'
                    ? 'OAuth credential key'
                    : 'OAuth credential key (optional)'}
                  value={accountForm.oauthCredentialKey}
                  onChange={(event) => setAccountForm({ ...accountForm, oauthCredentialKey: event.target.value })}
                  size="small"
                  helperText={hasCredentialKey
                    ? `Server env prefix: ${oauthEnvPrefix}`
                    : usesProviderEnv
                      ? `Using OAuth app env: ${providerEnvPrefix}CLIENT_ID / ${providerEnvPrefix}CLIENT_SECRET`
                      : `Server env prefix: ${oauthEnvPrefix}`}
                  fullWidth
                />
                {accountForm.oauthProvider === 'CUSTOM' && (
                  <TextField
                    label="OAuth token endpoint (custom)"
                    value={accountForm.oauthTokenEndpoint}
                    onChange={(event) => setAccountForm({ ...accountForm, oauthTokenEndpoint: event.target.value })}
                    size="small"
                    fullWidth
                  />
                )}
                {accountForm.oauthProvider === 'MICROSOFT' && (
                  <TextField
                    label="OAuth tenant ID (Microsoft)"
                    value={accountForm.oauthTenantId}
                    onChange={(event) => setAccountForm({ ...accountForm, oauthTenantId: event.target.value })}
                    size="small"
                    fullWidth
                  />
                )}
                <TextField
                  label="OAuth scope"
                  value={accountForm.oauthScope}
                  onChange={(event) => setAccountForm({ ...accountForm, oauthScope: event.target.value })}
                  size="small"
                  fullWidth
                />
                {hasCredentialKey && (
                  <>
                    <Typography variant="caption" color="text.secondary">
                      OAuth credentials are loaded from server environment variables:
                      {' '}
                      {oauthEnvPrefix}CLIENT_ID,
                      {' '}
                      {oauthEnvPrefix}CLIENT_SECRET,
                      {' '}
                      {oauthEnvPrefix}REFRESH_TOKEN
                    </Typography>
                    <TextField
                      label="OAuth client ID (from env)"
                      value="********"
                      size="small"
                      fullWidth
                      disabled
                    />
                    <TextField
                      label="OAuth client secret (from env)"
                      value="********"
                      size="small"
                      fullWidth
                      disabled
                    />
                    <TextField
                      label="OAuth refresh token (from env)"
                      value="********"
                      size="small"
                      fullWidth
                      disabled
                    />
                  </>
                )}
                {usesProviderEnv && (
                  <>
                    <Typography variant="caption" color="text.secondary">
                      OAuth app credentials are loaded from server environment variables:
                      {' '}
                      {providerEnvPrefix}CLIENT_ID,
                      {' '}
                      {providerEnvPrefix}CLIENT_SECRET.
                      Refresh tokens are stored after connecting.
                    </Typography>
                    <TextField
                      label="OAuth client ID (from env)"
                      value="********"
                      size="small"
                      fullWidth
                      disabled
                    />
                    <TextField
                      label="OAuth client secret (from env)"
                      value="********"
                      size="small"
                      fullWidth
                      disabled
                    />
                  </>
                )}
              </>
            )}
            <FormControl size="small" fullWidth>
              <InputLabel id="mail-enabled-label">Status</InputLabel>
              <Select
                labelId="mail-enabled-label"
                value={accountForm.enabled ? 'enabled' : 'disabled'}
                label="Status"
                onChange={(event) => setAccountForm({
                  ...accountForm,
                  enabled: event.target.value === 'enabled',
                })}
              >
                <MenuItem value="enabled">Enabled</MenuItem>
                <MenuItem value="disabled">Disabled</MenuItem>
              </Select>
            </FormControl>
            <TextField
              label="Poll interval (minutes)"
              type="number"
              value={accountForm.pollIntervalMinutes}
              onChange={(event) => setAccountForm({
                ...accountForm,
                pollIntervalMinutes: Number(event.target.value),
              })}
              size="small"
              fullWidth
            />
          </Stack>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setAccountDialogOpen(false)}>Cancel</Button>
          <Button variant="contained" onClick={handleSaveAccount}>Save</Button>
        </DialogActions>
      </Dialog>

      <Dialog open={ruleDialogOpen} onClose={() => setRuleDialogOpen(false)} maxWidth="sm" fullWidth>
        <DialogTitle>{editingRule ? 'Edit Mail Rule' : 'New Mail Rule'}</DialogTitle>
        <DialogContent>
          <Stack spacing={2} mt={1}>
            <TextField
              label="Name"
              value={ruleForm.name}
              onChange={(event) => setRuleForm({ ...ruleForm, name: event.target.value })}
              size="small"
              fullWidth
            />
            <FormControl size="small" fullWidth>
              <InputLabel id="mail-account-label">Account</InputLabel>
              <Select
                labelId="mail-account-label"
                value={ruleForm.accountId || ''}
                label="Account"
                onChange={(event) => setRuleForm({ ...ruleForm, accountId: event.target.value })}
              >
                <MenuItem value="">
                  <em>All accounts</em>
                </MenuItem>
                {accounts.map((account) => (
                  <MenuItem key={account.id} value={account.id}>{account.name}</MenuItem>
                ))}
              </Select>
            </FormControl>
            <TextField
              label="Priority"
              type="number"
              value={ruleForm.priority}
              onChange={(event) => setRuleForm({ ...ruleForm, priority: Number(event.target.value) })}
              size="small"
              fullWidth
            />
            <FormControlLabel
              control={
                <Checkbox
                  checked={ruleForm.enabled ?? true}
                  onChange={(event) => setRuleForm({ ...ruleForm, enabled: event.target.checked })}
                />
              }
              label="Enabled"
            />
            <TextField
              label="Mailbox folder"
              value={ruleForm.folder}
              onChange={(event) => setRuleForm({ ...ruleForm, folder: event.target.value })}
              size="small"
              helperText="IMAP folder name(s), comma-separated, default INBOX"
              fullWidth
            />
            <TextField
              label="Subject filter (regex)"
              value={ruleForm.subjectFilter}
              onChange={(event) => setRuleForm({ ...ruleForm, subjectFilter: event.target.value })}
              size="small"
              fullWidth
            />
            <TextField
              label="From filter (regex)"
              value={ruleForm.fromFilter}
              onChange={(event) => setRuleForm({ ...ruleForm, fromFilter: event.target.value })}
              size="small"
              fullWidth
            />
            <TextField
              label="To filter (regex)"
              value={ruleForm.toFilter}
              onChange={(event) => setRuleForm({ ...ruleForm, toFilter: event.target.value })}
              size="small"
              fullWidth
            />
            <TextField
              label="Body filter (regex)"
              value={ruleForm.bodyFilter}
              onChange={(event) => setRuleForm({ ...ruleForm, bodyFilter: event.target.value })}
              size="small"
              fullWidth
            />
            <TextField
              label="Attachment filename include"
              value={ruleForm.attachmentFilenameInclude}
              onChange={(event) => setRuleForm({ ...ruleForm, attachmentFilenameInclude: event.target.value })}
              size="small"
              helperText="Wildcard supported, e.g. *.pdf"
              fullWidth
            />
            <TextField
              label="Attachment filename exclude"
              value={ruleForm.attachmentFilenameExclude}
              onChange={(event) => setRuleForm({ ...ruleForm, attachmentFilenameExclude: event.target.value })}
              size="small"
              helperText="Wildcard supported, e.g. *secret*"
              fullWidth
            />
            <TextField
              label="Max age (days)"
              type="number"
              value={ruleForm.maxAgeDays}
              onChange={(event) => setRuleForm({ ...ruleForm, maxAgeDays: Number(event.target.value) })}
              size="small"
              helperText="0 to disable"
              fullWidth
            />
            <FormControlLabel
              control={
                <Checkbox
                  checked={ruleForm.includeInlineAttachments || false}
                  onChange={(event) => setRuleForm({
                    ...ruleForm,
                    includeInlineAttachments: event.target.checked,
                  })}
                />
              }
              label="Include inline attachments"
            />
            <FormControl size="small" fullWidth>
              <InputLabel id="mail-action-label">Processing scope</InputLabel>
              <Select
                labelId="mail-action-label"
                value={ruleForm.actionType || 'ATTACHMENTS_ONLY'}
                label="Processing scope"
                onChange={(event) => setRuleForm({
                  ...ruleForm,
                  actionType: event.target.value as MailActionType,
                })}
              >
                {actionOptions.map((option) => (
                  <MenuItem key={option} value={option}>{actionTypeLabels[option]}</MenuItem>
                ))}
              </Select>
              <FormHelperText>{actionTypeDescriptions[ruleForm.actionType || 'ATTACHMENTS_ONLY']}</FormHelperText>
            </FormControl>
            <FormControl size="small" fullWidth>
              <InputLabel id="mail-post-action-label">Mail action</InputLabel>
              <Select
                labelId="mail-post-action-label"
                value={ruleForm.mailAction || 'MARK_READ'}
                label="Mail action"
                onChange={(event) => setRuleForm({
                  ...ruleForm,
                  mailAction: event.target.value as MailPostAction,
                })}
              >
                {postActionOptions.map((option) => (
                  <MenuItem key={option} value={option}>{option}</MenuItem>
                ))}
              </Select>
            </FormControl>
            <TextField
              label="Mail action parameter"
              value={ruleForm.mailActionParam}
              onChange={(event) => setRuleForm({ ...ruleForm, mailActionParam: event.target.value })}
              size="small"
              helperText={mailActionHelper}
              disabled={!['MOVE', 'TAG'].includes(ruleForm.mailAction || '')}
              fullWidth
            />
            <FormControl size="small" fullWidth>
              <InputLabel id="mail-tag-label">Assign tag</InputLabel>
              <Select
                labelId="mail-tag-label"
                value={ruleForm.assignTagId || ''}
                label="Assign tag"
                onChange={(event) => setRuleForm({ ...ruleForm, assignTagId: event.target.value })}
              >
                <MenuItem value="">
                  <em>None</em>
                </MenuItem>
                {tags.map((tag) => (
                  <MenuItem key={tag.id} value={tag.id}>{tag.name}</MenuItem>
                ))}
              </Select>
            </FormControl>
            <TextField
              label="Target folder path"
              value={ruleForm.folderPath}
              onChange={(event) => setRuleForm({ ...ruleForm, folderPath: event.target.value })}
              size="small"
              helperText="Optional. Example: /Root/Inbox"
              fullWidth
            />
            <TextField
              label="Target folder ID"
              value={ruleForm.folderIdOverride}
              onChange={(event) => setRuleForm({ ...ruleForm, folderIdOverride: event.target.value })}
              size="small"
              helperText="Optional UUID override"
              fullWidth
            />
            {ruleForm.assignTagId && selectedTag && (
              <Typography variant="caption" color="text.secondary">
                Selected tag: {selectedTag.name}
              </Typography>
            )}
          </Stack>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setRuleDialogOpen(false)}>Cancel</Button>
          <Button variant="contained" onClick={handleSaveRule}>Save</Button>
        </DialogActions>
      </Dialog>

      <Dialog open={previewDialogOpen} onClose={closePreviewDialog} maxWidth="md" fullWidth>
        <DialogTitle>Preview Mail Rule</DialogTitle>
        <DialogContent>
          <Stack spacing={2} mt={1}>
            <Typography variant="subtitle2">
              Rule: {previewRule?.name || '—'}
            </Typography>
            {previewRule?.accountId ? (
              <TextField
                label="Account"
                value={accountNameById.get(previewRule.accountId) || previewRule.accountId}
                size="small"
                fullWidth
                disabled
              />
            ) : (
              <FormControl size="small" fullWidth>
                <InputLabel id="preview-account-label">Account</InputLabel>
                <Select
                  labelId="preview-account-label"
                  value={previewAccountId}
                  label="Account"
                  onChange={(event) => setPreviewAccountId(event.target.value)}
                >
                  {accounts.map((account) => (
                    <MenuItem key={account.id} value={account.id}>{account.name}</MenuItem>
                  ))}
                </Select>
              </FormControl>
            )}
            <TextField
              label="Max messages per folder"
              type="number"
              value={previewMaxMessages}
              onChange={(event) => setPreviewMaxMessages(Number(event.target.value))}
              size="small"
              fullWidth
            />
            <Box display="flex" alignItems="center" gap={1}>
              <Button variant="outlined" onClick={handleRunRulePreview} disabled={previewLoading}>
                {previewLoading ? 'Running...' : 'Run Preview'}
              </Button>
              {previewError && (
                <Typography variant="caption" color="error">
                  {previewError}
                </Typography>
              )}
            </Box>

            {previewResult && (
              <Stack spacing={2}>
                <Box>
                  <Typography variant="subtitle2">Summary</Typography>
                  <Box display="flex" flexWrap="wrap" gap={1} mt={1}>
                    <Chip size="small" label={`Found ${previewResult.foundMessages}`} />
                    <Chip size="small" label={`Scanned ${previewResult.scannedMessages}`} />
                    <Chip size="small" label={`Matched ${previewResult.matchedMessages}`} color="success" />
                    <Chip size="small" label={`Processable ${previewResult.processableMessages}`} />
                    <Chip size="small" label={`Skipped ${previewResult.skippedMessages}`} />
                    <Chip size="small" label={`Errors ${previewResult.errorMessages}`} color="warning" />
                  </Box>
                </Box>
                {previewResult.skipReasons && Object.keys(previewResult.skipReasons).length > 0 && (
                  <Box>
                    <Typography variant="subtitle2">Skip reasons</Typography>
                    <Box display="flex" flexWrap="wrap" gap={1} mt={1}>
                      {toSortedEntries(previewResult.skipReasons).map(([reason, count]) => (
                        <Chip key={reason} size="small" label={`${formatReason(reason)}: ${count}`} />
                      ))}
                    </Box>
                  </Box>
                )}
                <Box>
                  <Box display="flex" alignItems="center" justifyContent="space-between">
                    <Typography variant="subtitle2">Matched messages</Typography>
                    <Box display="flex" alignItems="center" gap={1}>
                      <FormControl size="small" sx={{ minWidth: 170 }}>
                        <InputLabel id="preview-processable-label">Processable</InputLabel>
                        <Select
                          labelId="preview-processable-label"
                          label="Processable"
                          value={previewProcessableFilter}
                          onChange={(event) =>
                            setPreviewProcessableFilter(event.target.value as typeof previewProcessableFilter)
                          }
                        >
                          <MenuItem value="ALL">All</MenuItem>
                          <MenuItem value="PROCESSABLE">Processable only</MenuItem>
                          <MenuItem value="UNPROCESSABLE">Not processable</MenuItem>
                        </Select>
                      </FormControl>
                      <ButtonGroup variant="outlined" size="small">
                        <Button startIcon={<ContentCopy />} onClick={handleCopyPreviewJson}>
                          Copy JSON
                        </Button>
                      </ButtonGroup>
                    </Box>
                  </Box>
                  {filteredPreviewMatches.length === 0 ? (
                    <Typography variant="body2" color="text.secondary" mt={1}>
                      No messages matched this rule in the scanned sample.
                    </Typography>
                  ) : (
                    <TableContainer sx={{ mt: 1 }}>
                      <Table size="small">
                        <TableHead>
                          <TableRow>
                            <TableCell>Folder</TableCell>
                            <TableCell>Subject</TableCell>
                            <TableCell>From</TableCell>
                            <TableCell>To</TableCell>
                            <TableCell>Received</TableCell>
                            <TableCell align="right">Attachments</TableCell>
                            <TableCell align="right">Processable</TableCell>
                          </TableRow>
                        </TableHead>
                        <TableBody>
                          {filteredPreviewMatches.map((item) => (
                            <TableRow key={`${item.folder}-${item.uid}`} hover>
                              <TableCell>{item.folder}</TableCell>
                              <TableCell>{item.subject || '-'}</TableCell>
                              <TableCell>{item.from || '-'}</TableCell>
                              <TableCell>{item.recipients || '-'}</TableCell>
                              <TableCell>{item.receivedAt ? new Date(item.receivedAt).toLocaleString() : '-'}</TableCell>
                              <TableCell align="right">{item.attachmentCount}</TableCell>
                              <TableCell align="right">
                                <Chip
                                  size="small"
                                  label={item.processable ? 'Yes' : 'No'}
                                  color={item.processable ? 'success' : 'default'}
                                  variant="outlined"
                                />
                              </TableCell>
                            </TableRow>
                          ))}
                        </TableBody>
                      </Table>
                    </TableContainer>
                  )}
                </Box>
              </Stack>
            )}
          </Stack>
        </DialogContent>
        <DialogActions>
          <Button onClick={closePreviewDialog}>Close</Button>
        </DialogActions>
      </Dialog>
    </Box>
  );
};

export default MailAutomationPage;
