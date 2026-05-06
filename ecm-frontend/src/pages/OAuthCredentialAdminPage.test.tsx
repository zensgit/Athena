import React from 'react';
import { fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import OAuthCredentialAdminPage from './OAuthCredentialAdminPage';
import oauthCredentialAdminService from 'services/oauthCredentialAdminService';

jest.mock('services/oauthCredentialAdminService', () => ({
  __esModule: true,
  default: {
    listCredentials: jest.fn(),
    requireReauth: jest.fn(),
  },
}));

const mockedOAuthCredentialAdminService = oauthCredentialAdminService as jest.Mocked<typeof oauthCredentialAdminService>;

const credential = {
  id: 'credential-1',
  ownerType: 'MAIL_ACCOUNT',
  ownerId: 'aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee',
  provider: 'GOOGLE',
  tokenEndpointConfigured: true,
  tenantIdConfigured: false,
  scopeConfigured: true,
  credentialKeyConfigured: true,
  accessTokenStored: true,
  refreshTokenStored: true,
  connected: true,
  tokenExpiresAt: '2026-05-06T10:15:30Z',
  createdAt: '2026-05-01T09:00:00Z',
  updatedAt: '2026-05-06T10:00:00Z',
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

test('does not require reauthorization when confirmation is cancelled', async () => {
  const confirmSpy = jest.spyOn(window, 'confirm').mockReturnValue(false);

  render(<OAuthCredentialAdminPage />);
  await screen.findByText('MAIL_ACCOUNT');

  fireEvent.click(screen.getByRole('button', { name: 'Require Reauth' }));

  expect(mockedOAuthCredentialAdminService.requireReauth).not.toHaveBeenCalled();
  confirmSpy.mockRestore();
});
