import React from 'react';
import ReactDOM from 'react-dom/client';
import './index.css';
import App from './App';
import authService from 'services/authService';
import { AuthInitTimeoutError, runAuthInitWithRetry } from 'services/authBootstrap';
import AppErrorBoundary from 'components/layout/AppErrorBoundary';
import AuthBootingScreen from 'components/auth/AuthBootingScreen';
import { store } from './store';
import { setSession } from 'store/slices/authSlice';
import {
  AUTH_INIT_STATUS_ERROR,
  AUTH_INIT_STATUS_KEY,
  AUTH_INIT_STATUS_SESSION_EXPIRED,
  AUTH_INIT_STATUS_TIMEOUT,
  LOGIN_IN_PROGRESS_KEY,
  LOGIN_IN_PROGRESS_STARTED_AT_KEY,
  resolvePositiveIntEnv,
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
const AUTH_INIT_TIMEOUT_MS = resolvePositiveIntEnv(process.env.REACT_APP_AUTH_INIT_TIMEOUT_MS, 15_000);
const AUTH_INIT_MAX_ATTEMPTS = resolvePositiveIntEnv(process.env.REACT_APP_AUTH_INIT_MAX_ATTEMPTS, 2);
const AUTH_INIT_RETRY_DELAY_MS = resolvePositiveIntEnv(process.env.REACT_APP_AUTH_INIT_RETRY_DELAY_MS, 800);
const AUTH_BOOT_WATCHDOG_MS = resolvePositiveIntEnv(process.env.REACT_APP_AUTH_BOOT_WATCHDOG_MS, 12_000);
const E2E_FORCE_AUTH_BOOT_HANG_KEY = 'ecm_e2e_force_auth_boot_hang';
const E2E_AUTH_BOOT_WATCHDOG_MS_KEY = 'ecm_e2e_auth_boot_watchdog_ms';

let authBootWatchdogRecovered = false;
let authBootWatchdogTriggeredLogged = false;

const safeSessionGetItem = (key: string): string | null => {
  try {
    return sessionStorage.getItem(key);
  } catch {
    return null;
  }
};

const safeSessionSetItem = (key: string, value: string) => {
  try {
    sessionStorage.setItem(key, value);
  } catch {
    // Storage can be unavailable in restricted browser contexts.
  }
};

const safeSessionRemoveItem = (key: string) => {
  try {
    sessionStorage.removeItem(key);
  } catch {
    // Storage can be unavailable in restricted browser contexts.
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
    // Storage can be unavailable in restricted browser contexts.
  }
};

const isWebdriverRuntime = (): boolean => window.navigator?.webdriver === true;
const isLocalhostRuntime = (): boolean => window.location.hostname === 'localhost' || window.location.hostname === '127.0.0.1';
const canUseE2EBootOverrides = (): boolean => isWebdriverRuntime() || isLocalhostRuntime();

const resolveAuthBootWatchdogMs = (): number => {
  const fallback = AUTH_BOOT_WATCHDOG_MS;
  if (!canUseE2EBootOverrides()) {
    return fallback;
  }
  const overrideRaw = safeLocalGetItem(E2E_AUTH_BOOT_WATCHDOG_MS_KEY) || undefined;
  return resolvePositiveIntEnv(overrideRaw, fallback);
};

const shouldForceE2EAuthBootHang = (): boolean => {
  if (!canUseE2EBootOverrides()) {
    return false;
  }
  return safeLocalGetItem(E2E_FORCE_AUTH_BOOT_HANG_KEY) === '1';
};

const clearE2EAuthBootFlags = () => {
  if (!canUseE2EBootOverrides()) {
    return;
  }
  safeLocalRemoveItem(E2E_FORCE_AUTH_BOOT_HANG_KEY);
  safeLocalRemoveItem(E2E_AUTH_BOOT_WATCHDOG_MS_KEY);
};

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

const clearLoginProgress = () => {
  safeSessionRemoveItem(LOGIN_IN_PROGRESS_KEY);
  safeSessionRemoveItem(LOGIN_IN_PROGRESS_STARTED_AT_KEY);
};

const clearAuthInitStatus = (options?: { preserveSessionExpired?: boolean }) => {
  const preserveSessionExpired = options?.preserveSessionExpired ?? false;
  const currentStatus = safeSessionGetItem(AUTH_INIT_STATUS_KEY);
  if (preserveSessionExpired && currentStatus === AUTH_INIT_STATUS_SESSION_EXPIRED) {
    return;
  }
  safeSessionRemoveItem(AUTH_INIT_STATUS_KEY);
};

const setAuthInitStatus = (status: string) => {
  safeSessionSetItem(AUTH_INIT_STATUS_KEY, status);
};

const recoverStartupToLogin = () => {
  if (authBootWatchdogRecovered) {
    return;
  }
  authBootWatchdogRecovered = true;
  clearLoginProgress();
  setAuthInitStatus(AUTH_INIT_STATUS_TIMEOUT);
  try {
    store.dispatch(
      setSession({
        user: null,
        token: null,
        isAuthenticated: false,
      })
    );
  } catch {
    // Best effort only.
  }
  logAuthRecoveryEvent('auth.bootstrap.watchdog.continue_to_login', {
    pathname: window.location.pathname,
  });
  clearE2EAuthBootFlags();
  try {
    window.history.replaceState({}, '', '/login');
  } catch {
    window.location.assign('/login');
    return;
  }
  renderApp();
};

const renderAuthBooting = () => {
  const watchdogMs = resolveAuthBootWatchdogMs();
  root.render(
    <React.StrictMode>
      <AuthBootingScreen
        watchdogMs={watchdogMs}
        onWatchdogTriggered={() => {
          if (!authBootWatchdogTriggeredLogged) {
            authBootWatchdogTriggeredLogged = true;
            logAuthRecoveryEvent('auth.bootstrap.watchdog.triggered', {
              watchdogMs,
              pathname: window.location.pathname,
            });
          }
        }}
        onReload={() => {
          logAuthRecoveryEvent('auth.bootstrap.watchdog.reload', {
            pathname: window.location.pathname,
          });
          window.location.reload();
        }}
        onContinueToLogin={recoverStartupToLogin}
      />
    </React.StrictMode>
  );
};

const initAuth = async () => {
  authBootWatchdogRecovered = false;
  authBootWatchdogTriggeredLogged = false;
  logAuthRecoveryEvent('auth.bootstrap.start', {
    pathname: window.location.pathname,
    search: window.location.search,
  });
  renderAuthBooting();
  try {
    if (shouldForceE2EAuthBootHang()) {
      logAuthRecoveryEvent('auth.bootstrap.e2e_forced_hang', {
        pathname: window.location.pathname,
      });
      await new Promise<never>(() => {
        // E2E-only startup hang injection; recovered via watchdog controls.
      });
    }
    clearAuthInitStatus({ preserveSessionExpired: true });
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
    if (authBootWatchdogRecovered) {
      logAuthRecoveryEvent('auth.bootstrap.skipped_after_watchdog_recovery', {
        stage: 'success',
      });
      return;
    }
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
    if (authBootWatchdogRecovered) {
      logAuthRecoveryEvent('auth.bootstrap.skipped_after_watchdog_recovery', {
        stage: 'error',
        error: error instanceof Error ? error.message : String(error),
      });
      return;
    }
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
void initAuth().catch((error) => {
  if (authBootWatchdogRecovered) {
    logAuthRecoveryEvent('auth.bootstrap.skipped_after_watchdog_recovery', {
      stage: 'fatal',
      error: error instanceof Error ? error.message : String(error),
    });
    return;
  }
  clearLoginProgress();
  setAuthInitStatus(AUTH_INIT_STATUS_ERROR);
  console.error('Keycloak init fatal error:', error);
  logAuthRecoveryEvent('auth.bootstrap.fatal', {
    error: error instanceof Error ? error.message : String(error),
  });
  try {
    store.dispatch(
      setSession({
        user: null,
        token: null,
        isAuthenticated: false,
      })
    );
  } catch {
    // Best effort only.
  }
  renderApp();
});
