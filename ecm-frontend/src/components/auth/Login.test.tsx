import React from 'react';
import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import Login from './Login';
import authService from 'services/authService';

jest.mock('services/authService', () => ({
  __esModule: true,
  default: {
    login: jest.fn().mockResolvedValue(undefined),
  },
}));

const authServiceMock = authService as jest.Mocked<typeof authService>;

beforeEach(() => {
  jest.clearAllMocks();
  sessionStorage.clear();
});

test('clears stale login markers on mount', () => {
  sessionStorage.setItem('ecm_kc_login_in_progress', '1');
  sessionStorage.setItem('ecm_kc_login_in_progress_started_at', '123');

  render(<Login />);

  expect(sessionStorage.getItem('ecm_kc_login_in_progress')).toBeNull();
  expect(sessionStorage.getItem('ecm_kc_login_in_progress_started_at')).toBeNull();
});

test('shows error when login fails', async () => {
  authServiceMock.login.mockRejectedValueOnce(new Error('Web Crypto API is not available.'));

  render(<Login />);
  fireEvent.click(screen.getByRole('button', { name: /sign in with keycloak/i }));

  expect(await screen.findByText(/sign-in failed/i)).toBeTruthy();
  expect(screen.getByText(/web crypto api is not available/i)).toBeTruthy();

  await waitFor(() => {
    const button = screen.getByRole('button', { name: /sign in with keycloak/i }) as HTMLButtonElement;
    expect(button.disabled).toBe(false);
  });
});
