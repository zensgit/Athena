import React from 'react';
import { render, screen, waitFor } from '@testing-library/react';
import { Provider } from 'react-redux';
import { configureStore } from '@reduxjs/toolkit';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import authReducer, { setSession } from 'store/slices/authSlice';
import nodeReducer from 'store/slices/nodeSlice';
import uiReducer from 'store/slices/uiSlice';
import PrivateRoute from './PrivateRoute';
import authService from 'services/authService';

jest.mock('services/authService', () => ({
  __esModule: true,
  default: {
    isAuthenticated: jest.fn(),
    login: jest.fn().mockResolvedValue(undefined),
    logout: jest.fn().mockResolvedValue(undefined),
    getCurrentUser: jest.fn(),
    getToken: jest.fn(),
    getTokenParsed: jest.fn(),
    refreshToken: jest.fn().mockResolvedValue(undefined),
    startTokenRefresh: jest.fn(),
    clearSession: jest.fn(),
  },
}));

const authServiceMock = authService as jest.Mocked<typeof authService>;

const renderPrivateRoute = ({
  isAuthenticated,
  roles,
  requiredRoles,
  initialEntries = ['/protected'],
}: {
  isAuthenticated: boolean;
  roles?: string[];
  requiredRoles?: string[];
  initialEntries?: string[];
}) => {
  const store = configureStore({
    reducer: {
      auth: authReducer,
      node: nodeReducer,
      ui: uiReducer,
    },
  });

  store.dispatch(
    setSession({
      user: roles
        ? {
            id: 'user-id',
            username: 'tester',
            email: 'tester@example.com',
            roles,
          }
        : null,
      token: isAuthenticated ? 'token' : null,
      isAuthenticated,
    })
  );

  render(
    <Provider store={store}>
      <MemoryRouter
        initialEntries={initialEntries}
        future={{ v7_startTransition: true, v7_relativeSplatPath: true }}
      >
        <Routes>
          <Route path="/login" element={<div>Login Page</div>} />
          <Route path="/unauthorized" element={<div>Unauthorized Page</div>} />
          <Route
            path="/protected"
            element={
              <PrivateRoute requiredRoles={requiredRoles}>
                <div>Protected Content</div>
              </PrivateRoute>
            }
          />
        </Routes>
      </MemoryRouter>
    </Provider>
  );
};

beforeEach(() => {
  authServiceMock.isAuthenticated.mockReturnValue(false);
  authServiceMock.getCurrentUser.mockReturnValue(null);
  authServiceMock.getToken.mockReturnValue(undefined);
  window.history.pushState({}, 'Test', '/');
  sessionStorage.clear();
  localStorage.clear();
});

afterEach(() => {
  jest.clearAllMocks();
});

test('redirects unauthenticated users to login', async () => {
  renderPrivateRoute({ isAuthenticated: false });

  expect(await screen.findByText('Login Page')).toBeTruthy();
});

test('renders children for authenticated users', () => {
  renderPrivateRoute({ isAuthenticated: true, roles: ['ROLE_VIEWER'] });

  expect(screen.getByText('Protected Content')).toBeTruthy();
});

test('redirects users missing required roles', async () => {
  renderPrivateRoute({
    isAuthenticated: true,
    roles: ['ROLE_VIEWER'],
    requiredRoles: ['ROLE_ADMIN'],
  });

  expect(await screen.findByText('Unauthorized Page')).toBeTruthy();
});

test('shows spinner when Keycloak callback params are present', async () => {
  window.history.pushState({}, 'Callback', '/protected?code=abc&state=def');

  renderPrivateRoute({
    isAuthenticated: false,
    initialEntries: ['/protected?code=abc&state=def'],
  });

  expect(await screen.findByText('Signing you in...')).toBeTruthy();
});

test('shows spinner when login is already in progress', async () => {
  sessionStorage.setItem('ecm_kc_login_in_progress', '1');
  sessionStorage.setItem('ecm_kc_login_in_progress_started_at', String(Date.now()));

  renderPrivateRoute({ isAuthenticated: false });

  expect(await screen.findByText('Signing you in...')).toBeTruthy();
});

test('clears stale in-progress marker and redirects to login', async () => {
  sessionStorage.setItem('ecm_kc_login_in_progress', '1');
  sessionStorage.setItem('ecm_kc_login_in_progress_started_at', String(Date.now() - 120_000));

  renderPrivateRoute({ isAuthenticated: false });

  expect(await screen.findByText('Login Page')).toBeTruthy();
  expect(sessionStorage.getItem('ecm_kc_login_in_progress')).toBeNull();
  expect(sessionStorage.getItem('ecm_kc_login_in_progress_started_at')).toBeNull();
});

test('clears markers when auto login request fails', async () => {
  authServiceMock.login.mockRejectedValueOnce(new Error('login failed'));

  renderPrivateRoute({ isAuthenticated: false });

  expect(await screen.findByText('Login Page')).toBeTruthy();
  await waitFor(() => {
    expect(sessionStorage.getItem('ecm_kc_login_in_progress')).toBeNull();
    expect(sessionStorage.getItem('ecm_kc_login_in_progress_started_at')).toBeNull();
    expect(sessionStorage.getItem('ecm_auth_init_status')).toBe('redirect_failed');
  });
});
