import keycloak from 'auth/keycloak';
import { User } from 'types';

class AuthService {
  async login(): Promise<void> {
    await keycloak.login();
  }

  async logout(): Promise<void> {
    await keycloak.logout();
    this.clearSession();
  }

  getToken(): string | undefined {
    return keycloak.token || undefined;
  }

  async refreshToken(): Promise<string | undefined> {
    if (!keycloak.authenticated) return undefined;
    try {
      await keycloak.updateToken(30);
      return keycloak.token || undefined;
    } catch (err) {
      await this.logout();
      return undefined;
    }
  }

  getCurrentUser(): User | null {
    if (!keycloak.tokenParsed) return null;
    const preferredUsername = keycloak.tokenParsed.preferred_username as string | undefined;
    const email = keycloak.tokenParsed.email as string | undefined;
    const realmRoles: string[] = (keycloak.tokenParsed.realm_access as any)?.roles || [];
    const resourceAccess = (keycloak.tokenParsed.resource_access as any) || {};
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
      id: keycloak.subject || '',
      username: preferredUsername || 'user',
      email: email || '',
      roles,
    } as User;
  }

  isAuthenticated(): boolean {
    return !!keycloak.authenticated;
  }

  clearSession() {
    localStorage.removeItem('token');
    localStorage.removeItem('user');
  }
}

const authService = new AuthService();
export { keycloak };
export default authService;
