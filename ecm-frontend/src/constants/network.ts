export const resolvePositiveIntEnv = (rawValue: string | undefined, fallback: number): number => {
  if (!rawValue) return fallback;
  const parsed = Number(rawValue);
  if (!Number.isFinite(parsed) || parsed <= 0) return fallback;
  return Math.floor(parsed);
};

export const API_TIMEOUT_READ_MS = resolvePositiveIntEnv(
  process.env.REACT_APP_API_TIMEOUT_READ_MS,
  15_000
);

export const API_TIMEOUT_WRITE_MS = resolvePositiveIntEnv(
  process.env.REACT_APP_API_TIMEOUT_WRITE_MS,
  20_000
);

export const API_TIMEOUT_UPLOAD_MS = resolvePositiveIntEnv(
  process.env.REACT_APP_API_TIMEOUT_UPLOAD_MS,
  120_000
);

export const API_TIMEOUT_DOWNLOAD_MS = resolvePositiveIntEnv(
  process.env.REACT_APP_API_TIMEOUT_DOWNLOAD_MS,
  120_000
);
