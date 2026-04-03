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
  Link,
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
import { Archive, OpenInNew, Refresh, Restore } from '@mui/icons-material';
import { Link as RouterLink } from 'react-router-dom';
import { toast } from 'react-toastify';
import contentArchiveService, {
  ArchivedNodeDto,
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
  const [statusLookup, setStatusLookup] = useState<ArchiveStatusDto | null>(null);
  const [archivedNodes, setArchivedNodes] = useState<ArchivedNodeDto[]>([]);
  const [loading, setLoading] = useState(false);
  const [submitting, setSubmitting] = useState(false);
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

  useEffect(() => {
    void loadArchivedNodes();
  }, [loadArchivedNodes]);

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
        </Grid>

        <Grid item xs={12} lg={8}>
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
                            <Link component={RouterLink} to={`/browse/${node.nodeId}`} underline="none">
                              <Button size="small" startIcon={<OpenInNew />}>
                                Open
                              </Button>
                            </Link>
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
        </Grid>
      </Grid>
    </Box>
  );
};

export default ContentArchivePage;
