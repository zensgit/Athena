# Phase 123: Recovery Registry Sync Mode

## Date
2026-02-25

## Background
- Recovery event registry is already externalized to `e2e/recovery-events.expected.txt`.
- Updating registry after adding new `recovery_event:*` markers still required manual edits.

## Goals
1. Add script mode to regenerate registry from `PHASE5_SPECS` marker sources.
2. Keep generated file deterministic and readable.
3. Ensure sync mode integrates with existing registry validation flow.

## Changes

### 1) Sync mode in phase5-regression
- `scripts/phase5-regression.sh`
  - new env:
    - `PHASE5_RECOVERY_REGISTRY_SYNC` (default `0`)
  - when enabled (`1`):
    - scans `PHASE5_SPECS` for `recovery_event:*` markers
    - writes sorted unique events to `PHASE5_RECOVERY_EVENTS_FILE`
    - keeps file header comments
    - continues to registry validation step.

### 2) Registry file normalization
- `ecm-frontend/e2e/recovery-events.expected.txt`
  - now maintained in sorted canonical order under generated header.

## Impact
- No runtime product behavior change.
- Registry maintenance becomes faster and less error-prone when adding new recovery markers.
