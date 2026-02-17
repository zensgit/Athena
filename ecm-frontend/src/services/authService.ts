import type { KeycloakInstance, KeycloakLoginOptions, KeycloakInitOptions } from 'keycloak-js';
import { User } from 'types';

type KeycloakInitOptionsWithPkce = KeycloakInitOptions & { pkceMethod?: string };

let keycloakInstance: KeycloakInstance | null = null;
let keycloakLoadPromise: Promise<KeycloakInstance> | null = null;

const TRANSIENT_REFRESH_ERROR_PATTERN =
  /network error|failed to fetch|timeout|timed out|ecconnreset|econnrefused|etimedout|status code 5\d{2}/i;
const AUTH_TERMINAL_REFRESH_ERROR_PATTERN =
  /invalid[_\s-]?grant|invalid[_\s-]?token|refresh token|session not active|not authenticated|unauthorized|forbidden/i;

const extractRefreshErrorStatus = (error: unknown): number | null => {
  if (!error || typeof error !== 'object') return null;
  const candidate = error as {
    status?: unknown;
    response?: { status?: unknown };
    xhr?: { status?: unknown };
  };
  const raw = candidate.response?.status ?? candidate.status ?? candidate.xhr?.status;
  const status = Number(raw);
  if (!Number.isFinite(status)) return null;
  return status;
};

const extractRefreshErrorText = (error: unknown): string => {
  if (!error) return '';
  if (typeof error === 'string') return error;
  if (error instanceof Error) return error.message || '';
  if (typeof error === 'object') {
    const candidate = error as {
      message?: unknown;
      error?: unknown;
      error_description?: unknown;
      response?: { data?: unknown };
    };
    const responseText =
      typeof candidate.response?.data === 'string'
        ? candidate.response?.data
        : JSON.stringify(candidate.response?.data ?? {});
    return [
      typeof candidate.message === 'string' ? candidate.message : '',
      typeof candidate.error === 'string' ? candidate.error : '',
      typeof candidate.error_description === 'string' ? candidate.error_description : '',
      responseText,
    ]
      .filter(Boolean)
      .join(' ');
  }
  return String(error);
};

export const shouldLogoutOnRefreshError = (error: unknown): boolean => {
  const status = extractRefreshErrorStatus(error);
  const text = extractRefreshErrorText(error);

  if (TRANSIENT_REFRESH_ERROR_PATTERN.test(text)) {
    return false;
  }
  if (status === 0) {
    return false;
  }
  if (status !== null) {
    if (status >= 500) return false;
    if (status >= 400 && status < 500) return true;
  }
  if (AUTH_TERMINAL_REFRESH_ERROR_PATTERN.test(text)) {
    return true;
  }

  // Keep previous conservative behavior for unknown refresh failures.
  return true;
};

const getBypassMode = () => {
  const bypassEnabledByEnv = process.env.REACT_APP_E2E_BYPASS_AUTH === '1';
  if (typeof window === 'undefined') {
    return bypassEnabledByEnv;
  }
  const isE2E = window.navigator?.webdriver === true;
  if (!isE2E) {
    return false;
  }
  const flag = window.localStorage.getItem('ecm_e2e_bypass');
  return bypassEnabledByEnv || flag === '1';
};

const loadBypassSession = () => {
  if (!getBypassMode()) {
    return null;
  }
  try {
    const token = localStorage.getItem('token');
    const userRaw = localStorage.getItem('user');
    if (!token || !userRaw) {
      return null;
    }
    const user = JSON.parse(userRaw) as User;
    return { token, user };
  } catch {
    return null;
  }
};

const loadKeycloak = async (): Promise<KeycloakInstance> => {
  if (keycloakInstance) {
    return keycloakInstance;
  }
  if (!keycloakLoadPromise) {
    keycloakLoadPromise = import('auth/keycloak').then((module) => {
      keycloakInstance = module.default as KeycloakInstance;
      return keycloakInstance;
    });
  }
  return keycloakLoadPromise;
};

const ensureKeycloakInitializedForLogin = async (keycloak: KeycloakInstance): Promise<void> => {
  const withInitState = keycloak as KeycloakInstance & { didInitialize?: boolean };
  if (withInitState.didInitialize) {
    return;
  }
  const canUsePkce = typeof window !== 'undefined' && !!(window.crypto && window.crypto.subtle);
  await keycloak.init({
    pkceMethod: canUsePkce ? 'S256' : undefined,
    checkLoginIframe: false,
  } as any);
};

class AuthService {
  async init(options: KeycloakInitOptionsWithPkce): Promise<boolean> {
    if (getBypassMode()) {
      return Boolean(loadBypassSession()?.token);
    }
    const keycloak = await loadKeycloak();
    const authenticated = await keycloak.init(options as any);
    return Boolean(authenticated);
  }

  async login(options?: KeycloakLoginOptions): Promise<void> {
    const keycloak = await loadKeycloak();
    await ensureKeycloakInitializedForLogin(keycloak);
    await keycloak.login(options);
  }

  async logout(): Promise<void> {
    if (keycloakInstance) {
      await keycloakInstance.logout();
    }
    this.clearSession();
  }

  getToken(): string | undefined {
    if (getBypassMode()) {
      return loadBypassSession()?.token;
    }
    return keycloakInstance?.token || undefined;
  }

  getTokenParsed(): Record<string, any> | null {
    return (keycloakInstance?.tokenParsed as Record<string, any> | undefined) || null;
  }

  async refreshToken(): Promise<string | undefined> {
    if (getBypassMode()) {
      return loadBypassSession()?.token;
    }
    if (!keycloakInstance || !keycloakInstance.authenticated) return undefined;
    try {
      await keycloakInstance.updateToken(30);
      return keycloakInstance.token || undefined;
    } catch (err) {
      if (shouldLogoutOnRefreshError(err)) {
        await this.logout();
        return undefined;
      }
      console.warn('Token refresh failed due to transient error; keeping current session.', err);
      return keycloakInstance.token || undefined;
    }
  }

  getCurrentUser(): User | null {
    if (getBypassMode()) {
      return loadBypassSession()?.user ?? null;
    }
    if (!keycloakInstance?.tokenParsed) return null;
    const tokenParsed = keycloakInstance.tokenParsed as Record<string, any>;
    const preferredUsername = tokenParsed.preferred_username as string | undefined;
    const email = tokenParsed.email as string | undefined;
    const realmRoles: string[] = (tokenParsed.realm_access as any)?.roles || [];
    const resourceAccess = (tokenParsed.resource_access as any) || {};
    const clientRoles: string[] = Object.values(resourceAccess).flatMap((access: any) => access?.roles || []);
    const rawRoles = [...realmRoles, ...clientRoles];
    const roles = Array.from(
      new Set(
        rawRoles.map((role) => {
          if (!role) return role;
          if (role.startsWith('ROLE_')) return role;
          if (role.startsWith('ecm-')) {
            return `ROLE_${role.replace('ecm-', '').toUpperCase()}`;
          }
          return `ROLE_${role.toUpperCase()}`;
        })
      )
    ).filter(Boolean) as string[];
    return {
      id: keycloakInstance.subject || '',
      username: preferredUsername || 'user',
      email: email || '',
      roles,
    } as User;
  }

  isAuthenticated(): boolean {
    if (getBypassMode()) {
      return Boolean(loadBypassSession()?.token);
    }
    return !!keycloakInstance?.authenticated;
  }

  startTokenRefresh(intervalMs = 20000) {
    if (!keycloakInstance) return;
    window.setInterval(() => {
      if (!keycloakInstance?.authenticated) return;
      void (async () => {
        try {
          await keycloakInstance.updateToken(30 as any);
        } catch {
          console.warn('Token refresh failed, user may need to re-login');
        }
      })();
    }, intervalMs);
  }

  clearSession() {
    localStorage.removeItem('token');
    localStorage.removeItem('user');
    localStorage.removeItem('ecm_e2e_bypass');
  }
}

const authService = new AuthService();
export default authService;
export { loadKeycloak };
