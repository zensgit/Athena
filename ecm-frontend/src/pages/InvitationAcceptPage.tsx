import React, { useState } from 'react';
import {
  Alert,
  Box,
  Button,
  Card,
  CardContent,
  Chip,
  CircularProgress,
  Stack,
  Typography,
} from '@mui/material';
import { CheckCircle, Cancel, Home } from '@mui/icons-material';
import { useNavigate, useSearchParams } from 'react-router-dom';
import { toast } from 'react-toastify';
import siteInvitationService, { SiteInvitationDto } from 'services/siteInvitationService';

// ── helpers ───────────────────────────────────────────────────────────────────

const formatDateTime = (value?: string | null): string => {
  if (!value) return '—';
  const parsed = new Date(value);
  if (Number.isNaN(parsed.getTime())) return value;
  return parsed.toLocaleString();
};

// ── page ──────────────────────────────────────────────────────────────────────

type PageState = 'idle' | 'submitting' | 'accepted' | 'rejected' | 'error';

const InvitationAcceptPage: React.FC = () => {
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();
  const token = searchParams.get('token') ?? '';

  const [pageState, setPageState] = useState<PageState>('idle');
  const [result, setResult] = useState<SiteInvitationDto | null>(null);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);

  const handleAccept = async () => {
    if (!token) {
      setErrorMessage('No invitation token found in the URL.');
      setPageState('error');
      return;
    }
    setPageState('submitting');
    try {
      const dto = await siteInvitationService.acceptInvitation({ token });
      setResult(dto);
      setPageState('accepted');
      toast.success(`You have joined "${dto.siteTitle}" as ${dto.invitedRole}.`);
    } catch (err) {
      console.error('Failed to accept invitation', err);
      setErrorMessage(
        'Failed to accept the invitation. It may have expired, already been used, or been cancelled.'
      );
      setPageState('error');
    }
  };

  const handleReject = async () => {
    if (!token) {
      setErrorMessage('No invitation token found in the URL.');
      setPageState('error');
      return;
    }
    setPageState('submitting');
    try {
      const dto = await siteInvitationService.rejectInvitation({ token });
      setResult(dto);
      setPageState('rejected');
      toast.info(`Invitation to "${dto.siteTitle}" rejected.`);
    } catch (err) {
      console.error('Failed to reject invitation', err);
      setErrorMessage(
        'Failed to reject the invitation. It may have expired, already been used, or been cancelled.'
      );
      setPageState('error');
    }
  };

  // ── no token ──────────────────────────────────────────────────────────────

  if (!token) {
    return (
      <Box sx={{ display: 'flex', justifyContent: 'center', alignItems: 'center', minHeight: '60vh', p: 3 }}>
        <Card variant="outlined" sx={{ maxWidth: 520, width: '100%' }}>
          <CardContent sx={{ p: 4 }}>
            <Alert severity="error" sx={{ mb: 2 }}>
              No invitation token found. Please use the link from your invitation email.
            </Alert>
            <Button startIcon={<Home />} onClick={() => navigate('/')}>
              Go to Home
            </Button>
          </CardContent>
        </Card>
      </Box>
    );
  }

  // ── error state ───────────────────────────────────────────────────────────

  if (pageState === 'error') {
    return (
      <Box sx={{ display: 'flex', justifyContent: 'center', alignItems: 'center', minHeight: '60vh', p: 3 }}>
        <Card variant="outlined" sx={{ maxWidth: 520, width: '100%' }}>
          <CardContent sx={{ p: 4 }}>
            <Alert severity="error" sx={{ mb: 3 }}>
              {errorMessage}
            </Alert>
            <Button startIcon={<Home />} onClick={() => navigate('/')}>
              Go to Home
            </Button>
          </CardContent>
        </Card>
      </Box>
    );
  }

  // ── accepted state ────────────────────────────────────────────────────────

  if (pageState === 'accepted' && result) {
    return (
      <Box sx={{ display: 'flex', justifyContent: 'center', alignItems: 'center', minHeight: '60vh', p: 3 }}>
        <Card variant="outlined" sx={{ maxWidth: 520, width: '100%' }}>
          <CardContent sx={{ p: 4 }}>
            <Stack spacing={2} alignItems="center" textAlign="center">
              <CheckCircle color="success" sx={{ fontSize: 56 }} />
              <Typography variant="h5" fontWeight={700}>
                You're in!
              </Typography>
              <Typography variant="body1" color="text.secondary">
                You have joined <strong>{result.siteTitle}</strong> as{' '}
                <strong>{result.invitedRole}</strong>.
              </Typography>
              <Stack direction="row" spacing={1} sx={{ mt: 1 }}>
                <Button
                  variant="contained"
                  onClick={() => navigate('/sites')}
                >
                  Go to Sites
                </Button>
                <Button onClick={() => navigate('/')}>
                  Home
                </Button>
              </Stack>
            </Stack>
          </CardContent>
        </Card>
      </Box>
    );
  }

  // ── rejected state ────────────────────────────────────────────────────────

  if (pageState === 'rejected' && result) {
    return (
      <Box sx={{ display: 'flex', justifyContent: 'center', alignItems: 'center', minHeight: '60vh', p: 3 }}>
        <Card variant="outlined" sx={{ maxWidth: 520, width: '100%' }}>
          <CardContent sx={{ p: 4 }}>
            <Stack spacing={2} alignItems="center" textAlign="center">
              <Cancel color="action" sx={{ fontSize: 56 }} />
              <Typography variant="h5" fontWeight={700}>
                Invitation declined
              </Typography>
              <Typography variant="body1" color="text.secondary">
                You have declined the invitation to join <strong>{result.siteTitle}</strong>.
              </Typography>
              <Button startIcon={<Home />} onClick={() => navigate('/')}>
                Go to Home
              </Button>
            </Stack>
          </CardContent>
        </Card>
      </Box>
    );
  }

  // ── idle / submitting (action-first) ──────────────────────────────────────

  return (
    <Box sx={{ display: 'flex', justifyContent: 'center', alignItems: 'center', minHeight: '60vh', p: 3 }}>
      <Card variant="outlined" sx={{ maxWidth: 520, width: '100%' }}>
        <CardContent sx={{ p: 4 }}>
          <Stack spacing={3}>
            <Box>
              <Typography variant="h5" fontWeight={700} gutterBottom>
                Site Invitation
              </Typography>
              <Typography variant="body2" color="text.secondary">
                You have been invited to join a site. Accept or decline the invitation below.
              </Typography>
            </Box>

            <Box sx={{ display: 'flex', gap: 1, flexWrap: 'wrap', alignItems: 'center' }}>
              <Chip label="Invitation token present" size="small" color="success" variant="outlined" />
            </Box>

            {pageState === 'submitting' ? (
              <Box sx={{ display: 'flex', justifyContent: 'center', py: 2 }}>
                <CircularProgress />
              </Box>
            ) : (
              <Stack direction="row" spacing={2}>
                <Button
                  variant="contained"
                  color="success"
                  startIcon={<CheckCircle />}
                  onClick={() => void handleAccept()}
                  fullWidth
                >
                  Accept Invitation
                </Button>
                <Button
                  variant="outlined"
                  color="error"
                  startIcon={<Cancel />}
                  onClick={() => void handleReject()}
                  fullWidth
                >
                  Decline
                </Button>
              </Stack>
            )}

            <Typography variant="caption" color="text.secondary" textAlign="center">
              Invitation details will be shown after you respond.
            </Typography>
          </Stack>
        </CardContent>
      </Card>
    </Box>
  );
};

export default InvitationAcceptPage;
