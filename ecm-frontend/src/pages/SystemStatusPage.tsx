import React, { useEffect, useMemo, useState } from 'react';
import {
  Box,
  Button,
  Card,
  CardContent,
  CircularProgress,
  Divider,
  Stack,
  Typography,
} from '@mui/material';
import { toast } from 'react-toastify';
import apiService from 'services/api';

type SystemStatus = {
  timestamp: string;
  database: Record<string, any>;
  redis: Record<string, any>;
  rabbitmq: Record<string, any>;
  search: Record<string, any>;
  ml: Record<string, any>;
  keycloak: Record<string, any>;
  wopi: Record<string, any>;
  antivirus: Record<string, any>;
};

type SanityCheckReport = {
  checkName: string;
  startTime?: string;
  endTime?: string;
  status: 'SUCCESS' | 'WARNING' | 'ERROR' | 'IN_PROGRESS';
  issues?: string[];
  fixes?: string[];
  itemsChecked?: number;
  issuesFound?: number;
  itemsFixed?: number;
};

const SystemStatusPage: React.FC = () => {
  const [status, setStatus] = useState<SystemStatus | null>(null);
  const [loading, setLoading] = useState(false);
  const [sanityLoading, setSanityLoading] = useState(false);
  const [sanityReports, setSanityReports] = useState<SanityCheckReport[] | null>(null);

  const loadStatus = async () => {
    try {
      setLoading(true);
      const data = await apiService.get<SystemStatus>('/system/status');
      setStatus(data);
    } catch {
      toast.error('Failed to load system status');
      setStatus(null);
    } finally {
      setLoading(false);
    }
  };

  const runSanityChecks = async (fix: boolean) => {
    try {
      if (fix) {
        const confirmed = window.confirm(
          'Run sanity checks with fix=true?\n\nThis may attempt automatic fixes. Use with caution in shared environments.'
        );
        if (!confirmed) {
          return;
        }
      }

      setSanityLoading(true);
      const reports = await apiService.post<SanityCheckReport[]>('/system/sanity/run', undefined, { params: { fix } });
      setSanityReports(reports);
      toast.success('Sanity checks completed');
    } catch {
      toast.error('Failed to run sanity checks (admin required)');
      setSanityReports(null);
    } finally {
      setSanityLoading(false);
    }
  };

  useEffect(() => {
    loadStatus();
  }, []);

  const cards = useMemo(() => {
    if (!status) {
      return [];
    }
    return [
      { title: 'Database', data: status.database },
      { title: 'Redis', data: status.redis },
      { title: 'RabbitMQ', data: status.rabbitmq },
      { title: 'Search', data: status.search },
      { title: 'ML', data: status.ml },
      { title: 'Keycloak', data: status.keycloak },
      { title: 'WOPI / Collabora', data: status.wopi },
      { title: 'Antivirus', data: status.antivirus },
    ];
  }, [status]);

  const handleCopyJson = async () => {
    try {
      if (!status) {
        toast.error('No status loaded');
        return;
      }
      await navigator.clipboard.writeText(JSON.stringify(status, null, 2));
      toast.success('System status copied');
    } catch {
      toast.error('Failed to copy');
    }
  };

  return (
    <Box p={3}>
      <Box display="flex" alignItems="center" justifyContent="space-between" mb={2} flexWrap="wrap" gap={1}>
        <Box>
          <Typography variant="h4">System Status</Typography>
          <Typography variant="body2" color="text.secondary">
            {status?.timestamp ? `Last updated: ${new Date(status.timestamp).toLocaleString()}` : '—'}
          </Typography>
        </Box>
        <Box display="flex" gap={1} flexWrap="wrap">
          <Button variant="outlined" onClick={handleCopyJson} disabled={!status || loading}>
            Copy JSON
          </Button>
          <Button variant="contained" onClick={loadStatus} disabled={loading}>
            Refresh
          </Button>
        </Box>
      </Box>

      {loading && (
        <Box display="flex" justifyContent="center" p={4}>
          <CircularProgress />
        </Box>
      )}

      {!loading && !status && (
        <Card variant="outlined">
          <CardContent>
            <Typography color="text.secondary">No status available.</Typography>
          </CardContent>
        </Card>
      )}

      {!loading && status && (
        <Stack spacing={2}>
          {cards.map((card) => (
            <Card key={card.title} variant="outlined">
              <CardContent>
                <Typography variant="h6" gutterBottom>
                  {card.title}
                </Typography>
                <Divider sx={{ mb: 2 }} />
                <Typography component="pre" variant="body2" sx={{ whiteSpace: 'pre-wrap', wordBreak: 'break-word' }}>
                  {JSON.stringify(card.data, null, 2)}
                </Typography>
              </CardContent>
            </Card>
          ))}

          <Card variant="outlined">
            <CardContent>
              <Box display="flex" alignItems="center" justifyContent="space-between" gap={1} flexWrap="wrap">
                <Typography variant="h6">Sanity Checks</Typography>
                <Box display="flex" gap={1} flexWrap="wrap">
                  <Button variant="outlined" onClick={() => runSanityChecks(false)} disabled={sanityLoading}>
                    Run
                  </Button>
                  <Button variant="contained" color="warning" onClick={() => runSanityChecks(true)} disabled={sanityLoading}>
                    Run (fix)
                  </Button>
                </Box>
              </Box>
              <Divider sx={{ my: 2 }} />

              {sanityLoading && (
                <Box display="flex" justifyContent="center" p={2}>
                  <CircularProgress size={24} />
                </Box>
              )}

              {!sanityLoading && !sanityReports && (
                <Typography color="text.secondary" variant="body2">
                  Click “Run” to execute consistency checks (admin only).
                </Typography>
              )}

              {!sanityLoading && sanityReports && (
                <Typography component="pre" variant="body2" sx={{ whiteSpace: 'pre-wrap', wordBreak: 'break-word' }}>
                  {JSON.stringify(sanityReports, null, 2)}
                </Typography>
              )}
            </CardContent>
          </Card>
        </Stack>
      )}
    </Box>
  );
};

export default SystemStatusPage;
