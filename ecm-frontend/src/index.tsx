import React from 'react';
import ReactDOM from 'react-dom/client';
import './index.css';
import App from './App';
import authService from 'services/authService';
import { store } from './store';
import { setSession } from 'store/slices/authSlice';

const root = ReactDOM.createRoot(document.getElementById('root') as HTMLElement);
const LOGIN_IN_PROGRESS_KEY = 'ecm_kc_login_in_progress';
const KEYCLOAK_CALLBACK_KEYS = ['code', 'state', 'session_state', 'iss'] as const;

const stripKeycloakCallbackParams = () => {
  const url = new URL(window.location.href);
  KEYCLOAK_CALLBACK_KEYS.forEach((key) => url.searchParams.delete(key));

  const rawHash = url.hash.startsWith('#') ? url.hash.slice(1) : '';
  if (rawHash && KEYCLOAK_CALLBACK_KEYS.some((key) => rawHash.includes(`${key}=`))) {
    const hashParams = new URLSearchParams(rawHash);
    KEYCLOAK_CALLBACK_KEYS.forEach((key) => hashParams.delete(key));
    const cleaned = hashParams.toString();
    url.hash = cleaned ? `#${cleaned}` : '';
  }

  const cleanedUrl = url.toString();
  if (cleanedUrl !== window.location.href) {
    window.history.replaceState({}, '', cleanedUrl);
  }
};

const renderApp = () => {
  root.render(
    <React.StrictMode>
      <App />
    </React.StrictMode>
  );
};

const initAuth = async () => {
  try {
    const authenticated = await authService.init({
      onLoad: 'check-sso',
      pkceMethod: 'S256',
      checkLoginIframe: false, // Disable iframe check to prevent CORS issues
    });
    sessionStorage.removeItem(LOGIN_IN_PROGRESS_KEY);
    store.dispatch(
      setSession({
        user: authenticated ? authService.getCurrentUser() : null,
        token: authenticated ? authService.getToken() || null : null,
        isAuthenticated: authenticated,
      })
    );
    stripKeycloakCallbackParams();
    renderApp();
    if (authenticated) {
      authService.startTokenRefresh();
    }
  } catch (error) {
    sessionStorage.removeItem(LOGIN_IN_PROGRESS_KEY);
    console.error('Keycloak init error:', error);
    renderApp();
  }
};

// Initialize Keycloak before rendering
void initAuth();
