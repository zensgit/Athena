import axios from 'axios';
import { toast } from 'react-toastify';
import {
  AUTH_INIT_STATUS_KEY,
  AUTH_INIT_STATUS_SESSION_EXPIRED,
} from 'constants/auth';
import authService from './authService';
import { ApiService } from './api';
import { logAuthRecoveryEvent } from 'utils/authRecoveryDebug';

jest.mock('./authService', () => ({
  __esModule: true,
  default: {
    refreshToken: jest.fn().mockResolvedValue(undefined),
    getToken: jest.fn().mockReturnValue(undefined),
  },
}));

jest.mock('react-toastify', () => ({
  toast: {
    error: jest.fn(),
  },
}));

jest.mock('utils/authRecoveryDebug', () => ({
  __esModule: true,
  logAuthRecoveryEvent: jest.fn(),
}));

type AxiosMockInstance = {
  request: jest.Mock;
  get: jest.Mock;
  post: jest.Mock;
  put: jest.Mock;
  patch: jest.Mock;
  delete: jest.Mock;
  interceptors: {
    request: {
      use: jest.Mock;
    };
    response: {
      use: jest.Mock;
    };
  };
};

const authServiceMock = authService as jest.Mocked<typeof authService>;
const toastErrorMock = toast.error as jest.Mock;
const logAuthRecoveryEventMock = logAuthRecoveryEvent as jest.Mock;

const getLatestAxiosInstance = (): AxiosMockInstance => {
  const createMock = axios.create as jest.Mock;
  const latestCall = createMock.mock.results[createMock.mock.results.length - 1];
  if (!latestCall) {
    throw new Error('Expected axios.create to be called');
  }
  return latestCall.value as AxiosMockInstance;
};

const getResponseErrorHandler = (instance: AxiosMockInstance) => {
  const call = instance.interceptors.response.use.mock.calls[instance.interceptors.response.use.mock.calls.length - 1];
  if (!call) {
    throw new Error('Expected response interceptor registration');
  }
  return call[1] as (error: any) => Promise<unknown>;
};

const getRequestInterceptor = (instance: AxiosMockInstance) => {
  const call = instance.interceptors.request.use.mock.calls[instance.interceptors.request.use.mock.calls.length - 1];
  if (!call) {
    throw new Error('Expected request interceptor registration');
  }
  return call[0] as (config: any) => Promise<any>;
};

describe('ApiService auth recovery', () => {
  const createAxiosInstance = (): AxiosMockInstance => ({
    request: jest.fn().mockResolvedValue({ data: {} }),
    get: jest.fn().mockResolvedValue({ data: {} }),
    post: jest.fn().mockResolvedValue({ data: {} }),
    put: jest.fn().mockResolvedValue({ data: {} }),
    patch: jest.fn().mockResolvedValue({ data: {} }),
    delete: jest.fn().mockResolvedValue({ data: {} }),
    interceptors: {
      request: { use: jest.fn() },
      response: { use: jest.fn() },
    },
  });

  beforeEach(() => {
    jest.clearAllMocks();
    sessionStorage.clear();
    window.history.pushState({}, '', '/search-results');
    (axios.isCancel as unknown as jest.Mock | undefined)?.mockReset?.();
    (axios.isCancel as unknown as jest.Mock | undefined)?.mockReturnValue?.(false);
    if (typeof axios.isCancel !== 'function') {
      (axios as any).isCancel = jest.fn().mockReturnValue(false);
    }
    const createMock = axios.create as jest.Mock;
    createMock.mockReset();
    createMock.mockImplementation(() => createAxiosInstance());
  });

  it('retries once with refreshed token before forcing login redirect', async () => {
    authServiceMock.refreshToken.mockResolvedValueOnce('fresh-token');
    const service = new ApiService();
    const instance = getLatestAxiosInstance();
    const responseErrorHandler = getResponseErrorHandler(instance);

    instance.request.mockResolvedValueOnce({ data: { ok: true } });
    const originalConfig = { headers: {}, url: '/documents', method: 'get' };

    const retriedResponse = await responseErrorHandler({
      response: { status: 401 },
      config: originalConfig,
    });

    expect(authServiceMock.refreshToken).toHaveBeenCalledTimes(1);
    expect(instance.request).toHaveBeenCalledWith(
      expect.objectContaining({
        _retryAuth: true,
        headers: expect.objectContaining({
          Authorization: 'Bearer fresh-token',
        }),
      })
    );
    expect(sessionStorage.getItem(AUTH_INIT_STATUS_KEY)).toBeNull();
    expect(retriedResponse).toEqual({ data: { ok: true } });
    expect(toastErrorMock).not.toHaveBeenCalled();
    expect(logAuthRecoveryEventMock).toHaveBeenCalledWith(
      'api.response.401.retry.success',
      expect.objectContaining({
        retryAuth: true,
      })
    );
    expect(service).toBeTruthy();
  });

  it('continues request flow on transient refresh failure without forcing logout markers', async () => {
    authServiceMock.refreshToken.mockRejectedValueOnce(new Error('Network Error'));
    authServiceMock.getToken.mockReturnValue('still-valid-token');
    new ApiService();
    const instance = getLatestAxiosInstance();
    const requestInterceptor = getRequestInterceptor(instance);

    const requestConfig = await requestInterceptor({
      headers: {},
      url: '/search',
      method: 'get',
    });

    expect(authServiceMock.refreshToken).toHaveBeenCalledTimes(1);
    expect(requestConfig.headers.Authorization).toBe('Bearer still-valid-token');
    expect(sessionStorage.getItem(AUTH_INIT_STATUS_KEY)).toBeNull();
    expect(localStorage.getItem('ecm_auth_redirect_reason')).toBeNull();
    expect(toastErrorMock).not.toHaveBeenCalled();
    expect(logAuthRecoveryEventMock).toHaveBeenCalledWith(
      'api.request.refresh.failed',
      expect.objectContaining({
        method: 'GET',
        url: '/search',
      })
    );
  });

  it('retries from responseURL when axios error config is missing', async () => {
    authServiceMock.refreshToken.mockResolvedValueOnce('fresh-token');
    new ApiService();
    const instance = getLatestAxiosInstance();
    const responseErrorHandler = getResponseErrorHandler(instance);

    instance.request.mockResolvedValueOnce({ data: { ok: true } });

    const retriedResponse = await responseErrorHandler({
      response: { status: 401 },
      request: {
        responseURL: '/api/v1/search?q=fallback-retry',
      },
    });

    expect(instance.request).toHaveBeenCalledWith(
      expect.objectContaining({
        _retryAuth: true,
        url: '/api/v1/search?q=fallback-retry',
      })
    );
    expect(retriedResponse).toEqual({ data: { ok: true } });
  });

  it('marks session expired when refresh cannot recover 401', async () => {
    window.history.pushState({}, '', '/login');
    authServiceMock.refreshToken.mockResolvedValueOnce(undefined);
    const service = new ApiService();
    const instance = getLatestAxiosInstance();
    const responseErrorHandler = getResponseErrorHandler(instance);

    const error = {
      response: { status: 401 },
      config: { headers: {}, url: '/documents', method: 'get' },
    };
    instance.request.mockRejectedValueOnce({
      response: { status: 401 },
      config: { headers: {}, url: '/documents', method: 'get', _retryAuth: true },
    });

    await expect(responseErrorHandler(error)).rejects.toMatchObject({
      response: { status: 401 },
    });
    expect(instance.request).toHaveBeenCalledTimes(1);
    expect(sessionStorage.getItem(AUTH_INIT_STATUS_KEY)).toBe(AUTH_INIT_STATUS_SESSION_EXPIRED);
    expect(localStorage.getItem('ecm_auth_redirect_reason')).toBe(AUTH_INIT_STATUS_SESSION_EXPIRED);
    expect(toastErrorMock).toHaveBeenCalledWith('Session expired. Please login again.');
    expect(logAuthRecoveryEventMock).toHaveBeenCalledWith(
      'api.session_expired.mark',
      expect.objectContaining({
        pathname: '/login',
      })
    );
    expect(service).toBeTruthy();
  });

  it('marks session expired when refresh throws terminal auth error during 401 recovery', async () => {
    window.history.pushState({}, '', '/login');
    authServiceMock.refreshToken.mockRejectedValueOnce({
      response: { status: 401 },
      error: 'invalid_grant',
    });
    new ApiService();
    const instance = getLatestAxiosInstance();
    const responseErrorHandler = getResponseErrorHandler(instance);

    const error = {
      response: { status: 401 },
      config: { headers: {}, url: '/documents', method: 'get' },
    };

    await expect(responseErrorHandler(error)).rejects.toMatchObject({
      response: { status: 401 },
    });

    expect(instance.request).not.toHaveBeenCalled();
    expect(sessionStorage.getItem(AUTH_INIT_STATUS_KEY)).toBe(AUTH_INIT_STATUS_SESSION_EXPIRED);
    expect(localStorage.getItem('ecm_auth_redirect_reason')).toBe(AUTH_INIT_STATUS_SESSION_EXPIRED);
    expect(toastErrorMock).toHaveBeenCalledWith('Session expired. Please login again.');
    expect(logAuthRecoveryEventMock).toHaveBeenCalledWith(
      'api.response.401.retry.failed',
      expect.objectContaining({
        method: 'GET',
        url: '/documents',
      })
    );
    expect(logAuthRecoveryEventMock).toHaveBeenCalledWith(
      'api.session_expired.mark',
      expect.objectContaining({
        pathname: '/login',
      })
    );
  });
});
