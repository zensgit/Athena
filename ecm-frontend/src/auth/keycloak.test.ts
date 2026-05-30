import { defaultKeycloakUrl } from './keycloak';

describe('defaultKeycloakUrl', () => {
  it('keeps the local development Keycloak default on localhost', () => {
    expect(defaultKeycloakUrl({ hostname: 'localhost', origin: 'http://localhost:3000' })).toBe('http://localhost:8180');
    expect(defaultKeycloakUrl({ hostname: '127.0.0.1', origin: 'http://127.0.0.1:3000' })).toBe('http://localhost:8180');
  });

  it('uses same-origin Keycloak for deployed non-localhost hosts', () => {
    expect(defaultKeycloakUrl({ hostname: '23.254.236.11', origin: 'https://23.254.236.11' })).toBe('https://23.254.236.11');
    expect(defaultKeycloakUrl({ hostname: 'staging.example.test', origin: 'https://staging.example.test' })).toBe('https://staging.example.test');
  });
});
