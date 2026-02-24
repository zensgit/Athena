# Phase 108: App Error Recovery Event Coverage in Guard Summary

## Date
2026-02-24

## Background
- Recovery telemetry/guard currently covered chunk-load and startup fallback paths.
- Runtime app-error fallback (`AppErrorBoundary` -> Back to Login) was not explicitly represented in `recovery_event` expected set.

## Goals
1. Emit structured recovery events for app-error fallback lifecycle.
2. Include app-error events in `phase5-regression` recovery guard expectations.
3. Keep mocked gate diagnostics coverage complete across startup/chunk/runtime paths.

## Changes

### 1) App-error recovery marker emission
- `ecm-frontend/e2e/app-error-boundary-recovery.mock.spec.ts`
  - emits:
    - `recovery_event:app_error_overlay_shown`
    - `recovery_event:app_error_back_to_login`

### 2) Recovery guard expected set expansion
- `scripts/phase5-regression.sh`
  - expected recovery events list now includes:
    - `app_error_overlay_shown`
    - `app_error_back_to_login`

## Impact
- No backend/API/runtime code-path behavior change.
- Improves recovery telemetry completeness and fail-fast diagnostics precision in mocked regression output.
