import type { KeycloakInstance, KeycloakLoginOptions, KeycloakInitOptions } from 'keycloak-js';
import { User } from 'types';

type KeycloakInitOptionsWithPkce = KeycloakInitOptions & { pkceMethod?: string };

let keycloakInstance: KeycloakInstance | null = null;
let keycloakLoadPromise: Promise<KeycloakInstance> | null = null;

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

class AuthService {
  async init(options: KeycloakInitOptionsWithPkce): Promise<boolean> {
    const keycloak = await loadKeycloak();
    const authenticated = await keycloak.init(options as any);
    return Boolean(authenticated);
  }

  async login(options?: KeycloakLoginOptions): Promise<void> {
    const keycloak = await loadKeycloak();
    await keycloak.login(options);
  }

  async logout(): Promise<void> {
    if (keycloakInstance) {
      await keycloakInstance.logout();
    }
    this.clearSession();
  }

  getToken(): string | undefined {
    return keycloakInstance?.token || undefined;
  }

  getTokenParsed(): Record<string, any> | null {
    return (keycloakInstance?.tokenParsed as Record<string, any> | undefined) || null;
  }

  async refreshToken(): Promise<string | undefined> {
    if (!keycloakInstance || !keycloakInstance.authenticated) return undefined;
    try {
      await keycloakInstance.updateToken(30);
      return keycloakInstance.token || undefined;
    } catch (err) {
      await this.logout();
      return undefined;
    }
  }

  getCurrentUser(): User | null {
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
  }
}

const authService = new AuthService();
export default authService;
export { loadKeycloak };
