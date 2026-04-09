import React, { useCallback, useEffect, useMemo, useState } from 'react';
import {
  Alert,
  Box,
  Button,
  Card,
  CardContent,
  Checkbox,
  Chip,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  FormControl,
  FormControlLabel,
  Grid,
  InputLabel,
  MenuItem,
  Paper,
  Select,
  Stack,
  Table,
  TableBody,
  TableCell,
  TableHead,
  TablePagination,
  TableRow,
  TextField,
  Typography,
} from '@mui/material';
import {
  Add,
  Delete,
  PlayArrow,
  PublishedWithChanges,
  Refresh,
  Save,
} from '@mui/icons-material';
import { toast } from 'react-toastify';
import transferReplicationService, {
  AuthType,
  ReplicationDefinitionDto,
  ReplicationDefinitionMutationRequest,
  ReplicationJobDto,
  TransportType,
  TransferTargetDto,
  TransferTargetMutationRequest,
  VerificationStatus,
} from 'services/transferReplicationService';

const EMPTY_TARGET_FORM: TransferTargetMutationRequest = {
  name: '',
  description: '',
  transportType: 'LOOPBACK',
  targetFolderId: '',
  endpointUrl: '',
  endpointPath: '/api/v1',
  authType: 'NONE',
  authUsername: '',
  authSecret: '',
  enabled: true,
};

const EMPTY_DEFINITION_FORM: ReplicationDefinitionMutationRequest = {
  name: '',
  description: '',
  sourceNodeId: '',
  transferTargetId: '',
  includeChildren: true,
  enabled: true,
};

const verificationColor: Record<VerificationStatus, 'default' | 'success' | 'warning' | 'error'> = {
  NEVER_VERIFIED: 'warning',
  VERIFIED: 'success',
  FAILED: 'error',
};

const jobColor: Record<ReplicationJobDto['status'], 'default' | 'primary' | 'success' | 'error'> = {
  PENDING: 'default',
  RUNNING: 'primary',
  COMPLETED: 'success',
  FAILED: 'error',
};

const runningStatuses = new Set<ReplicationJobDto['status']>(['PENDING', 'RUNNING']);

const formatTimestamp = (value?: string | null) => {
  if (!value) {
    return '—';
  }
  return new Date(value).toLocaleString();
};

const TransferReplicationPage: React.FC = () => {
  const [targets, setTargets] = useState<TransferTargetDto[]>([]);
  const [definitions, setDefinitions] = useState<ReplicationDefinitionDto[]>([]);
  const [jobs, setJobs] = useState<ReplicationJobDto[]>([]);
  const [jobsTotal, setJobsTotal] = useState(0);
  const [jobsPage, setJobsPage] = useState(0);
  const [jobsRowsPerPage, setJobsRowsPerPage] = useState(10);
  const [loading, setLoading] = useState(false);
  const [targetDialogOpen, setTargetDialogOpen] = useState(false);
  const [definitionDialogOpen, setDefinitionDialogOpen] = useState(false);
  const [selectedTargetId, setSelectedTargetId] = useState<string | null>(null);
  const [selectedDefinitionId, setSelectedDefinitionId] = useState<string | null>(null);
  const [targetForm, setTargetForm] = useState<TransferTargetMutationRequest>(EMPTY_TARGET_FORM);
  const [definitionForm, setDefinitionForm] = useState<ReplicationDefinitionMutationRequest>(EMPTY_DEFINITION_FORM);
  const [refreshTick, setRefreshTick] = useState(0);
  const [savingTarget, setSavingTarget] = useState(false);
  const [savingDefinition, setSavingDefinition] = useState(false);

  const hasRunningJobs = useMemo(() => jobs.some((job) => runningStatuses.has(job.status)), [jobs]);

  const loadData = useCallback(async () => {
    setLoading(true);
    try {
      const [nextTargets, nextDefinitions, jobsPageResult] = await Promise.all([
        transferReplicationService.listTargets(),
        transferReplicationService.listDefinitions(),
        transferReplicationService.listJobs(jobsPage, jobsRowsPerPage),
      ]);
      setTargets(nextTargets);
      setDefinitions(nextDefinitions);
      setJobs(jobsPageResult.content);
      setJobsTotal(jobsPageResult.totalElements);
    } catch {
      toast.error('Failed to load transfer replication data');
    } finally {
      setLoading(false);
    }
  }, [jobsPage, jobsRowsPerPage]);

  useEffect(() => {
    void loadData();
  }, [loadData, refreshTick]);

  useEffect(() => {
    if (!hasRunningJobs) {
      return undefined;
    }
    const timer = window.setInterval(() => setRefreshTick((value) => value + 1), 4000);
    return () => window.clearInterval(timer);
  }, [hasRunningJobs]);

  const openTargetDialog = (target?: TransferTargetDto) => {
    if (target) {
      setSelectedTargetId(target.id);
      setTargetForm({
        name: target.name,
        description: target.description || '',
        transportType: target.transportType,
        targetFolderId: target.targetFolderId,
        endpointUrl: target.endpointUrl || '',
        endpointPath: target.endpointPath || '/api/v1',
        authType: target.authType,
        authUsername: target.authUsername || '',
        authSecret: '',
        enabled: target.enabled,
      });
    } else {
      setSelectedTargetId(null);
      setTargetForm(EMPTY_TARGET_FORM);
    }
    setTargetDialogOpen(true);
  };

  const openDefinitionDialog = (definition?: ReplicationDefinitionDto) => {
    if (definition) {
      setSelectedDefinitionId(definition.id);
      setDefinitionForm({
        name: definition.name,
        description: definition.description || '',
        sourceNodeId: definition.sourceNodeId,
        transferTargetId: definition.transferTargetId,
        includeChildren: definition.includeChildren,
        enabled: definition.enabled,
      });
    } else {
      setSelectedDefinitionId(null);
      setDefinitionForm({
        ...EMPTY_DEFINITION_FORM,
        transferTargetId: targets[0]?.id || '',
      });
    }
    setDefinitionDialogOpen(true);
  };

  const closeTargetDialog = () => {
    setTargetDialogOpen(false);
    setSelectedTargetId(null);
    setTargetForm(EMPTY_TARGET_FORM);
  };

  const closeDefinitionDialog = () => {
    setDefinitionDialogOpen(false);
    setSelectedDefinitionId(null);
    setDefinitionForm(EMPTY_DEFINITION_FORM);
  };

  const buildTargetPayload = (): TransferTargetMutationRequest => {
    const base: TransferTargetMutationRequest = {
      name: targetForm.name.trim(),
      description: targetForm.description?.trim() || undefined,
      transportType: targetForm.transportType,
      targetFolderId: targetForm.targetFolderId.trim(),
      enabled: targetForm.enabled,
    };

    if (targetForm.transportType === 'ATHENA_HTTP') {
      base.endpointUrl = targetForm.endpointUrl?.trim() || undefined;
      base.endpointPath = targetForm.endpointPath?.trim() || undefined;
      base.authType = targetForm.authType;
      base.authUsername = targetForm.authUsername?.trim() || undefined;
      if (targetForm.authSecret?.trim()) {
        base.authSecret = targetForm.authSecret.trim();
      }
    }

    return base;
  };

  const handleSaveTarget = async () => {
    setSavingTarget(true);
    try {
      const payload = buildTargetPayload();
      if (selectedTargetId) {
        await transferReplicationService.updateTarget(selectedTargetId, payload);
        toast.success('Transfer target updated');
      } else {
        await transferReplicationService.createTarget(payload);
        toast.success('Transfer target created');
      }
      closeTargetDialog();
      setRefreshTick((value) => value + 1);
    } catch (error: any) {
      toast.error(error?.response?.data?.message || 'Failed to save transfer target');
    } finally {
      setSavingTarget(false);
    }
  };

  const handleVerifyTarget = async (targetId: string) => {
    try {
      await transferReplicationService.verifyTarget(targetId);
      toast.success('Transfer target verified');
      setRefreshTick((value) => value + 1);
    } catch (error: any) {
      toast.error(error?.response?.data?.message || 'Target verification failed');
      setRefreshTick((value) => value + 1);
    }
  };

  const handleDeleteTarget = async (targetId: string) => {
    if (!window.confirm('Delete this transfer target?')) {
      return;
    }
    try {
      await transferReplicationService.deleteTarget(targetId);
      toast.success('Transfer target deleted');
      setRefreshTick((value) => value + 1);
    } catch (error: any) {
      toast.error(error?.response?.data?.message || 'Failed to delete transfer target');
    }
  };

  const handleSaveDefinition = async () => {
    setSavingDefinition(true);
    try {
      const payload: ReplicationDefinitionMutationRequest = {
        name: definitionForm.name.trim(),
        description: definitionForm.description?.trim() || undefined,
        sourceNodeId: definitionForm.sourceNodeId.trim(),
        transferTargetId: definitionForm.transferTargetId,
        includeChildren: definitionForm.includeChildren,
        enabled: definitionForm.enabled,
      };
      if (selectedDefinitionId) {
        await transferReplicationService.updateDefinition(selectedDefinitionId, payload);
        toast.success('Replication definition updated');
      } else {
        await transferReplicationService.createDefinition(payload);
        toast.success('Replication definition created');
      }
      closeDefinitionDialog();
      setRefreshTick((value) => value + 1);
    } catch (error: any) {
      toast.error(error?.response?.data?.message || 'Failed to save replication definition');
    } finally {
      setSavingDefinition(false);
    }
  };

  const handleDeleteDefinition = async (definitionId: string) => {
    if (!window.confirm('Delete this replication definition?')) {
      return;
    }
    try {
      await transferReplicationService.deleteDefinition(definitionId);
      toast.success('Replication definition deleted');
      setRefreshTick((value) => value + 1);
    } catch (error: any) {
      toast.error(error?.response?.data?.message || 'Failed to delete replication definition');
    }
  };

  const handleRunDefinition = async (definitionId: string) => {
    try {
      await transferReplicationService.runDefinition(definitionId);
      toast.success('Replication job queued');
      setJobsPage(0);
      setRefreshTick((value) => value + 1);
    } catch (error: any) {
      toast.error(error?.response?.data?.message || 'Failed to start replication job');
    }
  };

  return (
    <Box sx={{ p: 3 }}>
      <Stack direction="row" justifyContent="space-between" alignItems="center" spacing={2} sx={{ mb: 3 }}>
        <Box>
          <Typography variant="h4" gutterBottom>
            Transfer Replication
          </Typography>
          <Typography variant="body2" color="text.secondary">
            Manage outbound transfer targets, replication definitions, remote verification, and execution jobs.
          </Typography>
        </Box>
        <Button variant="outlined" startIcon={<Refresh />} onClick={() => setRefreshTick((value) => value + 1)} disabled={loading}>
          Refresh
        </Button>
      </Stack>

      <Stack spacing={3}>
        <Alert severity="info">
          LOOPBACK keeps local same-repo replication. ATHENA_HTTP verifies a remote Athena target and replicates content
          via folder and upload APIs.
        </Alert>

        <Grid container spacing={3}>
          <Grid item xs={12} xl={6}>
            <Card>
              <CardContent>
                <Stack direction="row" justifyContent="space-between" alignItems="center" sx={{ mb: 2 }}>
                  <Box>
                    <Typography variant="h6">Transfer Targets</Typography>
                    <Typography variant="body2" color="text.secondary">
                      Configure local loopback and remote Athena outbound destinations.
                    </Typography>
                  </Box>
                  <Button variant="contained" startIcon={<Add />} onClick={() => openTargetDialog()}>
                    New Target
                  </Button>
                </Stack>

                <Table size="small">
                  <TableHead>
                    <TableRow>
                      <TableCell>Name</TableCell>
                      <TableCell>Transport</TableCell>
                      <TableCell>Folder</TableCell>
                      <TableCell>Verification</TableCell>
                      <TableCell align="right">Actions</TableCell>
                    </TableRow>
                  </TableHead>
                  <TableBody>
                    {targets.map((target) => (
                      <TableRow key={target.id} hover>
                        <TableCell>
                          <Stack spacing={0.5}>
                            <Typography variant="body2" fontWeight={600}>
                              {target.name}
                            </Typography>
                            <Typography variant="caption" color="text.secondary">
                              {target.description || target.endpointUrl || 'No description'}
                            </Typography>
                          </Stack>
                        </TableCell>
                        <TableCell>
                          <Chip size="small" label={target.transportType} />
                        </TableCell>
                        <TableCell sx={{ maxWidth: 180 }}>
                          <Typography variant="body2" noWrap title={target.targetFolderName || target.targetFolderId}>
                            {target.targetFolderName || target.targetFolderId}
                          </Typography>
                        </TableCell>
                        <TableCell>
                          <Stack spacing={0.5}>
                            <Chip
                              size="small"
                              label={target.verificationStatus}
                              color={verificationColor[target.verificationStatus]}
                            />
                            <Typography variant="caption" color="text.secondary">
                              {target.verificationMessage || formatTimestamp(target.lastVerifiedAt)}
                            </Typography>
                          </Stack>
                        </TableCell>
                        <TableCell align="right">
                          <Stack direction="row" justifyContent="flex-end" spacing={1}>
                            <Button size="small" onClick={() => openTargetDialog(target)}>
                              Edit
                            </Button>
                            <Button
                              size="small"
                              startIcon={<PublishedWithChanges />}
                              onClick={() => void handleVerifyTarget(target.id)}
                            >
                              Verify
                            </Button>
                            <Button
                              size="small"
                              color="error"
                              startIcon={<Delete />}
                              onClick={() => void handleDeleteTarget(target.id)}
                            >
                              Delete
                            </Button>
                          </Stack>
                        </TableCell>
                      </TableRow>
                    ))}
                    {targets.length === 0 && (
                      <TableRow>
                        <TableCell colSpan={5}>
                          <Typography variant="body2" color="text.secondary">
                            No transfer targets configured yet.
                          </Typography>
                        </TableCell>
                      </TableRow>
                    )}
                  </TableBody>
                </Table>
              </CardContent>
            </Card>
          </Grid>

          <Grid item xs={12} xl={6}>
            <Card>
              <CardContent>
                <Stack direction="row" justifyContent="space-between" alignItems="center" sx={{ mb: 2 }}>
                  <Box>
                    <Typography variant="h6">Replication Definitions</Typography>
                    <Typography variant="body2" color="text.secondary">
                      Bind source nodes to transfer targets and queue outbound replication jobs.
                    </Typography>
                  </Box>
                  <Button
                    variant="contained"
                    startIcon={<Add />}
                    onClick={() => openDefinitionDialog()}
                    disabled={targets.length === 0}
                  >
                    New Definition
                  </Button>
                </Stack>

                {targets.length === 0 && (
                  <Alert severity="warning" sx={{ mb: 2 }}>
                    Create at least one transfer target before adding replication definitions.
                  </Alert>
                )}

                <Table size="small">
                  <TableHead>
                    <TableRow>
                      <TableCell>Name</TableCell>
                      <TableCell>Source</TableCell>
                      <TableCell>Target</TableCell>
                      <TableCell>Last Run</TableCell>
                      <TableCell align="right">Actions</TableCell>
                    </TableRow>
                  </TableHead>
                  <TableBody>
                    {definitions.map((definition) => (
                      <TableRow key={definition.id} hover>
                        <TableCell>
                          <Stack spacing={0.5}>
                            <Typography variant="body2" fontWeight={600}>
                              {definition.name}
                            </Typography>
                            <Typography variant="caption" color="text.secondary">
                              {definition.includeChildren ? 'Deep copy enabled' : 'Single node only'}
                            </Typography>
                          </Stack>
                        </TableCell>
                        <TableCell>
                          <Typography variant="body2" noWrap title={definition.sourceNodeName || definition.sourceNodeId}>
                            {definition.sourceNodeName || definition.sourceNodeId}
                          </Typography>
                        </TableCell>
                        <TableCell>
                          <Typography variant="body2" noWrap title={definition.transferTargetName || definition.transferTargetId}>
                            {definition.transferTargetName || definition.transferTargetId}
                          </Typography>
                        </TableCell>
                        <TableCell>{formatTimestamp(definition.lastRunAt)}</TableCell>
                        <TableCell align="right">
                          <Stack direction="row" justifyContent="flex-end" spacing={1}>
                            <Button size="small" onClick={() => openDefinitionDialog(definition)}>
                              Edit
                            </Button>
                            <Button
                              size="small"
                              startIcon={<PlayArrow />}
                              onClick={() => void handleRunDefinition(definition.id)}
                            >
                              Run
                            </Button>
                            <Button
                              size="small"
                              color="error"
                              startIcon={<Delete />}
                              onClick={() => void handleDeleteDefinition(definition.id)}
                            >
                              Delete
                            </Button>
                          </Stack>
                        </TableCell>
                      </TableRow>
                    ))}
                    {definitions.length === 0 && (
                      <TableRow>
                        <TableCell colSpan={5}>
                          <Typography variant="body2" color="text.secondary">
                            No replication definitions configured yet.
                          </Typography>
                        </TableCell>
                      </TableRow>
                    )}
                  </TableBody>
                </Table>
              </CardContent>
            </Card>
          </Grid>
        </Grid>

        <Card>
          <CardContent>
            <Stack direction="row" justifyContent="space-between" alignItems="center" sx={{ mb: 2 }}>
              <Box>
                <Typography variant="h6">Replication Jobs</Typography>
                <Typography variant="body2" color="text.secondary">
                  Monitor queued, running, completed, and failed outbound replication jobs.
                </Typography>
              </Box>
              <Chip
                size="small"
                color={hasRunningJobs ? 'primary' : 'default'}
                label={hasRunningJobs ? 'Auto-refreshing' : 'Idle'}
              />
            </Stack>

            <Paper variant="outlined">
              <Table size="small">
                <TableHead>
                  <TableRow>
                    <TableCell>Status</TableCell>
                    <TableCell>User</TableCell>
                    <TableCell>Source Node</TableCell>
                    <TableCell>Target</TableCell>
                    <TableCell>Message</TableCell>
                    <TableCell>Started</TableCell>
                    <TableCell>Completed</TableCell>
                  </TableRow>
                </TableHead>
                <TableBody>
                  {jobs.map((job) => (
                    <TableRow key={job.id} hover>
                      <TableCell>
                        <Chip size="small" label={job.status} color={jobColor[job.status]} />
                      </TableCell>
                      <TableCell>{job.userId}</TableCell>
                      <TableCell sx={{ maxWidth: 180 }}>
                        <Typography variant="body2" noWrap title={job.sourceNodeId}>
                          {job.sourceNodeId}
                        </Typography>
                      </TableCell>
                      <TableCell sx={{ maxWidth: 180 }}>
                        <Typography variant="body2" noWrap title={job.transferTargetId}>
                          {job.transferTargetId}
                        </Typography>
                      </TableCell>
                      <TableCell sx={{ maxWidth: 260 }}>
                        <Typography variant="body2" noWrap title={job.errorLog || job.lastMessage || ''}>
                          {job.errorLog || job.lastMessage || '—'}
                        </Typography>
                      </TableCell>
                      <TableCell>{formatTimestamp(job.startedAt)}</TableCell>
                      <TableCell>{formatTimestamp(job.completedAt)}</TableCell>
                    </TableRow>
                  ))}
                  {jobs.length === 0 && (
                    <TableRow>
                      <TableCell colSpan={7}>
                        <Typography variant="body2" color="text.secondary">
                          No replication jobs yet.
                        </Typography>
                      </TableCell>
                    </TableRow>
                  )}
                </TableBody>
              </Table>
              <TablePagination
                component="div"
                count={jobsTotal}
                page={jobsPage}
                rowsPerPage={jobsRowsPerPage}
                onPageChange={(_, nextPage) => setJobsPage(nextPage)}
                onRowsPerPageChange={(event) => {
                  setJobsRowsPerPage(Number(event.target.value));
                  setJobsPage(0);
                }}
                rowsPerPageOptions={[10, 20, 50]}
              />
            </Paper>
          </CardContent>
        </Card>
      </Stack>

      <Dialog open={targetDialogOpen} onClose={closeTargetDialog} fullWidth maxWidth="sm">
        <DialogTitle>{selectedTargetId ? 'Edit Transfer Target' : 'New Transfer Target'}</DialogTitle>
        <DialogContent>
          <Stack spacing={2} sx={{ mt: 1 }}>
            <TextField
              label="Name"
              value={targetForm.name}
              onChange={(event) => setTargetForm((current) => ({ ...current, name: event.target.value }))}
              fullWidth
            />
            <TextField
              label="Description"
              value={targetForm.description || ''}
              onChange={(event) => setTargetForm((current) => ({ ...current, description: event.target.value }))}
              fullWidth
            />
            <FormControl fullWidth>
              <InputLabel id="transfer-target-transport-label">Transport</InputLabel>
              <Select
                labelId="transfer-target-transport-label"
                label="Transport"
                value={targetForm.transportType}
                onChange={(event) =>
                  setTargetForm((current) => ({
                    ...current,
                    transportType: event.target.value as TransportType,
                    authType: event.target.value === 'ATHENA_HTTP' ? current.authType || 'NONE' : 'NONE',
                  }))
                }
              >
                <MenuItem value="LOOPBACK">LOOPBACK</MenuItem>
                <MenuItem value="ATHENA_HTTP">ATHENA_HTTP</MenuItem>
              </Select>
            </FormControl>
            <TextField
              label="Target Folder ID"
              value={targetForm.targetFolderId}
              onChange={(event) => setTargetForm((current) => ({ ...current, targetFolderId: event.target.value }))}
              helperText="LOOPBACK uses a local folder UUID. ATHENA_HTTP uses a remote folder UUID."
              fullWidth
            />
            {targetForm.transportType === 'ATHENA_HTTP' && (
              <>
                <TextField
                  label="Endpoint URL"
                  value={targetForm.endpointUrl || ''}
                  onChange={(event) => setTargetForm((current) => ({ ...current, endpointUrl: event.target.value }))}
                  placeholder="https://replica.example.com"
                  fullWidth
                />
                <TextField
                  label="Endpoint Path"
                  value={targetForm.endpointPath || '/api/v1'}
                  onChange={(event) => setTargetForm((current) => ({ ...current, endpointPath: event.target.value }))}
                  fullWidth
                />
                <FormControl fullWidth>
                  <InputLabel id="transfer-target-auth-label">Auth Type</InputLabel>
                  <Select
                    labelId="transfer-target-auth-label"
                    label="Auth Type"
                    value={targetForm.authType || 'NONE'}
                    onChange={(event) =>
                      setTargetForm((current) => ({
                        ...current,
                        authType: event.target.value as AuthType,
                      }))
                    }
                  >
                    <MenuItem value="NONE">NONE</MenuItem>
                    <MenuItem value="BASIC">BASIC</MenuItem>
                    <MenuItem value="BEARER">BEARER</MenuItem>
                  </Select>
                </FormControl>
                {targetForm.authType === 'BASIC' && (
                  <TextField
                    label="Auth Username"
                    value={targetForm.authUsername || ''}
                    onChange={(event) => setTargetForm((current) => ({ ...current, authUsername: event.target.value }))}
                    fullWidth
                  />
                )}
                {targetForm.authType !== 'NONE' && (
                  <TextField
                    label={selectedTargetId ? 'Auth Secret (leave blank to keep current)' : 'Auth Secret'}
                    type="password"
                    value={targetForm.authSecret || ''}
                    onChange={(event) => setTargetForm((current) => ({ ...current, authSecret: event.target.value }))}
                    fullWidth
                  />
                )}
              </>
            )}
            <FormControlLabel
              control={
                <Checkbox
                  checked={Boolean(targetForm.enabled)}
                  onChange={(event) => setTargetForm((current) => ({ ...current, enabled: event.target.checked }))}
                />
              }
              label="Enabled"
            />
          </Stack>
        </DialogContent>
        <DialogActions>
          <Button onClick={closeTargetDialog}>Cancel</Button>
          <Button variant="contained" startIcon={<Save />} onClick={() => void handleSaveTarget()} disabled={savingTarget}>
            {selectedTargetId ? 'Save Target' : 'Create Target'}
          </Button>
        </DialogActions>
      </Dialog>

      <Dialog open={definitionDialogOpen} onClose={closeDefinitionDialog} fullWidth maxWidth="sm">
        <DialogTitle>{selectedDefinitionId ? 'Edit Replication Definition' : 'New Replication Definition'}</DialogTitle>
        <DialogContent>
          <Stack spacing={2} sx={{ mt: 1 }}>
            <TextField
              label="Name"
              value={definitionForm.name}
              onChange={(event) => setDefinitionForm((current) => ({ ...current, name: event.target.value }))}
              fullWidth
            />
            <TextField
              label="Description"
              value={definitionForm.description || ''}
              onChange={(event) => setDefinitionForm((current) => ({ ...current, description: event.target.value }))}
              fullWidth
            />
            <TextField
              label="Source Node ID"
              value={definitionForm.sourceNodeId}
              onChange={(event) => setDefinitionForm((current) => ({ ...current, sourceNodeId: event.target.value }))}
              helperText="Document or folder UUID to replicate."
              fullWidth
            />
            <FormControl fullWidth>
              <InputLabel id="replication-definition-target-label">Transfer Target</InputLabel>
              <Select
                labelId="replication-definition-target-label"
                label="Transfer Target"
                value={definitionForm.transferTargetId}
                onChange={(event) =>
                  setDefinitionForm((current) => ({ ...current, transferTargetId: event.target.value }))
                }
              >
                {targets.map((target) => (
                  <MenuItem key={target.id} value={target.id}>
                    {target.name} ({target.transportType})
                  </MenuItem>
                ))}
              </Select>
            </FormControl>
            <FormControlLabel
              control={
                <Checkbox
                  checked={Boolean(definitionForm.includeChildren)}
                  onChange={(event) =>
                    setDefinitionForm((current) => ({ ...current, includeChildren: event.target.checked }))
                  }
                />
              }
              label="Include children"
            />
            <FormControlLabel
              control={
                <Checkbox
                  checked={Boolean(definitionForm.enabled)}
                  onChange={(event) =>
                    setDefinitionForm((current) => ({ ...current, enabled: event.target.checked }))
                  }
                />
              }
              label="Enabled"
            />
          </Stack>
        </DialogContent>
        <DialogActions>
          <Button onClick={closeDefinitionDialog}>Cancel</Button>
          <Button
            variant="contained"
            startIcon={<Save />}
            onClick={() => void handleSaveDefinition()}
            disabled={savingDefinition}
          >
            {selectedDefinitionId ? 'Save Definition' : 'Create Definition'}
          </Button>
        </DialogActions>
      </Dialog>
    </Box>
  );
};

export default TransferReplicationPage;
