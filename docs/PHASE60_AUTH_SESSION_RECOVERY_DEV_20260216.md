# Phase 60 - Auth Session Recovery Hardening (Dev) - 2026-02-16

## Background

Recent UI runs showed intermittent "forced back to login" behavior during search/admin workflows.  
Root cause in frontend was a hard fail policy on any `401` response:

- API interceptor redirected immediately to `/login`.
- No retry path existed even when token refresh could recover.
- Login page did not clearly indicate session-expired handoff from API layer.

## Scope

Targeted hardening only (no API contract changes):

1. Add one-time auth recovery on `401`.
2. Keep redirect as fallback when recovery fails.
3. Surface a clear session-expired message on login screen.

## Implementation

### 1) `401` retry-once before redirect

File: `ecm-frontend/src/services/api.ts`

- Exported `ApiService` for unit testing.
- Added `tokenRefreshPromise` to deduplicate concurrent refresh attempts.
- On `401`:
  - If request has not retried yet, attempt refresh.
  - If refresh returns token, inject `Authorization` and retry request once.
  - If retry path cannot recover, mark session expired and redirect.
- Added redirect de-duplication (`redirectInFlight`) to avoid repeated toast/redirect storms.

### 2) Session-expired status handoff

Files:
- `ecm-frontend/src/constants/auth.ts`
- `ecm-frontend/src/services/api.ts`

Changes:
- Added `AUTH_INIT_STATUS_SESSION_EXPIRED = 'session_expired'`.
- On unrecoverable `401`, set `sessionStorage[AUTH_INIT_STATUS_KEY]` to `session_expired`.

### 3) Login UX message

File: `ecm-frontend/src/components/auth/Login.tsx`

- Added message branch:
  - `"Your session expired. Please sign in again."`
- Keeps existing timeout/error/redirect-failed messaging unchanged.

### 4) Testability + unit coverage

Files:
- `ecm-frontend/src/services/api.test.ts` (new)
- `ecm-frontend/src/components/auth/Login.test.tsx`
- `ecm-frontend/src/setupTests.ts`

Changes:
- Added API interceptor tests for:
  - successful refresh + single retry
  - unrecoverable `401` sets session-expired status
- Added login test for `session_expired` message rendering.
- Extended axios test mock with `request()` support.

## Files Changed

- `ecm-frontend/src/services/api.ts`
- `ecm-frontend/src/services/api.test.ts`
- `ecm-frontend/src/components/auth/Login.tsx`
- `ecm-frontend/src/components/auth/Login.test.tsx`
- `ecm-frontend/src/constants/auth.ts`
- `ecm-frontend/src/setupTests.ts`
- `docs/PHASE60_AUTH_SESSION_RECOVERY_VERIFICATION_20260216.md`
- `docs/DOCS_INDEX_20260212.md`
- `docs/NEXT_7DAY_PLAN_PHASE5_20260213.md`
