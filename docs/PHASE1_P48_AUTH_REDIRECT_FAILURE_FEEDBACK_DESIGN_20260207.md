# Phase 1 P48 - Auth Redirect Failure Feedback Design (2026-02-07)

## Background
- P45-P47 improved auth init timeout and retry behavior.
- Remaining UX gap: when automatic Keycloak redirect from protected route fails, users are sent back to login without clear reason.

## Goal
- Expose automatic redirect failure state on login screen.
- Reduce ambiguity between:
  - normal unauthenticated access,
  - explicit redirect/bootstrap failures.

## Scope
- Frontend only:
  - `ecm-frontend/src/components/auth/PrivateRoute.tsx`
  - `ecm-frontend/src/components/auth/Login.tsx`
  - `ecm-frontend/src/constants/auth.ts`
  - auth component tests

## Design

### 1) New status constant
- Added `AUTH_INIT_STATUS_REDIRECT_FAILED = 'redirect_failed'`.

### 2) Capture redirect failure in route guard
- In `PrivateRoute` auto-login path:
  - on `authService.login(...)` failure, set `AUTH_INIT_STATUS_KEY` to `redirect_failed`,
  - clear in-progress markers.

### 3) Login page warning message
- `Login` reads `AUTH_INIT_STATUS_KEY` on mount:
  - new warning for `redirect_failed`:
    - `Automatic sign-in redirect failed. Click Sign in with Keycloak to retry.`
- key is cleared after read to avoid stale warnings.

### 4) Test updates
- `PrivateRoute.test.tsx`: assert `ecm_auth_init_status=redirect_failed` after auto redirect failure.
- `Login.test.tsx`: verify redirect-failure warning rendering.

## Compatibility
- No backend changes.
- Session-scoped signal only; no persistent client schema changes.
