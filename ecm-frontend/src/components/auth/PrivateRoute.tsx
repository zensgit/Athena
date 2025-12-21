import React, { useEffect } from 'react';
import { Navigate, useLocation } from 'react-router-dom';
import { useAppSelector } from 'store';
import authService from 'services/authService';

interface PrivateRouteProps {
  children: React.ReactNode;
  requiredRoles?: string[];
}

const LOGIN_IN_PROGRESS_KEY = 'ecm_kc_login_in_progress';
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

  useEffect(() => {
    if (isAuthenticated || keycloakAuthenticated) return;
    if (hasKeycloakCallbackParams()) return;
    if (sessionStorage.getItem(LOGIN_IN_PROGRESS_KEY) === '1') return;

    sessionStorage.setItem(LOGIN_IN_PROGRESS_KEY, '1');
    const redirectUri = buildSafeRedirectUri(location.pathname, location.search, location.hash);
    authService.login({ redirectUri });
  }, [isAuthenticated, keycloakAuthenticated, location.hash, location.pathname, location.search]);

  if (!isAuthenticated && !keycloakAuthenticated) {
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
