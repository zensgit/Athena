# Phase 64: Auth Recovery Observability - Development

## Date
2026-02-18

## Background
- Auth/session recovery paths are stable but hard to diagnose quickly when operators report:
  - unexpected redirect to login
  - callback loops
  - route fallback behavior
- Existing logs were fragmented across `api`, `authService`, `index`, and `PrivateRoute`.

## Goal
1. Add opt-in, structured auth-recovery debug logs without changing production behavior.
2. Provide consistent event names across bootstrap, refresh, retry, redirect, and route fallback.
3. Ensure no sensitive tokens are leaked in debug payloads.

## Changes

### 1) New debug utility
- Added `ecm-frontend/src/utils/authRecoveryDebug.ts`
  - `isAuthRecoveryDebugEnabled()`
    - enabled by any of:
      - `REACT_APP_DEBUG_RECOVERY=1`
      - `localStorage.ecm_debug_recovery=1`
      - URL query `debugRecovery=1` or `authRecoveryDebug=1`
  - `logAuthRecoveryEvent(event, payload?)`
    - emits `console.info` only when debug enabled
    - recursively redacts sensitive fields (`token`, `Authorization`, `refreshToken`, etc.)

### 2) Auth/API lifecycle instrumentation
- `ecm-frontend/src/index.tsx`
  - bootstrap start / retry / success / failure events
- `ecm-frontend/src/services/authService.ts`
  - login start, logout start/done
  - refresh bypass/skipped/success/failed with status and `shouldLogout` decision
- `ecm-frontend/src/services/api.ts`
  - request refresh failure
  - 401 received / retry start / retry success / retry failed / redirect
  - session-expired marking and redirect decision
- `ecm-frontend/src/components/auth/PrivateRoute.tsx`
  - stale login marker cleanup
  - redirect pause due to cooldown/limit
  - auto redirect start/failure
- `ecm-frontend/src/App.tsx`
  - wildcard route fallback redirect event (`route.fallback.redirect`)

## Non-Functional Notes
- Default behavior unchanged: logs are silent unless debug is explicitly enabled.
- No API contract changes.
- Sensitive auth data is redacted before logging.
