import React from 'react';
import ReactDOM from 'react-dom/client';
import './index.css';
import App from './App';
import authService from 'services/authService';
import { store } from './store';
import { setSession } from 'store/slices/authSlice';

const root = ReactDOM.createRoot(document.getElementById('root') as HTMLElement);
const LOGIN_IN_PROGRESS_KEY = 'ecm_kc_login_in_progress';

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
