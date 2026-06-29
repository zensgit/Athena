import React, { useEffect, useState } from 'react';
import {
  Alert,
  Box,
  Card,
  CardContent,
  Chip,
  CircularProgress,
  Divider,
  Link,
  Stack,
  Typography,
} from '@mui/material';
import { Link as RouterLink } from 'react-router-dom';
import { toast } from 'react-toastify';
import apiService from '../../services/api';

/**
 * Cross-subsystem failure inventory (taskbook §4 first-cut). The ONE new signal is the preview
 * dead-letter count (+ non-PII category tally + latest failure time); transfer FAILED and mail
 * account-level ERROR counts are reused from the queue-backlog service. PII-safe: counts / timestamps
 * / categories only — every panel LINKS OUT to the existing ADMIN-gated deep surface for raw detail.
 */
export interface FailureInventorySummary {
  preview: {
    available: boolean;
    deadLetterCount: number;
    categoryTally: Record<string, number>;
    latestFailedAt: string | null;
  };
  transfer: {
    available: boolean;
    failedCount: number;
  };
  mail: {
    available: boolean;
    errorAccountCount: number;
  };
}

const fmtTime = (iso: string | null): string => (iso ? new Date(iso).toLocaleString() : '—');

const Unavailable: React.FC = () => (
  <Typography variant="body2" color="text.disabled">
    unavailable
  </Typography>
);

/**
 * Read-only "Failure Inventory" card: a failure-triage hub. Fetches the cross-subsystem inventory
 * and is FAILURE-ISOLATED (a fetch error shows a local warning + toast, never breaks the dashboard).
 * Each panel links out to its deep ADMIN-gated surface. Observability only — no replay / clear / retry.
 */
const FailureInventoryCard: React.FC = () => {
  const [data, setData] = useState<FailureInventorySummary | null>(null);
  const [loading, setLoading] = useState(true);
  const [failed, setFailed] = useState(false);

  useEffect(() => {
    let active = true;
    (async () => {
      setLoading(true);
      setFailed(false);
      try {
        const res = await apiService.get<FailureInventorySummary>('/admin/failure-inventory');
        if (active) {
          setData(res ?? null);
        }
      } catch {
        if (active) {
          setFailed(true);
          toast.warn('Failed to load failure inventory');
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
          Failure Inventory
        </Typography>
        <Typography variant="body2" color="text.secondary" gutterBottom>
          Read-only cross-subsystem failure triage (preview dead-letters · transfer failures · mail fetch errors).
          Counts only — open the linked deep surface for detail. Observability only — no replay or retry.
        </Typography>
        {loading && (
          <Box py={2} display="flex" justifyContent="center">
            <CircularProgress size={24} />
          </Box>
        )}
        {!loading && failed && (
          <Alert severity="warning">Failure inventory is unavailable right now.</Alert>
        )}
        {!loading && !failed && data && (
          <Stack divider={<Divider flexItem />} spacing={2}>
            <Box>
              <Typography variant="subtitle2">Preview dead-letters</Typography>
              {data.preview.available ? (
                <Stack direction="row" spacing={1} alignItems="center" flexWrap="wrap">
                  <Typography variant="body2" color="text.secondary">
                    Dead-letters: <b>{data.preview.deadLetterCount}</b> · Latest: <b>{fmtTime(data.preview.latestFailedAt)}</b>
                  </Typography>
                  {Object.entries(data.preview.categoryTally).map(([category, count]) => (
                    <Chip key={category} size="small" label={`${category}: ${count}`} />
                  ))}
                </Stack>
              ) : (
                <Unavailable />
              )}
              <Link component={RouterLink} to="/admin/preview-diagnostics" variant="body2">
                Open preview diagnostics
              </Link>
            </Box>

            <Box>
              <Typography variant="subtitle2">Transfer replication</Typography>
              {data.transfer.available ? (
                <Typography variant="body2" color="text.secondary">
                  Failed jobs: <b>{data.transfer.failedCount}</b>
                </Typography>
              ) : (
                <Unavailable />
              )}
              <Link component={RouterLink} to="/admin/transfer-replication" variant="body2">
                Open transfer jobs
              </Link>
            </Box>

            <Box>
              <Typography variant="subtitle2">Mail fetch</Typography>
              {data.mail.available ? (
                <Typography variant="body2" color="text.secondary">
                  Accounts in error: <b>{data.mail.errorAccountCount}</b>
                </Typography>
              ) : (
                <Unavailable />
              )}
              <Link component={RouterLink} to="/admin/mail" variant="body2">
                Open mail diagnostics
              </Link>
            </Box>
          </Stack>
        )}
      </CardContent>
    </Card>
  );
};

export default FailureInventoryCard;
