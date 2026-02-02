import React, { useEffect, useMemo, useState } from 'react';
import {
  Box,
  Button,
  Card,
  CardContent,
  CircularProgress,
  Divider,
  Chip,
  FormControlLabel,
  IconButton,
  Collapse,
  Stack,
  Switch,
  Typography,
} from '@mui/material';
import { ContentCopy, ExpandLess, ExpandMore } from '@mui/icons-material';
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

type StatusLevel = 'healthy' | 'warning' | 'error' | 'disabled';

const getStatusMeta = (data: Record<string, any> | undefined | null) => {
  if (!data) {
    return { level: 'error' as StatusLevel, label: 'Unavailable', color: 'error' as const };
  }

  if (typeof data.status === 'string') {
    const normalized = data.status.toLowerCase();
    if (normalized === 'healthy') {
      return { level: 'healthy' as StatusLevel, label: 'Healthy', color: 'success' as const };
    }
    if (normalized === 'disabled') {
      return { level: 'disabled' as StatusLevel, label: 'Disabled', color: 'default' as const };
    }
    if (normalized === 'unavailable') {
      return { level: 'error' as StatusLevel, label: 'Unavailable', color: 'error' as const };
    }
    return { level: 'warning' as StatusLevel, label: data.status, color: 'warning' as const };
  }

  if (data.enabled === false || data.searchEnabled === false) {
    return { level: 'disabled' as StatusLevel, label: 'Disabled', color: 'default' as const };
  }

  if (data.reachable === false || data.available === false) {
    return { level: 'error' as StatusLevel, label: 'Unavailable', color: 'error' as const };
  }

  if (data.error) {
    return { level: 'error' as StatusLevel, label: 'Error', color: 'error' as const };
  }

  return { level: 'healthy' as StatusLevel, label: 'Healthy', color: 'success' as const };
};

const SystemStatusPage: React.FC = () => {
  const AUTO_REFRESH_KEY = 'athena.ecm.systemStatus.autoRefresh';
  const AUTO_REFRESH_INTERVAL_MS = 30000;
  const [status, setStatus] = useState<SystemStatus | null>(null);
  const [loading, setLoading] = useState(false);
  const [sanityLoading, setSanityLoading] = useState(false);
  const [sanityReports, setSanityReports] = useState<SanityCheckReport[] | null>(null);
  const [autoRefresh, setAutoRefresh] = useState(() => {
    try {
      return window.localStorage.getItem(AUTO_REFRESH_KEY) === 'true';
    } catch {
      return false;
    }
  });
  const [expandedCards, setExpandedCards] = useState<Record<string, boolean>>({});

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

  useEffect(() => {
    try {
      window.localStorage.setItem(AUTO_REFRESH_KEY, String(autoRefresh));
    } catch {
      // ignore
    }
  }, [autoRefresh]);

  useEffect(() => {
    if (!autoRefresh) {
      return undefined;
    }
    const id = window.setInterval(() => {
      void loadStatus();
    }, AUTO_REFRESH_INTERVAL_MS);
    return () => window.clearInterval(id);
  }, [autoRefresh]);

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

  const statusSummary = useMemo(() => {
    if (!status) {
      return null;
    }
    const counts: Record<StatusLevel, number> = {
      healthy: 0,
      warning: 0,
      error: 0,
      disabled: 0,
    };
    cards.forEach((card) => {
      const meta = getStatusMeta(card.data);
      counts[meta.level] += 1;
    });
    return counts;
  }, [cards, status]);

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

  const handleCopyCard = async (title: string, data: Record<string, any>) => {
    try {
      if (!data) {
        toast.error('No data to copy');
        return;
      }
      await navigator.clipboard.writeText(JSON.stringify(data, null, 2));
      toast.success(`${title} copied`);
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
          <FormControlLabel
            control={
              <Switch
                checked={autoRefresh}
                onChange={(event) => setAutoRefresh(event.target.checked)}
                color="primary"
              />
            }
            label="Auto-refresh"
          />
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
          {statusSummary && (
            <Card variant="outlined">
              <CardContent>
                <Box display="flex" alignItems="center" justifyContent="space-between" flexWrap="wrap" gap={1}>
                  <Typography variant="h6">Overall Status</Typography>
                  <Box display="flex" gap={1} flexWrap="wrap">
                    {statusSummary.healthy > 0 && (
                      <Chip label={`Healthy ${statusSummary.healthy}`} color="success" size="small" />
                    )}
                    {statusSummary.warning > 0 && (
                      <Chip label={`Warning ${statusSummary.warning}`} color="warning" size="small" />
                    )}
                    {statusSummary.error > 0 && (
                      <Chip label={`Error ${statusSummary.error}`} color="error" size="small" />
                    )}
                    {statusSummary.disabled > 0 && (
                      <Chip label={`Disabled ${statusSummary.disabled}`} variant="outlined" size="small" />
                    )}
                  </Box>
                </Box>
              </CardContent>
            </Card>
          )}
          {cards.map((card) => {
            const statusMeta = getStatusMeta(card.data);
            const expanded = expandedCards[card.title] ?? true;
            return (
              <Card key={card.title} variant="outlined">
                <CardContent>
                  <Box display="flex" alignItems="center" justifyContent="space-between" gap={1}>
                    <Typography variant="h6">{card.title}</Typography>
                    <Box display="flex" alignItems="center" gap={1}>
                      <Chip
                        size="small"
                        label={statusMeta.label}
                        color={statusMeta.color}
                        variant={statusMeta.level === 'disabled' ? 'outlined' : 'filled'}
                      />
                      <IconButton
                        size="small"
                        aria-label={`Copy ${card.title}`}
                        onClick={() => handleCopyCard(card.title, card.data)}
                      >
                        <ContentCopy fontSize="small" />
                      </IconButton>
                      <IconButton
                        size="small"
                        aria-label={expanded ? `Collapse ${card.title}` : `Expand ${card.title}`}
                        onClick={() =>
                          setExpandedCards((prev) => ({ ...prev, [card.title]: !(prev[card.title] ?? true) }))
                        }
                      >
                        {expanded ? <ExpandLess fontSize="small" /> : <ExpandMore fontSize="small" />}
                      </IconButton>
                    </Box>
                  </Box>
                  <Divider sx={{ mb: 2 }} />
                  <Collapse in={expanded} timeout="auto" unmountOnExit>
                    <Typography component="pre" variant="body2" sx={{ whiteSpace: 'pre-wrap', wordBreak: 'break-word' }}>
                      {JSON.stringify(card.data, null, 2)}
                    </Typography>
                  </Collapse>
                  {!expanded && (
                    <Typography variant="body2" color="text.secondary">
                      Details hidden
                    </Typography>
                  )}
                </CardContent>
              </Card>
            );
          })}

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
