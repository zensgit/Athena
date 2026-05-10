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
  DialogContentText,
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
import { Add, Cancel, Refresh, Send } from '@mui/icons-material';
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

const ERROR_CAPTION_MAX_CHARS = 80;

const truncateForCaption = (value: string): string => {
  if (value.length <= ERROR_CAPTION_MAX_CHARS) return value;
  return `${value.slice(0, ERROR_CAPTION_MAX_CHARS - 1)}…`;
};

const resolveErrorMessage = (err: unknown, fallback: string): string => {
  const responseMessage = (err as { response?: { data?: { message?: unknown } } })?.response?.data?.message;
  if (typeof responseMessage === 'string' && responseMessage.trim()) {
    return responseMessage.trim();
  }
  if (err instanceof Error && err.message) {
    return err.message;
  }
  return fallback;
};

const buildSendFailureMessage = (
  invitation: SiteInvitationDto,
  actionLabel: 'Invitation created' | 'Resend attempted',
): string => {
  const base = `${actionLabel} for ${invitation.inviteeEmail}, but email send failed`;
  const detail = invitation.lastSendError?.trim();
  return detail ? `${base}: ${detail}` : `${base}.`;
};

interface SendStatusCellProps {
  invitation: SiteInvitationDto;
}

// Send status / last attempt / attempt count are co-located in one cell so
// the existing dense table style stays readable. Chip drives the primary
// signal; captions surface the timestamps and the truncated error.
const SendStatusCell: React.FC<SendStatusCellProps> = ({ invitation }) => {
  const { lastSendStatus, lastSendError, lastSendAttemptAt, lastSentAt, sendAttemptCount } = invitation;

  let chip: React.ReactNode;
  if (lastSendStatus === 'SENT') {
    chip = <Chip label="SENT" size="small" color="success" />;
  } else if (lastSendStatus === 'FAILED') {
    chip = <Chip label="FAILED" size="small" color="error" />;
  } else {
    chip = <Chip label="Not yet sent" size="small" color="default" variant="outlined" />;
  }

  return (
    <Stack spacing={0.5}>
      {chip}
      <Typography variant="caption" color="text.secondary">
        last attempt: {formatDateTime(lastSendAttemptAt)}
      </Typography>
      {lastSentAt && (
        <Typography variant="caption" color="text.secondary">
          last sent: {formatDateTime(lastSentAt)}
        </Typography>
      )}
      <Typography variant="caption" color="text.secondary">
        attempts: {sendAttemptCount}
      </Typography>
      {lastSendStatus === 'FAILED' && lastSendError && (
        <Tooltip title={lastSendError}>
          <Typography
            variant="caption"
            color="error"
            data-testid={`invitation-send-error-${invitation.id}`}
          >
            {truncateForCaption(lastSendError)}
          </Typography>
        </Tooltip>
      )}
    </Stack>
  );
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
      if (created.lastSendStatus === 'FAILED') {
        toast.error(buildSendFailureMessage(created, 'Invitation created'));
      } else if (created.lastSendStatus === 'SENT') {
        toast.success(`Invitation sent to ${created.inviteeEmail}.`);
      } else {
        toast.info(`Invitation created for ${created.inviteeEmail}; email send status is pending.`);
      }
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
  const [resendingInvitationId, setResendingInvitationId] = useState<string | null>(null);
  const [resendCandidate, setResendCandidate] = useState<SiteInvitationDto | null>(null);
  const [resendError, setResendError] = useState<string | null>(null);

  const loadInvitations = useCallback(async () => {
    if (!siteId) return;
    setLoading(true);
    setLoadError(null);
    try {
      const data = await siteInvitationService.listInvitations(siteId);
      setInvitations(data);
    } catch (err) {
      console.error('Failed to load invitations', err);
      setLoadError(resolveErrorMessage(err, 'Failed to load invitations.'));
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

  const openResendDialog = (invitation: SiteInvitationDto) => {
    setResendError(null);
    setResendCandidate(invitation);
  };

  const closeResendDialog = () => {
    if (resendingInvitationId) return;
    setResendCandidate(null);
    setResendError(null);
  };

  const handleResendConfirm = async () => {
    if (!siteId || !resendCandidate) return;
    const target = resendCandidate;
    setResendingInvitationId(target.id);
    setResendError(null);
    try {
      const updated = await siteInvitationService.resendInvitation(siteId, target.id);
      setInvitations((current) =>
        current.map((item) => (item.id === updated.id ? updated : item)),
      );
      if (updated.lastSendStatus === 'FAILED') {
        const failureMessage = buildSendFailureMessage(updated, 'Resend attempted');
        toast.error(failureMessage);
        setResendCandidate(updated);
        setResendError(failureMessage);
        return;
      }
      if (updated.lastSendStatus === 'SENT') {
        toast.success(`Invitation re-sent to ${updated.inviteeEmail}.`);
      } else {
        toast.info(`Resend completed for ${updated.inviteeEmail}; email send status is pending.`);
      }
      setResendCandidate(null);
    } catch (err) {
      console.error('Failed to resend invitation', err);
      setResendError(resolveErrorMessage(err, 'Failed to resend invitation.'));
    } finally {
      setResendingInvitationId(null);
    }
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
                <TableCell>Send status</TableCell>
                <TableCell>Invited By</TableCell>
                <TableCell>Expires</TableCell>
                <TableCell align="right">Actions</TableCell>
              </TableRow>
            </TableHead>
            <TableBody>
              {invitations.length === 0 ? (
                <TableRow>
                  <TableCell colSpan={7} align="center">
                    <Typography variant="body2" color="text.secondary" sx={{ py: 2 }}>
                      No invitations found. Use "Invite" to invite a new member.
                    </Typography>
                  </TableCell>
                </TableRow>
              ) : (
                invitations.map((inv) => {
                  const isPending = inv.status === 'PENDING';
                  const isResending = resendingInvitationId === inv.id;
                  return (
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
                        <SendStatusCell invitation={inv} />
                      </TableCell>
                      <TableCell>
                        <Typography variant="body2">{inv.invitedBy}</Typography>
                      </TableCell>
                      <TableCell>
                        <Typography variant="body2">{formatDateTime(inv.expiresAt)}</Typography>
                      </TableCell>
                      <TableCell align="right">
                        <Stack direction="row" spacing={1} justifyContent="flex-end">
                          <Button
                            size="small"
                            variant="outlined"
                            startIcon={<Send fontSize="small" />}
                            disabled={!isPending || isResending}
                            onClick={() => openResendDialog(inv)}
                            aria-label={`Resend invitation to ${inv.inviteeEmail}`}
                          >
                            {isResending ? 'Resending...' : 'Resend email'}
                          </Button>
                          <Tooltip title={isPending ? 'Cancel invitation' : ''}>
                            <span>
                              <IconButton
                                size="small"
                                color="error"
                                aria-label={`Cancel invitation to ${inv.inviteeEmail}`}
                                disabled={!isPending || cancelling === inv.id}
                                onClick={() => void handleCancel(inv.id, inv.inviteeEmail)}
                              >
                                <Cancel fontSize="small" />
                              </IconButton>
                            </span>
                          </Tooltip>
                        </Stack>
                      </TableCell>
                    </TableRow>
                  );
                })
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

      <Dialog
        open={resendCandidate !== null}
        onClose={closeResendDialog}
        fullWidth
        maxWidth="xs"
        aria-labelledby="resend-invitation-dialog-title"
      >
        <DialogTitle id="resend-invitation-dialog-title">Resend invitation</DialogTitle>
        <DialogContent>
          <DialogContentText>
            Resend invitation email to <strong>{resendCandidate?.inviteeEmail ?? ''}</strong>?
            The recipient will receive another copy of the invitation.
          </DialogContentText>
          {resendError && (
            <Alert severity="error" sx={{ mt: 2 }} data-testid="resend-invitation-error">
              {resendError}
            </Alert>
          )}
        </DialogContent>
        <DialogActions>
          <Button
            onClick={closeResendDialog}
            disabled={resendingInvitationId !== null}
          >
            Cancel
          </Button>
          <Button
            variant="contained"
            onClick={() => void handleResendConfirm()}
            disabled={resendingInvitationId !== null}
          >
            {resendingInvitationId !== null ? 'Resending...' : 'Resend'}
          </Button>
        </DialogActions>
      </Dialog>
    </Box>
  );
};

export default SiteInvitationsPage;
