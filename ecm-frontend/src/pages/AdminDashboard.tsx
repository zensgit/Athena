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

const AdminDashboard: React.FC = () => {
  const [tab, setTab] = useState(0);

  // Overview/dashboard state
  const [data, setData] = useState<DashboardData | null>(null);
  const [logs, setLogs] = useState<AuditLog[]>([]);
  const [loadingDashboard, setLoadingDashboard] = useState(true);

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

  const fetchDashboard = async () => {
    try {
      setLoadingDashboard(true);
      const dashboardRes = await apiService.get<DashboardData>('/analytics/dashboard');
      setData(dashboardRes);
      const logsRes = await apiService.get<AuditLog[]>('/analytics/audit/recent?limit=10');
      setLogs(logsRes);
    } catch {
      toast.error('Failed to load dashboard data');
    } finally {
      setLoadingDashboard(false);
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
              <Typography component="h2" variant="h6" color="primary" gutterBottom>
                Recent System Activity
              </Typography>
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
                  <TableCell align="center">Enabled</TableCell>
                </TableRow>
              </TableHead>
              <TableBody>
                {usersLoading ? (
                  <TableRow>
                    <TableCell colSpan={4} align="center">
                      <CircularProgress size={20} />
                    </TableCell>
                  </TableRow>
                ) : (
                  users.map((u) => (
                    <TableRow key={u.username} hover>
                      <TableCell>{u.username}</TableCell>
                      <TableCell>{u.email}</TableCell>
                      <TableCell>{`${u.firstName || ''} ${u.lastName || ''}`.trim() || '-'}</TableCell>
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
                    <TableCell colSpan={4} align="center">
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
