import React, { useCallback, useEffect, useState } from 'react';
import {
  Alert,
  Box,
  Button,
  Card,
  CardContent,
  Checkbox,
  Chip,
  CircularProgress,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  Divider,
  FormControl,
  FormControlLabel,
  Grid,
  InputLabel,
  List,
  ListItem,
  ListItemText,
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
import { Add, PlayArrow, DeleteOutline, Edit, DirectionsRun } from '@mui/icons-material';
import { toast } from 'react-toastify';
import dispositionScheduleService, {
  DispositionActionExecutionDto,
  DispositionBatchExecutionDto,
  DispositionDryRunDto,
  DispositionExecutionDto,
  DispositionPage,
  DispositionScheduleDto,
  DispositionScheduleUpsertRequest,
} from 'services/dispositionScheduleService';

// ── helpers ───────────────────────────────────────────────────────────────────

const formatDateTime = (value?: string | null): string => {
  if (!value) return '—';
  const parsed = new Date(value);
  if (Number.isNaN(parsed.getTime())) return value;
  return parsed.toLocaleString();
};

const STORAGE_TIER_OPTIONS: Array<{ value: string; label: string }> = [
  { value: 'STANDARD', label: 'STANDARD' },
  { value: 'GLACIER', label: 'GLACIER' },
  { value: 'DEEP_ARCHIVE', label: 'DEEP_ARCHIVE' },
];

// ── shared schedule form fields ───────────────────────────────────────────────

interface ScheduleFormValues {
  enabled: boolean;
  includeSubfolders: boolean;
  cutoffAfterDays: string;
  archiveAfterCutoffDays: string;
  destroyAfterArchiveDays: string;
  archiveStorageTier: string;
  maxCandidatesPerAction: string;
}

const DEFAULT_FORM: ScheduleFormValues = {
  enabled: true,
  includeSubfolders: false,
  cutoffAfterDays: '',
  archiveAfterCutoffDays: '',
  destroyAfterArchiveDays: '',
  archiveStorageTier: '',
  maxCandidatesPerAction: '100',
};

function scheduleToForm(s: DispositionScheduleDto): ScheduleFormValues {
  return {
    enabled: s.enabled,
    includeSubfolders: s.includeSubfolders,
    cutoffAfterDays: s.cutoffAfterDays != null ? String(s.cutoffAfterDays) : '',
    archiveAfterCutoffDays: s.archiveAfterCutoffDays != null ? String(s.archiveAfterCutoffDays) : '',
    destroyAfterArchiveDays: s.destroyAfterArchiveDays != null ? String(s.destroyAfterArchiveDays) : '',
    archiveStorageTier: s.archiveStorageTier ?? '',
    maxCandidatesPerAction: s.maxCandidatesPerAction != null ? String(s.maxCandidatesPerAction) : '',
  };
}

function formToRequest(f: ScheduleFormValues): DispositionScheduleUpsertRequest {
  const parseNum = (v: string): number | null =>
    v.trim() === '' ? null : Number(v);

  return {
    enabled: f.enabled,
    includeSubfolders: f.includeSubfolders,
    cutoffAfterDays: parseNum(f.cutoffAfterDays),
    archiveAfterCutoffDays: parseNum(f.archiveAfterCutoffDays),
    destroyAfterArchiveDays: parseNum(f.destroyAfterArchiveDays),
    archiveStorageTier: f.archiveStorageTier === '' ? null : f.archiveStorageTier,
    maxCandidatesPerAction: parseNum(f.maxCandidatesPerAction),
  };
}

interface ScheduleFormFieldsProps {
  form: ScheduleFormValues;
  onChange: (updated: Partial<ScheduleFormValues>) => void;
}

const ScheduleFormFields: React.FC<ScheduleFormFieldsProps> = ({ form, onChange }) => (
  <Stack spacing={2} sx={{ mt: 1 }}>
    <FormControlLabel
      control={
        <Checkbox
          checked={form.enabled}
          onChange={(e) => onChange({ enabled: e.target.checked })}
        />
      }
      label="Enabled"
    />
    <FormControlLabel
      control={
        <Checkbox
          checked={form.includeSubfolders}
          onChange={(e) => onChange({ includeSubfolders: e.target.checked })}
        />
      }
      label="Include subfolders"
    />
    <TextField
      label="Cutoff after days (optional)"
      type="number"
      value={form.cutoffAfterDays}
      onChange={(e) => onChange({ cutoffAfterDays: e.target.value })}
      fullWidth
      inputProps={{ min: 0 }}
      helperText="Days after creation before a record is cut off"
    />
    <TextField
      label="Archive after cutoff days (optional)"
      type="number"
      value={form.archiveAfterCutoffDays}
      onChange={(e) => onChange({ archiveAfterCutoffDays: e.target.value })}
      fullWidth
      inputProps={{ min: 0 }}
      helperText="Days after cutoff before archiving"
    />
    <TextField
      label="Destroy after archive days (optional)"
      type="number"
      value={form.destroyAfterArchiveDays}
      onChange={(e) => onChange({ destroyAfterArchiveDays: e.target.value })}
      fullWidth
      inputProps={{ min: 0 }}
      helperText="Days after archiving before permanent destruction"
    />
    <FormControl fullWidth>
      <InputLabel id="storage-tier-label">Archive storage tier (optional)</InputLabel>
      <Select
        labelId="storage-tier-label"
        label="Archive storage tier (optional)"
        value={form.archiveStorageTier}
        onChange={(e) => onChange({ archiveStorageTier: e.target.value })}
      >
        <MenuItem value="">
          <em>None</em>
        </MenuItem>
        {STORAGE_TIER_OPTIONS.map((opt) => (
          <MenuItem key={opt.value} value={opt.value}>
            {opt.label}
          </MenuItem>
        ))}
      </Select>
    </FormControl>
    <TextField
      label="Max candidates per action (optional)"
      type="number"
      value={form.maxCandidatesPerAction}
      onChange={(e) => onChange({ maxCandidatesPerAction: e.target.value })}
      fullWidth
      inputProps={{ min: 1 }}
      helperText="Maximum nodes processed per action type in one run"
    />
  </Stack>
);

// ── AddScheduleDialog ─────────────────────────────────────────────────────────

interface AddScheduleDialogProps {
  open: boolean;
  onClose: () => void;
  onAdded: (schedule: DispositionScheduleDto) => void;
}

const AddScheduleDialog: React.FC<AddScheduleDialogProps> = ({ open, onClose, onAdded }) => {
  const [folderId, setFolderId] = useState('');
  const [form, setForm] = useState<ScheduleFormValues>(DEFAULT_FORM);
  const [submitting, setSubmitting] = useState(false);

  const handleClose = () => {
    setFolderId('');
    setForm(DEFAULT_FORM);
    onClose();
  };

  const handleSubmit = async () => {
    const id = folderId.trim();
    if (!id) return;
    setSubmitting(true);
    try {
      const result = await dispositionScheduleService.upsertSchedule(id, formToRequest(form));
      toast.success(`Disposition schedule created for folder "${result.folderName}".`);
      onAdded(result);
      handleClose();
    } catch (err) {
      console.error('Failed to create disposition schedule', err);
      toast.error('Failed to create disposition schedule.');
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <Dialog open={open} onClose={handleClose} fullWidth maxWidth="sm">
      <DialogTitle>Add Disposition Schedule</DialogTitle>
      <DialogContent>
        <Stack spacing={2} sx={{ mt: 1 }}>
          <TextField
            label="Folder ID (UUID)"
            value={folderId}
            onChange={(e) => setFolderId(e.target.value)}
            required
            fullWidth
            autoFocus
            placeholder="e.g. a1b2c3d4-e5f6-7890-abcd-ef1234567890"
            helperText="Must be a records folder UUID from the repository"
          />
          <Divider />
          <ScheduleFormFields
            form={form}
            onChange={(partial) => setForm((prev) => ({ ...prev, ...partial }))}
          />
        </Stack>
      </DialogContent>
      <DialogActions>
        <Button onClick={handleClose} disabled={submitting}>
          Cancel
        </Button>
        <Button
          variant="contained"
          onClick={handleSubmit}
          disabled={!folderId.trim() || submitting}
        >
          {submitting ? 'Creating…' : 'Create'}
        </Button>
      </DialogActions>
    </Dialog>
  );
};

// ── EditScheduleDialog ────────────────────────────────────────────────────────

interface EditScheduleDialogProps {
  open: boolean;
  schedule: DispositionScheduleDto;
  onClose: () => void;
  onSaved: (schedule: DispositionScheduleDto) => void;
}

const EditScheduleDialog: React.FC<EditScheduleDialogProps> = ({
  open,
  schedule,
  onClose,
  onSaved,
}) => {
  const [form, setForm] = useState<ScheduleFormValues>(() => scheduleToForm(schedule));
  const [submitting, setSubmitting] = useState(false);

  useEffect(() => {
    if (open) {
      setForm(scheduleToForm(schedule));
    }
  }, [open, schedule]);

  const handleClose = () => {
    onClose();
  };

  const handleSubmit = async () => {
    setSubmitting(true);
    try {
      const result = await dispositionScheduleService.upsertSchedule(
        schedule.folderId,
        formToRequest(form),
      );
      toast.success(`Schedule for "${result.folderName}" updated.`);
      onSaved(result);
      handleClose();
    } catch (err) {
      console.error('Failed to update disposition schedule', err);
      toast.error('Failed to update disposition schedule.');
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <Dialog open={open} onClose={handleClose} fullWidth maxWidth="sm">
      <DialogTitle>Edit Disposition Schedule</DialogTitle>
      <DialogContent>
        <Typography variant="body2" color="text.secondary" sx={{ mt: 1, mb: 1 }}>
          Folder: <strong>{schedule.folderName}</strong>
        </Typography>
        <ScheduleFormFields
          form={form}
          onChange={(partial) => setForm((prev) => ({ ...prev, ...partial }))}
        />
      </DialogContent>
      <DialogActions>
        <Button onClick={handleClose} disabled={submitting}>
          Cancel
        </Button>
        <Button variant="contained" onClick={handleSubmit} disabled={submitting}>
          {submitting ? 'Saving…' : 'Save'}
        </Button>
      </DialogActions>
    </Dialog>
  );
};

// ── DryRunResultDialog ────────────────────────────────────────────────────────

const ACTION_TYPE_COLORS: Record<string, 'default' | 'info' | 'warning' | 'error'> = {
  CUTOFF: 'info',
  ARCHIVE: 'warning',
  DESTROY: 'error',
};

interface DryRunResultDialogProps {
  open: boolean;
  result: DispositionDryRunDto | null;
  onClose: () => void;
}

const DryRunResultDialog: React.FC<DryRunResultDialogProps> = ({ open, result, onClose }) => {
  if (!result) return null;
  return (
    <Dialog open={open} onClose={onClose} fullWidth maxWidth="md">
      <DialogTitle>Dry Run Results — {result.folderName}</DialogTitle>
      <DialogContent>
        <Stack direction="row" spacing={2} sx={{ mb: 2, mt: 1, flexWrap: 'wrap' }}>
          <Chip label={`Cutoff: ${result.cutoffCount}`} color="info" />
          <Chip label={`Archive: ${result.archiveCount}`} color="warning" />
          <Chip label={`Destroy: ${result.destroyCount}`} color="error" />
        </Stack>

        {result.candidates.length === 0 ? (
          <Typography variant="body2" color="text.secondary">
            No candidates found.
          </Typography>
        ) : (
          <Table size="small">
            <TableHead>
              <TableRow>
                <TableCell>Name</TableCell>
                <TableCell>Action</TableCell>
                <TableCell>Path</TableCell>
                <TableCell>Eligible at</TableCell>
                <TableCell>Blocked by hold</TableCell>
              </TableRow>
            </TableHead>
            <TableBody>
              {result.candidates.map((c) => (
                <TableRow key={c.nodeId}>
                  <TableCell>{c.name}</TableCell>
                  <TableCell>
                    <Chip
                      label={c.actionType}
                      size="small"
                      color={ACTION_TYPE_COLORS[c.actionType] ?? 'default'}
                    />
                  </TableCell>
                  <TableCell sx={{ maxWidth: 200, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
                    {c.path}
                  </TableCell>
                  <TableCell>{formatDateTime(c.eligibleAt)}</TableCell>
                  <TableCell>
                    {c.blockedByHoldNames ? (
                      <Chip label={c.blockedByHoldNames} size="small" color="warning" variant="outlined" />
                    ) : (
                      '—'
                    )}
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        )}
      </DialogContent>
      <DialogActions>
        <Button onClick={onClose}>Close</Button>
      </DialogActions>
    </Dialog>
  );
};

// ── ExecuteResultDialog ───────────────────────────────────────────────────────

interface ExecuteResultDialogProps {
  open: boolean;
  result: DispositionExecutionDto | null;
  onClose: () => void;
}

const ExecuteResultDialog: React.FC<ExecuteResultDialogProps> = ({ open, result, onClose }) => {
  if (!result) return null;
  return (
    <Dialog open={open} onClose={onClose} fullWidth maxWidth="sm">
      <DialogTitle>Execution Results — {result.folderName}</DialogTitle>
      <DialogContent>
        <Stack spacing={1} sx={{ mt: 1 }}>
          {result.error && (
            <Alert severity="error" sx={{ mb: 1 }}>
              {result.error}
            </Alert>
          )}
          <Grid container spacing={2}>
            <Grid item xs={6}>
              <Typography variant="caption" color="text.secondary" display="block">Cutoff</Typography>
              <Typography variant="body1">{result.cutoffCount}</Typography>
            </Grid>
            <Grid item xs={6}>
              <Typography variant="caption" color="text.secondary" display="block">Archived</Typography>
              <Typography variant="body1">{result.archivedNodeCount} / {result.archiveCandidateCount}</Typography>
            </Grid>
            <Grid item xs={6}>
              <Typography variant="caption" color="text.secondary" display="block">Destroyed</Typography>
              <Typography variant="body1">{result.destroyedNodeCount} / {result.destroyCandidateCount}</Typography>
            </Grid>
            <Grid item xs={6}>
              <Typography variant="caption" color="text.secondary" display="block">Blocked</Typography>
              <Typography variant="body1">{result.blockedCount}</Typography>
            </Grid>
            <Grid item xs={6}>
              <Typography variant="caption" color="text.secondary" display="block">Failures</Typography>
              <Typography variant="body1" color={result.failureCount > 0 ? 'error.main' : 'inherit'}>
                {result.failureCount}
              </Typography>
            </Grid>
          </Grid>

          {result.failures.length > 0 && (
            <Box sx={{ mt: 1 }}>
              <Typography variant="subtitle2" gutterBottom>
                Failure details
              </Typography>
              <List dense disablePadding>
                {result.failures.map((f, idx) => (
                  <ListItem key={idx} disablePadding sx={{ py: 0.25 }}>
                    <ListItemText
                      primary={
                        <Typography variant="caption" color="error.main">
                          {f}
                        </Typography>
                      }
                    />
                  </ListItem>
                ))}
              </List>
            </Box>
          )}
        </Stack>
      </DialogContent>
      <DialogActions>
        <Button onClick={onClose}>Close</Button>
      </DialogActions>
    </Dialog>
  );
};

// ── RunAllResultDialog ────────────────────────────────────────────────────────

interface RunAllResultDialogProps {
  open: boolean;
  result: DispositionBatchExecutionDto | null;
  onClose: () => void;
}

const RunAllResultDialog: React.FC<RunAllResultDialogProps> = ({ open, result, onClose }) => {
  if (!result) return null;
  return (
    <Dialog open={open} onClose={onClose} fullWidth maxWidth="md">
      <DialogTitle>Run All Schedules — Summary</DialogTitle>
      <DialogContent>
        <Stack spacing={2} sx={{ mt: 1 }}>
          <Grid container spacing={2}>
            <Grid item xs={4}>
              <Typography variant="caption" color="text.secondary" display="block">Schedules run</Typography>
              <Typography variant="h6">{result.executedSchedules}</Typography>
            </Grid>
            <Grid item xs={4}>
              <Typography variant="caption" color="text.secondary" display="block">Cutoff</Typography>
              <Typography variant="h6">{result.cutoffCount}</Typography>
            </Grid>
            <Grid item xs={4}>
              <Typography variant="caption" color="text.secondary" display="block">Archived</Typography>
              <Typography variant="h6">{result.archivedNodeCount}</Typography>
            </Grid>
            <Grid item xs={4}>
              <Typography variant="caption" color="text.secondary" display="block">Destroyed</Typography>
              <Typography variant="h6">{result.destroyedNodeCount}</Typography>
            </Grid>
            <Grid item xs={4}>
              <Typography variant="caption" color="text.secondary" display="block">Blocked</Typography>
              <Typography variant="h6">{result.blockedCount}</Typography>
            </Grid>
            <Grid item xs={4}>
              <Typography variant="caption" color="text.secondary" display="block">Failures</Typography>
              <Typography variant="h6" color={result.failureCount > 0 ? 'error.main' : 'inherit'}>
                {result.failureCount}
              </Typography>
            </Grid>
          </Grid>

          {result.results.length > 0 && (
            <>
              <Divider />
              <Typography variant="subtitle1" fontWeight={600}>Per-schedule results</Typography>
              {result.results.map((r, idx) => (
                <Card key={idx} variant="outlined">
                  <CardContent sx={{ py: 1, '&:last-child': { pb: 1 } }}>
                    <Stack direction="row" alignItems="center" justifyContent="space-between">
                      <Typography variant="body2" fontWeight={500}>{r.folderName}</Typography>
                      {r.error && (
                        <Chip label="Error" size="small" color="error" />
                      )}
                    </Stack>
                    <Typography variant="caption" color="text.secondary">
                      Cutoff: {r.cutoffCount} · Archived: {r.archivedNodeCount} · Destroyed: {r.destroyedNodeCount} · Failures: {r.failureCount}
                    </Typography>
                    {r.error && (
                      <Typography variant="caption" color="error.main" display="block">
                        {r.error}
                      </Typography>
                    )}
                  </CardContent>
                </Card>
              ))}
            </>
          )}
        </Stack>
      </DialogContent>
      <DialogActions>
        <Button onClick={onClose}>Close</Button>
      </DialogActions>
    </Dialog>
  );
};

// ── ConfirmExecuteDialog ──────────────────────────────────────────────────────

interface ConfirmExecuteDialogProps {
  open: boolean;
  folderName: string;
  onClose: () => void;
  onConfirm: () => void;
  submitting: boolean;
}

const ConfirmExecuteDialog: React.FC<ConfirmExecuteDialogProps> = ({
  open,
  folderName,
  onClose,
  onConfirm,
  submitting,
}) => (
  <Dialog open={open} onClose={onClose} fullWidth maxWidth="xs">
    <DialogTitle>Execute Disposition Schedule</DialogTitle>
    <DialogContent>
      <Alert severity="warning" sx={{ mt: 1 }}>
        This will immediately apply all eligible disposition actions (cutoff, archive, destroy) for
        folder <strong>{folderName}</strong>. This action cannot be undone.
      </Alert>
    </DialogContent>
    <DialogActions>
      <Button onClick={onClose} disabled={submitting}>
        Cancel
      </Button>
      <Button
        variant="contained"
        color="warning"
        onClick={onConfirm}
        disabled={submitting}
      >
        {submitting ? 'Executing…' : 'Execute Now'}
      </Button>
    </DialogActions>
  </Dialog>
);

// ── main page ──────────────────────────────────────────────────────────────────

const DispositionSchedulesPage: React.FC = () => {
  // list state
  const [schedules, setSchedules] = useState<DispositionScheduleDto[]>([]);
  const [loading, setLoading] = useState(false);
  const [loadError, setLoadError] = useState<string | null>(null);

  // selection / detail
  const [selectedFolderId, setSelectedFolderId] = useState<string | null>(null);
  const selected = schedules.find((s) => s.folderId === selectedFolderId) ?? null;

  // executions
  const [executions, setExecutions] = useState<DispositionPage<DispositionActionExecutionDto> | null>(null);
  const [execPage, setExecPage] = useState(0);
  const [execLoading, setExecLoading] = useState(false);

  // dialog visibility
  const [addOpen, setAddOpen] = useState(false);
  const [editOpen, setEditOpen] = useState(false);
  const [confirmExecOpen, setConfirmExecOpen] = useState(false);
  const [dryRunResultOpen, setDryRunResultOpen] = useState(false);
  const [executeResultOpen, setExecuteResultOpen] = useState(false);
  const [runAllResultOpen, setRunAllResultOpen] = useState(false);

  // dialog data
  const [dryRunResult, setDryRunResult] = useState<DispositionDryRunDto | null>(null);
  const [executeResult, setExecuteResult] = useState<DispositionExecutionDto | null>(null);
  const [runAllResult, setRunAllResult] = useState<DispositionBatchExecutionDto | null>(null);

  // action submitting
  const [dryRunning, setDryRunning] = useState(false);
  const [executing, setExecuting] = useState(false);
  const [runningAll, setRunningAll] = useState(false);
  const [deleting, setDeleting] = useState(false);

  // ── load list ──────────────────────────────────────────────────────────────

  const loadSchedules = useCallback(async () => {
    setLoading(true);
    setLoadError(null);
    try {
      const data = await dispositionScheduleService.listSchedules();
      setSchedules(data);
    } catch (err) {
      console.error('Failed to load disposition schedules', err);
      setLoadError('Failed to load disposition schedules.');
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void loadSchedules();
  }, [loadSchedules]);

  // ── load executions ────────────────────────────────────────────────────────

  const loadExecutions = useCallback(async (folderId: string, page: number) => {
    setExecLoading(true);
    try {
      const data = await dispositionScheduleService.listExecutions(folderId, page, 10);
      setExecutions(data);
    } catch (err) {
      console.error('Failed to load execution history', err);
    } finally {
      setExecLoading(false);
    }
  }, []);

  const handleSelectSchedule = (folderId: string) => {
    if (selectedFolderId === folderId) return;
    setSelectedFolderId(folderId);
    setExecPage(0);
    void loadExecutions(folderId, 0);
  };

  useEffect(() => {
    if (selectedFolderId) {
      void loadExecutions(selectedFolderId, execPage);
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [execPage]);

  // ── mutations ──────────────────────────────────────────────────────────────

  const applyScheduleUpdate = (updated: DispositionScheduleDto) => {
    setSchedules((prev) =>
      prev.map((s) => (s.folderId === updated.folderId ? updated : s)),
    );
  };

  const handleAdded = (schedule: DispositionScheduleDto) => {
    setSchedules((prev) => [schedule, ...prev]);
    setSelectedFolderId(schedule.folderId);
    setExecPage(0);
    void loadExecutions(schedule.folderId, 0);
  };

  const handleSaved = (schedule: DispositionScheduleDto) => {
    applyScheduleUpdate(schedule);
  };

  const handleDryRun = async () => {
    if (!selectedFolderId) return;
    setDryRunning(true);
    try {
      const result = await dispositionScheduleService.dryRun(selectedFolderId);
      setDryRunResult(result);
      // Update lastDryRunAt in list
      setSchedules((prev) =>
        prev.map((s) =>
          s.folderId === selectedFolderId
            ? { ...s, lastDryRunAt: new Date().toISOString() }
            : s,
        ),
      );
      setDryRunResultOpen(true);
    } catch (err) {
      console.error('Dry run failed', err);
      toast.error('Dry run failed.');
    } finally {
      setDryRunning(false);
    }
  };

  const handleExecuteConfirm = async () => {
    if (!selectedFolderId) return;
    setExecuting(true);
    try {
      const result = await dispositionScheduleService.execute(selectedFolderId);
      setExecuteResult(result);
      // Refresh the schedule to pick up lastExecutedAt / lastError
      const refreshed = await dispositionScheduleService.getSchedule(selectedFolderId);
      applyScheduleUpdate(refreshed);
      setConfirmExecOpen(false);
      setExecuteResultOpen(true);
      // Reload execution history
      setExecPage(0);
      void loadExecutions(selectedFolderId, 0);
    } catch (err) {
      console.error('Execution failed', err);
      toast.error('Execution failed.');
    } finally {
      setExecuting(false);
    }
  };

  const handleDelete = async () => {
    if (!selectedFolderId || !selected) return;
    setDeleting(true);
    try {
      await dispositionScheduleService.deleteSchedule(selectedFolderId);
      toast.success(`Schedule for "${selected.folderName}" deleted.`);
      setSchedules((prev) => prev.filter((s) => s.folderId !== selectedFolderId));
      setSelectedFolderId(null);
      setExecutions(null);
    } catch (err) {
      console.error('Failed to delete disposition schedule', err);
      toast.error('Failed to delete schedule.');
    } finally {
      setDeleting(false);
    }
  };

  const handleRunAll = async () => {
    setRunningAll(true);
    try {
      const result = await dispositionScheduleService.runAll();
      setRunAllResult(result);
      setRunAllResultOpen(true);
      // Reload list so timestamps update
      void loadSchedules();
    } catch (err) {
      console.error('Run all failed', err);
      toast.error('Run all schedules failed.');
    } finally {
      setRunningAll(false);
    }
  };

  // ── render ─────────────────────────────────────────────────────────────────

  return (
    <Box sx={{ p: 3 }}>
      {/* Page header */}
      <Stack direction="row" alignItems="center" justifyContent="space-between" sx={{ mb: 3 }}>
        <Box>
          <Typography variant="h4" fontWeight={700}>
            Disposition Schedules
          </Typography>
          <Typography variant="body2" color="text.secondary" sx={{ mt: 0.5 }}>
            Manage retention policies that govern the lifecycle of records folder content.
          </Typography>
        </Box>
        <Button
          variant="contained"
          startIcon={<PlayArrow />}
          onClick={handleRunAll}
          disabled={runningAll}
        >
          {runningAll ? 'Running…' : 'Run All Schedules'}
        </Button>
      </Stack>

      {loadError && (
        <Alert severity="error" sx={{ mb: 2 }}>
          {loadError}
        </Alert>
      )}

      <Grid container spacing={3}>
        {/* Left panel: schedule list */}
        <Grid item xs={12} md={4}>
          <Card variant="outlined">
            <CardContent sx={{ p: 0, '&:last-child': { pb: 0 } }}>
              <Stack
                direction="row"
                alignItems="center"
                justifyContent="space-between"
                sx={{ px: 2, py: 1.5, borderBottom: 1, borderColor: 'divider' }}
              >
                <Typography variant="subtitle2" fontWeight={600}>
                  Schedules
                </Typography>
                <Button
                  size="small"
                  startIcon={<Add />}
                  onClick={() => setAddOpen(true)}
                >
                  Add Schedule
                </Button>
              </Stack>
              <Typography variant="caption" color="text.secondary" sx={{ px: 2, py: 0.5, display: 'block' }}>
                Lists schedules attached to records folders
              </Typography>

              {loading ? (
                <Box sx={{ display: 'flex', justifyContent: 'center', py: 4 }}>
                  <CircularProgress size={32} />
                </Box>
              ) : schedules.length === 0 ? (
                <Box sx={{ p: 3, textAlign: 'center' }}>
                  <Typography variant="body2" color="text.secondary">
                    No disposition schedules found.
                  </Typography>
                </Box>
              ) : (
                <List disablePadding>
                  {schedules.map((s, idx) => (
                    <React.Fragment key={s.folderId}>
                      {idx > 0 && <Divider />}
                      <ListItem
                        button
                        selected={selectedFolderId === s.folderId}
                        onClick={() => handleSelectSchedule(s.folderId)}
                        sx={{ flexDirection: 'column', alignItems: 'flex-start', py: 1.5 }}
                      >
                        <Stack
                          direction="row"
                          alignItems="center"
                          justifyContent="space-between"
                          width="100%"
                          spacing={1}
                        >
                          <Typography variant="subtitle2" noWrap sx={{ flex: 1 }}>
                            {s.folderName}
                          </Typography>
                          <Chip
                            label={s.enabled ? 'Active' : 'Disabled'}
                            size="small"
                            color={s.enabled ? 'success' : 'default'}
                          />
                        </Stack>
                        <Typography variant="caption" color="text.secondary" noWrap sx={{ width: '100%' }}>
                          {s.folderPath}
                        </Typography>
                      </ListItem>
                    </React.Fragment>
                  ))}
                </List>
              )}
            </CardContent>
          </Card>
        </Grid>

        {/* Right panel: schedule detail */}
        <Grid item xs={12} md={8}>
          {!selectedFolderId ? (
            <Card variant="outlined">
              <CardContent>
                <Box sx={{ textAlign: 'center', py: 4 }}>
                  <Typography variant="body2" color="text.secondary">
                    Select a disposition schedule from the list to view details.
                  </Typography>
                </Box>
              </CardContent>
            </Card>
          ) : !selected ? (
            <Box sx={{ display: 'flex', justifyContent: 'center', py: 6 }}>
              <CircularProgress />
            </Box>
          ) : (
            <Stack spacing={2}>
              {/* Detail header */}
              <Card variant="outlined">
                <CardContent>
                  <Stack
                    direction="row"
                    alignItems="flex-start"
                    justifyContent="space-between"
                    spacing={2}
                  >
                    <Box sx={{ flex: 1, minWidth: 0 }}>
                      <Stack direction="row" alignItems="center" spacing={1} sx={{ mb: 0.5 }}>
                        <Typography variant="h6" fontWeight={600} noWrap>
                          {selected.folderName}
                        </Typography>
                        <Chip
                          label={selected.enabled ? 'Active' : 'Disabled'}
                          size="small"
                          color={selected.enabled ? 'success' : 'default'}
                        />
                      </Stack>
                      <Typography variant="caption" color="text.secondary" display="block" sx={{ mb: 1.5 }}>
                        {selected.folderPath}
                      </Typography>

                      {/* Settings grid */}
                      <Grid container spacing={1.5}>
                        <Grid item xs={6} sm={4}>
                          <Typography variant="caption" color="text.secondary" display="block">
                            Include subfolders
                          </Typography>
                          <Typography variant="body2">
                            {selected.includeSubfolders ? 'Yes' : 'No'}
                          </Typography>
                        </Grid>
                        <Grid item xs={6} sm={4}>
                          <Typography variant="caption" color="text.secondary" display="block">
                            Cutoff after
                          </Typography>
                          <Typography variant="body2">
                            {selected.cutoffAfterDays != null ? `${selected.cutoffAfterDays} days` : '—'}
                          </Typography>
                        </Grid>
                        <Grid item xs={6} sm={4}>
                          <Typography variant="caption" color="text.secondary" display="block">
                            Archive after cutoff
                          </Typography>
                          <Typography variant="body2">
                            {selected.archiveAfterCutoffDays != null ? `${selected.archiveAfterCutoffDays} days` : '—'}
                          </Typography>
                        </Grid>
                        <Grid item xs={6} sm={4}>
                          <Typography variant="caption" color="text.secondary" display="block">
                            Destroy after archive
                          </Typography>
                          <Typography variant="body2">
                            {selected.destroyAfterArchiveDays != null ? `${selected.destroyAfterArchiveDays} days` : '—'}
                          </Typography>
                        </Grid>
                        <Grid item xs={6} sm={4}>
                          <Typography variant="caption" color="text.secondary" display="block">
                            Storage tier
                          </Typography>
                          <Typography variant="body2">{selected.archiveStorageTier ?? '—'}</Typography>
                        </Grid>
                        <Grid item xs={6} sm={4}>
                          <Typography variant="caption" color="text.secondary" display="block">
                            Max candidates
                          </Typography>
                          <Typography variant="body2">
                            {selected.maxCandidatesPerAction != null ? selected.maxCandidatesPerAction : '—'}
                          </Typography>
                        </Grid>
                        <Grid item xs={6} sm={4}>
                          <Typography variant="caption" color="text.secondary" display="block">
                            Last dry run
                          </Typography>
                          <Typography variant="body2">{formatDateTime(selected.lastDryRunAt)}</Typography>
                        </Grid>
                        <Grid item xs={6} sm={4}>
                          <Typography variant="caption" color="text.secondary" display="block">
                            Last executed
                          </Typography>
                          <Typography variant="body2">{formatDateTime(selected.lastExecutedAt)}</Typography>
                        </Grid>
                      </Grid>

                      {selected.lastError && (
                        <Alert severity="error" sx={{ mt: 1.5 }}>
                          <Typography variant="caption">{selected.lastError}</Typography>
                        </Alert>
                      )}
                    </Box>

                    {/* Action buttons */}
                    <Stack direction="column" spacing={1} flexShrink={0}>
                      <Button
                        variant="outlined"
                        size="small"
                        startIcon={<Edit />}
                        onClick={() => setEditOpen(true)}
                      >
                        Edit Schedule
                      </Button>
                      <Button
                        variant="outlined"
                        size="small"
                        startIcon={<DirectionsRun />}
                        onClick={handleDryRun}
                        disabled={dryRunning}
                      >
                        {dryRunning ? 'Running…' : 'Dry Run'}
                      </Button>
                      <Button
                        variant="outlined"
                        size="small"
                        color="warning"
                        startIcon={<PlayArrow />}
                        onClick={() => setConfirmExecOpen(true)}
                        disabled={executing}
                      >
                        Execute Now
                      </Button>
                      <Button
                        variant="outlined"
                        size="small"
                        color="error"
                        startIcon={<DeleteOutline />}
                        onClick={handleDelete}
                        disabled={deleting}
                      >
                        {deleting ? 'Deleting…' : 'Delete Schedule'}
                      </Button>
                    </Stack>
                  </Stack>
                </CardContent>
              </Card>

              {/* Execution history */}
              <Card variant="outlined">
                <CardContent>
                  <Typography variant="subtitle1" fontWeight={600} sx={{ mb: 1.5 }}>
                    Execution History
                  </Typography>

                  {execLoading ? (
                    <Box sx={{ display: 'flex', justifyContent: 'center', py: 3 }}>
                      <CircularProgress size={28} />
                    </Box>
                  ) : !executions || executions.content.length === 0 ? (
                    <Typography variant="body2" color="text.secondary">
                      No executions recorded yet.
                    </Typography>
                  ) : (
                    <>
                      <Table size="small">
                        <TableHead>
                          <TableRow>
                            <TableCell>Action</TableCell>
                            <TableCell>Status</TableCell>
                            <TableCell>Node name</TableCell>
                            <TableCell align="right">Affected</TableCell>
                            <TableCell>Actor</TableCell>
                            <TableCell>Executed at</TableCell>
                          </TableRow>
                        </TableHead>
                        <TableBody>
                          {executions.content.map((e) => (
                            <TableRow key={e.id}>
                              <TableCell>
                                <Chip
                                  label={e.actionType}
                                  size="small"
                                  color={ACTION_TYPE_COLORS[e.actionType] ?? 'default'}
                                />
                              </TableCell>
                              <TableCell>
                                <Chip
                                  label={e.status}
                                  size="small"
                                  color={
                                    e.status === 'SUCCESS'
                                      ? 'success'
                                      : e.status === 'FAILED'
                                      ? 'error'
                                      : 'default'
                                  }
                                />
                              </TableCell>
                              <TableCell>{e.nodeName}</TableCell>
                              <TableCell align="right">{e.affectedNodeCount}</TableCell>
                              <TableCell>{e.actor}</TableCell>
                              <TableCell>{formatDateTime(e.executedAt)}</TableCell>
                            </TableRow>
                          ))}
                        </TableBody>
                      </Table>
                      <TablePagination
                        component="div"
                        count={executions.totalElements}
                        page={execPage}
                        rowsPerPage={10}
                        rowsPerPageOptions={[10]}
                        onPageChange={(_, newPage) => setExecPage(newPage)}
                      />
                    </>
                  )}
                </CardContent>
              </Card>
            </Stack>
          )}
        </Grid>
      </Grid>

      {/* Dialogs */}
      <AddScheduleDialog
        open={addOpen}
        onClose={() => setAddOpen(false)}
        onAdded={handleAdded}
      />

      {selected && (
        <>
          <EditScheduleDialog
            open={editOpen}
            schedule={selected}
            onClose={() => setEditOpen(false)}
            onSaved={handleSaved}
          />
          <ConfirmExecuteDialog
            open={confirmExecOpen}
            folderName={selected.folderName}
            onClose={() => setConfirmExecOpen(false)}
            onConfirm={handleExecuteConfirm}
            submitting={executing}
          />
        </>
      )}

      <DryRunResultDialog
        open={dryRunResultOpen}
        result={dryRunResult}
        onClose={() => setDryRunResultOpen(false)}
      />
      <ExecuteResultDialog
        open={executeResultOpen}
        result={executeResult}
        onClose={() => setExecuteResultOpen(false)}
      />
      <RunAllResultDialog
        open={runAllResultOpen}
        result={runAllResult}
        onClose={() => setRunAllResultOpen(false)}
      />
    </Box>
  );
};

export default DispositionSchedulesPage;
