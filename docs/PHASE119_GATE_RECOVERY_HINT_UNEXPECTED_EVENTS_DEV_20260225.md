# Phase 119: Gate Recovery Hint Unexpected-Event Detail

## Date
2026-02-25

## Background
- Delivery gate startup hints already parsed missing recovery events from failed logs.
- After strict-mode enhancement, recovery guard can also report unexpected events, but gate hints did not surface those names.

## Goals
1. Parse unexpected recovery-event names from failed stage logs.
2. Include unexpected-event names in gate startup diagnostics hints.
3. Keep existing missing-event hint behavior intact.

## Changes
- `scripts/phase5-phase6-delivery-gate.sh`
  - `print_startup_failure_hints` now collects:
    - `WARN missing event: <name>`
    - `WARN unexpected event: <name>`
  - recovery hint output now supports:
    - missing + unexpected both present
    - missing only
    - unexpected only
  - event names are deduplicated and truncated to the first 8 entries (same style as existing missing-event hint).

## Impact
- No runtime product behavior change.
- Delivery gate diagnostics become more actionable when strict recovery guard fails due to telemetry drift.
