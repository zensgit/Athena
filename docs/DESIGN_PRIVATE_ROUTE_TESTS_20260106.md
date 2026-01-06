# Design: PrivateRoute Tests (2026-01-06)

## Goal
- Cover authentication and role-gate behavior in `PrivateRoute`.

## Approach
- Mock `authService` to avoid Keycloak side effects.
- Validate unauthenticated redirects to `/login` and role failures to `/unauthorized`.
- Confirm authenticated users can render protected content.
- Cover Keycloak callback parameters to assert the signing-in spinner state.
- Cover the login-in-progress session flag to assert the same spinner state.

## Files
- ecm-frontend/src/components/auth/PrivateRoute.test.tsx
