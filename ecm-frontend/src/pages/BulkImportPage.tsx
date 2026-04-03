import React, { useCallback, useEffect, useMemo, useRef, useState } from 'react';
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
  LinearProgress,
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
import {
  Cancel,
  CloudUpload,
  FolderOpen,
  Refresh,
} from '@mui/icons-material';
import { toast } from 'react-toastify';
import bulkImportService, { ConflictPolicy, ImportJobDto } from 'services/bulkImportService';
import {
  BulkImportSelectionFile,
  summarizeBulkImportSelection,
} from 'utils/bulkImportUtils';

const activeJobStatuses = new Set<ImportJobDto['status']>(['PENDING', 'RUNNING']);

const statusColor: Record<
  ImportJobDto['status'],
  'default' | 'primary' | 'success' | 'warning' | 'error'
> = {
  PENDING: 'warning',
  RUNNING: 'primary',
  COMPLETED: 'success',
  FAILED: 'error',
  CANCELED: 'default',
};

const BulkImportPage: React.FC = () => {
  const folderInputRef = useRef<HTMLInputElement | null>(null);
  const fileInputRef = useRef<HTMLInputElement | null>(null);
  const [selectedFiles, setSelectedFiles] = useState<BulkImportSelectionFile[]>([]);
  const [targetFolderId, setTargetFolderId] = useState('');
  const [conflictPolicy, setConflictPolicy] = useState<ConflictPolicy>('SKIP');
  const [jobs, setJobs] = useState<ImportJobDto[]>([]);
  const [loadingJobs, setLoadingJobs] = useState(false);
  const [startingImport, setStartingImport] = useState(false);
  const [page, setPage] = useState(0);
  const [rowsPerPage, setRowsPerPage] = useState(10);
  const [totalJobs, setTotalJobs] = useState(0);
  const [refreshTick, setRefreshTick] = useState(0);

  const selectionSummary = useMemo(
    () => summarizeBulkImportSelection(selectedFiles),
    [selectedFiles]
  );
  const hasRunningJobs = useMemo(
    () => jobs.some((job) => activeJobStatuses.has(job.status)),
    [jobs]
  );

  useEffect(() => {
    folderInputRef.current?.setAttribute('webkitdirectory', '');
    folderInputRef.current?.setAttribute('directory', '');
  }, []);

  const loadJobs = useCallback(async () => {
    setLoadingJobs(true);
    try {
      const result = await bulkImportService.listJobs(page, rowsPerPage);
      setJobs(result.content);
      setTotalJobs(result.totalElements);
    } finally {
      setLoadingJobs(false);
    }
  }, [page, rowsPerPage]);

  useEffect(() => {
    void loadJobs();
  }, [loadJobs, refreshTick]);

  useEffect(() => {
    if (!hasRunningJobs) {
      return undefined;
    }
    const interval = window.setInterval(() => {
      setRefreshTick((value) => value + 1);
    }, 4000);
    return () => window.clearInterval(interval);
  }, [hasRunningJobs]);

  const handleFilesSelected = (fileList: FileList | null) => {
    setSelectedFiles(fileList ? (Array.from(fileList) as BulkImportSelectionFile[]) : []);
  };

  const handleStartImport = async () => {
    if (selectedFiles.length === 0) {
      return;
    }
    setStartingImport(true);
    try {
      const job = await bulkImportService.startImport(
        selectedFiles,
        targetFolderId.trim() || undefined,
        conflictPolicy
      );
      toast.success(`Bulk import queued: ${job.totalFiles} files`);
      setSelectedFiles([]);
      if (folderInputRef.current) {
        folderInputRef.current.value = '';
      }
      if (fileInputRef.current) {
        fileInputRef.current.value = '';
      }
      setPage(0);
      setRefreshTick((value) => value + 1);
    } finally {
      setStartingImport(false);
    }
  };

  const handleCancelJob = async (jobId: string) => {
    await bulkImportService.cancelJob(jobId);
    toast.success('Import job canceled');
    setRefreshTick((value) => value + 1);
  };

  const handleManualRefresh = () => {
    setRefreshTick((value) => value + 1);
  };

  return (
    <Box sx={{ p: 3 }}>
      <Stack direction="row" justifyContent="space-between" alignItems="center" spacing={2} sx={{ mb: 3 }}>
        <Box>
          <Typography variant="h4" gutterBottom>
            Bulk Import
          </Typography>
          <Typography variant="body2" color="text.secondary">
            Import a local folder tree into Athena with recursive folder creation and conflict policies.
          </Typography>
        </Box>
        <Button variant="outlined" startIcon={<Refresh />} onClick={handleManualRefresh} disabled={loadingJobs}>
          Refresh Jobs
        </Button>
      </Stack>

      <Grid container spacing={3}>
        <Grid item xs={12} lg={4}>
          <Card>
            <CardContent>
              <Stack spacing={2.5}>
                <Typography variant="h6">Start Import</Typography>
                <Alert severity="info">
                  Directory import preserves nested relative paths. Plain multi-file selection imports into a single
                  target folder.
                </Alert>
                <Stack direction={{ xs: 'column', sm: 'row' }} spacing={1.5}>
                  <Button
                    fullWidth
                    variant="contained"
                    startIcon={<FolderOpen />}
                    onClick={() => folderInputRef.current?.click()}
                  >
                    Select Folder
                  </Button>
                  <Button
                    fullWidth
                    variant="outlined"
                    startIcon={<CloudUpload />}
                    onClick={() => fileInputRef.current?.click()}
                  >
                    Select Files
                  </Button>
                </Stack>
                <input
                  ref={folderInputRef}
                  hidden
                  type="file"
                  multiple
                  onChange={(event) => handleFilesSelected(event.target.files)}
                />
                <input
                  ref={fileInputRef}
                  hidden
                  type="file"
                  multiple
                  onChange={(event) => handleFilesSelected(event.target.files)}
                />
                <TextField
                  label="Target Folder ID"
                  value={targetFolderId}
                  onChange={(event) => setTargetFolderId(event.target.value)}
                  placeholder="Leave blank for root"
                  fullWidth
                />
                <FormControl fullWidth>
                  <InputLabel id="bulk-import-conflict-policy-label">Conflict Policy</InputLabel>
                  <Select
                    labelId="bulk-import-conflict-policy-label"
                    label="Conflict Policy"
                    value={conflictPolicy}
                    onChange={(event) => setConflictPolicy(event.target.value as ConflictPolicy)}
                  >
                    <MenuItem value="SKIP">Skip existing files</MenuItem>
                    <MenuItem value="RENAME">Rename imported files</MenuItem>
                    <MenuItem value="OVERWRITE">Overwrite existing files</MenuItem>
                  </Select>
                </FormControl>
                {selectedFiles.length > 0 ? (
                  <Alert severity="success">
                    Selected {selectionSummary.fileCount} files across {selectionSummary.folderCount} folders.
                  </Alert>
                ) : (
                  <Alert severity="warning">No files selected yet.</Alert>
                )}
                <Button
                  variant="contained"
                  size="large"
                  onClick={handleStartImport}
                  disabled={startingImport || selectedFiles.length === 0}
                >
                  {startingImport ? 'Starting Import…' : 'Start Bulk Import'}
                </Button>
              </Stack>
            </CardContent>
          </Card>
        </Grid>

        <Grid item xs={12} lg={8}>
          <Card>
            <CardContent>
              <Stack spacing={2}>
                <Typography variant="h6">Import Jobs</Typography>
                {loadingJobs ? <LinearProgress /> : null}
                <Table size="small">
                  <TableHead>
                    <TableRow>
                      <TableCell>Status</TableCell>
                      <TableCell>Progress</TableCell>
                      <TableCell>Counts</TableCell>
                      <TableCell>Current Item</TableCell>
                      <TableCell>Updated</TableCell>
                      <TableCell align="right">Actions</TableCell>
                    </TableRow>
                  </TableHead>
                  <TableBody>
                    {jobs.length > 0 ? jobs.map((job) => {
                      const progress = job.totalFiles > 0
                        ? Math.round((job.processedFiles / job.totalFiles) * 100)
                        : 0;
                      return (
                        <TableRow key={job.id} hover>
                          <TableCell>
                            <Stack spacing={0.75}>
                              <Chip size="small" color={statusColor[job.status]} label={job.status} />
                              <Typography variant="caption" color="text.secondary">
                                {job.conflictPolicy}
                              </Typography>
                            </Stack>
                          </TableCell>
                          <TableCell sx={{ minWidth: 180 }}>
                            <Stack spacing={0.75}>
                              <LinearProgress
                                variant="determinate"
                                value={job.status === 'COMPLETED' ? 100 : progress}
                              />
                              <Typography variant="caption" color="text.secondary">
                                {job.processedFiles} / {job.totalFiles} processed
                              </Typography>
                            </Stack>
                          </TableCell>
                          <TableCell>
                            <Typography variant="body2">
                              {job.importedFiles} imported, {job.skippedFiles} skipped, {job.failedFiles} failed
                            </Typography>
                          </TableCell>
                          <TableCell sx={{ maxWidth: 260 }}>
                            <Typography variant="body2" noWrap title={job.currentItemPath || job.lastMessage || ''}>
                              {job.currentItemPath || job.lastMessage || 'Idle'}
                            </Typography>
                            {job.errorLog ? (
                              <Typography variant="caption" color="error" noWrap title={job.errorLog}>
                                {job.errorLog}
                              </Typography>
                            ) : null}
                          </TableCell>
                          <TableCell>
                            <Typography variant="body2">
                              {job.updatedAt ? new Date(job.updatedAt).toLocaleString() : '—'}
                            </Typography>
                          </TableCell>
                          <TableCell align="right">
                            {activeJobStatuses.has(job.status) ? (
                              <Button
                                color="warning"
                                size="small"
                                startIcon={<Cancel />}
                                onClick={() => void handleCancelJob(job.id)}
                              >
                                Cancel
                              </Button>
                            ) : (
                              <Typography variant="caption" color="text.secondary">
                                —
                              </Typography>
                            )}
                          </TableCell>
                        </TableRow>
                      );
                    }) : (
                      <TableRow>
                        <TableCell colSpan={6}>
                          <Typography variant="body2" color="text.secondary" align="center">
                            No import jobs yet.
                          </Typography>
                        </TableCell>
                      </TableRow>
                    )}
                  </TableBody>
                </Table>
                <TablePagination
                  component="div"
                  count={totalJobs}
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

export default BulkImportPage;
