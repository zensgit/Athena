export const LOGIN_IN_PROGRESS_KEY = 'ecm_kc_login_in_progress';
export const LOGIN_IN_PROGRESS_STARTED_AT_KEY = 'ecm_kc_login_in_progress_started_at';
export const LOGIN_IN_PROGRESS_TIMEOUT_MS = 45_000;
export const AUTH_INIT_STATUS_KEY = 'ecm_auth_init_status';
export const AUTH_INIT_STATUS_TIMEOUT = 'timeout';
export const AUTH_INIT_STATUS_ERROR = 'error';
export const AUTH_INIT_STATUS_REDIRECT_FAILED = 'redirect_failed';
export const AUTH_INIT_STATUS_SESSION_EXPIRED = 'session_expired';
export const AUTH_REDIRECT_REASON_KEY = 'ecm_auth_redirect_reason';
export const AUTH_REDIRECT_FAILURE_COUNT_KEY = 'ecm_auth_redirect_failure_count';
export const AUTH_REDIRECT_LAST_FAILURE_AT_KEY = 'ecm_auth_redirect_last_failure_at';

export const resolvePositiveIntEnv = (rawValue: string | undefined, fallback: number): number => {
  if (!rawValue) return fallback;
  const parsed = Number(rawValue);
  if (!Number.isFinite(parsed) || parsed <= 0) return fallback;
  return Math.floor(parsed);
};

export const AUTH_REDIRECT_FAILURE_COOLDOWN_MS = resolvePositiveIntEnv(
  process.env.REACT_APP_AUTH_REDIRECT_FAILURE_COOLDOWN_MS,
  30_000
);
export const AUTH_REDIRECT_MAX_AUTO_ATTEMPTS = resolvePositiveIntEnv(
  process.env.REACT_APP_AUTH_REDIRECT_MAX_AUTO_ATTEMPTS,
  2
);
export const AUTH_REDIRECT_FAILURE_WINDOW_MS = resolvePositiveIntEnv(
  process.env.REACT_APP_AUTH_REDIRECT_FAILURE_WINDOW_MS,
  300_000
);
