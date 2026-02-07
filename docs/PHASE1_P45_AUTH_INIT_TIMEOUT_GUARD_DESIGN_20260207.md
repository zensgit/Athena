# Phase 1 P45 - Auth Init Timeout Guard Design (2026-02-07)

## Problem
- In unstable environments (network hiccup, Keycloak endpoint slow/unreachable), `authService.init(check-sso)` may stall for too long.
- Since app rendering waits for auth init completion, a stalled init can present as a blank page.

## Goal
- Ensure frontend always exits auth bootstrap state in bounded time.
- Prevent startup blank-screen symptom by rendering a deterministic boot shell immediately.

## Scope
- Frontend only:
  - `ecm-frontend/src/index.tsx`
  - `ecm-frontend/src/services/authBootstrap.ts`
  - tests and docs

## Design

### 1) Timeout wrapper for auth init
- Added `withAuthInitTimeout(task, timeoutMs)` in `ecm-frontend/src/services/authBootstrap.ts`.
- Added explicit timeout error type `AuthInitTimeoutError`.
- `Promise.race` ensures bounded wait; timer is always cleared in `finally`.

### 2) Immediate boot rendering
- `index.tsx` now renders a lightweight `Initializing sign-in...` boot shell before auth init begins.
- This avoids white screen during startup and provides clear transitional feedback.

### 3) Auth init bounded by timeout
- Added `AUTH_INIT_TIMEOUT_MS` (default `15000`, env-overridable by `REACT_APP_AUTH_INIT_TIMEOUT_MS`).
- Wrapped `authService.init(...)` with `withAuthInitTimeout(...)`.
- On timeout or init error:
  - clear login progress markers,
  - dispatch unauthenticated session,
  - render main app so route guards can recover user to login.

### 4) Existing auth marker cleanup aligned
- Startup cleanup now removes both:
  - `ecm_kc_login_in_progress`
  - `ecm_kc_login_in_progress_started_at`

## Compatibility
- No backend/API schema changes.
- Default auth flow unchanged when Keycloak is healthy.
- New behavior activates only under stalled/error conditions and improves recovery.
