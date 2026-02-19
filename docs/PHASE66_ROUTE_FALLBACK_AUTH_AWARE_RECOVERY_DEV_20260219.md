# Phase 66: Route Fallback Auth-Aware Recovery - Development

## Date
2026-02-19

## Background
- In P1 smoke, unknown-route fallback occasionally redirected to Keycloak auth endpoint during auth bootstrap timing windows.
- Existing fallback expectation in smoke test assumed only in-app UI (`/login` or `/browse/*`) and could fail despite non-blank recovery.

## Goal
1. Keep unknown-route recovery deterministic and auth-aware.
2. Avoid false regression failures caused by legitimate Keycloak redirect timing.
3. Preserve no-blank-page behavior as the primary contract.

## Changes

### 1) Auth-aware wildcard fallback target
- File: `ecm-frontend/src/App.tsx`
- Updated `RouteFallbackRedirect`:
  - resolve target by auth state/session:
    - authenticated -> `/browse/root`
    - unauthenticated -> `/login`
  - keep recovery debug event logging with resolved `to` target.

### 2) P1 smoke stability for unknown-route case
- File: `ecm-frontend/e2e/p1-smoke.spec.ts`
- Updated `unknown route falls back without blank page` assertion:
  - accept either:
    - in-app shell visible (`Athena ECM`),
    - or Keycloak auth endpoint reached (with `client_id=unified-portal`).
- This keeps the test aligned with real auth timing while still validating “no blank page” recovery.

## Non-Functional Notes
- No backend/API contract changes.
- No auth policy change; only fallback target selection and smoke assertion robustness were adjusted.
