import React, { useEffect, useMemo, useState } from 'react';
import {
  Box,
  Button,
  Chip,
  Divider,
  FormControlLabel,
  Paper,
  Stack,
  Switch,
  TextField,
  Typography,
  Autocomplete,
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableRow,
  IconButton,
} from '@mui/material';
import { Delete, Refresh, Send } from '@mui/icons-material';
import { toast } from 'react-toastify';
import apiService from 'services/api';

interface WebhookSubscription {
  id: string;
  name: string;
  url: string;
  secret?: string | null;
  enabled: boolean;
  eventTypes: string[];
  lastSuccessAt?: string | null;
  lastFailureAt?: string | null;
  lastStatusCode?: number | null;
  lastErrorMessage?: string | null;
  createdBy?: string | null;
}

const WebhookSubscriptionsPage: React.FC = () => {
  const [subscriptions, setSubscriptions] = useState<WebhookSubscription[]>([]);
  const [eventTypes, setEventTypes] = useState<string[]>([]);
  const [loading, setLoading] = useState(false);
  const [creating, setCreating] = useState(false);

  const [name, setName] = useState('');
  const [url, setUrl] = useState('');
  const [secret, setSecret] = useState('');
  const [enabled, setEnabled] = useState(true);
  const [selectedEvents, setSelectedEvents] = useState<string[]>([]);

  const canSubmit = name.trim() && url.trim();

  const loadData = async () => {
    try {
      setLoading(true);
      const [subs, types] = await Promise.all([
        apiService.get<WebhookSubscription[]>('/webhooks'),
        apiService.get<string[]>('/webhooks/event-types').catch(() => []),
      ]);
      setSubscriptions(subs || []);
      setEventTypes(types || []);
    } catch {
      toast.error('Failed to load webhook subscriptions');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    loadData();
  }, []);

  const handleCreate = async () => {
    if (!canSubmit) {
      toast.error('Name and URL are required');
      return;
    }
    try {
      setCreating(true);
      await apiService.post<WebhookSubscription>('/webhooks', {
        name: name.trim(),
        url: url.trim(),
        secret: secret.trim() || null,
        enabled,
        eventTypes: selectedEvents,
      });
      toast.success('Webhook subscription created');
      setName('');
      setUrl('');
      setSecret('');
      setSelectedEvents([]);
      await loadData();
    } catch {
      toast.error('Failed to create webhook subscription');
    } finally {
      setCreating(false);
    }
  };

  const handleToggle = async (subscription: WebhookSubscription, nextEnabled: boolean) => {
    try {
      await apiService.put<WebhookSubscription>(`/webhooks/${subscription.id}`, {
        name: subscription.name,
        url: subscription.url,
        secret: subscription.secret,
        enabled: nextEnabled,
        eventTypes: subscription.eventTypes,
      });
      setSubscriptions((current) =>
        current.map((item) => (item.id === subscription.id ? { ...item, enabled: nextEnabled } : item))
      );
    } catch {
      toast.error('Failed to update subscription');
    }
  };

  const handleDelete = async (subscription: WebhookSubscription) => {
    if (!window.confirm(`Delete webhook "${subscription.name}"?`)) {
      return;
    }
    try {
      await apiService.delete(`/webhooks/${subscription.id}`);
      setSubscriptions((current) => current.filter((item) => item.id !== subscription.id));
      toast.success('Webhook deleted');
    } catch {
      toast.error('Failed to delete webhook');
    }
  };

  const handleTest = async (subscription: WebhookSubscription) => {
    try {
      await apiService.post(`/webhooks/${subscription.id}/test`, {});
      toast.success('Test event dispatched');
    } catch {
      toast.error('Failed to send test event');
    }
  };

  const renderLastStatus = (subscription: WebhookSubscription) => {
    if (subscription.lastStatusCode) {
      return `${subscription.lastStatusCode}`;
    }
    if (subscription.lastFailureAt) {
      return 'Failed';
    }
    return '—';
  };

  return (
    <Box maxWidth={1100}>
      <Typography variant="h5" gutterBottom>
        Webhook Subscriptions
      </Typography>

      <Paper sx={{ p: 2, mb: 3 }}>
        <Stack spacing={2}>
          <Typography variant="subtitle1">Create subscription</Typography>
          <TextField
            label="Name"
            value={name}
            onChange={(event) => setName(event.target.value)}
            fullWidth
          />
          <TextField
            label="Endpoint URL"
            value={url}
            onChange={(event) => setUrl(event.target.value)}
            fullWidth
          />
          <TextField
            label="Signing Secret (optional)"
            value={secret}
            onChange={(event) => setSecret(event.target.value)}
            fullWidth
          />
          <Autocomplete
            multiple
            options={eventTypes}
            value={selectedEvents}
            onChange={(_, value) => setSelectedEvents(value)}
            renderInput={(params) => (
              <TextField {...params} label="Event Types (empty = all)" />
            )}
          />
          <FormControlLabel
            control={<Switch checked={enabled} onChange={(event) => setEnabled(event.target.checked)} />}
            label="Enabled"
          />
          <Box display="flex" gap={1} flexWrap="wrap">
            <Button
              variant="contained"
              onClick={handleCreate}
              disabled={!canSubmit || creating}
            >
              {creating ? 'Creating...' : 'Create'}
            </Button>
            <Button variant="outlined" startIcon={<Refresh />} onClick={loadData} disabled={loading}>
              Refresh
            </Button>
          </Box>
        </Stack>
      </Paper>

      <Paper sx={{ p: 2 }}>
        <Typography variant="subtitle1" gutterBottom>
          Existing subscriptions
        </Typography>
        <Divider sx={{ mb: 2 }} />
        <Table size="small">
          <TableHead>
            <TableRow>
              <TableCell>Name</TableCell>
              <TableCell>URL</TableCell>
              <TableCell>Events</TableCell>
              <TableCell>Enabled</TableCell>
              <TableCell>Status</TableCell>
              <TableCell align="right">Actions</TableCell>
            </TableRow>
          </TableHead>
          <TableBody>
            {subscriptions.map((subscription) => (
              <TableRow key={subscription.id}>
                <TableCell>{subscription.name}</TableCell>
                <TableCell sx={{ maxWidth: 260, wordBreak: 'break-all' }}>{subscription.url}</TableCell>
                <TableCell>
                  <Box display="flex" flexWrap="wrap" gap={0.5}>
                    {(subscription.eventTypes?.length ? subscription.eventTypes : ['ALL']).map((event) => (
                      <Chip key={event} label={event} size="small" variant="outlined" />
                    ))}
                  </Box>
                </TableCell>
                <TableCell>
                  <Switch
                    size="small"
                    checked={subscription.enabled}
                    onChange={(event) => handleToggle(subscription, event.target.checked)}
                  />
                </TableCell>
                <TableCell>{renderLastStatus(subscription)}</TableCell>
                <TableCell align="right">
                  <IconButton
                    size="small"
                    onClick={() => handleTest(subscription)}
                    aria-label="Send test"
                  >
                    <Send fontSize="small" />
                  </IconButton>
                  <IconButton
                    size="small"
                    color="error"
                    onClick={() => handleDelete(subscription)}
                    aria-label="Delete"
                  >
                    <Delete fontSize="small" />
                  </IconButton>
                </TableCell>
              </TableRow>
            ))}
            {subscriptions.length === 0 && (
              <TableRow>
                <TableCell colSpan={6}>
                  <Typography variant="body2" color="text.secondary">
                    No webhook subscriptions configured.
                  </Typography>
                </TableCell>
              </TableRow>
            )}
          </TableBody>
        </Table>
      </Paper>
    </Box>
  );
};

export default WebhookSubscriptionsPage;
