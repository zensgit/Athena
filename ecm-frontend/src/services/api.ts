import axios, { AxiosInstance, AxiosRequestConfig } from 'axios';
import { toast } from 'react-toastify';
import authService from './authService';
import {
  AUTH_INIT_STATUS_KEY,
  AUTH_REDIRECT_REASON_KEY,
  AUTH_INIT_STATUS_SESSION_EXPIRED,
  LOGIN_IN_PROGRESS_KEY,
  LOGIN_IN_PROGRESS_STARTED_AT_KEY,
} from 'constants/auth';
import { logAuthRecoveryEvent } from 'utils/authRecoveryDebug';

const API_BASE_URL = process.env.REACT_APP_API_URL
  || process.env.REACT_APP_API_BASE_URL
  || '/api/v1';

export class ApiService {
  private api: AxiosInstance;
  private tokenRefreshPromise: Promise<string | undefined> | null = null;
  private redirectInFlight = false;
  private lastRequestConfig: (AxiosRequestConfig & { _retryAuth?: boolean }) | null = null;

  constructor() {
    this.api = axios.create({
      baseURL: API_BASE_URL,
      headers: {
        'Content-Type': 'application/json',
      },
    });

    // Request interceptor to add auth token
    this.api.interceptors.request.use(
      async (config) => {
        try {
          await this.refreshTokenOnce();
        } catch (refreshError) {
          logAuthRecoveryEvent('api.request.refresh.failed', {
            ...this.toRequestMeta(config),
            error: this.toErrorMessage(refreshError),
          });
          // If refresh fails, let the request proceed; the 401 handler will redirect to login.
        }

        const token = authService.getToken();
        if (token) {
          config.headers.Authorization = `Bearer ${token}`;
        }
        this.lastRequestConfig = {
          ...config,
          headers: {
            ...(config.headers || {}),
          },
        };
        return config;
      },
      (error) => {
        return Promise.reject(error);
      }
    );

    // Response interceptor for error handling
    this.api.interceptors.response.use(
      (response) => response,
      async (error) => {
        if (axios.isCancel(error) || error.code === 'ERR_CANCELED') {
          return Promise.reject(error);
        }
        if (error.response?.status === 401) {
          const originalRequest = this.resolveRetryRequestConfig(error);
          logAuthRecoveryEvent('api.response.401.received', this.toRequestMeta(originalRequest));
          if (originalRequest && !originalRequest._retryAuth) {
            originalRequest._retryAuth = true;
            logAuthRecoveryEvent('api.response.401.retry.start', this.toRequestMeta(originalRequest));
            try {
              const refreshedToken = await this.refreshTokenOnce();
              const fallbackToken = authService.getToken();
              const effectiveToken = refreshedToken || fallbackToken;
              if (effectiveToken) {
                originalRequest.headers = {
                  ...(originalRequest.headers || {}),
                  Authorization: `Bearer ${effectiveToken}`,
                };
              }
              // Retry once even if refresh returns undefined (browser/runtime differences).
              const response = await this.api.request(originalRequest);
              logAuthRecoveryEvent('api.response.401.retry.success', this.toRequestMeta(originalRequest));
              return response;
            } catch (retryError) {
              logAuthRecoveryEvent('api.response.401.retry.failed', {
                ...this.toRequestMeta(originalRequest),
                error: this.toErrorMessage(retryError),
              });
              // Fall through and redirect to login below.
            }
          }

          logAuthRecoveryEvent('api.response.401.redirect', this.toRequestMeta(originalRequest));
          this.markSessionExpiredAndRedirect();
        } else if (error.response?.data?.message) {
          toast.error(error.response.data.message);
        } else {
          toast.error('An unexpected error occurred');
        }
        return Promise.reject(error);
      }
    );
  }

  private async refreshTokenOnce(): Promise<string | undefined> {
    if (!this.tokenRefreshPromise) {
      this.tokenRefreshPromise = authService.refreshToken().finally(() => {
        this.tokenRefreshPromise = null;
      });
    }
    return this.tokenRefreshPromise;
  }

  private markSessionExpiredAndRedirect() {
    logAuthRecoveryEvent('api.session_expired.mark', {
      pathname: window.location.pathname,
    });
    try {
      sessionStorage.setItem(AUTH_INIT_STATUS_KEY, AUTH_INIT_STATUS_SESSION_EXPIRED);
      sessionStorage.removeItem(LOGIN_IN_PROGRESS_KEY);
      sessionStorage.removeItem(LOGIN_IN_PROGRESS_STARTED_AT_KEY);
      localStorage.setItem(AUTH_REDIRECT_REASON_KEY, AUTH_INIT_STATUS_SESSION_EXPIRED);
      localStorage.removeItem('token');
    } catch {
      // Best effort only.
    }

    if (!this.redirectInFlight) {
      this.redirectInFlight = true;
      toast.error('Session expired. Please login again.');
      if (window.location.pathname !== '/login') {
        const loginUrl = new URL('/login', window.location.origin);
        loginUrl.searchParams.set('reason', AUTH_INIT_STATUS_SESSION_EXPIRED);
        logAuthRecoveryEvent('api.session_expired.redirect', {
          from: window.location.pathname,
          to: `${loginUrl.pathname}${loginUrl.search}`,
        });
        window.location.assign(`${loginUrl.pathname}${loginUrl.search}`);
      } else {
        logAuthRecoveryEvent('api.session_expired.redirect.skipped', {
          reason: 'already_on_login',
        });
      }
    }
  }

  private resolveRetryRequestConfig(
    error: any
  ): (AxiosRequestConfig & { _retryAuth?: boolean }) | null {
    const candidate = (error?.config || error?.response?.config || {}) as AxiosRequestConfig & {
      _retryAuth?: boolean;
    };
    const responseUrl = typeof error?.request?.responseURL === 'string' ? error.request.responseURL : undefined;
    const resolvedUrl = candidate.url || responseUrl;
    if (!resolvedUrl) {
      if (this.lastRequestConfig?.url) {
        return {
          ...this.lastRequestConfig,
          headers: {
            ...(this.lastRequestConfig.headers || {}),
          },
        };
      }
      return null;
    }
    return {
      ...candidate,
      url: resolvedUrl,
      headers: {
        ...(candidate.headers || {}),
      },
    };
  }

  private toErrorMessage(error: unknown): string {
    if (!error) {
      return 'unknown';
    }
    if (error instanceof Error) {
      return error.message || error.name;
    }
    if (typeof error === 'string') {
      return error;
    }
    try {
      return JSON.stringify(error);
    } catch {
      return String(error);
    }
  }

  private toRequestMeta(config?: AxiosRequestConfig & { _retryAuth?: boolean } | null): Record<string, unknown> {
    const method = typeof config?.method === 'string' ? config.method.toUpperCase() : undefined;
    const url = typeof config?.url === 'string' ? config.url : undefined;
    return {
      method,
      url,
      retryAuth: Boolean(config?._retryAuth),
    };
  }

  get<T>(url: string, config?: AxiosRequestConfig): Promise<T> {
    return this.api.get<T>(url, config).then((response) => response.data);
  }

  post<T>(url: string, data?: any, config?: AxiosRequestConfig): Promise<T> {
    return this.api.post<T>(url, data, config).then((response) => response.data);
  }

  put<T>(url: string, data?: any, config?: AxiosRequestConfig): Promise<T> {
    return this.api.put<T>(url, data, config).then((response) => response.data);
  }

  patch<T>(url: string, data?: any, config?: AxiosRequestConfig): Promise<T> {
    return this.api.patch<T>(url, data, config).then((response) => response.data);
  }

  delete<T>(url: string, config?: AxiosRequestConfig): Promise<T> {
    return this.api.delete<T>(url, config).then((response) => response.data);
  }

  getBlob(url: string, config?: AxiosRequestConfig): Promise<Blob> {
    return this.api
      .get(url, { ...config, responseType: 'blob' })
      .then((response) => response.data as Blob);
  }

  uploadFile(url: string, file: File, onProgress?: (progress: number) => void): Promise<any> {
    const formData = new FormData();
    formData.append('file', file);

    return this.api.post(url, formData, {
      headers: {
        'Content-Type': 'multipart/form-data',
      },
      onUploadProgress: (progressEvent) => {
        if (onProgress && progressEvent.total) {
          const progress = Math.round((progressEvent.loaded * 100) / progressEvent.total);
          onProgress(progress);
        }
      },
    }).then((response) => response.data);
  }

  downloadFile(url: string, filename: string): Promise<void> {
    return this.api.get(url, { responseType: 'blob' }).then((response) => {
      const blob = new Blob([response.data]);
      const link = document.createElement('a');
      link.href = window.URL.createObjectURL(blob);
      link.download = filename;
      link.click();
      window.URL.revokeObjectURL(link.href);
    });
  }
}

const apiService = new ApiService();
export default apiService;
