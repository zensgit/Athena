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
  TextField,
  Typography,
} from '@mui/material';
import { CheckCircle, Cancel, Home } from '@mui/icons-material';
import { useNavigate, useSearchParams } from 'react-router-dom';
import { toast } from 'react-toastify';
import siteInvitationService, { SiteInvitationDto } from 'services/siteInvitationService';

// page

type PageState = 'idle' | 'submitting' | 'accepted' | 'rejected' | 'error';

const InvitationAcceptPage: React.FC = () => {
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();
  const urlToken = searchParams.get('token')?.trim() ?? '';

  const [manualToken, setManualToken] = useState('');
  const [pageState, setPageState] = useState<PageState>('idle');
  const [result, setResult] = useState<SiteInvitationDto | null>(null);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);
  const token = urlToken || manualToken.trim();

  const handleAccept = async () => {
    if (!token) {
      setErrorMessage('Enter an invitation token first.');
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
      setErrorMessage('Enter an invitation token first.');
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

  // error state

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

  // accepted state

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

  // rejected state

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

  // idle / submitting

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
              <Chip
                label={urlToken ? 'Invitation token present' : 'Manual token entry'}
                size="small"
                color={urlToken ? 'success' : 'warning'}
                variant="outlined"
              />
            </Box>

            {!urlToken && (
              <Alert severity="info">
                Paste the invitation token from your email if the email did not include an accept link.
              </Alert>
            )}

            {!urlToken && (
              <TextField
                label="Invitation token"
                value={manualToken}
                onChange={(event) => setManualToken(event.target.value)}
                fullWidth
                autoFocus
                placeholder="Paste invitation token"
              />
            )}

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
                  disabled={!token}
                  fullWidth
                >
                  Accept Invitation
                </Button>
                <Button
                  variant="outlined"
                  color="error"
                  startIcon={<Cancel />}
                  onClick={() => void handleReject()}
                  disabled={!token}
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
