# Verification Report (2025-12-20 11:19:30)

## Scope
- Command: `./scripts/verify.sh --no-restart`
- Base URLs:
  - Frontend: http://localhost:5500
  - API: http://localhost:7700
  - Keycloak: http://localhost:8180 (realm: ecm)

## Steps Executed
1. Wait for Keycloak and API health
2. ClamAV health check
3. Create test users in Keycloak
4. Obtain access token
5. Run API smoke tests
6. Frontend build
7. WOPI preview + audit verification
8. E2E Playwright suite

## Results
- Status: PASSED
- Passed: 8
- Failed: 0
- Skipped: 1 (service restart)

## Logs
- Directory: `tmp/`
- Prefix: `20251220_111930_*`
