import { AuthInitTimeoutError, withAuthInitTimeout } from './authBootstrap';

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
