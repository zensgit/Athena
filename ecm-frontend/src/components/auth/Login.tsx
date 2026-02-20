import React, { useEffect, useState } from 'react';
import { Alert, Box, Card, CardContent, Button, Typography } from '@mui/material';
import authService from 'services/authService';
import {
  AUTH_REDIRECT_FAILURE_COOLDOWN_MS,
  AUTH_REDIRECT_FAILURE_COUNT_KEY,
  AUTH_REDIRECT_FAILURE_WINDOW_MS,
  AUTH_REDIRECT_LAST_FAILURE_AT_KEY,
  AUTH_REDIRECT_MAX_AUTO_ATTEMPTS,
  AUTH_REDIRECT_REASON_KEY,
  AUTH_INIT_STATUS_ERROR,
  AUTH_INIT_STATUS_KEY,
  AUTH_INIT_STATUS_SESSION_EXPIRED,
  AUTH_INIT_STATUS_REDIRECT_FAILED,
  AUTH_INIT_STATUS_TIMEOUT,
  LOGIN_IN_PROGRESS_KEY,
  LOGIN_IN_PROGRESS_STARTED_AT_KEY,
} from 'constants/auth';

const buildRedirectFailureMessage = (failureCount: number, lastFailureAt: number): string | null => {
  if (failureCount <= 0) {
    return null;
  }
  if (lastFailureAt <= 0) {
    if (failureCount >= AUTH_REDIRECT_MAX_AUTO_ATTEMPTS) {
      return `Automatic sign-in is paused after repeated failures (${failureCount}/${AUTH_REDIRECT_MAX_AUTO_ATTEMPTS}). Click Sign in with Keycloak to retry.`;
    }
    return 'Automatic sign-in redirect failed. Click Sign in with Keycloak to retry.';
  }
  const now = Date.now();
  const elapsed = now - lastFailureAt;
  if (elapsed > AUTH_REDIRECT_FAILURE_WINDOW_MS) {
    return null;
  }
  if (failureCount >= AUTH_REDIRECT_MAX_AUTO_ATTEMPTS) {
    const remainingMs = Math.max(0, AUTH_REDIRECT_FAILURE_WINDOW_MS - elapsed);
    const remainingSeconds = Math.ceil(remainingMs / 1000);
    if (remainingSeconds > 0) {
      return `Automatic sign-in is paused after repeated failures (${failureCount}/${AUTH_REDIRECT_MAX_AUTO_ATTEMPTS}). Auto retry resumes in ~${remainingSeconds}s. Click Sign in with Keycloak to retry now.`;
    }
    return `Automatic sign-in is paused after repeated failures (${failureCount}/${AUTH_REDIRECT_MAX_AUTO_ATTEMPTS}). Click Sign in with Keycloak to retry.`;
  }
  const remainingMs = Math.max(0, AUTH_REDIRECT_FAILURE_COOLDOWN_MS - elapsed);
  const remainingSeconds = Math.ceil(remainingMs / 1000);
  if (remainingSeconds > 0) {
    return `Automatic sign-in redirect failed. Auto retry is paused for ~${remainingSeconds}s. Click Sign in with Keycloak to retry now.`;
  }
  return 'Automatic sign-in redirect failed. Click Sign in with Keycloak to retry.';
};

const Login: React.FC = () => {
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [authInitMessage, setAuthInitMessage] = useState<string | null>(null);

  useEffect(() => {
    const queryReason = new URLSearchParams(window.location.search).get('reason');
    let initStatus: string | null = null;
    let redirectReason: string | null = null;
    let redirectFailureCount = 0;
    let redirectLastFailureAt = 0;
    try {
      initStatus = sessionStorage.getItem(AUTH_INIT_STATUS_KEY);
      redirectReason = localStorage.getItem(AUTH_REDIRECT_REASON_KEY);
      redirectFailureCount = Number(sessionStorage.getItem(AUTH_REDIRECT_FAILURE_COUNT_KEY) || '0');
      redirectLastFailureAt = Number(sessionStorage.getItem(AUTH_REDIRECT_LAST_FAILURE_AT_KEY) || '0');
    } catch {
      // Storage can be unavailable in restricted contexts; keep URL-based fallback.
    }
    if (initStatus === AUTH_INIT_STATUS_TIMEOUT) {
      setAuthInitMessage('Sign-in initialization timed out. Please retry.');
    } else if (initStatus === AUTH_INIT_STATUS_ERROR) {
      setAuthInitMessage('Sign-in initialization failed. Please retry.');
    } else if (
      initStatus === AUTH_INIT_STATUS_SESSION_EXPIRED
      || (!initStatus && redirectReason === AUTH_INIT_STATUS_SESSION_EXPIRED)
      || queryReason === AUTH_INIT_STATUS_SESSION_EXPIRED
    ) {
      setAuthInitMessage('Your session expired. Please sign in again.');
    } else if (
      initStatus === AUTH_INIT_STATUS_REDIRECT_FAILED
      || (!initStatus && redirectFailureCount > 0 && redirectLastFailureAt > 0)
    ) {
      const normalizedFailureCount = initStatus === AUTH_INIT_STATUS_REDIRECT_FAILED
        ? Math.max(1, redirectFailureCount)
        : redirectFailureCount;
      const redirectFailureMessage = buildRedirectFailureMessage(normalizedFailureCount, redirectLastFailureAt);
      if (redirectFailureMessage) {
        setAuthInitMessage(redirectFailureMessage);
      } else if (redirectFailureCount > 0 || redirectLastFailureAt > 0) {
        try {
          sessionStorage.removeItem(AUTH_REDIRECT_FAILURE_COUNT_KEY);
          sessionStorage.removeItem(AUTH_REDIRECT_LAST_FAILURE_AT_KEY);
        } catch {
          // Best effort cleanup.
        }
      }
    }
    try {
      sessionStorage.removeItem(AUTH_INIT_STATUS_KEY);
      sessionStorage.removeItem(LOGIN_IN_PROGRESS_KEY);
      sessionStorage.removeItem(LOGIN_IN_PROGRESS_STARTED_AT_KEY);
      localStorage.removeItem(AUTH_REDIRECT_REASON_KEY);
    } catch {
      // Best effort cleanup.
    }
    if (queryReason) {
      window.history.replaceState({}, '', `${window.location.pathname}${window.location.hash || ''}`);
    }
  }, []);

  const handleLogin = async () => {
    setSubmitting(true);
    setError(null);
    setAuthInitMessage(null);
    sessionStorage.removeItem(AUTH_INIT_STATUS_KEY);
    sessionStorage.removeItem(AUTH_REDIRECT_FAILURE_COUNT_KEY);
    sessionStorage.removeItem(AUTH_REDIRECT_LAST_FAILURE_AT_KEY);
    localStorage.removeItem(AUTH_REDIRECT_REASON_KEY);
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
