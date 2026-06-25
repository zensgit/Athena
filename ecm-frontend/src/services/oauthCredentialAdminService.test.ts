import api from './api';
import oauthCredentialAdminService, {
  OAUTH_CREDENTIAL_ADMIN_UNEXPECTED_RESPONSE_MESSAGE,
  OAuthCredentialInventoryItem,
} from './oauthCredentialAdminService';

jest.mock('./api', () => ({
  __esModule: true,
  default: {
    get: jest.fn(),
    post: jest.fn(),
    put: jest.fn(),
  },
}));

const mockedApi = api as jest.Mocked<typeof api>;

const credential: OAuthCredentialInventoryItem = {
  id: 'credential-1',
  ownerType: 'MAIL_ACCOUNT',
  ownerId: 'aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee',
  provider: 'GOOGLE',
  tokenEndpointConfigured: true,
  revokeEndpointConfigured: false,
  tenantIdConfigured: false,
  scopeConfigured: true,
  credentialKeyConfigured: true,
  accessTokenStored: true,
  refreshTokenStored: true,
  connected: true,
  tokenExpiresAt: '2026-05-06T10:15:30Z',
  createdAt: '2026-05-01T09:00:00Z',
  updatedAt: '2026-05-06T10:00:00Z',
  providerRevokeSupported: true,
  providerRevokeUnsupportedReason: null,
  providerRevokeMode: 'PROVIDER_REVOKE',
};

describe('oauthCredentialAdminService', () => {
  beforeEach(() => {
    jest.clearAllMocks();
  });

  test('lists credential inventory with trimmed filter params', async () => {
    mockedApi.get.mockResolvedValueOnce([credential]);

    const result = await oauthCredentialAdminService.listCredentials({
      ownerType: ' MAIL_ACCOUNT ',
      provider: ' GOOGLE ',
    });

    expect(result).toEqual([credential]);
    expect(mockedApi.get).toHaveBeenCalledWith('/admin/oauth-credentials', {
      params: {
        ownerType: 'MAIL_ACCOUNT',
        provider: 'GOOGLE',
      },
    });
  });

  test('rejects HTML fallback for list responses', async () => {
    mockedApi.get.mockResolvedValueOnce('<!doctype html><html></html>');

    await expect(oauthCredentialAdminService.listCredentials()).rejects.toThrow(
      OAUTH_CREDENTIAL_ADMIN_UNEXPECTED_RESPONSE_MESSAGE
    );
  });

  test('rejects malformed list items', async () => {
    mockedApi.get.mockResolvedValueOnce([
      {
        ...credential,
        providerRevokeSupported: 'yes',
      },
    ]);

    await expect(oauthCredentialAdminService.listCredentials()).rejects.toThrow(
      OAUTH_CREDENTIAL_ADMIN_UNEXPECTED_RESPONSE_MESSAGE
    );
  });

  test('rejects list items missing explicit revoke capability mode', async () => {
    const withoutMode: Partial<OAuthCredentialInventoryItem> = { ...credential };
    delete withoutMode.providerRevokeMode;
    mockedApi.get.mockResolvedValueOnce([withoutMode]);

    await expect(oauthCredentialAdminService.listCredentials()).rejects.toThrow(
      OAUTH_CREDENTIAL_ADMIN_UNEXPECTED_RESPONSE_MESSAGE
    );
  });

  test.each([
    ['requireReauth', () => oauthCredentialAdminService.requireReauth('credential-1')],
    ['refreshNow', () => oauthCredentialAdminService.refreshNow('credential-1')],
    ['revoke', () => oauthCredentialAdminService.revoke('credential-1')],
  ])('rejects HTML fallback for %s responses', async (_name, action) => {
    mockedApi.post.mockResolvedValueOnce('<!doctype html><html></html>');

    await expect(action()).rejects.toThrow(OAUTH_CREDENTIAL_ADMIN_UNEXPECTED_RESPONSE_MESSAGE);
  });

  test.each([
    ['requireReauth', () => oauthCredentialAdminService.requireReauth('credential-1'), '/admin/oauth-credentials/credential-1/require-reauth'],
    ['refreshNow', () => oauthCredentialAdminService.refreshNow('credential-1'), '/admin/oauth-credentials/credential-1/refresh-now'],
    ['revoke', () => oauthCredentialAdminService.revoke('credential-1'), '/admin/oauth-credentials/credential-1/revoke'],
  ])('returns guarded %s response', async (_name, action, endpoint) => {
    mockedApi.post.mockResolvedValueOnce(credential);

    const result = await action();

    expect(result).toEqual(credential);
    expect(mockedApi.post).toHaveBeenCalledWith(endpoint);
  });

  test('updates guarded CUSTOM revoke endpoint response', async () => {
    mockedApi.put.mockResolvedValueOnce({
      ...credential,
      provider: 'CUSTOM',
      revokeEndpointConfigured: true,
      providerRevokeSupported: true,
      providerRevokeUnsupportedReason: null,
    });

    const result = await oauthCredentialAdminService.updateRevokeEndpoint(
      'credential-1',
      'https://custom.example/revoke'
    );

    expect(result.revokeEndpointConfigured).toBe(true);
    expect(mockedApi.put).toHaveBeenCalledWith('/admin/oauth-credentials/credential-1/revoke-endpoint', {
      revokeEndpoint: 'https://custom.example/revoke',
    });
  });

  test('rejects HTML fallback for updateRevokeEndpoint response', async () => {
    mockedApi.put.mockResolvedValueOnce('<!doctype html><html></html>');

    await expect(
      oauthCredentialAdminService.updateRevokeEndpoint('credential-1', 'https://custom.example/revoke')
    ).rejects.toThrow(OAUTH_CREDENTIAL_ADMIN_UNEXPECTED_RESPONSE_MESSAGE);
  });

  test('reads guarded CUSTOM revoke endpoint details', async () => {
    mockedApi.get.mockResolvedValueOnce({
      id: 'credential-1',
      ownerType: 'MAIL_ACCOUNT',
      ownerId: 'aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee',
      provider: 'CUSTOM',
      revokeEndpointConfigured: true,
      revokeEndpoint: 'https://custom.example/revoke',
    });

    const result = await oauthCredentialAdminService.getRevokeEndpointDetails('credential-1');

    expect(result.revokeEndpoint).toBe('https://custom.example/revoke');
    expect(mockedApi.get).toHaveBeenCalledWith('/admin/oauth-credentials/credential-1/revoke-endpoint');
  });

  test('rejects HTML fallback for getRevokeEndpointDetails response', async () => {
    mockedApi.get.mockResolvedValueOnce('<!doctype html><html></html>');

    await expect(
      oauthCredentialAdminService.getRevokeEndpointDetails('credential-1')
    ).rejects.toThrow(OAUTH_CREDENTIAL_ADMIN_UNEXPECTED_RESPONSE_MESSAGE);
  });

  test('rejects malformed getRevokeEndpointDetails response', async () => {
    mockedApi.get.mockResolvedValueOnce({
      id: 'credential-1',
      ownerType: 'MAIL_ACCOUNT',
      ownerId: 'aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee',
      provider: 'CUSTOM',
      revokeEndpointConfigured: true,
      revokeEndpoint: 42,
    });

    await expect(
      oauthCredentialAdminService.getRevokeEndpointDetails('credential-1')
    ).rejects.toThrow(OAUTH_CREDENTIAL_ADMIN_UNEXPECTED_RESPONSE_MESSAGE);
  });
});
