# Phase 1 P49 - Auth Redirect Cooldown Guard Design (2026-02-07)

## Background
- P48 added redirect-failure feedback, but automatic redirect may still be retried too aggressively right after failure.
- This can cause repeated redirect attempts and confusing loops in unstable auth environments.

## Goal
- Add short cooldown after auto redirect failure:
  - pause further automatic redirect attempts,
  - keep explicit manual sign-in always available.

## Scope
- Frontend only:
  - `ecm-frontend/src/components/auth/PrivateRoute.tsx`
  - `ecm-frontend/src/components/auth/Login.tsx`
  - `ecm-frontend/src/constants/auth.ts`
  - unit tests

## Design Changes

### 1) Redirect failure cooldown keys
- Added constants:
  - `AUTH_REDIRECT_FAILURE_COUNT_KEY`
  - `AUTH_REDIRECT_LAST_FAILURE_AT_KEY`
  - `AUTH_REDIRECT_FAILURE_COOLDOWN_MS` (`30_000`)

### 2) PrivateRoute cooldown behavior
- On automatic redirect failure:
  - set `AUTH_INIT_STATUS_KEY=redirect_failed`,
  - increment failure count,
  - record last failure timestamp,
  - clear login-in-progress markers.
- Before auto-redirect:
  - if recent redirect failure is within cooldown window, skip auto-redirect and keep login guidance path.
- On authenticated session:
  - clear cooldown markers.

### 3) Login behavior
- On manual click `Sign in with Keycloak`:
  - clear cooldown markers to allow immediate user-triggered retry.
- Redirect-failure warning now optionally includes remaining cooldown seconds.

### 4) Test updates
- `PrivateRoute` test:
  - verifies failure markers are written,
  - verifies auto redirect is skipped during cooldown,
  - verifies markers are cleared after successful auth.
- `Login` test:
  - verifies manual sign-in clears cooldown markers.

## Compatibility
- No backend/API changes.
- Only sessionStorage-based runtime behavior updated.
