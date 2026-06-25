import api from './api';

export type OAuthProviderType = 'GOOGLE' | 'MICROSOFT' | 'CUSTOM' | string;
export type OAuthRevokeCapabilityMode = 'PROVIDER_REVOKE' | 'LOCAL_CLEAR' | 'UNSUPPORTED';

export interface OAuthCredentialInventoryItem {
  id: string;
  ownerType: string;
  ownerId: string;
  provider?: OAuthProviderType | null;
  tokenEndpointConfigured: boolean;
  revokeEndpointConfigured: boolean;
  tenantIdConfigured: boolean;
  scopeConfigured: boolean;
  credentialKeyConfigured: boolean;
  accessTokenStored: boolean;
  refreshTokenStored: boolean;
  connected: boolean;
  tokenExpiresAt?: string | null;
  createdAt: string;
  updatedAt?: string | null;
  providerRevokeSupported: boolean;
  providerRevokeUnsupportedReason: string | null;
  providerRevokeMode: OAuthRevokeCapabilityMode;
}

export interface OAuthCredentialInventoryFilters {
  ownerType?: string;
  provider?: string;
}

export interface OAuthCredentialRevokeEndpointDetails {
  id: string;
  ownerType: string;
  ownerId: string;
  provider: OAuthProviderType;
  revokeEndpointConfigured: boolean;
  revokeEndpoint: string | null;
}

const BASE_URL = '/admin/oauth-credentials';

export const OAUTH_CREDENTIAL_ADMIN_UNEXPECTED_RESPONSE_MESSAGE =
  'OAuth credential admin endpoint returned an unexpected response. Mocked CI gate may not cover it; backend route may be missing.';

const isObject = (value: unknown): value is Record<string, unknown> => (
  value !== null && typeof value === 'object' && !Array.isArray(value)
);

const isStringOrNullish = (value: unknown): value is string | null | undefined => (
  value === null || value === undefined || typeof value === 'string'
);

const isOAuthRevokeCapabilityMode = (value: unknown): value is OAuthRevokeCapabilityMode => (
  value === 'PROVIDER_REVOKE' || value === 'LOCAL_CLEAR' || value === 'UNSUPPORTED'
);

const isInventoryItem = (value: unknown): value is OAuthCredentialInventoryItem => {
  if (!isObject(value)) {
    return false;
  }
  return typeof value.id === 'string'
    && typeof value.ownerType === 'string'
    && typeof value.ownerId === 'string'
    && isStringOrNullish(value.provider)
    && typeof value.tokenEndpointConfigured === 'boolean'
    && typeof value.revokeEndpointConfigured === 'boolean'
    && typeof value.tenantIdConfigured === 'boolean'
    && typeof value.scopeConfigured === 'boolean'
    && typeof value.credentialKeyConfigured === 'boolean'
    && typeof value.accessTokenStored === 'boolean'
    && typeof value.refreshTokenStored === 'boolean'
    && typeof value.connected === 'boolean'
    && isStringOrNullish(value.tokenExpiresAt)
    && typeof value.createdAt === 'string'
    && isStringOrNullish(value.updatedAt)
    && typeof value.providerRevokeSupported === 'boolean'
    && isStringOrNullish(value.providerRevokeUnsupportedReason)
    && isOAuthRevokeCapabilityMode(value.providerRevokeMode);
};

const assertInventoryItem = (value: unknown): OAuthCredentialInventoryItem => {
  if (!isInventoryItem(value)) {
    throw new Error(OAUTH_CREDENTIAL_ADMIN_UNEXPECTED_RESPONSE_MESSAGE);
  }
  return value;
};

const isRevokeEndpointDetails = (value: unknown): value is OAuthCredentialRevokeEndpointDetails => {
  if (!isObject(value)) {
    return false;
  }
  return typeof value.id === 'string'
    && typeof value.ownerType === 'string'
    && typeof value.ownerId === 'string'
    && typeof value.provider === 'string'
    && typeof value.revokeEndpointConfigured === 'boolean'
    && (value.revokeEndpoint === null || typeof value.revokeEndpoint === 'string');
};

const assertRevokeEndpointDetails = (value: unknown): OAuthCredentialRevokeEndpointDetails => {
  if (!isRevokeEndpointDetails(value)) {
    throw new Error(OAUTH_CREDENTIAL_ADMIN_UNEXPECTED_RESPONSE_MESSAGE);
  }
  return value;
};

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
    const result = await api.get<unknown>(BASE_URL, { params });
    if (!Array.isArray(result)) {
      throw new Error(OAUTH_CREDENTIAL_ADMIN_UNEXPECTED_RESPONSE_MESSAGE);
    }
    return result.map(assertInventoryItem);
  }

  async requireReauth(credentialId: string): Promise<OAuthCredentialInventoryItem> {
    const result = await api.post<unknown>(`${BASE_URL}/${credentialId}/require-reauth`);
    return assertInventoryItem(result);
  }

  async refreshNow(credentialId: string): Promise<OAuthCredentialInventoryItem> {
    const result = await api.post<unknown>(`${BASE_URL}/${credentialId}/refresh-now`);
    return assertInventoryItem(result);
  }

  async revoke(credentialId: string): Promise<OAuthCredentialInventoryItem> {
    const result = await api.post<unknown>(`${BASE_URL}/${credentialId}/revoke`);
    return assertInventoryItem(result);
  }

  async updateRevokeEndpoint(
    credentialId: string,
    revokeEndpoint: string
  ): Promise<OAuthCredentialInventoryItem> {
    const result = await api.put<unknown>(`${BASE_URL}/${credentialId}/revoke-endpoint`, {
      revokeEndpoint,
    });
    return assertInventoryItem(result);
  }

  async getRevokeEndpointDetails(credentialId: string): Promise<OAuthCredentialRevokeEndpointDetails> {
    const result = await api.get<unknown>(`${BASE_URL}/${credentialId}/revoke-endpoint`);
    return assertRevokeEndpointDetails(result);
  }
}

const oauthCredentialAdminService = new OAuthCredentialAdminService();

export default oauthCredentialAdminService;
