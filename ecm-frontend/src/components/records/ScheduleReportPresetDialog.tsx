import React, { useCallback, useEffect, useState } from 'react';
import {
  Alert,
  Box,
  Button,
  Chip,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  Divider,
  FormControlLabel,
  List,
  ListItem,
  ListItemText,
  Stack,
  Switch,
  TextField,
  Typography,
} from '@mui/material';
import recordsManagementService, {
  supportsReportPresetCsvDelivery,
  UpdateReportPresetScheduleRequest,
} from '../../services/recordsManagementService';
import {
  RmReportPreset,
  RmReportPresetExecution,
  RmReportPresetScheduleStatus,
} from 'types';

interface ScheduleReportPresetDialogProps {
  open: boolean;
  preset: RmReportPreset | null;
  onClose: () => void;
  onSaved?: (status: RmReportPresetScheduleStatus) => void;
}

const formatDateTime = (value?: string | null): string => {
  if (!value) return '—';
  const parsed = new Date(value);
  if (Number.isNaN(parsed.getTime())) return value;
  return parsed.toLocaleString();
};

const ScheduleReportPresetDialog: React.FC<ScheduleReportPresetDialogProps> = ({
  open,
  preset,
  onClose,
  onSaved,
}) => {
  const [loading, setLoading] = useState(false);
  const [saving, setSaving] = useState(false);
  const [delivering, setDelivering] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [status, setStatus] = useState<RmReportPresetScheduleStatus | null>(null);
  const [executions, setExecutions] = useState<RmReportPresetExecution[]>([]);

  const [enabled, setEnabled] = useState(false);
  const [cronExpression, setCronExpression] = useState('');
  const [timezone, setTimezone] = useState('UTC');
  const [deliveryFolderId, setDeliveryFolderId] = useState('');
  const [cronError, setCronError] = useState<string | null>(null);
  const [folderError, setFolderError] = useState<string | null>(null);

  const kindSupportsCsvDelivery = preset ? supportsReportPresetCsvDelivery(preset.kind) : false;

  const loadState = useCallback(async () => {
    if (!preset) {
      return null;
    }
    setLoading(true);
    setError(null);
    try {
      const [scheduleStatus, recentExecutions] = await Promise.all([
        recordsManagementService.getReportPresetSchedule(preset.id),
        recordsManagementService.listReportPresetExecutions(preset.id, 5),
      ]);
      setStatus(scheduleStatus);
      setExecutions(recentExecutions);
      setEnabled(scheduleStatus.enabled);
      setCronExpression(scheduleStatus.cronExpression ?? '');
      setTimezone(scheduleStatus.timezone ?? 'UTC');
      setDeliveryFolderId(scheduleStatus.deliveryFolderId ?? '');
      setCronError(null);
      setFolderError(null);
      return {
        scheduleStatus,
        recentExecutions,
      };
    } catch (err) {
      const message = err instanceof Error ? err.message : 'Failed to load schedule';
      setError(message);
      return null;
    } finally {
      setLoading(false);
    }
  }, [preset]);

  useEffect(() => {
    if (open && preset && kindSupportsCsvDelivery) {
      void loadState();
    }
  }, [open, preset, kindSupportsCsvDelivery, loadState]);

  const handleSave = async () => {
    if (!preset) return;
    setCronError(null);
    setFolderError(null);
    setError(null);

    let request: UpdateReportPresetScheduleRequest;
    if (enabled) {
      const trimmedCron = cronExpression.trim();
      const trimmedFolder = deliveryFolderId.trim();
      if (!trimmedCron) {
        setCronError('Cron expression is required when schedule is enabled');
        return;
      }
      if (!trimmedFolder) {
        setFolderError('Delivery folder id is required when schedule is enabled');
        return;
      }
      request = {
        enabled: true,
        cronExpression: trimmedCron,
        timezone: timezone.trim() || null,
        deliveryFolderId: trimmedFolder,
      };
    } else {
      request = {
        enabled: false,
        timezone: timezone.trim() || null,
        deliveryFolderId: deliveryFolderId.trim() || null,
      };
    }

    setSaving(true);
    try {
      const updated = await recordsManagementService.updateReportPresetSchedule(preset.id, request);
      setStatus(updated);
      setEnabled(updated.enabled);
      setCronExpression(updated.cronExpression ?? '');
      setTimezone(updated.timezone ?? 'UTC');
      setDeliveryFolderId(updated.deliveryFolderId ?? '');
      await loadState();
      onSaved?.(updated);
    } catch (err) {
      const message = err instanceof Error ? err.message : 'Failed to save schedule';
      setError(message);
    } finally {
      setSaving(false);
    }
  };

  const handleDeliverNow = async () => {
    if (!preset) return;
    setError(null);
    setDelivering(true);
    try {
      await recordsManagementService.deliverReportPresetNow(preset.id);
      const refreshed = await loadState();
      const execution = refreshed?.recentExecutions[0];
      if (execution?.status === 'FAILED') {
        setError(execution.message || 'Delivery failed');
      }
    } catch (err) {
      const message = err instanceof Error ? err.message : 'Failed to deliver preset';
      setError(message);
    } finally {
      setDelivering(false);
    }
  };

  const busy = loading || saving || delivering;

  return (
    <Dialog open={open} onClose={busy ? undefined : onClose} maxWidth="sm" fullWidth>
      <DialogTitle>Schedule Delivery — {preset?.name ?? ''}</DialogTitle>
      <DialogContent>
        {!kindSupportsCsvDelivery ? (
          <Alert severity="warning" sx={{ mb: 2 }}>
            This preset kind is summary-only and cannot be delivered as CSV. Scheduled delivery is disabled for{' '}
            <code>{preset?.kind}</code>.
          </Alert>
        ) : (
          <>
            {error && (
              <Alert severity="error" sx={{ mb: 2 }}>
                {error}
              </Alert>
            )}

            <FormControlLabel
              control={
                <Switch
                  checked={enabled}
                  onChange={(event) => setEnabled(event.target.checked)}
                  disabled={busy}
                />
              }
              label="Enable scheduled delivery"
              sx={{ mb: 2 }}
            />

            <TextField
              fullWidth
              label="Cron expression"
              value={cronExpression}
              onChange={(event) => {
                setCronExpression(event.target.value);
                if (cronError) setCronError(null);
              }}
              disabled={busy || !enabled}
              required={enabled}
              error={Boolean(cronError)}
              helperText={cronError || 'Example: 0 9 * * MON-FRI (9am weekdays). 5-min minimum interval.'}
              sx={{ mb: 2 }}
            />

            <TextField
              fullWidth
              label="Timezone"
              value={timezone}
              onChange={(event) => setTimezone(event.target.value)}
              disabled={busy}
              helperText="IANA timezone (e.g. UTC, America/New_York)"
              sx={{ mb: 2 }}
            />

            <TextField
              fullWidth
              label="Delivery folder ID"
              value={deliveryFolderId}
              onChange={(event) => {
                setDeliveryFolderId(event.target.value);
                if (folderError) setFolderError(null);
              }}
              disabled={busy}
              required={enabled}
              error={Boolean(folderError)}
              helperText={folderError || 'UUID of the folder that should receive the rendered CSV'}
              sx={{ mb: 2 }}
            />

            {status && (
              <Box sx={{ mb: 2 }}>
                <Typography variant="subtitle2" gutterBottom>
                  Current schedule
                </Typography>
                <Stack direction="row" spacing={1} sx={{ flexWrap: 'wrap', gap: 1 }}>
                  <Chip
                    label={status.enabled ? 'Enabled' : 'Disabled'}
                    color={status.enabled ? 'success' : 'default'}
                    size="small"
                  />
                  {status.nextRunAt && (
                    <Chip label={`Next: ${formatDateTime(status.nextRunAt)}`} size="small" variant="outlined" />
                  )}
                  {status.lastRunAt && (
                    <Chip label={`Last: ${formatDateTime(status.lastRunAt)}`} size="small" variant="outlined" />
                  )}
                </Stack>
              </Box>
            )}

            <Divider sx={{ my: 2 }} />
            <Stack direction="row" alignItems="center" justifyContent="space-between" sx={{ mb: 1 }}>
              <Typography variant="subtitle2">Recent executions</Typography>
              <Button
                size="small"
                variant="outlined"
                onClick={() => void handleDeliverNow()}
                disabled={busy}
              >
                {delivering ? 'Delivering…' : 'Deliver now'}
              </Button>
            </Stack>
            {executions.length === 0 ? (
              <Typography variant="body2" color="text.secondary">
                No deliveries yet.
              </Typography>
            ) : (
              <List dense>
                {executions.map((execution) => (
                  <ListItem key={execution.id} divider>
                    <ListItemText
                      primary={
                        <Stack direction="row" spacing={1} alignItems="center">
                          <Chip
                            label={execution.status}
                            size="small"
                            color={execution.status === 'SUCCESS' ? 'success' : 'error'}
                          />
                          <Chip label={execution.triggerType} size="small" variant="outlined" />
                          <Typography variant="body2">{formatDateTime(execution.startedAt)}</Typography>
                        </Stack>
                      }
                      secondary={execution.filename || execution.message || ''}
                    />
                  </ListItem>
                ))}
              </List>
            )}
          </>
        )}
      </DialogContent>
      <DialogActions>
        <Button onClick={onClose} disabled={busy}>
          Close
        </Button>
        {kindSupportsCsvDelivery && (
          <Button
            variant="contained"
            onClick={() => void handleSave()}
            disabled={busy}
          >
            {saving ? 'Saving…' : 'Save schedule'}
          </Button>
        )}
      </DialogActions>
    </Dialog>
  );
};

export default ScheduleReportPresetDialog;
