# Design: PrivateRoute Login-In-Progress Spinner (2026-01-06)

## Goal
- Validate the spinner state when a login attempt is already in progress.

## Approach
- Seed session storage with `ecm_kc_login_in_progress`.
- Assert the "Signing you in..." message while unauthenticated.

## Files
- ecm-frontend/src/components/auth/PrivateRoute.test.tsx
