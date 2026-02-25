# Phase 122: Gate Registry Mismatch Hints

## Date
2026-02-25

## Background
- Delivery gate startup hints already surfaced startup/recovery guard missing/unexpected events.
- After registry preflight was added, registry mismatch failures still relied on generic stage logs for root cause.

## Goals
1. Detect registry mismatch warnings in failed stage logs.
2. Surface concrete registry mismatch details in startup diagnostics hints.
3. Keep existing hint paths unchanged.

## Changes
- `scripts/phase5-phase6-delivery-gate.sh`
  - `print_startup_failure_hints` now parses registry mismatch signals:
    - `WARN marker missing from events file: <name>`
    - `WARN events file entry not found in specs: <name>`
    - `registry mismatch count: N`
  - added targeted hint output for:
    - missing-in-registry events
    - stale registry entries
    - combined mismatch case
  - event names are deduplicated and truncated consistently with existing hint formatting.

## Impact
- No runtime product behavior change.
- Delivery gate diagnostics become more actionable when mocked registry preflight fails.
