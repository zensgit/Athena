import Keycloak from 'keycloak-js';

const LOCAL_DEV_KEYCLOAK_URL = 'http://localhost:8180';

const isLocalhost = (hostname: string): boolean => (
  hostname === 'localhost'
  || hostname === '127.0.0.1'
  || hostname === '::1'
  || hostname === '[::1]'
);

export const defaultKeycloakUrl = (location: Pick<Location, 'hostname' | 'origin'> | undefined = window.location): string => {
  if (!location || isLocalhost(location.hostname)) {
    return LOCAL_DEV_KEYCLOAK_URL;
  }

  return location.origin;
};

export const keycloakConfig = {
  url: process.env.REACT_APP_KEYCLOAK_URL || defaultKeycloakUrl(),
  realm: process.env.REACT_APP_KEYCLOAK_REALM || 'ecm',
  clientId: process.env.REACT_APP_KEYCLOAK_CLIENT_ID || 'unified-portal',
};

const keycloak = new (Keycloak as any)(keycloakConfig);

export default keycloak;
