export class AuthInitTimeoutError extends Error {
  constructor(timeoutMs: number) {
    super(`Auth initialization timed out after ${timeoutMs}ms`);
    this.name = 'AuthInitTimeoutError';
  }
}

export type AuthInitTaskFactory<T> = () => Promise<T> | T;

export const withAuthInitTimeout = async <T>(task: Promise<T>, timeoutMs: number): Promise<T> => {
  let timeoutHandle: ReturnType<typeof setTimeout> | null = null;
  const timeoutPromise = new Promise<T>((_, reject) => {
    timeoutHandle = setTimeout(() => {
      reject(new AuthInitTimeoutError(timeoutMs));
    }, timeoutMs);
  });

  try {
    return await Promise.race([task, timeoutPromise]);
  } finally {
    if (timeoutHandle) {
      clearTimeout(timeoutHandle);
    }
  }
};

export const runAuthInitWithRetry = async <T>(
  taskFactory: AuthInitTaskFactory<T>,
  options?: {
    timeoutMs?: number;
    maxAttempts?: number;
    retryDelayMs?: number;
    onRetry?: (attempt: number, error: unknown) => void;
  }
): Promise<T> => {
  const timeoutMs = options?.timeoutMs ?? 15_000;
  const maxAttempts = Math.max(1, options?.maxAttempts ?? 2);
  const retryDelayMs = Math.max(0, options?.retryDelayMs ?? 800);

  let lastError: unknown;
  for (let attempt = 1; attempt <= maxAttempts; attempt += 1) {
    try {
      const task = Promise.resolve(taskFactory());
      return await withAuthInitTimeout(task, timeoutMs);
    } catch (error) {
      lastError = error;
      if (attempt >= maxAttempts) {
        break;
      }
      options?.onRetry?.(attempt, error);
      if (retryDelayMs > 0) {
        await new Promise((resolve) => setTimeout(resolve, retryDelayMs));
      }
    }
  }

  throw lastError;
};
