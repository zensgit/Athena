import React, { useEffect } from 'react';
import { Box, CircularProgress, Typography } from '@mui/material';
import { Navigate, useLocation } from 'react-router-dom';
import { useAppSelector } from 'store';
import authService from 'services/authService';
import {
  AUTH_REDIRECT_FAILURE_COOLDOWN_MS,
  AUTH_REDIRECT_FAILURE_COUNT_KEY,
  AUTH_REDIRECT_LAST_FAILURE_AT_KEY,
  AUTH_INIT_STATUS_KEY,
  AUTH_INIT_STATUS_REDIRECT_FAILED,
  LOGIN_IN_PROGRESS_KEY,
  LOGIN_IN_PROGRESS_STARTED_AT_KEY,
  LOGIN_IN_PROGRESS_TIMEOUT_MS,
} from 'constants/auth';

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
  const hasRecentRedirectFailure =
    redirectFailureCount > 0
    && redirectLastFailureAt > 0
    && Date.now() - redirectLastFailureAt < AUTH_REDIRECT_FAILURE_COOLDOWN_MS;
  const loginStale = loginInProgress && loginStartedAt > 0 && Date.now() - loginStartedAt > LOGIN_IN_PROGRESS_TIMEOUT_MS;
  const effectiveLoginInProgress = loginInProgress && !loginStale;
  const hasCallbackParams = hasKeycloakCallbackParams();

  useEffect(() => {
    if (loginStale) {
      sessionStorage.removeItem(LOGIN_IN_PROGRESS_KEY);
      sessionStorage.removeItem(LOGIN_IN_PROGRESS_STARTED_AT_KEY);
      return;
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
    if (hasRecentRedirectFailure) {
      sessionStorage.setItem(AUTH_INIT_STATUS_KEY, AUTH_INIT_STATUS_REDIRECT_FAILED);
      return;
    }

    sessionStorage.setItem(LOGIN_IN_PROGRESS_KEY, '1');
    sessionStorage.setItem(LOGIN_IN_PROGRESS_STARTED_AT_KEY, String(Date.now()));
    const redirectUri = buildSafeRedirectUri(location.pathname, location.search, location.hash);
    const loginRequest = authService.login({ redirectUri });
    void Promise.resolve(loginRequest).catch((error) => {
      console.error('Automatic login redirect failed', error);
      sessionStorage.setItem(AUTH_INIT_STATUS_KEY, AUTH_INIT_STATUS_REDIRECT_FAILED);
      sessionStorage.setItem(AUTH_REDIRECT_LAST_FAILURE_AT_KEY, String(Date.now()));
      sessionStorage.setItem(AUTH_REDIRECT_FAILURE_COUNT_KEY, String(redirectFailureCount + 1));
      sessionStorage.removeItem(LOGIN_IN_PROGRESS_KEY);
      sessionStorage.removeItem(LOGIN_IN_PROGRESS_STARTED_AT_KEY);
    });
  }, [
    redirectFailureCount,
    isAuthenticated,
    keycloakAuthenticated,
    location.hash,
    location.pathname,
    location.search,
    hasCallbackParams,
    effectiveLoginInProgress,
    hasRecentRedirectFailure,
    loginStale,
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
