# Phase 118: Recovery Guard Strict Mode

## Date
2026-02-25

## Background
- `phase5-regression` recovery guard reported missing expected events but did not support strict enforcement.
- Guard also only checked missing expected events and did not flag unexpected `recovery_event` markers.

## Goals
1. Add optional strict enforcement mode for recovery guard warnings.
2. Add unexpected-event detection in recovery guard summary.
3. Keep default mode backward compatible.

## Changes

### 1) Strict-mode runtime switch
- `scripts/phase5-regression.sh`
  - new env switch:
    - `PHASE5_RECOVERY_GUARD_STRICT=0` (default, backward compatible)
    - `PHASE5_RECOVERY_GUARD_STRICT=1` (strict mode)
  - strict mode behavior:
    - if recovery guard warnings exist, prints `phase5_regression: strict recovery guard failed`
    - exits non-zero

### 2) Unexpected recovery-event detection
- `scripts/phase5-regression.sh`
  - recovery guard now computes:
    - missing expected events
    - unexpected observed events (not in expected list)
  - warning count now equals `missing + unexpected`.

## Impact
- Default local/CI behavior remains unchanged unless strict mode is enabled.
- Teams can opt into strict guard enforcement to prevent silent telemetry drift.
