export type SearchErrorCategory =
  | 'transient'
  | 'authorization'
  | 'query'
  | 'server'
  | 'unknown';

export type SearchErrorRecovery = {
  category: SearchErrorCategory;
  message: string;
  canRetry: boolean;
  hint: string;
};

const TRANSIENT_ERROR_PATTERN =
  /network error|failed to fetch|timeout|timed out|temporarily|temporar(y|ily)|connection reset|ecconnreset|econnrefused|etimedout/i;
const AUTH_ERROR_PATTERN = /unauthorized|forbidden|session expired|not authenticated/i;
const QUERY_ERROR_PATTERN =
  /invalid query|invalid request|bad request|malformed|parse exception|parse error|query syntax|too many clauses|validation failed/i;

const toErrorText = (error: unknown): string => {
  if (!error) {
    return '';
  }
  if (typeof error === 'string') {
    return error;
  }
  if (error instanceof Error) {
    return error.message || error.name;
  }
  if (typeof error === 'object') {
    const candidate = error as {
      message?: unknown;
      response?: {
        data?: {
          message?: unknown;
          error?: unknown;
        };
      };
    };
    const message = typeof candidate.message === 'string' ? candidate.message : '';
    const responseMessage =
      typeof candidate.response?.data?.message === 'string' ? candidate.response?.data?.message : '';
    const responseError =
      typeof candidate.response?.data?.error === 'string' ? candidate.response?.data?.error : '';
    return [responseMessage, responseError, message].filter(Boolean).join(' ');
  }
  return String(error);
};

const extractStatusCode = (error: unknown): number | null => {
  if (!error || typeof error !== 'object') {
    return null;
  }
  const candidate = error as {
    status?: unknown;
    response?: { status?: unknown };
  };
  const status = Number(candidate.response?.status ?? candidate.status);
  if (!Number.isFinite(status)) {
    return null;
  }
  return status;
};

export const classifySearchError = (error: unknown): SearchErrorCategory => {
  const status = extractStatusCode(error);
  const text = toErrorText(error);

  if (status === 401 || status === 403 || AUTH_ERROR_PATTERN.test(text)) {
    return 'authorization';
  }
  if (status === 400 || status === 422 || QUERY_ERROR_PATTERN.test(text)) {
    return 'query';
  }
  if (status === 408 || status === 429 || TRANSIENT_ERROR_PATTERN.test(text)) {
    return 'transient';
  }
  if (status !== null && status >= 500) {
    return 'server';
  }
  return 'unknown';
};

export const resolveSearchErrorMessage = (error: unknown, fallback = 'Search failed'): string => {
  const text = toErrorText(error).trim();
  return text || fallback;
};

export const buildSearchErrorRecovery = (error: unknown, fallback = 'Search failed'): SearchErrorRecovery => {
  const category = classifySearchError(error);
  const message = resolveSearchErrorMessage(error, fallback);

  switch (category) {
    case 'authorization':
      return {
        category,
        message,
        canRetry: false,
        hint: 'Session or permission issue detected. Go back to folder or sign in again.',
      };
    case 'query':
      return {
        category,
        message,
        canRetry: false,
        hint: 'Update query or filters, then run search again.',
      };
    case 'transient':
      return {
        category,
        message,
        canRetry: true,
        hint: 'Temporary issue detected. Retry now or return to folder.',
      };
    case 'server':
      return {
        category,
        message,
        canRetry: true,
        hint: 'Server processing failed. Retry now or return to folder.',
      };
    case 'unknown':
    default:
      return {
        category: 'unknown',
        message,
        canRetry: true,
        hint: 'Retry now, or return to folder if the issue persists.',
      };
  }
};
