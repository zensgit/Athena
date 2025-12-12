import React from 'react';
import ReactDOM from 'react-dom/client';
import './index.css';
import App from './App';
import { keycloak } from 'services/authService';

const root = ReactDOM.createRoot(document.getElementById('root') as HTMLElement);

const renderApp = () => {
  root.render(
    <React.StrictMode>
      <App />
    </React.StrictMode>
  );
};

// Initialize Keycloak before rendering
keycloak
  .init({
    onLoad: 'check-sso',
    pkceMethod: 'S256',
    checkLoginIframe: false, // Disable iframe check to prevent CORS issues
  })
  .then((authenticated: boolean) => {
    renderApp();
    if (authenticated) {
      // Only set up token refresh if authenticated
      setInterval(() => {
        keycloak.updateToken(30).catch(() => {
          console.warn('Token refresh failed, user may need to re-login');
        });
      }, 20000);
    }
  })
  .catch((error: Error) => {
    console.error('Keycloak init error:', error);
    renderApp();
  });
