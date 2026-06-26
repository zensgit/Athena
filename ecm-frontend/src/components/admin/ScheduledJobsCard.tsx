import React, { useEffect, useState } from 'react';
import {
  Alert,
  Box,
  Card,
  CardContent,
  Chip,
  CircularProgress,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  Typography,
} from '@mui/material';
import { toast } from 'react-toastify';
import apiService from '../../services/api';

export interface SchedulerJobSnapshot {
  jobId: string;
  lastRunAt: string | null;
  lastStatus: 'SUCCESS' | 'FAILED' | 'RUNNING' | null;
  lastDurationMs: number | null;
  lastErrorType: string | null;
  runCount: number;
  failCount: number;
  nextRunAt: string | null;
  scheduleDescription: string;
}

const statusColor = (
  status: SchedulerJobSnapshot['lastStatus'],
): 'success' | 'error' | 'info' | 'default' => {
  switch (status) {
    case 'SUCCESS':
      return 'success';
    case 'FAILED':
      return 'error';
    case 'RUNNING':
      return 'info';
    default:
      return 'default';
  }
};

const fmt = (iso: string | null): string => (iso ? new Date(iso).toLocaleString() : '—');

const shortJob = (jobId: string): string => {
  const hash = jobId.indexOf('#');
  if (hash < 0) {
    return jobId;
  }
  const className = jobId.substring(0, hash);
  const simpleName = className.substring(className.lastIndexOf('.') + 1);
  return simpleName + jobId.substring(hash);
};

/**
 * Read-only "Scheduled Jobs" card (Day-2 scheduler-run observability). Fetches the admin scheduler
 * snapshot independently and is FAILURE-ISOLATED: a fetch error shows a local warning and a toast,
 * it never breaks the surrounding AdminDashboard panels. Observability only — no trigger/cancel.
 */
const ScheduledJobsCard: React.FC = () => {
  const [jobs, setJobs] = useState<SchedulerJobSnapshot[]>([]);
  const [loading, setLoading] = useState(true);
  const [failed, setFailed] = useState(false);

  useEffect(() => {
    let active = true;
    (async () => {
      setLoading(true);
      setFailed(false);
      try {
        const data = await apiService.get<SchedulerJobSnapshot[]>('/admin/schedulers');
        if (active) {
          setJobs(Array.isArray(data) ? data : []);
        }
      } catch {
        if (active) {
          setFailed(true);
          toast.warn('Failed to load scheduled-job status');
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
          Scheduled Jobs
        </Typography>
        <Typography variant="body2" color="text.secondary" gutterBottom>
          Read-only last-run / next-run status for recurring @Scheduled jobs (since boot). Observability only — no trigger or cancel.
        </Typography>
        {loading && (
          <Box py={2} display="flex" justifyContent="center">
            <CircularProgress size={24} />
          </Box>
        )}
        {!loading && failed && (
          <Alert severity="warning">Scheduled-job status is unavailable right now.</Alert>
        )}
        {!loading && !failed && (
          <TableContainer>
            <Table size="small" aria-label="scheduled jobs">
              <TableHead>
                <TableRow>
                  <TableCell>Job</TableCell>
                  <TableCell>Status</TableCell>
                  <TableCell>Last run</TableCell>
                  <TableCell>Next run</TableCell>
                  <TableCell>Duration</TableCell>
                  <TableCell>Last error</TableCell>
                </TableRow>
              </TableHead>
              <TableBody>
                {jobs.length === 0 && (
                  <TableRow>
                    <TableCell colSpan={6}>No scheduled jobs registered.</TableCell>
                  </TableRow>
                )}
                {jobs.map((job) => (
                  <TableRow key={job.jobId}>
                    <TableCell title={job.jobId}>{shortJob(job.jobId)}</TableCell>
                    <TableCell>
                      <Chip size="small" label={job.lastStatus ?? 'NEVER'} color={statusColor(job.lastStatus)} />
                    </TableCell>
                    <TableCell>{fmt(job.lastRunAt)}</TableCell>
                    <TableCell>{job.nextRunAt ? fmt(job.nextRunAt) : job.scheduleDescription}</TableCell>
                    <TableCell>{job.lastDurationMs != null ? `${job.lastDurationMs} ms` : '—'}</TableCell>
                    <TableCell>{job.lastErrorType ?? '—'}</TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </TableContainer>
        )}
      </CardContent>
    </Card>
  );
};

export default ScheduledJobsCard;
