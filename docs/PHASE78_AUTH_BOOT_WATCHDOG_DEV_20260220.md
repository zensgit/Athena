# Phase 78: Auth Boot Startup Watchdog and Recovery Actions

## Date
2026-02-20

## Background
- During app bootstrap, `index.tsx` renders a loading state while `authService.init()` executes.
- If auth bootstrap is delayed by IdP/network/runtime issues, users can remain on startup spinner without actionable recovery controls.

## Goal
1. Add an app-level startup watchdog banner for auth booting.
2. Provide explicit recovery actions at boot stage.
3. Keep bootstrap telemetry coherent when watchdog recovery is chosen.

## Changes

### 1) New auth boot watchdog component
- Files:
  - `ecm-frontend/src/components/auth/AuthBootingScreen.tsx`
  - `ecm-frontend/src/components/auth/AuthBootingScreen.test.tsx`
- Added:
  - Spinner + `Initializing sign-in...` baseline UI
  - Watchdog timeout transition (default 12s)
  - Watchdog alert with actions:
    - `Reload`
    - `Continue to login`
- Added test IDs:
  - `auth-booting-watchdog-alert`
  - `auth-booting-watchdog-reload`
  - `auth-booting-watchdog-continue-login`

### 2) Bootstrap integration in `index.tsx`
- File: `ecm-frontend/src/index.tsx`
- Replaced inline static loading markup with `AuthBootingScreen`.
- Added configurable timeout:
  - `REACT_APP_AUTH_BOOT_WATCHDOG_MS` (default `12000ms`)
- Added watchdog recovery path:
  - `Continue to login` dispatches unauthenticated session and renders app immediately (no hard reload required).
  - Sets auth init status to timeout for login-page guidance consistency.
- Added bootstrap race guard:
  - when watchdog recovery has already switched UI, late auth init success/error/fatal results are ignored to avoid overriding the recovered login state.
- Added debug events:
  - `auth.bootstrap.watchdog.triggered`
  - `auth.bootstrap.watchdog.reload`
  - `auth.bootstrap.watchdog.continue_to_login`
  - `auth.bootstrap.skipped_after_watchdog_recovery`

### 3) Config parsing hardening reuse
- File: `ecm-frontend/src/index.tsx`
- Updated auth init timeout/attempt/retry-delay env parsing to use `resolvePositiveIntEnv` for positive-integer normalization.

## Non-Functional Notes
- No backend contract changes.
- Startup fallback now has an explicit in-app recovery route before bootstrap completion.
