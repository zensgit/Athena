import React, { useEffect, useState } from 'react';
import { Alert, Box, Card, CardContent, Button, Typography } from '@mui/material';
import authService from 'services/authService';
import {
  AUTH_REDIRECT_FAILURE_COOLDOWN_MS,
  AUTH_REDIRECT_FAILURE_COUNT_KEY,
  AUTH_REDIRECT_FAILURE_WINDOW_MS,
  AUTH_REDIRECT_LAST_FAILURE_AT_KEY,
  AUTH_REDIRECT_MAX_AUTO_ATTEMPTS,
  AUTH_INIT_STATUS_ERROR,
  AUTH_INIT_STATUS_KEY,
  AUTH_INIT_STATUS_REDIRECT_FAILED,
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
    } else if (initStatus === AUTH_INIT_STATUS_REDIRECT_FAILED) {
      const failureCount = Number(sessionStorage.getItem(AUTH_REDIRECT_FAILURE_COUNT_KEY) || '0');
      const lastFailureAt = Number(sessionStorage.getItem(AUTH_REDIRECT_LAST_FAILURE_AT_KEY) || '0');
      if (failureCount >= AUTH_REDIRECT_MAX_AUTO_ATTEMPTS) {
        const elapsed = lastFailureAt > 0 ? Date.now() - lastFailureAt : AUTH_REDIRECT_FAILURE_WINDOW_MS;
        const remainingMs = Math.max(0, AUTH_REDIRECT_FAILURE_WINDOW_MS - elapsed);
        const remainingSeconds = Math.ceil(remainingMs / 1000);
        if (remainingSeconds > 0) {
          setAuthInitMessage(
            `Automatic sign-in is paused after repeated failures (${failureCount}/${AUTH_REDIRECT_MAX_AUTO_ATTEMPTS}). Auto retry resumes in ~${remainingSeconds}s. Click Sign in with Keycloak to retry now.`
          );
        } else {
          setAuthInitMessage(
            `Automatic sign-in is paused after repeated failures (${failureCount}/${AUTH_REDIRECT_MAX_AUTO_ATTEMPTS}). Click Sign in with Keycloak to retry.`
          );
        }
      } else {
        const elapsed = lastFailureAt > 0 ? Date.now() - lastFailureAt : AUTH_REDIRECT_FAILURE_COOLDOWN_MS;
        const remainingMs = Math.max(0, AUTH_REDIRECT_FAILURE_COOLDOWN_MS - elapsed);
        const remainingSeconds = Math.ceil(remainingMs / 1000);
        if (remainingSeconds > 0) {
          setAuthInitMessage(
            `Automatic sign-in redirect failed. Auto retry is paused for ~${remainingSeconds}s. Click Sign in with Keycloak to retry now.`
          );
        } else {
          setAuthInitMessage('Automatic sign-in redirect failed. Click Sign in with Keycloak to retry.');
        }
      }
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
    sessionStorage.removeItem(AUTH_REDIRECT_FAILURE_COUNT_KEY);
    sessionStorage.removeItem(AUTH_REDIRECT_LAST_FAILURE_AT_KEY);
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
