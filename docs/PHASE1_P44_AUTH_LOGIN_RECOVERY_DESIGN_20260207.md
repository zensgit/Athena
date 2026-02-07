# Phase 1 P44 - Auth Login Recovery Guard Design (2026-02-07)

## Background
- During recent UI runs, users still observed occasional white screen / login-loop behavior when auth bootstrap or redirect failed in unstable browser contexts.
- Existing flow already had callback cleanup and spinner fallback, but lacked:
  - robust handling when `authService.login()` does not return a promise-like object,
  - stale `login_in_progress` timeout recovery,
  - explicit user-facing error on login button failures,
  - safe crypto fallback installation that cannot crash app bootstrap.

## Goals
- Eliminate silent login failure states that can lead to blank or stuck screens.
- Ensure auth flow can recover to `/login` instead of hanging.
- Keep changes minimal and backward compatible.

## Scope
- Frontend only:
  - `ecm-frontend/src/components/auth/Login.tsx`
  - `ecm-frontend/src/components/auth/PrivateRoute.tsx`
  - `ecm-frontend/src/index.tsx`
  - related tests

## Design Changes

### 1) Shared auth keys/constants
- Added `ecm-frontend/src/constants/auth.ts`:
  - `LOGIN_IN_PROGRESS_KEY`
  - `LOGIN_IN_PROGRESS_STARTED_AT_KEY`
  - `LOGIN_IN_PROGRESS_TIMEOUT_MS` (`45_000`)

### 2) PrivateRoute stale-session recovery
- On each guard pass:
  - detect stale in-progress markers via timeout,
  - clear stale markers,
  - clear markers after successful authentication,
  - keep spinner only for active callback / non-stale in-progress state.
- Auto-login trigger now stores start timestamp.
- Auto-login call wrapped with `Promise.resolve(...)` before `.catch(...)` to tolerate non-promise return values safely.
- On login redirect failure, markers are cleared and route can recover to normal login page.

### 3) Login page failure visibility
- Login button now:
  - tracks submitting state,
  - catches login errors,
  - shows explicit `Alert` with reason,
  - clears stale in-progress markers on mount.

### 4) Bootstrap crypto fallback hardening
- In `index.tsx`, insecure crypto fallback is installed via guarded helper:
  - no direct unsafe assignment that can throw at top-level,
  - uses `Object.defineProperty` with try/catch,
  - logs warning instead of crashing app bootstrap.

## Compatibility / Risk
- No API contract changes.
- No backend behavior changes.
- Existing auth and E2E bypass behavior remains intact.
- Risk is limited to auth-entry UX and mitigated by unit + E2E regression.
