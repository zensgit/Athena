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
  localStorage.clear();
  window.history.pushState({}, '', '/login');
});

test('clears stale login markers on mount', () => {
  sessionStorage.setItem('ecm_kc_login_in_progress', '1');
  sessionStorage.setItem('ecm_kc_login_in_progress_started_at', '123');
  sessionStorage.setItem('ecm_auth_init_status', 'error');

  render(<Login />);

  expect(sessionStorage.getItem('ecm_kc_login_in_progress')).toBeNull();
  expect(sessionStorage.getItem('ecm_kc_login_in_progress_started_at')).toBeNull();
  expect(sessionStorage.getItem('ecm_auth_init_status')).toBeNull();
});

test('shows timeout warning when auth bootstrap timed out', async () => {
  sessionStorage.setItem('ecm_auth_init_status', 'timeout');

  render(<Login />);

  expect(await screen.findByText(/sign-in initialization timed out/i)).toBeTruthy();
});

test('shows generic init warning when auth bootstrap failed', async () => {
  sessionStorage.setItem('ecm_auth_init_status', 'error');

  render(<Login />);

  expect(await screen.findByText(/sign-in initialization failed/i)).toBeTruthy();
});

test('shows session expired message when api marks auth expiry', async () => {
  sessionStorage.setItem('ecm_auth_init_status', 'session_expired');

  render(<Login />);

  expect(await screen.findByText(/your session expired/i)).toBeTruthy();
});

test('shows session expired message from redirect reason fallback', async () => {
  localStorage.setItem('ecm_auth_redirect_reason', 'session_expired');

  render(<Login />);

  expect(await screen.findByText(/your session expired/i)).toBeTruthy();
});

test('shows session expired message from login reason query param fallback', async () => {
  window.history.pushState({}, '', '/login?reason=session_expired');

  render(<Login />);

  expect(await screen.findByText(/your session expired/i)).toBeTruthy();
});

test('shows redirect warning when automatic sign-in redirect fails', async () => {
  sessionStorage.setItem('ecm_auth_init_status', 'redirect_failed');
  sessionStorage.setItem('ecm_auth_redirect_last_failure_at', String(Date.now()));

  render(<Login />);

  expect(await screen.findByText(/automatic sign-in redirect failed/i)).toBeTruthy();
});

test('shows paused message when auto redirect failure count reaches cap', async () => {
  sessionStorage.setItem('ecm_auth_init_status', 'redirect_failed');
  sessionStorage.setItem('ecm_auth_redirect_failure_count', '2');
  sessionStorage.setItem('ecm_auth_redirect_last_failure_at', String(Date.now()));

  render(<Login />);

  expect(await screen.findByText(/automatic sign-in is paused after repeated failures/i)).toBeTruthy();
  expect(screen.getByText(/2\/2/)).toBeTruthy();
  expect(screen.getByText(/auto retry resumes in/i)).toBeTruthy();
});

test('manual sign-in clears redirect failure cooldown markers', async () => {
  sessionStorage.setItem('ecm_auth_init_status', 'redirect_failed');
  sessionStorage.setItem('ecm_auth_redirect_failure_count', '2');
  sessionStorage.setItem('ecm_auth_redirect_last_failure_at', String(Date.now()));

  render(<Login />);
  fireEvent.click(screen.getByRole('button', { name: /sign in with keycloak/i }));

  await waitFor(() => {
    expect(sessionStorage.getItem('ecm_auth_redirect_failure_count')).toBeNull();
  });
  expect(sessionStorage.getItem('ecm_auth_redirect_last_failure_at')).toBeNull();
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
