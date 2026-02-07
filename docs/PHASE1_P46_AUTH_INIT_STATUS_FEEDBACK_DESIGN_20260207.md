# Phase 1 P46 - Auth Init Status Feedback Design (2026-02-07)

## Background
- P45 introduced auth init timeout guard, but users still lack explicit reason after returning to login.
- Without feedback, timeout/error and normal logout look similar and slow down troubleshooting.

## Goal
- Surface startup auth init outcome on `/login` so users can distinguish:
  - Keycloak/bootstrap timeout
  - generic bootstrap failure
- Keep UX low-noise and backward compatible.

## Scope
- Frontend only:
  - `ecm-frontend/src/index.tsx`
  - `ecm-frontend/src/components/auth/Login.tsx`
  - `ecm-frontend/src/constants/auth.ts`
  - related tests

## Design Changes

### 1) Persist auth init status
- Added session key constants:
  - `AUTH_INIT_STATUS_KEY`
  - `AUTH_INIT_STATUS_TIMEOUT`
  - `AUTH_INIT_STATUS_ERROR`
- In `index.tsx`:
  - clear status at auth init start,
  - set `timeout` when `AuthInitTimeoutError` occurs,
  - set `error` for other init failures.

### 2) Display status on Login page
- `Login.tsx` reads `AUTH_INIT_STATUS_KEY` on mount and displays warning `Alert`:
  - timeout: `Sign-in initialization timed out. Please retry.`
  - error: `Sign-in initialization failed. Please retry.`
- Status key is cleared after read to prevent stale repeated warnings.
- Clicking sign-in clears warning state and status key.

### 3) Test coverage
- Extended login tests to verify:
  - status key cleanup on mount,
  - timeout warning display,
  - generic error warning display.

## Compatibility
- No API/backend impact.
- Session-scoped only; no localStorage persistence.
- Existing auth flows and roles unaffected.
