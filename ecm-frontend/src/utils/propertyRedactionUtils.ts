export const ENCRYPTED_PROPERTY_DISPLAY_VALUE = '[encrypted]';

const PROTECTED_PAYLOAD_TEXT_PATTERN = /\benc:[^:\s<>"']+:[^\s<>"']+/g;

export const isProtectedPropertyPayload = (value: unknown): value is string => {
  if (typeof value !== 'string' || !value.startsWith('enc:')) {
    return false;
  }
  const remainder = value.slice('enc:'.length);
  const delimiterIndex = remainder.indexOf(':');
  return delimiterIndex > 0 && delimiterIndex < remainder.length - 1;
};

export const containsProtectedPropertyPayload = (value: unknown): boolean => {
  if (isProtectedPropertyPayload(value)) {
    return true;
  }
  if (Array.isArray(value)) {
    return value.some((item) => containsProtectedPropertyPayload(item));
  }
  if (value && typeof value === 'object') {
    return Object.values(value as Record<string, unknown>).some((nestedValue) =>
      containsProtectedPropertyPayload(nestedValue)
    );
  }
  return false;
};

export const redactProtectedPropertyText = (value: string): string =>
  value.replace(PROTECTED_PAYLOAD_TEXT_PATTERN, ENCRYPTED_PROPERTY_DISPLAY_VALUE);

export const redactProtectedPropertyValue = (value: unknown): unknown => {
  if (isProtectedPropertyPayload(value)) {
    return ENCRYPTED_PROPERTY_DISPLAY_VALUE;
  }
  if (Array.isArray(value)) {
    return value.map((item) => redactProtectedPropertyValue(item));
  }
  if (value && typeof value === 'object') {
    return Object.fromEntries(
      Object.entries(value as Record<string, unknown>).map(([key, nestedValue]) => [
        key,
        redactProtectedPropertyValue(nestedValue),
      ])
    );
  }
  return value;
};

export const formatPropertyDisplayValue = (value: unknown, fallback = ''): string => {
  if (value === null || value === undefined || value === '') {
    return fallback;
  }
  const redacted = redactProtectedPropertyValue(value);
  if (Array.isArray(redacted)) {
    return redacted.map((item) => String(item)).join(', ');
  }
  if (redacted && typeof redacted === 'object') {
    return JSON.stringify(redacted);
  }
  return String(redacted);
};
