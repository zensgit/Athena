import Keycloak from 'keycloak-js';

const config = {
  url: process.env.REACT_APP_KEYCLOAK_URL || 'http://localhost:8180',
  realm: process.env.REACT_APP_KEYCLOAK_REALM || 'ecm',
  clientId: process.env.REACT_APP_KEYCLOAK_CLIENT_ID || 'ecm-api',
};

const keycloak = new (Keycloak as any)(config);

export default keycloak;
