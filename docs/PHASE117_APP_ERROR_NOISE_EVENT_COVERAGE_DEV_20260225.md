# Phase 117: App Error Noise Event Coverage

## Date
2026-02-25

## Background
- App error noise-filter mocked tests verified that non-fatal global noise does not trigger fatal fallback UI.
- Recovery guard expected set did not explicitly include these two noise-ignore paths.

## Goals
1. Emit structured marker when ResizeObserver noise is ignored.
2. Emit structured marker when abort-like unhandled rejection noise is ignored.
3. Add both markers to `phase5-regression` expected recovery event set.

## Changes

### 1) Noise-filter markers
- `ecm-frontend/e2e/app-error-boundary-noise-filter.mock.spec.ts`
  - emits:
    - `recovery_event:app_error_noise_resize_observer_ignored`
    - `recovery_event:app_error_noise_abort_rejection_ignored`

### 2) Recovery guard expected set expansion
- `scripts/phase5-regression.sh`
  - expected recovery events list now includes:
    - `app_error_noise_resize_observer_ignored`
    - `app_error_noise_abort_rejection_ignored`

## Impact
- No runtime behavior change.
- Recovery telemetry guard now covers false-positive prevention paths in app-error global noise filtering.
