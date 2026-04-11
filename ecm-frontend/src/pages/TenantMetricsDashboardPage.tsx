import React, { useCallback, useEffect, useState } from 'react';
import {
  Box,
  Button,
  Card,
  CardContent,
  Chip,
  CircularProgress,
  Grid,
  Paper,
  Stack,
  Typography,
} from '@mui/material';
import { Refresh, Storage, Description, AccountTree } from '@mui/icons-material';
import { toast } from 'react-toastify';
import {
  BarChart,
  Bar,
  XAxis,
  YAxis,
  CartesianGrid,
  Tooltip,
  Legend,
  PieChart,
  Pie,
  Cell,
  ResponsiveContainer,
} from 'recharts';
import tenantService, { TenantDto, TenantMetricsDto } from 'services/tenantService';

const PIE_COLORS = [
  '#1976d2',
  '#388e3c',
  '#f57c00',
  '#d32f2f',
  '#7b1fa2',
  '#0097a7',
  '#5d4037',
  '#455a64',
];

const formatBytes = (bytes: number | null): string => {
  if (bytes == null || bytes === 0) return '0 B';
  const units = ['B', 'KB', 'MB', 'GB', 'TB'];
  const i = Math.floor(Math.log(bytes) / Math.log(1024));
  return `${(bytes / Math.pow(1024, i)).toFixed(1)} ${units[i]}`;
};

interface StorageBarDatum {
  tenantName: string;
  storageUsed: number;
  quota: number | null;
  storageUsedLabel: string;
  quotaLabel: string;
}

interface PieDatum {
  name: string;
  value: number;
}

const TenantMetricsDashboardPage: React.FC = () => {
  const [loading, setLoading] = useState(false);
  const [metrics, setMetrics] = useState<TenantMetricsDto[]>([]);
  const [failedDomains, setFailedDomains] = useState<string[]>([]);

  const loadData = useCallback(async () => {
    setLoading(true);
    setFailedDomains([]);
    try {
      const tenants: TenantDto[] = await tenantService.listTenants();
      const results = await Promise.allSettled(
        tenants.map((t) => tenantService.getTenantMetrics(t.tenantDomain)),
      );

      const fulfilled: TenantMetricsDto[] = [];
      const failed: string[] = [];

      results.forEach((result, index) => {
        if (result.status === 'fulfilled') {
          fulfilled.push(result.value);
        } else {
          failed.push(tenants[index].tenantDomain);
        }
      });

      setMetrics(fulfilled);
      setFailedDomains(failed);

      if (failed.length > 0) {
        toast.warn(`Failed to load metrics for: ${failed.join(', ')}`);
      }
    } catch {
      toast.error('Failed to load tenants');
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void loadData();
  }, []); // eslint-disable-line react-hooks/exhaustive-deps

  // Summary totals
  const totalStorageUsed = metrics.reduce((sum, m) => sum + m.storageUsedBytes, 0);
  const totalDocuments = metrics.reduce((sum, m) => sum + m.documentCount, 0);
  const totalNodes = metrics.reduce((sum, m) => sum + m.nodeCount, 0);

  // Storage bar chart data
  const storageBarData: StorageBarDatum[] = metrics.map((m) => ({
    tenantName: m.tenantName,
    storageUsed: m.storageUsedBytes,
    quota: m.quotaBytes,
    storageUsedLabel: formatBytes(m.storageUsedBytes),
    quotaLabel: m.quotaBytes != null ? formatBytes(m.quotaBytes) : 'No quota',
  }));

  // Pie chart data
  const pieData: PieDatum[] = metrics
    .filter((m) => m.documentCount > 0)
    .map((m) => ({
      name: m.tenantName,
      value: m.documentCount,
    }));

  return (
    <Box maxWidth={1280}>
      <Box display="flex" justifyContent="space-between" alignItems="center" mb={2}>
        <Box>
          <Typography variant="h5">Tenant Metrics Dashboard</Typography>
          <Typography variant="body2" color="text.secondary">
            Cross-tenant storage and content distribution overview.
          </Typography>
        </Box>
        <Stack direction="row" spacing={1}>
          <Button
            variant="outlined"
            startIcon={<Refresh />}
            onClick={() => void loadData()}
            disabled={loading}
          >
            Refresh
          </Button>
        </Stack>
      </Box>

      {failedDomains.length > 0 && (
        <Paper variant="outlined" sx={{ p: 1.5, mb: 2, bgcolor: 'warning.light' }}>
          <Typography variant="body2">
            Metrics unavailable for: {failedDomains.map((d) => (
              <Chip key={d} label={d} size="small" sx={{ mr: 0.5 }} />
            ))}
          </Typography>
        </Paper>
      )}

      {loading ? (
        <Box display="flex" justifyContent="center" p={6}>
          <CircularProgress />
        </Box>
      ) : (
        <Stack spacing={3}>
          {/* Summary Cards */}
          <Grid container spacing={2}>
            <Grid item xs={12} sm={4}>
              <Card variant="outlined">
                <CardContent>
                  <Stack direction="row" spacing={1.5} alignItems="center">
                    <Storage color="primary" />
                    <Box>
                      <Typography variant="caption" color="text.secondary">
                        Total Storage Used
                      </Typography>
                      <Typography variant="h6">{formatBytes(totalStorageUsed)}</Typography>
                    </Box>
                  </Stack>
                </CardContent>
              </Card>
            </Grid>
            <Grid item xs={12} sm={4}>
              <Card variant="outlined">
                <CardContent>
                  <Stack direction="row" spacing={1.5} alignItems="center">
                    <Description color="primary" />
                    <Box>
                      <Typography variant="caption" color="text.secondary">
                        Total Documents
                      </Typography>
                      <Typography variant="h6">{totalDocuments.toLocaleString()}</Typography>
                    </Box>
                  </Stack>
                </CardContent>
              </Card>
            </Grid>
            <Grid item xs={12} sm={4}>
              <Card variant="outlined">
                <CardContent>
                  <Stack direction="row" spacing={1.5} alignItems="center">
                    <AccountTree color="primary" />
                    <Box>
                      <Typography variant="caption" color="text.secondary">
                        Total Nodes
                      </Typography>
                      <Typography variant="h6">{totalNodes.toLocaleString()}</Typography>
                    </Box>
                  </Stack>
                </CardContent>
              </Card>
            </Grid>
          </Grid>

          {/* Storage Usage Bar Chart */}
          <Paper variant="outlined" sx={{ p: 2 }}>
            <Typography variant="h6" gutterBottom>
              Storage Usage by Tenant
            </Typography>
            {storageBarData.length === 0 ? (
              <Typography variant="body2" color="text.secondary" sx={{ py: 4, textAlign: 'center' }}>
                No storage data available.
              </Typography>
            ) : (
              <ResponsiveContainer width="100%" height={Math.max(300, storageBarData.length * 50)}>
                <BarChart
                  data={storageBarData}
                  layout="vertical"
                  margin={{ top: 5, right: 30, left: 20, bottom: 5 }}
                >
                  <CartesianGrid strokeDasharray="3 3" />
                  <XAxis
                    type="number"
                    tickFormatter={(value: number) => formatBytes(value)}
                  />
                  <YAxis
                    type="category"
                    dataKey="tenantName"
                    width={140}
                  />
                  <Tooltip
                    formatter={(value: number, name: string) => [
                      formatBytes(value),
                      name === 'storageUsed' ? 'Used' : 'Quota',
                    ]}
                  />
                  <Legend
                    formatter={(value: string) =>
                      value === 'storageUsed' ? 'Used' : 'Quota'
                    }
                  />
                  <Bar dataKey="storageUsed" fill="#1976d2" name="storageUsed" />
                  <Bar dataKey="quota" fill="#b0bec5" name="quota" />
                </BarChart>
              </ResponsiveContainer>
            )}
          </Paper>

          {/* Content Distribution Pie Chart */}
          <Paper variant="outlined" sx={{ p: 2 }}>
            <Typography variant="h6" gutterBottom>
              Document Distribution by Tenant
            </Typography>
            {pieData.length === 0 ? (
              <Typography variant="body2" color="text.secondary" sx={{ py: 4, textAlign: 'center' }}>
                No document data available.
              </Typography>
            ) : (
              <ResponsiveContainer width="100%" height={360}>
                <PieChart>
                  <Pie
                    data={pieData}
                    dataKey="value"
                    nameKey="name"
                    cx="50%"
                    cy="50%"
                    outerRadius={120}
                    label={({ name, value }: { name: string; value: number }) =>
                      `${name}: ${value.toLocaleString()}`
                    }
                  >
                    {pieData.map((_entry, index) => (
                      <Cell
                        key={`cell-${index}`}
                        fill={PIE_COLORS[index % PIE_COLORS.length]}
                      />
                    ))}
                  </Pie>
                  <Tooltip
                    formatter={(value: number) => [value.toLocaleString(), 'Documents']}
                  />
                  <Legend />
                </PieChart>
              </ResponsiveContainer>
            )}
          </Paper>
        </Stack>
      )}
    </Box>
  );
};

export default TenantMetricsDashboardPage;
