import React from 'react';
import ReactDOM from 'react-dom/client';
import './index.css';
import App from './App';
import authService from 'services/authService';
import { AuthInitTimeoutError, runAuthInitWithRetry } from 'services/authBootstrap';
import AppErrorBoundary from 'components/layout/AppErrorBoundary';
import { store } from './store';
import { setSession } from 'store/slices/authSlice';
import {
  AUTH_INIT_STATUS_ERROR,
  AUTH_INIT_STATUS_KEY,
  AUTH_INIT_STATUS_SESSION_EXPIRED,
  AUTH_INIT_STATUS_TIMEOUT,
  LOGIN_IN_PROGRESS_KEY,
  LOGIN_IN_PROGRESS_STARTED_AT_KEY,
} from 'constants/auth';
import { logAuthRecoveryEvent } from 'utils/authRecoveryDebug';

const allowInsecureCrypto = process.env.REACT_APP_INSECURE_CRYPTO_OK === 'true';
const installInsecureCryptoFallback = () => {
  if (!allowInsecureCrypto) {
    return;
  }
  const cryptoRef = (window as any).crypto as { getRandomValues?: (arr: Uint8Array) => Uint8Array } | undefined;
  if (typeof cryptoRef?.getRandomValues === 'function') {
    return;
  }

  const insecureGetRandomValues = (arr: Uint8Array) => {
    for (let i = 0; i < arr.length; i += 1) {
      arr[i] = Math.floor(Math.random() * 256);
    }
    return arr;
  };

  try {
    if (cryptoRef) {
      Object.defineProperty(cryptoRef, 'getRandomValues', {
        value: insecureGetRandomValues,
        configurable: true,
      });
    } else {
      Object.defineProperty(window, 'crypto', {
        value: { getRandomValues: insecureGetRandomValues },
        configurable: true,
      });
    }
    console.warn('Using insecure crypto fallback for Keycloak dev login.');
  } catch (error) {
    console.warn('Failed to install insecure crypto fallback.', error);
  }
};

installInsecureCryptoFallback();

const root = ReactDOM.createRoot(document.getElementById('root') as HTMLElement);
const KEYCLOAK_CALLBACK_KEYS = ['code', 'state', 'session_state', 'iss'] as const;
const AUTH_INIT_TIMEOUT_MS = Number(process.env.REACT_APP_AUTH_INIT_TIMEOUT_MS || '15000');
const AUTH_INIT_MAX_ATTEMPTS = Number(process.env.REACT_APP_AUTH_INIT_MAX_ATTEMPTS || '2');
const AUTH_INIT_RETRY_DELAY_MS = Number(process.env.REACT_APP_AUTH_INIT_RETRY_DELAY_MS || '800');

const stripKeycloakCallbackParams = () => {
  const url = new URL(window.location.href);
  KEYCLOAK_CALLBACK_KEYS.forEach((key) => url.searchParams.delete(key));

  const rawHash = url.hash.startsWith('#') ? url.hash.slice(1) : '';
  if (rawHash && KEYCLOAK_CALLBACK_KEYS.some((key) => rawHash.includes(`${key}=`))) {
    const hashParams = new URLSearchParams(rawHash);
    KEYCLOAK_CALLBACK_KEYS.forEach((key) => hashParams.delete(key));
    const cleaned = hashParams.toString();
    url.hash = cleaned ? `#${cleaned}` : '';
  }

  const cleanedUrl = url.toString();
  if (cleanedUrl !== window.location.href) {
    window.history.replaceState({}, '', cleanedUrl);
  }
};

const renderApp = () => {
  root.render(
    <React.StrictMode>
      <AppErrorBoundary>
        <App />
      </AppErrorBoundary>
    </React.StrictMode>
  );
};

const renderAuthBooting = () => {
  root.render(
    <React.StrictMode>
      <div
        style={{
          minHeight: '100vh',
          display: 'flex',
          flexDirection: 'column',
          alignItems: 'center',
          justifyContent: 'center',
          gap: '12px',
          backgroundColor: '#f5f5f5',
          color: '#333',
          fontFamily: '"Roboto", "Helvetica", "Arial", sans-serif',
        }}
      >
        <div
          style={{
            width: '34px',
            height: '34px',
            borderRadius: '50%',
            border: '4px solid #d9d9d9',
            borderTopColor: '#1976d2',
            animation: 'ecm-auth-spin 1s linear infinite',
          }}
        />
        <div>Initializing sign-in...</div>
        <style>
          {`
            @keyframes ecm-auth-spin {
              from { transform: rotate(0deg); }
              to { transform: rotate(360deg); }
            }
          `}
        </style>
      </div>
    </React.StrictMode>
  );
};

const clearLoginProgress = () => {
  sessionStorage.removeItem(LOGIN_IN_PROGRESS_KEY);
  sessionStorage.removeItem(LOGIN_IN_PROGRESS_STARTED_AT_KEY);
};

const clearAuthInitStatus = (options?: { preserveSessionExpired?: boolean }) => {
  const preserveSessionExpired = options?.preserveSessionExpired ?? false;
  const currentStatus = sessionStorage.getItem(AUTH_INIT_STATUS_KEY);
  if (preserveSessionExpired && currentStatus === AUTH_INIT_STATUS_SESSION_EXPIRED) {
    return;
  }
  sessionStorage.removeItem(AUTH_INIT_STATUS_KEY);
};

const setAuthInitStatus = (status: string) => {
  sessionStorage.setItem(AUTH_INIT_STATUS_KEY, status);
};

const initAuth = async () => {
  logAuthRecoveryEvent('auth.bootstrap.start', {
    pathname: window.location.pathname,
    search: window.location.search,
  });
  renderAuthBooting();
  clearAuthInitStatus({ preserveSessionExpired: true });
  try {
    const canUsePkce = !!(window.crypto && window.crypto.subtle);
    if (!canUsePkce) {
      console.warn('PKCE disabled: Web Crypto API is unavailable in this context.');
    }
    const authenticated = await runAuthInitWithRetry(
      () =>
        authService.init({
          onLoad: 'check-sso',
          pkceMethod: canUsePkce ? 'S256' : undefined,
          checkLoginIframe: false, // Disable iframe check to prevent CORS issues
        }),
      {
        timeoutMs: AUTH_INIT_TIMEOUT_MS,
        maxAttempts: AUTH_INIT_MAX_ATTEMPTS,
        retryDelayMs: AUTH_INIT_RETRY_DELAY_MS,
        onRetry: (attempt, error) => {
          const retryAt = attempt + 1;
          logAuthRecoveryEvent('auth.bootstrap.retry', {
            attempt,
            retryAt,
            error: error instanceof Error ? error.message : String(error),
          });
          console.warn(`Keycloak init attempt ${attempt} failed, retrying attempt ${retryAt}.`, error);
        },
      }
    );
    clearLoginProgress();
    store.dispatch(
      setSession({
        user: authenticated ? authService.getCurrentUser() : null,
        token: authenticated ? authService.getToken() || null : null,
        isAuthenticated: authenticated,
      })
    );
    stripKeycloakCallbackParams();
    renderApp();
    if (authenticated) {
      authService.startTokenRefresh();
    }
    logAuthRecoveryEvent('auth.bootstrap.success', {
      authenticated,
    });
  } catch (error) {
    clearLoginProgress();
    store.dispatch(
      setSession({
        user: null,
        token: null,
        isAuthenticated: false,
      })
    );
    if (error instanceof AuthInitTimeoutError) {
      setAuthInitStatus(AUTH_INIT_STATUS_TIMEOUT);
      console.error('Keycloak init timeout:', error.message);
      logAuthRecoveryEvent('auth.bootstrap.failed', {
        status: AUTH_INIT_STATUS_TIMEOUT,
        error: error.message,
      });
    } else {
      setAuthInitStatus(AUTH_INIT_STATUS_ERROR);
      console.error('Keycloak init error:', error);
      logAuthRecoveryEvent('auth.bootstrap.failed', {
        status: AUTH_INIT_STATUS_ERROR,
        error: error instanceof Error ? error.message : String(error),
      });
    }
    renderApp();
  }
};

// Initialize Keycloak before rendering
void initAuth();
