import React, { useCallback, useEffect, useState } from 'react';
import {
  Alert,
  Box,
  Button,
  Card,
  CardContent,
  Chip,
  FormControl,
  Grid,
  InputLabel,
  MenuItem,
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
import { Archive, Refresh, Restore } from '@mui/icons-material';
import { toast } from 'react-toastify';
import contentArchiveService, {
  ArchivedNodeDto,
  ArchivePolicyBatchExecutionDto,
  ArchivePolicyCandidateDto,
  ArchivePolicyDryRunDto,
  ArchivePolicyDto,
  ArchivePolicyRequest,
  ArchiveStatus,
  ArchiveStatusDto,
  ArchiveStoreTier,
} from 'services/contentArchiveService';

const statusColor: Record<ArchiveStatus, 'default' | 'success' | 'warning'> = {
  LIVE: 'success',
  ARCHIVED: 'warning',
  RESTORING: 'default',
};

const tierColor: Record<ArchiveStoreTier, 'default' | 'info' | 'warning' | 'error'> = {
  HOT: 'info',
  WARM: 'default',
  COLD: 'warning',
  GLACIER: 'error',
};

const formatDateTime = (value?: string | null): string =>
  value ? new Date(value).toLocaleString() : '—';

const formatFileSize = (bytes?: number | null): string => {
  if (!bytes) {
    return '—';
  }
  const sizes = ['B', 'KB', 'MB', 'GB', 'TB'];
  const index = Math.min(Math.floor(Math.log(bytes) / Math.log(1024)), sizes.length - 1);
  return `${(bytes / Math.pow(1024, index)).toFixed(2)} ${sizes[index]}`;
};

const ContentArchivePage: React.FC = () => {
  const [nodeIdInput, setNodeIdInput] = useState('');
  const [storageTier, setStorageTier] = useState<ArchiveStoreTier>('COLD');
  const [policyFolderId, setPolicyFolderId] = useState('');
  const [policyForm, setPolicyForm] = useState<ArchivePolicyRequest>({
    enabled: true,
    inactivityDays: 90,
    storageTier: 'COLD',
    includeSubfolders: true,
    maxCandidatesPerRun: 100,
  });
  const [policyList, setPolicyList] = useState<ArchivePolicyDto[]>([]);
  const [dryRun, setDryRun] = useState<ArchivePolicyDryRunDto | null>(null);
  const [runSummary, setRunSummary] = useState<ArchivePolicyBatchExecutionDto | null>(null);
  const [statusLookup, setStatusLookup] = useState<ArchiveStatusDto | null>(null);
  const [archivedNodes, setArchivedNodes] = useState<ArchivedNodeDto[]>([]);
  const [loading, setLoading] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [policySubmitting, setPolicySubmitting] = useState(false);
  const [page, setPage] = useState(0);
  const [rowsPerPage, setRowsPerPage] = useState(10);
  const [totalRows, setTotalRows] = useState(0);

  const loadArchivedNodes = useCallback(async () => {
    setLoading(true);
    try {
      const result = await contentArchiveService.listArchivedNodes(page, rowsPerPage);
      setArchivedNodes(result.content);
      setTotalRows(result.totalElements);
    } catch {
      toast.error('Failed to load archived content');
    } finally {
      setLoading(false);
    }
  }, [page, rowsPerPage]);

  const loadPolicies = useCallback(async () => {
    try {
      const result = await contentArchiveService.listArchivePolicies();
      setPolicyList(result);
    } catch {
      toast.error('Failed to load archive policies');
    }
  }, []);

  useEffect(() => {
    void loadArchivedNodes();
  }, [loadArchivedNodes]);

  useEffect(() => {
    void loadPolicies();
  }, [loadPolicies]);

  const refreshLookup = useCallback(async (nodeId: string) => {
    const trimmed = nodeId.trim();
    if (!trimmed) {
      setStatusLookup(null);
      return;
    }
    const status = await contentArchiveService.getArchiveStatus(trimmed);
    setStatusLookup(status);
  }, []);

  const handleLookupStatus = async () => {
    const trimmed = nodeIdInput.trim();
    if (!trimmed) {
      toast.error('Node ID is required');
      return;
    }
    setSubmitting(true);
    try {
      await refreshLookup(trimmed);
      toast.success('Archive status loaded');
    } catch {
      toast.error('Failed to load archive status');
    } finally {
      setSubmitting(false);
    }
  };

  const handleArchiveNode = async () => {
    const trimmed = nodeIdInput.trim();
    if (!trimmed) {
      toast.error('Node ID is required');
      return;
    }
    setSubmitting(true);
    try {
      const result = await contentArchiveService.archiveNode(trimmed, storageTier);
      toast.success(`Archived ${result.affectedNodeCount} node(s) to ${result.archiveStoreTier}`);
      await Promise.all([refreshLookup(trimmed), loadArchivedNodes()]);
    } catch {
      toast.error('Failed to archive node');
    } finally {
      setSubmitting(false);
    }
  };

  const handleRestoreNode = async (nodeId = nodeIdInput) => {
    const trimmed = nodeId.trim();
    if (!trimmed) {
      toast.error('Node ID is required');
      return;
    }
    setSubmitting(true);
    try {
      const result = await contentArchiveService.restoreNode(trimmed);
      toast.success(`Restored ${result.affectedNodeCount} node(s) to HOT storage`);
      await Promise.all([refreshLookup(trimmed), loadArchivedNodes()]);
    } catch {
      toast.error('Failed to restore archived node');
    } finally {
      setSubmitting(false);
    }
  };

  const handlePolicyLoad = async () => {
    const trimmed = policyFolderId.trim();
    if (!trimmed) {
      toast.error('Folder ID is required');
      return;
    }
    setPolicySubmitting(true);
    try {
      const policy = await contentArchiveService.getArchivePolicy(trimmed);
      setPolicyForm({
        enabled: policy.enabled,
        inactivityDays: policy.inactivityDays,
        storageTier: policy.storageTier,
        includeSubfolders: policy.includeSubfolders,
        maxCandidatesPerRun: policy.maxCandidatesPerRun,
      });
      toast.success('Archive policy loaded');
    } catch {
      toast.error('Archive policy not found for folder');
    } finally {
      setPolicySubmitting(false);
    }
  };

  const handlePolicySave = async () => {
    const trimmed = policyFolderId.trim();
    if (!trimmed) {
      toast.error('Folder ID is required');
      return;
    }
    setPolicySubmitting(true);
    try {
      await contentArchiveService.upsertArchivePolicy(trimmed, policyForm);
      await loadPolicies();
      toast.success('Archive policy saved');
    } catch {
      toast.error('Failed to save archive policy');
    } finally {
      setPolicySubmitting(false);
    }
  };

  const handlePolicyDelete = async () => {
    const trimmed = policyFolderId.trim();
    if (!trimmed) {
      toast.error('Folder ID is required');
      return;
    }
    setPolicySubmitting(true);
    try {
      await contentArchiveService.deleteArchivePolicy(trimmed);
      setDryRun(null);
      await loadPolicies();
      toast.success('Archive policy deleted');
    } catch {
      toast.error('Failed to delete archive policy');
    } finally {
      setPolicySubmitting(false);
    }
  };

  const handlePolicyDryRun = async () => {
    const trimmed = policyFolderId.trim();
    if (!trimmed) {
      toast.error('Folder ID is required');
      return;
    }
    setPolicySubmitting(true);
    try {
      const result = await contentArchiveService.dryRunArchivePolicy(trimmed, policyForm);
      setDryRun(result);
      toast.success(`Dry-run found ${result.candidateCount} candidate(s)`);
    } catch {
      toast.error('Failed to run archive dry-run');
    } finally {
      setPolicySubmitting(false);
    }
  };

  const handlePolicyExecute = async () => {
    const trimmed = policyFolderId.trim();
    if (!trimmed) {
      toast.error('Folder ID is required');
      return;
    }
    setPolicySubmitting(true);
    try {
      const result = await contentArchiveService.executeArchivePolicy(trimmed);
      await Promise.all([loadPolicies(), loadArchivedNodes()]);
      toast.success(`Archive policy archived ${result.archivedNodeCount} node(s)`);
    } catch {
      toast.error('Failed to execute archive policy');
    } finally {
      setPolicySubmitting(false);
    }
  };

  const handleRunEnabledPolicies = async () => {
    setPolicySubmitting(true);
    try {
      const result = await contentArchiveService.runArchivePolicies();
      setRunSummary(result);
      await Promise.all([loadPolicies(), loadArchivedNodes()]);
      toast.success(`Executed ${result.executedPolicies} archive policy run(s)`);
    } catch {
      toast.error('Failed to run enabled archive policies');
    } finally {
      setPolicySubmitting(false);
    }
  };

  const applyPolicyRow = (policy: ArchivePolicyDto) => {
    setPolicyFolderId(policy.folderId);
    setPolicyForm({
      enabled: policy.enabled,
      inactivityDays: policy.inactivityDays,
      storageTier: policy.storageTier,
      includeSubfolders: policy.includeSubfolders,
      maxCandidatesPerRun: policy.maxCandidatesPerRun,
    });
  };

  const renderCandidateRows = (candidates: ArchivePolicyCandidateDto[]) => (
    candidates.length > 0 ? candidates.map((candidate) => (
      <TableRow key={candidate.nodeId} hover>
        <TableCell>{candidate.name}</TableCell>
        <TableCell>{candidate.nodeType}</TableCell>
        <TableCell sx={{ maxWidth: 260 }}>
          <Typography variant="body2" noWrap title={candidate.path}>
            {candidate.path}
          </Typography>
        </TableCell>
        <TableCell>{formatDateTime(candidate.activityDate)}</TableCell>
      </TableRow>
    )) : (
      <TableRow>
        <TableCell colSpan={4}>
          <Typography variant="body2" color="text.secondary" align="center" sx={{ py: 2 }}>
            No candidate nodes in the current dry-run.
          </Typography>
        </TableCell>
      </TableRow>
    )
  );

  return (
    <Box sx={{ p: 3 }}>
      <Stack direction="row" justifyContent="space-between" alignItems="center" spacing={2} sx={{ mb: 3 }}>
        <Box>
          <Typography variant="h4" gutterBottom>
            Content Archive
          </Typography>
          <Typography variant="body2" color="text.secondary">
            Move nodes into archive storage tiers and restore them back to HOT storage when needed.
          </Typography>
        </Box>
        <Button variant="outlined" startIcon={<Refresh />} onClick={() => void loadArchivedNodes()} disabled={loading}>
          Refresh
        </Button>
      </Stack>

      <Grid container spacing={3}>
        <Grid item xs={12} lg={4}>
          <Card>
            <CardContent>
              <Stack spacing={2.5}>
                <Typography variant="h6">Archive Operator</Typography>
                <Alert severity="info">
                  First version focuses on archive status, restore flow, and operator visibility. It does not yet move
                  content into an external cold-storage backend.
                </Alert>
                <TextField
                  label="Node ID"
                  value={nodeIdInput}
                  onChange={(event) => setNodeIdInput(event.target.value)}
                  placeholder="Paste a node UUID"
                  fullWidth
                />
                <FormControl fullWidth>
                  <InputLabel id="archive-storage-tier-label">Storage Tier</InputLabel>
                  <Select
                    labelId="archive-storage-tier-label"
                    label="Storage Tier"
                    value={storageTier}
                    onChange={(event) => setStorageTier(event.target.value as ArchiveStoreTier)}
                  >
                    <MenuItem value="WARM">WARM</MenuItem>
                    <MenuItem value="COLD">COLD</MenuItem>
                    <MenuItem value="GLACIER">GLACIER</MenuItem>
                  </Select>
                </FormControl>
                <Stack direction={{ xs: 'column', sm: 'row' }} spacing={1.5}>
                  <Button variant="outlined" onClick={() => void handleLookupStatus()} disabled={submitting}>
                    Check Status
                  </Button>
                  <Button
                    variant="contained"
                    color="warning"
                    startIcon={<Archive />}
                    onClick={() => void handleArchiveNode()}
                    disabled={submitting}
                  >
                    Archive
                  </Button>
                  <Button
                    variant="outlined"
                    color="success"
                    startIcon={<Restore />}
                    onClick={() => void handleRestoreNode()}
                    disabled={submitting}
                  >
                    Restore
                  </Button>
                </Stack>
                {statusLookup ? (
                  <Alert severity={statusLookup.archiveStatus === 'ARCHIVED' ? 'warning' : 'success'}>
                    <Stack spacing={1}>
                      <Typography variant="subtitle2">{statusLookup.name}</Typography>
                      <Stack direction="row" spacing={1} flexWrap="wrap">
                        <Chip size="small" color={statusColor[statusLookup.archiveStatus]} label={statusLookup.archiveStatus} />
                        <Chip size="small" color={tierColor[statusLookup.archiveStoreTier]} label={statusLookup.archiveStoreTier} />
                      </Stack>
                      <Typography variant="body2" color="text.secondary">
                        {statusLookup.path}
                      </Typography>
                      <Typography variant="caption" color="text.secondary">
                        Archived by {statusLookup.archivedBy || '—'} on {formatDateTime(statusLookup.archivedDate)}
                      </Typography>
                    </Stack>
                  </Alert>
                ) : null}
              </Stack>
            </CardContent>
          </Card>

          <Card sx={{ mt: 3 }}>
            <CardContent>
              <Stack spacing={2.5}>
                <Typography variant="h6">Archive Policy</Typography>
                <Alert severity="info">
                  Folder-level policies archive stale live content by inactivity window. First cut supports dry-run,
                  manual execution, and scheduled enabled-policy runs.
                </Alert>
                <TextField
                  label="Folder ID"
                  value={policyFolderId}
                  onChange={(event) => setPolicyFolderId(event.target.value)}
                  placeholder="Paste a folder UUID"
                  fullWidth
                />
                <TextField
                  label="Inactivity Days"
                  type="number"
                  value={policyForm.inactivityDays}
                  onChange={(event) => setPolicyForm((current) => ({
                    ...current,
                    inactivityDays: Number(event.target.value) || 0,
                  }))}
                  fullWidth
                />
                <TextField
                  label="Max Candidates Per Run"
                  type="number"
                  value={policyForm.maxCandidatesPerRun}
                  onChange={(event) => setPolicyForm((current) => ({
                    ...current,
                    maxCandidatesPerRun: Number(event.target.value) || 0,
                  }))}
                  fullWidth
                />
                <FormControl fullWidth>
                  <InputLabel id="archive-policy-tier-label">Policy Storage Tier</InputLabel>
                  <Select
                    labelId="archive-policy-tier-label"
                    label="Policy Storage Tier"
                    value={policyForm.storageTier}
                    onChange={(event) => setPolicyForm((current) => ({
                      ...current,
                      storageTier: event.target.value as ArchiveStoreTier,
                    }))}
                  >
                    <MenuItem value="WARM">WARM</MenuItem>
                    <MenuItem value="COLD">COLD</MenuItem>
                    <MenuItem value="GLACIER">GLACIER</MenuItem>
                  </Select>
                </FormControl>
                <FormControl fullWidth>
                  <InputLabel id="archive-policy-enabled-label">Enabled</InputLabel>
                  <Select
                    labelId="archive-policy-enabled-label"
                    label="Enabled"
                    value={policyForm.enabled ? 'true' : 'false'}
                    onChange={(event) => setPolicyForm((current) => ({
                      ...current,
                      enabled: event.target.value === 'true',
                    }))}
                  >
                    <MenuItem value="true">Enabled</MenuItem>
                    <MenuItem value="false">Disabled</MenuItem>
                  </Select>
                </FormControl>
                <FormControl fullWidth>
                  <InputLabel id="archive-policy-include-subfolders-label">Include Subfolders</InputLabel>
                  <Select
                    labelId="archive-policy-include-subfolders-label"
                    label="Include Subfolders"
                    value={policyForm.includeSubfolders ? 'true' : 'false'}
                    onChange={(event) => setPolicyForm((current) => ({
                      ...current,
                      includeSubfolders: event.target.value === 'true',
                    }))}
                  >
                    <MenuItem value="true">Yes</MenuItem>
                    <MenuItem value="false">Direct Children Only</MenuItem>
                  </Select>
                </FormControl>
                <Stack direction={{ xs: 'column', sm: 'row' }} spacing={1.5} flexWrap="wrap">
                  <Button variant="outlined" onClick={() => void handlePolicyLoad()} disabled={policySubmitting}>
                    Load Policy
                  </Button>
                  <Button variant="contained" onClick={() => void handlePolicySave()} disabled={policySubmitting}>
                    Save Policy
                  </Button>
                  <Button variant="outlined" color="warning" onClick={() => void handlePolicyDryRun()} disabled={policySubmitting}>
                    Dry Run
                  </Button>
                  <Button variant="outlined" color="success" onClick={() => void handlePolicyExecute()} disabled={policySubmitting}>
                    Execute
                  </Button>
                  <Button variant="outlined" color="error" onClick={() => void handlePolicyDelete()} disabled={policySubmitting}>
                    Delete
                  </Button>
                </Stack>
                <Button variant="outlined" onClick={() => void handleRunEnabledPolicies()} disabled={policySubmitting}>
                  Run Enabled Policies
                </Button>
              </Stack>
            </CardContent>
          </Card>
        </Grid>

        <Grid item xs={12} lg={8}>
          <Stack spacing={3}>
            <Card>
              <CardContent>
                <Stack spacing={2}>
                  <Typography variant="h6">Archived Nodes</Typography>
                  <Table size="small">
                    <TableHead>
                      <TableRow>
                        <TableCell>Name</TableCell>
                        <TableCell>Type</TableCell>
                        <TableCell>Tier</TableCell>
                        <TableCell>Archived</TableCell>
                        <TableCell>Path</TableCell>
                        <TableCell>Size</TableCell>
                        <TableCell align="right">Actions</TableCell>
                      </TableRow>
                    </TableHead>
                    <TableBody>
                      {archivedNodes.length > 0 ? archivedNodes.map((node) => (
                        <TableRow key={node.nodeId} hover>
                          <TableCell>
                            <Stack spacing={0.5}>
                              <Typography variant="body2">{node.name}</Typography>
                              <Typography variant="caption" color="text.secondary">
                                {node.createdBy || '—'}
                              </Typography>
                            </Stack>
                          </TableCell>
                          <TableCell>{node.nodeType}</TableCell>
                          <TableCell>
                            <Chip size="small" color={tierColor[node.archiveStoreTier]} label={node.archiveStoreTier} />
                          </TableCell>
                          <TableCell>
                            <Stack spacing={0.5}>
                              <Chip size="small" color={statusColor[node.archiveStatus]} label={node.archiveStatus} />
                              <Typography variant="caption" color="text.secondary">
                                {formatDateTime(node.archivedDate)}
                              </Typography>
                            </Stack>
                          </TableCell>
                          <TableCell sx={{ maxWidth: 260 }}>
                            <Typography variant="body2" noWrap title={node.path}>
                              {node.path}
                            </Typography>
                          </TableCell>
                          <TableCell>{formatFileSize(node.size)}</TableCell>
                          <TableCell align="right">
                            <Stack direction="row" justifyContent="flex-end" spacing={1}>
                              <Button size="small" color="success" startIcon={<Restore />} onClick={() => void handleRestoreNode(node.nodeId)}>
                                Restore
                              </Button>
                            </Stack>
                          </TableCell>
                        </TableRow>
                      )) : (
                        <TableRow>
                          <TableCell colSpan={7}>
                            <Typography variant="body2" color="text.secondary" align="center" sx={{ py: 3 }}>
                              {loading ? 'Loading archived nodes…' : 'No archived nodes yet.'}
                            </Typography>
                          </TableCell>
                        </TableRow>
                      )}
                    </TableBody>
                  </Table>
                  <TablePagination
                    component="div"
                    count={totalRows}
                    page={page}
                    onPageChange={(_, nextPage) => setPage(nextPage)}
                    rowsPerPage={rowsPerPage}
                    onRowsPerPageChange={(event) => {
                      setRowsPerPage(Number(event.target.value));
                      setPage(0);
                    }}
                    rowsPerPageOptions={[10, 20, 50]}
                  />
                </Stack>
              </CardContent>
            </Card>

            <Card>
              <CardContent>
                <Stack spacing={2}>
                  <Typography variant="h6">Archive Policies</Typography>
                  {runSummary ? (
                    <Alert severity={runSummary.failureCount > 0 ? 'warning' : 'success'}>
                      Executed {runSummary.executedPolicies} policy runs, archived {runSummary.archivedNodeCount} node(s),
                      failures {runSummary.failureCount}.
                    </Alert>
                  ) : null}
                  <Table size="small">
                    <TableHead>
                      <TableRow>
                        <TableCell>Folder</TableCell>
                        <TableCell>Enabled</TableCell>
                        <TableCell>Inactivity</TableCell>
                        <TableCell>Tier</TableCell>
                        <TableCell>Last Run</TableCell>
                        <TableCell align="right">Actions</TableCell>
                      </TableRow>
                    </TableHead>
                    <TableBody>
                      {policyList.length > 0 ? policyList.map((policy) => (
                        <TableRow key={policy.policyId} hover>
                          <TableCell>
                            <Stack spacing={0.5}>
                              <Typography variant="body2">{policy.folderName}</Typography>
                              <Typography variant="caption" color="text.secondary">
                                {policy.folderPath}
                              </Typography>
                            </Stack>
                          </TableCell>
                          <TableCell>
                            <Chip size="small" color={policy.enabled ? 'success' : 'default'} label={policy.enabled ? 'ENABLED' : 'DISABLED'} />
                          </TableCell>
                          <TableCell>{policy.inactivityDays} days</TableCell>
                          <TableCell>
                            <Chip size="small" color={tierColor[policy.storageTier]} label={policy.storageTier} />
                          </TableCell>
                          <TableCell>{formatDateTime(policy.lastExecutedAt)}</TableCell>
                          <TableCell align="right">
                            <Button size="small" onClick={() => applyPolicyRow(policy)}>
                              Load
                            </Button>
                          </TableCell>
                        </TableRow>
                      )) : (
                        <TableRow>
                          <TableCell colSpan={6}>
                            <Typography variant="body2" color="text.secondary" align="center" sx={{ py: 2 }}>
                              No archive policies configured yet.
                            </Typography>
                          </TableCell>
                        </TableRow>
                      )}
                    </TableBody>
                  </Table>
                </Stack>
              </CardContent>
            </Card>

            <Card>
              <CardContent>
                <Stack spacing={2}>
                  <Typography variant="h6">Dry-Run Diagnostics</Typography>
                  {dryRun ? (
                    <Alert severity={dryRun.candidateCount > 0 ? 'warning' : 'success'}>
                      {dryRun.folderName}: {dryRun.candidateCount} candidate(s) older than {formatDateTime(dryRun.cutoffDate)}.
                    </Alert>
                  ) : (
                    <Typography variant="body2" color="text.secondary">
                      Run a policy dry-run to preview archive candidates before execution.
                    </Typography>
                  )}
                  <Table size="small">
                    <TableHead>
                      <TableRow>
                        <TableCell>Name</TableCell>
                        <TableCell>Type</TableCell>
                        <TableCell>Path</TableCell>
                        <TableCell>Activity</TableCell>
                      </TableRow>
                    </TableHead>
                    <TableBody>
                      {renderCandidateRows(dryRun?.candidates ?? [])}
                    </TableBody>
                  </Table>
                </Stack>
              </CardContent>
            </Card>
          </Stack>
        </Grid>
      </Grid>
    </Box>
  );
};

export default ContentArchivePage;
