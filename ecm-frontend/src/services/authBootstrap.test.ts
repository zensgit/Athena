import { AuthInitTimeoutError, runAuthInitWithRetry, withAuthInitTimeout } from './authBootstrap';

test('resolves result when init completes before timeout', async () => {
  const result = await withAuthInitTimeout(Promise.resolve(true), 50);
  expect(result).toBe(true);
});

test('propagates init rejection', async () => {
  const task = Promise.reject(new Error('init failed'));
  await expect(withAuthInitTimeout(task, 50)).rejects.toThrow('init failed');
});

test('rejects with timeout error when init hangs', async () => {
  const hangingTask = new Promise<boolean>(() => {
    // Intentionally unresolved promise.
  });
  await expect(withAuthInitTimeout(hangingTask, 5)).rejects.toBeInstanceOf(AuthInitTimeoutError);
});

test('runAuthInitWithRetry retries once then succeeds', async () => {
  const taskFactory = jest
    .fn<Promise<boolean>, []>()
    .mockRejectedValueOnce(new Error('transient failure'))
    .mockResolvedValueOnce(true);

  const result = await runAuthInitWithRetry(taskFactory, {
    timeoutMs: 50,
    maxAttempts: 2,
    retryDelayMs: 0,
  });

  expect(result).toBe(true);
  expect(taskFactory).toHaveBeenCalledTimes(2);
});

test('runAuthInitWithRetry throws final error after max attempts', async () => {
  const taskFactory = jest.fn<Promise<boolean>, []>().mockRejectedValue(new Error('still failing'));

  await expect(
    runAuthInitWithRetry(taskFactory, {
      timeoutMs: 50,
      maxAttempts: 2,
      retryDelayMs: 0,
    })
  ).rejects.toThrow('still failing');
  expect(taskFactory).toHaveBeenCalledTimes(2);
});

test('runAuthInitWithRetry handles timeout and emits retry callback', async () => {
  const retrySpy = jest.fn();
  const hangingTask = jest
    .fn<Promise<boolean>, []>()
    .mockImplementationOnce(
      () =>
        new Promise<boolean>(() => {
          // intentionally unresolved
        })
    )
    .mockResolvedValueOnce(true);

  const result = await runAuthInitWithRetry(hangingTask, {
    timeoutMs: 5,
    maxAttempts: 2,
    retryDelayMs: 0,
    onRetry: retrySpy,
  });

  expect(result).toBe(true);
  expect(retrySpy).toHaveBeenCalledTimes(1);
  expect(hangingTask).toHaveBeenCalledTimes(2);
});
