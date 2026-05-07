import api from './api';

export type OAuthProviderType = 'GOOGLE' | 'MICROSOFT' | 'CUSTOM' | string;

export interface OAuthCredentialInventoryItem {
  id: string;
  ownerType: string;
  ownerId: string;
  provider?: OAuthProviderType | null;
  tokenEndpointConfigured: boolean;
  tenantIdConfigured: boolean;
  scopeConfigured: boolean;
  credentialKeyConfigured: boolean;
  accessTokenStored: boolean;
  refreshTokenStored: boolean;
  connected: boolean;
  tokenExpiresAt?: string | null;
  createdAt: string;
  updatedAt?: string | null;
}

export interface OAuthCredentialInventoryFilters {
  ownerType?: string;
  provider?: string;
}

const BASE_URL = '/admin/oauth-credentials';

class OAuthCredentialAdminService {
  async listCredentials(
    filters: OAuthCredentialInventoryFilters = {}
  ): Promise<OAuthCredentialInventoryItem[]> {
    const params: Record<string, string> = {};
    const ownerType = filters.ownerType?.trim();
    const provider = filters.provider?.trim();
    if (ownerType) {
      params.ownerType = ownerType;
    }
    if (provider) {
      params.provider = provider;
    }
    return api.get<OAuthCredentialInventoryItem[]>(BASE_URL, { params });
  }

  async requireReauth(credentialId: string): Promise<OAuthCredentialInventoryItem> {
    return api.post<OAuthCredentialInventoryItem>(`${BASE_URL}/${credentialId}/require-reauth`);
  }

  async refreshNow(credentialId: string): Promise<OAuthCredentialInventoryItem> {
    return api.post<OAuthCredentialInventoryItem>(`${BASE_URL}/${credentialId}/refresh-now`);
  }

  async revoke(credentialId: string): Promise<OAuthCredentialInventoryItem> {
    return api.post<OAuthCredentialInventoryItem>(`${BASE_URL}/${credentialId}/revoke`);
  }
}

const oauthCredentialAdminService = new OAuthCredentialAdminService();

export default oauthCredentialAdminService;
