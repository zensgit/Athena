import React, { useCallback, useEffect, useMemo, useState } from 'react';
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
  FormControl,
  FormControlLabel,
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
import { Add, Delete, Edit, Link, Login, Refresh, Visibility, ContentCopy } from '@mui/icons-material';
import { toast } from 'react-toastify';
import { useLocation, useNavigate } from 'react-router-dom';
import mailAutomationService, {
  MailAccount,
  MailAccountRequest,
  MailConnectionTestResult,
  MailDiagnosticsResult,
  MailFetchDebugResult,
  MailFetchSummary,
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
  const [listingFolders, setListingFolders] = useState(false);
  const [availableFolders, setAvailableFolders] = useState<string[]>([]);
  const [diagnostics, setDiagnostics] = useState<MailDiagnosticsResult | null>(null);
  const [diagnosticsLoading, setDiagnosticsLoading] = useState(false);
  const [diagnosticsAccountId, setDiagnosticsAccountId] = useState('');
  const [diagnosticsRuleId, setDiagnosticsRuleId] = useState('');
  const [diagnosticsStatus, setDiagnosticsStatus] = useState('');
  const [diagnosticsSubject, setDiagnosticsSubject] = useState('');
  const [processedRetention, setProcessedRetention] = useState<ProcessedMailRetentionStatus | null>(null);
  const [retentionLoading, setRetentionLoading] = useState(false);
  const [retentionCleaning, setRetentionCleaning] = useState(false);
  const [selectedProcessedIds, setSelectedProcessedIds] = useState<string[]>([]);
  const [connectingAccountId, setConnectingAccountId] = useState<string | null>(null);
  const [lastFetchSummary, setLastFetchSummary] = useState<MailFetchSummary | null>(null);
  const [lastFetchAt, setLastFetchAt] = useState<string | null>(null);
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

  const statusColor = (status?: string | null): 'default' | 'success' | 'error' => {
    if (status === 'ERROR') {
      return 'error';
    }
    if (status === 'PROCESSED') {
      return 'success';
    }
    return 'default';
  };

  const toSortedEntries = (map?: Record<string, number> | null) =>
    Object.entries(map || {}).sort((a, b) => b[1] - a[1]);

  const formatReason = (reason: string) => reason.replace(/_/g, ' ');

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
      const [accountList, ruleList, tagList] = await Promise.all([
        mailAutomationService.listAccounts(),
        mailAutomationService.listRules(),
        tagService.getAllTags(),
      ]);
      setAccounts(accountList);
      setRules(ruleList);
      setTags(tagList.map((tag) => ({ id: tag.id, name: tag.name })));
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
  }, [diagnosticsAccountId, diagnosticsRuleId, diagnosticsStatus, diagnosticsSubject]);

  useEffect(() => {
    loadAll();
  }, [loadAll]);

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
    loadDiagnostics({ silent: true });
  }, [diagnosticsAccountId, diagnosticsRuleId, diagnosticsStatus, diagnosticsSubject, loadDiagnostics]);

  useEffect(() => {
    if (!folderAccountId && accounts.length > 0) {
      setFolderAccountId(accounts[0].id);
    }
  }, [accounts, folderAccountId]);

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

  const recentProcessed: ProcessedMailDiagnosticItem[] = diagnostics?.recentProcessed ?? [];
  const recentDocuments: MailDocumentDiagnosticItem[] = diagnostics?.recentDocuments ?? [];
  const exportDisabled = !exportOptions.includeProcessed && !exportOptions.includeDocuments;
  const retentionEnabled = processedRetention?.enabled ?? false;
  const retentionDays = processedRetention?.retentionDays ?? 0;
  const retentionExpiredCount = processedRetention?.expiredCount ?? 0;
  const allProcessedSelected = recentProcessed.length > 0
    && recentProcessed.every((item) => selectedProcessedIds.includes(item.id));
  const someProcessedSelected = selectedProcessedIds.length > 0 && !allProcessedSelected;

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
      toast.success(`Found ${folders.length} folders`);
    } catch {
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
      password: '',
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

              {availableFolders.length > 0 && (
                <Box sx={{ mb: 2 }}>
                  <Typography variant="caption" color="text.secondary">
                    Available folders ({availableFolders.length})
                  </Typography>
                  <Stack direction="row" spacing={1} sx={{ mt: 0.5, flexWrap: 'wrap', gap: 1 }}>
                    {availableFolders.slice(0, 40).map((folder) => (
                      <Chip key={`folder-${folder}`} size="small" variant="outlined" label={folder} />
                    ))}
                  </Stack>
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
                    <TextField
                      size="small"
                      label="Subject contains"
                      value={diagnosticsSubject}
                      onChange={(event) => setDiagnosticsSubject(event.target.value)}
                      sx={{ minWidth: 220 }}
                    />
                  </Stack>
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
                              <TableCell>Subject</TableCell>
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
                                  <Typography variant="caption" title={item.subject || ''}>
                                    {summarizeText(item.subject) || '-'}
                                  </Typography>
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
                                  <Tooltip title="Open in folder">
                                    <IconButton size="small" onClick={() => handleOpenMailDocument(doc.documentId)}>
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
                                disabled={testingAccountId === account.id}
                              >
                                {testingAccountId === account.id ? (
                                  <CircularProgress size={16} />
                                ) : (
                                  <Link fontSize="small" />
                                )}
                              </IconButton>
                            </span>
                          </Tooltip>
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
              <TableContainer>
                <Table size="small">
                  <TableHead>
                    <TableRow>
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
                      <TableCell colSpan={9} align="center">
                        No mail rules configured
                      </TableCell>
                    </TableRow>
                    )}
                    {rules.map((rule) => (
                      <TableRow key={rule.id} hover>
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
                            <Typography variant="caption">Process: {rule.actionType}</Typography>
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
                label={editingAccount ? 'Password (leave blank to keep)' : 'Password'}
                type="password"
                value={accountForm.password}
                onChange={(event) => setAccountForm({ ...accountForm, password: event.target.value })}
                size="small"
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
                value={ruleForm.actionType}
                label="Processing scope"
                onChange={(event) => setRuleForm({
                  ...ruleForm,
                  actionType: event.target.value as MailActionType,
                })}
              >
                {actionOptions.map((option) => (
                  <MenuItem key={option} value={option}>{option}</MenuItem>
                ))}
              </Select>
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
