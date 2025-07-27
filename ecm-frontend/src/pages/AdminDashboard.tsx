import React, { useEffect, useState } from 'react';
import {
  Box,
  Grid,
  Paper,
  Typography,
  Card,
  CardContent,
  IconButton,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  LinearProgress,
  Chip,
  Button,
  Tab,
  Tabs,
} from '@mui/material';
import {
  Storage,
  People,
  Folder,
  Description,
  TrendingUp,
  Refresh,
  Settings,
  Security,
} from '@mui/icons-material';
import {
  LineChart,
  Line,
  AreaChart,
  Area,
  BarChart,
  Bar,
  PieChart,
  Pie,
  Cell,
  XAxis,
  YAxis,
  CartesianGrid,
  Tooltip,
  Legend,
  ResponsiveContainer,
} from 'recharts';
import { format } from 'date-fns';

interface SystemStats {
  totalDocuments: number;
  totalFolders: number;
  totalUsers: number;
  storageUsed: number;
  storageTotal: number;
  activeUsers: number;
  documentsToday: number;
  documentsGrowth: number;
}

interface ActivityData {
  date: string;
  uploads: number;
  downloads: number;
  logins: number;
}

interface StorageByType {
  type: string;
  size: number;
  color: string;
}

interface UserActivity {
  username: string;
  lastLogin: string;
  documentsCreated: number;
  status: 'active' | 'inactive';
}

const AdminDashboard: React.FC = () => {
  const [tabValue, setTabValue] = useState(0);
  const [loading, setLoading] = useState(true);
  const [stats, setStats] = useState<SystemStats>({
    totalDocuments: 15234,
    totalFolders: 1892,
    totalUsers: 156,
    storageUsed: 456.7 * 1024 * 1024 * 1024, // GB to bytes
    storageTotal: 1024 * 1024 * 1024 * 1024, // 1TB
    activeUsers: 89,
    documentsToday: 234,
    documentsGrowth: 12.5,
  });

  const [activityData] = useState<ActivityData[]>([
    { date: '2024-01-01', uploads: 120, downloads: 200, logins: 45 },
    { date: '2024-01-02', uploads: 132, downloads: 180, logins: 52 },
    { date: '2024-01-03', uploads: 101, downloads: 210, logins: 48 },
    { date: '2024-01-04', uploads: 134, downloads: 195, logins: 61 },
    { date: '2024-01-05', uploads: 90, downloads: 150, logins: 38 },
    { date: '2024-01-06', uploads: 85, downloads: 140, logins: 35 },
    { date: '2024-01-07', uploads: 140, downloads: 220, logins: 58 },
  ]);

  const [storageByType] = useState<StorageByType[]>([
    { type: 'Documents', size: 45, color: '#0088FE' },
    { type: 'Images', size: 25, color: '#00C49F' },
    { type: 'Videos', size: 20, color: '#FFBB28' },
    { type: 'Others', size: 10, color: '#FF8042' },
  ]);

  const [recentUsers] = useState<UserActivity[]>([
    { username: 'admin', lastLogin: '2024-01-07 14:30', documentsCreated: 1234, status: 'active' },
    { username: 'john.doe', lastLogin: '2024-01-07 12:15', documentsCreated: 567, status: 'active' },
    { username: 'jane.smith', lastLogin: '2024-01-06 09:45', documentsCreated: 890, status: 'active' },
    { username: 'mike.wilson', lastLogin: '2024-01-05 16:20', documentsCreated: 234, status: 'inactive' },
    { username: 'sarah.jones', lastLogin: '2024-01-07 11:00', documentsCreated: 456, status: 'active' },
  ]);

  useEffect(() => {
    // Simulate loading
    setTimeout(() => setLoading(false), 1000);
  }, []);

  const formatBytes = (bytes: number): string => {
    const sizes = ['B', 'KB', 'MB', 'GB', 'TB'];
    const i = Math.floor(Math.log(bytes) / Math.log(1024));
    return `${(bytes / Math.pow(1024, i)).toFixed(2)} ${sizes[i]}`;
  };

  const getStoragePercentage = () => {
    return (stats.storageUsed / stats.storageTotal) * 100;
  };

  const MetricCard: React.FC<{
    title: string;
    value: string | number;
    icon: React.ReactNode;
    subtitle?: string;
    trend?: number;
  }> = ({ title, value, icon, subtitle, trend }) => (
    <Card>
      <CardContent>
        <Box display="flex" justifyContent="space-between" alignItems="flex-start">
          <Box>
            <Typography color="textSecondary" gutterBottom>
              {title}
            </Typography>
            <Typography variant="h4" component="div">
              {value}
            </Typography>
            {subtitle && (
              <Typography variant="body2" color="textSecondary">
                {subtitle}
              </Typography>
            )}
            {trend !== undefined && (
              <Box display="flex" alignItems="center" mt={1}>
                <TrendingUp
                  sx={{
                    color: trend > 0 ? 'success.main' : 'error.main',
                    transform: trend < 0 ? 'rotate(180deg)' : 'none',
                    mr: 0.5,
                    fontSize: 16,
                  }}
                />
                <Typography
                  variant="body2"
                  color={trend > 0 ? 'success.main' : 'error.main'}
                >
                  {Math.abs(trend)}%
                </Typography>
              </Box>
            )}
          </Box>
          <Box
            sx={{
              backgroundColor: 'primary.light',
              borderRadius: 2,
              p: 1,
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
            }}
          >
            {icon}
          </Box>
        </Box>
      </CardContent>
    </Card>
  );

  const handleTabChange = (event: React.SyntheticEvent, newValue: number) => {
    setTabValue(newValue);
  };

  if (loading) {
    return (
      <Box sx={{ width: '100%' }}>
        <LinearProgress />
      </Box>
    );
  }

  return (
    <Box>
      <Box display="flex" justifyContent="space-between" alignItems="center" mb={3}>
        <Typography variant="h4">Admin Dashboard</Typography>
        <IconButton color="primary">
          <Refresh />
        </IconButton>
      </Box>

      <Grid container spacing={3} mb={3}>
        <Grid item xs={12} sm={6} md={3}>
          <MetricCard
            title="Total Documents"
            value={stats.totalDocuments.toLocaleString()}
            icon={<Description sx={{ color: 'primary.main' }} />}
            subtitle={`+${stats.documentsToday} today`}
            trend={stats.documentsGrowth}
          />
        </Grid>
        <Grid item xs={12} sm={6} md={3}>
          <MetricCard
            title="Total Folders"
            value={stats.totalFolders.toLocaleString()}
            icon={<Folder sx={{ color: 'primary.main' }} />}
          />
        </Grid>
        <Grid item xs={12} sm={6} md={3}>
          <MetricCard
            title="Active Users"
            value={stats.activeUsers}
            icon={<People sx={{ color: 'primary.main' }} />}
            subtitle={`of ${stats.totalUsers} total`}
          />
        </Grid>
        <Grid item xs={12} sm={6} md={3}>
          <MetricCard
            title="Storage Used"
            value={formatBytes(stats.storageUsed)}
            icon={<Storage sx={{ color: 'primary.main' }} />}
            subtitle={`${getStoragePercentage().toFixed(1)}% of ${formatBytes(stats.storageTotal)}`}
          />
        </Grid>
      </Grid>

      <Paper sx={{ width: '100%', mb: 3 }}>
        <Tabs value={tabValue} onChange={handleTabChange}>
          <Tab label="Activity Overview" />
          <Tab label="Storage Analytics" />
          <Tab label="User Management" />
          <Tab label="System Settings" />
        </Tabs>
      </Paper>

      {tabValue === 0 && (
        <Grid container spacing={3}>
          <Grid item xs={12}>
            <Paper sx={{ p: 3 }}>
              <Typography variant="h6" gutterBottom>
                System Activity (Last 7 Days)
              </Typography>
              <ResponsiveContainer width="100%" height={300}>
                <LineChart data={activityData}>
                  <CartesianGrid strokeDasharray="3 3" />
                  <XAxis
                    dataKey="date"
                    tickFormatter={(value) => format(new Date(value), 'MMM dd')}
                  />
                  <YAxis />
                  <Tooltip />
                  <Legend />
                  <Line type="monotone" dataKey="uploads" stroke="#8884d8" name="Uploads" />
                  <Line type="monotone" dataKey="downloads" stroke="#82ca9d" name="Downloads" />
                  <Line type="monotone" dataKey="logins" stroke="#ffc658" name="Logins" />
                </LineChart>
              </ResponsiveContainer>
            </Paper>
          </Grid>
        </Grid>
      )}

      {tabValue === 1 && (
        <Grid container spacing={3}>
          <Grid item xs={12} md={6}>
            <Paper sx={{ p: 3 }}>
              <Typography variant="h6" gutterBottom>
                Storage by File Type
              </Typography>
              <ResponsiveContainer width="100%" height={300}>
                <PieChart>
                  <Pie
                    data={storageByType}
                    cx="50%"
                    cy="50%"
                    labelLine={false}
                    label={(entry) => `${entry.type}: ${entry.size}%`}
                    outerRadius={80}
                    fill="#8884d8"
                    dataKey="size"
                  >
                    {storageByType.map((entry, index) => (
                      <Cell key={`cell-${index}`} fill={entry.color} />
                    ))}
                  </Pie>
                  <Tooltip />
                </PieChart>
              </ResponsiveContainer>
            </Paper>
          </Grid>
          <Grid item xs={12} md={6}>
            <Paper sx={{ p: 3 }}>
              <Typography variant="h6" gutterBottom>
                Storage Growth Trend
              </Typography>
              <ResponsiveContainer width="100%" height={300}>
                <AreaChart
                  data={activityData.map((d) => ({
                    date: d.date,
                    storage: Math.random() * 100 + 400,
                  }))}
                >
                  <CartesianGrid strokeDasharray="3 3" />
                  <XAxis
                    dataKey="date"
                    tickFormatter={(value) => format(new Date(value), 'MMM dd')}
                  />
                  <YAxis />
                  <Tooltip />
                  <Area type="monotone" dataKey="storage" stroke="#8884d8" fill="#8884d8" />
                </AreaChart>
              </ResponsiveContainer>
            </Paper>
          </Grid>
        </Grid>
      )}

      {tabValue === 2 && (
        <Paper sx={{ p: 3 }}>
          <Box display="flex" justifyContent="space-between" alignItems="center" mb={2}>
            <Typography variant="h6">Recent User Activity</Typography>
            <Button variant="contained" startIcon={<People />}>
              Manage Users
            </Button>
          </Box>
          <TableContainer>
            <Table>
              <TableHead>
                <TableRow>
                  <TableCell>Username</TableCell>
                  <TableCell>Last Login</TableCell>
                  <TableCell>Documents Created</TableCell>
                  <TableCell>Status</TableCell>
                  <TableCell>Actions</TableCell>
                </TableRow>
              </TableHead>
              <TableBody>
                {recentUsers.map((user) => (
                  <TableRow key={user.username}>
                    <TableCell>{user.username}</TableCell>
                    <TableCell>{user.lastLogin}</TableCell>
                    <TableCell>{user.documentsCreated}</TableCell>
                    <TableCell>
                      <Chip
                        label={user.status}
                        color={user.status === 'active' ? 'success' : 'default'}
                        size="small"
                      />
                    </TableCell>
                    <TableCell>
                      <Button size="small">View</Button>
                      <Button size="small">Edit</Button>
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </TableContainer>
        </Paper>
      )}

      {tabValue === 3 && (
        <Grid container spacing={3}>
          <Grid item xs={12} md={6}>
            <Paper sx={{ p: 3 }}>
              <Typography variant="h6" gutterBottom>
                System Configuration
              </Typography>
              <Box mt={2}>
                <Button variant="outlined" startIcon={<Settings />} fullWidth sx={{ mb: 1 }}>
                  General Settings
                </Button>
                <Button variant="outlined" startIcon={<Security />} fullWidth sx={{ mb: 1 }}>
                  Security Settings
                </Button>
                <Button variant="outlined" startIcon={<Storage />} fullWidth sx={{ mb: 1 }}>
                  Storage Settings
                </Button>
              </Box>
            </Paper>
          </Grid>
          <Grid item xs={12} md={6}>
            <Paper sx={{ p: 3 }}>
              <Typography variant="h6" gutterBottom>
                System Information
              </Typography>
              <Box mt={2}>
                <Typography variant="body2" gutterBottom>
                  <strong>Version:</strong> Athena ECM v1.0.0
                </Typography>
                <Typography variant="body2" gutterBottom>
                  <strong>Database:</strong> PostgreSQL 13.0
                </Typography>
                <Typography variant="body2" gutterBottom>
                  <strong>Search Engine:</strong> Elasticsearch 7.15.0
                </Typography>
                <Typography variant="body2" gutterBottom>
                  <strong>Workflow Engine:</strong> Flowable 6.7.0
                </Typography>
              </Box>
            </Paper>
          </Grid>
        </Grid>
      )}
    </Box>
  );
};

export default AdminDashboard;