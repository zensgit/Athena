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
  AUTH_INIT_STATUS_APP_RECOVERY,
  AUTH_INIT_STATUS_ERROR,
  AUTH_INIT_STATUS_KEY,
  AUTH_INIT_STATUS_STARTUP_RECOVERY,
  AUTH_INIT_STATUS_SESSION_EXPIRED,
  AUTH_INIT_STATUS_REDIRECT_FAILED,
  AUTH_INIT_STATUS_TIMEOUT,
  LOGIN_IN_PROGRESS_KEY,
  LOGIN_IN_PROGRESS_STARTED_AT_KEY,
} from 'constants/auth';

type AuthInitNotice = {
  title: string;
  detail: string;
};

const RECOVERY_CACHE_BUST_PARAM = '_ecm_reload';

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

const buildAuthInitNotice = (
  initStatus: string | null,
  redirectReason: string | null,
  queryReason: string | null,
  redirectFailureCount: number,
  redirectLastFailureAt: number
): AuthInitNotice | null => {
  if (initStatus === AUTH_INIT_STATUS_TIMEOUT) {
    return {
      title: 'Sign-in initialization timed out',
      detail: 'Sign-in initialization timed out. Please retry.',
    };
  }
  if (initStatus === AUTH_INIT_STATUS_ERROR) {
    return {
      title: 'Sign-in initialization failed',
      detail: 'Sign-in initialization failed. Please retry.',
    };
  }
  if (
    initStatus === AUTH_INIT_STATUS_SESSION_EXPIRED
    || (!initStatus && redirectReason === AUTH_INIT_STATUS_SESSION_EXPIRED)
    || queryReason === AUTH_INIT_STATUS_SESSION_EXPIRED
  ) {
    return {
      title: 'Session expired',
      detail: 'Your session expired. Please sign in again.',
    };
  }
  if (
    initStatus === AUTH_INIT_STATUS_STARTUP_RECOVERY
    || (!initStatus && redirectReason === AUTH_INIT_STATUS_STARTUP_RECOVERY)
    || queryReason === AUTH_INIT_STATUS_STARTUP_RECOVERY
  ) {
    return {
      title: 'Recovered from startup timeout',
      detail: 'App startup took too long and switched to sign-in recovery. Please sign in again.',
    };
  }
  if (
    initStatus === AUTH_INIT_STATUS_APP_RECOVERY
    || (!initStatus && redirectReason === AUTH_INIT_STATUS_APP_RECOVERY)
    || queryReason === AUTH_INIT_STATUS_APP_RECOVERY
  ) {
    return {
      title: 'Recovered from unexpected app error',
      detail: 'The app encountered an unexpected runtime error and returned to sign-in. Please sign in again.',
    };
  }
  if (
    initStatus === AUTH_INIT_STATUS_REDIRECT_FAILED
    || (!initStatus && redirectFailureCount > 0 && redirectLastFailureAt > 0)
  ) {
    const normalizedFailureCount = initStatus === AUTH_INIT_STATUS_REDIRECT_FAILED
      ? Math.max(1, redirectFailureCount)
      : redirectFailureCount;
    const redirectFailureMessage = buildRedirectFailureMessage(normalizedFailureCount, redirectLastFailureAt);
    if (redirectFailureMessage) {
      return {
        title: 'Automatic sign-in needs retry',
        detail: redirectFailureMessage,
      };
    }
  }
  return null;
};

const safeSessionGetItem = (key: string): string | null => {
  try {
    return sessionStorage.getItem(key);
  } catch {
    return null;
  }
};

const safeSessionRemoveItem = (key: string) => {
  try {
    sessionStorage.removeItem(key);
  } catch {
    // Ignore restricted storage contexts.
  }
};

const safeLocalGetItem = (key: string): string | null => {
  try {
    return localStorage.getItem(key);
  } catch {
    return null;
  }
};

const safeLocalRemoveItem = (key: string) => {
  try {
    localStorage.removeItem(key);
  } catch {
    // Ignore restricted storage contexts.
  }
};

const clearTransientRecoveryQueryParams = () => {
  try {
    const url = new URL(window.location.href);
    const originalSearch = url.search;
    url.searchParams.delete('reason');
    url.searchParams.delete(RECOVERY_CACHE_BUST_PARAM);

    if (url.search !== originalSearch) {
      const nextSearch = url.searchParams.toString();
      const nextUrl = `${url.pathname}${nextSearch ? `?${nextSearch}` : ''}${url.hash || ''}`;
      window.history.replaceState({}, '', nextUrl);
    }
  } catch {
    // Ignore URL parsing failures in constrained environments.
  }
};

const Login: React.FC = () => {
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [authInitNotice, setAuthInitNotice] = useState<AuthInitNotice | null>(null);

  useEffect(() => {
    const queryReason = new URLSearchParams(window.location.search).get('reason');
    let initStatus: string | null = null;
    let redirectReason: string | null = null;
    let redirectFailureCount = 0;
    let redirectLastFailureAt = 0;
    initStatus = safeSessionGetItem(AUTH_INIT_STATUS_KEY);
    redirectReason = safeLocalGetItem(AUTH_REDIRECT_REASON_KEY);
    redirectFailureCount = Number(safeSessionGetItem(AUTH_REDIRECT_FAILURE_COUNT_KEY) || '0');
    redirectLastFailureAt = Number(safeSessionGetItem(AUTH_REDIRECT_LAST_FAILURE_AT_KEY) || '0');
    const notice = buildAuthInitNotice(
      initStatus,
      redirectReason,
      queryReason,
      redirectFailureCount,
      redirectLastFailureAt
    );
    setAuthInitNotice(notice);
    if (!notice && (redirectFailureCount > 0 || redirectLastFailureAt > 0)) {
      safeSessionRemoveItem(AUTH_REDIRECT_FAILURE_COUNT_KEY);
      safeSessionRemoveItem(AUTH_REDIRECT_LAST_FAILURE_AT_KEY);
    }
    safeSessionRemoveItem(AUTH_INIT_STATUS_KEY);
    safeSessionRemoveItem(LOGIN_IN_PROGRESS_KEY);
    safeSessionRemoveItem(LOGIN_IN_PROGRESS_STARTED_AT_KEY);
    safeLocalRemoveItem(AUTH_REDIRECT_REASON_KEY);
    if (queryReason || window.location.search.includes(RECOVERY_CACHE_BUST_PARAM)) {
      clearTransientRecoveryQueryParams();
    }
  }, []);

  const handleLogin = async () => {
    setSubmitting(true);
    setError(null);
    setAuthInitNotice(null);
    safeSessionRemoveItem(AUTH_INIT_STATUS_KEY);
    safeSessionRemoveItem(AUTH_REDIRECT_FAILURE_COUNT_KEY);
    safeSessionRemoveItem(AUTH_REDIRECT_LAST_FAILURE_AT_KEY);
    safeLocalRemoveItem(AUTH_REDIRECT_REASON_KEY);
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
          {authInitNotice && (
            <Alert severity="warning" sx={{ mb: 2, textAlign: 'left' }} data-testid="login-auth-status-card">
              <Typography variant="subtitle2" sx={{ mb: 0.5 }}>
                {authInitNotice.title}
              </Typography>
              {authInitNotice.detail}
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
