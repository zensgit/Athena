# Phase 92: App Error Boundary Recovery E2E

## Date
2026-02-23

## Background
- Existing `AppErrorBoundary` already provides runtime crash fallback UI.
- Mocked gate had no end-to-end proof that users can recover from a forced render crash back to login.
- This leaves a blind spot for blank/failed-shell regression detection.

## Goals
1. Add deterministic mocked E2E coverage for global render crash recovery.
2. Keep recovery path aligned with existing boundary UX (`Back to Login`).
3. Include the scenario in default mocked regression gate.

## Changes

### 1) New mocked E2E
- File: `ecm-frontend/e2e/app-error-boundary-recovery.mock.spec.ts`
- Scenario:
  1. Open `/login` and seed render-crash marker (`ecm_e2e_force_render_error`).
  2. Navigate to protected route (`/browse/root`) and assert boundary fallback appears.
  3. Click `Back to Login`.
  4. Assert boundary message disappears, login page is visible, and marker is cleared.

### 2) Regression gate integration
- File: `scripts/phase5-regression.sh`
- Added spec entry:
  - `e2e/app-error-boundary-recovery.mock.spec.ts`
- Default mocked suite now runs this crash-recovery guardrail by default.

## Impact
- No backend/API contract changes.
- Improves confidence against blank/fatal-shell regressions in startup/auth navigation paths.
