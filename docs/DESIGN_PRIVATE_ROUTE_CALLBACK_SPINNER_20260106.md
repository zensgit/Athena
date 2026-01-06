# Design: PrivateRoute Callback Spinner (2026-01-06)

## Goal
- Verify the signing-in spinner renders when Keycloak callback parameters are present.

## Approach
- Seed `window.location` with `code`/`state` query parameters.
- Assert the "Signing you in..." message while unauthenticated.

## Files
- ecm-frontend/src/components/auth/PrivateRoute.test.tsx
