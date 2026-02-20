# Phase 75: Login Redirect-Failure Marker Fallback Hardening

## Date
2026-02-20

## Background
- In auth/route recovery flow, redirect-failure markers may remain in `sessionStorage` while `ecm_auth_init_status` has already been cleared.
- Entering `/login` directly in this state could miss the recovery warning, reducing operator/user diagnosability.

## Goal
1. Keep existing `redirect_failed` message behavior backward compatible.
2. Show redirect-failure guidance even when only marker state is present.
3. Clean stale redirect-failure markers once they are out of the configured failure window.
4. Add unit + matrix e2e coverage for the fallback path.

## Changes

### 1) Login fallback message resolver
- File: `ecm-frontend/src/components/auth/Login.tsx`
- Added `buildRedirectFailureMessage(failureCount, lastFailureAt)` helper:
  - supports capped auto-attempt warning and cooldown warning variants
  - returns `null` when markers are stale (elapsed > `AUTH_REDIRECT_FAILURE_WINDOW_MS`)
  - preserves generic warning behavior when timestamp is missing but status is terminal
- Updated mount initialization to read:
  - `ecm_auth_redirect_failure_count`
  - `ecm_auth_redirect_last_failure_at`
- Updated warning decision branch:
  - still supports `ecm_auth_init_status=redirect_failed`
  - also supports marker-only fallback (`init_status` absent)
  - normalizes terminal count (`Math.max(1, redirectFailureCount)`) to avoid regressing legacy no-count paths
  - stale-marker best-effort cleanup for both redirect marker keys

### 2) Unit test coverage expansion
- File: `ecm-frontend/src/components/auth/Login.test.tsx`
- Added tests:
  - marker-only fallback warning (without `ecm_auth_init_status`)
  - stale-marker cleanup with no warning rendered
- Reused `AUTH_REDIRECT_FAILURE_WINDOW_MS` constant for stale-window assertions.

### 3) Matrix e2e expansion
- File: `ecm-frontend/e2e/auth-route-recovery.matrix.spec.ts`
- Added direct-login fallback case:
  - seed redirect-failure markers only
  - navigate `/login`
  - assert warning + login CTA visible
- Hardened init script seeding by passing all keys via `addInitScript` args (avoids closure reference issues).

## Non-Functional Notes
- No backend API change.
- Scope limited to login-side recovery messaging and test coverage.
