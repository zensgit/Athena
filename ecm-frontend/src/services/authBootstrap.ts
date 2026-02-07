export class AuthInitTimeoutError extends Error {
  constructor(timeoutMs: number) {
    super(`Auth initialization timed out after ${timeoutMs}ms`);
    this.name = 'AuthInitTimeoutError';
  }
}

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
