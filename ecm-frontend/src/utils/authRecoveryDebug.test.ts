import {
  AUTH_RECOVERY_DEBUG_STORAGE_KEY,
  isAuthRecoveryDebugEnabled,
  isAuthRecoveryDebugLocalEnabled,
  logAuthRecoveryEvent,
  setAuthRecoveryDebugLocalEnabled,
} from './authRecoveryDebug';

describe('authRecoveryDebug', () => {
  const originalEnv = process.env.REACT_APP_DEBUG_RECOVERY;

  beforeEach(() => {
    delete process.env.REACT_APP_DEBUG_RECOVERY;
    window.localStorage.clear();
    window.history.pushState({}, '', '/');
    jest.restoreAllMocks();
  });

  afterAll(() => {
    process.env.REACT_APP_DEBUG_RECOVERY = originalEnv;
  });

  test('is disabled by default', () => {
    expect(isAuthRecoveryDebugEnabled()).toBe(false);
  });

  test('enables debug by env flag', () => {
    process.env.REACT_APP_DEBUG_RECOVERY = '1';
    expect(isAuthRecoveryDebugEnabled()).toBe(true);
  });

  test('enables debug by localStorage flag', () => {
    window.localStorage.setItem(AUTH_RECOVERY_DEBUG_STORAGE_KEY, '1');
    expect(isAuthRecoveryDebugEnabled()).toBe(true);
  });

  test('local debug helper persists and clears override', () => {
    expect(isAuthRecoveryDebugLocalEnabled()).toBe(false);

    setAuthRecoveryDebugLocalEnabled(true);
    expect(isAuthRecoveryDebugLocalEnabled()).toBe(true);
    expect(window.localStorage.getItem(AUTH_RECOVERY_DEBUG_STORAGE_KEY)).toBe('1');

    setAuthRecoveryDebugLocalEnabled(false);
    expect(isAuthRecoveryDebugLocalEnabled()).toBe(false);
    expect(window.localStorage.getItem(AUTH_RECOVERY_DEBUG_STORAGE_KEY)).toBeNull();
  });

  test('enables debug by query flag', () => {
    window.history.pushState({}, '', '/login?debugRecovery=1');
    expect(isAuthRecoveryDebugEnabled()).toBe(true);
  });

  test('redacts sensitive payload fields', () => {
    process.env.REACT_APP_DEBUG_RECOVERY = '1';
    const infoSpy = jest.spyOn(console, 'info').mockImplementation(() => undefined);

    logAuthRecoveryEvent('api.401.retry.failed', {
      token: 'abc',
      Authorization: 'Bearer abc',
      nested: {
        refreshToken: 'xyz',
      },
      keep: 'value',
    });

    expect(infoSpy).toHaveBeenCalledWith(
      '[auth-recovery] api.401.retry.failed',
      expect.objectContaining({
        token: '[redacted]',
        Authorization: '[redacted]',
        keep: 'value',
        nested: expect.objectContaining({
          refreshToken: '[redacted]',
        }),
      })
    );
  });
});
