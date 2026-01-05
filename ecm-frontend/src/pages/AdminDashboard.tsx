import React, { useEffect, useState } from 'react';
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
  Dialog,
  DialogTitle,
  DialogContent,
  DialogActions,
  TextField,
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
} from '@mui/icons-material';
import { format } from 'date-fns';
import apiService from '../services/api';
import { toast } from 'react-toastify';
import userGroupService, { Group } from 'services/userGroupService';
import { User } from 'types';

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

const AdminDashboard: React.FC = () => {
  const [tab, setTab] = useState(0);

  // Overview/dashboard state
  const [data, setData] = useState<DashboardData | null>(null);
  const [logs, setLogs] = useState<AuditLog[]>([]);
  const [ruleSummary, setRuleSummary] = useState<RuleExecutionSummary | null>(null);
  const [ruleEvents, setRuleEvents] = useState<AuditLog[]>([]);
  const [ruleEventFilter, setRuleEventFilter] = useState<string[]>(RULE_EVENT_TYPES);
  const [licenseInfo, setLicenseInfo] = useState<LicenseInfo | null>(null);
  const [loadingDashboard, setLoadingDashboard] = useState(true);
  const [retentionInfo, setRetentionInfo] = useState<AuditRetentionInfo | null>(null);
  const [exportingAudit, setExportingAudit] = useState(false);
  const [cleaningAudit, setCleaningAudit] = useState(false);

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

  const fetchDashboard = async () => {
    try {
      setLoadingDashboard(true);
      const [dashboardRes, logsRes, licenseRes, retentionRes, ruleSummaryRes, ruleEventsRes] = await Promise.all([
        apiService.get<DashboardData>('/analytics/dashboard'),
        apiService.get<AuditLog[]>('/analytics/audit/recent?limit=10'),
        apiService.get<LicenseInfo>('/system/license').catch(() => null),
        apiService.get<AuditRetentionInfo>('/analytics/audit/retention').catch(() => null),
        apiService.get<RuleExecutionSummary>('/analytics/rules/summary?days=7').catch(() => null),
        apiService.get<AuditLog[]>('/analytics/rules/recent?limit=20').catch(() => []),
      ]);
      setData(dashboardRes);
      setLogs(logsRes);
      setLicenseInfo(licenseRes);
      setRetentionInfo(retentionRes);
      setRuleSummary(ruleSummaryRes);
      setRuleEvents(ruleEventsRes || []);
    } catch {
      toast.error('Failed to load dashboard data');
    } finally {
      setLoadingDashboard(false);
    }
  };

  const handleExportAuditLogs = async () => {
    try {
      setExportingAudit(true);
      const fallbackTo = new Date();
      const fallbackFrom = new Date();
      fallbackFrom.setDate(fallbackFrom.getDate() - 30);

      const fromInput = auditExportFrom?.trim() ? new Date(auditExportFrom) : fallbackFrom;
      const toInput = auditExportTo?.trim() ? new Date(auditExportTo) : fallbackTo;

      if (Number.isNaN(fromInput.getTime()) || Number.isNaN(toInput.getTime())) {
        toast.error('Invalid audit export date range');
        return;
      }
      if (fromInput > toInput) {
        toast.error('Audit export start time must be before end time');
        return;
      }

      const fromStr = encodeURIComponent(formatDateTimeOffset(fromInput));
      const toStr = encodeURIComponent(formatDateTimeOffset(toInput));

      const apiBaseUrl = process.env.REACT_APP_API_URL
        || process.env.REACT_APP_API_BASE_URL
        || '/api/v1';
      const response = await fetch(
        `${apiBaseUrl}/analytics/audit/export?from=${fromStr}&to=${toStr}`,
        {
          headers: {
            Authorization: `Bearer ${localStorage.getItem('access_token') || ''}`,
          },
        }
      );

      if (!response.ok) {
        throw new Error('Export failed');
      }

      const blob = await response.blob();
      const url = window.URL.createObjectURL(blob);
      const a = document.createElement('a');
      a.href = url;
      a.download = `audit_logs_${format(fromInput, 'yyyyMMdd')}_to_${format(toInput, 'yyyyMMdd')}.csv`;
      document.body.appendChild(a);
      a.click();
      window.URL.revokeObjectURL(url);
      document.body.removeChild(a);

      toast.success('Audit logs exported successfully');
    } catch (error) {
      console.error(error);
      toast.error('Failed to export audit logs');
    } finally {
      setExportingAudit(false);
    }
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
  }, []);

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
          <IconButton onClick={fetchDashboard} color="primary">
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
                  {retentionInfo && retentionInfo.expiredLogCount > 0 && (
                    <Chip
                      size="small"
                      label={`${retentionInfo.expiredLogCount} expired`}
                      color="warning"
                      variant="outlined"
                    />
                  )}
                  <TextField
                    label="From"
                    type="datetime-local"
                    size="small"
                    value={auditExportFrom}
                    onChange={(event) => setAuditExportFrom(event.target.value)}
                    InputLabelProps={{ shrink: true }}
                    sx={{ minWidth: 210 }}
                  />
                  <TextField
                    label="To"
                    type="datetime-local"
                    size="small"
                    value={auditExportTo}
                    onChange={(event) => setAuditExportTo(event.target.value)}
                    InputLabelProps={{ shrink: true }}
                    sx={{ minWidth: 210 }}
                  />
                  <Button
                    size="small"
                    variant="outlined"
                    startIcon={<DownloadIcon />}
                    onClick={handleExportAuditLogs}
                    disabled={exportingAudit}
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
                              {log.username} - {log.eventType}
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
