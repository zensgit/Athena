# Phase 106: Startup Recovery Reason Split for Login Handoff

## Date
2026-02-24

## Background
- Startup blank-screen fallback (`public/index.html`) and AppErrorBoundary both redirected with `reason=app_recovery`.
- Login status card could not distinguish:
  - runtime crash recovery
  - startup timeout/blank recovery

## Goals
1. Introduce a dedicated startup recovery reason/status.
2. Preserve existing AppErrorBoundary `app_recovery` semantics.
3. Keep mocked startup fallback E2E deterministic.

## Changes

### 1) New auth init status constant
- `ecm-frontend/src/constants/auth.ts`
  - added `AUTH_INIT_STATUS_STARTUP_RECOVERY = 'startup_recovery'`.

### 2) Login notice split
- `ecm-frontend/src/components/auth/Login.tsx`
  - imported `AUTH_INIT_STATUS_STARTUP_RECOVERY`.
  - added startup-specific notice branch:
    - title: `Recovered from startup timeout`
    - detail: `App startup took too long and switched to sign-in recovery. Please sign in again.`
  - branch resolves from:
    - `sessionStorage[ecm_auth_init_status]`
    - local redirect reason fallback
    - `?reason=startup_recovery`

### 3) Bootstrap fallback handoff update
- `ecm-frontend/public/index.html`
  - Back-to-Login action now:
    - writes `sessionStorage['ecm_auth_init_status']='startup_recovery'` (best effort)
    - redirects to `/login?reason=startup_recovery`

### 4) Test updates
- `ecm-frontend/src/components/auth/Login.test.tsx`
  - added tests for startup recovery from session status and query reason fallback.
- `ecm-frontend/e2e/bootstrap-startup-fallback.mock.spec.ts`
  - updated expected URL to `reason=startup_recovery`
  - updated expected login notice title to startup-specific text.

## Impact
- No backend/API contract change.
- Improves operator-facing diagnosis by separating startup recovery from runtime crash recovery on login handoff.
