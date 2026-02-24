# Phase 104: Recovery Event Telemetry Summary and Gate Hinting

## Date
2026-02-24

## Background
- Phase100-103 added new startup/chunk recovery paths and mocked E2E coverage.
- Gate output still lacked a compact “recovery guard coverage” summary.

## Goals
1. Emit structured recovery markers from relevant mocked E2E flows.
2. Aggregate markers into `phase5-regression` recovery coverage summary.
3. Feed missing-recovery-marker signal into delivery gate startup diagnostics hints.

## Changes

### 1) Structured recovery markers in mocked E2E
- `ecm-frontend/e2e/app-error-boundary-chunk-load-recovery.mock.spec.ts`
  - emits:
    - `recovery_event:chunk_load_hint_shown`
    - `recovery_event:chunk_load_reload_cache_bust`
- `ecm-frontend/e2e/bootstrap-startup-fallback.mock.spec.ts`
  - emits:
    - `recovery_event:startup_fallback_overlay_shown`
    - `recovery_event:startup_fallback_back_to_login`
    - `recovery_event:startup_fallback_not_shown_normal`

### 2) `phase5-regression` aggregation
- `scripts/phase5-regression.sh`
  - parses `recovery_event:*` lines from Playwright output.
  - prints:
    - `phase5_regression: recovery events`
    - `phase5_regression: recovery guard status`
    - `phase5_regression: recovery guard warning count: N`
  - expected marker set:
    - `chunk_load_hint_shown`
    - `chunk_load_reload_cache_bust`
    - `startup_fallback_overlay_shown`
    - `startup_fallback_back_to_login`
    - `startup_fallback_not_shown_normal`

### 3) Delivery gate hint integration
- `scripts/phase5-phase6-delivery-gate.sh`
  - `print_startup_failure_hints` now also checks:
    - `phase5_regression: recovery guard warning count: [1-9]+`
  - new hint:
    - `Recovery guard coverage appears incomplete. Inspect 'phase5_regression: recovery guard status' for missing startup/error events.`

## Impact
- No backend/API changes.
- Improves operator diagnostics for recovery-coverage regressions in mocked gates.
