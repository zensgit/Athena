import React from 'react';
import {
  AUTH_INIT_STATUS_APP_RECOVERY,
  AUTH_INIT_STATUS_KEY,
  AUTH_REDIRECT_FAILURE_COUNT_KEY,
  AUTH_REDIRECT_LAST_FAILURE_AT_KEY,
  AUTH_REDIRECT_REASON_KEY,
  LOGIN_IN_PROGRESS_KEY,
  LOGIN_IN_PROGRESS_STARTED_AT_KEY,
} from 'constants/auth';

type AppErrorBoundaryProps = {
  children: React.ReactNode;
};

type AppErrorCategory = 'generic' | 'chunk_load';

type AppErrorBoundaryState = {
  hasError: boolean;
  message: string;
  category: AppErrorCategory;
};

const IGNORED_GLOBAL_ERROR_PATTERNS = [
  /ResizeObserver loop limit exceeded/i,
  /ResizeObserver loop completed with undelivered notifications/i,
];

const CHUNK_LOAD_ERROR_PATTERNS = [
  /Loading chunk [\d]+ failed/i,
  /ChunkLoadError/i,
  /Failed to fetch dynamically imported module/i,
  /Importing a module script failed/i,
  /module script load failure/i,
];

const isAbortLikeReason = (reason: unknown): boolean => {
  if (!reason) {
    return false;
  }
  if (reason instanceof Error) {
    if (reason.name === 'AbortError') {
      return true;
    }
    return /aborted|cancelled|canceled/i.test(reason.message || '');
  }
  if (typeof reason === 'object') {
    const record = reason as Record<string, unknown>;
    if (record.code === 'ERR_CANCELED') {
      return true;
    }
    if (typeof record.name === 'string' && record.name === 'AbortError') {
      return true;
    }
    if (typeof record.message === 'string' && /aborted|cancelled|canceled/i.test(record.message)) {
      return true;
    }
  }
  if (typeof reason === 'string') {
    return /aborted|cancelled|canceled/i.test(reason);
  }
  return false;
};

const shouldIgnoreGlobalRuntimeIssue = (message: string, reason?: unknown): boolean => {
  if (IGNORED_GLOBAL_ERROR_PATTERNS.some((pattern) => pattern.test(message))) {
    return true;
  }
  return isAbortLikeReason(reason);
};

const resolveErrorCategory = (message: string, reason?: unknown): AppErrorCategory => {
  if (CHUNK_LOAD_ERROR_PATTERNS.some((pattern) => pattern.test(message))) {
    return 'chunk_load';
  }
  if (typeof reason === 'object' && reason && 'name' in reason) {
    const name = String((reason as Record<string, unknown>).name || '');
    if (/ChunkLoadError/i.test(name)) {
      return 'chunk_load';
    }
  }
  return 'generic';
};

export const buildCacheBustReloadUrl = (href: string): string => {
  try {
    const url = new URL(href, window.location.origin);
    url.searchParams.set('_ecm_reload', String(Date.now()));
    return url.toString();
  } catch {
    return href;
  }
};

export const buildRecoveryLoginUrl = (href: string, reason: string): string => {
  try {
    const currentUrl = new URL(href, window.location.origin);
    const loginUrl = new URL('/login', currentUrl.origin);
    loginUrl.searchParams.set('reason', reason);
    loginUrl.searchParams.set('_ecm_reload', String(Date.now()));
    return loginUrl.toString();
  } catch {
    return `/login?reason=${encodeURIComponent(reason)}&_ecm_reload=${Date.now()}`;
  }
};

const safeSessionSetItem = (key: string, value: string) => {
  try {
    sessionStorage.setItem(key, value);
  } catch {
    // Ignore restricted storage contexts.
  }
};

const safeSessionRemoveItem = (key: string) => {
  try {
    sessionStorage.removeItem(key);
  } catch {
    // Ignore restricted storage contexts.
  }
};

const safeLocalRemoveItem = (key: string) => {
  try {
    localStorage.removeItem(key);
  } catch {
    // Ignore restricted storage contexts.
  }
};

class AppErrorBoundary extends React.Component<AppErrorBoundaryProps, AppErrorBoundaryState> {
  state: AppErrorBoundaryState = {
    hasError: false,
    message: '',
    category: 'generic',
  };

  componentDidMount() {
    window.addEventListener('error', this.handleWindowError);
    window.addEventListener('unhandledrejection', this.handleUnhandledRejection as EventListener);
  }

  componentWillUnmount() {
    window.removeEventListener('error', this.handleWindowError);
    window.removeEventListener('unhandledrejection', this.handleUnhandledRejection as EventListener);
  }

  static getDerivedStateFromError(error: unknown): AppErrorBoundaryState {
    const message = error instanceof Error ? error.message : 'Unexpected application error';
    return {
      hasError: true,
      message,
      category: resolveErrorCategory(message, error),
    };
  }

  componentDidCatch(error: unknown, errorInfo: React.ErrorInfo) {
    console.error('AppErrorBoundary caught render failure', { error, componentStack: errorInfo.componentStack });
  }

  private handleWindowError = (event: ErrorEvent) => {
    const error = event.error;
    const message = error instanceof Error ? error.message : event.message || 'Unexpected runtime error';
    if (shouldIgnoreGlobalRuntimeIssue(message, error)) {
      console.warn('AppErrorBoundary ignored non-fatal global runtime error', { message });
      return;
    }
    if (!this.state.hasError) {
      this.setState({ hasError: true, message, category: resolveErrorCategory(message, error) });
    }
    console.error('AppErrorBoundary caught global runtime error', { error, message });
  };

  private handleUnhandledRejection = (event: PromiseRejectionEvent | Event) => {
    const reason = (event as PromiseRejectionEvent).reason;
    const message = reason instanceof Error ? reason.message : typeof reason === 'string' ? reason : 'Unhandled promise rejection';
    if (shouldIgnoreGlobalRuntimeIssue(message, reason)) {
      console.warn('AppErrorBoundary ignored non-fatal unhandled rejection', { message });
      return;
    }
    if (!this.state.hasError) {
      this.setState({ hasError: true, message, category: resolveErrorCategory(message, reason) });
    }
    console.error('AppErrorBoundary caught unhandled promise rejection', { reason, message });
  };

  private handleReload = () => {
    if (this.state.category === 'chunk_load') {
      window.location.assign(buildCacheBustReloadUrl(window.location.href));
      return;
    }
    window.location.reload();
  };

  private handleGoToLogin = () => {
    try {
      window.localStorage.removeItem('ecm_e2e_force_render_error');
    } catch {
      // Ignore storage cleanup errors.
    }
    safeSessionSetItem(AUTH_INIT_STATUS_KEY, AUTH_INIT_STATUS_APP_RECOVERY);
    safeSessionRemoveItem(LOGIN_IN_PROGRESS_KEY);
    safeSessionRemoveItem(LOGIN_IN_PROGRESS_STARTED_AT_KEY);
    safeSessionRemoveItem(AUTH_REDIRECT_FAILURE_COUNT_KEY);
    safeSessionRemoveItem(AUTH_REDIRECT_LAST_FAILURE_AT_KEY);
    safeLocalRemoveItem(AUTH_REDIRECT_REASON_KEY);
    window.location.assign(buildRecoveryLoginUrl(window.location.href, AUTH_INIT_STATUS_APP_RECOVERY));
  };

  render() {
    if (!this.state.hasError) {
      return this.props.children;
    }

    return (
      <div
        style={{
          minHeight: '100vh',
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
          backgroundColor: '#f5f5f5',
          padding: '24px',
        }}
      >
        <div
          style={{
            maxWidth: '520px',
            width: '100%',
            backgroundColor: '#fff',
            border: '1px solid #ddd',
            borderRadius: '8px',
            padding: '24px',
            boxShadow: '0 4px 16px rgba(0, 0, 0, 0.08)',
          }}
        >
          <h1 style={{ marginTop: 0, marginBottom: '12px', fontSize: '28px' }}>Athena ECM</h1>
          <p style={{ marginTop: 0, marginBottom: '12px', color: '#333' }}>
            The page encountered an unexpected error. You can refresh and try again.
          </p>
          {this.state.category === 'chunk_load' && (
            <p style={{ marginTop: 0, marginBottom: '12px', color: '#333' }}>
              Application files may be outdated after an update. Reload to fetch the latest assets.
            </p>
          )}
          {process.env.NODE_ENV !== 'production' && (
            <p style={{ marginTop: 0, marginBottom: '16px', color: '#a40000' }}>
              Details: {this.state.message || 'unknown error'}
            </p>
          )}
          <div style={{ display: 'flex', gap: '12px', flexWrap: 'wrap' }}>
            <button type="button" onClick={this.handleReload}>
              Reload
            </button>
            <button type="button" onClick={this.handleGoToLogin}>
              Back to Login
            </button>
          </div>
        </div>
      </div>
    );
  }
}

export default AppErrorBoundary;
