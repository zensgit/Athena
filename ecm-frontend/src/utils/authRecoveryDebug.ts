const DEBUG_STORAGE_KEY = 'ecm_debug_recovery';
const REDACTED = '[redacted]';
const SENSITIVE_KEYS = new Set([
  'token',
  'accessToken',
  'refreshToken',
  'idToken',
  'authorization',
  'Authorization',
]);

const sanitizeValue = (value: unknown): unknown => {
  if (Array.isArray(value)) {
    return value.map((item) => sanitizeValue(item));
  }
  if (!value || typeof value !== 'object') {
    return value;
  }
  const record = value as Record<string, unknown>;
  const sanitized: Record<string, unknown> = {};
  Object.entries(record).forEach(([key, innerValue]) => {
    if (SENSITIVE_KEYS.has(key)) {
      sanitized[key] = REDACTED;
      return;
    }
    sanitized[key] = sanitizeValue(innerValue);
  });
  return sanitized;
};

export const isAuthRecoveryDebugEnabled = (): boolean => {
  const envEnabled = process.env.REACT_APP_DEBUG_RECOVERY === '1';
  if (typeof window === 'undefined') {
    return envEnabled;
  }

  let storageEnabled = false;
  try {
    storageEnabled = window.localStorage.getItem(DEBUG_STORAGE_KEY) === '1';
  } catch {
    storageEnabled = false;
  }

  let queryEnabled = false;
  try {
    const params = new URLSearchParams(window.location.search);
    queryEnabled = params.get('debugRecovery') === '1'
      || params.get('authRecoveryDebug') === '1';
  } catch {
    queryEnabled = false;
  }

  return envEnabled || storageEnabled || queryEnabled;
};

export const logAuthRecoveryEvent = (event: string, payload?: Record<string, unknown>) => {
  if (!isAuthRecoveryDebugEnabled()) {
    return;
  }
  const safePayload = sanitizeValue(payload);
  if (safePayload && typeof safePayload === 'object' && Object.keys(safePayload as Record<string, unknown>).length > 0) {
    console.info(`[auth-recovery] ${event}`, safePayload);
    return;
  }
  console.info(`[auth-recovery] ${event}`);
};

