import React, { useEffect, useMemo, useState } from 'react';
import {
  Box,
  Button,
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
import { Add, Delete, Edit, Link, Refresh } from '@mui/icons-material';
import { toast } from 'react-toastify';
import mailAutomationService, {
  MailAccount,
  MailAccountRequest,
  MailConnectionTestResult,
  MailFetchSummary,
  MailRule,
  MailRuleRequest,
  MailActionType,
  MailSecurityType,
  MailOAuthProvider,
  MailPostAction,
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
  const [accounts, setAccounts] = useState<MailAccount[]>([]);
  const [rules, setRules] = useState<MailRule[]>([]);
  const [tags, setTags] = useState<TagOption[]>([]);
  const [loading, setLoading] = useState(false);
  const [fetching, setFetching] = useState(false);
  const [refreshing, setRefreshing] = useState(false);
  const [testingAccountId, setTestingAccountId] = useState<string | null>(null);

  const [accountDialogOpen, setAccountDialogOpen] = useState(false);
  const [accountForm, setAccountForm] = useState<MailAccountRequest>(DEFAULT_ACCOUNT_FORM);
  const [editingAccount, setEditingAccount] = useState<MailAccount | null>(null);

  const [ruleDialogOpen, setRuleDialogOpen] = useState(false);
  const [ruleForm, setRuleForm] = useState(DEFAULT_RULE_FORM);
  const [editingRule, setEditingRule] = useState<MailRule | null>(null);

  const securityOptions: MailSecurityType[] = ['SSL', 'STARTTLS', 'NONE', 'OAUTH2'];
  const oauthProviderOptions: MailOAuthProvider[] = ['MICROSOFT', 'GOOGLE', 'CUSTOM'];
  const actionOptions: MailActionType[] = ['ATTACHMENTS_ONLY', 'METADATA_ONLY', 'EVERYTHING'];
  const postActionOptions: MailPostAction[] = ['MARK_READ', 'MOVE', 'DELETE', 'FLAG', 'TAG', 'NONE'];
  const isOauthAccount = accountForm.security === 'OAUTH2';
  const normalizedCredentialKey = accountForm.oauthCredentialKey
    ? normalizeCredentialKey(accountForm.oauthCredentialKey)
    : '';
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

  const loadAll = async (options?: { silent?: boolean }) => {
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
    } catch {
      toast.error('Failed to load mail automation data');
    } finally {
      if (!silent) {
        setLoading(false);
      }
    }
    return ok;
  };

  useEffect(() => {
    loadAll();
  }, []);

  const accountNameById = useMemo(() => {
    return new Map(accounts.map((account) => [account.id, account.name]));
  }, [accounts]);

  const tagNameById = useMemo(() => {
    return new Map(tags.map((tag) => [tag.id, tag.name]));
  }, [tags]);

  const handleTriggerFetch = async () => {
    setFetching(true);
    try {
      const summary: MailFetchSummary = await mailAutomationService.triggerFetch();
      const durationSeconds = (summary.durationMs / 1000).toFixed(1);
      const message =
        `Processed ${summary.processedMessages} of ${summary.matchedMessages} matched ` +
        `(${summary.foundMessages} unread, ${summary.skippedMessages} skipped) in ${durationSeconds}s`;
      if (summary.errorMessages > 0 || summary.accountErrors > 0) {
        toast.warn(`${message}. Errors: ${summary.errorMessages}, account errors: ${summary.accountErrors}`);
      } else {
        toast.success(message);
      }
    } catch {
      toast.error('Failed to trigger mail fetch');
    } finally {
      setFetching(false);
    }
  };

  const handleRefreshStatus = async () => {
    setRefreshing(true);
    try {
      const ok = await loadAll({ silent: true });
      if (ok) {
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
        if (!accountForm.oauthCredentialKey) {
          toast.warn('OAuth credential key is required');
          return;
        }
        if (accountForm.oauthProvider === 'CUSTOM' && !accountForm.oauthTokenEndpoint) {
          toast.warn('OAuth token endpoint is required for custom providers');
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

  const selectedTag = tags.find((tag) => tag.id === ruleForm.assignTagId) || null;
  const mailActionHelper =
    ruleForm.mailAction === 'MOVE'
      ? 'Target mailbox folder (IMAP)'
      : ruleForm.mailAction === 'TAG'
      ? 'IMAP keyword/label to apply'
      : 'Optional';

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
                      <TableCell align="right">Actions</TableCell>
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
                        <TableCell colSpan={8} align="center">
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
                        <TableCell align="right">
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
                    onChange={(event) => setAccountForm({
                      ...accountForm,
                      oauthProvider: event.target.value as MailOAuthProvider,
                    })}
                  >
                    {oauthProviderOptions.map((option) => (
                      <MenuItem key={option} value={option}>{option}</MenuItem>
                    ))}
                  </Select>
                </FormControl>
                <TextField
                  label="OAuth credential key"
                  value={accountForm.oauthCredentialKey}
                  onChange={(event) => setAccountForm({ ...accountForm, oauthCredentialKey: event.target.value })}
                  size="small"
                  helperText={`Server env prefix: ${oauthEnvPrefix}`}
                  fullWidth
                />
                <TextField
                  label="OAuth token endpoint (custom)"
                  value={accountForm.oauthTokenEndpoint}
                  onChange={(event) => setAccountForm({ ...accountForm, oauthTokenEndpoint: event.target.value })}
                  size="small"
                  fullWidth
                />
                <TextField
                  label="OAuth tenant ID (Microsoft)"
                  value={accountForm.oauthTenantId}
                  onChange={(event) => setAccountForm({ ...accountForm, oauthTenantId: event.target.value })}
                  size="small"
                  fullWidth
                />
                <TextField
                  label="OAuth scope"
                  value={accountForm.oauthScope}
                  onChange={(event) => setAccountForm({ ...accountForm, oauthScope: event.target.value })}
                  size="small"
                  fullWidth
                />
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
            <TextField
              label="Mailbox folder"
              value={ruleForm.folder}
              onChange={(event) => setRuleForm({ ...ruleForm, folder: event.target.value })}
              size="small"
              helperText="IMAP folder name, default INBOX"
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
    </Box>
  );
};

export default MailAutomationPage;
