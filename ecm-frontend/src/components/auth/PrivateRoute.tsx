import React, { useEffect } from 'react';
import { Box, CircularProgress, Typography } from '@mui/material';
import { Navigate, useLocation } from 'react-router-dom';
import { useAppSelector } from 'store';
import authService from 'services/authService';
import {
  AUTH_REDIRECT_FAILURE_COOLDOWN_MS,
  AUTH_REDIRECT_FAILURE_COUNT_KEY,
  AUTH_REDIRECT_FAILURE_WINDOW_MS,
  AUTH_REDIRECT_LAST_FAILURE_AT_KEY,
  AUTH_REDIRECT_MAX_AUTO_ATTEMPTS,
  AUTH_INIT_STATUS_KEY,
  AUTH_INIT_STATUS_REDIRECT_FAILED,
  LOGIN_IN_PROGRESS_KEY,
  LOGIN_IN_PROGRESS_STARTED_AT_KEY,
  LOGIN_IN_PROGRESS_TIMEOUT_MS,
} from 'constants/auth';
import { logAuthRecoveryEvent } from 'utils/authRecoveryDebug';

interface PrivateRouteProps {
  children: React.ReactNode;
  requiredRoles?: string[];
}

const KEYCLOAK_CALLBACK_KEYS = ['code', 'state', 'session_state', 'iss'] as const;

const hasKeycloakCallbackParams = () => {
  const searchParams = new URLSearchParams(window.location.search);
  if (KEYCLOAK_CALLBACK_KEYS.some((key) => searchParams.has(key))) return true;

  const rawHash = window.location.hash.startsWith('#') ? window.location.hash.slice(1) : '';
  if (!rawHash) return false;
  if (!KEYCLOAK_CALLBACK_KEYS.some((key) => rawHash.includes(`${key}=`))) return false;

  const hashParams = new URLSearchParams(rawHash);
  return KEYCLOAK_CALLBACK_KEYS.some((key) => hashParams.has(key));
};

const buildSafeRedirectUri = (pathname: string, search: string, hash: string) => {
  const url = new URL(`${window.location.origin}${pathname}${search}${hash || ''}`);

  KEYCLOAK_CALLBACK_KEYS.forEach((key) => url.searchParams.delete(key));

  const rawHash = url.hash.startsWith('#') ? url.hash.slice(1) : '';
  if (rawHash && KEYCLOAK_CALLBACK_KEYS.some((key) => rawHash.includes(`${key}=`))) {
    const hashParams = new URLSearchParams(rawHash);
    KEYCLOAK_CALLBACK_KEYS.forEach((key) => hashParams.delete(key));
    const cleaned = hashParams.toString();
    url.hash = cleaned ? `#${cleaned}` : '';
  }

  return url.toString();
};

const PrivateRoute: React.FC<PrivateRouteProps> = ({ children, requiredRoles }) => {
  const location = useLocation();
  const { isAuthenticated, user } = useAppSelector((state) => state.auth);
  const keycloakAuthenticated = authService.isAuthenticated();
  const loginInProgress = sessionStorage.getItem(LOGIN_IN_PROGRESS_KEY) === '1';
  const loginStartedAt = Number(sessionStorage.getItem(LOGIN_IN_PROGRESS_STARTED_AT_KEY) || '0');
  const redirectFailureCount = Number(sessionStorage.getItem(AUTH_REDIRECT_FAILURE_COUNT_KEY) || '0');
  const redirectLastFailureAt = Number(sessionStorage.getItem(AUTH_REDIRECT_LAST_FAILURE_AT_KEY) || '0');
  const redirectFailureWindowExpired =
    redirectLastFailureAt > 0 && Date.now() - redirectLastFailureAt > AUTH_REDIRECT_FAILURE_WINDOW_MS;
  const effectiveRedirectFailureCount = redirectFailureWindowExpired ? 0 : redirectFailureCount;
  const effectiveRedirectLastFailureAt = redirectFailureWindowExpired ? 0 : redirectLastFailureAt;
  const hasReachedAutoRedirectLimit = effectiveRedirectFailureCount >= AUTH_REDIRECT_MAX_AUTO_ATTEMPTS;
  const hasRecentRedirectFailure =
    effectiveRedirectFailureCount > 0
    && effectiveRedirectLastFailureAt > 0
    && Date.now() - effectiveRedirectLastFailureAt < AUTH_REDIRECT_FAILURE_COOLDOWN_MS;
  const shouldPauseAutoRedirect = hasReachedAutoRedirectLimit || hasRecentRedirectFailure;
  const loginStale = loginInProgress && loginStartedAt > 0 && Date.now() - loginStartedAt > LOGIN_IN_PROGRESS_TIMEOUT_MS;
  const effectiveLoginInProgress = loginInProgress && !loginStale;
  const hasCallbackParams = hasKeycloakCallbackParams();

  useEffect(() => {
    if (loginStale) {
      logAuthRecoveryEvent('private_route.login_stale', {
        pathname: location.pathname,
      });
      sessionStorage.removeItem(LOGIN_IN_PROGRESS_KEY);
      sessionStorage.removeItem(LOGIN_IN_PROGRESS_STARTED_AT_KEY);
      return;
    }
    if (redirectFailureWindowExpired) {
      logAuthRecoveryEvent('private_route.redirect_failure_window_reset', {
        pathname: location.pathname,
      });
      sessionStorage.removeItem(AUTH_REDIRECT_FAILURE_COUNT_KEY);
      sessionStorage.removeItem(AUTH_REDIRECT_LAST_FAILURE_AT_KEY);
    }
    if (isAuthenticated || keycloakAuthenticated) {
      sessionStorage.removeItem(LOGIN_IN_PROGRESS_KEY);
      sessionStorage.removeItem(LOGIN_IN_PROGRESS_STARTED_AT_KEY);
      sessionStorage.removeItem(AUTH_REDIRECT_FAILURE_COUNT_KEY);
      sessionStorage.removeItem(AUTH_REDIRECT_LAST_FAILURE_AT_KEY);
      return;
    }
    if (hasCallbackParams) return;
    if (effectiveLoginInProgress) return;
    if (shouldPauseAutoRedirect) {
      logAuthRecoveryEvent('private_route.redirect_paused', {
        pathname: location.pathname,
        effectiveRedirectFailureCount,
        hasRecentRedirectFailure,
        hasReachedAutoRedirectLimit,
      });
      sessionStorage.setItem(AUTH_INIT_STATUS_KEY, AUTH_INIT_STATUS_REDIRECT_FAILED);
      return;
    }

    logAuthRecoveryEvent('private_route.redirect_start', {
      pathname: location.pathname,
      redirectUri: buildSafeRedirectUri(location.pathname, location.search, location.hash),
    });
    sessionStorage.setItem(LOGIN_IN_PROGRESS_KEY, '1');
    sessionStorage.setItem(LOGIN_IN_PROGRESS_STARTED_AT_KEY, String(Date.now()));
    const redirectUri = buildSafeRedirectUri(location.pathname, location.search, location.hash);
    const loginRequest = authService.login({ redirectUri });
    void Promise.resolve(loginRequest).catch((error) => {
      logAuthRecoveryEvent('private_route.redirect_failed', {
        pathname: location.pathname,
        error: error instanceof Error ? error.message : String(error),
      });
      console.error('Automatic login redirect failed', error);
      sessionStorage.setItem(AUTH_INIT_STATUS_KEY, AUTH_INIT_STATUS_REDIRECT_FAILED);
      sessionStorage.setItem(AUTH_REDIRECT_LAST_FAILURE_AT_KEY, String(Date.now()));
      sessionStorage.setItem(AUTH_REDIRECT_FAILURE_COUNT_KEY, String(effectiveRedirectFailureCount + 1));
      sessionStorage.removeItem(LOGIN_IN_PROGRESS_KEY);
      sessionStorage.removeItem(LOGIN_IN_PROGRESS_STARTED_AT_KEY);
    });
  }, [
    effectiveRedirectFailureCount,
    isAuthenticated,
    keycloakAuthenticated,
    location.hash,
    location.pathname,
    location.search,
    hasCallbackParams,
    effectiveLoginInProgress,
    redirectFailureWindowExpired,
    shouldPauseAutoRedirect,
    loginStale,
    hasReachedAutoRedirectLimit,
    hasRecentRedirectFailure,
  ]);

  if (!isAuthenticated && !keycloakAuthenticated) {
    if (hasCallbackParams || effectiveLoginInProgress) {
      return (
        <Box
          sx={{
            minHeight: '100vh',
            display: 'flex',
            flexDirection: 'column',
            alignItems: 'center',
            justifyContent: 'center',
            gap: 2,
            bgcolor: 'background.default',
          }}
        >
          <CircularProgress />
          <Typography variant="body2" color="text.secondary">
            Signing you in...
          </Typography>
        </Box>
      );
    }
    return <Navigate to="/login" state={{ from: location }} replace />;
  }

  if (requiredRoles) {
    const effectiveUser = user ?? authService.getCurrentUser();
    const roles = effectiveUser?.roles ?? [];
    const hasRequiredRole = requiredRoles.some((role) => roles.includes(role));
    if (!hasRequiredRole) {
      return <Navigate to="/unauthorized" replace />;
    }
  }

  return <>{children}</>;
};

export default PrivateRoute;
