# Phase 93: Route Fallback No-Blank Mock E2E

## Date
2026-02-23

## Background
- `App.tsx` has route fallback redirect (`* -> /login` or `/browse/root`) and unit coverage in `src/App.test.tsx`.
- Missing piece: mocked E2E coverage in default regression gate to ensure real router/runtime path does not regress to blank page.

## Goals
1. Add mocked E2E for unknown-route fallback in both unauthenticated and authenticated contexts.
2. Keep coverage deterministic without Docker/full backend.
3. Include the scenario in `phase5-regression` default suite.

## Changes

### 1) New mocked E2E
- File: `ecm-frontend/e2e/route-fallback-no-blank.mock.spec.ts`
- Added cases:
  1. Unauthenticated:
     - go to `/definitely-not-a-real-route`
     - assert redirect to `/login`
     - assert login UI visible, no error-boundary fallback visible
  2. Authenticated:
     - seed bypass session
     - mock minimal browse endpoints
     - go to `/definitely-not-a-real-route`
     - assert redirect to `/browse/root`
     - assert folder shell and empty-folder state visible, no error-boundary fallback visible

### 2) Regression gate integration
- File: `scripts/phase5-regression.sh`
- Added spec:
  - `e2e/route-fallback-no-blank.mock.spec.ts`
- Mocked run count increased by 2 test cases (new spec has two tests).

## Impact
- No backend/API contract changes.
- Improves regression detection for unknown-route blank-page failures in real browser runtime.
