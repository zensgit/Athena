import { ApiErrorResponse } from 'types';

const isNonEmptyString = (value: unknown): value is string =>
  typeof value === 'string' && value.trim().length > 0;

export const extractApiErrorResponse = (error: unknown): ApiErrorResponse | null => {
  const responseData = (error as { response?: { data?: unknown } })?.response?.data;
  if (!responseData || typeof responseData !== 'object') {
    return null;
  }

  const candidate = responseData as Partial<ApiErrorResponse>;
  if (!isNonEmptyString(candidate.message)) {
    return null;
  }

  return {
    timestamp: typeof candidate.timestamp === 'string' ? candidate.timestamp : '',
    status: typeof candidate.status === 'number' ? candidate.status : 0,
    error: typeof candidate.error === 'string' ? candidate.error : '',
    message: candidate.message,
    path: typeof candidate.path === 'string' ? candidate.path : '',
    details: Array.isArray(candidate.details)
      ? candidate.details.filter(isNonEmptyString)
      : undefined,
  };
};

export const formatApiErrorMessage = (error: unknown, fallback: string): string => {
  const response = extractApiErrorResponse(error);
  if (response) {
    if (response.details?.length) {
      return `${response.message}\n${response.details.map((detail) => `• ${detail}`).join('\n')}`;
    }
    return response.message;
  }

  const message = (error as { message?: unknown })?.message;
  if (isNonEmptyString(message)) {
    return message;
  }
  return fallback;
};
