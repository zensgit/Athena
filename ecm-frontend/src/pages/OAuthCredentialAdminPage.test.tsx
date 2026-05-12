import React from 'react';
import { fireEvent, render, screen, waitFor, waitForElementToBeRemoved, within } from '@testing-library/react';
import OAuthCredentialAdminPage from './OAuthCredentialAdminPage';
import oauthCredentialAdminService from 'services/oauthCredentialAdminService';

jest.mock('services/oauthCredentialAdminService', () => ({
  __esModule: true,
  default: {
    listCredentials: jest.fn(),
    requireReauth: jest.fn(),
    refreshNow: jest.fn(),
    revoke: jest.fn(),
    updateRevokeEndpoint: jest.fn(),
  },
}));

const mockedOAuthCredentialAdminService = oauthCredentialAdminService as jest.Mocked<typeof oauthCredentialAdminService>;

const credential = {
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
};

beforeEach(() => {
  jest.clearAllMocks();
  mockedOAuthCredentialAdminService.listCredentials.mockResolvedValue([credential]);
});

test('loads redacted OAuth credential inventory', async () => {
  render(<OAuthCredentialAdminPage />);

  expect(await screen.findByText('OAuth Credential Store')).toBeTruthy();
  expect(screen.getByText('Token values are never returned by this admin surface. Only storage and configuration flags are shown.')).toBeTruthy();

  const inventoryTable = screen.getByRole('table', { name: 'OAuth credential inventory' });
  expect(within(inventoryTable).getByText('MAIL_ACCOUNT')).toBeTruthy();
  expect(within(inventoryTable).getByText('GOOGLE')).toBeTruthy();
  expect(within(inventoryTable).getByText('aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee')).toBeTruthy();
  expect(within(inventoryTable).getByText('Credential key')).toBeTruthy();
  expect(within(inventoryTable).getByText('Revoke endpoint')).toBeTruthy();
  expect(within(inventoryTable).getByText('Refresh token')).toBeTruthy();
  expect(screen.queryByText('secret-access-token')).toBeNull();
  expect(screen.queryByText('secret-refresh-token')).toBeNull();
  expect(mockedOAuthCredentialAdminService.listCredentials).toHaveBeenCalledWith();
});

test('applies owner type and provider filters', async () => {
  render(<OAuthCredentialAdminPage />);
  await screen.findByText('MAIL_ACCOUNT');

  fireEvent.change(screen.getByLabelText('Owner type'), { target: { value: ' MAIL_ACCOUNT ' } });
  fireEvent.change(screen.getByLabelText('Provider'), { target: { value: 'GOOGLE' } });
  fireEvent.click(screen.getByRole('button', { name: 'Apply Filters' }));

  await waitFor(() => {
    expect(mockedOAuthCredentialAdminService.listCredentials).toHaveBeenLastCalledWith({
      ownerType: ' MAIL_ACCOUNT ',
      provider: 'GOOGLE',
    });
  });
});

test('shows empty state for unmatched filters', async () => {
  mockedOAuthCredentialAdminService.listCredentials.mockResolvedValue([]);

  render(<OAuthCredentialAdminPage />);

  expect(await screen.findByText('No OAuth credentials match the current filters.')).toBeTruthy();
});

test('filters to CUSTOM credentials missing a revoke endpoint', async () => {
  mockedOAuthCredentialAdminService.listCredentials.mockResolvedValue([
    {
      ...credential,
      id: 'custom-gap',
      ownerId: 'custom-gap-owner',
      provider: 'CUSTOM',
      revokeEndpointConfigured: false,
      providerRevokeSupported: false,
      providerRevokeUnsupportedReason: 'Provider-side revoke endpoint is not configured for this CUSTOM credential',
    },
    {
      ...credential,
      id: 'custom-ready',
      ownerId: 'custom-ready-owner',
      provider: 'CUSTOM',
      revokeEndpointConfigured: true,
      providerRevokeSupported: true,
      providerRevokeUnsupportedReason: null,
    },
    {
      ...credential,
      id: 'microsoft-unsupported',
      ownerId: 'microsoft-owner',
      provider: 'MICROSOFT',
      providerRevokeSupported: false,
      providerRevokeUnsupportedReason:
        'Provider-side revoke is only supported for GOOGLE; this credential is MICROSOFT',
    },
  ]);

  render(<OAuthCredentialAdminPage />);
  await screen.findByText('custom-gap-owner');

  fireEvent.click(screen.getByRole('button', { name: 'CUSTOM revoke gaps (1)' }));

  expect(
    screen.getByText('Showing CUSTOM credentials where provider-side revoke is blocked by a missing revoke endpoint.')
  ).toBeTruthy();
  const inventoryTable = screen.getByRole('table', { name: 'OAuth credential inventory' });
  expect(within(inventoryTable).getByText('custom-gap-owner')).toBeTruthy();
  expect(within(inventoryTable).queryByText('custom-ready-owner')).toBeNull();
  expect(within(inventoryTable).queryByText('microsoft-owner')).toBeNull();
});

test('filters to Provider Revoke ready credentials', async () => {
  mockedOAuthCredentialAdminService.listCredentials.mockResolvedValue([
    {
      ...credential,
      id: 'google-ready',
      ownerId: 'google-ready-owner',
      provider: 'GOOGLE',
      providerRevokeSupported: true,
      providerRevokeUnsupportedReason: null,
    },
    {
      ...credential,
      id: 'custom-blocked',
      ownerId: 'custom-blocked-owner',
      provider: 'CUSTOM',
      providerRevokeSupported: false,
      providerRevokeUnsupportedReason: 'Provider-side revoke endpoint is not configured for this CUSTOM credential',
    },
  ]);

  render(<OAuthCredentialAdminPage />);
  await screen.findByText('google-ready-owner');

  fireEvent.click(screen.getByRole('button', { name: 'Provider revoke ready (1)' }));

  expect(screen.getByText('Showing credentials where Provider Revoke is currently actionable.')).toBeTruthy();
  const inventoryTable = screen.getByRole('table', { name: 'OAuth credential inventory' });
  expect(within(inventoryTable).getByText('google-ready-owner')).toBeTruthy();
  expect(within(inventoryTable).queryByText('custom-blocked-owner')).toBeNull();
});

test('filters to Provider Revoke blocked credentials and can return to all rows', async () => {
  mockedOAuthCredentialAdminService.listCredentials.mockResolvedValue([
    {
      ...credential,
      id: 'google-ready',
      ownerId: 'google-ready-owner',
      provider: 'GOOGLE',
      providerRevokeSupported: true,
      providerRevokeUnsupportedReason: null,
    },
    {
      ...credential,
      id: 'microsoft-blocked',
      ownerId: 'microsoft-blocked-owner',
      provider: 'MICROSOFT',
      providerRevokeSupported: false,
      providerRevokeUnsupportedReason:
        'Provider-side revoke is only supported for GOOGLE; this credential is MICROSOFT',
    },
  ]);

  render(<OAuthCredentialAdminPage />);
  await screen.findByText('google-ready-owner');

  fireEvent.click(screen.getByRole('button', { name: 'Provider revoke blocked (1)' }));

  expect(
    screen.getByText('Showing credentials where Provider Revoke is blocked by backend capability metadata.')
  ).toBeTruthy();
  const inventoryTable = screen.getByRole('table', { name: 'OAuth credential inventory' });
  expect(within(inventoryTable).queryByText('google-ready-owner')).toBeNull();
  expect(within(inventoryTable).getByText('microsoft-blocked-owner')).toBeTruthy();

  fireEvent.click(screen.getByRole('button', { name: 'All (2)' }));

  expect(within(inventoryTable).getByText('google-ready-owner')).toBeTruthy();
  expect(within(inventoryTable).getByText('microsoft-blocked-owner')).toBeTruthy();
});

test('shows empty state when CUSTOM revoke gap filter has no matches', async () => {
  mockedOAuthCredentialAdminService.listCredentials.mockResolvedValue([
    {
      ...credential,
      id: 'custom-ready',
      ownerId: 'custom-ready-owner',
      provider: 'CUSTOM',
      revokeEndpointConfigured: true,
      providerRevokeSupported: true,
      providerRevokeUnsupportedReason: null,
    },
  ]);

  render(<OAuthCredentialAdminPage />);
  await screen.findByText('custom-ready-owner');

  fireEvent.click(screen.getByRole('button', { name: 'CUSTOM revoke gaps (0)' }));

  expect(await screen.findByText('No CUSTOM credentials currently need a revoke endpoint.')).toBeTruthy();
});

test('requires reauthorization by clearing stored token status', async () => {
  const confirmSpy = jest.spyOn(window, 'confirm').mockReturnValue(true);
  mockedOAuthCredentialAdminService.requireReauth.mockResolvedValue({
    ...credential,
    accessTokenStored: false,
    refreshTokenStored: false,
    connected: false,
    tokenExpiresAt: null,
  });

  render(<OAuthCredentialAdminPage />);
  await screen.findByText('MAIL_ACCOUNT');

  fireEvent.click(screen.getByRole('button', { name: 'Require Reauth' }));

  await waitFor(() => {
    expect(mockedOAuthCredentialAdminService.requireReauth).toHaveBeenCalledWith('credential-1');
  });
  await waitFor(() => {
    const button = screen.getByRole('button', { name: 'Require Reauth' }) as HTMLButtonElement;
    expect(button.disabled).toBe(true);
  });

  confirmSpy.mockRestore();
});

test('refreshes OAuth credential token status', async () => {
  const confirmSpy = jest.spyOn(window, 'confirm').mockReturnValue(true);
  mockedOAuthCredentialAdminService.refreshNow.mockResolvedValue({
    ...credential,
    tokenExpiresAt: '2030-01-02T03:04:05Z',
    updatedAt: '2030-01-02T03:00:00Z',
  });

  render(<OAuthCredentialAdminPage />);
  await screen.findByText('MAIL_ACCOUNT');

  fireEvent.click(screen.getByRole('button', { name: 'Refresh Now' }));

  await waitFor(() => {
    expect(mockedOAuthCredentialAdminService.refreshNow).toHaveBeenCalledWith('credential-1');
  });
  expect((await screen.findAllByText(/2030/)).length).toBeGreaterThan(0);

  confirmSpy.mockRestore();
});

test('surfaces refresh provider errors and reloads inventory', async () => {
  const confirmSpy = jest.spyOn(window, 'confirm').mockReturnValue(true);
  mockedOAuthCredentialAdminService.refreshNow.mockRejectedValue({
    response: {
      data: {
        message: 'OAUTH_REAUTH_REQUIRED: invalid_grant - Token expired',
      },
    },
  });

  render(<OAuthCredentialAdminPage />);
  await screen.findByText('MAIL_ACCOUNT');

  fireEvent.click(screen.getByRole('button', { name: 'Refresh Now' }));

  expect(await screen.findByText('OAUTH_REAUTH_REQUIRED: invalid_grant - Token expired')).toBeTruthy();
  await waitFor(() => {
    expect(mockedOAuthCredentialAdminService.listCredentials).toHaveBeenCalledTimes(2);
  });

  confirmSpy.mockRestore();
});

test('does not require reauthorization when confirmation is cancelled', async () => {
  const confirmSpy = jest.spyOn(window, 'confirm').mockReturnValue(false);

  render(<OAuthCredentialAdminPage />);
  await screen.findByText('MAIL_ACCOUNT');

  fireEvent.click(screen.getByRole('button', { name: 'Require Reauth' }));

  expect(mockedOAuthCredentialAdminService.requireReauth).not.toHaveBeenCalled();
  confirmSpy.mockRestore();
});

test('Provider Revoke is enabled for GOOGLE rows with a stored token', async () => {
  render(<OAuthCredentialAdminPage />);
  await screen.findByText('MAIL_ACCOUNT');

  const button = await screen.findByRole('button', { name: 'Provider Revoke' });
  expect((button as HTMLButtonElement).disabled).toBe(false);
});

test('Provider Revoke is disabled when backend reports providerRevokeSupported=false (regardless of provider)', async () => {
  // Pass GOOGLE plus supported=false to prove backend metadata wins over the
  // legacy client-side decision tree (which used to gate on provider==='GOOGLE').
  mockedOAuthCredentialAdminService.listCredentials.mockResolvedValue([
    {
      ...credential,
      provider: 'GOOGLE',
      providerRevokeSupported: false,
      providerRevokeUnsupportedReason: 'No locally stored OAuth token to revoke',
    },
  ]);

  render(<OAuthCredentialAdminPage />);
  await screen.findByText('MAIL_ACCOUNT');

  const button = await screen.findByRole('button', { name: 'Provider Revoke' });
  expect((button as HTMLButtonElement).disabled).toBe(true);
});

test('Provider Revoke is disabled for non-GOOGLE rows when backend reports unsupported', async () => {
  mockedOAuthCredentialAdminService.listCredentials.mockResolvedValue([
    {
      ...credential,
      provider: 'MICROSOFT',
      providerRevokeSupported: false,
      providerRevokeUnsupportedReason:
        'Provider-side revoke is only supported for GOOGLE; this credential is MICROSOFT',
    },
  ]);

  render(<OAuthCredentialAdminPage />);
  await screen.findByText('MICROSOFT');

  const button = await screen.findByRole('button', { name: 'Provider Revoke' });
  expect((button as HTMLButtonElement).disabled).toBe(true);
});

test('Provider Revoke surfaces the backend unsupported reason via tooltip wrapper aria-label', async () => {
  // The wrapping span carries the same backend-supplied reason as both the
  // MUI Tooltip title and an aria-label, so the disabled-button case stays
  // observable without hover-based testing-library queries (the existing
  // tests in this file all use fireEvent, not userEvent).
  const reason = 'Provider-side revoke is only supported for GOOGLE; this credential is MICROSOFT';
  mockedOAuthCredentialAdminService.listCredentials.mockResolvedValue([
    {
      ...credential,
      provider: 'MICROSOFT',
      providerRevokeSupported: false,
      providerRevokeUnsupportedReason: reason,
    },
  ]);

  render(<OAuthCredentialAdminPage />);
  await screen.findByText('MICROSOFT');

  const wrapper = await screen.findByLabelText(reason);
  expect(wrapper).toBeTruthy();
});

test('does not revoke OAuth token when confirmation is cancelled', async () => {
  const confirmSpy = jest.spyOn(window, 'confirm').mockReturnValue(false);

  render(<OAuthCredentialAdminPage />);
  await screen.findByText('MAIL_ACCOUNT');

  fireEvent.click(screen.getByRole('button', { name: 'Provider Revoke' }));

  expect(mockedOAuthCredentialAdminService.revoke).not.toHaveBeenCalled();
  confirmSpy.mockRestore();
});

test('revokes OAuth token at the provider and replaces the row from the redacted response', async () => {
  const confirmSpy = jest.spyOn(window, 'confirm').mockReturnValue(true);
  mockedOAuthCredentialAdminService.revoke.mockResolvedValue({
    ...credential,
    accessTokenStored: false,
    refreshTokenStored: false,
    connected: false,
    tokenExpiresAt: null,
    // After a successful revoke the backend reports the row no longer has a
    // local token to revoke, so providerRevokeSupported flips to false.
    providerRevokeSupported: false,
    providerRevokeUnsupportedReason:
      'Provider-side revoke requires a locally stored OAuth token; this credential row only references env-managed secrets',
  });

  render(<OAuthCredentialAdminPage />);
  await screen.findByText('MAIL_ACCOUNT');

  fireEvent.click(screen.getByRole('button', { name: 'Provider Revoke' }));

  await waitFor(() => {
    expect(mockedOAuthCredentialAdminService.revoke).toHaveBeenCalledWith('credential-1');
  });
  // After a successful revoke the backend response carries
  // providerRevokeSupported=false, which re-disables the Provider Revoke button
  // via the new capability-driven gating.
  await waitFor(() => {
    const button = screen.getByRole('button', { name: 'Provider Revoke' }) as HTMLButtonElement;
    expect(button.disabled).toBe(true);
  });

  confirmSpy.mockRestore();
});

test('surfaces revoke provider errors and reloads inventory', async () => {
  const confirmSpy = jest.spyOn(window, 'confirm').mockReturnValue(true);
  mockedOAuthCredentialAdminService.revoke.mockRejectedValue({
    response: {
      data: {
        message: 'OAUTH_REVOKE_FAILED: provider returned 500',
      },
    },
  });

  render(<OAuthCredentialAdminPage />);
  await screen.findByText('MAIL_ACCOUNT');

  fireEvent.click(screen.getByRole('button', { name: 'Provider Revoke' }));

  expect(await screen.findByText('OAUTH_REVOKE_FAILED: provider returned 500')).toBeTruthy();
  await waitFor(() => {
    expect(mockedOAuthCredentialAdminService.listCredentials).toHaveBeenCalledTimes(2);
  });

  confirmSpy.mockRestore();
});

test('configures CUSTOM revoke endpoint and replaces the row from the redacted response', async () => {
  mockedOAuthCredentialAdminService.listCredentials.mockResolvedValue([
    {
      ...credential,
      provider: 'CUSTOM',
      revokeEndpointConfigured: false,
      providerRevokeSupported: false,
      providerRevokeUnsupportedReason: 'Provider-side revoke endpoint is not configured for this CUSTOM credential',
    },
  ]);
  mockedOAuthCredentialAdminService.updateRevokeEndpoint.mockResolvedValue({
    ...credential,
    provider: 'CUSTOM',
    revokeEndpointConfigured: true,
    providerRevokeSupported: true,
    providerRevokeUnsupportedReason: null,
  });

  render(<OAuthCredentialAdminPage />);
  await screen.findByText('CUSTOM');

  fireEvent.click(screen.getByRole('button', { name: 'Configure Revoke Endpoint' }));
  expect(await screen.findByRole('dialog', { name: 'Configure CUSTOM Revoke Endpoint' })).toBeTruthy();

  fireEvent.change(screen.getByLabelText('Revoke endpoint'), {
    target: { value: 'https://custom.example/revoke' },
  });
  fireEvent.click(screen.getByRole('button', { name: 'Save Endpoint' }));

  await waitFor(() => {
    expect(mockedOAuthCredentialAdminService.updateRevokeEndpoint).toHaveBeenCalledWith(
      'credential-1',
      'https://custom.example/revoke'
    );
  });
  await waitForElementToBeRemoved(() => (
    screen.queryByRole('dialog', { name: 'Configure CUSTOM Revoke Endpoint' })
  ));
  await waitFor(() => {
    const button = screen.getByRole('button', { name: 'Provider Revoke' }) as HTMLButtonElement;
    expect(button.disabled).toBe(false);
  });
});

test('surfaces revoke endpoint update errors without closing the dialog', async () => {
  mockedOAuthCredentialAdminService.listCredentials.mockResolvedValue([
    {
      ...credential,
      provider: 'CUSTOM',
      revokeEndpointConfigured: false,
      providerRevokeSupported: false,
      providerRevokeUnsupportedReason: 'Provider-side revoke endpoint is not configured for this CUSTOM credential',
    },
  ]);
  mockedOAuthCredentialAdminService.updateRevokeEndpoint.mockRejectedValue({
    response: {
      data: {
        message: 'Revoke endpoint must be a valid absolute HTTPS URL',
      },
    },
  });

  render(<OAuthCredentialAdminPage />);
  await screen.findByText('CUSTOM');

  fireEvent.click(screen.getByRole('button', { name: 'Configure Revoke Endpoint' }));
  fireEvent.change(screen.getByLabelText('Revoke endpoint'), {
    target: { value: 'http://custom.example/revoke' },
  });
  fireEvent.click(screen.getByRole('button', { name: 'Save Endpoint' }));

  expect(await screen.findByText('Revoke endpoint must be a valid absolute HTTPS URL')).toBeTruthy();
  expect(screen.getByRole('dialog', { name: 'Configure CUSTOM Revoke Endpoint' })).toBeTruthy();
});
