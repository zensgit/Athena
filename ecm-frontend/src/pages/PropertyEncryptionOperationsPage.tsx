import React, { useEffect, useState } from 'react';
import {
  Alert,
  Box,
  Button,
  Card,
  CardContent,
  CardHeader,
  Chip,
  CircularProgress,
  Divider,
  Grid,
  Stack,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  TextField,
  Typography,
} from '@mui/material';
import {
  Lock,
  PlayArrow,
  Refresh,
  Science,
  StopCircle,
  TaskAlt,
} from '@mui/icons-material';
import { toast } from 'react-toastify';
import propertyEncryptionService, {
  BackfillJobStatus,
  EncryptedPropertyDefinitionSummary,
  PropertyEncryptionBackfillDryRunResult,
  PropertyEncryptionBackfillJobDto,
  PropertyEncryptionRewrapDryRunResult,
  PropertyEncryptionStatus,
} from 'services/propertyEncryptionService';

const terminalStatuses = new Set<BackfillJobStatus>([
  'SUCCEEDED',
  'FAILED',
  'CANCELLED',
]);

const statusColor: Record<BackfillJobStatus, 'default' | 'primary' | 'success' | 'error' | 'warning'> = {
  PLANNED: 'default',
  RUNNING: 'primary',
  SUCCEEDED: 'success',
  FAILED: 'error',
  CANCEL_REQUESTED: 'warning',
  CANCELLED: 'warning',
};

const formatDateTime = (value?: string | null) => {
  if (!value) {
    return '-';
  }
  const parsed = new Date(value);
  if (Number.isNaN(parsed.getTime())) {
    return value;
  }
  return parsed.toLocaleString();
};

const formatNumber = (value?: number | null) => (value ?? 0).toLocaleString();

const getErrorMessage = (error: unknown, fallback: string) => {
  if (error instanceof Error && error.message) {
    return error.message;
  }
  if (error && typeof error === 'object' && 'response' in error) {
    const response = (error as { response?: { data?: { message?: string } } }).response;
    if (response?.data?.message) {
      return response.data.message;
    }
  }
  return fallback;
};

const PropertyEncryptionOperationsPage: React.FC = () => {
  const [loading, setLoading] = useState(false);
  const [actionLoading, setActionLoading] = useState<string | null>(null);
  const [status, setStatus] = useState<PropertyEncryptionStatus | null>(null);
  const [definitions, setDefinitions] = useState<EncryptedPropertyDefinitionSummary[]>([]);
  const [jobs, setJobs] = useState<PropertyEncryptionBackfillJobDto[]>([]);
  const [targetKeyVersion, setTargetKeyVersion] = useState('');
  const [backfillDryRun, setBackfillDryRun] = useState<PropertyEncryptionBackfillDryRunResult | null>(null);
  const [rewrapDryRun, setRewrapDryRun] = useState<PropertyEncryptionRewrapDryRunResult | null>(null);

  const effectiveTargetKeyVersion = targetKeyVersion.trim() || status?.activeKeyVersion || undefined;

  const loadData = async () => {
    setLoading(true);
    try {
      const [nextStatus, nextDefinitions, nextJobs] = await Promise.all([
        propertyEncryptionService.getStatus(),
        propertyEncryptionService.listDefinitions(),
        propertyEncryptionService.listBackfillJobs(10),
      ]);
      setStatus(nextStatus);
      setDefinitions(nextDefinitions);
      setJobs(nextJobs);
      if (!targetKeyVersion.trim() && nextStatus.activeKeyVersion) {
        setTargetKeyVersion(nextStatus.activeKeyVersion);
      }
    } catch (error) {
      const message = getErrorMessage(error, 'Failed to load property encryption operations.');
      toast.error(message);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    void loadData();
    // Initial load only. Subsequent refreshes are explicit admin actions.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const refreshJobs = async () => {
    const nextJobs = await propertyEncryptionService.listBackfillJobs(10);
    setJobs(nextJobs);
  };

  const handleBackfillDryRun = async () => {
    setActionLoading('backfill-dry-run');
    try {
      const result = await propertyEncryptionService.dryRunBackfill(effectiveTargetKeyVersion);
      setBackfillDryRun(result);
      toast.success('Backfill dry-run completed.');
    } catch (error) {
      toast.error(getErrorMessage(error, 'Backfill dry-run failed.'));
    } finally {
      setActionLoading(null);
    }
  };

  const handleRewrapDryRun = async () => {
    setActionLoading('rewrap-dry-run');
    try {
      const result = await propertyEncryptionService.dryRunRewrap(effectiveTargetKeyVersion);
      setRewrapDryRun(result);
      toast.success('Rewrap dry-run completed.');
    } catch (error) {
      toast.error(getErrorMessage(error, 'Rewrap dry-run failed.'));
    } finally {
      setActionLoading(null);
    }
  };

  const handlePlanBackfill = async () => {
    setActionLoading('plan-backfill');
    try {
      const job = await propertyEncryptionService.planBackfillJob(effectiveTargetKeyVersion);
      setJobs((current) => [job, ...current.filter((item) => item.id !== job.id)]);
      toast.success('Backfill job planned.');
    } catch (error) {
      toast.error(getErrorMessage(error, 'Backfill job planning failed.'));
    } finally {
      setActionLoading(null);
    }
  };

  const handleRunBackfill = async (jobId: string) => {
    setActionLoading(`run-${jobId}`);
    try {
      const job = await propertyEncryptionService.runBackfillJob(jobId);
      setJobs((current) => current.map((item) => (item.id === job.id ? job : item)));
      toast.success('Backfill job started.');
    } catch (error) {
      toast.error(getErrorMessage(error, 'Backfill job start failed.'));
    } finally {
      setActionLoading(null);
    }
  };

  const handleCancelBackfill = async (jobId: string) => {
    setActionLoading(`cancel-${jobId}`);
    try {
      const job = await propertyEncryptionService.cancelBackfillJob(jobId);
      setJobs((current) => current.map((item) => (item.id === job.id ? job : item)));
      toast.success('Backfill job cancellation requested.');
    } catch (error) {
      toast.error(getErrorMessage(error, 'Backfill job cancellation failed.'));
    } finally {
      setActionLoading(null);
    }
  };

  return (
    <Box sx={{ p: 3 }}>
      <Stack direction={{ xs: 'column', md: 'row' }} justifyContent="space-between" spacing={2} sx={{ mb: 3 }}>
        <Box>
          <Typography variant="h4" gutterBottom>
            Property Encryption
          </Typography>
          <Typography variant="body2" color="text.secondary">
            Monitor encrypted model properties and operate safe backfill jobs without exposing property values.
          </Typography>
        </Box>
        <Button
          variant="outlined"
          startIcon={loading ? <CircularProgress size={16} /> : <Refresh />}
          disabled={loading}
          onClick={() => void loadData()}
        >
          Refresh
        </Button>
      </Stack>

      <Grid container spacing={2}>
        <Grid item xs={12} lg={5}>
          <Card>
            <CardHeader
              avatar={<Lock color="primary" />}
              title="Crypto status"
              subheader="Configuration health and encrypted payload totals"
            />
            <CardContent>
              {!status && loading && <CircularProgress size={24} aria-label="Loading property encryption status" />}
              {status && (
                <Stack spacing={2}>
                  <Stack direction="row" spacing={1} flexWrap="wrap" useFlexGap>
                    <Chip
                      label={status.secretCryptoEnabled ? 'Secret crypto enabled' : 'Secret crypto disabled'}
                      color={status.secretCryptoEnabled ? 'success' : 'error'}
                    />
                    <Chip
                      label={`Active key: ${status.activeKeyVersion || '-'}`}
                      color={status.activeKeyConfigured ? 'success' : 'warning'}
                      variant="outlined"
                    />
                    <Chip
                      label={`Configured keys: ${status.configuredKeyVersions.length}`}
                      variant="outlined"
                    />
                  </Stack>

                  {status.warnings.length > 0 && (
                    <Alert severity="warning">
                      {status.warnings.join(', ')}
                    </Alert>
                  )}

                  <Grid container spacing={1}>
                    <Grid item xs={6}>
                      <Typography variant="caption" color="text.secondary">Definitions</Typography>
                      <Typography variant="h6">{formatNumber(status.encryptedPropertyDefinitionCount)}</Typography>
                    </Grid>
                    <Grid item xs={6}>
                      <Typography variant="caption" color="text.secondary">Encrypted values</Typography>
                      <Typography variant="h6">{formatNumber(status.encryptedPropertyValueCount)}</Typography>
                    </Grid>
                    <Grid item xs={6}>
                      <Typography variant="caption" color="text.secondary">Nodes with encrypted values</Typography>
                      <Typography variant="h6">{formatNumber(status.nodesWithEncryptedPropertiesCount)}</Typography>
                    </Grid>
                    <Grid item xs={6}>
                      <Typography variant="caption" color="text.secondary">Type / aspect definitions</Typography>
                      <Typography variant="h6">
                        {formatNumber(status.encryptedTypePropertyDefinitionCount)} / {formatNumber(status.encryptedAspectPropertyDefinitionCount)}
                      </Typography>
                    </Grid>
                  </Grid>
                </Stack>
              )}
            </CardContent>
          </Card>
        </Grid>

        <Grid item xs={12} lg={7}>
          <Card>
            <CardHeader
              avatar={<Science color="primary" />}
              title="Dry-runs and planning"
              subheader="Use dry-run output before creating a persistent backfill job"
            />
            <CardContent>
              <Stack spacing={2}>
                <TextField
                  label="Target key version"
                  value={targetKeyVersion}
                  onChange={(event) => setTargetKeyVersion(event.target.value)}
                  helperText="Defaults to the active backend key version."
                  size="small"
                />
                <Stack direction={{ xs: 'column', sm: 'row' }} spacing={1}>
                  <Button
                    variant="contained"
                    onClick={() => void handleBackfillDryRun()}
                    disabled={Boolean(actionLoading)}
                    startIcon={actionLoading === 'backfill-dry-run' ? <CircularProgress size={16} /> : <Science />}
                  >
                    Backfill Dry Run
                  </Button>
                  <Button
                    variant="outlined"
                    onClick={() => void handlePlanBackfill()}
                    disabled={Boolean(actionLoading) || backfillDryRun?.executable === false}
                    startIcon={actionLoading === 'plan-backfill' ? <CircularProgress size={16} /> : <TaskAlt />}
                  >
                    Plan Backfill Job
                  </Button>
                  <Button
                    variant="outlined"
                    onClick={() => void handleRewrapDryRun()}
                    disabled={Boolean(actionLoading)}
                    startIcon={actionLoading === 'rewrap-dry-run' ? <CircularProgress size={16} /> : <Science />}
                  >
                    Rewrap Dry Run
                  </Button>
                </Stack>

                {backfillDryRun && (
                  <Alert severity={backfillDryRun.executable ? 'success' : 'warning'}>
                    Backfill dry-run: {backfillDryRun.executable ? 'executable' : 'blocked'}.
                    Ready {formatNumber(backfillDryRun.readyValueCount)} of {formatNumber(backfillDryRun.plaintextValueCount)} plaintext values.
                    {backfillDryRun.warnings.length > 0 ? ` Warnings: ${backfillDryRun.warnings.join(', ')}` : ''}
                  </Alert>
                )}

                {rewrapDryRun && (
                  <Alert severity={rewrapDryRun.executable ? 'info' : 'warning'}>
                    Rewrap dry-run only: {formatNumber(rewrapDryRun.valuesRequiringRewrapCount)} values require rewrap to {rewrapDryRun.targetKeyVersion || '-'}.
                    {rewrapDryRun.warnings.length > 0 ? ` Warnings: ${rewrapDryRun.warnings.join(', ')}` : ''}
                  </Alert>
                )}
              </Stack>
            </CardContent>
          </Card>
        </Grid>

        <Grid item xs={12}>
          <Card>
            <CardHeader title="Encrypted property definitions" />
            <CardContent>
              <TableContainer>
                <Table size="small" aria-label="Encrypted property definitions">
                  <TableHead>
                    <TableRow>
                      <TableCell>Qualified name</TableCell>
                      <TableCell>Owner</TableCell>
                      <TableCell>Type</TableCell>
                      <TableCell>Flags</TableCell>
                    </TableRow>
                  </TableHead>
                  <TableBody>
                    {definitions.map((definition) => (
                      <TableRow key={definition.id}>
                        <TableCell>
                          <Typography variant="body2" fontWeight={600}>{definition.qualifiedName}</Typography>
                          <Typography variant="caption" color="text.secondary">{definition.title || definition.name}</Typography>
                        </TableCell>
                        <TableCell>{definition.ownerKind} {definition.ownerQName || '-'}</TableCell>
                        <TableCell>{definition.dataType}</TableCell>
                        <TableCell>
                          <Stack direction="row" spacing={0.5} flexWrap="wrap" useFlexGap>
                            {definition.mandatory && <Chip label="Mandatory" size="small" />}
                            {definition.multiValued && <Chip label="Multi-valued" size="small" />}
                            {definition.indexed && <Chip label="Indexed" size="small" variant="outlined" />}
                          </Stack>
                        </TableCell>
                      </TableRow>
                    ))}
                    {definitions.length === 0 && (
                      <TableRow>
                        <TableCell colSpan={4}>
                          <Typography variant="body2" color="text.secondary">No encrypted property definitions found.</Typography>
                        </TableCell>
                      </TableRow>
                    )}
                  </TableBody>
                </Table>
              </TableContainer>
            </CardContent>
          </Card>
        </Grid>

        <Grid item xs={12}>
          <Card>
            <CardHeader
              title="Backfill jobs"
              action={
                <Button size="small" onClick={() => void refreshJobs()} startIcon={<Refresh />}>
                  Refresh Jobs
                </Button>
              }
            />
            <CardContent>
              <TableContainer>
                <Table size="small" aria-label="Property encryption backfill jobs">
                  <TableHead>
                    <TableRow>
                      <TableCell>Status</TableCell>
                      <TableCell>Target key</TableCell>
                      <TableCell>Requested</TableCell>
                      <TableCell>Counters</TableCell>
                      <TableCell>Error</TableCell>
                      <TableCell align="right">Actions</TableCell>
                    </TableRow>
                  </TableHead>
                  <TableBody>
                    {jobs.map((job) => (
                      <TableRow key={job.id}>
                        <TableCell>
                          <Chip label={job.status} color={statusColor[job.status]} size="small" />
                        </TableCell>
                        <TableCell>{job.targetKeyVersion || '-'}</TableCell>
                        <TableCell>
                          <Typography variant="body2">{job.requestedBy}</Typography>
                          <Typography variant="caption" color="text.secondary">{formatDateTime(job.requestedAt)}</Typography>
                        </TableCell>
                        <TableCell>
                          Processed {formatNumber(job.processedValueCount)} / migrated {formatNumber(job.migratedValueCount)}
                          <br />
                          Ready snapshot {formatNumber(job.readyValueCount)}
                        </TableCell>
                        <TableCell>{job.lastError || '-'}</TableCell>
                        <TableCell align="right">
                          <Stack direction="row" spacing={1} justifyContent="flex-end">
                            <Button
                              size="small"
                              startIcon={actionLoading === `run-${job.id}` ? <CircularProgress size={14} /> : <PlayArrow />}
                              disabled={Boolean(actionLoading) || job.status !== 'PLANNED'}
                              onClick={() => void handleRunBackfill(job.id)}
                            >
                              Run
                            </Button>
                            <Button
                              size="small"
                              color="warning"
                              startIcon={actionLoading === `cancel-${job.id}` ? <CircularProgress size={14} /> : <StopCircle />}
                              disabled={Boolean(actionLoading) || terminalStatuses.has(job.status)}
                              onClick={() => void handleCancelBackfill(job.id)}
                            >
                              Cancel
                            </Button>
                          </Stack>
                        </TableCell>
                      </TableRow>
                    ))}
                    {jobs.length === 0 && (
                      <TableRow>
                        <TableCell colSpan={6}>
                          <Typography variant="body2" color="text.secondary">No backfill jobs found.</Typography>
                        </TableCell>
                      </TableRow>
                    )}
                  </TableBody>
                </Table>
              </TableContainer>
              <Divider sx={{ mt: 2, mb: 1 }} />
              <Typography variant="caption" color="text.secondary">
                Rewrap execution is intentionally not exposed here yet because the backend currently supports rewrap dry-run only.
              </Typography>
            </CardContent>
          </Card>
        </Grid>
      </Grid>
    </Box>
  );
};

export default PropertyEncryptionOperationsPage;
