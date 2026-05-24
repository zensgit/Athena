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
import { useParams, useSearchParams } from 'react-router-dom';
import { toast } from 'react-toastify';
import siteInvitationService, {
  BulkInviteRequest,
  BulkInviteResponse,
  BulkInviteResult,
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
type SendStatusFilter = 'ALL' | 'FAILED';

const SEND_STATUS_QUERY_PARAM = 'sendStatus';
const FAILED_SEND_STATUS_QUERY_VALUE = 'failed';

const statusChipColor = (status: string): StatusColor => {
  if (status === 'PENDING') return 'warning';
  if (status === 'ACCEPTED') return 'success';
  return 'default';
};

const resolveSendStatusFilter = (value: string | null): SendStatusFilter => {
  return value?.toLowerCase() === FAILED_SEND_STATUS_QUERY_VALUE ? 'FAILED' : 'ALL';
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

// Exported so the test suite can lock the parser independently of the dialog.
// Splits on newline / comma / semicolon; trims surrounding whitespace; lowercases
// for case-insensitive dedup; preserves first-seen order; drops blanks and
// duplicates.
export const parseBulkEmails = (raw: string): string[] => {
  if (!raw) return [];
  const seen = new Set<string>();
  const ordered: string[] = [];
  for (const token of raw.split(/[\n,;]/)) {
    const trimmed = token.trim().toLowerCase();
    if (trimmed && !seen.has(trimmed)) {
      seen.add(trimmed);
      ordered.push(trimmed);
    }
  }
  return ordered;
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

// CreateInvitationDialog (bulk-capable since 2026-05-24)
//
// The dialog is intentionally always-bulk: a single textarea splits on newline /
// comma / semicolon, parseBulkEmails dedupes case-insensitively, and the
// service call always goes through createInvitationsBulk(siteId, ...) even when
// the operator only enters one address. Rationale: single frontend code path,
// single toast/error logic. The single-invite backend endpoint is kept for
// external callers, not used by this dialog.

interface CreateInvitationDialogProps {
  open: boolean;
  siteId: string;
  onClose: () => void;
  onBulkCreated: (response: BulkInviteResponse) => void;
}

interface BulkDialogForm {
  textarea: string;
  invitedRole: SiteMemberRole;
  message: string;
}

const INITIAL_BULK_FORM: BulkDialogForm = {
  textarea: '',
  invitedRole: 'CONSUMER',
  message: '',
};

const CreateInvitationDialog: React.FC<CreateInvitationDialogProps> = ({
  open,
  siteId,
  onClose,
  onBulkCreated,
}) => {
  const [form, setForm] = useState<BulkDialogForm>(INITIAL_BULK_FORM);
  const [submitting, setSubmitting] = useState(false);
  const [failedRows, setFailedRows] = useState<BulkInviteResult[]>([]);

  const parsedEmails = parseBulkEmails(form.textarea);

  const handleClose = () => {
    setForm(INITIAL_BULK_FORM);
    setFailedRows([]);
    onClose();
  };

  const summarizeResults = (results: BulkInviteResult[]) => {
    let sent = 0;
    let emailFailed = 0;
    let rowFailed = 0;
    let pending = 0;
    for (const r of results) {
      if (r.status === 'FAILED') {
        rowFailed += 1;
        continue;
      }
      const inv = r.invitation;
      if (inv?.lastSendStatus === 'SENT') sent += 1;
      else if (inv?.lastSendStatus === 'FAILED') emailFailed += 1;
      else pending += 1;
    }
    return { sent, emailFailed, rowFailed, pending };
  };

  const handleSubmit = async () => {
    if (parsedEmails.length === 0) return;
    setSubmitting(true);
    setFailedRows([]);
    try {
      const request: BulkInviteRequest = {
        inviteeEmails: parsedEmails,
        invitedRole: form.invitedRole,
        message: form.message.trim() || undefined,
      };
      const response = await siteInvitationService.createInvitationsBulk(siteId, request);
      // Always merge SUCCESS rows into the parent list, regardless of partial failure.
      onBulkCreated(response);

      const { sent, emailFailed, rowFailed, pending } = summarizeResults(response.results);
      const failedRowList = response.results.filter((r) => r.status === 'FAILED');

      if (rowFailed > 0) {
        // Stay open. Show the failed rows; drain successful emails from the textarea
        // so the operator can edit + retry only what didn't go through.
        const failedEmails = failedRowList.map((r) => r.inviteeEmail);
        setForm((prev) => ({ ...prev, textarea: failedEmails.join('\n') }));
        setFailedRows(failedRowList);
        toast.error(
          `Bulk invite: ${sent} sent, ${emailFailed} email-send failed, ${rowFailed} rejected, ${pending} pending.`,
        );
      } else if (emailFailed > 0) {
        toast.error(
          `Bulk invite: ${sent} sent, ${emailFailed} email-send failed, ${pending} pending. See the table for details.`,
        );
        handleClose();
      } else if (pending > 0) {
        toast.info(`Bulk invite: ${sent} sent, ${pending} pending.`);
        handleClose();
      } else {
        toast.success(`Bulk invite: ${sent} sent.`);
        handleClose();
      }
    } catch (err) {
      console.error('Failed to send bulk invitations', err);
      toast.error(resolveErrorMessage(err, 'Failed to send invitations.'));
    } finally {
      setSubmitting(false);
    }
  };

  const submitDisabled = parsedEmails.length === 0 || submitting;
  const submitLabel = submitting
    ? `Sending (${parsedEmails.length})...`
    : parsedEmails.length === 0
      ? 'Send invitations'
      : `Send ${parsedEmails.length} invitation${parsedEmails.length === 1 ? '' : 's'}`;

  return (
    <Dialog open={open} onClose={handleClose} fullWidth maxWidth="sm">
      <DialogTitle>Invite to Site</DialogTitle>
      <DialogContent>
        <Stack spacing={2} sx={{ mt: 1 }}>
          {failedRows.length > 0 && (
            <Alert
              severity="error"
              data-testid="bulk-invitation-failed-rows"
              sx={{ whiteSpace: 'pre-wrap' }}
            >
              <Typography variant="body2" component="div" sx={{ fontWeight: 600, mb: 1 }}>
                The following {failedRows.length} email
                {failedRows.length === 1 ? ' was' : 's were'} not invited:
              </Typography>
              {failedRows.map((row) => (
                <Typography
                  key={`${row.inviteeEmail}-${row.errorCategory ?? 'unknown'}`}
                  variant="caption"
                  component="div"
                  data-testid={`bulk-invitation-failed-row-${row.errorCategory ?? 'unknown'}`}
                >
                  <strong>{row.inviteeEmail || '(blank)'}</strong>
                  {' '}
                  ({row.errorCategory ?? 'UNKNOWN'}):
                  {' '}
                  {row.errorMessage ?? 'No detail available.'}
                </Typography>
              ))}
            </Alert>
          )}
          <TextField
            label="Invitee emails"
            value={form.textarea}
            onChange={(e) => setForm((prev) => ({ ...prev, textarea: e.target.value }))}
            required
            fullWidth
            multiline
            rows={6}
            autoFocus
            placeholder={'alice@example.com\nbob@example.com\nclaire@example.com'}
            helperText={
              parsedEmails.length === 0
                ? 'Newline-, comma-, or semicolon-separated. Duplicates and blanks are ignored.'
                : `Parsed ${parsedEmails.length} unique email${parsedEmails.length === 1 ? '' : 's'}.`
            }
            inputProps={{ 'data-testid': 'bulk-invitation-textarea' }}
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
            value={form.message}
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
          onClick={() => void handleSubmit()}
          disabled={submitDisabled}
          data-testid="bulk-invitation-submit"
        >
          {submitLabel}
        </Button>
      </DialogActions>
    </Dialog>
  );
};

// main page

const SiteInvitationsPage: React.FC = () => {
  const { siteId } = useParams<{ siteId: string }>();
  const [searchParams, setSearchParams] = useSearchParams();
  const [invitations, setInvitations] = useState<SiteInvitationDto[]>([]);
  const [loading, setLoading] = useState(false);
  const [loadError, setLoadError] = useState<string | null>(null);
  const [createOpen, setCreateOpen] = useState(false);
  const [cancelling, setCancelling] = useState<string | null>(null);
  const [resendingInvitationId, setResendingInvitationId] = useState<string | null>(null);
  const [bulkResendingFailed, setBulkResendingFailed] = useState(false);
  const [resendCandidate, setResendCandidate] = useState<SiteInvitationDto | null>(null);
  const [resendError, setResendError] = useState<string | null>(null);
  const sendStatusFilter = resolveSendStatusFilter(searchParams.get(SEND_STATUS_QUERY_PARAM));

  const handleSendStatusFilterChange = (nextFilter: SendStatusFilter) => {
    setSearchParams((current) => {
      const next = new URLSearchParams(current);
      if (nextFilter === 'FAILED') {
        next.set(SEND_STATUS_QUERY_PARAM, FAILED_SEND_STATUS_QUERY_VALUE);
      } else {
        next.delete(SEND_STATUS_QUERY_PARAM);
      }
      return next;
    }, { replace: true });
  };

  const failedSendCount = invitations.filter((inv) => inv.lastSendStatus === 'FAILED').length;
  const failedPendingInvitations = invitations.filter(
    (inv) => inv.status === 'PENDING' && inv.lastSendStatus === 'FAILED',
  );
  const failedPendingCount = failedPendingInvitations.length;
  const visibleInvitations = sendStatusFilter === 'FAILED'
    ? invitations.filter((inv) => inv.lastSendStatus === 'FAILED')
    : invitations;

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

  const handleBulkCreated = (response: BulkInviteResponse) => {
    // Prepend only SUCCESS rows that carry an invitation payload. Order
    // is preserved relative to the request order (bulk emits results in
    // input order); the table's existing createdDate-desc ordering is the
    // dominant sort on next refresh anyway.
    const newInvitations = response.results
      .filter((r) => r.status === 'SUCCESS' && r.invitation !== null)
      .map((r) => r.invitation as SiteInvitationDto);
    if (newInvitations.length === 0) return;
    setInvitations((prev) => [...newInvitations, ...prev]);
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

  const handleBulkResendFailed = async () => {
    if (!siteId || failedPendingInvitations.length === 0) return;
    const targets = [...failedPendingInvitations];
    if (!window.confirm(`Resend ${targets.length} failed invitation email(s)?`)) return;

    setBulkResendingFailed(true);
    try {
      const results = await Promise.allSettled(
        targets.map((invitation) => siteInvitationService.resendInvitation(siteId, invitation.id)),
      );
      const updatedById = new Map<string, SiteInvitationDto>();
      let sent = 0;
      let failed = 0;
      let pending = 0;

      results.forEach((result) => {
        if (result.status === 'rejected') {
          failed += 1;
          return;
        }
        const updated = result.value;
        updatedById.set(updated.id, updated);
        if (updated.lastSendStatus === 'SENT') {
          sent += 1;
        } else if (updated.lastSendStatus === 'FAILED') {
          failed += 1;
        } else {
          pending += 1;
        }
      });

      setInvitations((current) =>
        current.map((item) => updatedById.get(item.id) ?? item),
      );

      if (failed > 0) {
        toast.error(`Bulk resend finished: ${sent} sent, ${failed} failed, ${pending} pending.`);
      } else if (pending > 0) {
        toast.info(`Bulk resend finished: ${sent} sent, ${pending} pending.`);
      } else {
        toast.success(`Bulk resend finished: ${sent} sent.`);
      }
    } catch (err) {
      console.error('Failed to bulk resend failed invitations', err);
      toast.error(resolveErrorMessage(err, 'Failed to resend failed invitations.'));
    } finally {
      setBulkResendingFailed(false);
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

      <Paper variant="outlined" sx={{ p: 1.5, mb: 2 }}>
        <Stack
          direction={{ xs: 'column', sm: 'row' }}
          spacing={1.5}
          justifyContent="space-between"
          alignItems={{ xs: 'flex-start', sm: 'center' }}
        >
          <Stack
            direction={{ xs: 'column', sm: 'row' }}
            spacing={1}
            alignItems={{ xs: 'flex-start', sm: 'center' }}
          >
            <Typography variant="body2" color="text.secondary">
              Send result
            </Typography>
            <Stack direction="row" spacing={1}>
              <Chip
                label={`All (${invitations.length})`}
                clickable
                color={sendStatusFilter === 'ALL' ? 'primary' : 'default'}
                variant={sendStatusFilter === 'ALL' ? 'filled' : 'outlined'}
                onClick={() => handleSendStatusFilterChange('ALL')}
                aria-label={`Show all invitations (${invitations.length})`}
              />
              <Chip
                label={`Failed sends (${failedSendCount})`}
                clickable
                color={sendStatusFilter === 'FAILED' ? 'error' : 'default'}
                variant={sendStatusFilter === 'FAILED' ? 'filled' : 'outlined'}
                onClick={() => handleSendStatusFilterChange('FAILED')}
                aria-label={`Show failed-send invitations (${failedSendCount})`}
              />
            </Stack>
          </Stack>
          <Button
            size="small"
            variant="outlined"
            color="error"
            startIcon={<Send fontSize="small" />}
            disabled={failedPendingCount === 0 || bulkResendingFailed || loading}
            onClick={() => void handleBulkResendFailed()}
            aria-label={`Resend all failed pending invitations (${failedPendingCount})`}
          >
            {bulkResendingFailed ? 'Resending failed...' : `Resend failed (${failedPendingCount})`}
          </Button>
        </Stack>
      </Paper>

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
              {visibleInvitations.length === 0 ? (
                <TableRow>
                  <TableCell colSpan={7} align="center">
                    <Typography variant="body2" color="text.secondary" sx={{ py: 2 }}>
                      {invitations.length === 0
                        ? 'No invitations found. Use "Invite" to invite a new member.'
                        : 'No failed-send invitations found.'}
                    </Typography>
                  </TableCell>
                </TableRow>
              ) : (
                visibleInvitations.map((inv) => {
                  const isPending = inv.status === 'PENDING';
                  const isResending = resendingInvitationId === inv.id || bulkResendingFailed;
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
        onBulkCreated={handleBulkCreated}
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
