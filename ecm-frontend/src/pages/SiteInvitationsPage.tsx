import React, { useCallback, useEffect, useState } from 'react';
import {
  Alert,
  Box,
  Button,
  Chip,
  CircularProgress,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  FormControl,
  IconButton,
  InputLabel,
  MenuItem,
  Paper,
  Select,
  Stack,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  TextField,
  Tooltip,
  Typography,
} from '@mui/material';
import { Add, Cancel, Refresh } from '@mui/icons-material';
import { useParams } from 'react-router-dom';
import { toast } from 'react-toastify';
import siteInvitationService, {
  InviteRequest,
  SiteInvitationDto,
} from 'services/siteInvitationService';
import type { SiteMemberRole } from 'services/siteService';

// helpers

const formatDateTime = (value?: string | null): string => {
  if (!value) return '-';
  const parsed = new Date(value);
  if (Number.isNaN(parsed.getTime())) return value;
  return parsed.toLocaleString();
};

type StatusColor = 'warning' | 'success' | 'default';

const statusChipColor = (status: string): StatusColor => {
  if (status === 'PENDING') return 'warning';
  if (status === 'ACCEPTED') return 'success';
  return 'default';
};

// CreateInvitationDialog

interface CreateInvitationDialogProps {
  open: boolean;
  siteId: string;
  onClose: () => void;
  onCreated: (invitation: SiteInvitationDto) => void;
}

const CreateInvitationDialog: React.FC<CreateInvitationDialogProps> = ({
  open,
  siteId,
  onClose,
  onCreated,
}) => {
  const [form, setForm] = useState<InviteRequest>({
    inviteeEmail: '',
    invitedRole: 'CONSUMER',
    message: '',
  });
  const [submitting, setSubmitting] = useState(false);

  const handleClose = () => {
    setForm({ inviteeEmail: '', invitedRole: 'CONSUMER', message: '' });
    onClose();
  };

  const handleSubmit = async () => {
    const email = form.inviteeEmail.trim();
    if (!email) return;
    setSubmitting(true);
    try {
      const created = await siteInvitationService.createInvitation(siteId, {
        inviteeEmail: email,
        invitedRole: form.invitedRole || 'CONSUMER',
        message: form.message?.trim() || undefined,
      });
      toast.success(`Invitation sent to ${created.inviteeEmail}.`);
      onCreated(created);
      handleClose();
    } catch (err) {
      console.error('Failed to send invitation', err);
      toast.error('Failed to send invitation.');
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <Dialog open={open} onClose={handleClose} fullWidth maxWidth="sm">
      <DialogTitle>Invite to Site</DialogTitle>
      <DialogContent>
        <Stack spacing={2} sx={{ mt: 1 }}>
          <TextField
            label="Email address"
            type="email"
            value={form.inviteeEmail}
            onChange={(e) => setForm((prev) => ({ ...prev, inviteeEmail: e.target.value }))}
            required
            fullWidth
            autoFocus
          />
          <FormControl fullWidth size="small">
            <InputLabel id="site-invitation-role-label">Role</InputLabel>
            <Select
              labelId="site-invitation-role-label"
              id="site-invitation-role"
              label="Role"
              value={form.invitedRole ?? 'CONSUMER'}
              onChange={(e) => setForm((prev) => ({ ...prev, invitedRole: e.target.value as SiteMemberRole }))}
            >
              <MenuItem value="CONSUMER">Consumer</MenuItem>
              <MenuItem value="CONTRIBUTOR">Contributor</MenuItem>
              <MenuItem value="COLLABORATOR">Collaborator</MenuItem>
              <MenuItem value="MANAGER">Manager</MenuItem>
            </Select>
          </FormControl>
          <TextField
            label="Message (optional)"
            value={form.message ?? ''}
            onChange={(e) => setForm((prev) => ({ ...prev, message: e.target.value }))}
            fullWidth
            multiline
            rows={3}
            placeholder="Optional personal message to include in the invitation email..."
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
          disabled={!form.inviteeEmail.trim() || submitting}
        >
          {submitting ? 'Sending...' : 'Send Invitation'}
        </Button>
      </DialogActions>
    </Dialog>
  );
};

// main page

const SiteInvitationsPage: React.FC = () => {
  const { siteId } = useParams<{ siteId: string }>();
  const [invitations, setInvitations] = useState<SiteInvitationDto[]>([]);
  const [loading, setLoading] = useState(false);
  const [loadError, setLoadError] = useState<string | null>(null);
  const [createOpen, setCreateOpen] = useState(false);
  const [cancelling, setCancelling] = useState<string | null>(null);

  const loadInvitations = useCallback(async () => {
    if (!siteId) return;
    setLoading(true);
    setLoadError(null);
    try {
      const data = await siteInvitationService.listInvitations(siteId);
      setInvitations(data);
    } catch (err) {
      console.error('Failed to load invitations', err);
      setLoadError('Failed to load invitations.');
    } finally {
      setLoading(false);
    }
  }, [siteId]);

  useEffect(() => {
    void loadInvitations();
  }, [loadInvitations]);

  const handleCancel = async (invitationId: string, email: string) => {
    if (!siteId) return;
    if (!window.confirm(`Cancel invitation to ${email}?`)) return;
    setCancelling(invitationId);
    try {
      await siteInvitationService.cancelInvitation(siteId, invitationId);
      toast.success('Invitation cancelled.');
      setInvitations((prev) =>
        prev.map((inv) =>
          inv.id === invitationId ? { ...inv, status: 'CANCELLED' } : inv
        )
      );
    } catch (err) {
      console.error('Failed to cancel invitation', err);
      toast.error('Failed to cancel invitation.');
    } finally {
      setCancelling(null);
    }
  };

  const handleCreated = (invitation: SiteInvitationDto) => {
    setInvitations((prev) => [invitation, ...prev]);
  };

  if (!siteId) {
    return (
      <Box sx={{ p: 3 }}>
        <Alert severity="error">No site ID provided.</Alert>
      </Box>
    );
  }

  return (
    <Box sx={{ p: 3 }}>
      {/* Page header */}
      <Stack direction="row" alignItems="center" justifyContent="space-between" sx={{ mb: 3 }}>
        <Box>
          <Typography variant="h4" fontWeight={700}>
            Site Invitations
          </Typography>
          <Typography variant="body2" color="text.secondary" sx={{ mt: 0.5 }}>
            Manage invitations for site <strong>{siteId}</strong>.
          </Typography>
        </Box>
        <Stack direction="row" spacing={1}>
          <Button
            variant="outlined"
            startIcon={<Refresh />}
            onClick={() => void loadInvitations()}
            disabled={loading}
          >
            Refresh
          </Button>
          <Button
            variant="contained"
            startIcon={<Add />}
            onClick={() => setCreateOpen(true)}
          >
            Invite
          </Button>
        </Stack>
      </Stack>

      {loadError && (
        <Alert severity="error" sx={{ mb: 2 }}>
          {loadError}
        </Alert>
      )}

      {loading ? (
        <Box sx={{ display: 'flex', justifyContent: 'center', py: 6 }}>
          <CircularProgress />
        </Box>
      ) : (
        <TableContainer component={Paper} variant="outlined">
          <Table size="small">
            <TableHead>
              <TableRow>
                <TableCell>Email</TableCell>
                <TableCell>Role</TableCell>
                <TableCell>Status</TableCell>
                <TableCell>Invited By</TableCell>
                <TableCell>Expires</TableCell>
                <TableCell align="right">Actions</TableCell>
              </TableRow>
            </TableHead>
            <TableBody>
              {invitations.length === 0 ? (
                <TableRow>
                  <TableCell colSpan={6} align="center">
                    <Typography variant="body2" color="text.secondary" sx={{ py: 2 }}>
                      No invitations found. Use "Invite" to invite a new member.
                    </Typography>
                  </TableCell>
                </TableRow>
              ) : (
                invitations.map((inv) => (
                  <TableRow key={inv.id} hover>
                    <TableCell>
                      <Typography variant="body2">{inv.inviteeEmail}</Typography>
                      {inv.inviteeUsername && (
                        <Typography variant="caption" color="text.secondary">
                          @{inv.inviteeUsername}
                        </Typography>
                      )}
                    </TableCell>
                    <TableCell>
                      <Chip label={inv.invitedRole} size="small" variant="outlined" />
                    </TableCell>
                    <TableCell>
                      <Chip
                        label={inv.status}
                        size="small"
                        color={statusChipColor(inv.status)}
                      />
                    </TableCell>
                    <TableCell>
                      <Typography variant="body2">{inv.invitedBy}</Typography>
                    </TableCell>
                    <TableCell>
                      <Typography variant="body2">{formatDateTime(inv.expiresAt)}</Typography>
                    </TableCell>
                    <TableCell align="right">
                      {inv.status === 'PENDING' && (
                        <Tooltip title="Cancel invitation">
                          <span>
                            <IconButton
                              size="small"
                              color="error"
                              aria-label="Cancel invitation"
                              disabled={cancelling === inv.id}
                              onClick={() => void handleCancel(inv.id, inv.inviteeEmail)}
                            >
                              <Cancel fontSize="small" />
                            </IconButton>
                          </span>
                        </Tooltip>
                      )}
                    </TableCell>
                  </TableRow>
                ))
              )}
            </TableBody>
          </Table>
        </TableContainer>
      )}

      <CreateInvitationDialog
        open={createOpen}
        siteId={siteId}
        onClose={() => setCreateOpen(false)}
        onCreated={handleCreated}
      />
    </Box>
  );
};

export default SiteInvitationsPage;
