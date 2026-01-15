import React, { useEffect, useMemo, useState } from 'react';
import {
  Box,
  Button,
  Card,
  CardContent,
  Chip,
  CircularProgress,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  FormControl,
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
import { Add, Delete, Edit, Refresh } from '@mui/icons-material';
import { toast } from 'react-toastify';
import mailAutomationService, {
  MailAccount,
  MailAccountRequest,
  MailRule,
  MailRuleRequest,
  MailActionType,
  MailSecurityType,
} from 'services/mailAutomationService';
import tagService from 'services/tagService';
import nodeService from 'services/nodeService';

interface TagOption {
  id: string;
  name: string;
}

const DEFAULT_ACCOUNT_FORM: MailAccountRequest = {
  name: '',
  host: '',
  port: 993,
  username: '',
  password: '',
  security: 'SSL',
  enabled: true,
  pollIntervalMinutes: 10,
};

const DEFAULT_RULE_FORM: MailRuleRequest & { folderPath: string; folderIdOverride: string } = {
  name: '',
  accountId: '',
  priority: 100,
  subjectFilter: '',
  fromFilter: '',
  bodyFilter: '',
  actionType: 'ATTACHMENTS_ONLY',
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

  const [accountDialogOpen, setAccountDialogOpen] = useState(false);
  const [accountForm, setAccountForm] = useState<MailAccountRequest>(DEFAULT_ACCOUNT_FORM);
  const [editingAccount, setEditingAccount] = useState<MailAccount | null>(null);

  const [ruleDialogOpen, setRuleDialogOpen] = useState(false);
  const [ruleForm, setRuleForm] = useState(DEFAULT_RULE_FORM);
  const [editingRule, setEditingRule] = useState<MailRule | null>(null);

  const securityOptions: MailSecurityType[] = ['SSL', 'STARTTLS', 'NONE'];
  const actionOptions: MailActionType[] = ['ATTACHMENTS_ONLY', 'METADATA_ONLY', 'EVERYTHING'];

  const loadAll = async () => {
    setLoading(true);
    try {
      const [accountList, ruleList, tagList] = await Promise.all([
        mailAutomationService.listAccounts(),
        mailAutomationService.listRules(),
        tagService.getAllTags(),
      ]);
      setAccounts(accountList);
      setRules(ruleList);
      setTags(tagList.map((tag) => ({ id: tag.id, name: tag.name })));
    } catch {
      toast.error('Failed to load mail automation data');
    } finally {
      setLoading(false);
    }
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
      await mailAutomationService.triggerFetch();
      toast.success('Mail fetch triggered');
    } catch {
      toast.error('Failed to trigger mail fetch');
    } finally {
      setFetching(false);
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
    });
    setAccountDialogOpen(true);
  };

  const handleSaveAccount = async () => {
    try {
      if (!accountForm.name || !accountForm.host || !accountForm.username || !accountForm.port) {
        toast.warn('Name, host, port, and username are required');
        return;
      }

      if (!editingAccount && !accountForm.password) {
        toast.warn('Password is required for new accounts');
        return;
      }

      if (editingAccount) {
        await mailAutomationService.updateAccount(editingAccount.id, accountForm);
        toast.success('Mail account updated');
      } else {
        await mailAutomationService.createAccount(accountForm);
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
      subjectFilter: rule.subjectFilter || '',
      fromFilter: rule.fromFilter || '',
      bodyFilter: rule.bodyFilter || '',
      actionType: rule.actionType,
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
        subjectFilter: ruleForm.subjectFilter || null,
        fromFilter: ruleForm.fromFilter || null,
        bodyFilter: ruleForm.bodyFilter || null,
        actionType: ruleForm.actionType,
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
                <Button variant="contained" startIcon={<Add />} onClick={openCreateAccount}>
                  New Account
                </Button>
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
                      <TableCell>Status</TableCell>
                      <TableCell align="right">Actions</TableCell>
                    </TableRow>
                  </TableHead>
                  <TableBody>
                    {accounts.length === 0 && (
                      <TableRow>
                        <TableCell colSpan={7} align="center">
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
                          <Chip
                            size="small"
                            label={account.enabled ? 'Enabled' : 'Disabled'}
                            color={account.enabled ? 'success' : 'default'}
                            variant="outlined"
                          />
                        </TableCell>
                        <TableCell align="right">
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
                      <TableCell>Action</TableCell>
                      <TableCell>Tag</TableCell>
                      <TableCell>Folder</TableCell>
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
                            {rule.subjectFilter && <Typography variant="caption">Subject: {rule.subjectFilter}</Typography>}
                            {rule.fromFilter && <Typography variant="caption">From: {rule.fromFilter}</Typography>}
                            {rule.bodyFilter && <Typography variant="caption">Body: {rule.bodyFilter}</Typography>}
                            {!rule.subjectFilter && !rule.fromFilter && !rule.bodyFilter && (
                              <Typography variant="caption" color="text.secondary">None</Typography>
                            )}
                          </Box>
                        </TableCell>
                        <TableCell>{rule.actionType}</TableCell>
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
            <TextField
              label={editingAccount ? 'Password (leave blank to keep)' : 'Password'}
              type="password"
              value={accountForm.password}
              onChange={(event) => setAccountForm({ ...accountForm, password: event.target.value })}
              size="small"
              fullWidth
            />
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
              label="Body filter (regex)"
              value={ruleForm.bodyFilter}
              onChange={(event) => setRuleForm({ ...ruleForm, bodyFilter: event.target.value })}
              size="small"
              fullWidth
            />
            <FormControl size="small" fullWidth>
              <InputLabel id="mail-action-label">Action</InputLabel>
              <Select
                labelId="mail-action-label"
                value={ruleForm.actionType}
                label="Action"
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
