# Phase 1 P50 - Auth Redirect Failure Cap Design (2026-02-07)

## Background
- P49 introduced short cooldown after automatic redirect failure.
- Remaining risk: repeated failures across multiple cooldown windows can still keep auto-redirect churning.

## Goal
- Add a cap on automatic redirect attempts after failures.
- After reaching cap, stop auto-redirect and require explicit manual sign-in.

## Scope
- Frontend only:
  - `ecm-frontend/src/constants/auth.ts`
  - `ecm-frontend/src/components/auth/PrivateRoute.tsx`
  - `ecm-frontend/src/components/auth/Login.tsx`
  - tests

## Design Changes

### 1) Failure cap constant
- Added `AUTH_REDIRECT_MAX_AUTO_ATTEMPTS = 2`.

### 2) PrivateRoute logic
- Derive `hasReachedAutoRedirectLimit` from failure count.
- Pause auto-redirect when:
  - recent failure cooldown is active, or
  - failure count reached cap.
- Continue setting `redirect_failed` status for login page guidance.

### 3) Login guidance
- If `redirect_failed` and failure count reached cap:
  - show stronger warning:
  - `Automatic sign-in is paused after repeated failures. Click Sign in with Keycloak to retry.`
- Manual sign-in continues to clear failure markers.

### 4) Test updates
- `PrivateRoute`:
  - new test verifies no auto-redirect when cap reached.
- `Login`:
  - new test verifies paused-message rendering at cap.

## Compatibility
- No backend/API change.
- Session-only behavior; no persistent data migration.
