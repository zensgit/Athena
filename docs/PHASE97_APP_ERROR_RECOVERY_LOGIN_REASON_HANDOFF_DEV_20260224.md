# Phase 97: App Error Recovery Login-Reason Handoff

## Date
2026-02-24

## Background
- Existing `AppErrorBoundary` already prevented persistent white-screen by exposing `Reload` and `Back to Login`.
- However, returning to login did not provide a dedicated recovery reason in the login status card, making operator/user diagnosis weak after runtime crash recovery.

## Goals
1. Persist a structured recovery reason when navigating from fatal fallback to login.
2. Show explicit login status card copy for this recovery path.
3. Keep behavior storage-safe in restricted browser contexts.

## Changes

### 1) Recovery reason status constant
- File: `ecm-frontend/src/constants/auth.ts`
- Added:
  - `AUTH_INIT_STATUS_APP_RECOVERY = 'app_recovery'`

### 2) Error boundary -> login recovery handoff
- File: `ecm-frontend/src/components/layout/AppErrorBoundary.tsx`
- `Back to Login` action now:
  - writes `sessionStorage[AUTH_INIT_STATUS_KEY] = app_recovery` (best effort)
  - clears login progress markers:
    - `LOGIN_IN_PROGRESS_KEY`
    - `LOGIN_IN_PROGRESS_STARTED_AT_KEY`
  - clears stale redirect reason marker:
    - `AUTH_REDIRECT_REASON_KEY`
  - navigates to `/login?reason=app_recovery`

### 3) Login status card coverage for app recovery
- File: `ecm-frontend/src/components/auth/Login.tsx`
- Added `app_recovery` branch in `buildAuthInitNotice`:
  - title: `Recovered from unexpected app error`
  - detail: `The app encountered an unexpected runtime error and returned to sign-in. Please sign in again.`
- Recognized from:
  - `sessionStorage` init status
  - fallback redirect reason
  - query param reason

### 4) Test updates
- File: `ecm-frontend/src/components/auth/Login.test.tsx`
  - added unit scenarios for `app_recovery` status and query reason.
- File: `ecm-frontend/e2e/app-error-boundary-recovery.mock.spec.ts`
  - added assertion that login page status card shows app recovery copy after `Back to Login`.

## Impact
- No backend/API contract changes.
- Improves crash recovery observability and reduces ambiguity after fallback-to-login transitions.
