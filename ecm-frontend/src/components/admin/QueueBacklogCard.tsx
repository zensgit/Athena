import React, { useEffect, useState } from 'react';
import {
  Alert,
  Box,
  Card,
  CardContent,
  Chip,
  CircularProgress,
  Divider,
  Stack,
  Typography,
} from '@mui/material';
import { toast } from 'react-toastify';
import apiService from '../../services/api';

export interface QueueBacklogSummary {
  ocr: {
    available: boolean;
    pendingDepth: number;
    oldestPendingAgeSeconds: number | null;
  };
  mail: {
    available: boolean;
    lastSuccessAt: string | null;
    errorRate: number;
    errors: number;
    status: string | null;
  };
  transfer: {
    available: boolean;
    pendingCount: number;
    runningCount: number;
    failedCount: number;
    oldestPendingAgeSeconds: number | null;
    stuckRunningCount: number;
    stuckThresholdMinutes: number;
  };
}

const fmtAge = (seconds: number | null): string => {
  if (seconds == null) {
    return '—';
  }
  if (seconds < 60) {
    return `${seconds}s`;
  }
  const minutes = Math.floor(seconds / 60);
  if (minutes < 60) {
    return `${minutes}m`;
  }
  return `${Math.floor(minutes / 60)}h ${minutes % 60}m`;
};

const fmtTime = (iso: string | null): string => (iso ? new Date(iso).toLocaleString() : '—');

const Unavailable: React.FC = () => (
  <Typography variant="body2" color="text.disabled">
    unavailable
  </Typography>
);

/**
 * Read-only "Queue Backlog" card (Day-2 queue backlog observability). Fetches OCR / mail / transfer
 * backlog independently and is FAILURE-ISOLATED: a fetch error shows a local warning + toast and never
 * breaks the surrounding AdminDashboard panels. A single unavailable subsystem renders a muted note for
 * its panel only. Observability only — no requeue / cancel / trigger.
 */
const QueueBacklogCard: React.FC = () => {
  const [data, setData] = useState<QueueBacklogSummary | null>(null);
  const [loading, setLoading] = useState(true);
  const [failed, setFailed] = useState(false);

  useEffect(() => {
    let active = true;
    (async () => {
      setLoading(true);
      setFailed(false);
      try {
        const res = await apiService.get<QueueBacklogSummary>('/admin/queue-backlog');
        if (active) {
          setData(res ?? null);
        }
      } catch {
        if (active) {
          setFailed(true);
          toast.warn('Failed to load queue backlog');
        }
      } finally {
        if (active) {
          setLoading(false);
        }
      }
    })();
    return () => {
      active = false;
    };
  }, []);

  return (
    <Card>
      <CardContent>
        <Typography variant="h6" gutterBottom>
          Queue Backlog
        </Typography>
        <Typography variant="body2" color="text.secondary" gutterBottom>
          Read-only OCR / mail / transfer backlog (depth, oldest-pending, failures). Observability only — no requeue or cancel.
        </Typography>
        {loading && (
          <Box py={2} display="flex" justifyContent="center">
            <CircularProgress size={24} />
          </Box>
        )}
        {!loading && failed && (
          <Alert severity="warning">Queue backlog is unavailable right now.</Alert>
        )}
        {!loading && !failed && data && (
          <Stack divider={<Divider flexItem />} spacing={2}>
            <Box>
              <Typography variant="subtitle2">OCR</Typography>
              {data.ocr.available ? (
                <Typography variant="body2" color="text.secondary">
                  Pending depth: <b>{data.ocr.pendingDepth}</b> · Oldest pending: <b>{fmtAge(data.ocr.oldestPendingAgeSeconds)}</b>
                </Typography>
              ) : (
                <Unavailable />
              )}
            </Box>

            <Box>
              <Typography variant="subtitle2">Mail fetch</Typography>
              {data.mail.available ? (
                <Stack direction="row" spacing={1} alignItems="center" flexWrap="wrap">
                  <Chip
                    size="small"
                    label={data.mail.status ?? 'UNKNOWN'}
                    color={data.mail.errorRate > 0 ? 'warning' : 'success'}
                  />
                  <Typography variant="body2" color="text.secondary">
                    Last success: <b>{fmtTime(data.mail.lastSuccessAt)}</b> · Error rate: <b>{Math.round(data.mail.errorRate * 100)}%</b> · Errors: <b>{data.mail.errors}</b>
                  </Typography>
                </Stack>
              ) : (
                <Unavailable />
              )}
            </Box>

            <Box>
              <Typography variant="subtitle2">Transfer replication</Typography>
              {data.transfer.available ? (
                <Typography variant="body2" color="text.secondary">
                  Pending: <b>{data.transfer.pendingCount}</b> · Running: <b>{data.transfer.runningCount}</b> · Failed: <b>{data.transfer.failedCount}</b>
                  {' · '}Oldest pending: <b>{fmtAge(data.transfer.oldestPendingAgeSeconds)}</b>
                  {' · '}RUNNING &gt; {data.transfer.stuckThresholdMinutes}m: <b>{data.transfer.stuckRunningCount}</b>
                </Typography>
              ) : (
                <Unavailable />
              )}
            </Box>
          </Stack>
        )}
      </CardContent>
    </Card>
  );
};

export default QueueBacklogCard;
