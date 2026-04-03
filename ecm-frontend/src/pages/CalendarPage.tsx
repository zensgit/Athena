import React, { useEffect, useState } from 'react';
import {
  Box, Button, Card, CardContent, Chip, CircularProgress, Dialog,
  DialogActions, DialogContent, DialogTitle, IconButton, Paper,
  Stack, TextField, Tooltip, Typography,
} from '@mui/material';
import { Add, Delete, Event, Refresh } from '@mui/icons-material';
import { useParams } from 'react-router-dom';
import { toast } from 'react-toastify';
import calendarService, { CalendarEventDto } from 'services/calendarService';
import { useAppSelector } from 'store';
import authService from 'services/authService';

const CalendarPage: React.FC = () => {
  const { siteId } = useParams<{ siteId: string }>();
  const { user } = useAppSelector((state) => state.auth);
  const effectiveUser = user ?? authService.getCurrentUser();
  const currentUsername = effectiveUser?.username;
  const isAdmin = Boolean(effectiveUser?.roles?.includes('ROLE_ADMIN'));

  const [events, setEvents] = useState<CalendarEventDto[]>([]);
  const [loading, setLoading] = useState(false);
  const [rangeFrom, setRangeFrom] = useState(() => {
    const d = new Date(); d.setDate(1); d.setHours(0,0,0,0);
    return d.toISOString().slice(0, 16);
  });
  const [rangeTo, setRangeTo] = useState(() => {
    const d = new Date(); d.setMonth(d.getMonth() + 1, 0); d.setHours(23,59,59,0);
    return d.toISOString().slice(0, 16);
  });

  // create dialog
  const [createOpen, setCreateOpen] = useState(false);
  const [form, setForm] = useState({ title: '', description: '', location: '', startDate: '', endDate: '', allDay: false });

  const load = async () => {
    if (!siteId) return;
    setLoading(true);
    try {
      const data = await calendarService.getEventsByRange(siteId, rangeFrom, rangeTo);
      setEvents(data);
    } catch { toast.error('Failed to load events'); }
    finally { setLoading(false); }
  };

  useEffect(() => { void load(); }, [siteId]); // eslint-disable-line react-hooks/exhaustive-deps

  const handleCreate = async () => {
    if (!siteId || !form.title.trim() || !form.startDate || !form.endDate) {
      toast.warn('Title, start date, and end date required');
      return;
    }
    try {
      await calendarService.createEvent(siteId, {
        title: form.title.trim(),
        description: form.description.trim() || undefined,
        location: form.location.trim() || undefined,
        startDate: form.startDate,
        endDate: form.endDate,
        allDay: form.allDay,
      });
      setCreateOpen(false);
      setForm({ title: '', description: '', location: '', startDate: '', endDate: '', allDay: false });
      toast.success('Event created');
      await load();
    } catch { toast.error('Failed to create event'); }
  };

  const handleDelete = async (eventId: string) => {
    if (!siteId || !window.confirm('Delete this event?')) return;
    try {
      await calendarService.deleteEvent(siteId, eventId);
      toast.success('Event deleted');
      await load();
    } catch { toast.error('Failed to delete'); }
  };

  const canModify = (event: CalendarEventDto) => event.createdBy === currentUsername || isAdmin;

  const formatRange = (start: string, end: string, allDay: boolean) => {
    const s = new Date(start);
    const e = new Date(end);
    if (allDay) return s.toLocaleDateString() + (s.toDateString() !== e.toDateString() ? ' - ' + e.toLocaleDateString() : ' (all day)');
    return s.toLocaleString() + ' - ' + e.toLocaleString();
  };

  return (
    <Box maxWidth={900}>
      <Box display="flex" justifyContent="space-between" alignItems="center" mb={2}>
        <Box>
          <Typography variant="h5">Calendar — {siteId}</Typography>
          <Typography variant="body2" color="text.secondary">Site calendar events</Typography>
        </Box>
        <Stack direction="row" spacing={1}>
          <Button variant="outlined" startIcon={<Refresh />} onClick={() => void load()} disabled={loading}>Refresh</Button>
          <Button variant="contained" startIcon={<Add />} onClick={() => setCreateOpen(true)}>New Event</Button>
        </Stack>
      </Box>

      {/* Range filter */}
      <Box display="flex" gap={2} mb={2}>
        <TextField size="small" label="From" type="datetime-local" value={rangeFrom} onChange={(e) => setRangeFrom(e.target.value)} InputLabelProps={{ shrink: true }} />
        <TextField size="small" label="To" type="datetime-local" value={rangeTo} onChange={(e) => setRangeTo(e.target.value)} InputLabelProps={{ shrink: true }} />
        <Button variant="outlined" onClick={() => void load()}>Apply</Button>
      </Box>

      {loading ? (
        <Box display="flex" justifyContent="center" p={4}><CircularProgress /></Box>
      ) : (
        <Stack spacing={1}>
          {events.map((event) => (
            <Card key={event.id} variant="outlined">
              <CardContent sx={{ py: 1.5, '&:last-child': { pb: 1.5 } }}>
                <Box display="flex" justifyContent="space-between" alignItems="flex-start">
                  <Box display="flex" gap={1.5} alignItems="flex-start">
                    <Event color="primary" sx={{ mt: 0.3 }} />
                    <Box>
                      <Typography variant="subtitle2">{event.title}</Typography>
                      <Typography variant="caption" color="text.secondary">
                        {formatRange(event.startDate, event.endDate, event.allDay)}
                      </Typography>
                      {event.location && (
                        <Typography variant="caption" color="text.secondary" display="block">
                          {event.location}
                        </Typography>
                      )}
                      {event.allDay && <Chip label="All day" size="small" sx={{ ml: 1 }} />}
                    </Box>
                  </Box>
                  {canModify(event) && (
                    <Tooltip title="Delete">
                      <IconButton size="small" color="error" onClick={() => void handleDelete(event.id)}>
                        <Delete fontSize="small" />
                      </IconButton>
                    </Tooltip>
                  )}
                </Box>
              </CardContent>
            </Card>
          ))}
          {events.length === 0 && (
            <Paper sx={{ p: 3, textAlign: 'center' }}><Typography color="text.secondary">No events in this range</Typography></Paper>
          )}
        </Stack>
      )}

      <Dialog open={createOpen} onClose={() => setCreateOpen(false)} maxWidth="sm" fullWidth>
        <DialogTitle>New Calendar Event</DialogTitle>
        <DialogContent>
          <Stack spacing={2} mt={1}>
            <TextField label="Title" value={form.title} onChange={(e) => setForm((p) => ({ ...p, title: e.target.value }))} required fullWidth />
            <TextField label="Description" value={form.description} onChange={(e) => setForm((p) => ({ ...p, description: e.target.value }))} fullWidth multiline minRows={2} />
            <TextField label="Location" value={form.location} onChange={(e) => setForm((p) => ({ ...p, location: e.target.value }))} fullWidth />
            <TextField label="Start" type="datetime-local" value={form.startDate} onChange={(e) => setForm((p) => ({ ...p, startDate: e.target.value }))} required fullWidth InputLabelProps={{ shrink: true }} />
            <TextField label="End" type="datetime-local" value={form.endDate} onChange={(e) => setForm((p) => ({ ...p, endDate: e.target.value }))} required fullWidth InputLabelProps={{ shrink: true }} />
          </Stack>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setCreateOpen(false)}>Cancel</Button>
          <Button variant="contained" onClick={() => void handleCreate()}>Create</Button>
        </DialogActions>
      </Dialog>
    </Box>
  );
};

export default CalendarPage;
