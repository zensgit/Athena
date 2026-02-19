import { shouldLogoutOnRefreshError } from './authService';

type MockKeycloak = {
  authenticated: boolean;
  token?: string;
  tokenParsed?: Record<string, unknown>;
  subject?: string;
  init: jest.Mock;
  login: jest.Mock;
  logout: jest.Mock;
  updateToken: jest.Mock;
};

const createKeycloakMock = (): MockKeycloak => ({
  authenticated: true,
  token: 'mock-token',
  tokenParsed: {},
  subject: 'mock-subject',
  init: jest.fn().mockResolvedValue(true),
  login: jest.fn().mockResolvedValue(undefined),
  logout: jest.fn().mockResolvedValue(undefined),
  updateToken: jest.fn().mockResolvedValue(true),
});

const loadIsolatedAuthService = async (keycloakMock: MockKeycloak) => {
  let loadedModule: any;
  let logAuthRecoveryEventMock: jest.Mock;
  jest.isolateModules(() => {
    jest.doMock('auth/keycloak', () => ({
      __esModule: true,
      default: keycloakMock,
    }));
    logAuthRecoveryEventMock = jest.fn();
    jest.doMock('utils/authRecoveryDebug', () => ({
      __esModule: true,
      logAuthRecoveryEvent: logAuthRecoveryEventMock,
    }));
    loadedModule = require('./authService');
  });
  return {
    ...(loadedModule as {
      default: {
        init: (options: any) => Promise<boolean>;
        refreshToken: () => Promise<string | undefined>;
      };
    }),
    logAuthRecoveryEventMock: logAuthRecoveryEventMock!,
  };
};

type IsolatedAuthServiceModule = {
    default: {
      init: (options: any) => Promise<boolean>;
      refreshToken: () => Promise<string | undefined>;
    };
    logAuthRecoveryEventMock: jest.Mock;
};

describe('shouldLogoutOnRefreshError', () => {
  test('returns false for transient network failures', () => {
    expect(shouldLogoutOnRefreshError(new Error('Network Error'))).toBe(false);
    expect(shouldLogoutOnRefreshError({ message: 'Failed to fetch', status: 0 })).toBe(false);
    expect(shouldLogoutOnRefreshError({ response: { status: 503 } })).toBe(false);
  });

  test('returns true for terminal auth failures', () => {
    expect(shouldLogoutOnRefreshError({ response: { status: 401 } })).toBe(true);
    expect(shouldLogoutOnRefreshError({ error: 'invalid_grant' })).toBe(true);
  });
});

describe('authService.refreshToken', () => {
  beforeEach(() => {
    localStorage.clear();
  });

  afterEach(() => {
    jest.resetModules();
    jest.clearAllMocks();
  });

  test('keeps session on transient refresh failure', async () => {
    const keycloakMock = createKeycloakMock();
    keycloakMock.token = 'still-valid-token';
    keycloakMock.updateToken.mockRejectedValueOnce(new Error('Network Error'));

    const warnSpy = jest.spyOn(console, 'warn').mockImplementation(() => undefined);
    const module = await loadIsolatedAuthService(keycloakMock) as IsolatedAuthServiceModule;
    const authService = module.default;

    await authService.init({ onLoad: 'check-sso', checkLoginIframe: false });
    const token = await authService.refreshToken();

    expect(token).toBe('still-valid-token');
    expect(keycloakMock.logout).not.toHaveBeenCalled();
    expect(module.logAuthRecoveryEventMock).toHaveBeenCalledWith(
      'auth.refresh.failed',
      expect.objectContaining({
        shouldLogout: false,
      })
    );
    warnSpy.mockRestore();
  });

  test('keeps session on transient 503 refresh failure payload', async () => {
    const keycloakMock = createKeycloakMock();
    keycloakMock.token = 'still-valid-token';
    keycloakMock.updateToken.mockRejectedValueOnce({
      response: { status: 503 },
      message: 'Service temporarily unavailable',
    });

    const warnSpy = jest.spyOn(console, 'warn').mockImplementation(() => undefined);
    const module = await loadIsolatedAuthService(keycloakMock) as IsolatedAuthServiceModule;
    const authService = module.default;

    await authService.init({ onLoad: 'check-sso', checkLoginIframe: false });
    const token = await authService.refreshToken();

    expect(token).toBe('still-valid-token');
    expect(keycloakMock.logout).not.toHaveBeenCalled();
    expect(module.logAuthRecoveryEventMock).toHaveBeenCalledWith(
      'auth.refresh.failed',
      expect.objectContaining({
        status: 503,
        shouldLogout: false,
      })
    );
    warnSpy.mockRestore();
  });

  test('logs out on terminal refresh failure', async () => {
    const keycloakMock = createKeycloakMock();
    keycloakMock.updateToken.mockRejectedValueOnce({
      response: { status: 401 },
      error: 'invalid_grant',
    });

    localStorage.setItem('token', 'stale-token');
    localStorage.setItem('user', JSON.stringify({ id: 'u1' }));

    const module = await loadIsolatedAuthService(keycloakMock) as IsolatedAuthServiceModule;
    const authService = module.default;

    await authService.init({ onLoad: 'check-sso', checkLoginIframe: false });
    const token = await authService.refreshToken();

    expect(token).toBeUndefined();
    expect(keycloakMock.logout).toHaveBeenCalledTimes(1);
    expect(module.logAuthRecoveryEventMock).toHaveBeenCalledWith(
      'auth.refresh.failed',
      expect.objectContaining({
        shouldLogout: true,
      })
    );
    expect(localStorage.getItem('token')).toBeNull();
    expect(localStorage.getItem('user')).toBeNull();
  });

  test('logs out on terminal forbidden refresh failure payload', async () => {
    const keycloakMock = createKeycloakMock();
    keycloakMock.updateToken.mockRejectedValueOnce({
      response: { status: 403 },
      message: 'Forbidden',
    });

    localStorage.setItem('token', 'stale-token');
    localStorage.setItem('user', JSON.stringify({ id: 'u1' }));

    const module = await loadIsolatedAuthService(keycloakMock) as IsolatedAuthServiceModule;
    const authService = module.default;

    await authService.init({ onLoad: 'check-sso', checkLoginIframe: false });
    const token = await authService.refreshToken();

    expect(token).toBeUndefined();
    expect(keycloakMock.logout).toHaveBeenCalledTimes(1);
    expect(module.logAuthRecoveryEventMock).toHaveBeenCalledWith(
      'auth.refresh.failed',
      expect.objectContaining({
        status: 403,
        shouldLogout: true,
      })
    );
    expect(localStorage.getItem('token')).toBeNull();
    expect(localStorage.getItem('user')).toBeNull();
  });
});
