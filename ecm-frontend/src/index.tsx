import React from 'react';
import ReactDOM from 'react-dom/client';
import './index.css';
import App from './App';
import authService from 'services/authService';
import { store } from './store';
import { setSession } from 'store/slices/authSlice';

const allowInsecureCrypto = process.env.REACT_APP_INSECURE_CRYPTO_OK === 'true';
if (allowInsecureCrypto && (!window.crypto || !window.crypto.getRandomValues)) {
  const insecureGetRandomValues = (arr: Uint8Array) => {
    for (let i = 0; i < arr.length; i += 1) {
      arr[i] = Math.floor(Math.random() * 256);
    }
    return arr;
  };
  window.crypto = { ...(window.crypto || {}), getRandomValues: insecureGetRandomValues } as Crypto;
  console.warn('Using insecure crypto fallback for Keycloak dev login.');
}

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
    const canUsePkce = !!(window.crypto && window.crypto.subtle);
    if (!canUsePkce) {
      console.warn('PKCE disabled: Web Crypto API is unavailable in this context.');
    }
    const authenticated = await authService.init({
      onLoad: 'check-sso',
      pkceMethod: canUsePkce ? 'S256' : undefined,
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
