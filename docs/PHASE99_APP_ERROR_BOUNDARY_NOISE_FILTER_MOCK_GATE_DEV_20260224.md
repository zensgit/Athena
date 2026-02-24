# Phase 99: AppErrorBoundary Noise Filter Mocked Gate Coverage

## Date
2026-02-24

## Background
- Phase98 added runtime noise filtering in `AppErrorBoundary`.
- To prevent regressions, this behavior should be part of the default mocked regression gate.

## Goals
1. Add a dedicated mocked E2E spec to validate non-fatal noise does not trigger fatal fallback.
2. Include this spec in `phase5-regression` default suite.

## Changes

### 1) New mocked E2E
- File: `ecm-frontend/e2e/app-error-boundary-noise-filter.mock.spec.ts`
- Added scenarios:
  - `App error boundary: ignores ResizeObserver global error noise (mocked)`
  - `App error boundary: ignores abort-like unhandled rejection noise (mocked)`
- Assertions:
  - unexpected-error fallback page stays hidden
  - login page remains visible and interactive

### 2) Gate integration
- File: `scripts/phase5-regression.sh`
- Added spec:
  - `e2e/app-error-boundary-noise-filter.mock.spec.ts`
- Mocked regression total case count increases from `23` to `25`.

## Impact
- No backend/API contract changes.
- Makes Phase98 behavior continuously protected by gate automation.
