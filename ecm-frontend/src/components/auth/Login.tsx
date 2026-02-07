import React, { useEffect, useState } from 'react';
import { Alert, Box, Card, CardContent, Button, Typography } from '@mui/material';
import authService from 'services/authService';
import {
  AUTH_INIT_STATUS_ERROR,
  AUTH_INIT_STATUS_KEY,
  AUTH_INIT_STATUS_TIMEOUT,
  LOGIN_IN_PROGRESS_KEY,
  LOGIN_IN_PROGRESS_STARTED_AT_KEY,
} from 'constants/auth';

const Login: React.FC = () => {
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [authInitMessage, setAuthInitMessage] = useState<string | null>(null);

  useEffect(() => {
    const initStatus = sessionStorage.getItem(AUTH_INIT_STATUS_KEY);
    if (initStatus === AUTH_INIT_STATUS_TIMEOUT) {
      setAuthInitMessage('Sign-in initialization timed out. Please retry.');
    } else if (initStatus === AUTH_INIT_STATUS_ERROR) {
      setAuthInitMessage('Sign-in initialization failed. Please retry.');
    }
    sessionStorage.removeItem(AUTH_INIT_STATUS_KEY);
    sessionStorage.removeItem(LOGIN_IN_PROGRESS_KEY);
    sessionStorage.removeItem(LOGIN_IN_PROGRESS_STARTED_AT_KEY);
  }, []);

  const handleLogin = async () => {
    setSubmitting(true);
    setError(null);
    setAuthInitMessage(null);
    sessionStorage.removeItem(AUTH_INIT_STATUS_KEY);
    try {
      await authService.login({ redirectUri: window.location.origin + '/' });
    } catch (loginError) {
      setError(loginError instanceof Error ? loginError.message : 'Unable to start sign-in.');
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <Box
      sx={{
        minHeight: '100vh',
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        bgcolor: 'background.default',
      }}
    >
      <Card sx={{ maxWidth: 400, width: '100%', mx: 2 }}>
        <CardContent sx={{ p: 4, textAlign: 'center' }}>
          <Typography variant="h4" component="h1" gutterBottom>
            Athena ECM
          </Typography>
          <Typography variant="body2" color="text.secondary" sx={{ mb: 3 }}>
            Sign in with your organization account
          </Typography>
          {authInitMessage && (
            <Alert severity="warning" sx={{ mb: 2, textAlign: 'left' }}>
              {authInitMessage}
            </Alert>
          )}
          {error && (
            <Alert severity="error" sx={{ mb: 2, textAlign: 'left' }}>
              Sign-in failed. {error}
            </Alert>
          )}
          <Button variant="contained" fullWidth onClick={handleLogin} disabled={submitting}>
            Sign in with Keycloak
          </Button>
        </CardContent>
      </Card>
    </Box>
  );
};

export default Login;
